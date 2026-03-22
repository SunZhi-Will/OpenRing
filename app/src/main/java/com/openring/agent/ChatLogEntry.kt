package com.openring.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    companion object {
        fun fromJsonObject(obj: JsonObject): ChatLogEntry? {
            val ts = obj["ts"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null
            return when (obj["type"]?.jsonPrimitive?.content) {
                "text" -> Text(
                    message = obj["message"]?.jsonPrimitive?.content ?: return null,
                    createdAtMs = ts
                )

                "tool_call" -> ToolCall(
                    toolName = obj["toolName"]?.jsonPrimitive?.content ?: return null,
                    args = obj["args"]?.jsonObject ?: return null,
                    createdAtMs = ts
                )

                "tool_result" -> ToolResult(
                    toolName = obj["toolName"]?.jsonPrimitive?.content ?: return null,
                    result = obj["result"]?.jsonObject ?: return null,
                    createdAtMs = ts
                )

                else -> null
            }
        }
    }
}

