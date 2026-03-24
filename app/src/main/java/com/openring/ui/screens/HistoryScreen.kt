package com.openring.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.openring.R
import com.openring.data.db.OpenRingDatabase
import java.util.concurrent.TimeUnit
import com.openring.ui.theme.Spacing
import com.openring.data.model.ExecutionRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = OpenRingDatabase.getDatabase(context)
    val history by db.executionHistoryDao().getRecentHistory().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.xl),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.padding(Spacing.lg),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
                Text(
                    stringResource(R.string.history_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    stringResource(R.string.history_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(history, key = { it.id }) { record ->
                    HistoryItem(record = record)
                }
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long, context: Context): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> context.getString(R.string.history_just_now)
        diff < TimeUnit.HOURS.toMillis(1) -> context.getString(R.string.history_minutes_ago, TimeUnit.MILLISECONDS.toMinutes(diff))
        diff < TimeUnit.DAYS.toMillis(1) -> context.getString(R.string.history_hours_ago, TimeUnit.MILLISECONDS.toHours(diff))
        diff < TimeUnit.DAYS.toMillis(7) -> context.getString(R.string.history_days_ago, TimeUnit.MILLISECONDS.toDays(diff))
        else -> SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
private fun HistoryItem(record: ExecutionRecord) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(record.scriptName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(formatRelativeTime(record.timestamp, context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                record.errorMessage?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Icon(
                imageVector = if (record.success) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (record.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}
