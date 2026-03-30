package com.openring.settings

import android.content.Context

/**
 * Local WebSocket relay URL and on/off state for [com.openring.core.OpenRingCloudRelayService].
 */
object OpenRingCloudRelayPrefs {

    private const val PREFS = "openring_cloud_relay"
    private const val KEY_URL = "relay_ws_url"
    private const val KEY_ENABLED = "relay_enabled"
    private const val DEFAULT_URL = "ws://192.168.1.100:8080"

    fun getRelayUrl(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    fun setRelayUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URL, url.trim())
            .apply()
    }

    fun isRelayEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun setRelayEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
