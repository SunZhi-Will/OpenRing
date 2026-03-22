package com.openring.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.openring.data.db.OpenRingDatabase
import com.openring.domain.ScriptExecutor
import com.openring.domain.Scheduler

/**
 * WorkManager Worker — 執行排程腳本
 */
class ScriptScheduledWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val scriptId = inputData.getString("scriptId") ?: return Result.failure()
        val db = OpenRingDatabase.getDatabase(applicationContext)
        val script = db.scriptDao().getScriptById(scriptId) ?: return Result.failure()
        val scriptStore = com.openring.data.ScriptStore(db.scriptDao())
        val schedule = scriptStore.parseSchedule(script.scheduleJson)

        val executor = ScriptExecutor(applicationContext, db.executionHistoryDao())
        val result = executor.execute(script)

        return when (result) {
            is ScriptExecutor.ExecutionResult.Success -> {
                // daily/hourly/interval(battery) 採 OneTime 鏈，需在成功後續排；interval(exact) 由 Alarm 先續排故不在此重複排程
                if (schedule.enabled) {
                    val chainNext = when (schedule.type) {
                        "daily", "hourly" -> true
                        "interval" -> schedule.mode != "exact" && schedule.mode != "always_on"
                        else -> false
                    }
                    if (chainNext) {
                        Scheduler(applicationContext).scheduleScript(scriptId, schedule)
                    }
                }
                Result.success()
            }
            is ScriptExecutor.ExecutionResult.Failure -> Result.retry()
        }
    }
}
