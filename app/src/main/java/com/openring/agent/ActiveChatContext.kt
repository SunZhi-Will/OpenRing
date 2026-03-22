package com.openring.agent

/**
 * 目前進行中的聊天工作階段與 Gemini API Key，供不依賴無障礙的工具（例如記憶／Skill）在分派時讀取。
 * 由 [com.openring.ui.screens.ChatScreen] 在每次推論前後設定／清除（含 API Key 與模型 id）。
 */
object ActiveChatContext {
    @Volatile
    var sessionId: String? = null

    @Volatile
    var geminiApiKey: String? = null

    /** 目前聊天使用的 Gemini 模型 id（供 describe_screen 等工具對齊同一條模型鏈）。 */
    @Volatile
    var geminiModel: String? = null
}
