package com.openring.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openring.R
import com.openring.settings.AiPromptStore
import com.openring.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    onEditSystemPrompt: () -> Unit,
    onEditMoralityPolicy: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToAutoScan: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val promptStore = remember { AiPromptStore(context) }

    val systemPromptPreview = remember { promptStore.getSystemPrompt().trim() }
        .lineSequence()
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString(" · ")
        .ifBlank { "—" }

    val moralityPolicyPreview = remember { promptStore.getMoralityPolicy().trim() }
        .lineSequence()
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString(" · ")
        .ifBlank { "—" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            item {
                SettingsNavCard(
                    icon = Icons.Default.Tune,
                    title = "System Prompt",
                    subtitle = systemPromptPreview,
                    onClick = onEditSystemPrompt
                )
            }

            item {
                SettingsNavCard(
                    icon = Icons.Default.Lock,
                    title = "道德",
                    subtitle = moralityPolicyPreview,
                    onClick = onEditMoralityPolicy
                )
            }

            item {
                SettingsNavCard(
                    icon = Icons.Default.Radar,
                    title = "自動掃描",
                    subtitle = "背景快取畫面供 get_cached_scan 使用",
                    onClick = onNavigateToAutoScan
                )
            }

            item {
                SettingsNavCard(
                    icon = Icons.Default.Extension,
                    title = "Skills",
                    subtitle = "安裝、啟用、管理外掛技能",
                    onClick = onNavigateToSkills
                )
            }

        }
    }
}

@Composable
private fun SettingsNavCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Icon(icon, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.padding(top = Spacing.xs))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

