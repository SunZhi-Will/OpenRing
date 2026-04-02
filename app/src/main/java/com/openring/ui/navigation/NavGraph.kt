package com.openring.ui.navigation

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.openring.core.CloudRelayTaskBus
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.openring.ui.screens.ChatScreen
import com.openring.ui.screens.ExecutionLogScreen
import com.openring.ui.screens.HistoryScreen
import com.openring.ui.screens.ScriptEditorScreen
import com.openring.ui.screens.ScriptListScreen
import com.openring.ui.screens.SettingsScreen
import com.openring.ui.screens.SettingsScreenMode
import com.openring.ui.screens.AiSettingsScreen
import com.openring.ui.screens.AutoScanScreen
import com.openring.ui.screens.MoralityEditScreen
import com.openring.ui.screens.SkillsScreen
import com.openring.ui.screens.SystemPromptEditScreen
import com.openring.ui.screens.TextSettingEditorScreen
import com.openring.ui.screens.PermissionsScreen
import com.openring.ui.screens.LanguageSettingsScreen
import com.openring.ui.screens.CloudRelayScreen
import com.openring.ui.MainActivity
import com.openring.settings.OpenRingCloudRelayPrefs

sealed class Screen(val route: String) {
    data object Chat : Screen("chat")
    data object ExecutionLog : Screen("execution_log")
    data object AiSettings : Screen("ai_settings")
    data object Skills : Screen("skills")
    data object Settings : Screen("settings")
    data object Permissions : Screen("permissions")
    data object List : Screen("script_list")
    data object Editor : Screen("script_editor/{scriptId}") {
        fun createRoute(scriptId: String?) = "script_editor/${scriptId ?: "new"}"
    }
    data object History : Screen("history")
    data object EditSystemPrompt : Screen("ai/edit_system_prompt")
    data object EditMoralityPolicy : Screen("ai/edit_morality_policy")
    data object AutoScan : Screen("ai/auto_scan")
    data object AiModelSettings : Screen("ai/model_settings")
    data object LanguageSettings : Screen("app/language_settings")
    data object CloudRelay : Screen("app/cloud_relay")
}

@Composable
fun OpenRingNavHost(
    navController: NavHostController = rememberNavController(),
    relayIntentNonce: Int = 0,
) {
    val context = LocalContext.current
    LaunchedEffect(relayIntentNonce) {
        val act = context as? ComponentActivity ?: return@LaunchedEffect
        val intent = act.intent
        val relayText = intent.getStringExtra(MainActivity.EXTRA_RELAY_TASK_TEXT)?.trim()
        if (!relayText.isNullOrEmpty()) {
            val ok = CloudRelayTaskBus.tryEnqueueFromRelay(relayText)
            Log.d("OpenRingNav", "relay task from intent len=${relayText.length} tryEmit=$ok")
            intent.removeExtra(MainActivity.EXTRA_RELAY_TASK_TEXT)
        }
        if (intent.getBooleanExtra(MainActivity.EXTRA_OPEN_CHAT_FROM_RELAY, false)) {
            navController.navigate(Screen.Chat.route) {
                launchSingleTop = true
            }
            intent.removeExtra(MainActivity.EXTRA_OPEN_CHAT_FROM_RELAY)
        }
        val deepUri = intent.data
        if (deepUri != null && deepUri.scheme == "openring" && deepUri.host == "relay") {
            val url = deepUri.getQueryParameter("url")?.trim()
            if (!url.isNullOrEmpty() &&
                (url.startsWith("ws://", ignoreCase = true) || url.startsWith("wss://", ignoreCase = true))
            ) {
                OpenRingCloudRelayPrefs.setRelayUrl(context, url)
                navController.navigate(Screen.CloudRelay.route) {
                    launchSingleTop = true
                }
            }
            intent.data = null
        }
    }
    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route
    ) {
        composable(Screen.Chat.route) {
            ChatScreen(
                onNavigateToWorkflows = { navController.navigate(Screen.List.route) },
                onNavigateToSkills = { navController.navigate(Screen.AiSettings.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToExecutionLog = { navController.navigate(Screen.ExecutionLog.route) },
                onNavigateToPermissions = { navController.navigate(Screen.Permissions.route) },
                onNavigateToCloudRelay = { navController.navigate(Screen.CloudRelay.route) }
            )
        }
        composable(Screen.ExecutionLog.route) {
            ExecutionLogScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.AiSettings.route) {
            AiSettingsScreen(
                onBack = { navController.popBackStack() },
                onEditSystemPrompt = { navController.navigate(Screen.EditSystemPrompt.route) },
                onEditMoralityPolicy = { navController.navigate(Screen.EditMoralityPolicy.route) },
                onNavigateToSkills = { navController.navigate(Screen.Skills.route) },
                onNavigateToAiModelSettings = { navController.navigate(Screen.AiModelSettings.route) },
                onNavigateToAutoScan = { navController.navigate(Screen.AutoScan.route) }
            )
        }
        composable(Screen.AutoScan.route) {
            AutoScanScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Skills.route) {
            SkillsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.EditSystemPrompt.route) {
            SystemPromptEditScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.EditMoralityPolicy.route) {
            MoralityEditScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLanguageSettings = { navController.navigate(Screen.LanguageSettings.route) },
                onNavigateToPermissionSettings = { navController.navigate(Screen.Permissions.route) },
                onNavigateToCloudRelay = { navController.navigate(Screen.CloudRelay.route) },
                screenMode = SettingsScreenMode.GENERAL
            )
        }
        composable(Screen.CloudRelay.route) {
            CloudRelayScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.AiModelSettings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                screenMode = SettingsScreenMode.AI_MODEL
            )
        }
        composable(Screen.Permissions.route) {
            PermissionsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.LanguageSettings.route) {
            LanguageSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.List.route) {
            ScriptListScreen(
                onBack = { navController.popBackStack() },
                onNavigateToEditor = { id ->
                    navController.navigate(Screen.Editor.createRoute(id))
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                }
            )
        }
        composable("script_editor/{scriptId}") { backStackEntry ->
            val scriptId = backStackEntry.arguments?.getString("scriptId")
            ScriptEditorScreen(
                scriptId = if (scriptId == "new") null else scriptId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.History.route) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }
    }
}
