package com.openring.skills

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.InputStream
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * 從 ZIP 安裝 Skill（manifest.json + script.js）。
 * 支援本機 InputStream 與允許來源之 URL。
 */
object SkillInstall {

    private val json = Json { ignoreUnknownKeys = true }

    sealed class Result {
        data class Ok(val skillId: String) : Result()
        data class Err(val code: String, val message: String) : Result()
    }

    private sealed class ManifestValidation {
        data class Ok(val skillId: String) : ManifestValidation()
        data class Err(val code: String, val message: String) : ManifestValidation()
    }

    fun installFromUrl(context: Context, urlString: String, allowedSources: SkillAllowedSourcesStore): Result {
        if (!allowedSources.isAllowed(urlString)) {
            return Result.Err("URL_NOT_ALLOWED", "URL not in allowed sources. User must add it in Settings.")
        }
        return runCatching {
            URL(urlString).openStream().use { installFromZipInputStream(context, it) }
        }.getOrElse { e ->
            Result.Err("INSTALL_FAILED", e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * 從本機 ZIP（例如檔案選擇器）安裝；不需 URL 白名單。
     */
    fun installFromZipInputStream(context: Context, input: InputStream): Result {
        return runCatching {
            ZipInputStream(input.buffered()).use { zis ->
                extractManifestAndScript(zis)
            }
        }.fold(
            onSuccess = { pair ->
                when {
                    pair == null ->
                        Result.Err("INVALID_PACKAGE", "ZIP must contain manifest.json and script.js")
                    else ->
                        installFromManifestAndScript(context, pair.first, pair.second)
                }
            },
            onFailure = { e -> Result.Err("INSTALL_FAILED", e.message ?: e.javaClass.simpleName) }
        )
    }

    private fun extractManifestAndScript(zis: ZipInputStream): Pair<String, String>? {
        var manifestJson: String? = null
        var scriptJs: String? = null
        var entry = zis.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                val name = entry.name.lowercase()
                val content = zis.readBytes().decodeToString()
                when {
                    name.endsWith("manifest.json") -> manifestJson = content
                    name.endsWith("script.js") -> scriptJs = content
                }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
        return if (manifestJson != null && scriptJs != null) manifestJson to scriptJs else null
    }

    internal fun installFromManifestAndScript(context: Context, manifestJson: String, scriptJs: String): Result {
        return when (val v = validateManifest(manifestJson)) {
            is ManifestValidation.Err -> Result.Err(v.code, v.message)
            is ManifestValidation.Ok -> {
                val skillId = v.skillId
                val dir = File(context.filesDir, "skills/$skillId").apply { mkdirs() }
                File(dir, "manifest.json").writeText(manifestJson.trim())
                File(dir, "script.js").writeText(scriptJs)
                InstalledSkillStore(context).addInstalled(skillId)
                Result.Ok(skillId)
            }
        }
    }

    private fun validateManifest(manifestJson: String): ManifestValidation {
        return runCatching {
            val obj = json.parseToJsonElement(manifestJson).jsonObject
            val rawName = obj["name"]?.jsonPrimitive?.content
            if (rawName.isNullOrBlank()) {
                return ManifestValidation.Err("INVALID_MANIFEST", "manifest.json must have non-empty 'name'")
            }
            val skillId = rawName.trim().filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "skill" }
            val inputSchema = obj["inputSchema"]
            if (inputSchema != null && inputSchema !is JsonObject) {
                return ManifestValidation.Err(
                    "INVALID_MANIFEST",
                    "manifest.json 'inputSchema' must be a JSON object"
                )
            }
            val outputSchema = obj["outputSchema"]
            if (outputSchema != null && outputSchema !is JsonObject) {
                return ManifestValidation.Err(
                    "INVALID_MANIFEST",
                    "manifest.json 'outputSchema' must be a JSON object"
                )
            }
            ManifestValidation.Ok(skillId)
        }.getOrElse { e ->
            ManifestValidation.Err("INVALID_MANIFEST", e.message ?: "Invalid manifest JSON")
        }
    }
}
