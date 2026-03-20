package com.openring.settings

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class ModelOption(
    val id: String,
    val provider: String = "gemini",
    val label: String,
    val model: String,
)

class ModelStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun getModels(): List<ModelOption> {
        val raw = prefs.getString(KEY_MODELS_JSON, null)
        val parsed = raw?.let {
            runCatching { json.decodeFromString<List<ModelOption>>(it) }.getOrNull()
        } ?: emptyList()

        if (parsed.isNotEmpty()) return parsed

        // Default models (can be reordered/edited by user)
        val defaults = listOf(
            ModelOption(id = UUID.randomUUID().toString(), provider = "gemini", label = "2.5 Flash", model = "gemini-2.5-flash"),
            ModelOption(id = UUID.randomUUID().toString(), provider = "gemini", label = "2.5 Pro", model = "gemini-2.5-pro"),
            ModelOption(id = UUID.randomUUID().toString(), provider = "gemini", label = "2.0 Flash", model = "gemini-2.0-flash"),
        )
        saveModels(defaults)
        return defaults
    }

    fun saveModels(models: List<ModelOption>) {
        prefs.edit().putString(KEY_MODELS_JSON, json.encodeToString(models)).apply()
    }

    companion object {
        private const val PREFS_NAME = "openring_model_prefs"
        private const val KEY_MODELS_JSON = "models_json"
    }
}

