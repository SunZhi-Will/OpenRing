package com.openring.agent

import android.content.Context
import com.openring.core.InstalledAppsProvider
import com.openring.core.IntentRouter
import com.openring.core.OpenRingAccessibilityService
import com.openring.core.ViewNodeUtils
import com.openring.core.model.ActionResult
import com.openring.core.model.ErrorCode
import com.openring.core.model.ViewNode
import com.openring.data.db.OpenRingDatabase
import com.openring.data.model.Schedule
import com.openring.domain.Scheduler
import com.openring.settings.AiPromptStore
import com.openring.settings.ScanCache
import com.openring.skills.SkillInstall
import com.openring.skills.SkillAllowedSourcesStore
import com.openring.skills.SkillEnabledStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.put

class ToolDispatcher(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
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

            "call_skill" -> {
                val skillId = args["skill"]?.jsonPrimitive?.content
                    ?: return ToolResult(false, "INVALID_ARGUMENT", "Missing skill")

                val enabled = SkillEnabledStore(context).isEnabled(skillId)
                if (!enabled) {
                    return ToolResult(
                        ok = false,
                        code = "SKILL_DISABLED",
                        message = "Skill disabled: $skillId"
                    )
                }

                // 技術債：QuickJS runtime / wiring 尚未完成時，仍回傳目前的錯誤碼。
                ToolResult(false, ErrorCode.PERMISSION_DENIED.name, "Skill engine not implemented yet")
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

