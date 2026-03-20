package com.openring.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ApiKeyStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Legacy single-key API (kept for backward compatibility).
     * New UX uses per-model keys: see getGeminiApiKeyForModel / setGeminiApiKeyForModel.
     */
    fun getGeminiApiKey(): String? = prefs.getString(KEY_GEMINI_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun setGeminiApiKey(value: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, value.trim()).apply()
    }

    fun clearGeminiApiKey() {
        prefs.edit().remove(KEY_GEMINI_API_KEY).apply()
    }

    fun getGeminiApiKeyForModel(modelId: String): String? =
        prefs.getString(modelKey(modelId), null)?.takeIf { it.isNotBlank() }

    fun setGeminiApiKeyForModel(modelId: String, value: String) {
        prefs.edit().putString(modelKey(modelId), value.trim()).apply()
    }

    fun clearGeminiApiKeyForModel(modelId: String) {
        prefs.edit().remove(modelKey(modelId)).apply()
    }

    companion object {
        private const val FILE_NAME = "openring_secure_prefs"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_GEMINI_API_KEY_PER_MODEL_PREFIX = "gemini_api_key_model__"

        private fun modelKey(modelId: String): String = KEY_GEMINI_API_KEY_PER_MODEL_PREFIX + modelId
    }
}

