package com.openring.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SettingsAccessibility
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.openring.ui.permissions.isOpenRingAccessibilityEnabled
import com.openring.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var overlayGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var accessibilityEnabled by remember {
        mutableStateOf(isOpenRingAccessibilityEnabled(context))
    }

    fun refreshStatus() {
        overlayGranted = Settings.canDrawOverlays(context)
        accessibilityEnabled = isOpenRingAccessibilityEnabled(context)
    }

    DisposableEffect(lifecycleOwner) {
        refreshStatus()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openOverlaySettings() {
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

    fun openAccessibilitySettings() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            Log.w("OpenRing", "無法開啟無障礙設定頁", e)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("權限") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(horizontal = Spacing.md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "在此查看並開啟系統權限。從其他畫面返回時會自動更新狀態。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PermissionCard(
                title = "懸浮窗（顯示在其他應用程式上層）",
                summary = if (overlayGranted) {
                    "已開啟：AI 執行時可顯示懸浮中斷按鈕。"
                } else {
                    "未開啟：無法顯示執行中的懸浮中斷按鈕。"
                },
                statusContentDescription = if (overlayGranted) {
                    "懸浮窗權限狀態：已開啟"
                } else {
                    "懸浮窗權限狀態：未開啟"
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                actionLabel = "前往設定",
                onAction = { openOverlaySettings() },
                actionContentDescription = "前往設定：懸浮窗權限"
            )

            PermissionCard(
                title = "無障礙服務",
                summary = if (accessibilityEnabled) {
                    "已開啟：OpenRing 可使用無障礙服務執行畫面操作（依功能需求）。"
                } else {
                    "未開啟：請在無障礙設定中啟用 OpenRing。"
                },
                statusContentDescription = if (accessibilityEnabled) {
                    "無障礙服務狀態：已開啟"
                } else {
                    "無障礙服務狀態：未開啟"
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.SettingsAccessibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                actionLabel = "前往設定",
                onAction = { openAccessibilitySettings() },
                actionContentDescription = "前往設定：無障礙服務"
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    summary: String,
    statusContentDescription: String,
    leadingIcon: @Composable () -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
    actionContentDescription: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(Spacing.md)
                .semantics(mergeDescendants = true) {
                    contentDescription = "$title。$statusContentDescription。$summary"
                },
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                leadingIcon()
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.semantics {
                        contentDescription = actionContentDescription
                    }
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
