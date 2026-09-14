/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : NavGraphSettings.kt
 * Date : 2026/09/14 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import org.hiylo.starburst.ui.screens.about.AboutScreen
import org.hiylo.starburst.ui.screens.settings.DiagnosticsScreen
import org.hiylo.starburst.ui.screens.settings.LlmProviderSettingsScreen
import org.hiylo.starburst.ui.screens.settings.SettingsScreen
import org.hiylo.starburst.ui.screens.settings.SyncSettingsScreen

/**
 * 应用设置相关路由：设置、LLM Provider 设置、同步设置、诊断、关于。
 *
 * @author Hsi Chu
 * @since 2026/09/14
 */
fun NavGraphBuilder.SettingsRoutes(navController: NavHostController) {
    // ============ Settings Screen ============
    composable(Screen.Settings.route) {
        SettingsScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToDiagnostics = { navController.navigate(Screen.Diagnostics.route) },
            onNavigateToSync = { navController.navigate(Screen.SyncSettings.route) },
            onNavigateToLlmProvider = { navController.navigate(Screen.LlmProvider.route) },
        )
    }

    composable(Screen.LlmProvider.route) {
        LlmProviderSettingsScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable(Screen.SyncSettings.route) {
        SyncSettingsScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }

    composable(Screen.Diagnostics.route) {
        DiagnosticsScreen(onNavigateBack = { navController.popBackStack() })
    }

    // ============ About Screen ============
    composable(Screen.About.route) {
        AboutScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )
    }
}