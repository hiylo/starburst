/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : NavGraphKb.kt
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
import org.hiylo.starburst.ui.screens.kb.KbCollectionDetailScreen
import org.hiylo.starburst.ui.screens.kb.KbScreen

/**
 * 知识库（3.1.0）路由：集合列表 + 集合详情。
 *
 * 两个路由都复用标准 server 参数（serverUrl/username/password/serverName/serverId），
 * 集合详情额外携带 collectionId（字符串形式，读取端转 Long）。
 *
 * @author Hsi Chu
 * @since 3.1.0
 */
fun NavGraphBuilder.buildKbRoutes(navController: NavHostController) {
    composable(
        route = "kb?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&serverId={serverId}",
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
        KbScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenCollection = { collectionId ->
                navController.navigate(
                    Screen.KbCollection.createRoute(
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        serverName = serverName,
                        serverId = serverId,
                        collectionId = collectionId,
                    ),
                )
            },
        )
    }

    composable(
        route = "kb_collection?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&serverId={serverId}&collectionId={collectionId}",
        arguments = listOf(
            navArgument("serverUrl") { type = NavType.StringType },
            navArgument("username") { type = NavType.StringType },
            navArgument("password") { type = NavType.StringType },
            navArgument("serverName") { type = NavType.StringType },
            navArgument("serverId") { type = NavType.StringType },
            navArgument("collectionId") { type = NavType.StringType },
        )
    ) {
        KbCollectionDetailScreen(
            onNavigateBack = { navController.popBackStack() },
        )
    }
}
