package com.openring.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.openring.settings.AiPromptStore
import com.openring.settings.MoralityStore
import com.openring.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoralityEditScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val moralityStore = remember { MoralityStore(context) }
    val promptStore = remember { AiPromptStore(context) }

    var lockEnabled by remember { mutableStateOf(moralityStore.isMoralityLockEnabled()) }
    var promptText by remember(promptStore.getMoralityPolicy()) { mutableStateOf(promptStore.getMoralityPolicy()) }

    fun save() {
        moralityStore.setMoralityLockEnabled(lockEnabled)
        promptStore.setMoralityPolicy(promptText)
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.morality_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    Button(onClick = { save() }) { Text(stringResource(R.string.save)) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.morality_lock_title), style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = lockEnabled,
                    onCheckedChange = { lockEnabled = it }
                )
            }
            Text(
                if (lockEnabled) stringResource(R.string.morality_lock_enabled) else stringResource(R.string.morality_lock_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 12,
                label = { Text(stringResource(R.string.morality_prompt_label)) }
            )
        }
    }
}
