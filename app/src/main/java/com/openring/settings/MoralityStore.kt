package com.openring.settings

import android.content.Context

class MoralityStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isMoralityLockEnabled(): Boolean = prefs.getBoolean(KEY_MORALITY_LOCK_ENABLED, true)

    fun setMoralityLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MORALITY_LOCK_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "openring_morality_prefs"
        private const val KEY_MORALITY_LOCK_ENABLED = "morality_lock_enabled"
    }
}

