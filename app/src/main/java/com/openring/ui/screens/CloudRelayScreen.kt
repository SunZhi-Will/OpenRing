package com.openring.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openring.R
import com.openring.core.OpenRingCloudRelayBridge
import com.openring.core.OpenRingCloudRelayService
import com.openring.settings.OpenRingCloudRelayPrefs
import com.openring.settings.RelayQrPayload

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudRelayScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(OpenRingCloudRelayPrefs.getRelayUrl(context)) }
    var prefsEnabled by remember { mutableStateOf(OpenRingCloudRelayPrefs.isRelayEnabled(context)) }

    val phase by OpenRingCloudRelayBridge.phase.collectAsStateWithLifecycle()
    val lastError by OpenRingCloudRelayBridge.lastError.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        url = OpenRingCloudRelayPrefs.getRelayUrl(context)
    }

    LaunchedEffect(phase) {
        prefsEnabled = OpenRingCloudRelayPrefs.isRelayEnabled(context)
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result?.contents ?: return@rememberLauncherForActivityResult
        val parsed = RelayQrPayload.parse(contents)
        if (parsed == null) {
            Toast.makeText(context, context.getString(R.string.cloud_relay_invalid_qr), Toast.LENGTH_SHORT).show()
        } else {
            url = parsed
            OpenRingCloudRelayPrefs.setRelayUrl(context, parsed)
            Toast.makeText(context, context.getString(R.string.cloud_relay_qr_applied), Toast.LENGTH_SHORT).show()
        }
    }

    val requestCameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scanLauncher.launch(
                ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    setPrompt(context.getString(R.string.cloud_relay_scan_prompt))
                    setBeepEnabled(false)
                }
            )
        }
    }

    fun launchQrScanner() {
        when (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
            PackageManager.PERMISSION_GRANTED -> {
                scanLauncher.launch(
                    ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setPrompt(context.getString(R.string.cloud_relay_scan_prompt))
                        setBeepEnabled(false)
                    }
                )
            }
            else -> requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    val isConnected = phase == OpenRingCloudRelayBridge.Phase.Connected
    val isConnecting = phase == OpenRingCloudRelayBridge.Phase.Connecting

    val statusText = when {
        !prefsEnabled -> stringResource(R.string.cloud_relay_status_idle)
        phase == OpenRingCloudRelayBridge.Phase.Connecting ->
            stringResource(R.string.cloud_relay_status_connecting)
        phase == OpenRingCloudRelayBridge.Phase.Connected ->
            stringResource(R.string.cloud_relay_status_connected)
        phase == OpenRingCloudRelayBridge.Phase.Failed ->
            stringResource(R.string.cloud_relay_status_failed)
        phase == OpenRingCloudRelayBridge.Phase.Disconnected ->
            stringResource(R.string.cloud_relay_status_disconnected)
        else -> stringResource(R.string.cloud_relay_status_connecting)
    }

    val statusColor = when {
        !prefsEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
        phase == OpenRingCloudRelayBridge.Phase.Connected ->
            MaterialTheme.colorScheme.primary
        phase == OpenRingCloudRelayBridge.Phase.Failed ->
            MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val urlEditable = !isConnected && !isConnecting

    fun stopRelay() {
        OpenRingCloudRelayPrefs.setRelayEnabled(context, false)
        context.stopService(Intent(context, OpenRingCloudRelayService::class.java))
        OpenRingCloudRelayBridge.setIdle()
        prefsEnabled = false
    }

    fun startRelay() {
        OpenRingCloudRelayPrefs.setRelayUrl(context, url)
        OpenRingCloudRelayPrefs.setRelayEnabled(context, true)
        ContextCompat.startForegroundService(
            context,
            Intent(context, OpenRingCloudRelayService::class.java)
        )
        prefsEnabled = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cloud_relay_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.cloud_relay_status_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        color = statusColor
                    )
                    if (phase == OpenRingCloudRelayBridge.Phase.Failed && !lastError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = lastError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.cloud_relay_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.cloud_relay_url_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.cloud_relay_url_label)) },
                singleLine = true,
                enabled = urlEditable
            )

            OutlinedButton(
                onClick = { launchQrScanner() },
                enabled = urlEditable,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Filled.QrCode2,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.cloud_relay_scan_qr))
            }

            if (isConnected) {
                OutlinedButton(
                    onClick = { stopRelay() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.cloud_relay_button_disconnect))
                }
            } else {
                Button(
                    onClick = { startRelay() },
                    enabled = !isConnecting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            if (isConnecting) {
                                stringResource(R.string.cloud_relay_status_connecting)
                            } else {
                                stringResource(R.string.cloud_relay_button_connect)
                            }
                        )
                    }
                }
            }

            if (prefsEnabled && !isConnected) {
                TextButton(
                    onClick = { stopRelay() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.cloud_relay_button_stop_relaying))
                }
            }
        }
    }
}
