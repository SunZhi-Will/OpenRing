package com.openring.skills

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Built-in catalog metadata only.
 * Templates are downloaded on demand and installed locally after user action.
 */
object SkillTemplateCatalog {

    data class Template(
        val id: String,
        val title: String,
        val description: String,
        val manifestUrl: String,
        val scriptUrl: String,
        val skillMarkdownUrl: String? = null
    )

    private const val RAW_BASE =
        "https://raw.githubusercontent.com/SunZhi-Will/OpenRing/main/docs/skill-templates"

    val templates: List<Template> = listOf(
        Template(
            id = "duolingo_word_match_guard",
            title = "Duolingo Word Match Guard",
            description = "Deterministic target resolver for Duolingo word-match tasks.",
            manifestUrl = "$RAW_BASE/duolingo_word_match_guard/manifest.json",
            scriptUrl = "$RAW_BASE/duolingo_word_match_guard/script.js",
            skillMarkdownUrl = "$RAW_BASE/duolingo_word_match_guard/SKILL.md"
        ),
        Template(
            id = "threads",
            title = "Threads",
            description = "Prepare a deterministic Threads post payload.",
            manifestUrl = "$RAW_BASE/threads/manifest.json",
            scriptUrl = "$RAW_BASE/threads/script.js"
        )
    )

    suspend fun installTemplate(context: Context, template: Template): SkillInstall.Result = withContext(Dispatchers.IO) {
        runCatching {
            val manifest = URL(template.manifestUrl).openStream().use { it.readBytes().decodeToString() }
            val script = URL(template.scriptUrl).openStream().use { it.readBytes().decodeToString() }
            val skillMarkdown = template.skillMarkdownUrl?.let { url ->
                runCatching { URL(url).openStream().use { it.readBytes().decodeToString() } }.getOrNull()
            }
            SkillInstall.installFromManifestAndScript(
                context = context,
                manifestJson = manifest,
                scriptJs = script,
                skillMarkdown = skillMarkdown
            )
        }.getOrElse { e ->
            SkillInstall.Result.Err("INSTALL_FAILED", e.message ?: e.javaClass.simpleName)
        }
    }
}
