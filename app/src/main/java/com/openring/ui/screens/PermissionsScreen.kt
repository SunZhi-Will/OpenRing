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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
                stringResource(R.string.permissions_intro),
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
                title = stringResource(R.string.permission_microphone_title),
                summary = if (micGranted) {
                    stringResource(R.string.permission_microphone_summary_on)
                } else {
                    stringResource(R.string.permission_microphone_summary_off)
                },
                statusContentDescription = if (micGranted) {
                    stringResource(R.string.permission_microphone_status_on)
                } else {
                    stringResource(R.string.permission_microphone_status_off)
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                actionLabel = if (micGranted) stringResource(R.string.permission_go_to_settings) else stringResource(R.string.permission_microphone_allow),
                onAction = {
                    if (!micGranted && activity != null) {
                        requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        openAppDetailsSettings()
                    }
                },
                actionContentDescription = if (micGranted) stringResource(R.string.permission_action_open_app_permissions) else stringResource(R.string.permission_action_request_microphone)
            )

            PermissionCard(
                title = stringResource(R.string.permission_device_audio_title),
                summary = when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ->
                        stringResource(R.string.permission_device_audio_summary_unsupported)
                    devicePlaybackActive ->
                        stringResource(R.string.permission_device_audio_summary_active)
                    !micGranted ->
                        stringResource(R.string.permission_device_audio_summary_need_mic)
                    else ->
                        stringResource(R.string.permission_device_audio_summary_inactive)
                },
                statusContentDescription = if (devicePlaybackActive) {
                    stringResource(R.string.permission_device_audio_status_on)
                } else {
                    stringResource(R.string.permission_device_audio_status_off)
                },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                actionLabel = when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> stringResource(R.string.permission_device_audio_action_learn)
                    devicePlaybackActive -> stringResource(R.string.permission_device_audio_action_stop)
                    !micGranted -> stringResource(R.string.permission_device_audio_action_allow_first_mic)
                    else -> stringResource(R.string.permission_device_audio_action_authorize)
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
                    devicePlaybackActive -> stringResource(R.string.permission_device_audio_action_stop_desc)
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> stringResource(R.string.permission_device_audio_action_open_app_info)
                    else -> stringResource(R.string.permission_device_audio_action_authorize_desc)
                }
            )

            PermissionCard(
                title = stringResource(R.string.permission_overlay_title),
                summary = if (overlayGranted) {
                    stringResource(R.string.permission_overlay_summary_on)
                } else {
                    stringResource(R.string.permission_overlay_summary_off)
                },
                statusContentDescription = if (overlayGranted) {
                    stringResource(R.string.permission_overlay_status_on)
                } else {
                    stringResource(R.string.permission_overlay_status_off)
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                actionLabel = stringResource(R.string.permission_go_to_settings),
                onAction = { openOverlaySettings() },
                actionContentDescription = stringResource(R.string.permission_overlay_action_desc)
            )

            PermissionCard(
                title = stringResource(R.string.permission_accessibility_title),
                summary = if (accessibilityEnabled) {
                    stringResource(R.string.permission_accessibility_summary_on)
                } else {
                    stringResource(R.string.permission_accessibility_summary_off)
                },
                statusContentDescription = if (accessibilityEnabled) {
                    stringResource(R.string.permission_accessibility_status_on)
                } else {
                    stringResource(R.string.permission_accessibility_status_off)
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.SettingsAccessibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                actionLabel = stringResource(R.string.permission_go_to_settings),
                onAction = { openAccessibilitySettings() },
                actionContentDescription = stringResource(R.string.permission_accessibility_action_desc)
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
