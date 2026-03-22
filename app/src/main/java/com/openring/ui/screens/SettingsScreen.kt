package com.openring.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.openring.ui.theme.Spacing
import com.openring.R
import com.openring.security.ApiKeyStore
import com.openring.localmodel.LocalModelCatalog
import com.openring.localmodel.LocalModelDownloader
import com.openring.settings.ModelOption
import com.openring.settings.ModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val keyStore = remember { ApiKeyStore(context) }
    val modelStore = remember { ModelStore(context) }

    val models = remember { mutableStateListOf<ModelOption>() }
    val scope = rememberCoroutineScope()
    var downloadingOptionId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(Unit) {
        models.clear()
        models.addAll(modelStore.getModels())
    }

    fun startLocalDownload(item: ModelOption) {
        val entry = LocalModelCatalog.byId(item.model) ?: return
        if (downloadingOptionId != null) return
        scope.launch {
            downloadingOptionId = item.id
            downloadProgress = 0f
            val result = withContext(Dispatchers.IO) {
                LocalModelDownloader.download(context, entry) { p ->
                    scope.launch(Dispatchers.Main) { downloadProgress = p }
                }
            }
            downloadingOptionId = null
            downloadProgress = null
            result.onSuccess {
                Toast.makeText(context, "「${item.label}」下載完成", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(context, "下載失敗：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Dialog states
    var addDialogOpen by remember { mutableStateOf(false) }
    var editDialogModelId by remember { mutableStateOf<String?>(null) }
    var deleteDialogModelId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { addDialogOpen = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新增模型")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            val overlayGranted = Settings.canDrawOverlays(context)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("懸浮窗權限", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (overlayGranted) "已開啟：AI 執行時可顯示懸浮中斷按鈕"
                        else "未開啟：將無法顯示懸浮中斷按鈕",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(
                        onClick = {
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
                            }
                        }
                    ) {
                        Text(if (overlayGranted) "前往權限頁（重新確認）" else "前往開啟權限")
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("模型", style = MaterialTheme.typography.titleMedium)
            Text(
                "長按任一模型卡可拖拉上下排序（除錯日誌 tag: OpenRingDrag）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            ModelReorderList(
                models = models,
                isModelReady = { item ->
                    when (item.provider.lowercase()) {
                        "local" -> LocalModelCatalog.isDownloaded(context, item.model)
                        else -> keyStore.getGeminiApiKeyForModel(item.id).isNullOrBlank().not()
                    }
                },
                downloadingOptionId = downloadingOptionId,
                downloadProgress = downloadProgress,
                onRequestDownload = { id ->
                    models.firstOrNull { it.id == id }?.let { startLocalDownload(it) }
                },
                onReorderCommitted = { modelStore.saveModels(models.toList()) },
                onRequestEdit = { id -> editDialogModelId = id },
                onRequestDelete = { id -> deleteDialogModelId = id }
            )
        }
    }

    if (addDialogOpen) {
        ModelUpsertDialog(
            title = "新增模型",
            initial = null,
            onDismiss = { addDialogOpen = false },
            onConfirm = { chosen, key ->
                if (chosen.provider == ModelProvider.LOCAL) {
                    if (models.any { it.provider == "local" && it.model == chosen.model }) {
                        Toast.makeText(context, "清單中已有相同地端模型", Toast.LENGTH_SHORT).show()
                        return@ModelUpsertDialog
                    }
                }
                val option = ModelOption(
                    id = UUID.randomUUID().toString(),
                    provider = chosen.provider.name.lowercase(),
                    label = chosen.label,
                    model = chosen.model
                )
                models.add(option)
                modelStore.saveModels(models.toList())
                if (chosen.provider != ModelProvider.LOCAL && key.isNotBlank()) {
                    keyStore.setGeminiApiKeyForModel(option.id, key)
                }
                addDialogOpen = false
            }
        )
    }

    val editTarget = editDialogModelId?.let { id -> models.firstOrNull { it.id == id } }
    if (editTarget != null) {
        val currentKey = keyStore.getGeminiApiKeyForModel(editTarget.id).orEmpty()
        val editInitial =
            if (editTarget.provider == "local") {
                KNOWN_LOCAL_MODELS.firstOrNull { editTarget.model == it.model }
                    ?: KNOWN_LOCAL_MODELS.first()
            } else {
                KNOWN_MODELS.firstOrNull { editTarget.model == it.model } ?: KNOWN_MODELS.first()
            }
        ModelUpsertDialog(
            title = "編輯模型",
            initial = editInitial,
            initialKey = currentKey,
            onDismiss = { editDialogModelId = null },
            onConfirm = { chosen, key ->
                if (chosen.provider == ModelProvider.LOCAL) {
                    val dup = models.any {
                        it.id != editTarget.id && it.provider == "local" && it.model == chosen.model
                    }
                    if (dup) {
                        Toast.makeText(context, "清單中已有相同地端模型", Toast.LENGTH_SHORT).show()
                        return@ModelUpsertDialog
                    }
                }
                val idx = models.indexOfFirst { it.id == editTarget.id }
                if (idx >= 0) {
                    models[idx] = models[idx].copy(
                        provider = chosen.provider.name.lowercase(),
                        label = chosen.label,
                        model = chosen.model
                    )
                    modelStore.saveModels(models.toList())
                }
                if (chosen.provider == ModelProvider.LOCAL) {
                    keyStore.clearGeminiApiKeyForModel(editTarget.id)
                } else {
                    if (key.isBlank()) keyStore.clearGeminiApiKeyForModel(editTarget.id)
                    else keyStore.setGeminiApiKeyForModel(editTarget.id, key)
                }
                editDialogModelId = null
            }
        )
    }

    val deleteTarget = deleteDialogModelId?.let { id -> models.firstOrNull { it.id == id } }
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteDialogModelId = null },
            title = { Text("刪除模型") },
            text = {
                val extra =
                    if (deleteTarget.provider == "local") "已下載的地端檔案也會從本機刪除。"
                    else "此模型的 API Key 也會一併移除。"
                Text("確定要刪除「${deleteTarget.label}」嗎？$extra")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idx = models.indexOfFirst { it.id == deleteTarget.id }
                        if (idx >= 0) {
                            if (deleteTarget.provider == "local") {
                                LocalModelCatalog.deleteDownloaded(context, deleteTarget.model)
                            }
                            models.removeAt(idx)
                            modelStore.saveModels(models.toList())
                        }
                        keyStore.clearGeminiApiKeyForModel(deleteTarget.id)
                        deleteDialogModelId = null
                    }
                ) { Text("刪除") }
            },
            dismissButton = { TextButton(onClick = { deleteDialogModelId = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun ModelReorderList(
    models: androidx.compose.runtime.snapshots.SnapshotStateList<ModelOption>,
    isModelReady: (ModelOption) -> Boolean,
    downloadingOptionId: String?,
    downloadProgress: Float?,
    onRequestDownload: (String) -> Unit,
    onReorderCommitted: () -> Unit,
    onRequestEdit: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    var draggingItemId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var lastMenuFor by remember { mutableStateOf<String?>(null) }
    var boxTopY by remember { mutableStateOf(0f) }
    var draggedItemY by remember { mutableStateOf(0f) }
    val cardShape = MaterialTheme.shapes.medium
    val shadowSpotColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val draggingIndex = draggingItemId?.let { id -> models.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { boxTopY = it.boundsInRoot().top }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                userScrollEnabled = draggingIndex == null,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(models, key = { _, it -> it.id }) { index, item ->
                val isDragging = draggingIndex == index
                val ready = isModelReady(item)
                val menuExpanded = lastMenuFor == item.id

                fun reorderToTarget(current: Int, targetIndex: Int) {
                    if (targetIndex == current || targetIndex !in models.indices) return
                    val moved = models.removeAt(current)
                    val insertAt = if (current < targetIndex) targetIndex - 1 else targetIndex
                    models.add(insertAt.coerceIn(0, models.size), moved)
                    onReorderCommitted()
                }
                fun computeTargetIndex(current: Int): Int {
                    val visible = listState.layoutInfo.visibleItemsInfo
                    val itemSize = visible.firstOrNull { it.index == current }?.size?.toFloat() ?: 80f
                    val draggedCenterY = draggedItemY - boxTopY + dragOffsetY + itemSize / 2f
                    val targetInfo = visible.minByOrNull { info ->
                        val mid = info.offset + info.size / 2f
                        kotlin.math.abs(mid - draggedCenterY)
                    }
                    return targetInfo?.index?.coerceIn(0, models.lastIndex) ?: current
                }
                val dragHandleModifier = Modifier.pointerInput(item.id) {
                    detectDragGestures(
                        onDragStart = {
                            Log.d("OpenRingDrag", "onDragStart id=${item.id} label=${item.label}")
                            draggingItemId = item.id
                            dragOffsetY = 0f
                        },
                        onDragEnd = {
                            val current = models.indexOfFirst { it.id == draggingItemId }.takeIf { it >= 0 } ?: run {
                                draggingItemId = null
                                dragOffsetY = 0f
                                return@detectDragGestures
                            }
                            val targetIndex = computeTargetIndex(current)
                            if (targetIndex != current) {
                                Log.d("OpenRingDrag", "drop: move from $current to $targetIndex")
                                reorderToTarget(current, targetIndex)
                            }
                            draggingItemId = null
                            dragOffsetY = 0f
                        },
                        onDragCancel = {
                            draggingItemId = null
                            dragOffsetY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetY += dragAmount.y
                            val current = models.indexOfFirst { it.id == draggingItemId }.takeIf { it >= 0 } ?: return@detectDragGestures
                            val targetIndex = computeTargetIndex(current)
                            if (targetIndex != current) reorderToTarget(current, targetIndex)
                        }
                    )
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { if (isDragging) draggedItemY = it.boundsInRoot().top }
                        .graphicsLayer { alpha = if (isDragging) 0f else 1f },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    ModelListCardContent(
                        item = item,
                        readyForUse = ready,
                        downloading = downloadingOptionId == item.id,
                        downloadProgress = if (downloadingOptionId == item.id) downloadProgress else null,
                        onRequestDownload = { onRequestDownload(item.id) },
                        menuExpanded = menuExpanded,
                        onMenuClick = { lastMenuFor = if (menuExpanded) null else item.id },
                        onRequestEdit = { onRequestEdit(item.id) },
                        onRequestDelete = { onRequestDelete(item.id) },
                        onDismissMenu = { lastMenuFor = null },
                        dragHandleModifier = dragHandleModifier
                    )
                }
        }
        }

        DraggingOverlayIfNeeded(
            draggingItemId = draggingItemId,
            models = models,
            isModelReady = isModelReady,
            downloadingOptionId = downloadingOptionId,
            downloadProgress = downloadProgress,
            onRequestDownload = onRequestDownload,
            draggedItemY = draggedItemY,
            boxTopY = boxTopY,
            dragOffsetY = dragOffsetY,
            cardShape = cardShape,
            shadowSpotColor = shadowSpotColor
        )
    }
}
}

@Composable
private fun DraggingOverlayIfNeeded(
    draggingItemId: String?,
    models: List<ModelOption>,
    isModelReady: (ModelOption) -> Boolean,
    downloadingOptionId: String?,
    downloadProgress: Float?,
    onRequestDownload: (String) -> Unit,
    draggedItemY: Float,
    boxTopY: Float,
    dragOffsetY: Float,
    cardShape: androidx.compose.ui.graphics.Shape,
    shadowSpotColor: androidx.compose.ui.graphics.Color
) {
    val item = draggingItemId?.let { id -> models.find { it.id == id } }
    if (item != null) {
        val ready = isModelReady(item)
        DraggingOverlay(
            item = item,
            readyForUse = ready,
            downloading = downloadingOptionId == item.id,
            downloadProgress = if (downloadingOptionId == item.id) downloadProgress else null,
            onRequestDownload = { onRequestDownload(item.id) },
            draggedItemY = draggedItemY,
            boxTopY = boxTopY,
            dragOffsetY = dragOffsetY,
            cardShape = cardShape,
            shadowSpotColor = shadowSpotColor
        )
    }
}

@Composable
private fun DraggingOverlay(
    item: ModelOption,
    readyForUse: Boolean,
    downloading: Boolean,
    downloadProgress: Float?,
    onRequestDownload: () -> Unit,
    draggedItemY: Float,
    boxTopY: Float,
    dragOffsetY: Float,
    cardShape: androidx.compose.ui.graphics.Shape,
    shadowSpotColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .pointerInput(Unit) { },
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, (draggedItemY - boxTopY + dragOffsetY).roundToInt()) }
                .shadow(12.dp, cardShape, spotColor = shadowSpotColor)
                .graphicsLayer {
                    scaleX = 1.02f
                    scaleY = 1.02f
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            ModelListCardContent(
                item = item,
                readyForUse = readyForUse,
                downloading = downloading,
                downloadProgress = downloadProgress,
                onRequestDownload = onRequestDownload,
                menuExpanded = false,
                onMenuClick = { },
                onRequestEdit = { },
                onRequestDelete = { },
                onDismissMenu = { },
                showMenu = false
            )
        }
    }
}

@Composable
private fun ModelListCardContent(
    item: ModelOption,
    readyForUse: Boolean,
    downloading: Boolean,
    downloadProgress: Float?,
    onRequestDownload: () -> Unit,
    menuExpanded: Boolean,
    onMenuClick: () -> Unit,
    onRequestEdit: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismissMenu: () -> Unit,
    showMenu: Boolean = true,
    dragHandleModifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = dragHandleModifier
                .minimumInteractiveComponentSize()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "拖拉排序",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            painter = painterResource(drawableResForProvider(providerFromString(item.provider))),
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .padding(4.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text("${item.provider.uppercase()} · ${item.label}")
            Text(
                item.model,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.provider.equals("local", ignoreCase = true)) {
                val entry = LocalModelCatalog.byId(item.model)
                val sizeMb = entry?.let { (it.sizeBytesApprox / (1024 * 1024)).toString() } ?: "—"
                Text(
                    "約 ${sizeMb} MB · 地端檔案",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    downloading -> {
                        Text("下載中…", style = MaterialTheme.typography.bodySmall)
                        if (downloadProgress != null) {
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            )
                        }
                    }
                    readyForUse ->
                        Text(
                            "地端檔案：已下載",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    else ->
                        Text(
                            "地端檔案：未下載",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                }
            } else {
                Text(
                    if (readyForUse) "API Key：已設定" else "API Key：未設定",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (readyForUse) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )
            }
        }

        if (item.provider.equals("local", ignoreCase = true) && !downloading && !readyForUse && showMenu) {
            IconButton(
                onClick = onRequestDownload,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = "下載地端模型",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (showMenu) {
            Box {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = onDismissMenu
                ) {
                    DropdownMenuItem(
                        text = { Text("編輯") },
                        onClick = { onDismissMenu(); onRequestEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("刪除") },
                        onClick = { onDismissMenu(); onRequestDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                    )
                }
            }
        }
    }
}

/** 全螢幕模型選擇器（參考 App 選擇器：搜尋 + 網格圖示與名稱） */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ModelPickerSheet(
    models: List<KnownModel>,
    selected: KnownModel?,
    onDismiss: () -> Unit,
    onSelect: (KnownModel) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(models, searchQuery) {
        if (searchQuery.isBlank()) models
        else models.filter { opt ->
            opt.provider.displayName.contains(searchQuery, ignoreCase = true) ||
                opt.label.contains(searchQuery, ignoreCase = true) ||
                opt.model.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("選擇模型") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        placeholder = { Text("搜尋模型...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        singleLine = true
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 96.dp),
                        contentPadding = PaddingValues(Spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered, key = { "${it.provider}-${it.model}" }) { opt ->
                            val isSelected = selected?.model == opt.model && selected?.provider == opt.provider
                            Column(
                                modifier = Modifier
                                    .clickable {
                                        onSelect(opt)
                                        onDismiss()
                                    }
                                    .padding(Spacing.sm),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(drawableResForProvider(opt.provider)),
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${opt.provider.displayName} · ${opt.label}",
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class ModelSourceMode { CLOUD, LOCAL }

@Composable
private fun ModelUpsertDialog(
    title: String,
    initial: KnownModel?,
    initialKey: String = "",
    onDismiss: () -> Unit,
    onConfirm: (KnownModel, String) -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var showModelPicker by remember { mutableStateOf(false) }
    var sourceMode by remember {
        mutableStateOf(
            if (initial?.provider == ModelProvider.LOCAL) ModelSourceMode.LOCAL else ModelSourceMode.CLOUD
        )
    }
    var selected by remember {
        mutableStateOf(
            initial ?: KNOWN_MODELS.first()
        )
    }
    var apiKey by remember { mutableStateOf(initialKey) }
    var reveal by remember { mutableStateOf(false) }

    LaunchedEffect(initial?.model, initial?.provider) {
        if (initial != null) {
            sourceMode = if (initial.provider == ModelProvider.LOCAL) ModelSourceMode.LOCAL else ModelSourceMode.CLOUD
            selected = initial
        }
    }

    val pickerModels = if (sourceMode == ModelSourceMode.LOCAL) KNOWN_LOCAL_MODELS else KNOWN_MODELS

    if (showModelPicker) {
        ModelPickerSheet(
            models = pickerModels,
            selected = selected,
            onDismiss = { showModelPicker = false },
            onSelect = { selected = it; showModelPicker = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = sourceMode == ModelSourceMode.CLOUD,
                        onClick = {
                            sourceMode = ModelSourceMode.CLOUD
                            selected = KNOWN_MODELS.first()
                        }
                    )
                    Text("雲端 API", style = MaterialTheme.typography.bodyMedium)
                    RadioButton(
                        selected = sourceMode == ModelSourceMode.LOCAL,
                        onClick = {
                            sourceMode = ModelSourceMode.LOCAL
                            selected = KNOWN_LOCAL_MODELS.first()
                        }
                    )
                    Text("地端模型", style = MaterialTheme.typography.bodyMedium)
                }

                OutlinedTextField(
                    value = "${selected.provider.displayName} · ${selected.label}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("模型") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showModelPicker = true },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(drawableResForProvider(selected.provider)),
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { showModelPicker = true }) {
                            Icon(Icons.Default.Search, contentDescription = "選擇模型")
                        }
                    }
                )

                if (sourceMode == ModelSourceMode.CLOUD) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { reveal = !reveal }) {
                                Icon(
                                    imageVector = if (reveal) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (reveal) "隱藏" else "顯示"
                                )
                            }
                        }
                    )

                    TextButton(
                        onClick = {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(selected.provider.apiKeyUrl)))
                        }
                    ) { Text("取得 API Key") }
                } else {
                    Text(
                        "地端模型需下載 GGUF 後才能聊天；清單由上而下依序嘗試，可拖曳調整優先權。本機路徑僅純文字對話（無 ReAct／工具／雲端視覺），Gemini 仍可用於自動化。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val keyOut = if (sourceMode == ModelSourceMode.LOCAL) "" else apiKey
                    onConfirm(selected, keyOut)
                }
            ) { Text("確認") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

