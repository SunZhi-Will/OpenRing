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
        val shouldChainNext = schedule.enabled && when (schedule.type) {
            "daily", "hourly" -> true
            "interval" -> schedule.mode != "exact" && schedule.mode != "always_on"
            else -> false
        }

        // 先續排下一次，避免本次執行失敗後排程斷鍊。
        if (shouldChainNext) {
            Scheduler(applicationContext).scheduleScript(scriptId, schedule)
        }

        val executor = ScriptExecutor(applicationContext, db.executionHistoryDao())
        // 背景排程執行完成後不主動拉回 OpenRing，避免打斷使用者目前前景 App。
        val result = executor.execute(script, restoreOpenRingOnFinish = false)

        return when (result) {
            is ScriptExecutor.ExecutionResult.Success -> Result.success()
            // 已先續排下一次，避免 WorkManager retry backoff 破壞固定頻率
            is ScriptExecutor.ExecutionResult.Failure -> Result.success()
        }
    }
}
