package com.openring.core

import com.openring.core.model.ViewNode

/**
 * ViewNode 工具函數
 */
object ViewNodeUtils {

    fun findNodeById(node: ViewNode?, id: String): ViewNode? {
        if (node == null) return null
        if (node.id == id) return node
        return node.children.firstNotNullOfOrNull { findNodeById(it, id) }
    }

    fun findNodeByText(node: ViewNode?, text: String, match: String): ViewNode? {
        if (node == null) return null
        val nodeText = node.text ?: node.contentDesc ?: return null
        val matches = when (match.lowercase()) {
            "exact" -> nodeText == text
            "contains" -> nodeText.contains(text, ignoreCase = true)
            else -> nodeText.contains(text, ignoreCase = true)
        }
        if (matches && node.clickable) return node
        return node.children.firstNotNullOfOrNull { findNodeByText(it, text, match) }
    }

    fun extractText(node: ViewNode?, nodeId: String): String? {
        val n = findNodeById(node, nodeId) ?: return null
        return n.text ?: n.contentDesc
    }
}
