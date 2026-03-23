package com.openring.agent

import android.content.Context
import android.util.Log
import com.openring.gemini.GeminiRestClient
import com.openring.gemini.model.Content
import com.openring.gemini.model.FunctionResponse
import com.openring.gemini.model.GenerateContentRequest
import com.openring.gemini.model.Part
import com.openring.settings.AiPromptStore
import com.openring.skills.SkillInstructionCatalog
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive

class ReActCoordinator(
    private val context: Context,
    private val gemini: GeminiRestClient = GeminiRestClient(),
    private val dispatcher: ToolDispatcher = ToolDispatcher(context),
) {
    private companion object {
        private const val TAG = "OpenRing"
        private val SYSTEM_NOISE_TEXT = setOf(
            "在線", "offline", "online", "typing", "正在輸入", "已讀", "送出", "發送", "搜尋",
            "Search", "Message", "訊息", "Discord", "OpenRing"
        )
    }

    private data class UiTextSignal(
        val key: String,
        val text: String,
        val left: Int,
        val right: Int
    )

    // Sticky continuous mode across multiple run() calls in same coordinator instance.
    private var continuousSessionArmed: Boolean = false

    data class Turn(
        val role: String,
        val text: String? = null,
        val toolName: String? = null,
        val toolResult: JsonObject? = null
    )

    data class RunResult(
        val finalText: String,
        val turns: List<Turn>
    )

    fun run(
        apiKey: String,
        model: String,
        userText: String,
        /** 先前對話輪次（user/model 文字），會置於本次 userText 之前送給模型。 */
        priorContents: List<Content> = emptyList(),
        maxRounds: Int = 30,
        shouldCancel: () -> Boolean = { false },
        onTurn: (Turn) -> Unit = {}
    ): RunResult {
        val tools = ToolSchemas.buildTools(context)
        val baseSystemPrompt = AiPromptStore(context).getSystemPrompt().takeIf { it.isNotBlank() }
        val skillInstructionSection = SkillInstructionCatalog.buildPromptSection(context).takeIf { it.isNotBlank() }
        val systemPrompt = listOfNotNull(baseSystemPrompt, skillInstructionSection).joinToString("\n\n").takeIf { it.isNotBlank() }
        val systemInstruction = systemPrompt?.let { Content(role = "user", parts = listOf(Part(text = it))) }
        val coercedPrior = if (priorContents.size > 24) priorContents.takeLast(24) else priorContents
        val contents = mutableListOf<Content>().apply {
            addAll(coercedPrior)
            add(Content(role = "user", parts = listOf(Part(text = userText))))
        }
        val turns = mutableListOf(Turn(role = "user", text = userText))
        Log.d(
            TAG,
            "ReAct start model=$model maxRounds=$maxRounds userChars=${userText.length} tools=${tools.size} systemPromptChars=${systemPrompt?.length ?: 0}"
        )
        onTurn(turns.last())

        val repeatGuardTools = setOf("find_and_click", "input_text", "input_text_focused")
        val explicitEnableContinuous = userText.contains("持續聊天") ||
            userText.contains("待命") ||
            userText.contains("有回覆") ||
            userText.contains("continue chat", ignoreCase = true)
        val explicitDisableContinuous = userText.contains("停止待命") ||
            userText.contains("停止持續聊天") ||
            userText.contains("停止自動回覆") ||
            userText.contains("stop standby", ignoreCase = true) ||
            userText.contains("stop continuous", ignoreCase = true)
        if (explicitEnableContinuous) continuousSessionArmed = true
        if (explicitDisableContinuous) continuousSessionArmed = false
        val continuousRequested = continuousSessionArmed || explicitEnableContinuous
        val roundLimit = if (continuousRequested) maxRounds.coerceAtLeast(1000) else maxRounds
        var lastToolSignature: String? = null
        var sameToolCount = 0
        var pendingSubmitAfterInput = false
        val inputTools = setOf("input_text", "input_text_focused")
        val submitOrSearchTools = setOf("click_send_button", "find_and_click", "click_node")
        val flowResetTools = setOf("launch_app", "back", "home")
        var consecutiveInputAttempts = 0
        var pendingPostSendFollowUp = false
        var postSendNudgeCount = 0
        var pendingSendVerification = false
        var verifyNudgeCount = 0
        var standbyAfterSend = false
        var standbyScreenFingerprint: String? = null
        var standbyIdleTicks = 0
        var waitingIncomingReply = false
        var incomingReplyDetected = false
        var lastSentText: String? = null
        val standbySeenKeys = mutableSetOf<String>()
        val sentHistory = ArrayDeque<String>()

        var rounds = 0
        while (rounds < roundLimit) {
            if (shouldCancel()) {
                val text = "已中斷本次執行。"
                turns.add(Turn(role = "model", text = text))
                onTurn(turns.last())
                return RunResult(finalText = text, turns = turns.toList())
            }
            rounds++
            val contentParts = contents.sumOf { it.parts.size }
            Log.d(
                TAG,
                "ReAct round=$rounds/$roundLimit phase=request model=$model contents=${contents.size} parts=$contentParts turns=${turns.size}"
            )
            val resp = gemini.generateContent(
                apiKey = apiKey,
                model = model,
                request = GenerateContentRequest(
                    contents = contents.toList(),
                    tools = tools,
                    systemInstruction = systemInstruction
                )
            )

            val modelContent = resp.candidates.firstOrNull()?.content
            if (modelContent != null) {
                contents.add(modelContent)
            }

            val functionCalls = resp.functionCalls()
            if (functionCalls.isEmpty()) {
                if (continuousRequested && waitingIncomingReply) {
                    val scanResult = dispatcher.dispatch("get_view_tree", buildJsonObject {})
                    val root = scanResult.data["root"]
                    val fp = root?.let { UiTreeCompact.fingerprintUiText(it) }
                    val signals = root?.let { collectUiTextSignals(it) } ?: emptyList()
                    val screenWidth = extractScreenWidth(root)
                    if (scanResult.ok && standbySeenKeys.isEmpty() && signals.isNotEmpty()) {
                        standbySeenKeys.addAll(signals.map { it.key })
                    }
                    val newIncoming = signals.filter { signal ->
                        signal.key !in standbySeenKeys &&
                            isLikelyIncomingSignal(signal, sentHistory, screenWidth)
                    }

                    if (scanResult.ok && newIncoming.isNotEmpty()) {
                        incomingReplyDetected = true
                        waitingIncomingReply = false
                        standbyAfterSend = false
                        standbySeenKeys.addAll(signals.map { it.key })
                        standbyIdleTicks = 0
                        standbyScreenFingerprint = fp
                        contents.add(
                            Content(
                                role = "user",
                                parts = listOf(
                                    Part(
                                        text = "New incoming message detected: ${newIncoming.take(3).joinToString(" | ") { it.text }}. Continue the conversation now."
                                    )
                                )
                            )
                        )
                        continue
                    }

                    if (scanResult.ok && fp != null && fp == standbyScreenFingerprint) {
                        standbyIdleTicks += 1
                        val waitMs = if (standbyIdleTicks < 10) 1200L else 2200L
                        Thread.sleep(waitMs)
                        contents.add(
                            Content(
                                role = "user",
                                parts = listOf(Part(text = "No new incoming message yet. Stay standby and keep watching."))
                            )
                        )
                        continue
                    }
                    standbyIdleTicks = 0
                    standbyScreenFingerprint = fp
                    standbySeenKeys.addAll(signals.map { it.key })
                    contents.add(
                        Content(
                            role = "user",
                            parts = listOf(Part(text = "UI changed but no new incoming message detected yet. Stay standby."))
                        )
                    )
                    continue
                }
                if (pendingSendVerification && verifyNudgeCount < 2) {
                    verifyNudgeCount += 1
                    contents.add(
                        Content(
                            role = "user",
                            parts = listOf(
                                Part(
                                    text = "Do not finish yet. You must call verify_send_result after click_send_button and confirm delivery first."
                                )
                            )
                        )
                    )
                    Log.d(TAG, "ReAct round=$rounds/$maxRounds phase=verify_send_nudge nudge=$verifyNudgeCount")
                    continue
                }
                if (pendingPostSendFollowUp && postSendNudgeCount < 2) {
                    postSendNudgeCount += 1
                    contents.add(
                        Content(
                            role = "user",
                            parts = listOf(
                                Part(
                                    text = "Send completed. Continue the task instead of ending now. Refresh the view and decide the next chat action."
                                )
                            )
                        )
                    )
                    Log.d(
                        TAG,
                        "ReAct round=$rounds/$roundLimit phase=post_send_nudge nudge=$postSendNudgeCount"
                    )
                    continue
                }
                val text = resp.firstText() ?: "(no response)"
                Log.d(
                    TAG,
                    "ReAct round=$rounds/$roundLimit phase=finalize reason=no_function_call textChars=${text.length}"
                )
                turns.add(Turn(role = "model", text = text))
                onTurn(turns.last())
                return RunResult(finalText = text, turns = turns.toList())
            }

            val callNames = functionCalls.map { it.name }
            Log.d(
                TAG,
                "ReAct round=$rounds/$roundLimit phase=tool_calls count=${functionCalls.size} names=${callNames.joinToString(",")}"
            )

            for (call in functionCalls) {
                if (shouldCancel()) {
                    val text = "已中斷本次執行。"
                    turns.add(Turn(role = "model", text = text))
                    onTurn(turns.last())
                    return RunResult(finalText = text, turns = turns.toList())
                }
                turns.add(Turn(role = "tool_call", toolName = call.name, toolResult = call.args))
                onTurn(turns.last())
                Log.d(
                    TAG,
                    "ReAct round=$rounds/$roundLimit phase=tool_dispatch tool=${call.name} argsKeys=${call.args.keys.joinToString(",")}"
                )
                val argsPreview = previewToolArgs(call.args)
                if (argsPreview.isNotBlank()) {
                    Log.d(TAG, "ReAct round=$rounds/$roundLimit phase=tool_dispatch_preview tool=${call.name} args=$argsPreview")
                }

                val signature = "${call.name}:${call.args}"
                if (signature == lastToolSignature) {
                    sameToolCount += 1
                } else {
                    lastToolSignature = signature
                    sameToolCount = 1
                }

                val toolResult = if (pendingSubmitAfterInput && call.name in inputTools) {
                    ToolDispatcher.ToolResult(
                        ok = false,
                        code = "FLOW_GUARD",
                        message = "Text already entered. Click send/search before entering more text."
                    )
                } else if (waitingIncomingReply && (call.name in inputTools || call.name == "click_send_button")) {
                    ToolDispatcher.ToolResult(
                        ok = false,
                        code = "WAITING_INCOMING",
                        message = "No incoming reply detected yet. Stay standby and do not send another message."
                    )
                } else if (pendingSendVerification && call.name in inputTools) {
                    ToolDispatcher.ToolResult(
                        ok = false,
                        code = "FLOW_GUARD",
                        message = "Send was attempted. Verify delivery first via verify_send_result before entering more text."
                    )
                } else if (call.name in inputTools && consecutiveInputAttempts >= 2) {
                    ToolDispatcher.ToolResult(
                        ok = false,
                        code = "FLOW_GUARD",
                        message = "Too many consecutive input attempts. Stop re-input loop; click send/search, refresh view tree, or change target first."
                    )
                } else if (call.name in repeatGuardTools && sameToolCount >= 3) {
                    ToolDispatcher.ToolResult(
                        ok = false,
                        code = "REPEAT_GUARD",
                        message = "Repeated identical action blocked. Avoid loops: use exact target match, then click_send_button, or ask for human takeover."
                    )
                } else {
                    dispatcher.dispatch(call.name, call.args)
                }

                // The full view tree payload is huge and can cause Gemini `400 INVALID_ARGUMENT`.
                // For model context we send a compact summary instead of the entire `root`.
                val toolResultForModel = shrinkToolResultForModel(call.name, toolResult)

                if (toolResult.ok && call.name in inputTools) {
                    pendingSubmitAfterInput = true
                    consecutiveInputAttempts = 0
                    val textArg = try {
                        call.args["text"]?.jsonPrimitive?.content
                    } catch (_: Exception) {
                        null
                    }
                    if (!textArg.isNullOrBlank()) lastSentText = textArg
                    if (!textArg.isNullOrBlank()) {
                        sentHistory.addLast(textArg)
                        while (sentHistory.size > 12) sentHistory.removeFirst()
                    }
                } else if (toolResultForModel.ok && (call.name in submitOrSearchTools || call.name in flowResetTools)) {
                    pendingSubmitAfterInput = false
                    consecutiveInputAttempts = 0
                } else if (call.name in inputTools) {
                    consecutiveInputAttempts += 1
                } else if (toolResultForModel.ok) {
                    consecutiveInputAttempts = 0
                }

                if (toolResultForModel.ok && call.name == "click_send_button") {
                    pendingPostSendFollowUp = true
                    postSendNudgeCount = 0
                    pendingSendVerification = true
                    verifyNudgeCount = 0
                } else if (toolResultForModel.ok && call.name in flowResetTools) {
                    pendingPostSendFollowUp = false
                    postSendNudgeCount = 0
                    pendingSendVerification = false
                    verifyNudgeCount = 0
                    standbyAfterSend = false
                    standbyIdleTicks = 0
                    waitingIncomingReply = false
                    incomingReplyDetected = false
                    standbySeenKeys.clear()
                } else if (toolResultForModel.ok && (call.name == "get_view_tree" || call.name == "summarize_view_tree")) {
                    // A successful refresh after send means we can allow natural completion again.
                    pendingPostSendFollowUp = false
                } else if (call.name == "verify_send_result") {
                    if (toolResultForModel.ok) {
                        pendingSendVerification = false
                        verifyNudgeCount = 0
                        if (continuousRequested) {
                            standbyAfterSend = true
                            standbyScreenFingerprint = null
                            standbyIdleTicks = 0
                            waitingIncomingReply = true
                            incomingReplyDetected = false
                            standbySeenKeys.clear()
                        }
                    }
                }
                Log.d(
                    TAG,
                    "ReAct round=$rounds/$roundLimit phase=tool_result tool=${call.name} ok=${toolResultForModel.ok} code=${toolResultForModel.code ?: "null"} message=${toolResultForModel.message ?: "null"} dataKeys=${toolResultForModel.data.keys.joinToString(",")}"
                )
                val functionResponsePart = Part(
                    functionResponse = FunctionResponse(
                        name = call.name,
                        response = toolResultForModel.toJsonObject(),
                        id = call.id
                    )
                )
                contents.add(Content(role = "user", parts = listOf(functionResponsePart)))
                turns.add(Turn(role = "tool_result", toolName = call.name, toolResult = toolResultForModel.toJsonObject()))
                onTurn(turns.last())
            }
        }

        val finalText = "已達最大回合數（$roundLimit），請縮小目標或使用人類接管。"
        Log.w(
            TAG,
            "ReAct stop reason=max_rounds maxRounds=$roundLimit turns=${turns.size} contents=${contents.size}"
        )
        turns.add(Turn(role = "model", text = finalText))
        onTurn(turns.last())
        return RunResult(
            finalText = "已達最大回合數（$roundLimit），請縮小目標或使用人類接管。",
            turns = turns.toList()
        )
    }

    private fun collectUiTextSignals(root: JsonElement): List<UiTextSignal> {
        val out = mutableListOf<UiTextSignal>()
        collectUiTextSignalsRec(root, out)
        return out.distinctBy { it.key }
    }

    private fun collectUiTextSignalsRec(element: JsonElement, out: MutableList<UiTextSignal>) {
        when (element) {
            is JsonObject -> {
                val rawText = element["text"]?.toString()?.trim('"').orEmpty()
                val rawDesc = element["contentDesc"]?.toString()?.trim('"').orEmpty()
                val text = listOf(rawText, rawDesc).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                if (text.isNotBlank()) {
                    val bounds = element["bounds"] as? JsonObject
                    val left = bounds?.get("left")?.toString()?.toIntOrNull() ?: -1
                    val right = bounds?.get("right")?.toString()?.toIntOrNull() ?: -1
                    val key = "$text@$left:$right"
                    out.add(UiTextSignal(key = key, text = text, left = left, right = right))
                }
                element["children"]?.let { collectUiTextSignalsRec(it, out) }
            }
            is JsonArray -> element.forEach { collectUiTextSignalsRec(it, out) }
            else -> Unit
        }
    }

    private fun extractScreenWidth(root: JsonElement?): Int {
        val obj = root as? JsonObject ?: return 1080
        val bounds = obj["bounds"] as? JsonObject ?: return 1080
        val right = bounds["right"]?.toString()?.toIntOrNull() ?: 1080
        val left = bounds["left"]?.toString()?.toIntOrNull() ?: 0
        return (right - left).coerceAtLeast(720)
    }

    private fun isLikelyIncomingSignal(
        signal: UiTextSignal,
        sentHistory: ArrayDeque<String>,
        screenWidth: Int
    ): Boolean {
        val t = signal.text.trim()
        if (t.length < 2) return false
        if (SYSTEM_NOISE_TEXT.any { t.equals(it, ignoreCase = true) || t.contains(it, ignoreCase = true) }) return false
        if (sentHistory.any { isSimilarMessage(t, it) }) return false

        // Heuristic for chat bubbles: incoming usually appears on the left side.
        if (signal.left >= 0 && signal.left > (screenWidth * 0.55).toInt()) return false
        return true
    }

    private fun isSimilarMessage(a: String, b: String): Boolean {
        if (a.equals(b, ignoreCase = true)) return true
        val x = a.take(18)
        val y = b.take(18)
        return x.contains(y, ignoreCase = true) || y.contains(x, ignoreCase = true)
    }

    private fun previewToolArgs(args: JsonObject, maxChars: Int = 800): String {
        if (args.isEmpty()) return ""
        val entries = args.entries.take(8)
        val head = entries.joinToString(",") { (k, v) ->
            "$k=${previewJsonPrimitiveOrComplex(v, maxCharsPerValue = 120)}"
        }
        val raw = if (args.size <= 8) "{$head}" else "{$head,...(+${args.size - 8})}"
        return if (raw.length <= maxChars) raw else raw.take(maxChars) + "..."
    }

    private fun previewJsonPrimitiveOrComplex(value: JsonElement, maxCharsPerValue: Int): String {
        return when (value) {
            is JsonPrimitive -> {
                val oneLine = value.content.replace('\n', ' ').replace('\r', ' ').trim()
                val trimmed = if (oneLine.length <= maxCharsPerValue) oneLine else oneLine.take(maxCharsPerValue) + "..."
                "\"$trimmed\""
            }
            else -> "<complex>"
        }
    }

}

