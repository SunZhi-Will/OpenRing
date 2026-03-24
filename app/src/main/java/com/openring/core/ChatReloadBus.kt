package com.openring.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 本地聊天訊息在背景寫入（例如排程 [ai_action]）後，通知 [com.openring.ui.screens.ChatScreen] 重新載入，
 * 避免僅依賴 lifecycle / backgroundWorkCount 而漏掉更新。
 */
object ChatReloadBus {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun notifyMessagesChanged() {
        _events.tryEmit(Unit)
    }
}
