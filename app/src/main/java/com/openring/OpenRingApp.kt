package com.openring

import android.app.Application
import android.util.Log
import com.openring.skills.DefaultSkillBootstrap
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
        SkillQuickJsExecutor.ensureLoaderInitialized()
        DefaultSkillBootstrap.apply(this)
        ScanScheduler.apply(this)
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
