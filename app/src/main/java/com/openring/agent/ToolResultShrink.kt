package com.openring.agent

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 與 [ReActCoordinator] 相同：把過大的 UI 樹工具結果壓成 compact，避免塞爆上下文。
 */
internal fun shrinkToolResultForModel(
    toolName: String,
    original: ToolDispatcher.ToolResult,
): ToolDispatcher.ToolResult {
    if (!original.ok) return original
    if (toolName == "http_request") {
        val data = original.data
        val bodyEl = data["body"] ?: return original
        val text = bodyEl.jsonPrimitive.content
        if (text.length <= 12000) return original
        val clipped = text.take(12000) + "\n…(truncated for model)"
        return ToolDispatcher.ToolResult(
            ok = true,
            code = original.code,
            message = original.message,
            data = buildJsonObject {
                for ((k, v) in data) {
                    if (k == "body") put(k, JsonPrimitive(clipped))
                    else put(k, v)
                }
            },
        )
    }
    if (toolName != "get_view_tree" && toolName != "get_cached_scan") return original

    val data = original.data
    val root = data["root"]

    if (root == null || root is JsonNull) return original

    val compactData = UiTreeCompact.compactViewTreeData(data) ?: return original

    return ToolDispatcher.ToolResult(
        ok = original.ok,
        code = original.code,
        message = original.message,
        data = compactData,
    )
}
