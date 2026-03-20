package com.openring.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 腳本資料模型 — 對應 SCRIPT_FORMAT.md
 */
@Entity(tableName = "scripts")
data class Script(
    @PrimaryKey
    val id: String,
    val name: String,
    val version: Int = 1,
    val stepsJson: String,
    val scheduleJson: String
)
