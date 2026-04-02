package com.openring.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "prompt_notes",
    indices = [Index(value = ["updatedAtMs"])]
)
data class PromptNoteEntity(
    @PrimaryKey val id: String,
    /** "prompt" | "skill" */
    val kind: String,
    val title: String,
    val description: String,
    val body: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)
