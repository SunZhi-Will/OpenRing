package com.openring.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed class ChatLogEntry(open val createdAtMs: Long) {
    data class Text(val message: String, override val createdAtMs: Long) : ChatLogEntry(createdAtMs)
    data class ToolCall(
        val toolName: String,
        val args: JsonObject,
        override val createdAtMs: Long
    ) : ChatLogEntry(createdAtMs)

    data class ToolResult(
        val toolName: String,
        val result: JsonObject,
        override val createdAtMs: Long
    ) : ChatLogEntry(createdAtMs)

    fun toJsonElement(): JsonObject = when (this) {
        is Text -> buildJsonObject {
            put("type", "text")
            put("message", message)
            put("ts", createdAtMs)
        }

        is ToolCall -> buildJsonObject {
            put("type", "tool_call")
            put("toolName", toolName)
            put("args", args)
            put("ts", createdAtMs)
        }

        is ToolResult -> buildJsonObject {
            put("type", "tool_result")
            put("toolName", toolName)
            put("result", result)
            put("ts", createdAtMs)
        }
    }
}

