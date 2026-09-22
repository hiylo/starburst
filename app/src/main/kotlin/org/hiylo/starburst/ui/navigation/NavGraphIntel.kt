/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : NavGraphIntel.kt
 * Date : 2026/09/22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.hiylo.starburst.ui.screens.testintel.TestIntelProjectScreen
import org.hiylo.starburst.ui.screens.testintel.TestIntelScreen

/**
 * 测试智能（3.1.0）路由：项目列表 + 项目详情。
 *
 * 两个路由都复用标准 server 参数（serverUrl/username/password/serverName/serverId），
 * 项目详情额外携带 projectId（字符串形式，读取端转 Long）。
 *
 * @author Hsi Chu
 * @since 3.1.0
 */
fun NavGraphBuilder.buildIntelRoutes(navController: NavHostController) {
    composable(
        route = "test_intel?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&serverId={serverId}",
        arguments = listOf(
            navArgument("serverUrl") { type = NavType.StringType },
            navArgument("username") { type = NavType.StringType },
            navArgument("password") { type = NavType.StringType },
            navArgument("serverName") { type = NavType.StringType },
            navArgument("serverId") { type = NavType.StringType },
        )
    ) { backStackEntry ->
        val serverUrl = backStackEntry.arguments?.getString("serverUrl").orEmpty()
        val username = backStackEntry.arguments?.getString("username").orEmpty()
        val password = backStackEntry.arguments?.getString("password").orEmpty()
        val serverName = backStackEntry.arguments?.getString("serverName").orEmpty()
        val serverId = backStackEntry.arguments?.getString("serverId").orEmpty()
        TestIntelScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenProject = { projectId ->
                navController.navigate(
                    Screen.TestIntelProject.createRoute(
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        serverName = serverName,
                        serverId = serverId,
                        projectId = projectId,
                    ),
                )
            },
        )
    }

    composable(
        route = "test_intel_project?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&serverId={serverId}&projectId={projectId}",
        arguments = listOf(
            navArgument("serverUrl") { type = NavType.StringType },
            navArgument("username") { type = NavType.StringType },
            navArgument("password") { type = NavType.StringType },
            navArgument("serverName") { type = NavType.StringType },
            navArgument("serverId") { type = NavType.StringType },
            navArgument("projectId") { type = NavType.StringType },
        )
    ) {
        TestIntelProjectScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
