package com.openring.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.window.PopupProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.openring.core.InstalledAppsProvider
import com.openring.data.db.OpenRingDatabase
import com.openring.data.model.Schedule
import com.openring.data.model.Script
import com.openring.data.model.ScriptStep
import com.openring.data.ScriptStore
import com.openring.domain.Scheduler
import com.openring.ui.components.AppIcon
import com.openring.ui.components.AppPickerSheet
import com.openring.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEditorScreen(
    scriptId: String?,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = OpenRingDatabase.getDatabase(context)
    val scriptStore = ScriptStore(db.scriptDao())
    val scheduler = Scheduler(context)

    var name by remember(scriptId) { mutableStateOf("") }
    val steps = remember(scriptId) { mutableStateListOf<ScriptStep>() }
    var schedule by remember(scriptId) { mutableStateOf(Schedule()) }
    var installedApps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(Unit) {
        installedApps = withContext(Dispatchers.IO) {
            InstalledAppsProvider.getInstalledLauncherApps(context)
        }
    }
    LaunchedEffect(scriptId) {
        if (scriptId != null && scriptId != "new") {
            val script = withContext(Dispatchers.IO) { scriptStore.getScript(scriptId) }
            script?.let {
                name = it.name
                steps.clear()
                steps.addAll(scriptStore.parseSteps(it.stepsJson))
                schedule = scriptStore.parseSchedule(it.scheduleJson)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (scriptId == null || scriptId == "new") "新增工作流" else "編輯工作流") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    scope.launch {
                        val json = Json { ignoreUnknownKeys = true }
                        // 轉成一般 List 再序列化，避免 SnapshotStateList 無法序列化
                        val stepsList = steps.toList()
                        val stepsJson = json.encodeToString(stepsList)
                        val scheduleJson = json.encodeToString(schedule)
                        withContext(Dispatchers.IO) {
                            if (scriptId == null || scriptId == "new") {
                                val script = scriptStore.insertScript(name, stepsList, schedule)
                                scheduler.scheduleScript(script.id, schedule)
                            } else {
                                val script = Script(scriptId!!, name, 1, stepsJson, scheduleJson)
                                scriptStore.updateScript(script)
                                scheduler.scheduleScript(scriptId, schedule)
                            }
                        }
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                Text("儲存")
            }
        }
    ) { padding ->
        // 使用 LazyColumn 作為唯一可滾動容器，避免 Column(verticalScroll) + LazyColumn 巢狀導致的無限高度錯誤
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("工作流名稱") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item { Spacer(modifier = Modifier.height(Spacing.lg)) }
            item {
                ScheduleSection(
                    schedule = schedule,
                    onScheduleChange = { schedule = it }
                )
            }
            item { Spacer(modifier = Modifier.height(Spacing.lg)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("步驟", style = MaterialTheme.typography.titleMedium)
                    if (scriptId == null || scriptId == "new") {
                        TextButton(
                            onClick = {
                                name = "Threads 瀏覽範例"
                                steps.clear()
                                steps.addAll(
                                    listOf(
                                        ScriptStep("launch_app", mapOf("package" to "com.instagram.barcelona")),
                                        ScriptStep("wait", mapOf("ms" to "3000")),
                                        ScriptStep("swipe", mapOf("direction" to "up", "distance" to "500")),
                                        ScriptStep("wait", mapOf("ms" to "2000")),
                                        ScriptStep("swipe", mapOf("direction" to "up", "distance" to "500"))
                                    )
                                )
                            }
                        ) {
                            Text("使用範例")
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(Spacing.sm)) }
            itemsIndexed(steps) { index, step ->
                StepCard(
                    stepNumber = index + 1,
                    step = step,
                    installedApps = installedApps,
                    onDelete = { steps.removeAt(index) },
                    onUpdate = { steps[index] = it }
                )
            }
            item {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        steps.add(ScriptStep("wait", mapOf("ms" to "1000")))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.padding(Spacing.sm))
                    Text("新增步驟")
                }
            }
            item { Spacer(modifier = Modifier.height(Spacing.sm)) }
        }
    }
}

private val STEP_TYPES = listOf(
    "ai_action", "wait", "launch_app", "find_and_click", "click_node", "swipe", "long_press",
    "back", "home", "extract_text"
)

private val FIND_MATCH_OPTIONS = listOf(
    "contains" to "包含（contains）",
    "exact" to "完全相符（exact）",
)

private val SWIPE_DIRECTION_OPTIONS = listOf(
    "up" to "上（up）",
    "down" to "下（down）",
    "left" to "左（left）",
    "right" to "右（right）",
)

private const val CUSTOM_OPTION_KEY = "__custom__"

private val SWIPE_DISTANCE_OPTIONS = listOf(
    "200" to "200",
    "300" to "300",
    "500" to "500",
    "800" to "800",
    CUSTOM_OPTION_KEY to "自訂…",
)

private fun stepTypeLabel(type: String): String = when (type) {
    "ai_action" -> "AI 指令"
    "wait" -> "等待"
    "launch_app" -> "開啟 App"
    "find_and_click" -> "依文字點擊"
    "click_node" -> "依節點點擊"
    "swipe" -> "滑動"
    "long_press" -> "長按"
    "back" -> "返回鍵"
    "home" -> "Home 鍵"
    "extract_text" -> "擷取文字"
    else -> type
}

private fun stepTypeDescription(type: String): String = when (type) {
    "wait" -> "等待 N 毫秒，讓畫面載入"
    "launch_app" -> "開啟指定 App"
    "find_and_click" -> "依畫面上的文字找到並點擊"
    "click_node" -> "依節點 ID 點擊（進階）"
    "swipe" -> "滑動畫面（上/下/左/右）"
    "long_press" -> "長按某元素"
    "back" -> "按返回鍵"
    "home" -> "按 Home 鍵回桌面"
    "extract_text" -> "擷取畫面上某處文字"
    else -> type
}

private const val CUSTOM_PACKAGE_KEY = "__custom__"  // 與 ScriptExecutor 檢查一致

private fun defaultParamsForType(type: String): Map<String, String> = when (type) {
    "ai_action" -> mapOf("prompt" to "")
    "wait" -> mapOf("ms" to "1000")
    "launch_app" -> mapOf("package" to "")
    "find_and_click" -> mapOf("text" to "", "match" to "contains")
    "click_node" -> mapOf("nodeId" to "")
    "swipe" -> mapOf("direction" to "down", "distance" to "300")
    "long_press" -> mapOf("text" to "")
    "extract_text" -> mapOf("nodeId" to "", "variable" to "")
    else -> emptyMap()
}

@Composable
private fun anchorTextFieldColors() = OutlinedTextFieldDefaults.colors(
    disabledTextColor = MaterialTheme.colorScheme.onSurface,
    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun StepCard(
    stepNumber: Int,
    step: ScriptStep,
    installedApps: List<Pair<String, String>>,
    onDelete: () -> Unit,
    onUpdate: (ScriptStep) -> Unit
) {
    var params by remember(step) { mutableStateOf(step.params.toMutableMap()) }
    val currentType = step.type
    var typeExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$stepNumber.", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "刪除")
                }
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { typeExpanded = !typeExpanded }
                ) {
                    OutlinedTextField(
                        value = stepTypeLabel(currentType),
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("步驟類型") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.graphicsLayer { rotationZ = if (typeExpanded) 90f else 0f }
                            )
                        },
                        colors = anchorTextFieldColors()
                    )
                }
                DropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false },
                    modifier = Modifier.requiredSizeIn(maxHeight = 240.dp),
                    properties = PopupProperties(focusable = false, dismissOnClickOutside = true)
                ) {
                    STEP_TYPES.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(stepTypeLabel(type)) },
                            onClick = {
                                val newParams = defaultParamsForType(type).toMutableMap()
                                params = newParams
                                onUpdate(ScriptStep(type, newParams))
                                typeExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            when (currentType) {
                "ai_action" -> {
                    OutlinedTextField(
                        value = params["prompt"] ?: "",
                        onValueChange = { params["prompt"] = it; onUpdate(step.copy(params = params)) },
                        label = { Text("要執行的 AI 指令 (如: 抓取畫面上 BTC 的價格並記錄)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 5,
                        minLines = 2
                    )
                }
                "launch_app" -> {
                    val pkg = params["package"] ?: ""
                    val selectedApp = installedApps.find { it.second == pkg }?.first
                    var showAppPicker by remember { mutableStateOf(false) }
                    val displayText = selectedApp ?: if (pkg.isNotEmpty() && pkg != CUSTOM_PACKAGE_KEY) "其他: $pkg" else "點擊選擇 App"

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable { showAppPicker = !showAppPicker }
                        ) {
                            OutlinedTextField(
                                value = displayText,
                                onValueChange = {},
                                readOnly = true,
                                enabled = false,
                                label = { Text("選擇 App") },
                                leadingIcon = if (pkg.isNotEmpty() && pkg != CUSTOM_PACKAGE_KEY) {
                                    { AppIcon(packageName = pkg, size = 36.dp) }
                                } else null,
                                trailingIcon = { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = anchorTextFieldColors()
                            )
                        }
                    }

                    if (showAppPicker) {
                        AppPickerSheet(
                            apps = installedApps,
                            selectedPackage = if (pkg != CUSTOM_PACKAGE_KEY) pkg else null,
                            onDismiss = { showAppPicker = false },
                            onSelectApp = {
                                params["package"] = it
                                onUpdate(step.copy(params = params))
                            },
                            onSelectCustom = {
                                params["package"] = CUSTOM_PACKAGE_KEY
                                onUpdate(step.copy(params = params))
                            }
                        )
                    }

                    if (selectedApp == null) {
                        val customPkg = if (pkg == CUSTOM_PACKAGE_KEY) "" else pkg
                        OutlinedTextField(
                            value = customPkg,
                            onValueChange = {
                                params["package"] = it
                                onUpdate(step.copy(params = params))
                            },
                            label = { Text("自訂 Package") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = params["uri"] ?: "",
                        onValueChange = { params["uri"] = it; onUpdate(step.copy(params = params)) },
                        label = { Text("URI (選填)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                "wait" -> {
                    OutlinedTextField(
                        value = params["ms"] ?: "1000",
                        onValueChange = { params["ms"] = it; onUpdate(step.copy(params = params)) },
                        label = { Text("毫秒") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                "find_and_click" -> {
                    OutlinedTextField(
                        value = params["text"] ?: "",
                        onValueChange = { params["text"] = it; onUpdate(step.copy(params = params)) },
                        label = { Text("要點擊的文字") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    var matchExpanded by remember { mutableStateOf(false) }
                    val matchValue = params["match"]?.ifBlank { "contains" } ?: "contains"
                    val matchLabel = FIND_MATCH_OPTIONS.find { it.first == matchValue }?.second ?: matchValue
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable { matchExpanded = !matchExpanded }
                        ) {
                            OutlinedTextField(
                                value = matchLabel,
                                onValueChange = {},
                                readOnly = true,
                                enabled = false,
                                label = { Text("比對方式") },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.KeyboardArrowRight,
                                        contentDescription = null,
                                        modifier = Modifier.graphicsLayer { rotationZ = if (matchExpanded) 90f else 0f }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = anchorTextFieldColors()
                            )
                        }
                        DropdownMenu(
                            expanded = matchExpanded,
                            onDismissRequest = { matchExpanded = false },
                            modifier = Modifier.requiredSizeIn(maxHeight = 180.dp),
                            properties = PopupProperties(focusable = false, dismissOnClickOutside = true)
                        ) {
                            FIND_MATCH_OPTIONS.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        params["match"] = value
                                        onUpdate(step.copy(params = params))
                                        matchExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                "swipe" -> {
                    var directionExpanded by remember { mutableStateOf(false) }
                    val directionValue = params["direction"]?.ifBlank { "down" } ?: "down"
                    val directionLabel = SWIPE_DIRECTION_OPTIONS.find { it.first == directionValue }?.second ?: directionValue
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable { directionExpanded = !directionExpanded }
                        ) {
                            OutlinedTextField(
                                value = directionLabel,
                                onValueChange = {},
                                readOnly = true,
                                enabled = false,
                                label = { Text("方向") },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.KeyboardArrowRight,
                                        contentDescription = null,
                                        modifier = Modifier.graphicsLayer { rotationZ = if (directionExpanded) 90f else 0f }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = anchorTextFieldColors()
                            )
                        }
                        DropdownMenu(
                            expanded = directionExpanded,
                            onDismissRequest = { directionExpanded = false },
                            modifier = Modifier.requiredSizeIn(maxHeight = 220.dp),
                            properties = PopupProperties(focusable = false, dismissOnClickOutside = true)
                        ) {
                            SWIPE_DIRECTION_OPTIONS.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        params["direction"] = value
                                        onUpdate(step.copy(params = params))
                                        directionExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    var distanceExpanded by remember { mutableStateOf(false) }
                    var distanceMode by remember(step) {
                        mutableStateOf(
                            if (SWIPE_DISTANCE_OPTIONS.any { it.first == (step.params["distance"] ?: "300") }) {
                                step.params["distance"] ?: "300"
                            } else {
                                CUSTOM_OPTION_KEY
                            }
                        )
                    }
                    val distanceValue = params["distance"]?.ifBlank { "300" } ?: "300"
                    val distanceLabel = SWIPE_DISTANCE_OPTIONS.find { it.first == distanceMode }?.second
                        ?: if (distanceMode == CUSTOM_OPTION_KEY) "自訂…" else distanceMode
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .clickable { distanceExpanded = !distanceExpanded }
                        ) {
                            OutlinedTextField(
                                value = if (distanceMode == CUSTOM_OPTION_KEY) "自訂：$distanceValue" else distanceLabel,
                                onValueChange = {},
                                readOnly = true,
                                enabled = false,
                                label = { Text("距離") },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.KeyboardArrowRight,
                                        contentDescription = null,
                                        modifier = Modifier.graphicsLayer { rotationZ = if (distanceExpanded) 90f else 0f }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = anchorTextFieldColors()
                            )
                        }
                        DropdownMenu(
                            expanded = distanceExpanded,
                            onDismissRequest = { distanceExpanded = false },
                            modifier = Modifier.requiredSizeIn(maxHeight = 240.dp),
                            properties = PopupProperties(focusable = false, dismissOnClickOutside = true)
                        ) {
                            SWIPE_DISTANCE_OPTIONS.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        distanceMode = value
                                        if (value != CUSTOM_OPTION_KEY) {
                                            params["distance"] = value
                                            onUpdate(step.copy(params = params))
                                        }
                                        distanceExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (distanceMode == CUSTOM_OPTION_KEY) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        OutlinedTextField(
                            value = distanceValue,
                            onValueChange = { params["distance"] = it; onUpdate(step.copy(params = params)) },
                            label = { Text("自訂距離") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                "click_node" -> {
                    OutlinedTextField(
                        value = params["nodeId"] ?: "",
                        onValueChange = { params["nodeId"] = it; onUpdate(step.copy(params = params)) },
                        label = { Text("nodeId") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                "long_press" -> {
                    OutlinedTextField(
                        value = params["text"] ?: "",
                        onValueChange = { params["text"] = it; onUpdate(step.copy(params = params)) },
                        label = { Text("文字 (選填)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = params["nodeId"] ?: "",
                        onValueChange = { params["nodeId"] = it; onUpdate(step.copy(params = params)) },
                        label = { Text("nodeId (選填)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                "extract_text" -> {
                    OutlinedTextField(
                        value = params["nodeId"] ?: "",
                        onValueChange = { params["nodeId"] = it; onUpdate(step.copy(params = params)) },
                        label = { Text("nodeId") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = params["variable"] ?: "",
                        onValueChange = { params["variable"] = it; onUpdate(step.copy(params = params)) },
                        label = { Text("變數名") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private val SCHEDULE_OPTIONS = listOf(
    "disabled" to "關閉",
    "daily" to "每日",
    "hourly" to "每小時",
    "interval" to "間隔"
)

private val SCHEDULE_MODE_OPTIONS = listOf(
    "battery" to "省電（可能延後）",
    "exact" to "精準（較耗電）",
    "always_on" to "常駐（最穩、常駐通知）"
)

@Composable
private fun ScheduleSection(
    schedule: Schedule,
    onScheduleChange: (Schedule) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val displayText = SCHEDULE_OPTIONS.find { it.first == schedule.type }?.second ?: "關閉"

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = displayText,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("排程") },
                trailingIcon = {
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 90f else 0f }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = anchorTextFieldColors()
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.requiredSizeIn(maxHeight = 160.dp),
            properties = PopupProperties(focusable = false, dismissOnClickOutside = true)
        ) {
            SCHEDULE_OPTIONS.forEach { (type, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onScheduleChange(
                            schedule.copy(
                                type = type,
                                enabled = type != "disabled",
                                mode = schedule.mode.ifBlank { "battery" }
                            )
                        )
                        expanded = false
                    }
                )
            }
        }
    }

    if (schedule.type != "disabled" && schedule.enabled) {
        Spacer(modifier = Modifier.height(Spacing.sm))
        var modeExpanded by remember { mutableStateOf(false) }
        val modeText = SCHEDULE_MODE_OPTIONS.find { it.first == schedule.mode }?.second
            ?: SCHEDULE_MODE_OPTIONS.first().second
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable { modeExpanded = !modeExpanded }
            ) {
                OutlinedTextField(
                    value = modeText,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("模式") },
                    trailingIcon = {
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.graphicsLayer { rotationZ = if (modeExpanded) 90f else 0f }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = anchorTextFieldColors()
                )
            }
            DropdownMenu(
                expanded = modeExpanded,
                onDismissRequest = { modeExpanded = false },
                modifier = Modifier.requiredSizeIn(maxHeight = 200.dp),
                properties = PopupProperties(focusable = false, dismissOnClickOutside = true)
            ) {
                SCHEDULE_MODE_OPTIONS.forEach { (mode, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onScheduleChange(schedule.copy(mode = mode))
                            modeExpanded = false
                        }
                    )
                }
            }
        }

        if (schedule.mode == "exact") {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                "提示：精準模式會更耗電，且在部分系統版本可能需要額外允許精準鬧鐘。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (schedule.mode == "always_on") {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                "提示：常駐模式會啟動前景服務並顯示常駐通知，以換取螢幕關閉時的穩定性。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (schedule.type == "daily") {
        Spacer(modifier = Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            OutlinedTextField(
                value = schedule.hour.toString(),
                onValueChange = { onScheduleChange(schedule.copy(hour = it.toIntOrNull() ?: 9)) },
                label = { Text("時") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = schedule.minute.toString(),
                onValueChange = { onScheduleChange(schedule.copy(minute = it.toIntOrNull() ?: 0)) },
                label = { Text("分") },
                modifier = Modifier.weight(1f)
            )
        }
    }
    if (schedule.type == "hourly") {
        Spacer(modifier = Modifier.height(Spacing.sm))
        OutlinedTextField(
            value = schedule.minute.toString(),
            onValueChange = { onScheduleChange(schedule.copy(minute = it.toIntOrNull() ?: 0)) },
            label = { Text("每小時的幾分") },
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (schedule.type == "interval") {
        Spacer(modifier = Modifier.height(Spacing.sm))
        OutlinedTextField(
            value = schedule.minutes.toString(),
            onValueChange = { onScheduleChange(schedule.copy(minutes = it.toIntOrNull() ?: 30)) },
            label = { Text("每 N 分鐘") },
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(modifier = Modifier.height(Spacing.md))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Text(
                "排程可能延遲的原因",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "省電模式、Doze 與各廠商後台限制，可能延後 WorkManager 觸發；與雲端「永遠在線」敘事不同。若需較準時，請選「精準」或「常駐」模式，並在系統設定中允許本 App 背景執行／忽略電池最佳化（選項因裝置而異）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            TextButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            ) {
                Text("開啟應用程式資訊（電池／背景）")
            }
        }
    }
}
