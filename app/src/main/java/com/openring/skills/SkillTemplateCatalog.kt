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
            scriptUrl = "$RAW_BASE/threads/script.js",
            skillMarkdownUrl = "$RAW_BASE/threads/SKILL.md"
        ),
        Template(
            id = "html_metadata_extractor",
            title = "HTML Metadata Extractor",
            description = "Extract title, description, and OpenGraph image from raw HTML.",
            manifestUrl = "$RAW_BASE/html_metadata_extractor/manifest.json",
            scriptUrl = "$RAW_BASE/html_metadata_extractor/script.js"
        ),
        Template(
            id = "markdown_to_blocks",
            title = "Markdown to Blocks",
            description = "Parse markdown text into deterministic structured JSON blocks.",
            manifestUrl = "$RAW_BASE/markdown_to_blocks/manifest.json",
            scriptUrl = "$RAW_BASE/markdown_to_blocks/script.js"
        ),
        Template(
            id = "json_reformatter",
            title = "JSON Reformatter",
            description = "Reformat JSON into compact or pretty deterministic output.",
            manifestUrl = "$RAW_BASE/json_reformatter/manifest.json",
            scriptUrl = "$RAW_BASE/json_reformatter/script.js"
        ),
        Template(
            id = "crypto_price_fetcher",
            title = "Crypto Price Fetcher",
            description = "Deterministic placeholder for symbol price lookup workflows.",
            manifestUrl = "$RAW_BASE/crypto_price_fetcher/manifest.json",
            scriptUrl = "$RAW_BASE/crypto_price_fetcher/script.js"
        ),
        Template(
            id = "text_uppercase",
            title = "Text Uppercase",
            description = "Convert input text into uppercase deterministically.",
            manifestUrl = "$RAW_BASE/text_uppercase/manifest.json",
            scriptUrl = "$RAW_BASE/text_uppercase/script.js"
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
