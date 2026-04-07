package com.openring.settings

import android.content.Context

/**
 * Agent governance: automation confirmation mode and chat history window for Gemini/local ReAct.
 * (Audit trail remains execution log + logcat; no separate audit file in v1.)
 */
class AgentGovernanceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAutomationMode(): String =
        prefs.getString(KEY_AUTOMATION_MODE, MODE_AUTO)?.trim()?.lowercase().takeIf { it in MODES }
            ?: MODE_AUTO

    fun setAutomationMode(mode: String) {
        val m = mode.trim().lowercase()
        if (m !in MODES) return
        prefs.edit().putString(KEY_AUTOMATION_MODE, m).apply()
    }

    fun isConfirmSensitiveMode(): Boolean = getAutomationMode() == MODE_CONFIRM

    fun getChatHistoryTurns(): Int =
        prefs.getInt(KEY_CHAT_HISTORY_TURNS, DEFAULT_HISTORY_TURNS).coerceIn(MIN_HISTORY_TURNS, MAX_HISTORY_TURNS)

    fun setChatHistoryTurns(value: Int) {
        prefs.edit().putInt(KEY_CHAT_HISTORY_TURNS, value.coerceIn(MIN_HISTORY_TURNS, MAX_HISTORY_TURNS)).apply()
    }

    companion object {
        private const val PREFS_NAME = "openring_agent_governance_prefs"
        private const val KEY_AUTOMATION_MODE = "automation_mode"
        private const val KEY_CHAT_HISTORY_TURNS = "chat_history_turns"

        const val MODE_AUTO = "auto"
        const val MODE_CONFIRM = "confirm_sensitive"

        private val MODES = setOf(MODE_AUTO, MODE_CONFIRM)

        private const val DEFAULT_HISTORY_TURNS = 32
        private const val MIN_HISTORY_TURNS = 4
        private const val MAX_HISTORY_TURNS = 80
    }
}
