package com.openring.settings

import android.content.Context

/**
 * 自動掃描開關與間隔設定。
 */
class AutoScanStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAutoScanEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setAutoScanEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** 間隔分鐘數（WorkManager 週期下限約 15）。 */
    fun getAutoScanIntervalMinutes(): Int =
        prefs.getInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES).coerceIn(15, 60)

    fun setAutoScanIntervalMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_INTERVAL_MINUTES, minutes.coerceIn(15, 60)).apply()
    }

    companion object {
        private const val PREFS_NAME = "openring_auto_scan_prefs"
        private const val KEY_ENABLED = "auto_scan_enabled"
        private const val KEY_INTERVAL_MINUTES = "auto_scan_interval_minutes"
        const val DEFAULT_INTERVAL_MINUTES = 15
        val ALLOWED_INTERVALS = listOf(15, 30, 60)
    }
}
