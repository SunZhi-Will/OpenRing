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
                // daily/hourly 採用 OneTimeWorkRequest，需要在執行後續排下一次
                if (schedule.enabled && (schedule.type == "daily" || schedule.type == "hourly")) {
                    Scheduler(applicationContext).scheduleScript(scriptId, schedule)
                }
                Result.success()
            }
            is ScriptExecutor.ExecutionResult.Failure -> Result.retry()
        }
    }
}
