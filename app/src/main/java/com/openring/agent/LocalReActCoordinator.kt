package com.openring.agent

import android.content.Context
import android.util.Log
import com.openring.BuildConfig
import com.openring.gemini.model.Content
import com.openring.localmodel.LocalLlmChatPrompt
import com.openring.localmodel.LocalLlmEngine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * 本機 GGUF 的簡化 ReAct：模型以 **單一 JSON** 回覆 `final` 或 `tool_calls`，由 app 執行 [ToolDispatcher] 並把結果接回 prompt。
 * 不含 Gemini 路徑的待命／連續聊天狀態機；小模型 JSON 可能不穩定，失敗時會退回純文字當作結束。
 */
class LocalReActCoordinator(
    private val context: Context,
    private val dispatcher: ToolDispatcher = ToolDispatcher(context),
) {
    companion object {
        private const val TAG = "OpenRing"

        fun buildAgentPreamble(toolCatalog: String): String = buildString {
            append(
                """
You control this Android device via accessibility tools. Reply with ONE JSON object only (no markdown fences, no prose before/after).
- {"final":"..."} when you are done or no tool is needed.
- {"tool_calls":[{"name":"exact_tool_name","arguments":{}}]} to act. arguments must be an object (use {} if empty).

Prefer summarize_view_tree or get_view_tree before tapping. Use exact tool names from the list below.

Tools:
                """.trimIndent()
            )
            append('\n')
            append(toolCatalog.trim())
        }
    }

    private val compactJson = Json { encodeDefaults = false; prettyPrint = false }

    suspend fun run(
        catalogId: String,
        userText: String,
        priorContents: List<Content>,
        systemPrompt: String,
        memoryInjection: String,
        toolCatalogText: String,
        maxRounds: Int = 14,
        shouldCancel: () -> Boolean = { false },
        onTurn: (ReActCoordinator.Turn) -> Unit = {},
        onStatus: (String) -> Unit = {},
    ): ReActCoordinator.RunResult {
        val style = LocalLlmChatPrompt.styleForCatalogId(catalogId)
        val agentBlock = buildAgentPreamble(toolCatalogText)
        val mergedSystem = buildString {
            val s = systemPrompt.trim()
            if (s.isNotEmpty()) {
                append(s)
                append("\n\n")
            }
            append(agentBlock)
        }.trim()

        var promptPrefix = LocalLlmChatPrompt.buildPrompt(
            style = style,
            systemPrompt = mergedSystem,
            memoryInjection = memoryInjection,
            priorContents = priorContents,
            currentUserMessage = userText,
        )

        val turns = mutableListOf<ReActCoordinator.Turn>()
        turns.add(ReActCoordinator.Turn(role = "user", text = userText))
        onTurn(turns.last())

        var lastToolSig: String? = null
        var sameToolCount = 0
        val repeatGuardTools = setOf("find_and_click", "input_text", "input_text_focused")

        var round = 0
        while (round < maxRounds) {
            if (shouldCancel()) {
                val t = "已中斷本次執行。"
                turns.add(ReActCoordinator.Turn(role = "model", text = t))
                onTurn(turns.last())
                return ReActCoordinator.RunResult(finalText = t, turns = turns.toList())
            }
            round++
            onStatus("本機代理 回合 $round…")
            Log.d(TAG, "LocalReAct round=$round/$maxRounds promptChars=${promptPrefix.length}")

            val raw = LocalLlmEngine.generate(
                context = context,
                catalogId = catalogId,
                prompt = promptPrefix,
                isCancelled = shouldCancel,
            )
            if (shouldCancel()) {
                val t = "已中斷本次執行。"
                turns.add(ReActCoordinator.Turn(role = "model", text = t))
                onTurn(turns.last())
                return ReActCoordinator.RunResult(finalText = t, turns = turns.toList())
            }

            val parsed = parseLocalAgentModelOutput(raw)
            val calls = parsed.toolCalls
            val finalOnly = parsed.finalText?.takeIf { it.isNotBlank() }

            if (BuildConfig.DEBUG) {
                val mode = when {
                    calls.isNotEmpty() ->
                        "tool_calls=${calls.size}:" + calls.joinToString(",") { it.first }
                    finalOnly != null -> "final(len=${finalOnly.length})"
                    else -> "fallback_raw(len=${raw.length})"
                }
                val head = raw.trim().replace("\n", " ").take(180)
                Log.d(TAG, "LocalReAct parse round=$round $mode rawHead=$head")
            }

            if (calls.isEmpty()) {
                val text = finalOnly ?: raw.trim().ifBlank { "（本機模型未產生內容）" }
                turns.add(ReActCoordinator.Turn(role = "model", text = text))
                onTurn(turns.last())
                return ReActCoordinator.RunResult(finalText = text, turns = turns.toList())
            }

            val toolResultBlocks = StringBuilder()
            for ((name, args) in calls) {
                if (shouldCancel()) break
                turns.add(ReActCoordinator.Turn(role = "tool_call", toolName = name, toolResult = args))
                onTurn(turns.last())
                onStatus("本機代理：$name")

                val sig = "$name:$args"
                if (sig == lastToolSig) {
                    sameToolCount++
                } else {
                    lastToolSig = sig
                    sameToolCount = 1
                }

                val toolResult = if (name in repeatGuardTools && sameToolCount >= 3) {
                    ToolDispatcher.ToolResult(
                        ok = false,
                        code = "REPEAT_GUARD",
                        message = "Repeated identical action blocked.",
                    )
                } else {
                    dispatcher.dispatch(name, args)
                }

                val forModel = shrinkToolResultForModel(name, toolResult)
                turns.add(
                    ReActCoordinator.Turn(
                        role = "tool_result",
                        toolName = name,
                        toolResult = forModel.toJsonObject(),
                    ),
                )
                onTurn(turns.last())

                toolResultBlocks.append("[tool ").append(name).append("]\n")
                toolResultBlocks.append(compactJson.encodeToString(JsonObject.serializer(), forModel.toJsonObject()))
                toolResultBlocks.append("\n\n")
            }

            val userFollowUp = buildString {
                append("Tool result(s) (JSON). Continue with the next JSON object only.\n\n")
                append(toolResultBlocks)
            }.trim()

            promptPrefix = promptPrefix + LocalLlmChatPrompt.appendAgentToolRound(style, raw, userFollowUp)
        }

        val end = "已達本機代理最大回合數（$maxRounds）。"
        turns.add(ReActCoordinator.Turn(role = "model", text = end))
        onTurn(turns.last())
        return ReActCoordinator.RunResult(finalText = end, turns = turns.toList())
    }
}
