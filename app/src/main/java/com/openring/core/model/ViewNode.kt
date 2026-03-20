package com.openring.core.model

import android.graphics.Rect

/**
 * 結構化節點 — 對應 SCRIPT_FORMAT 的 View Tree JSON
 * 用於 ViewTreeParser 輸出與 ActionExecutor 定位
 */
data class ViewNode(
    val id: String,
    val className: String?,
    val text: String?,
    val contentDesc: String?,
    val clickable: Boolean,
    val bounds: Rect,
    val children: List<ViewNode> = emptyList()
) {
    fun toJsonMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "className" to className,
        "text" to text,
        "contentDesc" to contentDesc,
        "clickable" to clickable,
        "bounds" to mapOf(
            "left" to bounds.left,
            "top" to bounds.top,
            "right" to bounds.right,
            "bottom" to bounds.bottom
        ),
        "children" to children.map { it.toJsonMap() }
    )
}
