package com.openring

import android.app.Application
import android.util.Log
import com.openring.core.AlwaysOnRunGate
import com.openring.domain.Scheduler
import com.openring.skills.SkillQuickJsExecutor
import com.openring.worker.ScanScheduler
import java.io.PrintWriter
import java.io.StringWriter

/**
 * OpenRing Application
 * 純手機端架構 — 無 Web 控制台、無後端
 */
class OpenRingApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 使用者若透過通知終止常駐排程，只停用到本次 App 結束；下次重新開啟 App 自動恢復可啟動。
        AlwaysOnRunGate.clearSuspensionOnAppLaunch(this)
        SkillQuickJsExecutor.ensureLoaderInitialized()
        ScanScheduler.apply(this)
        Scheduler(this).refreshAlwaysOnServiceState()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "CRASH: ${throwable.message}", throwable)
            Log.e(TAG, "Thread: ${thread.name}")
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            Log.e(TAG, sw.toString())
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        private const val TAG = "OpenRing"
    }
}
