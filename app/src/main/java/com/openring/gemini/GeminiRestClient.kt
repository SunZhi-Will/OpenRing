package com.openring.gemini

import android.util.Log
import com.openring.BuildConfig
import com.openring.gemini.model.Content
import com.openring.gemini.model.EmbedContentRequest
import com.openring.gemini.model.EmbedContentResponse
import com.openring.gemini.model.GenerateContentRequest
import com.openring.gemini.model.GenerateContentResponse
import com.openring.gemini.model.InlineData
import com.openring.gemini.model.Part
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class GeminiRestClient(
    /** Defaults are 10s in OkHttp; Gemini + tool payloads often need longer. */
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private companion object {
        private const val TAG = "OpenRing"

        // Avoid logcat overload and prevent dumping huge payloads (e.g. get_view_tree root).
        private const val CONTENTS_PREVIEW_LIMIT = 10
        private const val PARTS_PREVIEW_LIMIT = 6
        private const val PART_TEXT_PREVIEW_LIMIT = 140
        private const val TOTAL_PREVIEW_CHARS_LIMIT = 2200

        private const val MAX_GENERATE_ATTEMPTS = 5
        private const val INITIAL_BACKOFF_MS = 400L
        private const val MAX_BACKOFF_MS = 12_000L
    }

    fun generateContent(
        apiKey: String,
        model: String,
        request: GenerateContentRequest
    ): GenerateContentResponse {
        var attempt = 0
        var backoffMs = INITIAL_BACKOFF_MS
        var lastError: Exception? = null
        while (attempt < MAX_GENERATE_ATTEMPTS) {
            attempt++
            try {
                return executeGenerateContentOnce(apiKey, model, request, attempt)
            } catch (e: GeminiHttpException) {
                lastError = e
                if (attempt >= MAX_GENERATE_ATTEMPTS || !isRetryableHttp(e.httpCode)) throw e
                logRetry(model, attempt, e.httpCode, backoffMs)
                Thread.sleep(backoffMs + Random.nextLong(0, 280))
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            } catch (e: IOException) {
                lastError = e
                if (attempt >= MAX_GENERATE_ATTEMPTS) throw e
                logRetry(model, attempt, -1, backoffMs)
                Thread.sleep(backoffMs + Random.nextLong(0, 280))
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
        throw lastError ?: IllegalStateException("Gemini generateContent: exhausted retries")
    }

    private fun logRetry(model: String, attempt: Int, code: Int, waitMs: Long) {
        Log.w(TAG, "Gemini retry model=$model attempt=$attempt/$MAX_GENERATE_ATTEMPTS code=$code sleepingMs=$waitMs")
    }

    private fun isRetryableHttp(code: Int): Boolean =
        code == 429 || code == 500 || code == 502 || code == 503 || code == 504

    private fun executeGenerateContentOnce(
        apiKey: String,
        model: String,
        request: GenerateContentRequest,
        attempt: Int,
    ): GenerateContentResponse {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val requestContents = request.contents.size
        val requestParts = request.contents.sumOf { it.parts.size }
        val requestTextChars = request.contents.sumOf { content ->
            content.parts.sumOf { part ->
                (part.text?.length ?: 0) +
                    (part.functionCall?.name?.length ?: 0) +
                    (part.functionResponse?.name?.length ?: 0)
            }
        }
        val body = json.encodeToString(GenerateContentRequest.serializer(), request)
            .toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        Log.d(
            TAG,
            "Gemini request model=$model url=$url contents=$requestContents parts=$requestParts approxTextChars=$requestTextChars bodyChars=${body.contentLength()} attempt=$attempt"
        )
        Log.d(
            TAG,
            "Gemini request preview model=$model systemInstruction=${previewSystemInstruction(request.systemInstruction)} tools=${previewTools(request.tools)} contents=${previewContents(request.contents)}"
        )

        val startMs = System.currentTimeMillis()
        httpClient.newCall(httpRequest).execute().use { resp ->
            val respBody = resp.body?.string()
            val elapsedMs = System.currentTimeMillis() - startMs
            if (!resp.isSuccessful || respBody == null) {
                val preview = respBody?.replace("\n", " ")?.take(1200) ?: "empty"
                Log.e(
                    TAG,
                    "Gemini failed code=${resp.code} message=${resp.message} model=$model elapsedMs=$elapsedMs body=$preview"
                )
                throw GeminiHttpException(resp.code, respBody)
            }
            return try {
                val parsed = json.decodeFromString(GenerateContentResponse.serializer(), respBody)
                val candidates = parsed.candidates.size
                val functionCalls = parsed.functionCalls()
                val textChars = parsed.firstText()?.length ?: 0
                val u = parsed.usageMetadata
                val usageLog = if (u != null) {
                    " promptTok=${u.promptTokenCount} candTok=${u.candidatesTokenCount} totalTok=${u.totalTokenCount}"
                } else {
                    ""
                }
                Log.d(
                    TAG,
                    "Gemini response model=$model elapsedMs=$elapsedMs candidates=$candidates functionCalls=${functionCalls.size} firstTextChars=$textChars$usageLog"
                )
                if (BuildConfig.DEBUG && u != null) {
                    Log.d(
                        TAG,
                        "Gemini usageMetadata model=$model prompt=${u.promptTokenCount} candidates=${u.candidatesTokenCount} total=${u.totalTokenCount}"
                    )
                }
                parsed
            } catch (e: Exception) {
                val preview = respBody.replace("\n", " ").take(1200)
                Log.e(TAG, "Gemini parse failed model=$model body=$preview", e)
                throw e
            }
        }
    }

    /**
     * Single-turn vision: JPEG (Base64) + prompt → model text (e.g. screen understanding when A11y tree is insufficient).
     */
    fun describeScreenWithVision(
        apiKey: String,
        model: String,
        imageJpegBase64: String,
        prompt: String,
    ): String {
        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    role = "user",
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = imageJpegBase64))
                    )
                )
            )
        )
        val resp = generateContent(apiKey, model, request)
        return resp.firstText()?.trim()
            ?: throw IllegalStateException("Vision response had no text")
    }

    /**
     * Single-turn audio: WAV (Base64) + prompt → model text (e.g. spoken prompt / language-learning audio).
     */
    fun describeAmbientAudioWithGemini(
        apiKey: String,
        model: String,
        audioWavBase64: String,
        prompt: String,
    ): String {
        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    role = "user",
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "audio/wav", data = audioWavBase64)),
                    ),
                ),
            ),
        )
        val resp = generateContent(apiKey, model, request)
        return resp.firstText()?.trim()
            ?: throw IllegalStateException("Audio understanding response had no text")
    }

    /**
     * Text embedding for vector memory / similarity (Gemini Developer API).
     * @param modelId e.g. `gemini-embedding-001`
     */
    fun embedContent(
        apiKey: String,
        text: String,
        modelId: String = "gemini-embedding-001",
        taskType: String? = null,
    ): FloatArray {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:embedContent"
        val request = EmbedContentRequest(
            model = "models/$modelId",
            content = Content(
                role = "user",
                parts = listOf(Part(text = text))
            ),
            taskType = taskType
        )
        val body = json.encodeToString(EmbedContentRequest.serializer(), request)
            .toRequestBody("application/json".toMediaType())
        var attempt = 0
        var backoffMs = INITIAL_BACKOFF_MS
        var lastError: Exception? = null
        while (attempt < MAX_GENERATE_ATTEMPTS) {
            attempt++
            try {
                val httpRequest = Request.Builder()
                    .url(url)
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build()
                httpClient.newCall(httpRequest).execute().use { resp ->
                    val respBody = resp.body?.string()
                    if (!resp.isSuccessful || respBody == null) {
                        throw GeminiHttpException(resp.code, respBody)
                    }
                    return parseEmbeddingValues(respBody)
                }
            } catch (e: GeminiHttpException) {
                lastError = e
                if (attempt >= MAX_GENERATE_ATTEMPTS || !isRetryableHttp(e.httpCode)) {
                    throw IllegalStateException("Gemini embed HTTP ${e.httpCode}: ${e.responseBodyPreview ?: "empty"}")
                }
                Thread.sleep(backoffMs + Random.nextLong(0, 280))
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            } catch (e: IOException) {
                lastError = e
                if (attempt >= MAX_GENERATE_ATTEMPTS) throw e
                Thread.sleep(backoffMs + Random.nextLong(0, 280))
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
        throw lastError ?: IllegalStateException("Gemini embed: exhausted retries")
    }

    private fun parseEmbeddingValues(respBody: String): FloatArray {
        return try {
            val parsed = json.decodeFromString(EmbedContentResponse.serializer(), respBody)
            val values = parsed.embedding?.values ?: emptyList()
            FloatArray(values.size) { i -> values[i].toFloat() }
        } catch (_: Exception) {
            val root = json.parseToJsonElement(respBody).jsonObject
            val emb = root["embedding"]?.jsonObject
            val arr = emb?.get("values")?.jsonArray
                ?: throw IllegalStateException("embedContent: missing embedding.values")
            FloatArray(arr.size) { i ->
                arr[i].jsonPrimitive.content.toFloatOrNull()
                    ?: throw IllegalStateException("embedContent: invalid float at $i")
            }
        }
    }

    private fun previewSystemInstruction(systemInstruction: com.openring.gemini.model.Content?): String {
        if (systemInstruction == null) return "null"
        val texts = systemInstruction.parts.mapNotNull { it.text }
        val head = texts.take(2).joinToString(" | ") { previewText(it, PART_TEXT_PREVIEW_LIMIT) }
        return if (texts.isEmpty()) "empty" else "\"$head\"(chars=${head.length})"
    }

    private fun previewTools(tools: List<com.openring.gemini.model.Tool>?): String {
        if (tools == null) return "null"
        val names = tools.flatMap { t -> t.functionDeclarations.map { it.name } }
        val head = names.take(12).joinToString(",")
        return if (names.size <= 12) head else "$head,...(+${names.size - 12})"
    }

    private fun previewContents(contents: List<com.openring.gemini.model.Content>): String {
        if (contents.isEmpty()) return "[]"
        val sb = StringBuilder()
        sb.append("[")
        val maxContents = minOf(contents.size, CONTENTS_PREVIEW_LIMIT)
        for (i in 0 until maxContents) {
            if (sb.length >= TOTAL_PREVIEW_CHARS_LIMIT) {
                sb.append("...truncated")
                break
            }
            val c = contents[i]
            sb.append("#").append(i).append(" role=").append(c.role).append(" parts=").append(c.parts.size).append(" ")
            val parts = c.parts.take(PARTS_PREVIEW_LIMIT)
            val partSummaries = parts.map { previewPart(it) }
            sb.append(partSummaries.joinToString(" | "))
            if (c.parts.size > PARTS_PREVIEW_LIMIT) sb.append(" ...")
            sb.append("; ")
        }
        if (contents.size > CONTENTS_PREVIEW_LIMIT) {
            sb.append("...(+${contents.size - CONTENTS_PREVIEW_LIMIT} more)")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun previewPart(part: Part): String {
        return when {
            part.inlineData != null -> {
                val id = part.inlineData!!
                "inlineData(mime=${id.mimeType}, base64Chars=${id.data.length})"
            }
            part.text != null -> "text=\"${previewText(part.text!!, PART_TEXT_PREVIEW_LIMIT)}\""
            part.functionCall != null -> {
                val fc = part.functionCall!!
                val argsPreview = previewJsonObjectPrimitives(fc.args, 4)
                "funcCall name=${fc.name} args=$argsPreview"
            }
            part.functionResponse != null -> {
                val fr = part.functionResponse!!
                val summary = summarizeFunctionResponse(fr.response)
                "funcResult name=${fr.name} $summary"
            }
            else -> "<emptyPart>"
        }
    }

    private fun summarizeFunctionResponse(response: JsonObject): String {
        val data = response["data"] as? JsonObject
        val dataKeys = data?.keys?.joinToString(",").orEmpty()
        val hasRoot = data?.containsKey("root") == true
        val ok = response["ok"] as? JsonElement
        val code = response["code"]?.let { it as? JsonPrimitive }?.content
        val msg = response["message"]?.let { it as? JsonPrimitive }?.content

        val msgPreview = if (!msg.isNullOrBlank()) previewText(msg, 120) else null
        val dataPreview = when {
            data == null -> "data=null"
            hasRoot -> "dataKeys=[$dataKeys] root=<omitted>"
            else -> "dataKeys=[$dataKeys]"
        }

        return "ok=$ok code=${code ?: "null"} msg=${msgPreview ?: "null"} $dataPreview"
    }

    private fun previewJsonObjectPrimitives(obj: JsonObject, maxEntries: Int): String {
        if (obj.isEmpty()) return "{}"
        val entries = obj.entries.take(maxEntries)
        val head = entries.joinToString(",") { (k, v) ->
            val value = previewJsonElementPrimitiveOrComplex(v)
            "$k=$value"
        }
        return if (obj.size <= maxEntries) "{$head}" else "{$head,...(+${obj.size - maxEntries})}"
    }

    private fun previewJsonElementPrimitiveOrComplex(el: JsonElement): String {
        return when (el) {
            is JsonPrimitive -> "\"" + previewText(el.content, PART_TEXT_PREVIEW_LIMIT) + "\""
            else -> "<complex>"
        }
    }

    private fun previewText(text: String, maxChars: Int): String {
        val oneLine = text.replace('\n', ' ').replace('\r', ' ').trim()
        if (oneLine.length <= maxChars) return oneLine
        return oneLine.take(maxChars) + "..."
    }
}

