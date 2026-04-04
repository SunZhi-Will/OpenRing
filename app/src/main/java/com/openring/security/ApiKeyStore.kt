package com.openring.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ApiKeyStore(context: Context) {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences = openEncryptedPrefsWithRecovery(appContext)

    private fun openEncryptedPrefsWithRecovery(ctx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return try {
            verifyEncryptedPrefs(createEncrypted(ctx, masterKey))
        } catch (e: Exception) {
            if (!isKeystoreDecryptRecoverable(e)) throw e
            Log.w(TAG, "Encrypted prefs unreadable (MAC/key mismatch). Clearing $FILE_NAME and recreating.", e)
            ctx.deleteSharedPreferences(FILE_NAME)
            val masterKey2 = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            verifyEncryptedPrefs(createEncrypted(ctx, masterKey2))
        }
    }

    /** Force load so decrypt/MAC failures surface here (EncryptedSharedPreferences may lazy-read per key). */
    private fun verifyEncryptedPrefs(p: SharedPreferences): SharedPreferences {
        p.all
        return p
    }

    private fun createEncrypted(ctx: Context, masterKey: MasterKey): SharedPreferences =
        EncryptedSharedPreferences.create(
            ctx,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    /**
     * Backup restore / keystore rotation / corrupt file can make AES-GCM MAC fail on read.
     */
    private fun isKeystoreDecryptRecoverable(t: Throwable): Boolean {
        var e: Throwable? = t
        while (e != null) {
            if (e.javaClass.name == "android.security.KeyStoreException") return true
            val msg = e.message.orEmpty()
            if (msg.contains("Signature/MAC verification failed", ignoreCase = true)) return true
            if (msg.contains("VERIFICATION_FAILED", ignoreCase = true)) return true
            e = e.cause
        }
        return false
    }

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
        private const val TAG = "OpenRing"
        private const val FILE_NAME = "openring_secure_prefs"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_GEMINI_API_KEY_PER_MODEL_PREFIX = "gemini_api_key_model__"

        private fun modelKey(modelId: String): String = KEY_GEMINI_API_KEY_PER_MODEL_PREFIX + modelId
    }
}

