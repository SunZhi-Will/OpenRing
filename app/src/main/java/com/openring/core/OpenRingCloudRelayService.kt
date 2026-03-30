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
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.openring.R
import com.openring.data.db.OpenRingDatabase
import com.openring.domain.ScriptExecutor
import com.openring.settings.OpenRingCloudRelayPrefs
import com.openring.ui.MainActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var connectJob: Job? = null
    private var activeSocket: WebSocket? = null

    private val httpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(getString(R.string.notification_cloud_relay_text_connecting))
        )
        connectJob?.cancel()
        connectJob = scope.launch { connectLoop() }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        connectJob?.cancel()
        connectJob = null
        activeSocket?.cancel()
        activeSocket = null
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
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_cloud_relay_title))
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
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
                    val hello = JSONObject()
                        .put("type", "relay")
                        .put("event", "hello")
                        .put("role", "phone")
                        .put("deviceName", deviceName)
                    webSocket.send(hello.toString())
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
                    if (OpenRingCloudRelayBridge.phase.value != OpenRingCloudRelayBridge.Phase.Failed) {
                        OpenRingCloudRelayBridge.setDisconnected()
                    }
                    finishSession(sessionDone)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure", t)
                    OpenRingCloudRelayBridge.setFailed(t.message ?: "connect failed")
                    finishSession(sessionDone)
                }
            }
            val ws = httpClient.newWebSocket(Request.Builder().url(url).build(), listener)
            activeSocket = ws
            sessionDone.await()
            activeSocket = null
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
                    handleRelayMessage(json)
                }
                else -> Log.w(TAG, "unknown action: $action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Invalid relay payload or execution error", e)
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
            Log.w(TAG, "No workflow named \"$scriptName\"")
            return
        }
        val executor = ScriptExecutor(applicationContext, db.executionHistoryDao())
        executor.execute(script)
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
