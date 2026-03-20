package com.openring.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "execution_history")
data class ExecutionRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scriptId: String,
    val scriptName: String,
    val success: Boolean,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
