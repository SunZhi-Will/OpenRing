package com.openring.settings

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * 快取最近一次 UI 掃描結果，供 AI 透過 get_cached_scan 取得。
 * 可由 get_view_tree 或 ScanWorker 更新。
 */
class ScanCache(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun setLastScan(data: JsonObject) {
        prefs.edit()
            .putLong(KEY_TIMESTAMP_MS, System.currentTimeMillis())
            .putString(KEY_DATA_JSON, json.encodeToString(JsonObject.serializer(), data))
            .apply()
    }

    fun getLastScan(): Pair<Long, JsonObject>? {
        val ts = prefs.getLong(KEY_TIMESTAMP_MS, 0L)
        val raw = prefs.getString(KEY_DATA_JSON, null) ?: return null
        return try {
            val obj = json.decodeFromString(JsonObject.serializer(), raw)
            Pair(ts, obj)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "openring_scan_cache"
        private const val KEY_TIMESTAMP_MS = "last_scan_timestamp_ms"
        private const val KEY_DATA_JSON = "last_scan_data_json"
    }
}
