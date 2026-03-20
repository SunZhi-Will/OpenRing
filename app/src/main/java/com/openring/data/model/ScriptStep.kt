package com.openring.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 腳本步驟 — 對應 SCRIPT_FORMAT 動作類型
 * params 使用 JsonObject 以支援不同型別 (ms: Int, text: String 等)
 */
@Serializable
data class ScriptStep(
    val type: String,
    val params: Map<String, String> = emptyMap()
) {
    fun getParam(key: String): String? = params[key]
    fun getParamInt(key: String, default: Int = 0): Int = params[key]?.toIntOrNull() ?: default
}
