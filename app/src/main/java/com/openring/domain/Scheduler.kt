package com.openring.domain

import android.content.Context
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.openring.data.model.Schedule
import com.openring.core.AlwaysOnSchedulerService
import com.openring.receiver.ScriptAlarmReceiver
import com.openring.worker.ScriptScheduledWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 定時觸發腳本 — 可選 WorkManager / AlarmManager / 常駐服務
 * US-2.3: 支援每日、每小時、自訂時間
 */
class Scheduler(private val context: Context) {

    companion object {
        private const val WORK_TAG = "openring_scheduled_script"
        private const val ALARM_REQ_CODE_BASE = 31000
    }

    /**
     * 排程腳本
     */
    fun scheduleScript(scriptId: String, schedule: Schedule) {
        cancelScript(scriptId)
        if (!schedule.enabled || schedule.type == "disabled") return

        when (schedule.mode) {
            "exact" -> scheduleExact(scriptId, schedule)
            "always_on" -> ensureAlwaysOnServiceRunning()
            else -> scheduleBattery(scriptId, schedule)
        }
    }

    private fun scheduleBattery(scriptId: String, schedule: Schedule) {
        when (schedule.type) {
            "daily" -> {
                val workRequest = createDailyWork(scriptId, schedule.hour, schedule.minute)
                WorkManager.getInstance(context).enqueueUniqueWork(
                    workName(scriptId),
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            }
            "hourly" -> {
                val workRequest = createHourlyWork(scriptId, schedule.minute)
                WorkManager.getInstance(context).enqueueUniqueWork(
                    workName(scriptId),
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            }
            "interval" -> {
                val workRequest = createIntervalWork(scriptId, schedule.minutes)
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    workName(scriptId),
                    ExistingPeriodicWorkPolicy.REPLACE,
                    workRequest
                )
            }
        }
    }

    private fun scheduleExact(scriptId: String, schedule: Schedule) {
        val triggerAt = when (schedule.type) {
            "daily" -> computeTriggerAt(schedule.hour, schedule.minute)
            "hourly" -> computeTriggerAtMinute(schedule.minute)
            "interval" -> System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(schedule.minutes.toLong().coerceAtLeast(1))
            else -> return
        }
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = alarmPendingIntent(scriptId)
        // 先取消同一個 PendingIntent，避免重複
        alarmManager.cancel(pendingIntent)

        // setExactAndAllowWhileIdle 在 Doze 下也盡量準時（但仍可能受系統策略影響）
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    /**
     * 取消排程
     */
    fun cancelScript(scriptId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(scriptId))
        context.getSystemService(AlarmManager::class.java).cancel(alarmPendingIntent(scriptId))
    }

    /**
     * 立即執行一次（不排程）
     */
    fun runOnce(scriptId: String) {
        val request = OneTimeWorkRequestBuilder<ScriptScheduledWorker>()
            .setInputData(androidx.work.workDataOf("scriptId" to scriptId))
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    private fun createDailyWork(scriptId: String, hour: Int, minute: Int): OneTimeWorkRequest {
        val delay = computeDelayTo(hour, minute)
        return OneTimeWorkRequestBuilder<ScriptScheduledWorker>()
            .setInputData(androidx.work.workDataOf("scriptId" to scriptId))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .build()
    }

    private fun createHourlyWork(scriptId: String, minute: Int): OneTimeWorkRequest {
        val delay = computeDelayToMinute(minute)
        return OneTimeWorkRequestBuilder<ScriptScheduledWorker>()
            .setInputData(androidx.work.workDataOf("scriptId" to scriptId))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .build()
    }

    private fun createIntervalWork(scriptId: String, minutes: Int): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<ScriptScheduledWorker>(
            minutes.toLong().coerceAtLeast(15),
            TimeUnit.MINUTES
        )
            .setInputData(androidx.work.workDataOf("scriptId" to scriptId))
            .addTag(WORK_TAG)
            .build()
    }

    private fun computeDelayTo(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        var target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(cal)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - cal.timeInMillis
    }

    private fun computeDelayToMinute(minute: Int): Long {
        val cal = Calendar.getInstance()
        var target = Calendar.getInstance().apply {
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(cal)) {
            target.add(Calendar.HOUR_OF_DAY, 1)
        }
        return target.timeInMillis - cal.timeInMillis
    }

    private fun computeTriggerAt(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(cal)) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis
    }

    private fun computeTriggerAtMinute(minute: Int): Long {
        val cal = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(cal)) target.add(Calendar.HOUR_OF_DAY, 1)
        return target.timeInMillis
    }

    private fun alarmPendingIntent(scriptId: String): PendingIntent {
        val intent = Intent(context, ScriptAlarmReceiver::class.java).apply {
            setPackage(context.packageName)
            putExtra(ScriptAlarmReceiver.EXTRA_SCRIPT_ID, scriptId)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmRequestCode(scriptId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun alarmRequestCode(scriptId: String): Int {
        // 用 hash 避免超過 Int 範圍；同 scriptId 穩定一致即可
        val h = scriptId.hashCode()
        return ALARM_REQ_CODE_BASE + (kotlin.math.abs(h) % 30000)
    }

    private fun workName(scriptId: String) = "script_$scriptId"

    private fun ensureAlwaysOnServiceRunning() {
        val intent = Intent(context, AlwaysOnSchedulerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
