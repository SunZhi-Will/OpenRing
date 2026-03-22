package com.openring.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.openring.ui.screens.AiSettingsScreen
import com.openring.ui.screens.AutoScanScreen
import com.openring.ui.screens.MoralityEditScreen
import com.openring.ui.screens.SkillsScreen
import com.openring.ui.screens.SystemPromptEditScreen
import com.openring.ui.screens.TextSettingEditorScreen
import com.openring.ui.screens.PermissionsScreen

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
}

@Composable
fun OpenRingNavHost(
    navController: NavHostController = rememberNavController()
) {
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
                onNavigateToPermissions = { navController.navigate(Screen.Permissions.route) }
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
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Permissions.route) {
            PermissionsScreen(onBack = { navController.popBackStack() })
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
