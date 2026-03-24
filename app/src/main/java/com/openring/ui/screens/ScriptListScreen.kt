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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openring.R
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
                title = { Text(stringResource(R.string.script_list_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.script_list_back_to_chat))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showTemplateDialog = true },
                        enabled = templateEntries.isNotEmpty()
                    ) {
                        Icon(Icons.Default.PostAdd, contentDescription = stringResource(R.string.script_list_create_from_template))
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = stringResource(R.string.script_list_execution_history))
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
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.script_list_add_workflow))
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
                            stringResource(R.string.script_list_accessibility_required_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            stringResource(R.string.script_list_accessibility_required_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        TextButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        ) {
                            Text(stringResource(R.string.script_list_go_to_settings))
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
                        stringResource(R.string.script_list_empty_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        stringResource(R.string.script_list_empty_subtitle),
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
            title = { Text(stringResource(R.string.script_list_create_from_template)) },
            text = {
                Column {
                    if (templateEntries.isEmpty()) {
                        Text(stringResource(R.string.script_list_no_builtin_templates))
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
                    Text(stringResource(R.string.close))
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
        !schedule.enabled -> stringResource(R.string.script_schedule_none)
        schedule.type == "daily" -> stringResource(
            R.string.script_schedule_daily,
            schedule.hour,
            schedule.minute.toString().padStart(2, '0')
        )
        schedule.type == "hourly" -> stringResource(R.string.script_schedule_hourly, schedule.minute)
        schedule.type == "interval" -> stringResource(R.string.script_schedule_interval, schedule.minutes)
        else -> stringResource(R.string.script_schedule_none)
    }
    val modeBadge = when {
        !schedule.enabled -> null
        schedule.mode == "exact" -> stringResource(R.string.script_schedule_mode_exact)
        schedule.mode == "always_on" -> stringResource(R.string.script_schedule_mode_always_on)
        else -> null
    }

    var showContextMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.script_delete_title)) },
            text = { Text(stringResource(R.string.script_delete_confirm, script.name)) },
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
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
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
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.script_delete_icon_desc), tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = onRun) {
                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.script_run_icon_desc), tint = MaterialTheme.colorScheme.primary)
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
                        contentDescription = if (schedule.enabled) {
                            stringResource(R.string.script_schedule_toggle_off)
                        } else {
                            stringResource(R.string.script_schedule_toggle_on)
                        },
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
                text = { Text(stringResource(R.string.edit)) },
                onClick = {
                    showContextMenu = false
                    onClick()
                },
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
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
