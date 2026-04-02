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
 * 從 ZIP 安裝 Skill（manifest.json + script.js，可選 SKILL.md）。
 * 支援本機 InputStream 與允許來源之 URL。
 */
object SkillInstall {

    private val json = Json { ignoreUnknownKeys = true }

    /** Max sizes for AI inline install (`create_skill`); avoids runaway token payloads. */
    const val MAX_MANIFEST_JSON_CHARS = 64 * 1024
    const val MAX_SCRIPT_JS_CHARS = 512 * 1024
    const val MAX_SKILL_MD_CHARS = 128 * 1024

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
                extractSkillPackage(zis)
            }
        }.fold(
            onSuccess = { pkg ->
                when {
                    pkg == null ->
                        Result.Err("INVALID_PACKAGE", "ZIP must contain manifest.json and script.js")
                    else ->
                        installFromManifestAndScript(context, pkg.manifestJson, pkg.scriptJs, pkg.skillMarkdown)
                }
            },
            onFailure = { e -> Result.Err("INSTALL_FAILED", e.message ?: e.javaClass.simpleName) }
        )
    }

    private data class SkillPackage(
        val manifestJson: String,
        val scriptJs: String,
        val skillMarkdown: String?
    )

    private fun extractSkillPackage(zis: ZipInputStream): SkillPackage? {
        var manifestJson: String? = null
        var scriptJs: String? = null
        var skillMarkdown: String? = null
        var entry = zis.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                val name = entry.name.lowercase()
                val content = zis.readBytes().decodeToString()
                when {
                    name.endsWith("manifest.json") -> manifestJson = content
                    name.endsWith("script.js") -> scriptJs = content
                    name.endsWith("skill.md") -> skillMarkdown = content
                }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
        return if (manifestJson != null && scriptJs != null) {
            SkillPackage(
                manifestJson = manifestJson,
                scriptJs = scriptJs,
                skillMarkdown = skillMarkdown
            )
        } else null
    }

    /**
     * Install or replace a skill from inline strings (e.g. AI `create_skill` tool).
     *
     * @param skillMdSpecified if false, existing [SKILL.md] is left unchanged when overwriting.
     * If true, [skillMdContent] empty or null removes [SKILL.md]; non-blank writes the file.
     */
    fun installFromInlineContent(
        context: Context,
        manifestJson: String,
        scriptJs: String,
        overwrite: Boolean,
        skillMdSpecified: Boolean,
        skillMdContent: String?
    ): Result {
        if (manifestJson.length > MAX_MANIFEST_JSON_CHARS) {
            return Result.Err("TOO_LARGE", "manifest.json exceeds $MAX_MANIFEST_JSON_CHARS characters")
        }
        if (scriptJs.length > MAX_SCRIPT_JS_CHARS) {
            return Result.Err("TOO_LARGE", "script.js exceeds $MAX_SCRIPT_JS_CHARS characters")
        }
        if (skillMdSpecified) {
            val len = skillMdContent?.length ?: 0
            if (len > MAX_SKILL_MD_CHARS) {
                return Result.Err("TOO_LARGE", "SKILL.md exceeds $MAX_SKILL_MD_CHARS characters")
            }
        }
        return when (val v = validateManifest(manifestJson)) {
            is ManifestValidation.Err -> Result.Err(v.code, v.message)
            is ManifestValidation.Ok -> {
                val skillId = v.skillId
                val dir = File(context.filesDir, "skills/$skillId")
                if (dir.exists() && !overwrite) {
                    return Result.Err(
                        "SKILL_EXISTS",
                        "Skill '$skillId' already exists. Pass overwrite=true to replace files."
                    )
                }
                dir.mkdirs()
                File(dir, "manifest.json").writeText(manifestJson.trim())
                File(dir, "script.js").writeText(scriptJs)
                if (skillMdSpecified) {
                    val mdFile = File(dir, "SKILL.md")
                    val md = skillMdContent?.trim().orEmpty()
                    if (md.isEmpty()) {
                        mdFile.delete()
                    } else {
                        mdFile.writeText(md)
                    }
                }
                InstalledSkillStore(context).addInstalled(skillId)
                SkillEnabledStore(context).setEnabled(skillId, true)
                Result.Ok(skillId)
            }
        }
    }

    internal fun installFromManifestAndScript(
        context: Context,
        manifestJson: String,
        scriptJs: String,
        skillMarkdown: String? = null
    ): Result {
        return when (val v = validateManifest(manifestJson)) {
            is ManifestValidation.Err -> Result.Err(v.code, v.message)
            is ManifestValidation.Ok -> {
                val skillId = v.skillId
                val dir = File(context.filesDir, "skills/$skillId").apply { mkdirs() }
                File(dir, "manifest.json").writeText(manifestJson.trim())
                File(dir, "script.js").writeText(scriptJs)
                if (!skillMarkdown.isNullOrBlank()) {
                    File(dir, "SKILL.md").writeText(skillMarkdown.trim())
                }
                InstalledSkillStore(context).addInstalled(skillId)
                // Auto-enable immediately after successful install.
                SkillEnabledStore(context).setEnabled(skillId, true)
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
            val netCfg = SkillNetworkConfig.parse(manifestJson)
            if (netCfg.networkEnabled && netCfg.allowedHosts.isEmpty()) {
                return ManifestValidation.Err(
                    "INVALID_MANIFEST",
                    "manifest.json requires non-empty 'networkHosts' when network permission is declared"
                )
            }
            ManifestValidation.Ok(skillId)
        }.getOrElse { e ->
            ManifestValidation.Err("INVALID_MANIFEST", e.message ?: "Invalid manifest JSON")
        }
    }
}
