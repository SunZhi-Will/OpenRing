package com.openring.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.openring.settings.AutoScanStore
import java.util.concurrent.TimeUnit

/**
 * 依 AutoScanStore 設定排程或取消 ScanWorker。
 */
object ScanScheduler {
    private const val WORK_NAME = "openring_auto_scan"

    fun apply(context: Context) {
        val store = AutoScanStore(context)
        val workManager = WorkManager.getInstance(context)
        if (!store.isAutoScanEnabled()) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val intervalMin = store.getAutoScanIntervalMinutes().toLong()
        val request = PeriodicWorkRequestBuilder<ScanWorker>(intervalMin, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
