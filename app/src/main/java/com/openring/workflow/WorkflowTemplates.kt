package com.openring.workflow

import android.content.Context
import com.openring.data.model.Schedule
import com.openring.data.model.ScriptStep
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 內建於 assets 的簡易工作流範本（對齊「一句話閉環」體感：降低從零建立門檻）。
 */
object WorkflowTemplates {

    @Serializable
    data class IndexEntry(
        val id: String,
        val title: String,
        val file: String,
    )

    @Serializable
    data class TemplateFile(
        val name: String,
        val steps: List<ScriptStep>,
        val schedule: Schedule = Schedule(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun listEntries(context: Context): List<IndexEntry> {
        return try {
            val text = context.assets.open("workflow_templates/index.json").bufferedReader().use { it.readText() }
            json.decodeFromString<List<IndexEntry>>(text)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun loadTemplate(context: Context, fileName: String): TemplateFile? {
        return try {
            val path = "workflow_templates/$fileName"
            val text = context.assets.open(path).bufferedReader().use { it.readText() }
            json.decodeFromString<TemplateFile>(text)
        } catch (_: Exception) {
            null
        }
    }
}
