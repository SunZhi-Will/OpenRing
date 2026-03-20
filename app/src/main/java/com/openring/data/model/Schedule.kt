package com.openring.data.model

import kotlinx.serialization.Serializable

/**
 * 排程設定 — 對應 SCRIPT_FORMAT
 * type: disabled, daily, hourly, interval
 * mode:
 * - battery: 省電（預設，可能被系統延後）
 * - exact: 精準（使用 AlarmManager 精準觸發，較耗電，部分系統版本/設定可能需要額外權限）
 * - always_on: 常駐（前景服務常駐通知，最穩定、最耗電）
 */
@Serializable
data class Schedule(
    val enabled: Boolean = false,
    val type: String = "disabled",
    val mode: String = "battery",
    val hour: Int = 9,
    val minute: Int = 0,
    val minutes: Int = 30  // for interval type
)
