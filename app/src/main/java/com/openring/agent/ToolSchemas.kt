package com.openring.agent

import android.content.Context
import android.util.Log
import com.openring.gemini.model.FunctionDeclaration
import com.openring.gemini.model.Tool
import com.openring.skills.SkillEnabledStore
import com.openring.skills.SkillTemplateCatalog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

object ToolSchemas {

    private val json = Json { ignoreUnknownKeys = true }

    fun buildTools(context: Context): List<Tool> {
        val staticTools = listOf(
            getInstalledApps(),
            getViewTree(),
            getCachedScan(),
            summarizeViewTree(),
            describeScreen(),
            describeAmbientAudio(),
            launchApp(),
            duolingoMatchPick(),
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
            listScheduledScripts(),
            updateScriptSchedule(),
            createScheduledScript(),
            deleteScheduledScript(),
            memorySaveFact(),
            memoryListFacts(),
            memoryDeleteFact(),
            memorySetSessionSummary(),
            memoryGetSessionSummary(),
            memorySaveChunk(),
            memoryRecall(),
            callSkill(),
            httpRequest(),
            setSystemPrompt(),
            installOfficialSkill(),
            installSkill(),
            createSkill(),
            listPromptNotes(),
            getPromptNote()
        )

        val dynamicTools = loadDynamicSkills(context)

        return listOf(
            Tool(
                functionDeclarations = staticTools + dynamicTools
            )
        )
    }

    private fun loadDynamicSkills(context: Context): List<FunctionDeclaration> {
        val enabledIds = SkillEnabledStore(context).getEnabledIds()
        val declarations = mutableListOf<FunctionDeclaration>()

        for (skillId in enabledIds) {
            val manifestFile = File(context.filesDir, "skills/$skillId/manifest.json")
            if (manifestFile.exists()) {
                try {
                    val manifestText = manifestFile.readText()
                    val manifestObj = json.parseToJsonElement(manifestText).jsonObject

                    val manifestName = manifestObj["name"]?.jsonPrimitive?.content ?: skillId
                    val description = manifestObj["description"]?.jsonPrimitive?.content ?: "Skill: $manifestName"
                    val inputSchema = manifestObj["inputSchema"]?.jsonObject ?: buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {}
                    }

                    // Dynamic tool names are bound to installed skillId, which is canonicalized by SkillInstall.
                    // This avoids runtime mismatch when manifest.name contains chars trimmed during install.
                    val toolName = "skill_$skillId"

                    declarations.add(
                        FunctionDeclaration(
                            name = toolName,
                            description = description,
                            parameters = inputSchema
                        )
                    )
                } catch (e: Exception) {
                    Log.e("ToolSchemas", "Failed to parse manifest for skill $skillId", e)
                }
            }
        }
        return declarations
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

    private fun summarizeViewTree() = FunctionDeclaration(
        name = "summarize_view_tree",
        description = "Returns a compact, text-oriented UI summary (fingerprint + clickable labels with node ids). Use when the full get_view_tree JSON is too large or you only need tap targets. Updates the same scan cache as get_view_tree. No pixel/vision data.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {}
        }
    )

    private fun describeScreen() = FunctionDeclaration(
        name = "describe_screen",
        description = "Fallback when the accessibility tree is empty, unreliable, or the UI is WebView/game/custom-rendered: capture the current screen and get a short visual description via Gemini vision (requires API 30+, cloud key). Prefer get_view_tree or summarize_view_tree (text-only compact summary) first; call this when the tree is insufficient and vision is available. Pair with describe_ambient_audio for audio tasks (prefer device playback capture via user-granted MediaProjection; see Permissions).",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("question") {
                    put("type", JsonPrimitive("string"))
                    put(
                        "description",
                        JsonPrimitive("Optional focus question, e.g. where is the login button?")
                    )
                }
            }
        }
    )

    private fun describeAmbientAudio() = FunctionDeclaration(
        name = "describe_ambient_audio",
        description = "Hearing: prefer capturing **device-internal playback** (other apps’ speaker/mix audio) via user-granted MediaProjection (same flow as screen record; enable in OpenRing Permissions). Falls back to microphone if projection is off or playback capture fails. Sends WAV to Gemini for transcription/summary. Use for sound-match or listen-and-tap when UI text is not enough. Requires RECORD_AUDIO and Gemini API key; target apps may disallow playback capture.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("maxDurationSeconds") {
                    put("type", JsonPrimitive("number"))
                    put(
                        "description",
                        JsonPrimitive("Recording length 1–10 seconds; default 6. Shorter is faster and smaller payload.")
                    )
                }
                putJsonObject("question") {
                    put("type", JsonPrimitive("string"))
                    put(
                        "description",
                        JsonPrimitive("Optional: what to focus on (e.g. transcribe the spoken word, identify language).")
                    )
                }
            }
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

    private fun duolingoMatchPick() = FunctionDeclaration(
        name = "duolingo_match_pick",
        description = "For Duolingo word-match screens: collect visible clickable labels, resolve target deterministically via duolingo_word_match_guard, then click the selected label. Prefer this over raw find_and_click for word-match tasks. For listen/sound exercises, call describe_ambient_audio first (device playback capture in Permissions) to obtain the spoken target before choosing target text.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("target") { put("type", JsonPrimitive("string")) }
                putJsonObject("allowContainsFallback") { put("type", JsonPrimitive("boolean")) }
            }
            putJsonArray("required") { add(JsonPrimitive("target")) }
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

    private fun httpRequest() = FunctionDeclaration(
        name = "http_request",
        description = "Perform an HTTPS request from the device (OkHttp). Only hosts the user added to the HTTP allowlist in OpenRing → Skills are permitted. Use for REST APIs, webhooks, or fetching JSON/text when no Skill covers the case. Prefer call_skill when a packaged Skill exists. Methods: GET, HEAD, POST, PUT, PATCH, DELETE. Response body may be truncated at ~512 KiB.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("url") {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Full https URL."))
                }
                putJsonObject("method") {
                    put("type", JsonPrimitive("string"))
                    put(
                        "description",
                        JsonPrimitive("Optional. Default GET. Allowed: GET, HEAD, POST, PUT, PATCH, DELETE.")
                    )
                }
                putJsonObject("headers") {
                    put("type", JsonPrimitive("object"))
                    put("description", JsonPrimitive("Optional header name → string value map."))
                }
                putJsonObject("body") {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Optional raw body for non-GET (e.g. JSON string)."))
                }
            }
            putJsonArray("required") { add(JsonPrimitive("url")) }
        }
    )

    private fun listScheduledScripts() = FunctionDeclaration(
        name = "list_scheduled_scripts",
        description = "List all locally saved scheduled scripts (id, name, schedule, prompt preview). Call this first when planning recurring tasks: check for duplicates, pick scriptId for update_script_schedule or delete_scheduled_script, or confirm names before create_scheduled_script.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {}
        }
    )

    private fun updateScriptSchedule() = FunctionDeclaration(
        name = "update_script_schedule",
        description = "Update timing or enable/disable an existing scheduled script. Always call list_scheduled_scripts first if you do not already have scriptId. Use this after the user changes cadence, time-of-day, or wants to pause/resume automation.",
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

    private fun createScheduledScript() = FunctionDeclaration(
        name = "create_scheduled_script",
        description = "Create a new scheduled automation: one AI prompt step plus a recurrence rule. When the user asks for reminders, periodic checks, or background tasks, plan the prompt and schedule explicitly, call list_scheduled_scripts to avoid duplicate names/rules if needed, then create. type: interval|hourly|daily|disabled; mode: battery (deferred)|exact (alarm)|always_on (foreground service).",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("name") { put("type", JsonPrimitive("string")) }
                putJsonObject("prompt") { put("type", JsonPrimitive("string")) }
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
            putJsonArray("required") { add(JsonPrimitive("name")); add(JsonPrimitive("prompt")) }
        }
    )

    private fun deleteScheduledScript() = FunctionDeclaration(
        name = "delete_scheduled_script",
        description = "Delete a scheduled script and cancel its WorkManager/alarms. Use scriptId from list_scheduled_scripts when the user cancels automation or you remove a superseded plan.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("scriptId") { put("type", JsonPrimitive("string")) }
            }
            putJsonArray("required") { add(JsonPrimitive("scriptId")) }
        }
    )

    private fun memorySaveFact() = FunctionDeclaration(
        name = "memory_save_fact",
        description = "Save a structured key/value fact into long-term memory. scope=session ties to the current chat; scope=global is shared across sessions. Use for stable preferences, names, or constraints the user wants remembered.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("factKey") { put("type", JsonPrimitive("string")) }
                putJsonObject("factValue") { put("type", JsonPrimitive("string")) }
                putJsonObject("scope") {
                    put("type", JsonPrimitive("string"))
                    putJsonArray("enum") {
                        add(JsonPrimitive("session"))
                        add(JsonPrimitive("global"))
                    }
                }
            }
            putJsonArray("required") { add(JsonPrimitive("factKey")); add(JsonPrimitive("factValue")) }
        }
    )

    private fun memoryListFacts() = FunctionDeclaration(
        name = "memory_list_facts",
        description = "List saved memory facts for session or global scope (most recent first).",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("scope") {
                    put("type", JsonPrimitive("string"))
                    putJsonArray("enum") {
                        add(JsonPrimitive("session"))
                        add(JsonPrimitive("global"))
                    }
                }
                putJsonObject("limit") { put("type", JsonPrimitive("integer")) }
            }
        }
    )

    private fun memoryDeleteFact() = FunctionDeclaration(
        name = "memory_delete_fact",
        description = "Delete a fact by id returned from memory_list_facts.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("id") { put("type", JsonPrimitive("string")) }
            }
            putJsonArray("required") { add(JsonPrimitive("id")) }
        }
    )

    private fun memorySetSessionSummary() = FunctionDeclaration(
        name = "memory_set_session_summary",
        description = "Overwrite the running chat session summary (rolling narrative). Keep it concise; user-visible when memory is injected.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("text") { put("type", JsonPrimitive("string")) }
            }
            putJsonArray("required") { add(JsonPrimitive("text")) }
        }
    )

    private fun memoryGetSessionSummary() = FunctionDeclaration(
        name = "memory_get_session_summary",
        description = "Read the current session summary text.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {}
        }
    )

    private fun memorySaveChunk() = FunctionDeclaration(
        name = "memory_save_chunk",
        description = "Embed and store a free-text chunk for vector recall (Gemini embedding). scope=session or global. Requires Gemini API key (chat session). Use for salient excerpts the user may ask about later.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("text") { put("type", JsonPrimitive("string")) }
                putJsonObject("scope") {
                    put("type", JsonPrimitive("string"))
                    putJsonArray("enum") {
                        add(JsonPrimitive("session"))
                        add(JsonPrimitive("global"))
                    }
                }
            }
            putJsonArray("required") { add(JsonPrimitive("text")) }
        }
    )

    private fun memoryRecall() = FunctionDeclaration(
        name = "memory_recall",
        description = "Vector search over stored memory chunks (cosine similarity on Gemini embeddings). Returns top hits with scores for the query.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("query") { put("type", JsonPrimitive("string")) }
                putJsonObject("topK") { put("type", JsonPrimitive("integer")) }
            }
            putJsonArray("required") { add(JsonPrimitive("query")) }
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

    private fun createSkill() = FunctionDeclaration(
        name = "create_skill",
        description = "Create and install a full Skill from inline manifest.json and script.js (QuickJS). Requires user opt-in (Settings → Skills). On conflict, set overwrite=true to replace. Optional skill_md: omit to keep existing SKILL.md when overwriting; empty string removes SKILL.md.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("manifest") {
                    put("type", JsonPrimitive("string"))
                    put(
                        "description",
                        JsonPrimitive("Full JSON for manifest.json (must include name; optional description, inputSchema).")
                    )
                }
                putJsonObject("script") {
                    put("type", JsonPrimitive("string"))
                    put(
                        "description",
                        JsonPrimitive("QuickJS script defining run(input); must return a JSON object.")
                    )
                }
                putJsonObject("overwrite") {
                    put("type", JsonPrimitive("boolean"))
                    put(
                        "description",
                        JsonPrimitive("If true, replace an existing skill with the same canonical id. Default false.")
                    )
                }
                putJsonObject("skill_md") {
                    put("type", JsonPrimitive("string"))
                    put(
                        "description",
                        JsonPrimitive("Optional SKILL.md body for model guidance. Omit to leave SKILL.md unchanged on overwrite; empty string deletes SKILL.md.")
                    )
                }
            }
            putJsonArray("required") {
                add(JsonPrimitive("manifest"))
                add(JsonPrimitive("script"))
            }
        }
    )

    private fun installSkill() = FunctionDeclaration(
        name = "install_skill",
        description = "Install a third-party Skill plugin from an allowed URL (user must have added the URL to allowed sources in Settings). Do NOT use this for official built-in templates.",
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

    private fun listPromptNotes() = FunctionDeclaration(
        name = "list_prompt_notes",
        description = "Lists entries in the user's prompt library (same OpenRing memory layer as memory_save_fact / memory_recall): saved prompt notes and skill-style notes (id, kind, title, short description). Call when the user refers to stored instructions or you need a named behavior; then call get_prompt_note with an id.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {}
        }
    )

    private fun getPromptNote() = FunctionDeclaration(
        name = "get_prompt_note",
        description = "Loads full text of a prompt-library entry by id (from list_prompt_notes or the injected memory index). Same persistent layer as long-term memory. Follow formattedBlock for this task.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("note_id") {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("id from list_prompt_notes"))
                }
            }
            putJsonArray("required") { add(JsonPrimitive("note_id")) }
        }
    )

    private fun installOfficialSkill() = FunctionDeclaration(
        name = "install_official_skill",
        description = "Install an official built-in Skill template from the OpenRing GitHub catalog by templateId. Use this for requests like 'install official skill'. Never construct a custom URL.",
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            putJsonObject("properties") {
                putJsonObject("templateId") {
                    put("type", JsonPrimitive("string"))
                    putJsonArray("enum") {
                        SkillTemplateCatalog.templates.forEach { template ->
                            add(JsonPrimitive(template.id))
                        }
                    }
                }
            }
            putJsonArray("required") { add(JsonPrimitive("templateId")) }
        }
    )

    /**
     * 地端代理：將 [buildTools] 的宣告壓成純文字（含動態 skill_*），供本機模型讀取。
     */
    fun buildLocalToolCatalogText(context: Context, maxChars: Int = 1400): String {
        val decls = buildTools(context).flatMap { it.functionDeclarations }
        val sb = StringBuilder()
        for (d in decls) {
            val desc = (d.description ?: "").replace('\n', ' ').trim().take(120)
            val argHint = summarizeJsonSchemaPropertyKeys(d.parameters)
            val line = "- ${d.name}: $desc. args: $argHint\n"
            if (sb.length + line.length > maxChars) {
                sb.append("(additional tools omitted for context limit)\n")
                break
            }
            sb.append(line)
        }
        return sb.toString().trimEnd()
    }

    private fun summarizeJsonSchemaPropertyKeys(schema: JsonObject): String {
        val props = schema["properties"] as? JsonObject ?: return "{}"
        if (props.isEmpty()) return "{}"
        return props.keys.sorted().joinToString(", ", prefix = "{ ", postfix = " }")
    }
}

