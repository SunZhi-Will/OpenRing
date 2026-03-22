package com.openring.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_vector_chunks",
    indices = [Index(value = ["scope", "sessionId", "createdAtMs"])]
)
data class MemoryVectorChunkEntity(
    @PrimaryKey val id: String,
    /** "session" | "global" */
    val scope: String,
    /** 工作階段 id；global 時為空字串 */
    val sessionId: String,
    val content: String,
    /** JSON array of floats */
    val embeddingJson: String,
    val embeddingModel: String,
    val createdAtMs: Long
)
