package com.openring.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId", "createdAtMs"])]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    /** "user" | "model" */
    val role: String,
    val body: String,
    val createdAtMs: Long
)
