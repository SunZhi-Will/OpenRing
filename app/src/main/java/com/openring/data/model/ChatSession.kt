package com.openring.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey val id: String,
    val title: String,
    /** 工作階段長摘要（可由 AI 透過 memory_set_session_summary 更新） */
    val summary: String = "",
    val createdAtMs: Long,
    val updatedAtMs: Long
)
