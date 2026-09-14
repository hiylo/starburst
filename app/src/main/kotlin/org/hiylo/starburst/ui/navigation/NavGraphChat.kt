/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : NavGraphChat.kt
 * Date : 2026/09/14 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.navigation

import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.MutableSharedFlow
import org.hiylo.starburst.data.repository.EventReducer
import org.hiylo.starburst.ui.screens.chat.ChatScreen
import org.hiylo.starburst.ui.screens.files.AgentsMdScreen
import org.hiylo.starburst.ui.screens.files.WorkspaceFilesScreen
import org.hiylo.starburst.ui.screens.git.GitScreen
import org.hiylo.starburst.ui.screens.shared.SharedSessionScreen
import org.hiylo.starburst.ui.screens.webview.WebViewScreen

/**
 * 聊天相关路由：ChatScreen、共享会话、WebView，以及从会话进入的
 * Workspace/Git/AgentsMd 等附属功能页。
 *
 * @author Hsi Chu
 * @since 2026/09/14
 */
fun NavGraphBuilder.ChatRoutes(
    navController: NavHostController,
    eventReducer: EventReducer,
    connectedServerIds: Set<String>,
    pendingShareUris: MutableState<List<Uri>>,
    pendingShareSessionId: MutableState<String?>,
    webViewNavigateFlow: MutableSharedFlow<String>,
) {
    composable(
        route = "shared_session?shareId={shareId}",
        arguments = listOf(navArgument("shareId") { type = NavType.StringType }),
    ) {
        SharedSessionScreen(onNavigateBack = { navController.popBackStack() })
    }

    // ============ WebView Screen (legacy) ============
    composable(
        route = "webview?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&initialPath={initialPath}",
        arguments = listOf(
            navArgument("serverUrl") {
                type = NavType.StringType
                nullable = false
            },
            navArgument("username") {
                type = NavType.StringType
                nullable = false
            },
            navArgument("password") {
                type = NavType.StringType
                nullable = false
            },
            navArgument("serverName") {
                type = NavType.StringType
                nullable = false
            },
            navArgument("initialPath") {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ) { backStackEntry ->
        val serverUrl = backStackEntry.arguments?.getString("serverUrl").orEmpty()
        val username = backStackEntry.arguments?.getString("username").orEmpty()
        val password = backStackEntry.arguments?.getString("password").orEmpty()
        val serverName = backStackEntry.arguments?.getString("serverName").orEmpty()
        val initialPath = backStackEntry.arguments?.getString("initialPath").orEmpty()

        WebViewScreen(
            serverUrl = serverUrl,
            username = username,
            password = password,
            serverName = serverName,
            initialPath = initialPath,
            navigateUrlFlow = webViewNavigateFlow,
            onNavigateBack = {
                navController.popBackStack()
            }
        )
    }

    // ============ Chat Screen (native) ============
    composable(
        route = "chat?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&serverId={serverId}&sessionId={sessionId}&openTerminal={openTerminal}&retry={retry}",
        arguments = listOf(
            navArgument("serverUrl") { type = NavType.StringType },
            navArgument("username") { type = NavType.StringType },
            navArgument("password") { type = NavType.StringType },
            navArgument("serverName") { type = NavType.StringType },
            navArgument("serverId") { type = NavType.StringType },
            navArgument("sessionId") { type = NavType.StringType },
            navArgument("openTerminal") { type = NavType.BoolType; defaultValue = false },
            navArgument("retry") { type = NavType.BoolType; defaultValue = false }
        )
    ) { backStackEntry ->
        val serverUrl = backStackEntry.arguments?.getString("serverUrl").orEmpty()
        val username = backStackEntry.arguments?.getString("username").orEmpty()
        val password = backStackEntry.arguments?.getString("password").orEmpty()
        val serverName = backStackEntry.arguments?.getString("serverName").orEmpty()
        val serverId = backStackEntry.arguments?.getString("serverId").orEmpty()
        val sessionId = backStackEntry.arguments?.getString("sessionId").orEmpty()
        val openTerminal = backStackEntry.arguments?.getBoolean("openTerminal") ?: false

        // Only pass shared attachments to the targeted session, then clear them
        val attachmentsForThisSession = if (pendingShareSessionId.value == sessionId && pendingShareUris.value.isNotEmpty()) {
            pendingShareUris.value
        } else {
            emptyList()
        }

        ChatScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToSession = { newSessionId ->
                val route = Screen.Chat.createRoute(
                    serverUrl = serverUrl,
                    username = username,
                    password = password,
                    serverName = serverName,
                    serverId = serverId,
                    sessionId = newSessionId
                )
                navController.navigate(route) {
                    // Pop current chat so back goes to session list, not old session
                    popUpTo("sessions?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&serverId={serverId}&autoNewSession={autoNewSession}") {
                        inclusive = false
                    }
                }
            },
            onNavigateToChildSession = { childSessionId ->
                navController.navigate(
                    Screen.Chat.createRoute(
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        serverName = serverName,
                        serverId = serverId,
                        sessionId = childSessionId,
                    ),
                )
            },
            onOpenInWebView = {
                // Build the session path: /<base64url(directory)>/session/<sessionId>
                val session = eventReducer.sessions.value.find { it.id == sessionId }
                val dir = session?.directory ?: ""
                val encodedDir = android.util.Base64.encodeToString(
                    dir.toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP
                ).replace('+', '-').replace('/', '_').replace("=", "")
                val sessionPath = "/$encodedDir/session/$sessionId"
                val route = Screen.WebView.createRoute(
                    serverUrl = serverUrl,
                    username = username,
                    password = password,
                    serverName = serverName,
                    initialPath = sessionPath
                )
                navController.navigate(route) { launchSingleTop = true }
            },
            onOpenSharedSession = { shareId ->
                navController.navigate(Screen.SharedSession.createRoute(shareId)) { launchSingleTop = true }
            },
            onOpenWorkspace = { directory ->
                navController.navigate(
                    Screen.WorkspaceFiles.createRoute(
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        directory = directory,
                    ),
                )
            },
            onOpenAgentsMd = { directory ->
                navController.navigate(
                    Screen.AgentsMd.createRoute(
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        serverName = serverName,
                        serverId = serverId,
                        directory = directory,
                    ),
                )
            },
            onOpenGit = {
                val directory = eventReducer.sessions.value
                    .find { it.id == sessionId }
                    ?.directory
                    .orEmpty()
                navController.navigate(
                    Screen.Git.createRoute(
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        serverName = serverName,
                        serverId = serverId,
                        directory = directory,
                    ),
                )
            },
            onManageModels = {
                navController.navigate(
                    Screen.ServerModelFilter.createRoute(
                        serverUrl = serverUrl,
                        username = username,
                        password = password,
                        serverName = serverName,
                        serverId = serverId,
                    ),
                )
            },
            initialSharedAttachments = attachmentsForThisSession,
            onSharedAttachmentsConsumed = {
                pendingShareUris.value = emptyList()
                pendingShareSessionId.value = null
            },
            startInTerminalMode = openTerminal,
            isServerConnected = serverId in connectedServerIds,
            serverBaseUrl = serverUrl,
            onOpenChatLink = { linkUrl ->
                // Same-origin conversation links open in the built-in WebView
                val base = serverUrl.trimEnd('/')
                val path = linkUrl.removePrefix(base).ifBlank { "/" }
                val route = Screen.WebView.createRoute(
                    serverUrl = serverUrl,
                    username = username,
                    password = password,
                    serverName = serverName,
                    initialPath = path,
                )
                navController.navigate(route) { launchSingleTop = true }
            },
        )
    }

    composable(
        route = "workspace_files?serverUrl={serverUrl}&username={username}&password={password}&directory={directory}",
        arguments = listOf(
            navArgument("serverUrl") { type = NavType.StringType },
            navArgument("username") { type = NavType.StringType },
            navArgument("password") { type = NavType.StringType },
            navArgument("directory") { type = NavType.StringType },
        ),
    ) {
        WorkspaceFilesScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable(
        route = "git?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&serverId={serverId}&directory={directory}",
        arguments = listOf(
            navArgument("serverUrl") { type = NavType.StringType },
            navArgument("username") { type = NavType.StringType },
            navArgument("password") { type = NavType.StringType },
            navArgument("serverName") { type = NavType.StringType },
            navArgument("serverId") { type = NavType.StringType },
            navArgument("directory") { type = NavType.StringType },
        ),
    ) {
        GitScreen(onNavigateBack = { navController.popBackStack() })
    }

    composable(
        route = "agents_md?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&serverId={serverId}&directory={directory}",
        arguments = listOf(
            navArgument("serverUrl") { type = NavType.StringType },
            navArgument("username") { type = NavType.StringType },
            navArgument("password") { type = NavType.StringType },
            navArgument("serverName") { type = NavType.StringType },
            navArgument("serverId") { type = NavType.StringType },
            navArgument("directory") { type = NavType.StringType },
        ),
    ) {
        AgentsMdScreen(onNavigateBack = { navController.popBackStack() })
    }
}
