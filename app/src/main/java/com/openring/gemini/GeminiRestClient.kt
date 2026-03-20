package com.openring.gemini

import android.util.Log
import com.openring.gemini.model.GenerateContentRequest
import com.openring.gemini.model.GenerateContentResponse
import com.openring.gemini.model.Part
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GeminiRestClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private companion object {
        private const val TAG = "OpenRing"

        // Avoid logcat overload and prevent dumping huge payloads (e.g. get_view_tree root).
        private const val CONTENTS_PREVIEW_LIMIT = 10
        private const val PARTS_PREVIEW_LIMIT = 6
        private const val PART_TEXT_PREVIEW_LIMIT = 140
        private const val TOTAL_PREVIEW_CHARS_LIMIT = 2200
    }

    fun generateContent(
        apiKey: String,
        model: String,
        request: GenerateContentRequest
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
            "Gemini request model=$model url=$url contents=$requestContents parts=$requestParts approxTextChars=$requestTextChars bodyChars=${body.contentLength()}"
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
                throw IllegalStateException("Gemini HTTP ${resp.code}: ${respBody ?: "empty"}")
            }
            return try {
                val parsed = json.decodeFromString(GenerateContentResponse.serializer(), respBody)
                val candidates = parsed.candidates.size
                val functionCalls = parsed.functionCalls()
                val textChars = parsed.firstText()?.length ?: 0
                Log.d(
                    TAG,
                    "Gemini response model=$model elapsedMs=$elapsedMs candidates=$candidates functionCalls=${functionCalls.size} firstTextChars=$textChars"
                )
                parsed
            } catch (e: Exception) {
                val preview = respBody.replace("\n", " ").take(1200)
                Log.e(TAG, "Gemini parse failed model=$model body=$preview", e)
                throw e
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

