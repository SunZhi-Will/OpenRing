package com.openring.data

import android.content.Context
import com.openring.data.db.OpenRingDatabase
import com.openring.data.memory.VectorMemoryMath
import com.openring.data.model.MemoryFactEntity
import com.openring.data.model.MemoryVectorChunkEntity
import com.openring.gemini.GeminiRestClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * 長期記憶：工作階段摘要、結構化關鍵事實、向量片段（Gemini embedding + 本地餘弦檢索）。
 */
class MemoryRepository(
    context: Context,
    private val gemini: GeminiRestClient = GeminiRestClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val appContext = context.applicationContext

    private fun db() = OpenRingDatabase.getDatabase(appContext)

    companion object {
        private const val EMBED_MODEL = "gemini-embedding-001"
    }

    suspend fun buildContextInjection(apiKey: String, sessionId: String, userQuery: String): String {
        val sb = StringBuilder()
        val session = db().chatSessionDao().getById(sessionId)
        val summary = session?.summary?.trim().orEmpty()
        if (summary.isNotEmpty()) {
            sb.appendLine("Session summary:")
            sb.appendLine(summary.take(1200))
            sb.appendLine()
        }
        val globalFacts = db().memoryFactDao().listForScope("global", "", 14)
        val scopedFacts = db().memoryFactDao().listForScope("session", sessionId, 14)
        if (globalFacts.isNotEmpty()) {
            sb.appendLine("Global facts:")
            globalFacts.forEach { sb.appendLine("- ${it.factKey}: ${it.factValue.take(380)}") }
            sb.appendLine()
        }
        if (scopedFacts.isNotEmpty()) {
            sb.appendLine("Session facts:")
            scopedFacts.forEach { sb.appendLine("- ${it.factKey}: ${it.factValue.take(380)}") }
            sb.appendLine()
        }
        val q = userQuery.trim()
        if (q.length >= 2) {
            val hits = vectorRecallInternal(apiKey, sessionId, q, topK = 4, scanLimit = 320)
            if (hits.isNotEmpty()) {
                sb.appendLine("Vector recall (similar past notes):")
                hits.forEach { (score, text) ->
                    sb.appendLine("- [${"%.3f".format(score)}] ${text.take(480)}")
                }
            }
        }
        return sb.toString().trim()
    }

    suspend fun saveFact(scope: String, sessionId: String, factKey: String, factValue: String): String {
        val dao = db().memoryFactDao()
        val key = factKey.trim()
        val now = System.currentTimeMillis()
        val trimmedValue = factValue.take(8000)
        val existing = dao.findByScopeSessionAndKey(scope, sessionId, key)
        return if (existing != null) {
            dao.insert(
                existing.copy(
                    factValue = trimmedValue,
                    updatedAtMs = now
                )
            )
            existing.id
        } else {
            val id = java.util.UUID.randomUUID().toString()
            dao.insert(
                MemoryFactEntity(
                    id = id,
                    scope = scope,
                    sessionId = sessionId,
                    factKey = key,
                    factValue = trimmedValue,
                    createdAtMs = now,
                    updatedAtMs = now
                )
            )
            id
        }
    }

    suspend fun listFacts(scope: String, sessionId: String, limit: Int): List<MemoryFactEntity> =
        db().memoryFactDao().listForScope(scope, sessionId, limit.coerceIn(1, 100))

    suspend fun deleteFact(id: String): Boolean {
        val existing = db().memoryFactDao().getById(id) ?: return false
        db().memoryFactDao().deleteById(existing.id)
        return true
    }

    suspend fun setSessionSummary(sessionId: String, summary: String) {
        val dao = db().chatSessionDao()
        val s = dao.getById(sessionId) ?: return
        dao.update(s.copy(summary = summary.take(12000), updatedAtMs = System.currentTimeMillis()))
    }

    suspend fun getSessionSummary(sessionId: String): String =
        db().chatSessionDao().getById(sessionId)?.summary.orEmpty()

    suspend fun saveVectorChunk(
        apiKey: String,
        scope: String,
        sessionId: String,
        text: String,
    ): String {
        val trimmed = text.trim().take(8000)
        if (trimmed.isEmpty()) return ""
        val vec = gemini.embedContent(
            apiKey = apiKey,
            text = trimmed,
            modelId = EMBED_MODEL,
            taskType = "RETRIEVAL_DOCUMENT"
        )
        val embeddingJson = floatArrayToJson(vec)
        val id = java.util.UUID.randomUUID().toString()
        db().memoryVectorDao().insert(
            MemoryVectorChunkEntity(
                id = id,
                scope = scope,
                sessionId = sessionId,
                content = trimmed,
                embeddingJson = embeddingJson,
                embeddingModel = EMBED_MODEL,
                createdAtMs = System.currentTimeMillis()
            )
        )
        return id
    }

    suspend fun vectorRecall(
        apiKey: String,
        sessionId: String,
        query: String,
        topK: Int,
    ): List<Pair<Float, String>> =
        vectorRecallInternal(apiKey, sessionId, query, topK.coerceIn(1, 20), scanLimit = 400)

    private suspend fun vectorRecallInternal(
        apiKey: String,
        sessionId: String,
        query: String,
        topK: Int,
        scanLimit: Int,
    ): List<Pair<Float, String>> {
        val qVec = gemini.embedContent(
            apiKey = apiKey,
            text = query.trim().take(2000),
            modelId = EMBED_MODEL,
            taskType = "RETRIEVAL_QUERY"
        )
        val rows = db().memoryVectorDao().listForRecall(sessionId, scanLimit)
        if (rows.isEmpty()) return emptyList()
        val scored = rows.mapNotNull { row ->
            val v = parseEmbeddingJson(row.embeddingJson) ?: return@mapNotNull null
            if (v.size != qVec.size) return@mapNotNull null
            VectorMemoryMath.cosine(qVec, v) to row.content
        }
        return scored.sortedByDescending { it.first }.take(topK)
    }

    private fun floatArrayToJson(vec: FloatArray): String = buildString {
        append('[')
        for (i in vec.indices) {
            if (i > 0) append(',')
            append(vec[i].toDouble())
        }
        append(']')
    }

    private fun parseEmbeddingJson(raw: String): FloatArray? =
        runCatching {
            val arr = json.parseToJsonElement(raw).jsonArray
            FloatArray(arr.size) { i ->
                arr[i].jsonPrimitive.content.toFloatOrNull() ?: 0f
            }
        }.getOrNull()
}
