package com.openring.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.openring.ui.theme.Spacing

@Composable
fun SystemPromptEditScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { AiPromptStore(context) }

    var allowAiSetPrompt by remember(store.getAllowAiToSetSystemPrompt()) {
        mutableStateOf(store.getAllowAiToSetSystemPrompt())
    }

    TextSettingEditorScreen(
        title = stringResource(R.string.ai_settings_system_prompt_title),
        initialValue = store.getSystemPrompt(),
        headerContent = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.system_prompt_allow_ai_modify_title), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.padding(top = Spacing.xs))
                        Text(
                            stringResource(R.string.system_prompt_allow_ai_modify_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = allowAiSetPrompt,
                        onCheckedChange = {
                            allowAiSetPrompt = it
                            store.setAllowAiToSetSystemPrompt(it)
                        }
                    )
                }
                Text(
                    stringResource(R.string.system_prompt_allow_ai_modify_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        onSave = { store.setSystemPrompt(it); onBack() },
        onBack = onBack
    )
}

