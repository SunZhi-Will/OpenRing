package com.openring.core

import android.content.Context

object AlwaysOnRunGate {
    private const val PREF_NAME = "openring_runtime_state"
    private const val KEY_SUSPENDED_UNTIL_APP_RESTART = "always_on_suspended_until_app_restart"

    fun suspendUntilNextAppLaunch(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_SUSPENDED_UNTIL_APP_RESTART, true)
            .apply()
    }

    fun clearSuspensionOnAppLaunch(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_SUSPENDED_UNTIL_APP_RESTART, false)
            .apply()
    }

    fun isSuspended(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SUSPENDED_UNTIL_APP_RESTART, false)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
