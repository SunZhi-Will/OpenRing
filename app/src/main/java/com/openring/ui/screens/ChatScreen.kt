package com.openring.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.openring.agent.ChatLogEntry
import com.openring.agent.ExecutionLogStore
import com.openring.agent.ReActCoordinator
import com.openring.agent.RunCancellationRegistry
import com.openring.core.OverlayService
import com.openring.security.ApiKeyStore
import com.openring.settings.ModelStore
import com.openring.ui.notifications.AiRunNotification
import com.openring.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.Date
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToWorkflows: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToExecutionLog: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val coordinator = remember { ReActCoordinator(context) }
    val keyStore = remember { ApiKeyStore(context) }
    val modelStore = remember { ModelStore(context) }
    val modelChain = modelStore.getModels()
    val runnableGemini = modelChain.filter {
        it.provider == "gemini" && keyStore.getGeminiApiKeyForModel(it.id).isNullOrBlank().not()
    }

    data class ChatMessage(
        val id: String,
        val role: String,
        val text: String,
        val createdAtMs: Long
    )

    fun nowMs(): Long = System.currentTimeMillis()
    val timeFormatter = remember(context) { DateFormat.getTimeFormat(context) }
    fun formatTime(ts: Long): String = timeFormatter.format(Date(ts))

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var runningSessionId by remember { mutableStateOf<String?>(null) }
    var overlayPermissionDialogText by remember { mutableStateOf<String?>(null) }
    var processingText by remember { mutableStateOf("正在處理中…") }

    fun sanitizeJsonForLog(toolName: String, json: JsonObject): JsonObject {
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
                processingText = if (ok == true) {
                    "工具結果：$toolName（成功）"
                } else {
                    val code = (turn.toolResult?.get("code") as? JsonPrimitive)?.content
                    val msg = (turn.toolResult?.get("message") as? JsonPrimitive)?.content
                    val detail = listOfNotNull(
                        code,
                        msg?.takeIf { it.isNotBlank() }
                    ).joinToString(" / ").take(80)
                    "工具結果：$toolName（失敗${if (detail.isNotBlank()) ": $detail" else ""}）"
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

    fun startRun(text: String, tryStartOverlay: Boolean) {
        if (running || text.isBlank() || runnableGemini.isEmpty()) return
        ExecutionLogStore.clear()
        processingText = "正在處理中…"
        messages.add(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                role = "user",
                text = text,
                createdAtMs = nowMs()
            )
        )
        running = true
        val sessionId = UUID.randomUUID().toString()
        runningSessionId = sessionId
        RunCancellationRegistry.register(sessionId)
        AiRunNotification.show(context, sessionId)
        if (tryStartOverlay && Settings.canDrawOverlays(context)) {
            val overlayIntent = Intent(context, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START_AI_RUN
                putExtra(OverlayService.EXTRA_SESSION_ID, sessionId)
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
        scope.launch {
            try {
                val resultText = withContext(Dispatchers.IO) {
                    var lastError: String? = null
                    for (opt in modelChain) {
                        val key = keyStore.getGeminiApiKeyForModel(opt.id).orEmpty()
                        if (key.isBlank()) continue
                        if (opt.provider != "gemini") {
                            lastError = "略過 ${opt.provider.uppercase()}·${opt.label}（尚未接入此供應商）"
                            continue
                        }
                        try {
                            updateProcessingText("嘗試模型：GEMINI·${opt.label} (${opt.model})")
                            val r = coordinator.run(
                                apiKey = key,
                                model = opt.model,
                                userText = text,
                                shouldCancel = { RunCancellationRegistry.isCancelled(sessionId) },
                                onTurn = { turn ->
                                    scope.launch(Dispatchers.Main) { recordTurnToLog(turn) }
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
                            updateProcessingText(lastError)
                        }
                    }
                    lastError ?: "所有模型都不可用：請到設定新增模型並輸入 API Key。"
                }
                messages.add(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = "model",
                        text = resultText,
                        createdAtMs = nowMs()
                    )
                )
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
                RunCancellationRegistry.clear(sessionId)
                runningSessionId = null
                running = false
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
                    Column {
                        Text(
                            "OpenRing",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Chat-Driven OS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToWorkflows) {
                        Icon(Icons.Default.Description, contentDescription = "排程 / 工作流")
                    }
                    IconButton(onClick = onNavigateToExecutionLog) {
                        Icon(Icons.Default.Tune, contentDescription = "執行 Log")
                    }
                    IconButton(onClick = onNavigateToSkills) {
                        Icon(Icons.Default.Apps, contentDescription = "技能中心")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
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
            val warning =
                if (modelChain.isEmpty()) "尚未新增任何模型：請到設定新增模型與 API Key（可設定多個作為備援）。"
                else if (runnableGemini.isEmpty()) "尚未設定可用模型的 API Key：請到設定為至少一個 Gemini 模型輸入 API Key（將依清單順序自動備援）。"
                else null
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
                        TextButton(onClick = onNavigateToSettings) {
                            Text("前往設定")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
            }
            if (!Settings.canDrawOverlays(context)) {
                val (container, content) =
                    MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
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
                            "尚未開啟懸浮窗權限：無法顯示 AI 執行中的懸浮中斷按鈕。",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { openOverlayPermissionPage() }) {
                            Text("開啟權限")
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
                            MessageRow(
                                isUser = msg.role == "user",
                                timeText = formatTime(msg.createdAtMs),
                                text = msg.text,
                                maxBubbleWidth = maxBubbleWidth,
                                onCopy = {
                                    clipboard.setText(AnnotatedString(msg.text))
                                    Toast.makeText(context, "已複製訊息", Toast.LENGTH_SHORT).show()
                                },
                                onRerun = {
                                    if (msg.text.isBlank()) return@MessageRow
                                    if (running) {
                                        Toast.makeText(context, "目前正在執行，請稍後再重跑", Toast.LENGTH_SHORT).show()
                                        return@MessageRow
                                    }
                                    if (runnableGemini.isEmpty()) {
                                        onNavigateToSettings()
                                        return@MessageRow
                                    }
                                    val rerunText = msg.text.trim()
                                    if (!Settings.canDrawOverlays(context)) {
                                        overlayPermissionDialogText = rerunText
                                    } else {
                                        startRun(rerunText, tryStartOverlay = true)
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
                        val canSend = !running && input.isNotBlank() && runnableGemini.isNotEmpty()
                        val canCancel = running && runningSessionId != null
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraLarge)
                                .background(MaterialTheme.colorScheme.surface),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            TextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = Spacing.sm),
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
                                            RunCancellationRegistry.cancel(runningSessionId!!)
                                            updateProcessingText("已送出中斷要求，正在停止…")
                                            return@IconButton
                                        }
                                        if (runnableGemini.isEmpty()) {
                                            onNavigateToSettings()
                                            return@IconButton
                                        }
                                        val text = input.trim()
                                        input = ""
                                        if (!Settings.canDrawOverlays(context)) {
                                            overlayPermissionDialogText = text
                                        } else {
                                            startRun(text, tryStartOverlay = true)
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

    if (overlayPermissionDialogText != null) {
        AlertDialog(
            onDismissRequest = { overlayPermissionDialogText = null },
            title = { Text("需要懸浮窗權限") },
            text = {
                Text("要顯示 AI 執行中的懸浮中斷按鈕，需先開啟「顯示在其他應用程式上層」。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        openOverlayPermissionPage()
                        val text = overlayPermissionDialogText
                        overlayPermissionDialogText = null
                        if (!text.isNullOrBlank()) startRun(text, tryStartOverlay = false)
                    }
                ) { Text("前往設定") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val text = overlayPermissionDialogText
                        overlayPermissionDialogText = null
                        if (!text.isNullOrBlank()) startRun(text, tryStartOverlay = false)
                    }
                ) { Text("先繼續") }
            }
        )
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
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "OR",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

