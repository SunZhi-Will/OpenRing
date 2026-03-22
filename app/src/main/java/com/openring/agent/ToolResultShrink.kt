package com.openring.agent

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * 與 [ReActCoordinator] 相同：把過大的 UI 樹工具結果壓成 compact，避免塞爆上下文。
 */
internal fun shrinkToolResultForModel(
    toolName: String,
    original: ToolDispatcher.ToolResult,
): ToolDispatcher.ToolResult {
    if (!original.ok) return original
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
