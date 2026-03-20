package com.openring.core

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.openring.core.model.ViewNode

/**
 * 解析 AccessibilityNodeInfo 為結構化 ViewNode 樹
 * US-1.2: 輸出包含 className、text、contentDesc、clickable、bounds
 */
class ViewTreeParser(private val service: AccessibilityService) {

    private var nodeIdCounter = 0

    /**
     * 從根節點解析完整 View Tree
     */
    fun parse(root: AccessibilityNodeInfo?): ViewNode? {
        if (root == null) return null
        nodeIdCounter = 0
        return parseNode(root)
    }

    /**
     * 從當前視窗取得根節點並解析
     */
    fun parseFromWindow(): ViewNode? {
        val root = service.rootInActiveWindow ?: return null
        return parse(root)
    }

    private fun parseNode(node: AccessibilityNodeInfo): ViewNode? {
        val id = "node_${nodeIdCounter++}"
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val text = getSafeText(node)
        val contentDesc = getSafeContentDesc(node)

        val children = mutableListOf<ViewNode>()
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                parseNode(child)?.let { children.add(it) }
            }
        }

        return ViewNode(
            id = id,
            className = node.className?.toString(),
            text = text,
            contentDesc = contentDesc,
            clickable = node.isClickable,
            bounds = rect,
            children = children
        )
    }

    /**
     * 敏感欄位：password 類型回傳空值
     */
    private fun getSafeText(node: AccessibilityNodeInfo): String? {
        if (node.isPassword) return null
        return node.text?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun getSafeContentDesc(node: AccessibilityNodeInfo): String? {
        if (node.isPassword) return null
        return node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
    }
}
