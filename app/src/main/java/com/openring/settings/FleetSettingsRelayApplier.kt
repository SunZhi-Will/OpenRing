package com.openring.settings

import android.content.Context
import com.openring.security.ApiKeyStore
import com.openring.skills.SkillEnabledStore
import kotlinx.serialization.json.Json
import org.json.JSONObject

/**
 * Applies [RELAY_FLEET_SETTINGS] payloads from OpenRing Cloud relay (optional fields, all merged).
 * Keys are written only when the sender sets applyKeys=true (explicit opt-in).
 */
object FleetSettingsRelayApplier {

    private const val MAX_PATCH_CHARS = 512_000

    data class Result(val appliedParts: List<String>, val error: String?)

    fun apply(context: Context, envelope: JSONObject): Result {
        val patch = envelope.optJSONObject("patch")
        if (patch == null) {
            return Result(emptyList(), "missing patch object")
        }
        val patchStr = patch.toString()
        if (patchStr.length > MAX_PATCH_CHARS) {
            return Result(emptyList(), "patch too large")
        }
        val applyKeys = envelope.optBoolean("applyKeys", false)
        val applied = mutableListOf<String>()

        val json = Json { ignoreUnknownKeys = true }

        patch.optJSONArray("models")?.let { arr ->
            if (arr.length() == 0) return@let
            val parsed = runCatching {
                json.decodeFromString<List<ModelOption>>(arr.toString())
            }.getOrNull()
            if (!parsed.isNullOrEmpty()) {
                ModelStore(context).saveModels(parsed)
                applied.add("models(${parsed.size})")
            }
        }

        if (patch.has("systemPrompt")) {
            val t = patch.optString("systemPrompt", "")
            AiPromptStore(context).setSystemPrompt(t)
            applied.add("systemPrompt")
        }
        if (patch.has("moralityPolicy")) {
            AiPromptStore(context).setMoralityPolicy(patch.optString("moralityPolicy", ""))
            applied.add("moralityPolicy")
        }
        if (patch.has("maxRounds")) {
            val n = patch.optInt("maxRounds", AiPromptStore(context).getMaxRounds())
            AiPromptStore(context).setMaxRounds(n)
            applied.add("maxRounds")
        }
        if (patch.has("allowAiToSetSystemPrompt")) {
            AiPromptStore(context).setAllowAiToSetSystemPrompt(patch.getBoolean("allowAiToSetSystemPrompt"))
            applied.add("allowAiToSetSystemPrompt")
        }
        if (patch.has("allowAiToCreateSkill")) {
            AiPromptStore(context).setAllowAiToCreateSkill(patch.getBoolean("allowAiToCreateSkill"))
            applied.add("allowAiToCreateSkill")
        }

        patch.optJSONArray("enabledSkillIds")?.let { arr ->
            val ids = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "").trim()
                if (s.isNotEmpty()) ids.add(s)
            }
            SkillEnabledStore(context).replaceEnabledIds(ids)
            applied.add("enabledSkillIds(${ids.size})")
        }

        if (applyKeys) {
            val keysObj = patch.optJSONObject("geminiApiKeys")
            if (keysObj != null) {
                val store = ApiKeyStore(context)
                var n = 0
                val it = keysObj.keys()
                while (it.hasNext()) {
                    val modelId = it.next().trim()
                    if (modelId.isEmpty()) continue
                    val v = keysObj.optString(modelId, "").trim()
                    if (v.isNotEmpty()) {
                        store.setGeminiApiKeyForModel(modelId, v)
                        n++
                    }
                }
                if (n > 0) applied.add("geminiApiKeys($n)")
            }
        }

        return if (applied.isEmpty()) {
            Result(emptyList(), "no recognized fields")
        } else {
            Result(applied, null)
        }
    }
}
