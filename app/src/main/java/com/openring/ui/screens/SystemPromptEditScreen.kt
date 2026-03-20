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
        title = "System Prompt",
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
                        Text("允許 AI 修改 System Prompt", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.padding(top = Spacing.xs))
                        Text(
                            "開啟後，AI 可透過 set_system_prompt 工具更新系統指令",
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
                    "建議只在你信任的使用情境下開啟。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        onSave = { store.setSystemPrompt(it); onBack() },
        onBack = onBack
    )
}

