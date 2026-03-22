package com.openring.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray

object ExecutionLogStore {
    private const val MAX_ENTRIES = 250

    private val _entries = MutableStateFlow<List<ChatLogEntry>>(emptyList())
    val entries = _entries.asStateFlow()

    fun clear() {
        _entries.value = emptyList()
    }

    /** 切換對話或從資料庫還原時，取代記憶體中的執行紀錄（仍受 MAX_ENTRIES 限制）。 */
    fun replaceAll(entries: List<ChatLogEntry>) {
        _entries.value = if (entries.size > MAX_ENTRIES) entries.takeLast(MAX_ENTRIES) else entries
    }

    fun add(entry: ChatLogEntry) {
        _entries.update { old ->
            val next = old + entry
            if (next.size > MAX_ENTRIES) next.takeLast(MAX_ENTRIES) else next
        }
    }

    fun snapshotEntries(): List<ChatLogEntry> = _entries.value

    fun snapshotAsJsonArray(): JsonArray {
        val list = _entries.value
        return buildJsonArray {
            list.forEach { entry ->
                add(entry.toJsonElement())
            }
        }
    }
}

