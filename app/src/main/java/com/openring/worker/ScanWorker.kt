package com.openring.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.openring.agent.ToolDispatcher
import com.openring.settings.ScanCache
import kotlinx.serialization.json.buildJsonObject

/**
 * 週期性執行 UI 掃描並寫入 ScanCache，供 get_cached_scan 使用。
 * 需無障礙服務已啟用，背景執行時若無法取得畫面則略過。
 */
class ScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = ToolDispatcher(applicationContext).dispatch("get_view_tree", buildJsonObject {})
        if (result.ok && result.data.keys.isNotEmpty()) {
            ScanCache(applicationContext).setLastScan(result.data)
        }
        return Result.success()
    }
}
