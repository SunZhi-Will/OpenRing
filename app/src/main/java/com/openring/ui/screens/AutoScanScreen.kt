package com.openring.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.openring.R
import com.openring.settings.AutoScanStore
import com.openring.ui.theme.Spacing
import com.openring.worker.ScanScheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoScanScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { AutoScanStore(context) }
    var enabled by remember(store.isAutoScanEnabled()) { mutableStateOf(store.isAutoScanEnabled()) }
    var interval by remember(store.getAutoScanIntervalMinutes()) { mutableStateOf(store.getAutoScanIntervalMinutes()) }

    fun apply() {
        ScanScheduler.apply(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.auto_scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.auto_scan_enable), style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        store.setAutoScanEnabled(it)
                        apply()
                    }
                )
            }
            Text(
                stringResource(R.string.auto_scan_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(stringResource(R.string.auto_scan_interval_minutes), style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                AutoScanStore.ALLOWED_INTERVALS.forEach { min ->
                    FilterChip(
                        selected = interval == min,
                        onClick = {
                            interval = min
                            store.setAutoScanIntervalMinutes(min)
                            apply()
                        },
                        label = { Text("$min") }
                    )
                }
            }
            Text(
                stringResource(R.string.auto_scan_min_limit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
