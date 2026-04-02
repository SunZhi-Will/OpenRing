package com.openring.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.openring.BuildConfig
import com.openring.R
import com.openring.data.db.OpenRingDatabase
import com.openring.data.model.ExecutionRecord
import com.openring.domain.ScriptExecutor
import com.openring.settings.OpenRingCloudRelayPrefs
import com.openring.ui.MainActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit

/**
 * Foreground service: maintains OkHttp WebSocket to the local relay; on [RUN_SCRIPT] runs a
 * workflow by [com.openring.data.model.Script.name] via [ScriptExecutor].
 */
class OpenRingCloudRelayService : Service() {

    companion object {
        private const val TAG = "OpenRingCloudRelay"
        const val CHANNEL_ID = "openring_cloud_relay_v1"
        private const val NOTIFICATION_ID = 1008
        private const val RELAY_LOG_MAX_MESSAGE = 2000
        private const val HISTORY_SNAPSHOT_LIMIT = 80

        /** 通知列「斷線」按鈕：停止中繼並關閉前景服務。 */
        const val ACTION_DISCONNECT = "com.openring.cloud_relay.DISCONNECT"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var connectJob: Job? = null
    private var activeSocket: WebSocket? = null
    private var outboundReplyJob: Job? = null

    private val httpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 供背景執行緒安全送出日誌／歷史回應（與 [activeSocket] 同步於連線生命週期）。 */
    private val relayWsRef = AtomicReference<WebSocket?>(null)
    private val relayDeviceNameRef = AtomicReference<String>("")

    /** 同一則 WebSocket 框在極短時間內重複送達時只處理一次（雙連線／重送）。 */
    private val relayFrameDedupeLock = Any()
    private var lastRelayFrameRaw: String? = null
    private var lastRelayFrameAtMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            disconnectAndStop()
            return START_NOT_STICKY
        }
        startForeground(
            NOTIFICATION_ID,
            buildNotification(getString(R.string.notification_cloud_relay_text_connecting))
        )
        connectJob?.cancel()
        outboundReplyJob?.cancel()
        outboundReplyJob = null
        // 必須關閉舊 WebSocket，否則中繼會殘留多條連線，廣播時同一支手機會收到多次 RELAY_MESSAGE。
        activeSocket?.cancel()
        activeSocket = null
        relayWsRef.set(null)
        relayDeviceNameRef.set("")
        connectJob = scope.launch { connectLoop() }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        connectJob?.cancel()
        connectJob = null
        activeSocket?.cancel()
        activeSocket = null
        relayWsRef.set(null)
        relayDeviceNameRef.set("")
        OpenRingCloudRelayBridge.setDisconnected()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_cloud_relay_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disconnectPending = PendingIntent.getService(
            this,
            10083,
            Intent(this, OpenRingCloudRelayService::class.java).apply {
                setPackage(packageName)
                action = ACTION_DISCONNECT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_cloud_relay_title))
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                R.drawable.ic_stat_stop,
                getString(R.string.cloud_relay_button_disconnect),
                disconnectPending
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    /** 與 [CloudRelayScreen] 的 stopRelay 一致：關閉中繼偏好、釋放連線並回到 Idle。 */
    private fun disconnectAndStop() {
        OpenRingCloudRelayPrefs.setRelayEnabled(applicationContext, false)
        connectJob?.cancel()
        connectJob = null
        outboundReplyJob?.cancel()
        outboundReplyJob = null
        activeSocket?.cancel()
        activeSocket = null
        relayWsRef.set(null)
        relayDeviceNameRef.set("")
        OpenRingCloudRelayBridge.setIdle()
        try {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    private fun notifyForeground(contentText: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private suspend fun connectLoop() {
        while (scope.isActive) {
            if (!OpenRingCloudRelayPrefs.isRelayEnabled(applicationContext)) {
                break
            }
            OpenRingCloudRelayBridge.setConnecting()
            mainHandler.post {
                notifyForeground(getString(R.string.notification_cloud_relay_text_connecting))
            }
            val url = OpenRingCloudRelayPrefs.getRelayUrl(applicationContext)
            Log.d(TAG, "Connecting: $url")
            val sessionDone = CompletableDeferred<Unit>()
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(TAG, "WebSocket connected")
                    val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                    relayWsRef.set(webSocket)
                    relayDeviceNameRef.set(deviceName)
                    val hello = JSONObject()
                        .put("type", "relay")
                        .put("event", "hello")
                        .put("role", "phone")
                        .put("deviceName", deviceName)
                    webSocket.send(hello.toString())
                    emitRelayClientInfo(webSocket, deviceName)
                    emitRelayLog("INFO", "relay connected")
                    outboundReplyJob?.cancel()
                    outboundReplyJob = scope.launch {
                        OpenRingCloudRelayBridge.outboundRelayReplies.collect { replyText ->
                            try {
                                val payload = JSONObject()
                                    .put("action", "RELAY_CHAT_REPLY")
                                    .put("deviceName", deviceName)
                                    .put("text", replyText.take(12_000))
                                webSocket.send(payload.toString())
                            } catch (e: Exception) {
                                Log.e(TAG, "RELAY_CHAT_REPLY send failed", e)
                            }
                        }
                    }
                    OpenRingCloudRelayBridge.setConnected()
                    mainHandler.post {
                        notifyForeground(getString(R.string.notification_cloud_relay_text_connected))
                        Toast.makeText(
                            this@OpenRingCloudRelayService,
                            getString(R.string.cloud_relay_toast_connected),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    scope.launch(Dispatchers.IO) { handleRemoteCommand(text) }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    // Terminal state is handled in onClosed / onFailure
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    relayWsRef.set(null)
                    relayDeviceNameRef.set("")
                    outboundReplyJob?.cancel()
                    outboundReplyJob = null
                    if (OpenRingCloudRelayBridge.phase.value != OpenRingCloudRelayBridge.Phase.Failed) {
                        OpenRingCloudRelayBridge.setDisconnected()
                    }
                    finishSession(sessionDone)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure", t)
                    relayWsRef.set(null)
                    relayDeviceNameRef.set("")
                    outboundReplyJob?.cancel()
                    outboundReplyJob = null
                    OpenRingCloudRelayBridge.setFailed(t.message ?: "connect failed")
                    finishSession(sessionDone)
                }
            }
            val ws = httpClient.newWebSocket(Request.Builder().url(url).build(), listener)
            activeSocket = ws
            try {
                sessionDone.await()
            } finally {
                try {
                    ws.cancel()
                } catch (_: Exception) {
                    /* ignore */
                }
                if (activeSocket === ws) {
                    activeSocket = null
                }
            }
            if (!OpenRingCloudRelayPrefs.isRelayEnabled(applicationContext)) {
                OpenRingCloudRelayBridge.setIdle()
                break
            }
            delay(3_000L)
        }
    }

    private fun finishSession(done: CompletableDeferred<Unit>) {
        if (done.isCompleted) return
        done.complete(Unit)
    }

    /** 讓 Console 辨識是否為含中繼日誌／歷史同步的建置（舊 APK 不會送出此訊息）。 */
    private fun emitRelayClientInfo(webSocket: WebSocket, deviceName: String) {
        try {
            val payload = JSONObject()
                .put("action", "RELAY_CLIENT_INFO")
                .put("deviceName", deviceName)
                .put("versionName", BuildConfig.VERSION_NAME)
                .put("versionCode", BuildConfig.VERSION_CODE)
                .put("supportsRelayTelemetry", true)
            webSocket.send(payload.toString())
        } catch (e: Exception) {
            Log.e(TAG, "RELAY_CLIENT_INFO failed", e)
        }
    }

    /** 轉發至中繼，由 Cloud 儀表板顯示（非持久化，僅連線期間）。 */
    private fun emitRelayLog(level: String, message: String) {
        val ws = relayWsRef.get() ?: return
        val deviceName = relayDeviceNameRef.get().ifBlank { return }
        val trimmed = message.take(RELAY_LOG_MAX_MESSAGE)
        try {
            val payload = JSONObject()
                .put("action", "RELAY_DEVICE_LOG")
                .put("deviceName", deviceName)
                .put("level", level.take(16))
                .put("message", trimmed)
                .put("ts", System.currentTimeMillis())
            ws.send(payload.toString())
        } catch (e: Exception) {
            Log.e(TAG, "RELAY_DEVICE_LOG failed", e)
        }
    }

    private suspend fun sendExecutionHistorySnapshot() {
        val ws = relayWsRef.get() ?: return
        val deviceName = relayDeviceNameRef.get().ifBlank { return }
        val dao = OpenRingDatabase.getDatabase(applicationContext).executionHistoryDao()
        val list: List<ExecutionRecord> = dao.getRecentHistoryLimited(HISTORY_SNAPSHOT_LIMIT)
        val arr = JSONArray()
        for (r in list) {
            val o = JSONObject()
                .put("id", r.id)
                .put("scriptId", r.scriptId)
                .put("scriptName", r.scriptName)
                .put("success", r.success)
                .put("timestamp", r.timestamp)
            if (r.errorMessage != null) {
                o.put("errorMessage", r.errorMessage)
            } else {
                o.put("errorMessage", JSONObject.NULL)
            }
            arr.put(o)
        }
        try {
            val payload = JSONObject()
                .put("action", "RELAY_HISTORY_REPLY")
                .put("deviceName", deviceName)
                .put("records", arr)
            ws.send(payload.toString())
            Log.i(TAG, "RELAY_HISTORY_REPLY sent device=$deviceName records=${list.size}")
        } catch (e: Exception) {
            Log.e(TAG, "RELAY_HISTORY_REPLY failed", e)
        }
    }

    private suspend fun handleRemoteCommand(text: String) {
        try {
            if (text.length > 400) {
                Log.d(TAG, "onMessage len=${text.length} head=${text.take(200)}…")
            } else {
                Log.d(TAG, "onMessage: $text")
            }
            val json = JSONObject(text)
            if (json.optString("type", "") == "relay") {
                if (json.optString("event", "") == "presence") {
                    applyPresencePayload(json.optJSONArray("clients"))
                }
                return
            }
            val action = json.optString("action", "")
            Log.d(TAG, "action=$action")
            when (action) {
                "RUN_SCRIPT" -> handleRunScript(json)
                "RELAY_MESSAGE" -> {
                    if (isDuplicateRelayFrame(text)) {
                        Log.w(TAG, "RELAY_MESSAGE skipped (duplicate frame within window)")
                    } else {
                        handleRelayMessage(json)
                    }
                }
                "REQUEST_EXECUTION_HISTORY" -> scope.launch(Dispatchers.IO) { sendExecutionHistorySnapshot() }
                "RELAY_CHAT_REPLY",
                "RELAY_CLIENT_INFO",
                "RELAY_DEVICE_LOG",
                "RELAY_HISTORY_REPLY" -> { /* 廣播給所有端；手機不需處理 */ }
                else -> Log.w(TAG, "unknown action: $action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Invalid relay payload or execution error", e)
        }
    }

    private fun isDuplicateRelayFrame(raw: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        synchronized(relayFrameDedupeLock) {
            if (raw == lastRelayFrameRaw && now - lastRelayFrameAtMs < 1_200L) {
                return true
            }
            lastRelayFrameRaw = raw
            lastRelayFrameAtMs = now
            return false
        }
    }

    private fun applyPresencePayload(clients: JSONArray?) {
        if (clients == null) {
            OpenRingCloudRelayBridge.setPresenceClients(emptyList())
            return
        }
        val list = ArrayList<RelayPresenceClient>(clients.length())
        for (i in 0 until clients.length()) {
            val o = clients.optJSONObject(i) ?: continue
            list.add(
                RelayPresenceClient(
                    role = o.optString("role", "unknown").take(32),
                    deviceName = o.optString("deviceName", "").take(128),
                )
            )
        }
        OpenRingCloudRelayBridge.setPresenceClients(list)
    }

    private suspend fun handleRunScript(json: JSONObject) {
        val scriptName = json.optString("scriptName", "").trim()
        if (scriptName.isEmpty()) return

        val db = OpenRingDatabase.getDatabase(applicationContext)
        val script = db.scriptDao().getScriptByName(scriptName) ?: run {
            emitRelayLog("WARN", "RUN_SCRIPT: no workflow named \"$scriptName\"")
            Log.w(TAG, "No workflow named \"$scriptName\"")
            return
        }
        emitRelayLog("INFO", "RUN_SCRIPT start: ${script.name}")
        val executor = ScriptExecutor(applicationContext, db.executionHistoryDao())
        when (val result = executor.execute(script)) {
            is ScriptExecutor.ExecutionResult.Success ->
                emitRelayLog("INFO", "RUN_SCRIPT OK: ${script.name}")
            is ScriptExecutor.ExecutionResult.Failure ->
                emitRelayLog(
                    "WARN",
                    "RUN_SCRIPT fail: ${script.name} step=${result.stepIndex} ${result.error.take(400)}"
                )
        }
    }

    /**
     * Must not call [SharedFlow.emit] before [startActivity]: suspend [emit] can block the WS
     * coroutine indefinitely when Chat has no collector, so [startActivity] never runs.
     * Task text is delivered via [MainActivity.EXTRA_RELAY_TASK_TEXT]; NavHost feeds [CloudRelayTaskBus].
     */
    private fun handleRelayMessage(json: JSONObject) {
        val text = json.optString("text", "").trim()
        if (text.isEmpty()) {
            Log.w(TAG, "RELAY_MESSAGE ignored: empty text")
            return
        }
        Log.i(TAG, "RELAY_MESSAGE accepted len=${text.length}")
        emitRelayLog("INFO", "RELAY_MESSAGE accepted len=${text.length}")
        mainHandler.post {
            Log.d(TAG, "startActivity MainActivity (relay text in intent)")
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_OPEN_CHAT_FROM_RELAY, true)
                    putExtra(MainActivity.EXTRA_RELAY_TASK_TEXT, text)
                }
            )
        }
    }
}
