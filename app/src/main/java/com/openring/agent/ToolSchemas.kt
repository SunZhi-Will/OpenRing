package com.openring.agent

import com.openring.gemini.model.FunctionDeclaration
import com.openring.gemini.model.Tool
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object ToolSchemas {

    fun buildTools(): List<Tool> {
        return listOf(
            Tool(
                functionDeclarations = listOf(
                    getInstalledApps(),
                    getViewTree(),
                    getCachedScan(),
                    launchApp(),
                    findAndClick(),
                    clickNode(),
                    swipe(),
                    back(),
                    home(),
                    inputText(),
                    inputTextFocused(),
                    clickSendButton(),
                    verifySendResult(),
                    extractText(),
                    callSkill(),
                    updateScriptSchedule(),
                    setSystemPrompt(),
                    installSkill()
                )
            )
        )
    }

    private fun getInstalledApps() = FunctionDeclaration(
        name = "get_installed_apps",
        description = "Returns installed launcher apps with displayName and packageName. Call this before launch_app to pick a valid package.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {}
        }
    )

    private fun getViewTree() = FunctionDeclaration(
        name = "get_view_tree",
        description = "Returns the current semantic UI node tree (password fields are masked).",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {}
        }
    )

    private fun getCachedScan() = FunctionDeclaration(
        name = "get_cached_scan",
        description = "Returns the last cached UI scan (timestampMs and root tree) if auto-scan or a previous get_view_tree ran. Use when you need recent screen state without triggering a new scan.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {}
        }
    )

    private fun launchApp() = FunctionDeclaration(
        name = "launch_app",
        description = "Launch an installed app by package. Prefer calling get_installed_apps first and choose a package from that list.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("package") { put("type", JsonPrimitive("string")) }
                putJsonObject("uri") { put("type", JsonPrimitive("string")) }
            }
            putJsonArray("required") { add(JsonPrimitive("package")) }
        }
    )

    private fun findAndClick() = FunctionDeclaration(
        name = "find_and_click",
        description = "Find a node by text/contentDesc and click it. For person names, prefer match=exact to avoid clicking the wrong target. In Discord, exact+unambiguous targets are required.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("text") {
                    put("type", JsonPrimitive("string"))
                }
                putJsonObject("match") {
                    put("type", JsonPrimitive("string"))
                    putJsonArray("enum") {
                        add(JsonPrimitive("exact"))
                        add(JsonPrimitive("contains"))
                    }
                }
            }
            putJsonArray("required") { add(JsonPrimitive("text")) }
        }
    )

    private fun clickSendButton() = FunctionDeclaration(
        name = "click_send_button",
        description = "Click a typical chat send button (Send Message/Send/發送/送出/傳送). Use this right after input_text_focused.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {}
        }
    )

    private fun verifySendResult() = FunctionDeclaration(
        name = "verify_send_result",
        description = "Verify whether the most recent message input was actually sent. Call this after click_send_button.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {}
        }
    )

    private fun clickNode() = FunctionDeclaration(
        name = "click_node",
        description = "Click a node by nodeId. In Discord, this is restricted to send-like controls for safety.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("nodeId") { put("type", JsonPrimitive("string")) }
            }
            putJsonArray("required") { add(JsonPrimitive("nodeId")) }
        }
    )

    private fun swipe() = FunctionDeclaration(
        name = "swipe",
        description = "Swipe the screen in a direction.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("direction") {
                    put("type", JsonPrimitive("string"))
                    putJsonArray("enum") {
                        add(JsonPrimitive("up"))
                        add(JsonPrimitive("down"))
                        add(JsonPrimitive("left"))
                        add(JsonPrimitive("right"))
                    }
                }
                putJsonObject("distance") {
                    put("type", JsonPrimitive("integer"))
                }
            }
            putJsonArray("required") { add(JsonPrimitive("direction")) }
        }
    )

    private fun back() = FunctionDeclaration(
        name = "back",
        description = "Perform global back action.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {}
        }
    )

    private fun home() = FunctionDeclaration(
        name = "home",
        description = "Perform global home action.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {}
        }
    )

    private fun extractText() = FunctionDeclaration(
        name = "extract_text",
        description = "Extract text from a node by nodeId.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("nodeId") { put("type", JsonPrimitive("string")) }
            }
            putJsonArray("required") { add(JsonPrimitive("nodeId")) }
        }
    )

    private fun inputText() = FunctionDeclaration(
        name = "input_text",
        description = "Input text into an editable node by nodeId (requires a recent get_view_tree).",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("nodeId") { put("type", JsonPrimitive("string")) }
                putJsonObject("text") { put("type", JsonPrimitive("string")) }
            }
            putJsonArray("required") { add(JsonPrimitive("nodeId")); add(JsonPrimitive("text")) }
        }
    )

    private fun inputTextFocused() = FunctionDeclaration(
        name = "input_text_focused",
        description = "Input text into the currently focused input field (recommended for dynamic chat UIs).",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("text") { put("type", JsonPrimitive("string")) }
            }
            putJsonArray("required") { add(JsonPrimitive("text")) }
        }
    )

    private fun callSkill() = FunctionDeclaration(
        name = "call_skill",
        description = "Execute a locally installed Skill Plugin (QuickJS).",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("skill") { put("type", JsonPrimitive("string")) }
                putJsonObject("input") {
                    put("type", JsonPrimitive("object"))
                }
            }
            putJsonArray("required") { add(JsonPrimitive("skill")); add(JsonPrimitive("input")) }
        }
    )

    private fun updateScriptSchedule() = FunctionDeclaration(
        name = "update_script_schedule",
        description = "Update the schedule of an existing script (workflow). Script must exist; use scriptId from script list.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("scriptId") { put("type", JsonPrimitive("string")) }
                putJsonObject("enabled") { put("type", JsonPrimitive("boolean")) }
                putJsonObject("type") {
                    put("type", JsonPrimitive("string"))
                    putJsonArray("enum") {
                        add(JsonPrimitive("disabled"))
                        add(JsonPrimitive("daily"))
                        add(JsonPrimitive("hourly"))
                        add(JsonPrimitive("interval"))
                    }
                }
                putJsonObject("mode") {
                    put("type", JsonPrimitive("string"))
                    putJsonArray("enum") {
                        add(JsonPrimitive("battery"))
                        add(JsonPrimitive("exact"))
                        add(JsonPrimitive("always_on"))
                    }
                }
                putJsonObject("hour") { put("type", JsonPrimitive("integer")) }
                putJsonObject("minute") { put("type", JsonPrimitive("integer")) }
                putJsonObject("minutes") { put("type", JsonPrimitive("integer")) }
            }
            putJsonArray("required") { add(JsonPrimitive("scriptId")) }
        }
    )

    private fun setSystemPrompt() = FunctionDeclaration(
        name = "set_system_prompt",
        description = "Update the system prompt (instruction) used for this assistant. Only works when the user has enabled AI system-prompt edits on the System Prompt screen.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("prompt") { put("type", JsonPrimitive("string")) }
            }
            putJsonArray("required") { add(JsonPrimitive("prompt")) }
        }
    )

    private fun installSkill() = FunctionDeclaration(
        name = "install_skill",
        description = "Install a Skill plugin from an allowed URL (user must have added the URL to allowed sources in Settings). Returns success or error.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("url") {
                    put("type", JsonPrimitive("string"))
                }
            }
            putJsonArray("required") { add(JsonPrimitive("url")) }
        }
    )
}

