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
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openring.R
import com.openring.settings.AgentGovernanceStore
import com.openring.settings.AiPromptStore
import com.openring.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    onEditSystemPrompt: () -> Unit,
    onEditMoralityPolicy: () -> Unit,
    onNavigateToSkills: () -> Unit,
    onNavigateToAiModelSettings: () -> Unit,
    onNavigateToAutoScan: () -> Unit = {},
    onNavigateToPromptNotes: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val promptStore = remember { AiPromptStore(context) }
    val governanceStore = remember { AgentGovernanceStore(context) }
    var maxRounds by remember { mutableStateOf(promptStore.getMaxRounds()) }
    var showMaxRoundsDialog by remember { mutableStateOf(false) }
    var automationMode by remember { mutableStateOf(governanceStore.getAutomationMode()) }
    var chatHistoryTurns by remember { mutableStateOf(governanceStore.getChatHistoryTurns()) }
    var showAutomationModeDialog by remember { mutableStateOf(false) }
    var showHistoryTurnsDialog by remember { mutableStateOf(false) }

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
                    title = stringResource(R.string.ai_settings_system_prompt_title),
                    subtitle = systemPromptPreview,
                    onClick = onEditSystemPrompt
                )
            }

            item {
                SettingsNavCard(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.ai_settings_morality_title),
                    subtitle = moralityPolicyPreview,
                    onClick = onEditMoralityPolicy
                )
            }

            item {
                SettingsNavCard(
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.ai_model_settings_title),
                    subtitle = stringResource(R.string.ai_model_settings_subtitle),
                    onClick = onNavigateToAiModelSettings
                )
            }

            item {
                SettingsNavCard(
                    icon = Icons.Default.Notes,
                    title = stringResource(R.string.prompt_notes_title),
                    subtitle = stringResource(R.string.ai_settings_prompt_notes_subtitle),
                    onClick = onNavigateToPromptNotes
                )
            }

            item {
                SettingsNavCard(
                    icon = Icons.Default.Radar,
                    title = stringResource(R.string.ai_settings_auto_scan_title),
                    subtitle = stringResource(R.string.ai_settings_auto_scan_subtitle),
                    onClick = onNavigateToAutoScan
                )
            }

            item {
                SettingsNavCard(
                    icon = Icons.Default.Extension,
                    title = "Skills",
                    subtitle = stringResource(R.string.ai_settings_skills_subtitle),
                    onClick = onNavigateToSkills
                )
            }

            item {
                SettingsNavCard(
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.ai_settings_max_rounds_title),
                    subtitle = stringResource(R.string.ai_settings_max_rounds_subtitle, maxRounds),
                    onClick = { showMaxRoundsDialog = true }
                )
            }

            item {
                val govSubtitle = if (automationMode == AgentGovernanceStore.MODE_CONFIRM) {
                    stringResource(R.string.agent_governance_automation_subtitle_confirm)
                } else {
                    stringResource(R.string.agent_governance_automation_subtitle_auto)
                }
                SettingsNavCard(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.agent_governance_automation_title),
                    subtitle = govSubtitle,
                    onClick = { showAutomationModeDialog = true }
                )
            }

            item {
                SettingsNavCard(
                    icon = Icons.Default.List,
                    title = stringResource(R.string.agent_governance_chat_history_title),
                    subtitle = stringResource(R.string.agent_governance_chat_history_subtitle, chatHistoryTurns),
                    onClick = { showHistoryTurnsDialog = true }
                )
            }

        }
    }

    if (showAutomationModeDialog) {
        AlertDialog(
            onDismissRequest = { showAutomationModeDialog = false },
            title = { Text(stringResource(R.string.agent_governance_pick_mode_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.agent_governance_pick_mode_body))
                    TextButton(
                        onClick = {
                            governanceStore.setAutomationMode(AgentGovernanceStore.MODE_AUTO)
                            automationMode = AgentGovernanceStore.MODE_AUTO
                            showAutomationModeDialog = false
                        }
                    ) { Text(stringResource(R.string.agent_governance_mode_auto)) }
                    TextButton(
                        onClick = {
                            governanceStore.setAutomationMode(AgentGovernanceStore.MODE_CONFIRM)
                            automationMode = AgentGovernanceStore.MODE_CONFIRM
                            showAutomationModeDialog = false
                        }
                    ) { Text(stringResource(R.string.agent_governance_mode_confirm)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutomationModeDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showHistoryTurnsDialog) {
        var draft by remember(showHistoryTurnsDialog) { mutableStateOf(chatHistoryTurns.toString()) }
        AlertDialog(
            onDismissRequest = { showHistoryTurnsDialog = false },
            title = { Text(stringResource(R.string.agent_governance_adjust_history_turns)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.agent_governance_history_turns_range))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { next ->
                            draft = next.filter { it.isDigit() }.take(2)
                        },
                        singleLine = true,
                        label = { Text(stringResource(R.string.agent_governance_chat_history_title)) }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = draft.toIntOrNull()
                        if (parsed != null) {
                            val normalized = parsed.coerceIn(4, 80)
                            governanceStore.setChatHistoryTurns(normalized)
                            chatHistoryTurns = normalized
                            showHistoryTurnsDialog = false
                        }
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showHistoryTurnsDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showMaxRoundsDialog) {
        var draft by remember(showMaxRoundsDialog) { mutableStateOf(maxRounds.toString()) }
        AlertDialog(
            onDismissRequest = { showMaxRoundsDialog = false },
            title = { Text(stringResource(R.string.ai_settings_adjust_max_rounds)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.ai_settings_max_rounds_range))
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { next ->
                            draft = next.filter { it.isDigit() }.take(3)
                        },
                        singleLine = true,
                        label = { Text(stringResource(R.string.ai_settings_max_rounds_title)) }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = draft.toIntOrNull()
                        if (parsed != null) {
                            val normalized = parsed.coerceIn(5, 300)
                            promptStore.setMaxRounds(normalized)
                            maxRounds = normalized
                            showMaxRoundsDialog = false
                        }
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showMaxRoundsDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
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

