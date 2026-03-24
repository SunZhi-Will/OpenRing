package com.openring.ui.screens

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import com.openring.core.OpenRingAccessibilityService
import com.openring.ui.components.AppIcon
import com.openring.ui.theme.Spacing
import com.openring.data.model.Script
import com.openring.data.db.OpenRingDatabase
import com.openring.data.ScriptStore
import com.openring.domain.Scheduler
import com.openring.domain.ScriptExecutor
import com.openring.workflow.WorkflowTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ScriptListScreen(
    onBack: () -> Unit,
    onNavigateToEditor: (String?) -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = OpenRingDatabase.getDatabase(context)
    val scriptStore = ScriptStore(db.scriptDao())
    val scheduler = remember { Scheduler(context) }
    val json = remember { Json { ignoreUnknownKeys = true } }
    val scripts by scriptStore.allScripts.collectAsState(initial = emptyList())
    val templateEntries = remember { WorkflowTemplates.listEntries(context) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var isAccessibilityEnabled by remember { mutableStateOf(OpenRingAccessibilityService.isEnabled()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = OpenRingAccessibilityService.isEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("工作流", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回聊天")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showTemplateDialog = true },
                        enabled = templateEntries.isNotEmpty()
                    ) {
                        Icon(Icons.Default.PostAdd, contentDescription = "從範本建立")
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "執行歷史")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToEditor(null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增工作流")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!isAccessibilityEnabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            "請啟用無障礙服務",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            "OpenRing 需要無障礙權限以讀取畫面並執行自動化操作",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        TextButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        ) {
                            Text("前往設定")
                        }
                    }
                }
            }

            if (scripts.isEmpty() && isAccessibilityEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.xl),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.padding(Spacing.lg),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    Text(
                        "尚無工作流",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        "點擊右下角 + 按鈕建立第一個工作流",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md)
            ) {
                items(scripts, key = { it.id }) { script ->
                    ScriptItem(
                        script = script,
                        scriptStore = scriptStore,
                        onClick = { onNavigateToEditor(script.id) },
                        onDelete = {
                            scope.launch {
                                scheduler.cancelScript(script.id)
                                withContext(Dispatchers.IO) {
                                    scriptStore.deleteScript(script.id)
                                }
                            }
                        },
                        onRun = {
                            scope.launch {
                                try {
                                    Log.d("OpenRing", "開始執行腳本: ${script.name}")
                                    val executor = ScriptExecutor(context, db.executionHistoryDao())
                                    val result = withContext(Dispatchers.Default) {
                                        executor.execute(script)
                                    }
                                    when (result) {
                                        is com.openring.domain.ScriptExecutor.ExecutionResult.Success ->
                                            Log.d("OpenRing", "腳本執行完成: 成功")
                                        is com.openring.domain.ScriptExecutor.ExecutionResult.Failure ->
                                            Log.e("OpenRing", "腳本執行完成: 失敗 step=${result.stepIndex} error=${result.error}")
                                    }
                                } catch (e: Exception) {
                                    Log.e("OpenRing", "執行崩潰", e)
                                    e.printStackTrace()
                                }
                            }
                        },
                        onToggleSchedule = {
                            scope.launch {
                                val currentSchedule = scriptStore.parseSchedule(script.scheduleJson)
                                if (currentSchedule.type == "disabled") return@launch
                                val updatedSchedule = currentSchedule.copy(enabled = !currentSchedule.enabled)
                                withContext(Dispatchers.IO) {
                                    scriptStore.updateScript(
                                        script.copy(
                                            scheduleJson = json.encodeToString(updatedSchedule)
                                        )
                                    )
                                    if (updatedSchedule.enabled) {
                                        scheduler.scheduleScript(script.id, updatedSchedule)
                                    } else {
                                        scheduler.cancelScript(script.id)
                                    }
                                }
                            }
                        }
                    )
                }
            }
            }
        }
    }

    if (showTemplateDialog) {
        AlertDialog(
            onDismissRequest = { showTemplateDialog = false },
            title = { Text("從範本建立") },
            text = {
                Column {
                    if (templateEntries.isEmpty()) {
                        Text("尚無內建範本。")
                    } else {
                        templateEntries.forEach { entry ->
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val t = withContext(Dispatchers.IO) {
                                            WorkflowTemplates.loadTemplate(context, entry.file)
                                        }
                                        if (t == null) return@launch
                                        val script = withContext(Dispatchers.IO) {
                                            scriptStore.insertScript(t.name, t.steps, t.schedule)
                                        }
                                        withContext(Dispatchers.Main) {
                                            showTemplateDialog = false
                                            onNavigateToEditor(script.id)
                                        }
                                    }
                                }
                            ) {
                                Text(entry.title)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTemplateDialog = false }) {
                    Text("關閉")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScriptItem(
    script: Script,
    scriptStore: ScriptStore,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRun: () -> Unit,
    onToggleSchedule: () -> Unit
) {
    val schedule = scriptStore.parseSchedule(script.scheduleJson)
    val scheduleText = when {
        !schedule.enabled -> "未排程"
        schedule.type == "daily" -> "每日 ${schedule.hour}:${schedule.minute.toString().padStart(2, '0')}"
        schedule.type == "hourly" -> "每小時 ${schedule.minute} 分"
        schedule.type == "interval" -> "每 ${schedule.minutes} 分鐘"
        else -> "未排程"
    }
    val modeBadge = when {
        !schedule.enabled -> null
        schedule.mode == "exact" -> "精準"
        schedule.mode == "always_on" -> "常駐"
        else -> null
    }

    var showContextMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("刪除腳本") },
            text = { Text("確定要刪除「${script.name}」嗎？此操作無法復原。") },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("刪除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showContextMenu = true }
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val firstAppPkg = scriptStore.parseSteps(script.stepsJson)
                    .firstOrNull { it.type == "launch_app" }
                    ?.params?.get("package")
                    ?.takeIf { it.isNotBlank() && it != "__custom__" }
                if (firstAppPkg != null) {
                    AppIcon(packageName = firstAppPkg, size = 44.dp, modifier = Modifier.padding(end = Spacing.sm))
                } else {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .padding(end = Spacing.sm),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(script.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (modeBadge != null) "$scheduleText ・$modeBadge" else scheduleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "刪除", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onRun) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "執行", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(
                    onClick = onToggleSchedule,
                    enabled = schedule.type != "disabled"
                ) {
                    val enabledColor = if (schedule.enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Icon(
                        Icons.Default.History,
                        contentDescription = if (schedule.enabled) "關閉排程" else "啟用排程",
                        tint = enabledColor
                    )
                }
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("編輯") },
                onClick = {
                    showContextMenu = false
                    onClick()
                },
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("刪除") },
                onClick = {
                    showContextMenu = false
                    showDeleteConfirm = true
                },
                leadingIcon = {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            )
        }
    }
}
