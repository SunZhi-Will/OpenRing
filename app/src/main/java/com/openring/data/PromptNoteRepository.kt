package com.openring.data

import android.content.Context
import com.openring.data.db.OpenRingDatabase
import com.openring.data.model.PromptNoteEntity
import java.util.UUID

/**
 * 使用者「記憶庫」中的 Prompt／Skill 筆記（與 [MemoryRepository] 注入的長期記憶同一層，模型可經工具讀取全文）。
 */
class PromptNoteRepository(context: Context) {
    private val dao = OpenRingDatabase.getDatabase(context).promptNoteDao()

    suspend fun listAllOrdered(): List<PromptNoteEntity> = dao.listAllOrdered()

    suspend fun getById(id: String): PromptNoteEntity? = dao.getById(id)

    suspend fun upsert(
        id: String?,
        kind: String,
        title: String,
        description: String,
        body: String,
    ) {
        val now = System.currentTimeMillis()
        val existing = id?.let { dao.getById(it) }
        val entity = PromptNoteEntity(
            id = id ?: UUID.randomUUID().toString(),
            kind = kind,
            title = title.trim(),
            description = description.trim(),
            body = body.trim(),
            createdAtMs = existing?.createdAtMs ?: now,
            updatedAtMs = now
        )
        dao.upsert(entity)
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    companion object {
        const val KIND_PROMPT = "prompt"
        const val KIND_SKILL = "skill"

        /** 插入輸入框或送給模型時用的純文字區塊 */
        fun formatForChat(note: PromptNoteEntity): String = buildString {
            if (note.kind == KIND_SKILL) {
                appendLine("## Skill: ${note.title}")
                if (note.description.isNotBlank()) {
                    appendLine("Description: ${note.description}")
                }
                appendLine()
                appendLine("### Prompt")
                append(note.body.trim())
            } else {
                appendLine("## Prompt: ${note.title}")
                if (note.description.isNotBlank()) {
                    appendLine(note.description.trim())
                    appendLine()
                }
                append("---\n")
                append(note.body.trim())
            }
        }

        /** 工具回傳給模型的結構化摘要 */
        fun formatForTool(note: PromptNoteEntity): String = buildString {
            appendLine("kind=${note.kind}")
            appendLine("title=${note.title}")
            if (note.description.isNotBlank()) {
                appendLine("description=${note.description}")
            }
            appendLine("body:")
            append(note.body.trim())
        }
    }
}
