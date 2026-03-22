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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.openring.skills.InstalledSkillStore
import com.openring.skills.SkillAllowedSourcesStore
import com.openring.skills.SkillEnabledStore
import com.openring.skills.SkillInstall
import com.openring.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var urlToInstall by remember { mutableStateOf("") }

    fun refreshSkillLists() {
        installedIds = installedStore.getInstalledIds()
        enabledIds = enabledStore.getEnabledIds().toSet()
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
                        statusLine = "已安裝：${result.skillId}"
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
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(Spacing.sm)) {
                        Text(
                            "信任與邊界",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "與封閉市集／雲端代管不同：OpenRing 不代為審核第三方 Skill。白名單與本機 QuickJS 執行讓你自行承擔風險與控制權；僅啟用你信任的來源。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.92f)
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
            }
            item {
                Text(
                    "允許從 URL 安裝來源（白名單）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "AI 的 install_skill 與下方「從 URL 安裝」僅能使用已加入白名單的 URL 前綴。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.xs))
                OutlinedButton(
                    onClick = { showAddDialog = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
                    Text("新增允許來源")
                }
                Spacer(Modifier.height(Spacing.sm))
            }
            items(allowedUrls) { url ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                Spacer(Modifier.height(Spacing.md))
                Text(
                    "已安裝技能（可啟用/停用）",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.sm)
                )

                if (installedIds.isEmpty()) {
                    Text(
                        "目前沒有安裝任何 Skill。可使用「匯入 ZIP」、從白名單 URL 安裝（含 GitHub Releases 的 zip 直連），或由 AI 呼叫 install_skill。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = enabledIds.contains(skillId),
                                    onCheckedChange = { checked ->
                                        enabledStore.setEnabled(skillId, checked)
                                        enabledIds = enabledStore.getEnabledIds().toSet()
                                    }
                                )
                                Text(
                                    displayName,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Text(
                                skillId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(Spacing.md))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedButton(onClick = { pickZipLauncher.launch("application/zip") }) {
                        Text("匯入 ZIP")
                    }
                    OutlinedButton(onClick = { showUrlInstallDialog = true }) {
                        Text("從 URL 安裝（含 GitHub）")
                    }
                }
                statusLine?.let { line ->
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
                                                statusLine = "已安裝：${result.skillId}"
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
        }
    }
}
