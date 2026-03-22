package com.openring.agent

import android.content.Context
import com.openring.gemini.GeminiRestClient
import com.openring.core.InstalledAppsProvider
import com.openring.core.IntentRouter
import com.openring.core.OpenRingAccessibilityService
import com.openring.core.ViewNodeUtils
import com.openring.core.model.ActionResult
import com.openring.core.model.ErrorCode
import com.openring.core.model.ViewNode
import com.openring.data.MemoryRepository
import com.openring.data.ScriptStore
import com.openring.data.db.OpenRingDatabase
import com.openring.data.model.Schedule
import com.openring.domain.Scheduler
import com.openring.settings.AiPromptStore
import com.openring.settings.ScanCache
import com.openring.skills.InstalledSkillStore
import com.openring.skills.SkillAllowedSourcesStore
import com.openring.skills.SkillEnabledStore
import com.openring.skills.SkillInstall
import com.openring.skills.SkillQuickJsExecutor
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.put

class ToolDispatcher(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val geminiRest = GeminiRestClient()

    companion object {
        private const val DISCORD_PACKAGE = "com.discord"
    }

    private var lastInputText: String? = null
    private var pendingVerifyText: String? = null

    data class ToolResult(
        val ok: Boolean,
        val code: String? = null,
        val message: String? = null,
        val data: JsonObject = buildJsonObject { }
    ) {
        fun toJsonObject(): JsonObject = buildJsonObject {
            put("ok", ok)
            put("code", code)
            put("message", message)
            put("data", data)
        }
    }

    fun dispatch(name: String, args: JsonObject): ToolResult {
        when {
            name == "call_skill" -> {
                val skillId = args["skill"]?.jsonPrimitive?.content
                    ?: return ToolResult(false, "INVALID_ARGUMENT", "Missing skill")
                val input = args["input"]?.jsonObject ?: buildJsonObject { }
                return executeSkill(skillId, input)
            }

            name.startsWith("skill_") -> {
                val skillId = name.removePrefix("skill_")
                return executeSkill(skillId, args)
            }

            name.startsWith("memory_") -> {
                return dispatchMemoryTool(name, args)
            }
        }

        val service = OpenRingAccessibilityService.getInstance()
            ?: return ToolResult(false, ErrorCode.PERMISSION_DENIED.name, "AccessibilityService 未啟用")

        val actionExecutor = service.getActionExecutor()
        val intentRouter = IntentRouter(context)
        val currentPackage = service.rootInActiveWindow?.packageName?.toString().orEmpty()
        val inDiscord = currentPackage == DISCORD_PACKAGE

        return when (name) {
            "get_installed_apps" -> {
                val apps = InstalledAppsProvider.getInstalledLauncherApps(context)
                val data = buildJsonObject {
                    putJsonArray("apps") {
                        apps.forEach { (displayName, packageName) ->
                            add(buildJsonObject {
                                put("displayName", displayName)
                                put("packageName", packageName)
                            })
                        }
                    }
                    put("count", apps.size)
                }
                ToolResult(true, data = data)
            }

            "get_view_tree" -> {
                val tree = service.getViewTree()
                    ?: return ToolResult(false, ErrorCode.ACTION_FAILED.name, "Cannot get view tree")
                val data = buildJsonObject {
                    put("timestampMs", System.currentTimeMillis())
                    put("root", viewNodeToJson(tree))
                }
                ScanCache(context).setLastScan(data)
                ToolResult(true, data = data)
            }

            "get_cached_scan" -> {
                val cached = ScanCache(context).getLastScan()
                if (cached == null) {
                    ToolResult(false, "NO_CACHED_SCAN", "No cached scan available. Enable auto-scan or call get_view_tree first.")
                } else {
                    ToolResult(true, data = cached.second)
                }
            }

            "summarize_view_tree" -> {
                val tree = service.getViewTree()
                    ?: return ToolResult(false, ErrorCode.ACTION_FAILED.name, "Cannot get view tree")
                val fullData = buildJsonObject {
                    put("timestampMs", System.currentTimeMillis())
                    put("root", viewNodeToJson(tree))
                }
                ScanCache(context).setLastScan(fullData)
                val compact = UiTreeCompact.compactViewTreeData(fullData)
                    ?: return ToolResult(false, "COMPACT_FAILED", "Could not build UI summary")
                val data = buildJsonObject {
                    put("format", "compact_ui_summary")
                    compact.forEach { (k, v) -> put(k, v) }
                }
                ToolResult(true, data = data)
            }

            "describe_screen" -> {
                val apiKey = ActiveChatContext.geminiApiKey
                if (apiKey.isNullOrBlank()) {
                    return ToolResult(
                        false,
                        "NO_API_KEY",
                        "Gemini API key required for describe_screen. Configure a Gemini model with a key in Settings."
                    )
                }
                val model = ActiveChatContext.geminiModel?.trim().orEmpty().ifBlank { "gemini-2.0-flash" }
                val question = args["question"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank {
                    "You are helping an Android UI automation agent. Describe the visible screen briefly: list major interactive controls (buttons, fields) and any readable text that helps choose tap targets. Be concise."
                }
                val jpeg = com.openring.core.ScreenCapture.captureJpegBase64(service)
                    ?: return ToolResult(
                        false,
                        "SCREENSHOT_UNAVAILABLE",
                        "Screen capture requires Android 11+ (API 30) and AccessibilityService. Otherwise rely on get_view_tree."
                    )
                return try {
                    val text = geminiRest.describeScreenWithVision(apiKey, model, jpeg, question)
                    ToolResult(
                        true,
                        data = buildJsonObject {
                            put("description", text.take(12000))
                            put("visionModel", model)
                        }
                    )
                } catch (e: Exception) {
                    ToolResult(false, "VISION_FAILED", e.message?.take(500) ?: "Vision call failed")
                }
            }

            "find_and_click" -> {
                val text = args["text"]?.jsonPrimitive?.content
                    ?: return ToolResult(false, "INVALID_ARGUMENT", "Missing text")
                val requestedMatch = args["match"]?.jsonPrimitive?.content ?: "contains"
                val tree = service.getViewTree()
                    ?: return ToolResult(false, ErrorCode.NODE_NOT_FOUND.name, "Cannot get view tree")

                var effectiveMatch = requestedMatch
                if (inDiscord) {
                    val exactMatches = findNodesByText(tree, text, exact = true)
                    if (exactMatches.size > 1) {
                        return ToolResult(
                            false,
                            "DISCORD_AMBIGUOUS_TARGET",
                            "Exact label '$text' appears ${exactMatches.size} times. Refine target or open chat first."
                        )
                    }
                    if (exactMatches.isEmpty()) {
                        val containsMatches = findNodesByText(tree, text, exact = false)
                        if (containsMatches.isEmpty()) {
                            return ToolResult(false, ErrorCode.NODE_NOT_FOUND.name, "No match found for '$text' in current Discord view")
                        }
                        if (containsMatches.size > 1) {
                            return ToolResult(
                                false,
                                "DISCORD_AMBIGUOUS_TARGET",
                                "Text '$text' matches ${containsMatches.size} nodes in Discord. Please provide a more specific target."
                            )
                        }
                        effectiveMatch = "contains"
                    } else {
                        effectiveMatch = "exact"
                    }
                }
                when (val r = actionExecutor.clickByText(text, effectiveMatch, tree)) {
                    is ActionResult.Ok -> ToolResult(
                        true,
                        data = buildJsonObject { put("clickedNodeId", "") }
                    )

                    is ActionResult.Error -> ToolResult(false, r.code.name, r.message)
                }
            }

            "click_node" -> {
                val nodeId = args["nodeId"]?.jsonPrimitive?.content
                    ?: return ToolResult(false, "INVALID_ARGUMENT", "Missing nodeId")
                val tree = service.getViewTree()
                    ?: return ToolResult(false, ErrorCode.NODE_NOT_FOUND.name, "Cannot get view tree")

                if (inDiscord) {
                    val node = ViewNodeUtils.findNodeById(tree, nodeId)
                        ?: return ToolResult(false, ErrorCode.NODE_NOT_FOUND.name, "Unknown nodeId in current view tree")
                    val label = (node.text ?: node.contentDesc ?: "").trim()
                    if (label.isBlank()) {
                        return ToolResult(
                            false,
                            "DISCORD_PRECISION_REQUIRED",
                            "In Discord, click_node requires a node with visible text/contentDesc to reduce misclick risk."
                        )
                    }
                }
                when (val r = actionExecutor.clickByNodeId(nodeId, tree)) {
                    is ActionResult.Ok -> ToolResult(true, data = buildJsonObject { put("clickedNodeId", nodeId) })
                    is ActionResult.Error -> ToolResult(false, r.code.name, r.message)
                }
            }

            "swipe" -> {
                val direction = args["direction"]?.jsonPrimitive?.content
                    ?: return ToolResult(false, "INVALID_ARGUMENT", "Missing direction")
                val distance = args["distance"]?.jsonPrimitive?.content?.toIntOrNull() ?: 300
                when (val r = actionExecutor.swipe(direction, distance)) {
                    is ActionResult.Ok -> ToolResult(true, data = buildJsonObject {
                        put("direction", direction)
                        put("distance", distance)
                    })

                    is ActionResult.Error -> ToolResult(false, r.code.name, r.message)
                }
            }

            "back" -> when (val r = actionExecutor.back()) {
                is ActionResult.Ok -> ToolResult(true)
                is ActionResult.Error -> ToolResult(false, r.code.name, r.message)
            }

            "home" -> when (val r = actionExecutor.home()) {
                is ActionResult.Ok -> ToolResult(true)
                is ActionResult.Error -> ToolResult(false, r.code.name, r.message)
            }

            "input_text" -> {
                val nodeId = args["nodeId"]?.jsonPrimitive?.content
                    ?: return ToolResult(false, "INVALID_ARGUMENT", "Missing nodeId")
                val text = args["text"]?.jsonPrimitive?.content
                    ?: return ToolResult(false, "INVALID_ARGUMENT", "Missing text")
                val tree = service.getViewTree()
                    ?: return ToolResult(false, ErrorCode.NODE_NOT_FOUND.name, "Cannot get view tree")
                when (val r = actionExecutor.inputTextByNodeId(nodeId, text, tree)) {
                    is ActionResult.Ok -> {
                        lastInputText = text
                        ToolResult(true, data = buildJsonObject { put("nodeId", nodeId) })
                    }
                    is ActionResult.Error -> ToolResult(false, r.code.name, r.message)
                }
            }

            "input_text_focused" -> {
                val text = args["text"]?.jsonPrimitive?.content
                    ?: return ToolResult(false, "INVALID_ARGUMENT", "Missing text")
                when (val r = actionExecutor.inputTextToFocused(text)) {
                    is ActionResult.Ok -> {
                        lastInputText = text
                        ToolResult(true)
                    }
                    is ActionResult.Error -> ToolResult(false, r.code.name, r.message)
                }
            }

            "click_send_button" -> {
                val tree = service.getViewTree()
                    ?: return ToolResult(false, ErrorCode.NODE_NOT_FOUND.name, "Cannot get view tree")
                val candidates = listOf("Send Message", "Send", "發送", "送出", "傳送")

                for (label in candidates) {
                    when (val r = actionExecutor.clickByText(label, "exact", tree)) {
                        is ActionResult.Ok -> {
                            pendingVerifyText = lastInputText
                            return ToolResult(true, data = buildJsonObject { put("matched", label) })
                        }
                        is ActionResult.Error -> Unit
                    }
                }
                for (label in candidates) {
                    when (val r = actionExecutor.clickByText(label, "contains", tree)) {
                        is ActionResult.Ok -> {
                            pendingVerifyText = lastInputText
                            return ToolResult(true, data = buildJsonObject { put("matched", label) })
                        }
                        is ActionResult.Error -> Unit
                    }
                }
                ToolResult(false, ErrorCode.NODE_NOT_FOUND.name, "Send button not found")
            }

            "verify_send_result" -> {
                val tree = service.getViewTree()
                    ?: return ToolResult(false, ErrorCode.NODE_NOT_FOUND.name, "Cannot get view tree")
                val expected = pendingVerifyText?.trim().orEmpty()
                val editableTexts = collectEditableTexts(tree)

                if (expected.isNotBlank()) {
                    val stillInInput = editableTexts.any { it.contains(expected, ignoreCase = true) }
                    if (stillInInput) {
                        return ToolResult(false, "SEND_NOT_CONFIRMED", "Message still appears in input box; likely not sent.")
                    }
                    val onScreen = containsText(tree, expected)
                    if (onScreen) {
                        pendingVerifyText = null
                        return ToolResult(true, data = buildJsonObject {
                            put("confirmed", true)
                            put("reason", "Expected text found on screen after send.")
                        })
                    }
                }

                // Fallback: if all editable fields are empty, likely sent (useful for emoji / short messages).
                val hasDraft = editableTexts.any { it.isNotBlank() }
                return if (!hasDraft) {
                    pendingVerifyText = null
                    ToolResult(true, data = buildJsonObject {
                        put("confirmed", true)
                        put("reason", "Input box is empty after send.")
                    })
                } else {
                    ToolResult(false, "SEND_NOT_CONFIRMED", "Input still has draft content after send.")
                }
            }

            "extract_text" -> {
                val nodeId = args["nodeId"]?.jsonPrimitive?.content
                    ?: return ToolResult(false, "INVALID_ARGUMENT", "Missing nodeId")
                val tree = service.getViewTree()
                    ?: return ToolResult(false, ErrorCode.NODE_NOT_FOUND.name, "Cannot get view tree")
                val text = ViewNodeUtils.extractText(tree, nodeId)
                    ?: return ToolResult(false, ErrorCode.NODE_NOT_FOUND.name, "Cannot extract text from $nodeId")
                ToolResult(true, data = buildJsonObject {
                    put("nodeId", nodeId)
                    put("text", text)
                })
            }

            "list_scheduled_scripts" -> {
                runBlocking {
                    val db = OpenRingDatabase.getDatabase(context)
                    val dao = db.scriptDao()
                    val store = ScriptStore(dao)
                    val scripts = dao.getAllScriptsOnce()
                    val data = buildJsonObject {
                        putJsonArray("scripts") {
                            for (script in scripts) {
                                val sched = store.parseSchedule(script.scheduleJson)
                                val steps = store.parseSteps(script.stepsJson)
                                val promptPreview = steps.firstOrNull { it.type == "ai_action" }
                                    ?.getParam("prompt")
                                    ?.take(300).orEmpty()
                                add(buildJsonObject {
                                    put("scriptId", script.id)
                                    put("name", script.name)
                                    put("enabled", sched.enabled)
                                    put("scheduleType", sched.type)
                                    put("scheduleMode", sched.mode)
                                    put("hour", sched.hour)
                                    put("minute", sched.minute)
                                    put("intervalMinutes", sched.minutes)
                                    put("promptPreview", promptPreview)
                                })
                            }
                        }
                        put("count", scripts.size)
                    }
                    ToolResult(true, data = data)
                }
            }

            "delete_scheduled_script" -> {
                val scriptId = args["scriptId"]?.jsonPrimitive?.content
                    ?: return ToolResult(false, "INVALID_ARGUMENT", "Missing scriptId")
                runBlocking {
                    val db = OpenRingDatabase.getDatabase(context)
                    val dao = db.scriptDao()
                    val existing = dao.getScriptById(scriptId)
                    if (existing == null) {
                        ToolResult(false, "SCRIPT_NOT_FOUND", "No script with id: $scriptId")
                    } else {
                        ScriptStore(dao).deleteScript(scriptId)
                        Scheduler(context).cancelScript(scriptId)
                        ToolResult(true, data = buildJsonObject {
                            put("scriptId", scriptId)
                            put("deleted", true)
                        })
                    }
                }
            }

            "create_scheduled_script" -> {
                val name = args["name"]?.jsonPrimitive?.content
                    ?: return ToolResult(false, "INVALID_ARGUMENT", "Missing name")
                val prompt = args["prompt"]?.jsonPrimitive?.content
                    ?: return ToolResult(false, "INVALID_ARGUMENT", "Missing prompt")
                val enabled = args["enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
                val type = args["type"]?.jsonPrimitive?.content ?: "interval"
                val mode = args["mode"]?.jsonPrimitive?.content ?: "battery"
                val hour = args["hour"]?.jsonPrimitive?.content?.toIntOrNull() ?: 9
                val minute = args["minute"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val minutes = args["minutes"]?.jsonPrimitive?.content?.toIntOrNull() ?: 30
                val schedule = Schedule(enabled, type, mode, hour, minute, minutes)

                runBlocking {
                    val db = OpenRingDatabase.getDatabase(context)
                    val dao = db.scriptDao()
                    val scriptId = java.util.UUID.randomUUID().toString()
                    
                    val stepsList = listOf(
                        com.openring.data.model.ScriptStep(
                            type = "ai_action",
                            params = mapOf("prompt" to prompt)
                        )
                    )
                    
                    val script = com.openring.data.model.Script(
                        id = scriptId,
                        name = name,
                        version = 1,
                        stepsJson = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.openring.data.model.ScriptStep.serializer()), stepsList),
                        scheduleJson = json.encodeToString(Schedule.serializer(), schedule)
                    )
                    dao.insert(script)
                    Scheduler(context).scheduleScript(scriptId, schedule)
                    ToolResult(true, data = buildJsonObject {
                        put("scriptId", scriptId)
                        put("name", name)
                        put("enabled", enabled)
                        put("type", type)
                    })
                }
            }

            "update_script_schedule" -> {
                val scriptId = args["scriptId"]?.jsonPrimitive?.content
                    ?: return ToolResult(false, "INVALID_ARGUMENT", "Missing scriptId")
                val enabled = args["enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
                val type = args["type"]?.jsonPrimitive?.content ?: "disabled"
                val mode = args["mode"]?.jsonPrimitive?.content ?: "battery"
                val hour = args["hour"]?.jsonPrimitive?.content?.toIntOrNull() ?: 9
                val minute = args["minute"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val minutes = args["minutes"]?.jsonPrimitive?.content?.toIntOrNull() ?: 30
                val schedule = Schedule(enabled, type, mode, hour, minute, minutes)
                runBlocking {
                    val db = OpenRingDatabase.getDatabase(context)
                    val dao = db.scriptDao()
                    val script = dao.getScriptById(scriptId)
                    if (script == null) {
                        ToolResult(false, "SCRIPT_NOT_FOUND", "No script with id: $scriptId")
                    } else {
                        val updated = script.copy(scheduleJson = json.encodeToString(Schedule.serializer(), schedule))
                        dao.update(updated)
                        Scheduler(context).scheduleScript(scriptId, schedule)
                        ToolResult(true, data = buildJsonObject {
                            put("scriptId", scriptId)
                            put("enabled", enabled)
                            put("type", type)
                        })
                    }
                }
            }

            "set_system_prompt" -> {
                val promptStore = AiPromptStore(context)
                if (!promptStore.getAllowAiToSetSystemPrompt()) {
                    return ToolResult(false, "NOT_ALLOWED", "User has not allowed AI to change system prompt. Enable it on the System Prompt screen.")
                }
                val prompt = args["prompt"]?.jsonPrimitive?.content
                    ?: return ToolResult(false, "INVALID_ARGUMENT", "Missing prompt")
                val trimmed = prompt.take(16_000)
                promptStore.setSystemPrompt(trimmed)
                ToolResult(true, data = buildJsonObject { put("updated", true) })
            }

            "install_skill" -> {
                val url = args["url"]?.jsonPrimitive?.content
                    ?: return ToolResult(false, "INVALID_ARGUMENT", "Missing url")
                val allowed = SkillAllowedSourcesStore(context)
                when (val r = SkillInstall.installFromUrl(context, url, allowed)) {
                    is SkillInstall.Result.Ok -> ToolResult(true, data = buildJsonObject {
                        put("skillId", r.skillId)
                        put("message", "Skill installed successfully.")
                    })
                    is SkillInstall.Result.Err -> ToolResult(false, r.code, r.message)
                }
            }

            "launch_app" -> {
                val pkg = args["package"]?.jsonPrimitive?.content
                    ?: return ToolResult(false, "INVALID_ARGUMENT", "Missing package")
                val uri = args["uri"]?.jsonPrimitive?.content
                when (val r = intentRouter.launchApp(pkg, uri)) {
                    is ActionResult.Ok -> ToolResult(true)
                    is ActionResult.Error -> ToolResult(false, r.code.name, r.message)
                }
            }

            else -> ToolResult(false, "UNKNOWN_TOOL", "Unknown tool: $name")
        }
    }

    private fun dispatchMemoryTool(name: String, args: JsonObject): ToolResult {
        val sessionId = ActiveChatContext.sessionId
        val apiKey = ActiveChatContext.geminiApiKey
        val repo = MemoryRepository(context)
        return runBlocking {
            when (name) {
                "memory_save_fact" -> {
                    val factKey = args["factKey"]?.jsonPrimitive?.content
                        ?: return@runBlocking ToolResult(false, "INVALID_ARGUMENT", "Missing factKey")
                    val factValue = args["factValue"]?.jsonPrimitive?.content
                        ?: return@runBlocking ToolResult(false, "INVALID_ARGUMENT", "Missing factValue")
                    val scope = args["scope"]?.jsonPrimitive?.content ?: "session"
                    if (scope != "session" && scope != "global") {
                        return@runBlocking ToolResult(
                            false,
                            "INVALID_ARGUMENT",
                            "scope must be session or global"
                        )
                    }
                    val sid = when (scope) {
                        "global" -> ""
                        else -> sessionId
                            ?: return@runBlocking ToolResult(false, "NO_SESSION", "No active chat session")
                    }
                    val id = repo.saveFact(scope, sid, factKey, factValue)
                    ToolResult(true, data = buildJsonObject { put("id", id) })
                }

                "memory_list_facts" -> {
                    val scope = args["scope"]?.jsonPrimitive?.content ?: "session"
                    if (scope != "session" && scope != "global") {
                        return@runBlocking ToolResult(false, "INVALID_ARGUMENT", "Invalid scope")
                    }
                    val limit = args["limit"]?.jsonPrimitive?.content?.toIntOrNull()?.coerceIn(1, 100) ?: 24
                    val sid = when (scope) {
                        "global" -> ""
                        else -> sessionId ?: ""
                    }
                    val rows = repo.listFacts(scope, sid, limit)
                    ToolResult(true, data = buildJsonObject {
                        putJsonArray("facts") {
                            for (it in rows) {
                                add(buildJsonObject {
                                    put("id", it.id)
                                    put("factKey", it.factKey)
                                    put("factValue", it.factValue.take(2000))
                                    put("scope", it.scope)
                                    put("updatedAtMs", it.updatedAtMs)
                                })
                            }
                        }
                        put("count", rows.size)
                    })
                }

                "memory_delete_fact" -> {
                    val id = args["id"]?.jsonPrimitive?.content
                        ?: return@runBlocking ToolResult(false, "INVALID_ARGUMENT", "Missing id")
                    val ok = repo.deleteFact(id)
                    if (!ok) {
                        ToolResult(false, "NOT_FOUND", "Fact not found")
                    } else {
                        ToolResult(true, data = buildJsonObject { put("deleted", true); put("id", id) })
                    }
                }

                "memory_set_session_summary" -> {
                    val sid = sessionId
                        ?: return@runBlocking ToolResult(false, "NO_SESSION", "No active chat session")
                    val text = args["text"]?.jsonPrimitive?.content
                        ?: return@runBlocking ToolResult(false, "INVALID_ARGUMENT", "Missing text")
                    repo.setSessionSummary(sid, text)
                    ToolResult(true, data = buildJsonObject { put("updated", true) })
                }

                "memory_get_session_summary" -> {
                    val sid = sessionId
                        ?: return@runBlocking ToolResult(false, "NO_SESSION", "No active chat session")
                    val summary = repo.getSessionSummary(sid)
                    ToolResult(true, data = buildJsonObject { put("summary", summary) })
                }

                "memory_save_chunk" -> {
                    if (apiKey.isNullOrBlank()) {
                        return@runBlocking ToolResult(
                            false,
                            "NO_API_KEY",
                            "Gemini API key required for embeddings. Chat with a Gemini model configured with a key."
                        )
                    }
                    val text = args["text"]?.jsonPrimitive?.content
                        ?: return@runBlocking ToolResult(false, "INVALID_ARGUMENT", "Missing text")
                    val scope = args["scope"]?.jsonPrimitive?.content ?: "session"
                    if (scope != "session" && scope != "global") {
                        return@runBlocking ToolResult(false, "INVALID_ARGUMENT", "scope must be session or global")
                    }
                    val sid = when (scope) {
                        "global" -> ""
                        else -> sessionId
                            ?: return@runBlocking ToolResult(false, "NO_SESSION", "No active chat session")
                    }
                    val id = repo.saveVectorChunk(apiKey, scope, sid, text)
                    ToolResult(true, data = buildJsonObject { put("id", id) })
                }

                "memory_recall" -> {
                    if (apiKey.isNullOrBlank()) {
                        return@runBlocking ToolResult(
                            false,
                            "NO_API_KEY",
                            "Gemini API key required for query embedding."
                        )
                    }
                    val sid = sessionId
                        ?: return@runBlocking ToolResult(false, "NO_SESSION", "No active chat session")
                    val query = args["query"]?.jsonPrimitive?.content
                        ?: return@runBlocking ToolResult(false, "INVALID_ARGUMENT", "Missing query")
                    val topK = args["topK"]?.jsonPrimitive?.content?.toIntOrNull()?.coerceIn(1, 20) ?: 6
                    val hits = repo.vectorRecall(apiKey, sid, query, topK)
                    ToolResult(true, data = buildJsonObject {
                        putJsonArray("hits") {
                            for ((score, chunk) in hits) {
                                add(buildJsonObject {
                                    put("score", score.toDouble())
                                    put("text", chunk.take(4000))
                                })
                            }
                        }
                        put("count", hits.size)
                    })
                }

                else -> ToolResult(false, "UNKNOWN_TOOL", "Unknown memory tool: $name")
            }
        }
    }

    private fun executeSkill(skillId: String, input: JsonObject): ToolResult {
        val trimmed = skillId.trim()
        if (trimmed.isBlank()) {
            return ToolResult(false, "INVALID_ARGUMENT", "Empty skill id")
        }
        val store = InstalledSkillStore(context)
        val canonicalId = store.getInstalledIds().firstOrNull { it.equals(trimmed, ignoreCase = true) }
            ?: return ToolResult(false, "SKILL_NOT_INSTALLED", "Skill not installed: $trimmed")
        if (!SkillEnabledStore(context).isEnabled(canonicalId)) {
            return ToolResult(
                false,
                "SKILL_DISABLED",
                "Skill disabled: $canonicalId. Enable it in the Skills screen."
            )
        }
        val dir = store.getSkillDir(context, canonicalId)
            ?: return ToolResult(false, "SKILL_NOT_FOUND", "Skill directory missing: $canonicalId")
        val scriptFile = File(dir, "script.js")
        if (!scriptFile.isFile) {
            return ToolResult(false, "INVALID_SKILL", "script.js missing for skill: $canonicalId")
        }
        val scriptText = try {
            scriptFile.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            return ToolResult(false, "READ_FAILED", e.message ?: "Cannot read script.js")
        }
        return SkillQuickJsExecutor.execute(scriptText, input).fold(
            onSuccess = { data ->
                ToolResult(
                    true,
                    data = buildJsonObject {
                        put("skillId", canonicalId)
                        for ((k, v) in data) {
                            put(k, v)
                        }
                    }
                )
            },
            onFailure = { e ->
                ToolResult(
                    false,
                    "SKILL_RUNTIME_ERROR",
                    e.message ?: e.javaClass.simpleName
                )
            }
        )
    }

    private fun viewNodeToJson(node: ViewNode): JsonObject {
        return buildJsonObject {
            put("id", node.id)
            put("className", node.className)
            put("text", node.text)
            put("contentDesc", node.contentDesc)
            put("clickable", node.clickable)
            putJsonObject("bounds") {
                put("left", node.bounds.left)
                put("top", node.bounds.top)
                put("right", node.bounds.right)
                put("bottom", node.bounds.bottom)
            }
            putJsonArray("children") {
                node.children.forEach { add(viewNodeToJson(it)) }
            }
        }
    }

    private fun findNodesByText(node: ViewNode?, text: String, exact: Boolean): List<ViewNode> {
        if (node == null) return emptyList()
        val here = mutableListOf<ViewNode>()
        val value = node.text ?: node.contentDesc
        if (node.clickable && value != null) {
            val hit = if (exact) value.equals(text, ignoreCase = true) else value.contains(text, ignoreCase = true)
            if (hit) here.add(node)
        }
        for (child in node.children) {
            here.addAll(findNodesByText(child, text, exact))
        }
        return here
    }

    private fun containsText(node: ViewNode?, needle: String): Boolean {
        if (node == null || needle.isBlank()) return false
        val v = node.text ?: node.contentDesc
        if (v != null && v.contains(needle, ignoreCase = true)) return true
        return node.children.any { containsText(it, needle) }
    }

    private fun collectEditableTexts(node: ViewNode?): List<String> {
        if (node == null) return emptyList()
        val out = mutableListOf<String>()
        val className = node.className.orEmpty()
        val isEditableLike = className.contains("EditText", ignoreCase = true)
        if (isEditableLike) {
            out.add((node.text ?: node.contentDesc).orEmpty())
        }
        node.children.forEach { out.addAll(collectEditableTexts(it)) }
        return out
    }
}

