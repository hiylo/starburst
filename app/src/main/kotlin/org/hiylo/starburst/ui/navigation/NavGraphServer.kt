/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : NavGraphServer.kt
 * Date : 2026/09/14 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.hiylo.starburst.ui.screens.server.ServerAuditScreen
import org.hiylo.starburst.ui.screens.server.ServerManagementScreen
import org.hiylo.starburst.ui.screens.server.ServerMcpScreen
import org.hiylo.starburst.ui.screens.server.ServerModelFilterScreen
import org.hiylo.starburst.ui.screens.server.ServerProvidersScreen
import org.hiylo.starburst.ui.screens.server.ServerRulesScreen
import org.hiylo.starburst.ui.screens.server.ServerSettingsScreen
import org.hiylo.starburst.ui.screens.server.ServerTokensScreen
import org.hiylo.starburst.ui.screens.server.SkillsScreen
import org.hiylo.starburst.ui.screens.tasks.TaskListScreen

/**
 * 服务器管理相关路由：服务器设置、任务、技能、规则、令牌、审计、Provider、模型过滤、MCP、管理。
 *
 * @author Hsi Chu
 * @since 2026/09/14
 */
fun NavGraphBuilder.ServerRoutes(navController: NavHostController) {
    composable(
        route = "server_settings?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&serverId={serverId}",
        arguments = listOf(
            navArgument("serverUrl") { type = NavType.StringType },
            navArgument("username") { type = NavType.StringType },
            navArgument("password") { type = NavType.StringType },
            navArgument("serverName") { type = NavType.StringType },
            navArgument("serverId") { type = NavType.StringType },
        )
    ) {
        val serverUrl = it.arguments?.getString("serverUrl").orEmpty()
        val username = it.arguments?.getString("username").orEmpty()
        val password = it.arguments?.getString("password").orEmpty()
        val serverName = it.arguments?.getString("serverName").orEmpty()
        val serverId = it.arguments?.getString("serverId").orEmpty()
        ServerSettingsScreen(
            serverId = serverId,
            onNavigateBack = { navController.popBackStack() },
            onOpenProviders = {
                navController.navigate(
                    Screen.ServerProviders.createRoute(
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        serverName = serverName,
                        serverId = serverId
                    )
                )
            },
            onOpenModels = {
                navController.navigate(
                    Screen.ServerModelFilter.createRoute(
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        serverName = serverName,
                        serverId = serverId
                    )
                )
            },
            onOpenMcp = {
                navController.navigate(
                    Screen.ServerMcp.createRoute(
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        serverName = serverName,
                        serverId = serverId,
                    )
                )
            },
            onOpenTasks = {
                navController.navigate(
                    Screen.TaskList.createRoute(
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        serverName = serverName,
                        serverId = serverId,
                    )
                )
            },
            onOpenSkills = {
                navController.navigate(
                    Screen.Skills.createRoute(
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        serverName = serverName,
                        serverId = serverId,
                    )
                )
            },
            onOpenRules = {
                navController.navigate(
                    Screen.ServerRules.createRoute(
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        serverName = serverName,
                        serverId = serverId,
                    )
                )
            },
            onOpenTokens = {
                navController.navigate(
                    Screen.ServerTokens.createRoute(
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        serverName = serverName,
                        serverId = serverId,
                    )
                )
            },
            onOpenAudit = {
                navController.navigate(
                    Screen.ServerAudit.createRoute(
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        serverName = serverName,
                        serverId = serverId,
                    )
                )
            }
        )
    }

    composable(
        route = "tasks?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&serverId={serverId}",
        arguments = listOf(
            navArgument("serverUrl") { type = NavType.StringType },
            navArgument("username") { type = NavType.StringType },
            navArgument("password") { type = NavType.StringType },
            navArgument("serverName") { type = NavType.StringType },
            navArgument("serverId") { type = NavType.StringType },
        )
    ) {
        TaskListScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable(
        route = "skills?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&serverId={serverId}",
        arguments = listOf(
            navArgument("serverUrl") { type = NavType.StringType },
            navArgument("username") { type = NavType.StringType },
            navArgument("password") { type = NavType.StringType },
            navArgument("serverName") { type = NavType.StringType },
            navArgument("serverId") { type = NavType.StringType },
        )
    ) {
        SkillsScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable(
        route = "server_rules?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&serverId={serverId}",
        arguments = listOf(
            navArgument("serverUrl") { type = NavType.StringType },
            navArgument("username") { type = NavType.StringType },
            navArgument("password") { type = NavType.StringType },
            navArgument("serverName") { type = NavType.StringType },
            navArgument("serverId") { type = NavType.StringType },
        )
    ) {
        ServerRulesScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable(
        route = "server_tokens?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&serverId={serverId}",
        arguments = listOf(
            navArgument("serverUrl") { type = NavType.StringType },
            navArgument("username") { type = NavType.StringType },
            navArgument("password") { type = NavType.StringType },
            navArgument("serverName") { type = NavType.StringType },
            navArgument("serverId") { type = NavType.StringType },
        )
    ) {
        ServerTokensScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable(
        route = "server_audit?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&serverId={serverId}",
        arguments = listOf(
            navArgument("serverUrl") { type = NavType.StringType },
            navArgument("username") { type = NavType.StringType },
            navArgument("password") { type = NavType.StringType },
            navArgument("serverName") { type = NavType.StringType },
            navArgument("serverId") { type = NavType.StringType },
        )
    ) {
        ServerAuditScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable(
        route = "server_providers?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&serverId={serverId}",
        arguments = listOf(
            navArgument("serverUrl") { type = NavType.StringType },
            navArgument("username") { type = NavType.StringType },
            navArgument("password") { type = NavType.StringType },
            navArgument("serverName") { type = NavType.StringType },
            navArgument("serverId") { type = NavType.StringType },
        )
    ) {
        ServerProvidersScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(
        route = "server_model_filter?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&serverId={serverId}",
        arguments = listOf(
            navArgument("serverUrl") { type = NavType.StringType },
            navArgument("username") { type = NavType.StringType },
            navArgument("password") { type = NavType.StringType },
            navArgument("serverName") { type = NavType.StringType },
            navArgument("serverId") { type = NavType.StringType },
        )
    ) {
        ServerModelFilterScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(
        route = "server_mcp?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&serverId={serverId}",
        arguments = listOf(
            navArgument("serverUrl") { type = NavType.StringType },
            navArgument("username") { type = NavType.StringType },
            navArgument("password") { type = NavType.StringType },
            navArgument("serverName") { type = NavType.StringType },
            navArgument("serverId") { type = NavType.StringType },
        )
    ) {
        ServerMcpScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable(
        route = "server_management?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&serverId={serverId}&directory={directory}",
        arguments = listOf(
            navArgument("serverUrl") { type = NavType.StringType },
            navArgument("username") { type = NavType.StringType },
            navArgument("password") { type = NavType.StringType },
            navArgument("serverName") { type = NavType.StringType },
            navArgument("serverId") { type = NavType.StringType },
            navArgument("directory") { type = NavType.StringType; defaultValue = "" },
        )
    ) {
        val serverUrl = it.arguments?.getString("serverUrl").orEmpty()
        val username = it.arguments?.getString("username").orEmpty()
        val password = it.arguments?.getString("password").orEmpty()
        val serverName = it.arguments?.getString("serverName").orEmpty()
        val serverId = it.arguments?.getString("serverId").orEmpty()
        ServerManagementScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToKb = { url, user, pwd, name, id ->
                navController.navigate(Screen.Kb.createRoute(url, user, pwd, name, id))
            },
        )
    }
}
