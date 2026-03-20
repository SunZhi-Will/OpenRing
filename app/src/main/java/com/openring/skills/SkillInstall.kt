package com.openring.skills

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * 從允許的 URL 下載 ZIP 並安裝 Skill（manifest.json + script.js）。
 */
object SkillInstall {

    private val json = Json { ignoreUnknownKeys = true }

    sealed class Result {
        data class Ok(val skillId: String) : Result()
        data class Err(val code: String, val message: String) : Result()
    }

    fun installFromUrl(context: Context, urlString: String, allowedSources: SkillAllowedSourcesStore): Result {
        if (!allowedSources.isAllowed(urlString)) {
            return Result.Err("URL_NOT_ALLOWED", "URL not in allowed sources. User must add it in Settings.")
        }
        return runCatching {
            val url = URL(urlString)
            url.openStream().use { input ->
                ZipInputStream(input).use { zis ->
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
                    when {
                        manifestJson == null -> Result.Err("INVALID_PACKAGE", "ZIP must contain manifest.json")
                        scriptJs == null -> Result.Err("INVALID_PACKAGE", "ZIP must contain script.js")
                        else -> {
                            val name = json.parseToJsonElement(manifestJson).jsonObject["name"]
                                ?.jsonPrimitive?.content
                                ?: return Result.Err("INVALID_MANIFEST", "manifest.json must have 'name'")
                            val skillId = name.trim().filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "skill" }
                            val dir = File(context.filesDir, "skills/$skillId").apply { mkdirs() }
                            File(dir, "manifest.json").writeText(manifestJson)
                            File(dir, "script.js").writeText(scriptJs)
                            InstalledSkillStore(context).addInstalled(skillId)
                            Result.Ok(skillId)
                        }
                    }
                }
            }
        }.getOrElse { e ->
            Result.Err("INSTALL_FAILED", e.message ?: e.javaClass.simpleName)
        }
    }
}
