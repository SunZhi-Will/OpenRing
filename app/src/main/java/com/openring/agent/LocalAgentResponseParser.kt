package com.openring.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

data class ParsedLocalAgentResponse(
    val finalText: String?,
    val toolCalls: List<Pair<String, JsonObject>>,
)

/**
 * 解析本機模型輸出：期望單一 JSON，含 [final] 或 [tool_calls]；失敗時視為純文字結束。
 */
fun parseLocalAgentModelOutput(raw: String): ParsedLocalAgentResponse {
    val trimmed = raw.trim()
    val jsonStr = extractJsonObjectString(trimmed) ?: return ParsedLocalAgentResponse(
        finalText = trimmed,
        toolCalls = emptyList(),
    )
    return try {
        val obj = lenientJson.parseToJsonElement(jsonStr).jsonObject
        val finalText = obj["final"]?.let { el ->
            when (el) {
                is JsonPrimitive -> el.content
                else -> el.toString()
            }
        }?.trim()?.takeIf { it.isNotBlank() }
        val toolCalls = parseToolCallsArray(obj["tool_calls"])
        when {
            toolCalls.isNotEmpty() -> ParsedLocalAgentResponse(finalText = finalText, toolCalls = toolCalls)
            finalText != null -> ParsedLocalAgentResponse(finalText = finalText, toolCalls = emptyList())
            else -> ParsedLocalAgentResponse(finalText = trimmed, toolCalls = emptyList())
        }
    } catch (_: Exception) {
        ParsedLocalAgentResponse(finalText = trimmed, toolCalls = emptyList())
    }
}

private fun parseToolCallsArray(el: JsonElement?): List<Pair<String, JsonObject>> {
    if (el == null) return emptyList()
    val arr = el as? JsonArray ?: return emptyList()
    val out = mutableListOf<Pair<String, JsonObject>>()
    for (item in arr) {
        val o = item as? JsonObject ?: continue
        val name = o["name"]?.jsonPrimitive?.content?.trim() ?: continue
        val args = o["arguments"]?.jsonObject ?: buildJsonObject { }
        out.add(name to args)
    }
    return out
}

private fun extractJsonObjectString(s: String): String? {
    val t = s.trim()
    val fromFence = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE).find(t)
    val candidate = fromFence?.groupValues?.getOrNull(1)?.trim() ?: t
    val start = candidate.indexOf('{')
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escape = false
    for (i in start until candidate.length) {
        val c = candidate[i]
        if (escape) {
            escape = false
            continue
        }
        if (c == '\\' && inString) {
            escape = true
            continue
        }
        if (c == '"') {
            inString = !inString
            continue
        }
        if (inString) continue
        when (c) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return candidate.substring(start, i + 1)
            }
        }
    }
    return null
}
