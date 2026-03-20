package com.openring.core.model

/**
 * 動作執行結果
 * 對應 SCRIPT_FORMAT 錯誤碼
 */
sealed class ActionResult {
    data object Ok : ActionResult()
    data class Error(val code: ErrorCode, val message: String? = null) : ActionResult()
}

enum class ErrorCode {
    NODE_NOT_FOUND,
    ACTION_FAILED,
    APP_NOT_INSTALLED,
    PERMISSION_DENIED,
    TIMEOUT,
    UNKNOWN
}
