package com.openring.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.openring.R
import com.openring.ui.i18n.AppLanguageManager
import com.openring.ui.theme.Spacing
import androidx.activity.ComponentActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isEnglishSelected by remember { mutableStateOf(AppLanguageManager.isEnglishSelected(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.language_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = stringResource(R.string.language_settings_intro),
                style = MaterialTheme.typography.bodyLarge
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.language_settings_option_english)) },
                supportingContent = { Text(stringResource(R.string.language_settings_option_english_subtitle)) },
                leadingContent = {
                    RadioButton(
                        selected = isEnglishSelected,
                        onClick = {
                            if (!isEnglishSelected) {
                                AppLanguageManager.setSelectedLanguage(context, "en")
                                isEnglishSelected = true
                                (context as? ComponentActivity)?.recreate()
                            }
                        }
                    )
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.language_settings_option_zh_tw)) },
                supportingContent = { Text(stringResource(R.string.language_settings_option_zh_tw_subtitle)) },
                leadingContent = {
                    RadioButton(
                        selected = !isEnglishSelected,
                        onClick = {
                            if (isEnglishSelected) {
                                AppLanguageManager.setSelectedLanguage(context, "zh-TW")
                                isEnglishSelected = false
                                (context as? ComponentActivity)?.recreate()
                            }
                        }
                    )
                }
            )
            Button(
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_LOCALE_SETTINGS))
                    } catch (_: Exception) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.language_settings_open_system_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            ) {
                Text(stringResource(R.string.language_settings_open_system_button))
            }
        }
    }
}
