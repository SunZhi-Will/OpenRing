package com.openring.domain

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.openring.core.ActionExecutor
import com.openring.core.IntentRouter
import com.openring.core.OpenRingAccessibilityService
import com.openring.core.OverlayService
import com.openring.core.ViewNodeUtils
import com.openring.core.model.ActionResult
import com.openring.core.model.ViewNode
import com.openring.agent.ReActCoordinator
import com.openring.agent.RunCancellationRegistry
import com.openring.data.ChatRepository
import com.openring.data.dao.ExecutionHistoryDao
import com.openring.data.model.ExecutionRecord
import com.openring.data.model.Script
import com.openring.data.model.ScriptStep
import com.openring.ui.notifications.AiRunNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 腳本執行引擎 — 依 steps 依序執行動作
 * US-2.2: 支援 launch_app, wait, find_and_click, click_node, swipe, back, home, extract_text
 */
class ScriptExecutor(
    private val context: Context,
    private val executionHistoryDao: ExecutionHistoryDao
) {

    private val json = Json { ignoreUnknownKeys = true }

    sealed class ExecutionResult {
        data class Success(val variables: Map<String, String>) : ExecutionResult()
        data class Failure(val stepIndex: Int, val error: String) : ExecutionResult()
    }

    suspend fun execute(script: Script, onStepComplete: ((Int, String) -> Unit)? = null): ExecutionResult {
        Log.d("OpenRing", "ScriptExecutor: 開始解析腳本 stepsJson=${script.stepsJson.take(200)}...")
        val steps = parseSteps(script.stepsJson)
        Log.d("OpenRing", "ScriptExecutor: 解析到 ${steps.size} 個步驟")
        if (steps.isEmpty()) {
            Log.w("OpenRing", "ScriptExecutor: 無步驟可執行")
            recordExecution(script, success = true)
            return ExecutionResult.Success(emptyMap())
        }

        val needsAccessibility = steps.any { stepRequiresAccessibilityService(it) }
        val service = OpenRingAccessibilityService.getInstance()
        if (needsAccessibility && service == null) {
            Log.e("OpenRing", "ScriptExecutor: AccessibilityService 未啟用，無法執行")
            return ExecutionResult.Failure(-1, "AccessibilityService 未啟用")
        }

        val appCtx = context.applicationContext
        val runSessionId = startScriptRunUi(appCtx, steps)
        try {
            val actionExecutor = service?.getActionExecutor()
            val intentRouter = IntentRouter(context)
            val variables = mutableMapOf<String, String>()

            for ((index, step) in steps.withIndex()) {
                if (RunCancellationRegistry.isCancelled(runSessionId)) {
                    Log.w("OpenRing", "ScriptExecutor: 使用者中斷 scriptId=${script.id}")
                    recordExecution(script, success = false, errorMessage = "使用者已中斷")
                    return ExecutionResult.Failure(index, "使用者已中斷")
                }
                Log.d("OpenRing", "ScriptExecutor: 步驟 ${index + 1}/${steps.size} type=${step.type} params=${step.params}")
                onStepComplete?.invoke(index, step.type)
                val result = executeStep(script, step, service, actionExecutor, intentRouter, variables, runSessionId)
                if (result != null) {
                    Log.e("OpenRing", "ScriptExecutor: 步驟 ${index + 1} 失敗: $result")
                    recordExecution(script, success = false, errorMessage = result)
                    return ExecutionResult.Failure(index, result)
                }
                Log.d("OpenRing", "ScriptExecutor: 步驟 ${index + 1} 完成")
            }

            Log.d("OpenRing", "ScriptExecutor: 全部步驟執行成功")
            recordExecution(script, success = true)
            return ExecutionResult.Success(variables.toMap())
        } finally {
            stopScriptRunUi(appCtx, runSessionId)
        }
    }

    /**
     * 手動／排程／常駐觸發腳本時，顯示與聊天室 AI 相同的中斷機制：通知（含 ai 步驟時）＋懸浮鈕（有權限時）。
     */
    private fun startScriptRunUi(appCtx: Context, steps: List<ScriptStep>): String {
        val id = java.util.UUID.randomUUID().toString()
        RunCancellationRegistry.register(id)
        if (steps.any { it.type == "ai_action" }) {
            AiRunNotification.show(appCtx, id)
        }
        if (Settings.canDrawOverlays(appCtx)) {
            try {
                val intent = Intent(appCtx, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_START_AI_RUN
                    putExtra(OverlayService.EXTRA_SESSION_ID, id)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appCtx.startForegroundService(intent)
                } else {
                    appCtx.startService(intent)
                }
            } catch (e: Exception) {
                Log.w("OpenRing", "ScriptExecutor: OverlayService 啟動失敗", e)
            }
        }
        return id
    }

    private fun stopScriptRunUi(appCtx: Context, runSessionId: String) {
        RunCancellationRegistry.clear(runSessionId)
        AiRunNotification.cancel(appCtx)
        try {
            appCtx.startService(
                Intent(appCtx, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_STOP_AI_RUN
                }
            )
        } catch (_: Exception) {
            try {
                appCtx.stopService(Intent(appCtx, OverlayService::class.java))
            } catch (_: Exception) {
            }
        }
    }

    /** 僅 ai_action / wait / launch_app 可在無無障礙服務時執行（背景排程常見）。 */
    private fun stepRequiresAccessibilityService(step: ScriptStep): Boolean = when (step.type) {
        "ai_action", "wait", "launch_app" -> false
        else -> true
    }

    private suspend fun executeStep(
        script: Script,
        step: ScriptStep,
        service: OpenRingAccessibilityService?,
        actionExecutor: ActionExecutor?,
        intentRouter: IntentRouter,
        variables: MutableMap<String, String>,
        runSessionId: String
    ): String? {
        val params = step.params
        return when (step.type) {
            "launch_app" -> {
                val pkg = params["package"]?.takeIf { it.isNotBlank() && it != "__custom__" }
                    ?: return "Missing package"
                val uri = params["uri"]
                Log.d("OpenRing", "ScriptExecutor: launch_app pkg=$pkg uri=$uri")
                when (val r = intentRouter.launchApp(pkg, uri)) {
                    is ActionResult.Ok -> {
                        Log.d("OpenRing", "ScriptExecutor: launch_app 成功")
                        null
                    }
                    is ActionResult.Error -> {
                        Log.e("OpenRing", "ScriptExecutor: launch_app 失敗 ${r.code}: ${r.message}")
                        "${r.code}: ${r.message}"
                    }
                }
            }
            "wait" -> {
                val ms = params["ms"]?.toIntOrNull() ?: 1000
                Log.d("OpenRing", "ScriptExecutor: wait ${ms}ms")
                delay(ms.toLong())
                null
            }
            "find_and_click" -> {
                val s = service ?: return "AccessibilityService 未啟用"
                val ae = actionExecutor ?: return "AccessibilityService 未啟用"
                val tree = s.getViewTree() ?: return "NODE_NOT_FOUND: Cannot get view tree"
                val text = params["text"]
                val match = params["match"] ?: "contains"
                val nodeId = params["nodeId"]
                when {
                    nodeId != null -> when (val r = ae.clickByNodeId(nodeId, tree)) {
                        is ActionResult.Ok -> null
                        is ActionResult.Error -> "${r.code}: ${r.message}"
                    }
                    text != null -> when (val r = ae.clickByText(text, match, tree)) {
                        is ActionResult.Ok -> null
                        is ActionResult.Error -> "${r.code}: ${r.message}"
                    }
                    else -> "Missing text or nodeId"
                }
            }
            "click_node" -> {
                val s = service ?: return "AccessibilityService 未啟用"
                val ae = actionExecutor ?: return "AccessibilityService 未啟用"
                val nodeId = params["nodeId"] ?: return "Missing nodeId"
                val tree = s.getViewTree() ?: return "NODE_NOT_FOUND"
                when (val r = ae.clickByNodeId(nodeId, tree)) {
                    is ActionResult.Ok -> null
                    is ActionResult.Error -> "${r.code}: ${r.message}"
                }
            }
            "swipe" -> {
                val ae = actionExecutor ?: return "AccessibilityService 未啟用"
                val direction = params["direction"] ?: return "Missing direction"
                val distance = params["distance"]?.toIntOrNull() ?: 300
                Log.d("OpenRing", "ScriptExecutor: swipe direction=$direction distance=$distance")
                when (val r = ae.swipe(direction, distance)) {
                    is ActionResult.Ok -> null
                    is ActionResult.Error -> "${r.code}: ${r.message}"
                }
            }
            "long_press" -> {
                val s = service ?: return "AccessibilityService 未啟用"
                val ae = actionExecutor ?: return "AccessibilityService 未啟用"
                val tree = s.getViewTree() ?: return "NODE_NOT_FOUND"
                val nodeId = params["nodeId"]
                val text = params["text"]
                when (val r = ae.longPress(tree, nodeId, text)) {
                    is ActionResult.Ok -> null
                    is ActionResult.Error -> "${r.code}: ${r.message}"
                }
            }
            "back" -> {
                val ae = actionExecutor ?: return "AccessibilityService 未啟用"
                when (val r = ae.back()) {
                    is ActionResult.Ok -> null
                    is ActionResult.Error -> "${r.code}: ${r.message}"
                }
            }
            "home" -> {
                val ae = actionExecutor ?: return "AccessibilityService 未啟用"
                when (val r = ae.home()) {
                    is ActionResult.Ok -> null
                    is ActionResult.Error -> "${r.code}: ${r.message}"
                }
            }
            "extract_text" -> {
                val s = service ?: return "AccessibilityService 未啟用"
                val nodeId = params["nodeId"] ?: return "Missing nodeId"
                val variable = params["variable"] ?: return "Missing variable"
                val tree = s.getViewTree() ?: return "NODE_NOT_FOUND"
                val text = ViewNodeUtils.extractText(tree, nodeId)
                if (text != null) {
                    variables[variable] = text
                    null
                } else {
                    "NODE_NOT_FOUND: Cannot extract text from $nodeId"
                }
            }
            "ai_action" -> {
                val prompt = params["prompt"] ?: return "Missing prompt"
                val keyStore = com.openring.security.ApiKeyStore(context)
                val modelStore = com.openring.settings.ModelStore(context)
                var lastError: String? = "No valid API key or model available"
                var success = false
                var runResult: ReActCoordinator.RunResult? = null
                val coordinator = ReActCoordinator(context)
                for (opt in modelStore.getModels()) {
                    val key = keyStore.getGeminiApiKeyForModel(opt.id)
                    if (key.isNullOrBlank() || opt.provider != "gemini") continue
                    try {
                        runResult = coordinator.run(
                            apiKey = key,
                            model = opt.model,
                            userText = prompt,
                            shouldCancel = { RunCancellationRegistry.isCancelled(runSessionId) },
                            onTurn = { }
                        )
                        success = true
                        break
                    } catch (e: Exception) {
                        lastError = e.message
                    }
                }
                if (!success) {
                    lastError
                } else {
                    runResult?.let { appendScheduledAiToChat(script, prompt, it.finalText) }
                    null
                }
            }
            else -> "Unknown step type: ${step.type}"
        }
    }

    private fun parseSteps(stepsJson: String): List<ScriptStep> {
        return try {
            json.decodeFromString<List<ScriptStep>>(stepsJson)
        } catch (e: Exception) {
            try {
                val arr = json.parseToJsonElement(stepsJson).jsonArray
                arr.map { item ->
                    val obj = item.jsonObject
                    ScriptStep(
                        type = obj["type"]?.jsonPrimitive?.content ?: "",
                        params = obj["params"]?.jsonObject?.entries?.associate { (k, v) ->
                            k to (v.jsonPrimitive.content)
                        } ?: emptyMap()
                    )
                }
            } catch (e2: Exception) {
                emptyList()
            }
        }
    }

    private suspend fun recordExecution(script: Script, success: Boolean, errorMessage: String? = null) {
        withContext(Dispatchers.IO) {
            executionHistoryDao.insert(
                ExecutionRecord(
                    scriptId = script.id,
                    scriptName = script.name,
                    success = success,
                    errorMessage = errorMessage
                )
            )
        }
    }

    /**
     * 排程或腳本中的 ai_action 完成後寫入目前作用中 Chat 工作階段，讓使用者在對話中可讀到結果。
     */
    private suspend fun appendScheduledAiToChat(script: Script, prompt: String, finalText: String) {
        withContext(Dispatchers.IO) {
            try {
                val repo = ChatRepository(context.applicationContext)
                val sessionId = repo.getOrCreateActiveSessionId()
                val idBase = java.util.UUID.randomUUID().toString()
                repo.addUserMessage(
                    sessionId,
                    "${idBase}_sched_user",
                    "[排程：${script.name}]\n$prompt"
                )
                val body = finalText.trim().ifEmpty { "（本次無文字回覆）" }
                repo.addModelMessage(sessionId, "${idBase}_sched_model", body)
            } catch (e: Exception) {
                Log.e("OpenRing", "ScriptExecutor: 寫入 Chat 失敗 scriptId=${script.id}", e)
            }
        }
    }
}
