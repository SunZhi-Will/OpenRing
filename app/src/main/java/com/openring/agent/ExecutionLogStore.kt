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

