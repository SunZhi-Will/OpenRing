package com.openring.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_facts",
    indices = [
        Index(value = ["scope", "sessionId", "factKey"], unique = true)
    ]
)
data class MemoryFactEntity(
    @PrimaryKey val id: String,
    /** "session" | "global" */
    val scope: String,
    /** 工作階段 id；global 時為空字串 */
    val sessionId: String,
    val factKey: String,
    val factValue: String,
    val createdAtMs: Long,
    val updatedAtMs: Long
)
