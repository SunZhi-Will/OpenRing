package com.openring.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import android.widget.Toast
import com.openring.R
import com.openring.agent.ActiveChatContext
import com.openring.agent.ChatLogEntry
import com.openring.agent.ExecutionLogStore
import com.openring.agent.LocalReActCoordinator
import com.openring.agent.ReActCoordinator
import com.openring.agent.RunCancellationRegistry
import com.openring.agent.ToolSchemas
import com.openring.chat.ChatAttachmentLoader
import com.openring.chat.ChatAttachmentModelParts
import com.openring.chat.ChatAttachmentPayload
import com.openring.core.BackgroundWorkTracker
import com.openring.core.ChatReloadBus
import com.openring.core.CloudRelayTaskBus
import com.openring.core.OpenRingCloudRelayBridge
import com.openring.core.OverlayService
import com.openring.data.ChatRepository
import com.openring.data.MemoryRepository
import com.openring.data.ScriptStore
import com.openring.data.db.OpenRingDatabase
import com.openring.data.model.ChatMessageEntity
import com.openring.data.model.ChatSession
import com.openring.localmodel.LocalLlmChatPrompt
import com.openring.localmodel.LocalModelCatalog
import com.openring.localmodel.LocalModelSupport
import com.openring.settings.AiPromptStore
import com.openring.security.ApiKeyStore
import com.openring.skills.SkillInstructionCatalog
import com.openring.settings.ModelStore
import com.openring.ui.notifications.AiRunNotification
import com.openring.ui.theme.Spacing
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Date
import java.util.UUID

private suspend fun reloadChatScreenState(
    chatRepository: ChatRepository,
    context: android.content.Context,
    applyLoaded: (
        sessionId: String,
        rows: List<ChatMessageEntity>,
        logs: List<ChatLogEntry>,
        hasEnabledSchedule: Boolean
    ) -> Unit
) {
    withContext(Dispatchers.IO) {
        val sid = chatRepository.getOrCreateActiveSessionId()
        val rows = chatRepository.getMessages(sid)
        val logs = chatRepository.loadExecutionLog(sid)
        val scriptDao = OpenRingDatabase.getDatabase(context).scriptDao()
        val scriptStore = ScriptStore(scriptDao)
        val hasAnyEnabledSchedule = scriptDao.getAllScriptsOnce().any { script ->
            val schedule = scriptStore.parseSchedule(script.scheduleJson)
            schedule.enabled && schedule.type != "disabled"
        }
        withContext(Dispatchers.Main) {
            applyLoaded(sid, rows, logs, hasAnyEnabledSchedule)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToWorkflows: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToExecutionLog: () -> Unit,
    onNavigateToPermissions: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val pendingAttachments = remember { mutableStateListOf<ChatAttachmentPayload>() }
    val pickAttachmentsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            for (uri in uris) {
                val r = ChatAttachmentLoader.load(context, uri)
                withContext(Dispatchers.Main) {
                    r.fold(
                        onSuccess = { pendingAttachments.add(it) },
                        onFailure = { e ->
                            val msg = when (e.message) {
                                "FILE_TOO_LARGE" -> context.getString(R.string.chat_attachment_too_large)
                                else -> context.getString(R.string.chat_attachment_read_failed)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
    val coordinator = remember { ReActCoordinator(context) }
    val localReActCoordinator = remember { LocalReActCoordinator(context) }
    val chatRepository = remember { ChatRepository(context) }
    val memoryRepository = remember { MemoryRepository(context) }
    val keyStore = remember { ApiKeyStore(context) }
    val modelStore = remember { ModelStore(context) }
    val localModelSupported = remember { LocalModelSupport.isSupportedDevice() }
    val modelChain = modelStore.getModels()
    val runnableGemini = modelChain.filter {
        it.provider == "gemini" && keyStore.getGeminiApiKeyForModel(it.id).isNullOrBlank().not()
    }
    val runnableLocal = modelChain.filter {
        localModelSupported &&
            it.provider.equals("local", ignoreCase = true) &&
            LocalModelCatalog.isDownloaded(context, it.model)
    }
    val canRunChat = runnableGemini.isNotEmpty() || runnableLocal.isNotEmpty()
    val hasGeminiWithKey = modelChain.any {
        it.provider == "gemini" && keyStore.getGeminiApiKeyForModel(it.id).isNullOrBlank().not()
    }
    val localEntries = modelChain.filter { it.provider.equals("local", ignoreCase = true) }
    val anyLocalNotDownloaded = localEntries.any {
        !LocalModelCatalog.isDownloaded(context, it.model)
    }

    data class ChatMessage(
        val id: String,
        val role: String,
        val text: String,
        val createdAtMs: Long,
        val attachments: List<ChatAttachmentPayload> = emptyList(),
    )

    fun nowMs(): Long = System.currentTimeMillis()
    val timeFormatter = remember(context) { DateFormat.getTimeFormat(context) }
    fun formatTime(ts: Long): String = timeFormatter.format(Date(ts))

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var activeChatSessionId by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var sessionSheetOpen by remember { mutableStateOf(false) }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var sessionsForPicker by remember { mutableStateOf<List<ChatSession>>(emptyList()) }
    var sessionPendingDelete by remember { mutableStateOf<ChatSession?>(null) }
    val sessionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var running by remember { mutableStateOf(false) }
    var runningSessionId by remember { mutableStateOf<String?>(null) }
    var overlayPendingRun by remember { mutableStateOf<Pair<String, List<ChatAttachmentPayload>>?>(null) }
    data class PermissionReminder(val title: String, val message: String)
    var permissionReminder by remember { mutableStateOf<PermissionReminder?>(null) }
    var permissionReminderShownThisRun by remember { mutableStateOf(false) }
    var processingText by remember { mutableStateOf("正在處理中…") }
    var hasEnabledSchedule by remember { mutableStateOf(false) }
    val backgroundWorkCount by BackgroundWorkTracker.activeCount.collectAsState(initial = 0)
    val lifecycleOwner = LocalLifecycleOwner.current
    val runningState = rememberUpdatedState(running)

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (!runningState.value) {
                reloadChatScreenState(chatRepository, context) { sid, rows, logs, hasSched ->
                    activeChatSessionId = sid
                    messages.clear()
                    hasEnabledSchedule = hasSched
                    for (m in rows) {
                        messages.add(
                            ChatMessage(
                                id = m.id,
                                role = m.role,
                                text = m.body,
                                createdAtMs = m.createdAtMs,
                                attachments = chatRepository.parseAttachments(m.attachmentsJson)
                            )
                        )
                    }
                    ExecutionLogStore.replaceAll(logs)
                }
            }
        }
    }

    var prevBackgroundWorkCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(backgroundWorkCount) {
        if (prevBackgroundWorkCount > 0 && backgroundWorkCount == 0 && !runningState.value) {
            reloadChatScreenState(chatRepository, context) { sid, rows, logs, hasSched ->
                activeChatSessionId = sid
                messages.clear()
                hasEnabledSchedule = hasSched
                for (m in rows) {
                    messages.add(
                        ChatMessage(
                            id = m.id,
                            role = m.role,
                            text = m.body,
                            createdAtMs = m.createdAtMs,
                            attachments = chatRepository.parseAttachments(m.attachmentsJson)
                        )
                    )
                }
                ExecutionLogStore.replaceAll(logs)
            }
        }
        prevBackgroundWorkCount = backgroundWorkCount
    }

    LaunchedEffect(Unit) {
        ChatReloadBus.events.collect {
            if (!runningState.value) {
                reloadChatScreenState(chatRepository, context) { sid, rows, logs, hasSched ->
                    activeChatSessionId = sid
                    messages.clear()
                    hasEnabledSchedule = hasSched
                    for (m in rows) {
                        messages.add(
                            ChatMessage(
                                id = m.id,
                                role = m.role,
                                text = m.body,
                                createdAtMs = m.createdAtMs,
                                attachments = chatRepository.parseAttachments(m.attachmentsJson)
                            )
                        )
                    }
                    ExecutionLogStore.replaceAll(logs)
                }
            }
        }
    }

    LaunchedEffect(sessionSheetOpen) {
        if (sessionSheetOpen) {
            sessionsForPicker = withContext(Dispatchers.IO) {
                chatRepository.listSessions(100)
            }
        }
    }

    fun sanitizeJsonForLog(toolName: String, json: JsonObject): JsonObject {
        if (toolName == "describe_screen") {
            val data = json["data"] as? JsonObject ?: return json
            val desc = data["description"]?.jsonPrimitive?.content ?: return json
            if (desc.length <= 2000) return json
            val sanitizedData = buildJsonObject {
                for ((k, v) in data) {
                    if (k == "description") {
                        put(k, JsonPrimitive("${desc.take(2000)}… [truncated]"))
                    } else {
                        put(k, v)
                    }
                }
            }
            return buildJsonObject {
                for ((k, v) in json) {
                    if (k == "data") put(k, sanitizedData) else put(k, v)
                }
            }
        }
        // `get_view_tree` / `get_cached_scan` 可能回傳超大的 UI 樹，log 全量保存會非常難用也可能導致記憶體壓力。
        if (toolName == "get_view_tree" || toolName == "get_cached_scan") {
            val data = json["data"] as? JsonObject ?: return json
            val sanitizedData = buildJsonObject {
                for ((k, v) in data) {
                    if (k == "root") {
                        put(k, JsonPrimitive("[omitted: large UI tree]"))
                    } else {
                        put(k, v)
                    }
                }
            }
            return buildJsonObject {
                for ((k, v) in json) {
                    if (k == "data") put(k, sanitizedData) else put(k, v)
                }
            }
        }
        return json
    }

    fun updateProcessingText(message: String) {
        processingText = message
    }

    fun recordTurnToLog(turn: ReActCoordinator.Turn) {
        val toolName = turn.toolName ?: return
        when (turn.role) {
            "tool_call" -> {
                processingText = "呼叫工具：$toolName"
                val args = turn.toolResult ?: buildJsonObject { }
                ExecutionLogStore.add(
                    ChatLogEntry.ToolCall(
                        toolName = toolName,
                        args = args,
                        createdAtMs = nowMs()
                    )
                )
            }

            "tool_result" -> {
                val ok = (turn.toolResult?.get("ok") as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
                val code = (turn.toolResult?.get("code") as? JsonPrimitive)?.content
                val msg = (turn.toolResult?.get("message") as? JsonPrimitive)?.content
                processingText = if (ok == true) {
                    "工具結果：$toolName（成功）"
                } else {
                    val detail = listOfNotNull(
                        code,
                        msg?.takeIf { it.isNotBlank() }
                    ).joinToString(" / ").take(80)
                    "工具結果：$toolName（失敗${if (detail.isNotBlank()) ": $detail" else ""}）"
                }
                if (ok != true && !permissionReminderShownThisRun) {
                    permissionReminder = when {
                        code == "RECORD_AUDIO_DENIED" -> {
                            permissionReminderShownThisRun = true
                            PermissionReminder(
                                title = "需要麥克風權限",
                                message = "偵測到目前未授權麥克風，無法使用聽覺相關功能。請到「權限設定」開啟後再試。"
                            )
                        }
                        code == "PERMISSION_DENIED" && msg?.contains("AccessibilityService", ignoreCase = true) == true -> {
                            permissionReminderShownThisRun = true
                            PermissionReminder(
                                title = "需要無障礙權限",
                                message = "目前未啟用 OpenRing 無障礙服務，無法執行畫面操作。請前往「權限設定」開啟。"
                            )
                        }
                        toolName == "describe_ambient_audio" && code == "AUDIO_RECORD_FAILED" -> {
                            permissionReminderShownThisRun = true
                            PermissionReminder(
                                title = "音訊擷取需要權限",
                                message = "無法取得音訊。請到「權限設定」確認麥克風與手機播放音訊（MediaProjection）授權狀態。"
                            )
                        }
                        else -> null
                    }
                }
                val resultObj = turn.toolResult ?: buildJsonObject { }
                ExecutionLogStore.add(
                    ChatLogEntry.ToolResult(
                        toolName = toolName,
                        result = sanitizeJsonForLog(toolName, resultObj),
                        createdAtMs = nowMs()
                    )
                )
            }
        }
    }

    fun startRun(
        text: String,
        tryStartOverlay: Boolean,
        fromRelay: Boolean = false,
        attachments: List<ChatAttachmentPayload> = emptyList(),
    ) {
        val trimmed = text.trim()
        if (trimmed.isBlank() && attachments.isEmpty()) return
        if (running) {
            Log.w("OpenRing", "startRun skipped: already running")
            Toast.makeText(context, "執行中請稍候", Toast.LENGTH_SHORT).show()
            return
        }
        if (!canRunChat) {
            Log.w(
                "OpenRing",
                "startRun skipped: no runnable model (need Gemini API key or downloaded local GGUF)"
            )
            Toast.makeText(
                context,
                "目前無法開始聊天：請（1）為至少一個 Gemini 填入 API Key，或（2）新增地端模型並完成 GGUF 下載。",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        processingText = "正在處理中…"
        running = true
        permissionReminderShownThisRun = false
        val runSessionId = UUID.randomUUID().toString()
        runningSessionId = runSessionId
        RunCancellationRegistry.register(runSessionId)
        AiRunNotification.show(context, runSessionId)
        if (tryStartOverlay && Settings.canDrawOverlays(context)) {
            val overlayIntent = Intent(context, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START_AI_RUN
                putExtra(OverlayService.EXTRA_SESSION_ID, runSessionId)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(overlayIntent)
                } else {
                    context.startService(overlayIntent)
                }
            } catch (e: Exception) {
                Log.w("OpenRing", "AI Overlay 啟動失敗，改用通知中斷", e)
            }
        }
        // IMPORTANT: startRun can outlive the ChatScreen composition (e.g. navigation/recomposition).
        // Using the remembered `scope` here can trigger ForgottenCoroutineScopeException.
        // Create a run-scoped coroutine scope per execution so callbacks remain valid.
        val runScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        runScope.launch {
            BackgroundWorkTracker.acquire(context)
            try {
                val chatSid = withContext(Dispatchers.IO) {
                    chatRepository.getOrCreateActiveSessionId()
                }
                activeChatSessionId = chatSid
                val priorContents = withContext(Dispatchers.IO) {
                    chatRepository.messagesToGeminiContents(chatRepository.getMessages(chatSid))
                }
                val userMsgId = UUID.randomUUID().toString()
                val userTs = nowMs()
                withContext(Dispatchers.IO) {
                    val enc = chatRepository.encodeAttachments(attachments)
                    chatRepository.addUserMessage(chatSid, userMsgId, trimmed, enc)
                }
                messages.add(
                    ChatMessage(
                        id = userMsgId,
                        role = "user",
                        text = trimmed,
                        createdAtMs = userTs,
                        attachments = attachments
                    )
                )
                var streamedLocalModelMessageId: String? = null
                val resultText = withContext(Dispatchers.IO) {
                    var lastError: String? = null
                    val aiPromptStore = AiPromptStore(context)
                    val maxRounds = aiPromptStore.getMaxRounds()
                    for (opt in modelChain) {
                        when (opt.provider.lowercase()) {
                            "gemini" -> {
                                val key = keyStore.getGeminiApiKeyForModel(opt.id).orEmpty()
                                if (key.isBlank()) continue
                                try {
                                    withContext(Dispatchers.Main) {
                                        updateProcessingText("嘗試模型：GEMINI·${opt.label} (${opt.model})")
                                    }
                                    ActiveChatContext.sessionId = chatSid
                                    ActiveChatContext.geminiApiKey = key
                                    ActiveChatContext.geminiModel = opt.model
                                    val memoryQuery = trimmed.ifBlank {
                                        attachments.joinToString(", ") { it.displayName }
                                    }
                                    val injection = try {
                                        memoryRepository.buildContextInjection(key, chatSid, memoryQuery)
                                    } catch (e: Exception) {
                                        Log.w("OpenRing", "Long-term memory injection failed", e)
                                        ""
                                    }
                                    val coreUser = trimmed.ifBlank { "請根據附檔內容回答。" }
                                    val userForModel = if (injection.isBlank()) {
                                        coreUser
                                    } else {
                                        "[Long-term memory context — use if relevant]\n$injection\n\n---\nUser message:\n$coreUser"
                                    }
                                    val extraParts = attachments.flatMap { ChatAttachmentModelParts.toGeminiParts(it) }
                                    val r = coordinator.run(
                                        apiKey = key,
                                        model = opt.model,
                                        userText = userForModel,
                                        priorContents = priorContents,
                                        extraUserParts = extraParts,
                                        maxRounds = maxRounds,
                                        shouldCancel = { RunCancellationRegistry.isCancelled(runSessionId) },
                                        onTurn = { turn ->
                                            runScope.launch(Dispatchers.Main) {
                                                recordTurnToLog(turn)
                                            }
                                            runScope.launch(Dispatchers.IO) {
                                                val toolName = turn.toolName ?: return@launch
                                                when (turn.role) {
                                                    "tool_call" -> {
                                                        val args = turn.toolResult ?: buildJsonObject { }
                                                        chatRepository.appendExecutionLog(
                                                            chatSid,
                                                            ChatLogEntry.ToolCall(
                                                                toolName = toolName,
                                                                args = args,
                                                                createdAtMs = nowMs()
                                                            )
                                                        )
                                                    }

                                                    "tool_result" -> {
                                                        val resultObj = turn.toolResult ?: buildJsonObject { }
                                                        chatRepository.appendExecutionLog(
                                                            chatSid,
                                                            ChatLogEntry.ToolResult(
                                                                toolName = toolName,
                                                                result = sanitizeJsonForLog(toolName, resultObj),
                                                                createdAtMs = nowMs()
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    )
                                    return@withContext r.finalText
                                } catch (e: Exception) {
                                    Log.e(
                                        "OpenRing",
                                        "Model run failed provider=gemini label=${opt.label} model=${opt.model}",
                                        e
                                    )
                                    val detail = e.message
                                        ?.replace("\n", " ")
                                        ?.take(220)
                                        ?.takeIf { it.isNotBlank() }
                                    lastError =
                                        if (detail != null) "模型失敗：GEMINI·${opt.label}（${e.javaClass.simpleName}: $detail）"
                                        else "模型失敗：GEMINI·${opt.label}（${e.javaClass.simpleName}）"
                                    withContext(Dispatchers.Main) { updateProcessingText(lastError) }
                                } finally {
                                    ActiveChatContext.sessionId = null
                                    ActiveChatContext.geminiApiKey = null
                                    ActiveChatContext.geminiModel = null
                                }
                            }

                            "local" -> {
                                if (!LocalModelCatalog.isDownloaded(context, opt.model)) {
                                    lastError = "略過 地端·${opt.label}（GGUF 尚未下載）"
                                    continue
                                }
                                val placeholderId = UUID.randomUUID().toString()
                                try {
                                    withContext(Dispatchers.Main) {
                                        updateProcessingText("本機代理：${opt.label} (${opt.model})")
                                        messages.add(
                                            ChatMessage(
                                                id = placeholderId,
                                                role = "model",
                                                text = "…",
                                                createdAtMs = nowMs()
                                            )
                                        )
                                        streamedLocalModelMessageId = placeholderId
                                    }
                                    ActiveChatContext.sessionId = chatSid
                                    ActiveChatContext.geminiApiKey = null
                                    ActiveChatContext.geminiModel = null
                                    val memInject = try {
                                        memoryRepository.buildLocalTextOnlyInjection(chatSid)
                                    } catch (e: Exception) {
                                        Log.w("OpenRing", "Local memory injection failed", e)
                                        ""
                                    }
                                    val attachLocal = attachments.joinToString("\n\n") {
                                        ChatAttachmentModelParts.toLocalTextBlock(it)
                                    }
                                    val localUserText = when {
                                        attachLocal.isBlank() -> trimmed
                                        trimmed.isBlank() -> attachLocal
                                        else -> "$trimmed\n\n$attachLocal"
                                    }
                                    val squeezedBase = LocalLlmChatPrompt.squeezeSystemForTinyLlamaCatalog(
                                        opt.model,
                                        aiPromptStore.getSystemPrompt(),
                                    )
                                    val skillSection =
                                        SkillInstructionCatalog.buildPromptSection(context).trim()
                                            .takeIf { it.isNotBlank() }
                                    val localSystemPrompt = listOfNotNull(
                                        squeezedBase.trim().takeIf { it.isNotEmpty() },
                                        skillSection,
                                    ).joinToString("\n\n")
                                    val toolCatalog = ToolSchemas.buildLocalToolCatalogText(context)
                                    val r = localReActCoordinator.run(
                                        catalogId = opt.model,
                                        userText = localUserText,
                                        priorContents = priorContents,
                                        systemPrompt = localSystemPrompt,
                                        memoryInjection = memInject,
                                        toolCatalogText = toolCatalog,
                                        maxRounds = maxRounds,
                                        shouldCancel = { RunCancellationRegistry.isCancelled(runSessionId) },
                                        onTurn = { turn ->
                                            runScope.launch(Dispatchers.Main) {
                                                recordTurnToLog(turn)
                                            }
                                            runScope.launch(Dispatchers.IO) {
                                                val toolName = turn.toolName ?: return@launch
                                                when (turn.role) {
                                                    "tool_call" -> {
                                                        val args = turn.toolResult ?: buildJsonObject { }
                                                        chatRepository.appendExecutionLog(
                                                            chatSid,
                                                            ChatLogEntry.ToolCall(
                                                                toolName = toolName,
                                                                args = args,
                                                                createdAtMs = nowMs()
                                                            )
                                                        )
                                                    }

                                                    "tool_result" -> {
                                                        val resultObj = turn.toolResult ?: buildJsonObject { }
                                                        chatRepository.appendExecutionLog(
                                                            chatSid,
                                                            ChatLogEntry.ToolResult(
                                                                toolName = toolName,
                                                                result = sanitizeJsonForLog(toolName, resultObj),
                                                                createdAtMs = nowMs()
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        // 僅更新頂部處理列；勿覆寫助理氣泡（否則會把「本機代理 回合 N」等內部狀態寫進對話，與 typing 列重複且像錯誤回覆）。
                                        onStatus = { msg ->
                                            runScope.launch(Dispatchers.Main) {
                                                updateProcessingText(msg)
                                            }
                                        },
                                    )
                                    withContext(Dispatchers.Main) {
                                        val idx = messages.indexOfFirst { it.id == placeholderId }
                                        if (idx >= 0) {
                                            messages[idx] = messages[idx].copy(text = r.finalText)
                                        }
                                    }
                                    return@withContext r.finalText
                                } catch (e: Exception) {
                                    Log.e(
                                        "OpenRing",
                                        "Local model failed label=${opt.label} catalog=${opt.model}",
                                        e
                                    )
                                    withContext(Dispatchers.Main) {
                                        messages.removeAll { it.id == placeholderId }
                                        streamedLocalModelMessageId = null
                                    }
                                    val detail = e.message
                                        ?.replace("\n", " ")
                                        ?.take(220)
                                        ?.takeIf { it.isNotBlank() }
                                    lastError =
                                        if (detail != null) "地端失敗：${opt.label}（${e.javaClass.simpleName}: $detail）"
                                        else "地端失敗：${opt.label}（${e.javaClass.simpleName}）"
                                    withContext(Dispatchers.Main) { updateProcessingText(lastError) }
                                } finally {
                                    ActiveChatContext.sessionId = null
                                    ActiveChatContext.geminiApiKey = null
                                    ActiveChatContext.geminiModel = null
                                }
                            }

                            else -> {
                                lastError = "略過 ${opt.provider.uppercase()}·${opt.label}（尚未接入此供應商）"
                            }
                        }
                    }
                    lastError
                        ?: "所有模型都不可用：請到模型頁新增 Gemini（含 API Key）或地端模型（並完成下載），並確認清單順序。"
                }
                if (streamedLocalModelMessageId != null) {
                    withContext(Dispatchers.IO) {
                        chatRepository.addModelMessage(
                            chatSid,
                            streamedLocalModelMessageId!!,
                            resultText
                        )
                    }
                    val idx = messages.indexOfFirst { it.id == streamedLocalModelMessageId }
                    if (idx >= 0) {
                        val old = messages[idx]
                        messages[idx] = old.copy(text = resultText)
                    }
                } else {
                    val modelMsgId = UUID.randomUUID().toString()
                    val modelTs = nowMs()
                    withContext(Dispatchers.IO) {
                        chatRepository.addModelMessage(chatSid, modelMsgId, resultText)
                    }
                    messages.add(
                        ChatMessage(
                            id = modelMsgId,
                            role = "model",
                            text = resultText,
                            createdAtMs = modelTs
                        )
                    )
                }
                if (fromRelay) {
                    OpenRingCloudRelayBridge.trySendRelayChatReply(resultText)
                }
            } finally {
                AiRunNotification.cancel(context)
                if (Settings.canDrawOverlays(context)) {
                    try {
                        context.startService(
                            Intent(context, OverlayService::class.java).apply {
                                action = OverlayService.ACTION_STOP_AI_RUN
                            }
                        )
                    } catch (_: Exception) {
                        context.stopService(Intent(context, OverlayService::class.java))
                    }
                }
                RunCancellationRegistry.clear(runSessionId)
                runningSessionId = null
                running = false
                BackgroundWorkTracker.release(context)
                runScope.cancel()
            }
        }
    }

    LaunchedEffect(Unit) {
        CloudRelayTaskBus.tasks.collect { text ->
            val t = text.trim()
            Log.d("CloudRelayTask", "ChatScreen received relay len=${t.length}")
            if (t.isNotEmpty()) {
                startRun(t, tryStartOverlay = true, fromRelay = true)
            }
        }
    }

    fun openOverlayPermissionPage() {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            Log.w("OpenRing", "無法開啟懸浮窗權限設定頁", e)
            Toast.makeText(context, "無法開啟懸浮窗權限頁，請到系統設定手動開啟", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "OpenRing",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.widthIn(min = Spacing.sm))
                        val active = running || backgroundWorkCount > 0 || hasEnabledSchedule
                        val pulseTransition = rememberInfiniteTransition(label = "always_on_pulse")
                        val pulseScale by pulseTransition.animateFloat(
                            initialValue = 0.95f,
                            targetValue = 1.15f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 1200),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "always_on_scale"
                        )
                        val lampColor = if (active) Color(0xFF26D96A) else MaterialTheme.colorScheme.outlineVariant
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .scale(if (active) pulseScale else 1f)
                                .clip(CircleShape)
                                .background(lampColor)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (running) {
                                Toast.makeText(context, "執行中請稍候", Toast.LENGTH_SHORT).show()
                            } else {
                                sessionSheetOpen = true
                            }
                        }
                    ) {
                        Icon(Icons.Default.List, contentDescription = "聊天記錄列表")
                    }
                    IconButton(
                        onClick = {
                            if (running) {
                                Toast.makeText(context, "執行中無法開新對話", Toast.LENGTH_SHORT).show()
                            } else {
                                scope.launch(Dispatchers.IO) {
                                    val newId = chatRepository.createSessionAndSelect()
                                    withContext(Dispatchers.Main) {
                                        activeChatSessionId = newId
                                        messages.clear()
                                        ExecutionLogStore.clear()
                                        Toast.makeText(context, "已開始新對話", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "新對話")
                    }
                    Box {
                        IconButton(onClick = { moreMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = moreMenuExpanded,
                            onDismissRequest = { moreMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_workflows)) },
                                onClick = {
                                    moreMenuExpanded = false
                                    onNavigateToWorkflows()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Description, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_execution_log)) },
                                onClick = {
                                    moreMenuExpanded = false
                                    onNavigateToExecutionLog()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Tune, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_ai_settings)) },
                                onClick = {
                                    moreMenuExpanded = false
                                    onNavigateToSkills()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Apps, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_settings)) },
                                onClick = {
                                    moreMenuExpanded = false
                                    onNavigateToSettings()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Settings, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Chat 警告只顯示一個：優先提示「未選模型」，其次提示「未填 Key」
            val warning = when {
                modelChain.isEmpty() ->
                    "尚未新增任何模型：請到模型頁新增雲端模型（API Key）或地端模型（可設定多個作為備援）。"
                !hasGeminiWithKey && anyLocalNotDownloaded ->
                    "有地端模型尚未下載：請到模型頁在該模型旁點擊下載圖示，完成後再試。"
                !canRunChat ->
                    "目前無法開始聊天：請（1）為至少一個 Gemini 填入 API Key，或（2）新增地端模型並完成 GGUF 下載。"
                !hasGeminiWithKey && runnableLocal.isNotEmpty() ->
                    "目前僅使用本機文字模型：可對話，但不支援 ReAct／工具與雲端視覺。需要時請在模型頁加入 Gemini 並拖曳調整優先順序。"
                !hasGeminiWithKey ->
                    "尚未設定 Gemini API Key：若清單僅有地端模型請完成下載；若需雲端備援請新增 Gemini 並輸入 Key。"
                else -> null
            }
            if (warning != null) {
                val (container, content) =
                    MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md),
                    shape = MaterialTheme.shapes.large,
                    color = container,
                    contentColor = content
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            warning,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = onNavigateToSkills) {
                            Text(stringResource(R.string.go_to_models_page))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            ) {
                if (messages.isEmpty() && !running) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Spacing.lg),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "把任務丟給 OpenRing",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = "例如：建立一個工作流、整理資料、或請它執行一段腳本。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val maxBubbleWidth = maxWidth * 0.82f
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        if (running) {
                            item(key = "typing") {
                                AssistantTypingRow(processingText = processingText)
                            }
                        }

                        items(messages.asReversed(), key = { it.id }) { msg ->
                            val bubbleText = formatUserBubbleText(msg.text, msg.attachments)
                            MessageRow(
                                isUser = msg.role == "user",
                                timeText = formatTime(msg.createdAtMs),
                                text = bubbleText,
                                maxBubbleWidth = maxBubbleWidth,
                                onCopy = {
                                    clipboard.setText(AnnotatedString(bubbleText))
                                    Toast.makeText(context, "已複製訊息", Toast.LENGTH_SHORT).show()
                                },
                                onRerun = {
                                    if (msg.text.isBlank() && msg.attachments.isEmpty()) return@MessageRow
                                    if (running) {
                                        Toast.makeText(context, "目前正在執行，請稍後再重跑", Toast.LENGTH_SHORT).show()
                                        return@MessageRow
                                    }
                                    if (!canRunChat) {
                                        onNavigateToSkills()
                                        return@MessageRow
                                    }
                                    val rerunText = msg.text.trim()
                                    val rerunAttach = msg.attachments
                                    if (!Settings.canDrawOverlays(context)) {
                                        overlayPendingRun = rerunText to rerunAttach
                                    } else {
                                        startRun(rerunText, tryStartOverlay = true, attachments = rerunAttach)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.ime)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        // 須與 canRunChat 一致：僅地端模型時 runnableGemini 為空，但仍應可送出。
                        val canSend = !running && canRunChat && (input.isNotBlank() || pendingAttachments.isNotEmpty())
                        val canCancel = running && runningSessionId != null
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (pendingAttachments.isNotEmpty()) {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = Spacing.xs),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                ) {
                                    items(
                                        pendingAttachments,
                                        key = { a ->
                                            a.displayName + a.mimeType + (a.textContent?.length ?: 0) + (a.base64Data?.length ?: 0)
                                        }
                                    ) { a ->
                                        InputChip(
                                            selected = false,
                                            onClick = { pendingAttachments.remove(a) },
                                            label = {
                                                Text(
                                                    a.displayName,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            trailingIcon = {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = stringResource(R.string.chat_remove_attachment_cd)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.extraLarge)
                                    .background(MaterialTheme.colorScheme.surface),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(0.dp)
                            ) {
                            IconButton(
                                onClick = {
                                    if (running) {
                                        Toast.makeText(context, "執行中請稍候", Toast.LENGTH_SHORT).show()
                                    } else {
                                        pickAttachmentsLauncher.launch(arrayOf("*/*"))
                                    }
                                },
                                enabled = !running,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.AttachFile,
                                    contentDescription = stringResource(R.string.chat_attach_files_cd)
                                )
                            }
                            TextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.weight(1f),
                                enabled = !running,
                                placeholder = { Text("輸入任務…") },
                                maxLines = 4,
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    errorIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    disabledContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )

                            Surface(
                                modifier = Modifier
                                    .padding(end = Spacing.xs)
                                    .size(40.dp),
                                shape = MaterialTheme.shapes.extraLarge,
                                color = if (canSend || canCancel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (canSend || canCancel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                IconButton(
                                    enabled = canSend || canCancel,
                                    onClick = {
                                        if (running && runningSessionId != null) {
                                            RunCancellationRegistry.cancelAll()
                                            RunCancellationRegistry.cancel(runningSessionId!!)
                                            AiRunNotification.cancel(context)
                                            try {
                                                context.startService(
                                                    Intent(context, OverlayService::class.java).apply {
                                                        action = OverlayService.ACTION_STOP_AI_RUN
                                                    }
                                                )
                                            } catch (_: Exception) {
                                                context.stopService(Intent(context, OverlayService::class.java))
                                            }
                                            updateProcessingText("已送出中斷要求，正在停止…")
                                            return@IconButton
                                        }
                                        if (!canRunChat) {
                                            onNavigateToSkills()
                                            return@IconButton
                                        }
                                        val text = input.trim()
                                        val attachSnap = pendingAttachments.toList()
                                        if (text.isBlank() && attachSnap.isEmpty()) return@IconButton
                                        input = ""
                                        pendingAttachments.clear()
                                        if (!Settings.canDrawOverlays(context)) {
                                            overlayPendingRun = text to attachSnap
                                        } else {
                                            startRun(text, tryStartOverlay = true, attachments = attachSnap)
                                        }
                                    }
                                ) {
                                    Icon(
                                        if (running) Icons.Default.Close else Icons.Default.Send,
                                        contentDescription = if (running) "中斷" else "送出"
                                    )
                                }
                            }
                            }
                        }
                    }
                }
            }
        }
    }

    if (overlayPendingRun != null) {
        AlertDialog(
            onDismissRequest = { overlayPendingRun = null },
            title = { Text("需要懸浮窗權限") },
            text = {
                Text("要顯示 AI 執行中的懸浮中斷按鈕，需先開啟「顯示在其他應用程式上層」。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        openOverlayPermissionPage()
                        val pending = overlayPendingRun
                        overlayPendingRun = null
                        if (pending != null) {
                            startRun(pending.first, tryStartOverlay = false, attachments = pending.second)
                        }
                    }
                ) { Text("前往設定") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val pending = overlayPendingRun
                        overlayPendingRun = null
                        if (pending != null) {
                            startRun(pending.first, tryStartOverlay = false, attachments = pending.second)
                        }
                    }
                ) { Text("先繼續") }
            }
        )
    }

    permissionReminder?.let { reminder ->
        AlertDialog(
            onDismissRequest = { permissionReminder = null },
            title = { Text(reminder.title) },
            text = { Text(reminder.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        permissionReminder = null
                        onNavigateToPermissions()
                    }
                ) { Text("前往權限設定") }
            },
            dismissButton = {
                TextButton(onClick = { permissionReminder = null }) {
                    Text("稍後再說")
                }
            }
        )
    }

    sessionPendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { sessionPendingDelete = null },
            title = { Text("刪除聊天記錄") },
            text = {
                Text(
                    "確定要刪除「${pending.title.ifBlank { "未命名對話" }}」？此操作無法復原。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = pending
                        sessionPendingDelete = null
                        scope.launch {
                            val wasViewing = activeChatSessionId == target.id
                            val newActiveId: String
                            val refreshed: List<ChatSession>
                            val rows: List<ChatMessageEntity>
                            val logs: List<ChatLogEntry>
                            withContext(Dispatchers.IO) {
                                newActiveId = chatRepository.deleteSession(target.id)
                                refreshed = chatRepository.listSessions(100)
                                if (wasViewing) {
                                    rows = chatRepository.getMessages(newActiveId)
                                    logs = chatRepository.loadExecutionLog(newActiveId)
                                } else {
                                    rows = emptyList()
                                    logs = emptyList()
                                }
                            }
                            sessionsForPicker = refreshed
                            if (wasViewing) {
                                activeChatSessionId = newActiveId
                                messages.clear()
                                for (m in rows) {
                                    messages.add(
                                        ChatMessage(
                                            id = m.id,
                                            role = m.role,
                                            text = m.body,
                                            createdAtMs = m.createdAtMs,
                                            attachments = chatRepository.parseAttachments(m.attachmentsJson)
                                        )
                                    )
                                }
                                ExecutionLogStore.replaceAll(logs)
                            }
                            Toast.makeText(context, "已刪除聊天記錄", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("刪除") }
            },
            dismissButton = {
                TextButton(onClick = { sessionPendingDelete = null }) { Text("取消") }
            }
        )
    }

    if (sessionSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sessionSheetOpen = false },
            sheetState = sessionSheetState
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                items(sessionsForPicker, key = { it.id }) { s ->
                    ListItem(
                        headlineContent = {
                            Text(
                                s.title.ifBlank { "未命名對話" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = { Text(formatTime(s.updatedAtMs)) },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    if (running) {
                                        Toast.makeText(context, "執行中請稍候", Toast.LENGTH_SHORT).show()
                                    } else {
                                        sessionPendingDelete = s
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "刪除此聊天記錄")
                            }
                        },
                        modifier = Modifier.clickable {
                            if (running) {
                                Toast.makeText(context, "執行中請稍候", Toast.LENGTH_SHORT).show()
                            } else {
                                scope.launch(Dispatchers.IO) {
                                    chatRepository.activateSession(s.id)
                                    val rows = chatRepository.getMessages(s.id)
                                    val logs = chatRepository.loadExecutionLog(s.id)
                                    withContext(Dispatchers.Main) {
                                        activeChatSessionId = s.id
                                        messages.clear()
                                        for (m in rows) {
                                            messages.add(
                                                ChatMessage(
                                                    id = m.id,
                                                    role = m.role,
                                                    text = m.body,
                                                    createdAtMs = m.createdAtMs,
                                                    attachments = chatRepository.parseAttachments(m.attachmentsJson)
                                                )
                                            )
                                        }
                                        ExecutionLogStore.replaceAll(logs)
                                        sessionSheetOpen = false
                                    }
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AssistantTypingRow(processingText: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        AssistantAvatar()
        Spacer(modifier = Modifier.widthIn(min = Spacing.sm))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.large,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.widthIn(min = Spacing.sm))
                Column {
                    Text(
                        text = "正在處理中…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = processingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun JsonElementView(
    element: JsonElement,
    depth: Int,
    path: String,
    maxDepth: Int = 8,
    maxChildren: Int = 30
) {
    if (depth > maxDepth) {
        Text(text = "...", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        return
    }

    val indentPx = (depth * 12).dp
    when (element) {
        is JsonObject -> {
            val entries = element.entries.toList()
            val canAutoExpand = depth <= 1 && entries.size <= 8
            var expanded by remember(path) { mutableStateOf(canAutoExpand) }
            if (depth >= maxDepth) {
                Text(
                    text = "{...}",
                    modifier = Modifier.padding(start = indentPx),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return
            }
            Row(
                modifier = Modifier
                    .padding(start = indentPx)
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "[-]" else "[+]",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.widthIn(min = Spacing.xs))
                Text(
                    text = "{... (${entries.size})}",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!expanded) return
            if (entries.isEmpty()) {
                Text(
                    text = "{}",
                    modifier = Modifier.padding(start = indentPx),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return
            }
            val renderEntries = if (entries.size > maxChildren) entries.take(maxChildren) else entries
            Column {
                renderEntries.forEachIndexed { idx, entry ->
                    Column(modifier = Modifier.padding(start = indentPx)) {
                        Text(
                            text = "\"${entry.key}\":",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        JsonElementView(
                            element = entry.value,
                            depth = depth + 1,
                            path = "${path}.${entry.key}"
                        )
                    }
                    if (idx == renderEntries.lastIndex && entries.size > maxChildren) {
                        Text(
                            text = "... (omitted ${entries.size - maxChildren} keys)",
                            modifier = Modifier.padding(start = indentPx),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        is JsonArray -> {
            val canAutoExpand = depth <= 1 && element.size <= 8
            var expanded by remember(path) { mutableStateOf(canAutoExpand) }
            if (depth >= maxDepth) {
                Text(
                    text = "[...]",
                    modifier = Modifier.padding(start = indentPx),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return
            }
            Row(
                modifier = Modifier
                    .padding(start = indentPx)
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "[-]" else "[+]",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.widthIn(min = Spacing.xs))
                Text(
                    text = "[... (${element.size})]",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!expanded) return
            if (element.isEmpty()) {
                Text(
                    text = "[]",
                    modifier = Modifier.padding(start = indentPx),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return
            }

            val renderItems = if (element.size > maxChildren) element.take(maxChildren) else element
            Column {
                renderItems.forEachIndexed { idx, value ->
                    Column(modifier = Modifier.padding(start = indentPx)) {
                        Text(
                            text = "[$idx]",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        JsonElementView(
                            element = value,
                            depth = depth + 1,
                            path = "${path}[$idx]"
                        )
                    }
                    if (idx == renderItems.lastIndex && element.size > maxChildren) {
                        Text(
                            text = "... (omitted ${element.size - maxChildren} items)",
                            modifier = Modifier.padding(start = indentPx),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        is JsonPrimitive -> {
            val content = element.content
            val shown = if (content.length > 240) content.take(240) + "..." else content
            Text(
                text = shown,
                modifier = Modifier.padding(start = indentPx),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        is JsonNull -> {
            Text(
                text = "null",
                modifier = Modifier.padding(start = indentPx),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatUserBubbleText(text: String, attachments: List<ChatAttachmentPayload>): String {
    if (attachments.isEmpty()) return text
    val names = attachments.joinToString(", ") { it.displayName }
    return buildString {
        append("📎 ")
        append(names)
        if (text.isNotBlank()) {
            append("\n\n")
            append(text)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MessageRow(
    isUser: Boolean,
    timeText: String,
    text: String,
    maxBubbleWidth: androidx.compose.ui.unit.Dp,
    onCopy: () -> Unit,
    onRerun: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            AssistantAvatar()
            Spacer(modifier = Modifier.widthIn(min = Spacing.sm))
        }

        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            val bubbleShape = if (isUser) {
                androidx.compose.foundation.shape.RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomEnd = 6.dp,
                    bottomStart = 18.dp
                )
            } else {
                androidx.compose.foundation.shape.RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomEnd = 18.dp,
                    bottomStart = 6.dp
                )
            }

            Surface(
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = bubbleShape,
                tonalElevation = if (isUser) 0.dp else 1.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { menuExpanded = true }
                )
            ) {
                Text(
                    text = text,
                    modifier = Modifier
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                        .widthIn(max = maxBubbleWidth),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("複製該訊息") },
                    onClick = {
                        menuExpanded = false
                        onCopy()
                    }
                )
                DropdownMenuItem(
                    text = { Text("重跑") },
                    onClick = {
                        menuExpanded = false
                        onRerun()
                    }
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AssistantAvatar() {
    Surface(
        shape = CircleShape,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground_art),
            contentDescription = "OpenRing logo",
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
        )
    }
}

