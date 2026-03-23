package com.openring.skills

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Builds a compact OpenClaw-style skill instruction block from installed SKILL.md files.
 * This is guidance text for the model, separate from executable QuickJS runtime.
 */
object SkillInstructionCatalog {
    private val json = Json { ignoreUnknownKeys = true }

    fun buildPromptSection(context: Context, maxChars: Int = 6000): String {
        val enabled = SkillEnabledStore(context).getEnabledIds()
        if (enabled.isEmpty()) return ""

        val blocks = mutableListOf<String>()
        for (skillId in enabled) {
            val dir = File(context.filesDir, "skills/$skillId")
            val manifestName = readManifestName(dir) ?: skillId
            val skillMarkdown = readSkillMarkdown(dir) ?: continue
            val body = extractBody(skillMarkdown).ifBlank { continue }
            val clipped = if (body.length <= 1200) body else body.take(1200) + "\n..."
            blocks.add(
                """
                ### skill_$skillId ($manifestName)
                $clipped
                """.trimIndent()
            )
        }

        if (blocks.isEmpty()) return ""

        val header = """
            ## Installed Skill Instructions
            Use these instructions to decide when to call dynamic tools (skill_<id>) or call_skill.
            Prefer deterministic skills when the user request matches the scope.
        """.trimIndent()

        val full = buildString {
            append(header)
            append("\n\n")
            append(blocks.joinToString("\n\n"))
        }
        return if (full.length <= maxChars) full else full.take(maxChars) + "\n..."
    }

    private fun readSkillMarkdown(dir: File): String? {
        val md = File(dir, "SKILL.md")
        return if (md.isFile) runCatching { md.readText(Charsets.UTF_8) }.getOrNull() else null
    }

    private fun readManifestName(dir: File): String? {
        val mf = File(dir, "manifest.json")
        if (!mf.isFile) return null
        return runCatching {
            val obj = json.parseToJsonElement(mf.readText(Charsets.UTF_8)).jsonObject
            obj["name"]?.jsonPrimitive?.content
        }.getOrNull()
    }

    private fun extractBody(text: String): String {
        val normalized = text.replace("\r\n", "\n")
        if (!normalized.startsWith("---\n")) return normalized.trim()
        val end = normalized.indexOf("\n---\n", startIndex = 4)
        if (end < 0) return normalized.trim()
        return normalized.substring(end + 5).trim()
    }
}
