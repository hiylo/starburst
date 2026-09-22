/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : Screen.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal fun encodeNavigationArgument(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

/**
 * 构建带公共 server 参数的路由。
 *
 * 所有 server 路由的公共参数顺序固定为 serverUrl/username/password/serverName/serverId
 * （与 NavGraph 的 route 模式一致），额外参数通过 [extra] 追加，避免每个 Screen 重复
 * encode 相同参数。值统一做 URL 编码。
 */
internal fun serverRoute(
    name: String,
    serverUrl: String,
    username: String,
    password: String,
    serverName: String,
    serverId: String,
    vararg extra: Pair<String, String>,
): String {
    val parts = listOf(
        "serverUrl" to encodeNavigationArgument(serverUrl),
        "username" to encodeNavigationArgument(username),
        "password" to encodeNavigationArgument(password),
        "serverName" to encodeNavigationArgument(serverName),
        "serverId" to encodeNavigationArgument(serverId),
    ) + extra.map { (k, v) -> k to encodeNavigationArgument(v) }
    return "$name?" + parts.joinToString("&") { "${it.first}=${it.second}" }
}

/**
 * Navigation routes for the app
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object CrossServerSessions : Screen("cross_server_sessions")
    data object GlobalSearch : Screen("global_search")
    data object Bookmarks : Screen("bookmarks") {
        fun createRoute(serverId: String, serverName: String): String =
            "bookmarks?serverId=${encodeNavigationArgument(serverId)}&serverName=${encodeNavigationArgument(serverName)}"
    }

    data object FtsSearch : Screen("fts_search") {
        fun createRoute(serverId: String, serverName: String): String =
            "fts_search?serverId=${encodeNavigationArgument(serverId)}&serverName=${encodeNavigationArgument(serverName)}"
    }

    data object SharedSession : Screen("shared_session") {
        fun createRoute(shareId: String): String =
            "shared_session?shareId=${encodeNavigationArgument(shareId)}"
    }
    
    data object WebView : Screen("webview") {
        fun createRoute(
            serverUrl: String,
            username: String,
            password: String,
            serverName: String,
            initialPath: String = ""
        ): String {
            val encodedUrl = encodeNavigationArgument(serverUrl)
            val encodedUsername = encodeNavigationArgument(username)
            val encodedPassword = encodeNavigationArgument(password)
            val encodedName = encodeNavigationArgument(serverName)
            val encodedPath = encodeNavigationArgument(initialPath)
            return "webview?serverUrl=$encodedUrl&username=$encodedUsername&password=$encodedPassword&serverName=$encodedName&initialPath=$encodedPath"
        }
    }
    
    data object SessionList : Screen("sessions") {
        fun createRoute(
            serverUrl: String,
            username: String,
            password: String,
            serverName: String,
            serverId: String,
            autoNewSession: Boolean = false,
        ): String = serverRoute(
            "sessions", serverUrl, username, password, serverName, serverId,
            "autoNewSession" to autoNewSession.toString(),
        )
    }
    
    data object Chat : Screen("chat") {
        fun createRoute(
            serverUrl: String,
            username: String,
            password: String,
            serverName: String,
            serverId: String,
            sessionId: String,
            openTerminal: Boolean = false,
            retry: Boolean = false,
        ): String = serverRoute(
            "chat", serverUrl, username, password, serverName, serverId,
            "sessionId" to sessionId, "openTerminal" to openTerminal.toString(), "retry" to retry.toString(),
        )
    }

    data object WorkspaceFiles : Screen("workspace_files") {
        fun createRoute(
            serverUrl: String,
            username: String,
            password: String,
            directory: String,
        ): String {
            return "workspace_files?serverUrl=${encodeNavigationArgument(serverUrl)}" +
                "&username=${encodeNavigationArgument(username)}" +
                "&password=${encodeNavigationArgument(password)}" +
                "&directory=${encodeNavigationArgument(directory)}"
        }
    }

    data object Git : Screen("git") {
        fun createRoute(
            serverUrl: String,
            username: String,
            password: String,
            serverName: String,
            serverId: String,
            directory: String,
        ): String = serverRoute("git", serverUrl, username, password, serverName, serverId, "directory" to directory)
    }

    data object AgentsMd : Screen("agents_md") {
        fun createRoute(
            serverUrl: String,
            username: String,
            password: String,
            serverName: String,
            serverId: String,
            directory: String,
        ): String = serverRoute("agents_md", serverUrl, username, password, serverName, serverId, "directory" to directory)
    }

    data object ServerSettings : Screen("server_settings") {
        fun createRoute(
            serverUrl: String,
            username: String,
            password: String,
            serverName: String,
            serverId: String
        ): String = serverRoute("server_settings", serverUrl, username, password, serverName, serverId)
    }

    data object ServerManagement : Screen("server_management") {
        fun createRoute(
            serverUrl: String,
            username: String,
            password: String,
            serverName: String,
            serverId: String,
            directory: String = "",
        ): String = serverRoute("server_management", serverUrl, username, password, serverName, serverId, "directory" to directory)
    }

    data object ServerProviders : Screen("server_providers") {
        fun createRoute(
            serverUrl: String,
            username: String,
            password: String,
            serverName: String,
            serverId: String
        ): String = serverRoute("server_providers", serverUrl, username, password, serverName, serverId)
    }

    data object ServerModelFilter : Screen("server_model_filter") {
        fun createRoute(
            serverUrl: String,
            username: String,
            password: String,
            serverName: String,
            serverId: String
        ): String = serverRoute("server_model_filter", serverUrl, username, password, serverName, serverId)
    }

    data object ServerMcp : Screen("server_mcp") {
        fun createRoute(
            serverUrl: String,
            username: String,
            password: String,
            serverName: String,
            serverId: String,
        ): String = serverRoute("server_mcp", serverUrl, username, password, serverName, serverId)
    }

    data object TaskList : Screen("tasks") {
        fun createRoute(
            serverUrl: String,
            username: String,
            password: String,
            serverName: String,
            serverId: String,
        ): String = serverRoute("tasks", serverUrl, username, password, serverName, serverId)
    }

    data object Skills : Screen("skills") {
        fun createRoute(
            serverUrl: String,
            username: String,
            password: String,
            serverName: String,
            serverId: String,
        ): String = serverRoute("skills", serverUrl, username, password, serverName, serverId)
    }

    data object ServerRules : Screen("server_rules") {
        fun createRoute(
            serverUrl: String,
            username: String,
            password: String,
            serverName: String,
            serverId: String,
        ): String = serverRoute("server_rules", serverUrl, username, password, serverName, serverId)
    }

    data object ServerTokens : Screen("server_tokens") {
        fun createRoute(
            serverUrl: String,
            username: String,
            password: String,
            serverName: String,
            serverId: String,
        ): String = serverRoute("server_tokens", serverUrl, username, password, serverName, serverId)
    }

    data object ServerAudit : Screen("server_audit") {
        fun createRoute(
            serverUrl: String,
            username: String,
            password: String,
            serverName: String,
            serverId: String,
        ): String = serverRoute("server_audit", serverUrl, username, password, serverName, serverId)
    }

    data object TestIntel : Screen("test_intel") {
        fun createRoute(
            serverUrl: String,
            username: String,
            password: String,
            serverName: String,
            serverId: String,
        ): String = serverRoute("test_intel", serverUrl, username, password, serverName, serverId)
    }

    data object TestIntelProject : Screen("test_intel_project") {
        fun createRoute(
            serverUrl: String,
            username: String,
            password: String,
            serverName: String,
            serverId: String,
            projectId: Long,
        ): String = serverRoute(
            "test_intel_project", serverUrl, username, password, serverName, serverId,
            "projectId" to projectId.toString(),
        )
    }

    data object Kb : Screen("kb") {
        fun createRoute(
            serverUrl: String,
            username: String,
            password: String,
            serverName: String,
            serverId: String,
        ): String = serverRoute("kb", serverUrl, username, password, serverName, serverId)
    }

    data object KbCollection : Screen("kb_collection") {
        fun createRoute(
            serverUrl: String,
            username: String,
            password: String,
            serverName: String,
            serverId: String,
            collectionId: Long,
        ): String = serverRoute(
            "kb_collection", serverUrl, username, password, serverName, serverId,
            "collectionId" to collectionId.toString(),
        )
    }

    data object Settings : Screen("settings")
    data object SyncSettings : Screen("sync_settings")
    data object Diagnostics : Screen("diagnostics")
    data object LlmProvider : Screen("llm_provider_settings")
    data object About : Screen("about")
}
