package com.openring.settings

import android.content.Context

class AiPromptStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSystemPrompt(): String = prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT).orEmpty()

    fun setSystemPrompt(value: String) {
        prefs.edit().putString(KEY_SYSTEM_PROMPT, value).apply()
    }

    fun getMoralityPolicy(): String = prefs.getString(KEY_MORALITY_POLICY, DEFAULT_MORALITY_POLICY).orEmpty()

    fun setMoralityPolicy(value: String) {
        prefs.edit().putString(KEY_MORALITY_POLICY, value).apply()
    }

    /** 是否允許 AI 透過 set_system_prompt 修改系統 Prompt（預設關閉） */
    fun getAllowAiToSetSystemPrompt(): Boolean = prefs.getBoolean(KEY_ALLOW_AI_SET_SYSTEM_PROMPT, false)

    fun setAllowAiToSetSystemPrompt(allowed: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_AI_SET_SYSTEM_PROMPT, allowed).apply()
    }

    companion object {
        private const val PREFS_NAME = "openring_ai_prompt_prefs"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_MORALITY_POLICY = "morality_policy"
        private const val KEY_ALLOW_AI_SET_SYSTEM_PROMPT = "allow_ai_set_system_prompt"

        // Keep technical docs in English; UI in zh-TW.
        private const val DEFAULT_SYSTEM_PROMPT =
            "You are OpenRing.\n" +
            "Use the provided tools when needed.\n" +
            "Be concise. Ask for clarification only when necessary.\n"

        private const val DEFAULT_MORALITY_POLICY =
            "Morality policy (runtime):\n" +
            "- Never change security- or settings-related data unless the user explicitly requests it.\n" +
            "- If Morality Lock is enabled, refuse any tool call that tries to modify protected settings.\n"
    }
}

