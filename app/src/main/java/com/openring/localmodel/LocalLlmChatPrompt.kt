package com.openring.localmodel

import com.openring.gemini.model.Content

/**
 * 將 Gemini 格式的對話歷史轉成地端 TinyLlama / 類 Chat 模型可用的純文字 prompt。
 * 一般聊天為純文字續寫；多輪工具請用 [appendAgentToolRound] 接續 prompt。
 */
object LocalLlmChatPrompt {

    enum class Style {
        /** TinyLlama-1.1B-Chat 等常見的 ### User / ### Assistant 區塊 */
        TINYLLAMA_BLOCKS,

        /** Qwen2.x Instruct 系：ChatML（<|im_start|> / <|im_end|>） */
        CHATML_QWEN,

        /** Phi-3 / Phi-3.5 Instruct：Microsoft chat markers */
        PHI3_INSTRUCT,

        /** Gemma 2 IT：<start_of_turn>user / model */
        GEMMA2_IT,
    }

    fun styleForCatalogId(catalogId: String): Style {
        val id = catalogId.lowercase()
        return when {
            id.contains("qwen") -> Style.CHATML_QWEN
            id.contains("phi") -> Style.PHI3_INSTRUCT
            id.contains("gemma") -> Style.GEMMA2_IT
            else -> Style.TINYLLAMA_BLOCKS
        }
    }

    /**
     * @param priorContents 不含本輪 user 訊息（與 Gemini ReAct 路徑一致）
     */
    fun buildPrompt(
        style: Style,
        systemPrompt: String,
        memoryInjection: String,
        priorContents: List<Content>,
        currentUserMessage: String,
    ): String {
        val history = contentsToUserAssistantPairs(priorContents)
        return when (style) {
            Style.TINYLLAMA_BLOCKS -> buildTinyLlamaBlocks(
                systemPrompt = systemPrompt,
                memoryInjection = memoryInjection,
                history = history,
                currentUser = currentUserMessage
            )

            Style.CHATML_QWEN -> buildQwenChatMl(
                systemPrompt = systemPrompt,
                memoryInjection = memoryInjection,
                history = history,
                currentUser = currentUserMessage
            )

            Style.PHI3_INSTRUCT -> buildPhi3Instruct(
                systemPrompt = systemPrompt,
                memoryInjection = memoryInjection,
                history = history,
                currentUser = currentUserMessage
            )

            Style.GEMMA2_IT -> buildGemma2It(
                systemPrompt = systemPrompt,
                memoryInjection = memoryInjection,
                history = history,
                currentUser = currentUserMessage
            )
        }
    }

    private fun contentsToUserAssistantPairs(contents: List<Content>): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        var pendingUser: String? = null
        for (c in contents) {
            val text = c.parts.firstNotNullOfOrNull { it.text }?.trim().orEmpty()
            if (text.isEmpty()) continue
            when (c.role) {
                "user" -> pendingUser = text
                "model" -> {
                    if (pendingUser != null) {
                        pairs.add(pendingUser!! to text)
                        pendingUser = null
                    }
                }
            }
        }
        return pairs
    }

    private fun buildTinyLlamaBlocks(
        systemPrompt: String,
        memoryInjection: String,
        history: List<Pair<String, String>>,
        currentUser: String,
    ): String = buildString {
        val sys = systemPrompt.trim()
        if (sys.isNotEmpty()) {
            append("### System:\n")
            append(sys)
            append("\n\n")
        }
        val mem = memoryInjection.trim()
        if (mem.isNotEmpty()) {
            append("### Memory (text only, no tools):\n")
            append(mem)
            append("\n\n")
        }
        for ((u, a) in history) {
            append("### User:\n")
            append(u)
            append("\n### Assistant:\n")
            append(a)
            append("\n\n")
        }
        append("### User:\n")
        append(currentUser.trim())
        append("\n### Assistant:\n")
    }

    private fun buildQwenChatMl(
        systemPrompt: String,
        memoryInjection: String,
        history: List<Pair<String, String>>,
        currentUser: String,
    ): String = buildString {
        val sys = systemPrompt.trim()
        val mem = memoryInjection.trim()
        val systemBlock = buildString {
            if (sys.isNotEmpty()) {
                append(sys)
            }
            if (mem.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Memory (text only, no tools):\n")
                append(mem)
            }
        }.trim()
        if (systemBlock.isNotEmpty()) {
            append("<|im_start|>system\n")
            append(systemBlock)
            append("<|im_end|>\n")
        }
        for ((u, a) in history) {
            append("<|im_start|>user\n")
            append(u.trim())
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
            append(a.trim())
            append("<|im_end|>\n")
        }
        append("<|im_start|>user\n")
        append(currentUser.trim())
        append("<|im_end|>\n")
        append("<|im_start|>assistant\n")
    }

    private fun buildPhi3Instruct(
        systemPrompt: String,
        memoryInjection: String,
        history: List<Pair<String, String>>,
        currentUser: String,
    ): String = buildString {
        val sys = systemPrompt.trim()
        val mem = memoryInjection.trim()
        val systemBlock = buildString {
            if (sys.isNotEmpty()) append(sys)
            if (mem.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Memory (text only, no tools):\n")
                append(mem)
            }
        }.trim()
        if (systemBlock.isNotEmpty()) {
            append("<|system|>\n")
            append(systemBlock)
            append("<|end|>\n")
        }
        for ((u, a) in history) {
            append("<|user|>\n")
            append(u.trim())
            append("<|end|>\n")
            append("<|assistant|>\n")
            append(a.trim())
            append("<|end|>\n")
        }
        append("<|user|>\n")
        append(currentUser.trim())
        append("<|end|>\n")
        append("<|assistant|>\n")
    }

    private fun buildGemma2It(
        systemPrompt: String,
        memoryInjection: String,
        history: List<Pair<String, String>>,
        currentUser: String,
    ): String = buildString {
        val sys = systemPrompt.trim()
        val mem = memoryInjection.trim()
        val preamble = buildString {
            if (sys.isNotEmpty()) append(sys)
            if (mem.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Memory (text only, no tools):\n")
                append(mem)
            }
        }.trim()
        for ((u, a) in history) {
            append("<start_of_turn>user\n")
            append(u.trim())
            append("<end_of_turn>\n")
            append("<start_of_turn>model\n")
            append(a.trim())
            append("<end_of_turn>\n")
        }
        val currentBlock = if (preamble.isEmpty()) {
            currentUser.trim()
        } else {
            "$preamble\n\n---\n${currentUser.trim()}"
        }
        append("<start_of_turn>user\n")
        append(currentBlock)
        append("<end_of_turn>\n")
        append("<start_of_turn>model\n")
    }

    /**
     * 本機代理多輪：[buildPrompt] 已結束在 assistant 起頭；接上模型輸出後，插入 User 回合（工具結果）再開新 assistant。
     */
    fun appendAgentToolRound(
        style: Style,
        modelOutput: String,
        toolUserMessage: String,
    ): String {
        val u = toolUserMessage.trim()
        val m = modelOutput.trimEnd()
        return when (style) {
            Style.TINYLLAMA_BLOCKS -> "\n$m\n### User:\n$u\n### Assistant:\n"
            Style.CHATML_QWEN -> "$m\n<|im_end|>\n<|im_start|>user\n$u\n<|im_end|>\n<|im_start|>assistant\n"
            Style.PHI3_INSTRUCT -> "$m\n<|end|>\n<|user|>\n$u\n<|end|>\n<|assistant|>\n"
            Style.GEMMA2_IT -> "$m\n<end_of_turn>\n<start_of_turn>user\n$u\n<end_of_turn>\n<start_of_turn>model\n"
        }
    }
}

