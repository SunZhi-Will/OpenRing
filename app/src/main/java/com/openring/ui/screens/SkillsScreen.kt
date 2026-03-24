package com.openring.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openring.skills.InstalledSkillStore
import com.openring.skills.SkillAllowedSourcesStore
import com.openring.skills.SkillEnabledStore
import com.openring.skills.SkillInstall
import com.openring.skills.SkillTemplateCatalog
import com.openring.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allowedStore = remember { SkillAllowedSourcesStore(context) }
    var allowedUrls by remember { mutableStateOf(allowedStore.getAllowedUrls()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newUrl by remember { mutableStateOf("") }

    val installedStore = remember { InstalledSkillStore(context) }
    val enabledStore = remember { SkillEnabledStore(context) }
    var installedIds by remember { mutableStateOf(installedStore.getInstalledIds()) }
    var enabledIds by remember { mutableStateOf(enabledStore.getEnabledIds().toSet()) }

    var statusLine by remember { mutableStateOf<String?>(null) }
    var showUrlInstallDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var urlToInstall by remember { mutableStateOf("") }
    val templateCatalog = remember { SkillTemplateCatalog.templates }

    fun refreshSkillLists() {
        installedIds = installedStore.getInstalledIds()
        enabledIds = enabledStore.getEnabledIds().toSet()
    }

    fun loadSkillInstructionSummary(skillId: String): String? {
        val file = File(context.filesDir, "skills/$skillId/SKILL.md")
        if (!file.isFile) return null
        val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        val normalized = raw.replace("\r\n", "\n")
        val body = if (normalized.startsWith("---\n")) {
            val end = normalized.indexOf("\n---\n", startIndex = 4)
            if (end >= 0) normalized.substring(end + 5) else normalized
        } else normalized
        val oneLine = body
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("-") }
            ?: body.trim().lines().firstOrNull { it.isNotBlank() }
        return oneLine?.let { if (it.length > 120) it.take(120) + "..." else it }
    }

    fun installHint(skillId: String): String {
        val hasSkillMd = File(context.filesDir, "skills/$skillId/SKILL.md").isFile
        return if (hasSkillMd) "已安裝：$skillId" else "已安裝：$skillId（建議補上 SKILL.md，讓模型更懂何時使用）"
    }

    fun uninstallSkill(skillId: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                enabledStore.setEnabled(skillId, false)
                installedStore.getSkillDir(context, skillId)?.deleteRecursively()
                installedStore.removeInstalled(skillId)
            }.onSuccess {
                withContext(Dispatchers.Main) {
                    statusLine = "已移除：$skillId"
                    refreshSkillLists()
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    statusLine = "移除失敗：${e.message ?: e.javaClass.simpleName}"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshSkillLists()
    }

    val pickZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val result = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    SkillInstall.installFromZipInputStream(context, stream)
                } ?: SkillInstall.Result.Err("READ_FAILED", "Cannot open file")
            } catch (e: Exception) {
                SkillInstall.Result.Err("READ_FAILED", e.message ?: e.javaClass.simpleName)
            }
            withContext(Dispatchers.Main) {
                when (result) {
                    is SkillInstall.Result.Ok -> {
                        statusLine = installHint(result.skillId)
                        refreshSkillLists()
                    }
                    is SkillInstall.Result.Err ->
                        statusLine = "錯誤 [${result.code}] ${result.message}"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skills") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = Spacing.xs) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        FilledTonalButton(
                            onClick = { pickZipLauncher.launch("application/zip") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("匯入 ZIP")
                        }
                        OutlinedButton(
                            onClick = { showUrlInstallDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("從 URL 安裝")
                        }
                    }
                    statusLine?.let { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Text(
                            "Skills 管理",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "管理安裝來源、官方範本與已安裝技能。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.92f)
                        )
                        Text(
                            "已安裝 ${installedIds.size} ・ 已啟用 ${enabledIds.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "來源白名單",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "僅允許白名單 URL 前綴進行安裝",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { showAddDialog = true }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.padding(end = Spacing.xs)
                            )
                            Text("新增")
                        }
                    }
                }
            }
            items(allowedUrls) { url ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(url, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        IconButton(
                            onClick = {
                                allowedStore.removeAllowedUrl(url)
                                allowedUrls = allowedStore.getAllowedUrls()
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "移除")
                        }
                    }
                }
            }
            item {
                if (allowedUrls.isEmpty()) {
                    Text(
                        "尚未加入任何白名單來源。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.md)
                    )
                }
            }
            item {
                Text(
                    "官方 Skill 範本",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "依需求下載，不會自動預載到本機。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(onClick = { showTemplateDialog = true }) {
                        Text("開啟範本列表")
                    }
                }
            }
            item {
                Text(
                    "已安裝技能（${installedIds.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md)
                )
                if (installedIds.isEmpty()) {
                    Text(
                        "目前沒有安裝任何 Skill，可使用上方的安裝方式加入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.md)
                    )
                }
            }

            if (installedIds.isNotEmpty()) {
                items(installedIds) { skillId ->
                    val displayName = when (skillId.lowercase()) {
                        "threads" -> "Threads"
                        else -> skillId
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = Spacing.sm)
                                ) {
                                    Text(displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        skillId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            val summary = loadSkillInstructionSummary(skillId)
                            Text(
                                if (summary != null) "說明：$summary" else "未提供 SKILL.md（建議補上）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val next = !enabledIds.contains(skillId)
                                        enabledStore.setEnabled(skillId, next)
                                        enabledIds = enabledStore.getEnabledIds().toSet()
                                    }
                                ) {
                                    Text(if (enabledIds.contains(skillId)) "停用" else "啟用")
                                }
                                TextButton(onClick = { uninstallSkill(skillId) }) {
                                    Text("移除")
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.xl)) }
        }
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false; newUrl = "" },
                title = { Text("新增允許安裝來源") },
                text = {
                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        label = { Text("URL (https://...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newUrl.isNotBlank()) {
                                allowedStore.addAllowedUrl(newUrl.trim())
                                allowedUrls = allowedStore.getAllowedUrls()
                                newUrl = ""
                                showAddDialog = false
                            }
                        }
                    ) { Text("新增") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false; newUrl = "" }) { Text("取消") }
                }
            )
        }
        if (showUrlInstallDialog) {
            AlertDialog(
                onDismissRequest = { showUrlInstallDialog = false; urlToInstall = "" },
                title = { Text("從 URL 安裝") },
                text = {
                    Column {
                        Text(
                            "貼上指向 .zip 的 https 連結（例如 GitHub Releases 的 asset 下載網址）。該網址必須以白名單中的其中一項為前綴。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        OutlinedTextField(
                            value = urlToInstall,
                            onValueChange = { urlToInstall = it },
                            label = { Text("ZIP URL") },
                            singleLine = false,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val u = urlToInstall.trim()
                            if (u.isNotBlank()) {
                                scope.launch(Dispatchers.IO) {
                                    val result = SkillInstall.installFromUrl(context, u, allowedStore)
                                    withContext(Dispatchers.Main) {
                                        showUrlInstallDialog = false
                                        urlToInstall = ""
                                        when (result) {
                                            is SkillInstall.Result.Ok -> {
                                                statusLine = installHint(result.skillId)
                                                refreshSkillLists()
                                            }
                                            is SkillInstall.Result.Err ->
                                                statusLine = "錯誤 [${result.code}] ${result.message}"
                                        }
                                    }
                                }
                            }
                        }
                    ) { Text("安裝") }
                },
                dismissButton = {
                    TextButton(onClick = { showUrlInstallDialog = false; urlToInstall = "" }) {
                        Text("取消")
                    }
                }
            )
        }
        if (showTemplateDialog) {
            AlertDialog(
                onDismissRequest = { showTemplateDialog = false },
                title = { Text("官方 Skill 範本") },
                text = {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        items(templateCatalog) { template ->
                            val installed = installedIds.contains(template.id)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = Spacing.sm)
                                    ) {
                                        Text(template.title, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            template.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            template.id,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                when (val result = SkillTemplateCatalog.installTemplate(context, template)) {
                                                    is SkillInstall.Result.Ok -> {
                                                        statusLine = installHint(result.skillId)
                                                        refreshSkillLists()
                                                    }
                                                    is SkillInstall.Result.Err -> {
                                                        statusLine = "錯誤 [${result.code}] ${result.message}"
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        Text(if (installed) "重新安裝" else "安裝")
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTemplateDialog = false }) { Text("關閉") }
                }
            )
        }
        }
    }
}
