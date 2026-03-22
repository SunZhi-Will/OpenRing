package com.openring.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 將完整無障礙樹壓成給 LLM 用的精簡欄位（與 ReAct 內送 Gemini 的邏輯一致）。
 */
object UiTreeCompact {

    fun fingerprintUiText(root: JsonElement): String {
        val bag = mutableListOf<String>()
        collectText(root, bag)
        return bag.sorted().joinToString(separator = "|").take(8000)
    }

    private fun collectText(element: JsonElement, out: MutableList<String>) {
        when (element) {
            is JsonObject -> {
                val t = element["text"]?.toString()?.trim('"').orEmpty()
                val d = element["contentDesc"]?.toString()?.trim('"').orEmpty()
                if (t.isNotBlank()) out.add(t)
                if (d.isNotBlank()) out.add(d)
                element["children"]?.let { collectText(it, out) }
            }
            is JsonArray -> element.forEach { collectText(it, out) }
            else -> Unit
        }
    }

    fun collectClickableTextNodeSummaries(root: JsonElement, maxNodes: Int = 120): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        fun walk(el: JsonElement) {
            if (out.size >= maxNodes) return
            when (el) {
                is JsonObject -> {
                    val clickable = el["clickable"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() == true
                    val id = el["id"]?.jsonPrimitive?.content
                    val text = el["text"]?.jsonPrimitive?.content
                    val contentDesc = el["contentDesc"]?.jsonPrimitive?.content
                    val label = listOf(text, contentDesc).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

                    if (clickable && !id.isNullOrBlank() && label.isNotBlank()) {
                        val bounds = el["bounds"] as? JsonObject
                        val left = bounds?.get("left")?.jsonPrimitive?.content?.toIntOrNull()
                        val right = bounds?.get("right")?.jsonPrimitive?.content?.toIntOrNull()
                        val item = buildJsonObject {
                            put("id", id)
                            put("label", label.take(200))
                            if (left != null) put("left", left)
                            if (right != null) put("right", right)
                        }
                        out.add(item)
                        if (out.size >= maxNodes) return
                    }

                    val children = el["children"]
                    if (children is JsonArray) {
                        children.forEach { walk(it) }
                    }
                }

                is JsonArray -> el.forEach { walk(it) }
                else -> Unit
            }
        }
        walk(root)
        return out
    }

    /**
     * @param data 含 `root` 與可選 `timestampMs`（與 get_view_tree 回傳相同）
     * @return 精簡後的 data；若無 root 則 null
     */
    fun compactViewTreeData(data: JsonObject): JsonObject? {
        val timestampMs = data["timestampMs"]?.jsonPrimitive?.content?.toLongOrNull()
        val root = data["root"] ?: return null
        if (root is kotlinx.serialization.json.JsonNull) return null

        val rootFingerprint = fingerprintUiText(root).take(8000)
        val clickableSummaries = collectClickableTextNodeSummaries(root, maxNodes = 120)

        return buildJsonObject {
            if (timestampMs != null) put("timestampMs", timestampMs)
            put("rootFingerprint", rootFingerprint)
            put("clickableTextNodes", JsonArray(clickableSummaries))
        }
    }
}
