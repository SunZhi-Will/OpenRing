package com.openring.agent

/**
 * 目前進行中的聊天工作階段與 Gemini API Key，供不依賴無障礙的工具（例如記憶／Skill）在分派時讀取。
 * 由 [com.openring.ui.screens.ChatScreen] 在每次推論前後設定／清除。
 */
object ActiveChatContext {
    @Volatile
    var sessionId: String? = null

    @Volatile
    var geminiApiKey: String? = null
}
