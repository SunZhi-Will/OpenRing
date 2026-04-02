package com.openring.data

import android.content.Context
import com.openring.agent.ChatLogEntry
import com.openring.data.db.OpenRingDatabase
import com.openring.chat.ChatAttachmentModelParts
import com.openring.chat.ChatAttachmentPayload
import com.openring.data.model.ChatMessageEntity
import com.openring.data.model.ChatSession
import com.openring.data.model.ExecutionLogEntryEntity
import com.openring.gemini.model.Content
import com.openring.gemini.model.Part
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * 對話訊息與執行紀錄的本地持久化（Room）。
 * 系統提示詞／Skill 白名單等仍由各 Store 管理；此層僅負責「可還原的對話與工具軌跡」。
 */
class ChatRepository(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun db() = OpenRingDatabase.getDatabase(appContext)

    suspend fun getOrCreateActiveSessionId(): String {
        val cached = prefs.getString(KEY_ACTIVE_SESSION, null)
        if (cached != null) {
            val row = db().chatSessionDao().getById(cached)
            if (row != null) return cached
        }
        return createSessionAndSelect()
    }

    suspend fun createSessionAndSelect(): String {
        val id = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        db().chatSessionDao().insert(
            ChatSession(
                id = id,
                title = "",
                summary = "",
                createdAtMs = now,
                updatedAtMs = now
            )
        )
        prefs.edit().putString(KEY_ACTIVE_SESSION, id).apply()
        return id
    }

    suspend fun getMessages(sessionId: String): List<ChatMessageEntity> =
        db().chatMessageDao().getMessagesForSession(sessionId)

    suspend fun addUserMessage(sessionId: String, id: String, body: String, attachmentsJson: String = "") {
        val now = System.currentTimeMillis()
        db().chatMessageDao().insert(
            ChatMessageEntity(
                id = id,
                sessionId = sessionId,
                role = "user",
                body = body,
                attachmentsJson = attachmentsJson,
                createdAtMs = now
            )
        )
        val titleHint = body.replace("\n", " ").trim().ifBlank {
            firstAttachmentDisplayName(attachmentsJson) ?: ""
        }
        touchSession(sessionId, now, titleHint = titleHint.ifBlank { null })
    }

    suspend fun addModelMessage(sessionId: String, id: String, body: String) {
        val now = System.currentTimeMillis()
        db().chatMessageDao().insert(
            ChatMessageEntity(
                id = id,
                sessionId = sessionId,
                role = "model",
                body = body,
                attachmentsJson = "",
                createdAtMs = now
            )
        )
        touchSession(sessionId, now, titleHint = null)
    }

    private suspend fun touchSession(sessionId: String, now: Long, titleHint: String?) {
        val dao = db().chatSessionDao()
        val s = dao.getById(sessionId) ?: return
        val newTitle = if (s.title.isBlank() && titleHint != null) {
            titleHint.replace("\n", " ").trim().take(TITLE_MAX)
        } else {
            s.title
        }
        dao.update(s.copy(title = newTitle, updatedAtMs = now))
    }

    suspend fun loadExecutionLog(sessionId: String, limit: Int = 500): List<ChatLogEntry> {
        val rows = db().executionLogDao().getForSession(sessionId, limit)
        return rows.mapNotNull { row ->
            runCatching {
                json.parseToJsonElement(row.payload).jsonObject
            }.getOrNull()?.let { ChatLogEntry.fromJsonObject(it) }
        }
    }

    suspend fun appendExecutionLog(sessionId: String, entry: ChatLogEntry) {
        val payload = json.encodeToString(JsonObject.serializer(), entry.toJsonElement())
        db().executionLogDao().insert(
            ExecutionLogEntryEntity(
                sessionId = sessionId,
                payload = payload,
                createdAtMs = entry.createdAtMs
            )
        )
    }

    suspend fun listSessions(limit: Int = 80): List<ChatSession> =
        db().chatSessionDao().listRecent(limit)

    suspend fun activateSession(sessionId: String): Boolean {
        if (db().chatSessionDao().getById(sessionId) == null) return false
        prefs.edit().putString(KEY_ACTIVE_SESSION, sessionId).apply()
        return true
    }

    /**
     * 刪除一則聊天記錄（訊息、執行 log 由外鍵 CASCADE；session 範圍的記憶須手動清除）。
     * 若刪除的是目前選中的工作階段，會改選最近一則，沒有則新建。
     * @return 刪除後目前作用中的 session id
     */
    suspend fun deleteSession(sessionId: String): String {
        db().memoryFactDao().deleteAllForSessionScope(sessionId)
        db().memoryVectorDao().deleteAllForSessionScope(sessionId)
        val wasActive = prefs.getString(KEY_ACTIVE_SESSION, null) == sessionId
        db().chatSessionDao().deleteById(sessionId)
        if (!wasActive) {
            return prefs.getString(KEY_ACTIVE_SESSION, null) ?: getOrCreateActiveSessionId()
        }
        val remaining = db().chatSessionDao().listRecent(1).firstOrNull()
        return if (remaining != null) {
            prefs.edit().putString(KEY_ACTIVE_SESSION, remaining.id).apply()
            remaining.id
        } else {
            createSessionAndSelect()
        }
    }

    fun messagesToGeminiContents(messages: List<ChatMessageEntity>, maxTurns: Int = 24): List<Content> {
        val tail = if (messages.size > maxTurns) messages.takeLast(maxTurns) else messages
        return tail.map { m ->
            val parts = mutableListOf<Part>()
            parts.add(Part(text = m.body))
            if (m.attachmentsJson.isNotBlank()) {
                val list = runCatching {
                    json.decodeFromString(ListSerializer(ChatAttachmentPayload.serializer()), m.attachmentsJson)
                }.getOrElse { emptyList() }
                for (a in list) {
                    parts.addAll(ChatAttachmentModelParts.toGeminiParts(a))
                }
            }
            Content(
                role = if (m.role == "user") "user" else "model",
                parts = parts
            )
        }
    }

    fun parseAttachments(payloadJson: String): List<ChatAttachmentPayload> {
        if (payloadJson.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ChatAttachmentPayload.serializer()), payloadJson)
        }.getOrElse { emptyList() }
    }

    fun encodeAttachments(list: List<ChatAttachmentPayload>): String {
        if (list.isEmpty()) return ""
        return json.encodeToString(ListSerializer(ChatAttachmentPayload.serializer()), list)
    }

    private fun firstAttachmentDisplayName(attachmentsJson: String): String? {
        if (attachmentsJson.isBlank()) return null
        return parseAttachments(attachmentsJson).firstOrNull()?.displayName
    }

    companion object {
        private const val PREFS_NAME = "openring_chat_prefs"
        private const val KEY_ACTIVE_SESSION = "active_session_id"
        private const val TITLE_MAX = 48
    }
}
