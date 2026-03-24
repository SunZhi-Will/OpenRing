package com.openring.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.openring.R
import com.openring.ui.MainActivity
import com.openring.ui.notifications.OpenRingNotificationStyle
import com.openring.receiver.AlwaysOnSchedulerControlReceiver
import com.openring.data.ScriptStore
import com.openring.data.db.OpenRingDatabase
import com.openring.data.model.Schedule
import com.openring.data.model.Script
import com.openring.domain.ScriptExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 常駐排程服務（前景服務）：
 * - 只處理 schedule.mode == "always_on" 的腳本
 * - 以常駐通知換取螢幕關閉/待機時的穩定性
 * - 當無任何 always_on 任務時自動停止
 */
class AlwaysOnSchedulerService : Service() {

    companion object {
        const val CHANNEL_ID = "openring_main_v1"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_TERMINATE_ALWAYS_ON = "com.openring.action.TERMINATE_ALWAYS_ON"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    // interval 類型需要維持下一次觸發時間，避免服務重啟後每次都「從現在開始算」
    private val intervalNextAt = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (AlwaysOnRunGate.isSuspended(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, createNotification())
        if (loopJob == null) {
            loopJob = scope.launch { loop() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        loopJob?.cancel()
        loopJob = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OpenRing 常駐排程",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { setPackage(packageName) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val terminateIntent = PendingIntent.getBroadcast(
            this,
            1102,
            Intent(this, AlwaysOnSchedulerControlReceiver::class.java).apply {
                setPackage(packageName)
                action = ACTION_TERMINATE_ALWAYS_ON
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return OpenRingNotificationStyle.apply(
            NotificationCompat.Builder(this, CHANNEL_ID),
            this
        )
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_always_on_running))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                R.drawable.ic_stat_stop,
                getString(R.string.notification_action_stop_resident),
                terminateIntent
            )
            .build()
    }

    private suspend fun loop() {
        val appContext = applicationContext
        val db = OpenRingDatabase.getDatabase(appContext)
        val scriptDao = db.scriptDao()
        val historyDao = db.executionHistoryDao()
        val scriptStore = ScriptStore(scriptDao)
        val executor = ScriptExecutor(appContext, historyDao)

        while (scope.isActive) {
            val scripts = try {
                scriptDao.getAllScriptsOnce()
            } catch (e: Exception) {
                Log.e("AlwaysOnScheduler", "讀取腳本失敗", e)
                delay(5_000)
                continue
            }

            val alwaysOn = scripts.mapNotNull { script ->
                val schedule = scriptStore.parseSchedule(script.scheduleJson)
                if (schedule.enabled && schedule.type != "disabled" && schedule.mode == "always_on") {
                    script to schedule
                } else null
            }

            if (alwaysOn.isEmpty()) {
                stopSelf()
                return
            }

            val now = System.currentTimeMillis()
            val next = alwaysOn
                .map { (script, schedule) -> script to nextTriggerAt(script, schedule, now) }
                .minByOrNull { it.second }

            if (next == null) {
                delay(2_000)
                continue
            }

            val (script, triggerAt) = next
            val waitMs = (triggerAt - System.currentTimeMillis()).coerceAtLeast(0)
            if (waitMs > 0) delay(waitMs)

            // 再次確認仍有此任務（避免剛好被關掉）
            val refreshed = scriptDao.getScriptById(script.id) ?: continue
            val refreshedSchedule = scriptStore.parseSchedule(refreshed.scheduleJson)
            if (!refreshedSchedule.enabled || refreshedSchedule.type == "disabled" || refreshedSchedule.mode != "always_on") {
                continue
            }

            try {
                executor.execute(refreshed, restoreOpenRingOnFinish = true)
            } catch (e: Exception) {
                Log.e("AlwaysOnScheduler", "執行腳本失敗 id=${refreshed.id}", e)
            }

            // interval 下一次觸發點更新（其他類型由計算函數動態推進）
            if (refreshedSchedule.type == "interval") {
                intervalNextAt[refreshed.id] = System.currentTimeMillis() +
                    TimeUnit.MINUTES.toMillis(refreshedSchedule.minutes.toLong().coerceAtLeast(1))
            }
        }
    }

    private fun nextTriggerAt(script: Script, schedule: Schedule, now: Long): Long {
        return when (schedule.type) {
            "daily" -> {
                val calNow = Calendar.getInstance()
                val target = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, schedule.hour)
                    set(Calendar.MINUTE, schedule.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (target.timeInMillis <= now) target.add(Calendar.DAY_OF_YEAR, 1)
                target.timeInMillis
            }
            "hourly" -> {
                val target = Calendar.getInstance().apply {
                    set(Calendar.MINUTE, schedule.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (target.timeInMillis <= now) target.add(Calendar.HOUR_OF_DAY, 1)
                target.timeInMillis
            }
            "interval" -> {
                intervalNextAt[script.id] ?: (now + TimeUnit.MINUTES.toMillis(schedule.minutes.toLong().coerceAtLeast(1)))
            }
            else -> now + 60_000
        }
    }
}

