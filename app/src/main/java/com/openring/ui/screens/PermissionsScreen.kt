package com.openring.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.openring.R
import com.openring.core.MediaProjectionHostService
import com.openring.core.MediaProjectionSession
import com.openring.domain.Scheduler
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.SettingsAccessibility
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
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
import androidx.activity.ComponentActivity
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
    val activity = LocalActivity.current as? ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    var overlayGranted by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var accessibilityEnabled by remember {
        mutableStateOf(isOpenRingAccessibilityEnabled(context))
    }
    var notificationsGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var devicePlaybackActive by remember {
        mutableStateOf(MediaProjectionSession.isActive())
    }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val requestPostNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        if (granted) {
            Scheduler(context.applicationContext).refreshAlwaysOnServiceState()
        }
    }

    val requestRecordAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
    }

    fun refreshStatus() {
        overlayGranted = Settings.canDrawOverlays(context)
        accessibilityEnabled = isOpenRingAccessibilityEnabled(context)
        notificationsGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        micGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        devicePlaybackActive = MediaProjectionSession.isActive()
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val act = activity
            if (act != null) {
                MediaProjectionSession.attachFromActivityResult(act, result.resultCode, result.data!!)
            }
        } else {
            MediaProjectionHostService.requestStop(context.applicationContext)
        }
        refreshStatus()
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

    fun openAppNotificationSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w("OpenRing", "無法開啟通知設定", e)
        }
    }

    fun openAppDetailsSettings() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            Log.w("OpenRing", "無法開啟應用程式資訊", e)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.permission_settings_screen_title)) },
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
                title = stringResource(R.string.permission_notifications_title),
                summary = when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                        stringResource(R.string.permission_notifications_legacy_summary)
                    notificationsGranted ->
                        stringResource(R.string.permission_notifications_granted_summary)
                    else ->
                        stringResource(R.string.permission_notifications_denied_summary)
                },
                statusContentDescription = when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                        stringResource(R.string.permission_notifications_status_legacy)
                    notificationsGranted ->
                        stringResource(R.string.permission_notifications_status_on)
                    else ->
                        stringResource(R.string.permission_notifications_status_off)
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                actionLabel = when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                        stringResource(R.string.permission_notifications_action_settings)
                    notificationsGranted ->
                        stringResource(R.string.permission_notifications_action_settings)
                    else ->
                        stringResource(R.string.permission_notifications_action_allow)
                },
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !notificationsGranted &&
                        activity != null
                    ) {
                        requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openAppNotificationSettings()
                    }
                },
                actionContentDescription = stringResource(R.string.permission_notifications_title)
            )

            PermissionCard(
                title = "麥克風（聽覺／環境音）",
                summary = if (micGranted) {
                    "已允許：Agent 工具 describe_ambient_audio 可短錄音並由 Gemini 理解語音或提示音（例如聽音配對）。"
                } else {
                    "未允許：無法使用聽覺工具；若題目依賴聲音，請先允許麥克風。"
                },
                statusContentDescription = if (micGranted) {
                    "麥克風權限狀態：已允許"
                } else {
                    "麥克風權限狀態：未允許"
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                actionLabel = if (micGranted) "前往設定" else "允許麥克風",
                onAction = {
                    if (!micGranted && activity != null) {
                        requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        openAppDetailsSettings()
                    }
                },
                actionContentDescription = if (micGranted) "前往設定：應用程式權限" else "請求麥克風權限"
            )

            PermissionCard(
                title = "手機播放音訊（內部混音）",
                summary = when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ->
                        "需要 Android 10 以上才能擷取他 App 從裝置播出的聲音。"
                    devicePlaybackActive ->
                        "已啟用：describe_ambient_audio 會優先錄內部播放（與螢幕錄製相同授權）；可點「停止擷取」結束。"
                    !micGranted ->
                        "請先允許上一項「麥克風」，再回來按「授權擷取」完成系統對話框。"
                    else ->
                        "未啟用：按「授權擷取」後會出現系統提示與前台通知（Android 14+ 規定）。"
                },
                statusContentDescription = if (devicePlaybackActive) {
                    "裝置播放音訊擷取：已啟用"
                } else {
                    "裝置播放音訊擷取：未啟用"
                },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                actionLabel = when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> "了解"
                    devicePlaybackActive -> "停止擷取"
                    !micGranted -> "先允許麥克風"
                    else -> "授權擷取"
                },
                onAction = {
                    when {
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> openAppDetailsSettings()
                        devicePlaybackActive -> {
                            MediaProjectionSession.releaseAndStopService(context)
                            refreshStatus()
                        }
                        !micGranted -> {
                            if (activity != null) {
                                requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                        else -> {
                            val act = activity ?: return@PermissionCard
                            MediaProjectionHostService.requestPrepare(context.applicationContext)
                            mainHandler.postDelayed({
                                val mpm =
                                    act.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
                            }, 280L)
                        }
                    }
                },
                actionContentDescription = when {
                    devicePlaybackActive -> "停止裝置播放音訊擷取"
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> "開啟應用程式資訊"
                    else -> "授權擷取裝置播放音訊"
                }
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
