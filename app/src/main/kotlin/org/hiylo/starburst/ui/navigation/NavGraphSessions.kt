/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : NavGraphSessions.kt
 * Date : 2026/09/14 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.hiylo.starburst.data.repository.EventReducer
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.service.StarBurstConnectionService
import org.hiylo.starburst.ui.screens.bookmarks.BookmarksScreen
import org.hiylo.starburst.ui.screens.chat.ChatScreen
import org.hiylo.starburst.ui.screens.search.FtsSearchScreen
import org.hiylo.starburst.ui.screens.sessions.CrossServerSessionsScreen
import org.hiylo.starburst.ui.screens.sessions.GlobalSearchScreen
import org.hiylo.starburst.ui.screens.sessions.SessionListScreen

/**
 * 会话与跨服务器相关路由：跨服务器会话、全局搜索、书签、全文搜索，以及会话列表
 * （含宽屏双栏布局与右侧 pane 的嵌套 NavHost）。
 *
 * @author Hsi Chu
 * @since 2026/09/14
 */
fun NavGraphBuilder.SessionsRoutes(
    navController: NavHostController,
    serverRepository: ServerRepository,
    eventReducer: EventReducer,
    connectedServerIds: Set<String>,
    context: Context,
) {
    composable(Screen.CrossServerSessions.route) {
        CrossServerSessionsScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenSession = { item ->
                navController.navigate(
                    Screen.Chat.createRoute(
                        serverUrl = item.server.url,
                        username = item.server.username,
                        password = item.server.password.orEmpty(),
                        serverName = item.server.displayName,
                        serverId = item.server.id,
                        sessionId = item.session.id,
                    ),
                )
            },
            onConnectServer = { server ->
                val intent = Intent(context, StarBurstConnectionService::class.java).apply {
                    putExtra("server_id", server.id)
                    putExtra("server_name", server.name)
                    putExtra("server_url", server.url)
                    putExtra("server_username", server.username)
                    putExtra("server_password", server.password)
                }
                ContextCompat.startForegroundService(context, intent)
            },
        )
    }

    composable(Screen.GlobalSearch.route) {
        GlobalSearchScreen(
            onNavigateBack = { navController.popBackStack() },
            onOpenSession = { item ->
                if (item.server.id in connectedServerIds) {
                    navController.navigate(
                        Screen.Chat.createRoute(
                            serverUrl = item.server.url,
                            username = item.server.username,
                            password = item.server.password.orEmpty(),
                            serverName = item.server.displayName,
                            serverId = item.server.id,
                            sessionId = item.session.id,
                        ),
                    )
                }
            },
        )
    }

    composable(
        route = "bookmarks?serverId={serverId}&serverName={serverName}",
        arguments = listOf(
            navArgument("serverId") { type = NavType.StringType; defaultValue = "" },
            navArgument("serverName") { type = NavType.StringType; defaultValue = "" },
        ),
    ) { backStackEntry ->
        val servers by serverRepository.servers.collectAsState(initial = emptyList())
        val serverId = backStackEntry.arguments?.getString("serverId").orEmpty()
        val serverName = backStackEntry.arguments?.getString("serverName").orEmpty()
        BookmarksScreen(
            serverName = serverName,
            onNavigateBack = { navController.popBackStack() },
            onOpenSession = { targetServerId, sessionId ->
                val server = servers.firstOrNull { it.id == targetServerId } ?: return@BookmarksScreen
                navController.navigate(
                    Screen.Chat.createRoute(
                        serverUrl = server.url,
                        username = server.username,
                        password = server.password.orEmpty(),
                        serverName = server.displayName,
                        serverId = server.id,
                        sessionId = sessionId,
                    ),
                )
            },
        )
    }

    composable(
        route = "fts_search?serverId={serverId}&serverName={serverName}",
        arguments = listOf(
            navArgument("serverId") { type = NavType.StringType; defaultValue = "" },
            navArgument("serverName") { type = NavType.StringType; defaultValue = "" },
        ),
    ) { backStackEntry ->
        val servers by serverRepository.servers.collectAsState(initial = emptyList())
        val serverId = backStackEntry.arguments?.getString("serverId").orEmpty()
        val serverName = backStackEntry.arguments?.getString("serverName").orEmpty()
        FtsSearchScreen(
            serverName = serverName,
            onNavigateBack = { navController.popBackStack() },
            onOpenResult = { targetServerId, sessionId, _ ->
                val server = servers.firstOrNull { it.id == targetServerId } ?: return@FtsSearchScreen
                navController.navigate(
                    Screen.Chat.createRoute(
                        serverUrl = server.url,
                        username = server.username,
                        password = server.password.orEmpty(),
                        serverName = server.displayName,
                        serverId = server.id,
                        sessionId = sessionId,
                    ),
                )
            },
        )
    }

    // ============ Session List Screen (native) ============
    composable(
        route = "sessions?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&serverId={serverId}&autoNewSession={autoNewSession}",
        arguments = listOf(
            navArgument("serverUrl") { type = NavType.StringType },
            navArgument("username") { type = NavType.StringType },
            navArgument("password") { type = NavType.StringType },
            navArgument("serverName") { type = NavType.StringType },
            navArgument("serverId") { type = NavType.StringType },
            navArgument("autoNewSession") { type = NavType.BoolType; defaultValue = false }
        )
    ) { backStackEntry ->
        val serverUrl = backStackEntry.arguments?.getString("serverUrl").orEmpty()
        val username = backStackEntry.arguments?.getString("username").orEmpty()
        val password = backStackEntry.arguments?.getString("password").orEmpty()
        val serverName = backStackEntry.arguments?.getString("serverName").orEmpty()
        val serverId = backStackEntry.arguments?.getString("serverId").orEmpty()

        // Wide-screen (foldable/unfolded/tablet) two-pane layout: session list + chat side by side.
        // The right pane uses its own nested NavHost whose chat route carries the full
        // parameter set, so the ChatViewModel (argument-driven) reads them correctly.
        // The NavHost is always composed (placeholder as start) and navigation is driven
        // by LaunchedEffect so the graph is always attached before .navigate() is called.
        var paneSessionId by rememberSaveable { mutableStateOf<String?>(null) }
        var paneOpenTerminal by rememberSaveable { mutableStateOf(false) }
        val paneNavController = rememberNavController()
        val allServers by serverRepository.servers.collectAsState(initial = emptyList())

        fun switchToServer(targetServerId: String) {
            val target = allServers.find { it.id == targetServerId } ?: return
            if (target.id == serverId) return
            navController.navigate(
                Screen.SessionList.createRoute(
                    serverUrl = target.url,
                    username = target.username,
                    password = target.password.orEmpty(),
                    serverName = target.displayName,
                    serverId = target.id,
                ),
            ) {
                popUpTo(Screen.Home.route)
                launchSingleTop = true
            }
        }

        fun paneChatRoute(sessionId: String, openTerminal: Boolean): String =
            "pane_chat?serverUrl=$serverUrl&username=$username&password=$password" +
                "&serverName=$serverName&serverId=$serverId&sessionId=$sessionId" +
                "&openTerminal=$openTerminal"

        /**
         * 从会话内跳转到另一个会话（子会话/相关会话）时压栈导航，
         * 保留父会话在 pane 返回栈中，返回时逐级回到父会话。
         */
        fun pushPaneSession(sessionId: String, openTerminal: Boolean) {
            paneNavController.navigate(paneChatRoute(sessionId, openTerminal)) {
                launchSingleTop = false
            }
        }

        /**
         * pane 返回处理：若当前处于子会话（栈深 > 1），逐级弹出回到父会话；
         * 仅在回到栈底占位页时清空 paneSessionId。
         */
        fun paneBack() {
            val stack = paneNavController.currentBackStack.value
            if (stack.size <= 1) {
                paneSessionId = null
                paneOpenTerminal = false
                return
            }
            paneNavController.popBackStack()
            // 弹出后把 paneSessionId 同步到当前可见会话：子会话返回父会话时保持父会话，
            // 回到占位页时清空。
            val top = paneNavController.currentBackStackEntry
            val visibleSessionId = top?.arguments?.getString("sessionId")
            if (top?.destination?.route?.startsWith("pane_chat") == true && !visibleSessionId.isNullOrBlank()) {
                paneSessionId = visibleSessionId
            } else {
                paneSessionId = null
            }
            paneOpenTerminal = false
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 600.dp
            if (isWide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(0.8f).fillMaxHeight()) {
                        SessionListScreen(
                            onNavigateToChat = { sessionId, openTerminal ->
                                paneSessionId = sessionId
                                paneOpenTerminal = openTerminal
                            },
                            onNavigateBack = { navController.popBackStack() },
                            onSwitchServer = { targetServerId ->
                                switchToServer(targetServerId)
                            },
                            onOpenBookmarks = { targetServerId ->
                                navController.navigate(Screen.Bookmarks.createRoute(targetServerId, serverName))
                            },
                            onOpenFtsSearch = { targetServerId ->
                                navController.navigate(Screen.FtsSearch.createRoute(targetServerId, serverName))
                            },
                        )
                    }
                    VerticalDivider()
                    Box(modifier = Modifier.weight(1.1f).fillMaxHeight()) {
                        NavHost(
                            navController = paneNavController,
                            startDestination = "pane_placeholder",
                        ) {
                            composable("pane_placeholder") {
                                EmptyPanePlaceholder()
                            }
                            composable(
                                route = "pane_chat?serverUrl={serverUrl}&username={username}&password={password}" +
                                    "&serverName={serverName}&serverId={serverId}&sessionId={sessionId}&openTerminal={openTerminal}",
                                arguments = listOf(
                                    navArgument("serverUrl") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("username") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("password") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("serverName") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("serverId") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("sessionId") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("openTerminal") { type = NavType.BoolType; defaultValue = false },
                                ),
                            ) { entry ->
                                val sessionId = entry.arguments?.getString("sessionId").orEmpty()
                                if (sessionId.isBlank()) {
                                    EmptyPanePlaceholder()
                                } else {
                                    ChatScreen(
                                        onNavigateBack = { paneBack() },
                                        onNavigateToSession = { newSessionId ->
                                            pushPaneSession(newSessionId, openTerminal = false)
                                        },
                                        onNavigateToChildSession = { childSessionId ->
                                            pushPaneSession(childSessionId, openTerminal = false)
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
                                        startInTerminalMode = entry.arguments?.getBoolean("openTerminal") ?: false,
                                    )
                                }
                            }
                        }
                        // Drive navigation from state after the graph is attached.
                        // Idempotent: only navigate when the target actually changed, so
                        // returning from a full-screen chat to the two-pane layout does not
                        // destroy and recreate the pane ChatScreen/
                        LaunchedEffect(paneSessionId, paneOpenTerminal) {
                            val current = paneSessionId
                            val currRoute = paneNavController.currentBackStackEntry?.destination?.route
                                        ?: paneNavController.currentDestination?.route
                            if (current != null) {
                                val targetUri = paneChatRoute(current, paneOpenTerminal)
                                    .substringBefore('?')
                                val isAlreadyOnSession = currRoute?.startsWith("pane_chat") == true &&
                                    paneNavController.currentBackStackEntry
                                        ?.arguments?.getString("sessionId") == current
                                if (!isAlreadyOnSession) {
                                    // popUpTo the placeholder so every switch creates a fresh chat
                                    // entry with updated arguments (launchSingleTop would reuse the
                                    // existing entry's stale arguments).
                                    paneNavController.navigate(paneChatRoute(current, paneOpenTerminal)) {
                                        launchSingleTop = false
                                        popUpTo("pane_placeholder") { inclusive = false }
                                    }
                                }
                            } else {
                                if (currRoute != "pane_placeholder") {
                                    paneNavController.navigate("pane_placeholder") {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                SessionListScreen(
                    onNavigateToChat = { sessionId, openTerminal ->
                        navController.navigate(
                            Screen.Chat.createRoute(
                                serverUrl = serverUrl,
                                username = username,
                                password = password,
                                serverName = serverName,
                                serverId = serverId,
                                sessionId = sessionId,
                                openTerminal = openTerminal,
                            )
                        )
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onSwitchServer = { targetServerId ->
                        switchToServer(targetServerId)
                    },
                    onOpenBookmarks = { targetServerId ->
                        navController.navigate(Screen.Bookmarks.createRoute(targetServerId, serverName))
                    },
                    onOpenFtsSearch = { targetServerId ->
                        navController.navigate(Screen.FtsSearch.createRoute(targetServerId, serverName))
                    },
                )
            }
        }
    }
}