package com.openring.core

import com.openring.core.model.ViewNode

/**
 * 敏感節點過濾
 * US-1.8: password 與金鑰輸入框一律回傳空值
 */
object SensitiveFilter {

    private val SENSITIVE_CLASS_PATTERNS = listOf(
        "password",
        "Password",
        "editText",
        "EditText"
    )

    private val SENSITIVE_CONTENT_DESC = listOf(
        "password",
        "密碼",
        "助記詞",
        "mnemonic",
        "private key",
        "私鑰",
        "secret"
    )

    /**
     * 過濾節點樹，將敏感節點的 text 設為空
     */
    fun filter(node: ViewNode): ViewNode {
        return if (isSensitive(node)) {
            node.copy(
                text = "",
                contentDesc = null,
                children = node.children.map { filter(it) }
            )
        } else {
            node.copy(children = node.children.map { filter(it) })
        }
    }

    private fun isSensitive(node: ViewNode): Boolean {
        val className = node.className?.lowercase() ?: ""
        if (SENSITIVE_CLASS_PATTERNS.any { className.contains(it.lowercase()) }) return true

        val contentDesc = node.contentDesc?.lowercase() ?: ""
        if (SENSITIVE_CONTENT_DESC.any { contentDesc.contains(it.lowercase()) }) return true

        return false
    }
}
