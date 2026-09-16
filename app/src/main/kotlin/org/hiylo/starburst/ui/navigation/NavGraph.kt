/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : NavGraph.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.navigation

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.R
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.SessionDeepLink
import org.hiylo.starburst.BuildConfig
import org.hiylo.starburst.data.repository.EventReducer
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.data.repository.SettingsRepository
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.domain.model.Session
import org.hiylo.starburst.domain.model.SessionCategory
import org.hiylo.starburst.ui.screens.home.HomeScreen
import org.hiylo.starburst.ui.components.isAmoledTheme
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.sessionCategoryColor
import org.hiylo.starburst.ui.components.sessionCategoryIcon
import org.hiylo.starburst.widget.StarBurstWidgetProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "NavGraph"

internal fun connectedShareServers(
    servers: List<ServerConfig>,
    connectedServerIds: Set<String>,
): List<ServerConfig> = servers.filter { it.id in connectedServerIds }

/** Widget/快捷方式入口的目标服务器：优先当前已连接、其次最近连接、最后任一台。 */
internal fun widgetTargetServer(
    servers: List<ServerConfig>,
    connectedServerIds: Set<String> = emptySet(),
): ServerConfig? {
    if (connectedServerIds.isNotEmpty()) {
        servers.firstOrNull { it.id in connectedServerIds }?.let { return it }
    }
    servers.filter { it.lastConnected != null }
        .maxByOrNull { it.lastConnected ?: 0L }
        ?.let { return it }
    return servers.firstOrNull()
}

internal data class SharePickerItem(
    val server: ServerConfig,
    val session: Session,
    val isFavorite: Boolean,
    val favoriteIndex: Int?,
    val category: SessionCategory?,
)

internal data class SharePickerServerPreferences(
    val favoriteIds: List<String>,
    val categoryAssignments: Map<String, String>,
)

internal fun shouldReopenSharePicker(
    waitingForConnection: Boolean,
    pickerVisible: Boolean,
    hasPendingAttachments: Boolean,
    targetSessionId: String?,
    hasConnectedServers: Boolean,
): Boolean = waitingForConnection && !pickerVisible && hasPendingAttachments &&
    targetSessionId == null && hasConnectedServers

internal fun buildSharePickerItems(
    servers: List<ServerConfig>,
    sessions: List<Session>,
    serverSessions: Map<String, Set<String>>,
    connectedServerIds: Set<String>,
    preferencesByServer: Map<String, SharePickerServerPreferences>,
    favoriteOrder: List<String>,
    categories: List<SessionCategory>,
): List<SharePickerItem> {
    val storedIndices = favoriteOrder.withIndex().associate { it.value to it.index }
    val categoriesById = categories.associateBy(SessionCategory::id)
    return servers.asSequence()
        .filter { it.id in connectedServerIds }
        .flatMapIndexed { serverIndex, server ->
            val sessionIds = serverSessions[server.id].orEmpty()
            val preferences = preferencesByServer[server.id]
                ?: SharePickerServerPreferences(emptyList(), emptyMap())
            val favoriteIndices = preferences.favoriteIds.withIndex().associate { it.value to it.index }
            val candidates = sessions.filter { session ->
                session.id in sessionIds && !session.isArchived && session.parentId == null
            }
            candidates
                .sortedWith(
                    compareByDescending<Session> { it.id in favoriteIndices }
                        .thenBy { favoriteIndices[it.id] ?: Int.MAX_VALUE }
                        .thenByDescending { it.time.updated },
                )
                .take(maxOf(15, candidates.count { it.id in favoriteIndices }))
                .map { session ->
                    val key = "${server.id}:${session.id}"
                    val localIndex = favoriteIndices[session.id]
                    SharePickerItem(
                        server = server,
                        session = session,
                        isFavorite = localIndex != null,
                        favoriteIndex = localIndex?.let {
                            storedIndices[key] ?: favoriteOrder.size + serverIndex * 10_000 + it
                        },
                        category = preferences.categoryAssignments[session.id]?.let(categoriesById::get),
                    )
                }
        }
        .sortedWith(
            compareByDescending<SharePickerItem>(SharePickerItem::isFavorite)
                .thenBy { it.favoriteIndex ?: Int.MAX_VALUE }
                .thenByDescending { it.session.time.updated },
        )
        .toList()
}

/**
 * Main navigation graph for the app
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun NavGraph(
    deepLinkFlow: MutableSharedFlow<SessionDeepLink>,
    navActionFlow: MutableSharedFlow<String>,
    sharedAttachmentsFlow: SharedFlow<List<Uri>>,
    settingsRepository: SettingsRepository,
    serverRepository: ServerRepository,
    eventReducer: EventReducer,
    connectedServerIds: Set<String>,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    
    // Use native UI by default (WebView is legacy)
    val useNativeUi = true
    
    // Flow to tell the *existing* WebView to navigate to a new URL
    // (used when deep-link arrives while WebView is already on screen)
    val webViewNavigateFlow = remember { MutableSharedFlow<String>(extraBufferCapacity = 1) }

    // ============ Share Target Picker state ============
    var showSharePicker by remember { mutableStateOf(false) }
    val pendingShareUrisState = remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pendingShareUris by pendingShareUrisState
    // Target session that should receive the shared attachments (null = not yet chosen)
    val pendingShareSessionIdState = remember { mutableStateOf<String?>(null) }
    var pendingShareSessionId by pendingShareSessionIdState
    var reopenSharePickerAfterConnect by remember { mutableStateOf(false) }
    var addServerRequest by remember { mutableIntStateOf(0) }
    val sharePickerServers by serverRepository.servers.collectAsState(initial = emptyList())
    val sharePickerSessions by eventReducer.sessions.collectAsState()
    val sharePickerServerSessions by eventReducer.serverSessions.collectAsState()
    val sharePickerFavoriteOrder by settingsRepository.crossServerFavoriteOrder.collectAsState(initial = emptyList())
    val sharePickerCategories by settingsRepository.sessionCategories.collectAsState(initial = emptyList())
    val sharePickerPreferences by produceState<Map<String, SharePickerServerPreferences>>(
        initialValue = emptyMap(),
        key1 = sharePickerServers,
    ) {
        if (sharePickerServers.isEmpty()) {
            value = emptyMap()
        } else {
            combine(
                sharePickerServers.map { server ->
                    combine(
                        settingsRepository.favoriteSessionIds(server.id),
                        settingsRepository.sessionCategoryAssignments(server.id),
                    ) { favoriteIds, assignments ->
                        server.id to SharePickerServerPreferences(favoriteIds, assignments)
                    }
                },
            ) { values -> values.toMap() }.collect { value = it }
        }
    }

    // Listen for shared attachments
    LaunchedEffect(Unit) {
        sharedAttachmentsFlow.collect { uris ->
            if (uris.isEmpty()) return@collect
            Log.i(TAG, "Shared attachments received: ${uris.size} URIs")

            // Store pending URIs (will be consumed by the target ChatScreen)
            pendingShareUris = uris
            pendingShareSessionId = null
            reopenSharePickerAfterConnect = false

            // Always show the session picker so the user can choose the target,
            // even when a ChatScreen is already open — the shared content should
            // never silently land in the current session.
            showSharePicker = true
        }
    }

    // Keep a shared attachment pending while the user connects a server from Home.
    LaunchedEffect(
        reopenSharePickerAfterConnect,
        showSharePicker,
        pendingShareUris,
        pendingShareSessionId,
        connectedServerIds,
    ) {
        if (shouldReopenSharePicker(
                waitingForConnection = reopenSharePickerAfterConnect,
                pickerVisible = showSharePicker,
                hasPendingAttachments = pendingShareUris.isNotEmpty(),
                targetSessionId = pendingShareSessionId,
                hasConnectedServers = connectedServerIds.isNotEmpty(),
            )
        ) {
            reopenSharePickerAfterConnect = false
            showSharePicker = true
        }
    }

    // Share Target Picker Dialog
    if (showSharePicker && pendingShareUris.isNotEmpty()) {
        ShareTargetPickerDialog(
            servers = sharePickerServers,
            sessions = sharePickerSessions,
            serverSessions = sharePickerServerSessions,
            connectedServerIds = connectedServerIds,
            preferencesByServer = sharePickerPreferences,
            favoriteOrder = sharePickerFavoriteOrder,
            categories = sharePickerCategories,
            attachmentCount = pendingShareUris.size,
            onSelectSession = { server, session ->
                showSharePicker = false
                reopenSharePickerAfterConnect = false
                pendingShareSessionId = session.id
                val route = Screen.Chat.createRoute(
                    serverUrl = server.url,
                    username = server.username,
                    password = server.password ?: "",
                    serverName = server.displayName,
                    serverId = server.id,
                    sessionId = session.id
                )
                Log.i(TAG, "Share → navigating to session ${session.id} on ${server.displayName}")
                navController.navigate(route) { launchSingleTop = true }
            },
            onNewSession = { server ->
                showSharePicker = false
                reopenSharePickerAfterConnect = false
                // Navigate to session list — user can create a new session there.
                // Attachments remain pending and will be consumed when ChatScreen opens.
                val route = Screen.SessionList.createRoute(
                    serverUrl = server.url,
                    username = server.username,
                    password = server.password ?: "",
                    serverName = server.displayName,
                    serverId = server.id
                )
                Log.i(TAG, "Share → navigating to session list on ${server.displayName}")
                navController.navigate(route) { launchSingleTop = true }
            },
            onManageServers = {
                showSharePicker = false
                reopenSharePickerAfterConnect = connectedServerIds.isEmpty()
                navController.navigate(Screen.Home.route) {
                    launchSingleTop = true
                    popUpTo(Screen.Home.route)
                }
                if (sharePickerServers.isEmpty()) addServerRequest++
            },
            onDismiss = {
                showSharePicker = false
                reopenSharePickerAfterConnect = false
                pendingShareUris = emptyList()
            }
        )
    }

    // Listen for deep-link events from notification taps
    LaunchedEffect(Unit) {
        deepLinkFlow.collect { deepLink ->
            // Consume the event so it's not replayed on recomposition
            deepLinkFlow.resetReplayCache()
            val currentRoute = navController.currentDestination?.route
            if (BuildConfig.DEBUG) Log.d(TAG, "Deep-link received: sessionPath=${deepLink.sessionPath}, sessionId=${deepLink.sessionId}, currentRoute=$currentRoute, useNativeUi=$useNativeUi")
            
            if (useNativeUi) {
                // ---- Native UI path ----
                // Deep-links carry a sessionPath like /L2hvbWUv.../session/<sessionId>
                // Extract the sessionId from the path if present, fall back to raw sessionId
                val sessionId = deepLink.sessionPath
                    .trimEnd('/')
                    .substringAfterLast("/session/", "")
                    .takeIf { it.isNotBlank() }
                    ?: deepLink.sessionId.takeIf { it.isNotBlank() }

                if (sessionId != null) {
                    // Navigate directly into the chat for this session
                    val route = Screen.Chat.createRoute(
                        serverUrl = deepLink.serverUrl,
                        username = deepLink.username,
                        password = deepLink.password,
                        serverName = deepLink.serverName,
                        serverId = deepLink.serverId,
                        sessionId = sessionId,
                        retry = deepLink.retry,
                    )
                    val currentSessionId = navController.currentBackStackEntry
                        ?.arguments
                        ?.getString("sessionId")

                    Log.i(
                        TAG,
                        "Deep-link → native Chat: targetSession=$sessionId currentSession=$currentSessionId"
                    )

                    if (currentRoute?.startsWith("chat") == true && currentSessionId != sessionId) {
                        // Replace current chat screen when switching sessions from notification.
                        // launchSingleTop alone can keep the same top chat destination and skip
                        // visible transition to a different session.
                        navController.popBackStack()
                        navController.navigate(route)
                    } else {
                        navController.navigate(route) { launchSingleTop = true }
                    }
                } else {
                    // No specific session — open session list (placeholder; the
                    // user can also just stay on Home if preferred)
                    Log.i(TAG, "Deep-link has no sessionId, ignoring native path")
                }
            } else {
                // ---- WebView path (legacy) ----
                val isWebViewOnScreen = currentRoute?.startsWith("webview") == true
                
                if (isWebViewOnScreen && deepLink.sessionPath.isNotBlank()) {
                    val newUrl = deepLink.serverUrl.trimEnd('/') + deepLink.sessionPath
                    Log.i(TAG, "WebView already on screen, navigating in-place to: $newUrl")
                    webViewNavigateFlow.tryEmit(newUrl)
                } else {
                    val route = Screen.WebView.createRoute(
                        serverUrl = deepLink.serverUrl,
                        username = deepLink.username,
                        password = deepLink.password,
                        serverName = deepLink.serverName,
                        initialPath = deepLink.sessionPath
                    )
                    Log.i(TAG, "Deep-link → WebView: $route")
                    navController.navigate(route) { launchSingleTop = true }
                }
            }
        }
    }

    // Listen for Widget / App Shortcut entry actions
    LaunchedEffect(Unit) {
        val servers = serverRepository.servers
        navActionFlow.collect { action ->
            navActionFlow.resetReplayCache()
            when (action) {
                StarBurstWidgetProvider.ACTION_SHORTCUT_SEARCH,
                StarBurstWidgetProvider.ACTION_WIDGET_SEARCH,
                -> {
                    navController.navigate(Screen.GlobalSearch.route) { launchSingleTop = true }
                }
                StarBurstWidgetProvider.ACTION_WIDGET_TASK_CENTER -> {
                    // 任务中心需要目标服务器：优先当前已连接、其次最近连接、最后任一台。
                    val target = widgetTargetServer(servers.first(), connectedServerIds)
                    if (target != null) {
                        navController.navigate(
                            Screen.TaskList.createRoute(
                                serverUrl = target.url,
                                username = target.username,
                                password = target.password.orEmpty(),
                                serverName = target.displayName,
                                serverId = target.id,
                            ),
                        ) { launchSingleTop = true }
                    } else {
                        navController.navigate(Screen.Home.route) { launchSingleTop = true }
                    }
                }
                StarBurstWidgetProvider.ACTION_WIDGET_NEW_SESSION,
                StarBurstWidgetProvider.ACTION_SHORTCUT_NEW_SESSION,
                -> {
                    // 新建会话需要目标服务器：优先当前已连接、其次最近连接、最后任一台。
                    val target = widgetTargetServer(servers.first(), connectedServerIds)
                    if (target != null) {
                        // 每次「新建会话」都压入全新的 sessions 目的地（不复用栈内旧条目），
                        // 否则 back stack 里已有的 sessions 条目会因 launchSingleTop 被复用，
                        // 其 ViewModel 不会重新 init，autoNewSession 也就不会再生效。
                        navController.navigate(
                            Screen.SessionList.createRoute(
                                serverUrl = target.url,
                                username = target.username,
                                password = target.password.orEmpty(),
                                serverName = target.displayName,
                                serverId = target.id,
                                autoNewSession = true,
                            ),
                        ) {
                            launchSingleTop = false
                            popUpTo(Screen.Home.route) { inclusive = false }
                        }
                    } else {
                        navController.navigate(Screen.Home.route) { launchSingleTop = true }
                    }
                }
            }
        }
    }
    
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        // ============ Home Screen ============
        composable(Screen.Home.route) {
            HomeScreen(
                addServerRequest = addServerRequest,
                onNavigateToSessions = { serverUrl, username, password, serverName, serverId ->
                    navController.navigate(
                        Screen.SessionList.createRoute(serverUrl, username, password, serverName, serverId)
                    )
                },
                onNavigateToCrossServerSessions = {
                    navController.navigate(Screen.CrossServerSessions.route)
                },
                onNavigateToGlobalSearch = {
                    navController.navigate(Screen.GlobalSearch.route)
                },
                onNavigateToServerSettings = { serverUrl, username, password, serverName, serverId ->
                    navController.navigate(
                        Screen.ServerSettings.createRoute(serverUrl, username, password, serverName, serverId)
                    )
                },
                onNavigateToServerManagement = { serverUrl, username, password, serverName, serverId ->
                    navController.navigate(
                        Screen.ServerManagement.createRoute(serverUrl, username, password, serverName, serverId)
                    )
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToAbout = {
                    navController.navigate(Screen.About.route)
                }
            )
        }

        SettingsRoutes(navController = navController)

        ServerRoutes(navController = navController)

        SessionsRoutes(
            navController = navController,
            serverRepository = serverRepository,
            eventReducer = eventReducer,
            connectedServerIds = connectedServerIds,
            context = context,
        )

        ChatRoutes(
            navController = navController,
            eventReducer = eventReducer,
            connectedServerIds = connectedServerIds,
            pendingShareUris = pendingShareUrisState,
            pendingShareSessionId = pendingShareSessionIdState,
            webViewNavigateFlow = webViewNavigateFlow,
        )
    }
}

@Composable
internal fun EmptyPanePlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.sessions_select_session_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Dialog shown when attachments are shared into the app via ACTION_SEND.
 * Lists recent sessions from servers that have SSE data loaded,
 * grouped by server. User taps a session to open it with the shared attachment(s).
 */
@Composable
private fun ShareTargetPickerDialog(
    servers: List<ServerConfig>,
    sessions: List<Session>,
    serverSessions: Map<String, Set<String>>,
    connectedServerIds: Set<String>,
    preferencesByServer: Map<String, SharePickerServerPreferences>,
    favoriteOrder: List<String>,
    categories: List<SessionCategory>,
    attachmentCount: Int,
    onSelectSession: (server: ServerConfig, session: Session) -> Unit,
    onNewSession: (server: ServerConfig) -> Unit,
    onManageServers: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val isAmoled = isAmoledTheme()

    val items = remember(
        servers,
        sessions,
        serverSessions,
        connectedServerIds,
        preferencesByServer,
        favoriteOrder,
        categories,
    ) {
        buildSharePickerItems(
            servers = servers,
            sessions = sessions,
            serverSessions = serverSessions,
            connectedServerIds = connectedServerIds,
            preferencesByServer = preferencesByServer,
            favoriteOrder = favoriteOrder,
            categories = categories,
        )
    }

    // Servers that have sessions loaded (for the "New session" option)
    val activeServers = remember(servers, connectedServerIds) {
        connectedShareServers(servers, connectedServerIds)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 560.dp)
                .then(
                    if (isAmoled) {
                        Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(28.dp),
                        )
                    } else {
                        Modifier
                    },
                ),
            shape = RoundedCornerShape(28.dp),
            color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = if (isAmoled) 0.dp else 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.share_send_attachments_to),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (attachmentCount == 1)
                                stringResource(R.string.attachment_count_single)
                            else
                                stringResource(R.string.attachment_count_multiple, attachmentCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                when {
                    activeServers.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                modifier = Modifier.size(64.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.secondaryContainer,
                                border = if (isAmoled) {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f))
                                } else null,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.CloudOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(30.dp),
                                        tint = if (isAmoled) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        },
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.share_no_connected_servers),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.share_no_servers_body),
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            AppPrimaryButton(
                                onClick = onManageServers,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            ) {
                                Icon(
                                    imageVector = if (servers.isEmpty()) Icons.Default.Add else Icons.Default.Dns,
                                    contentDescription = null,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(
                                        if (servers.isEmpty()) R.string.share_add_server else R.string.share_manage_servers,
                                    ),
                                )
                            }
                        }
                    }
                    else -> {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

                        if (items.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = stringResource(R.string.share_no_sessions),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(R.string.share_create_session_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 440.dp),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(items, key = { "${it.server.id}/${it.session.id}" }) { item ->
                                    val projectName = item.session.directory
                                        .trimEnd('/')
                                        .substringAfterLast('/')
                                        .ifEmpty { null }
                                    val subtitle = buildString {
                                        if (projectName != null) append(projectName)
                                        if (activeServers.size > 1) {
                                            if (isNotEmpty()) append(" · ")
                                            append(item.server.displayName)
                                        }
                                    }

                                    Surface(
                                        onClick = { onSelectSession(item.server, item.session) },
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isAmoled) Color.Black else Color.Transparent,
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(
                                                alpha = if (isAmoled) 1f else 0.55f,
                                            ),
                                        ),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(40.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.primaryContainer,
                                                border = if (isAmoled) {
                                                    BorderStroke(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                                                    )
                                                } else null,
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.Chat,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(20.dp),
                                                        tint = if (isAmoled) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.onPrimaryContainer
                                                        },
                                                    )
                                                }
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    if (item.isFavorite) {
                                                        Icon(
                                                            Icons.Default.Star,
                                                            contentDescription = stringResource(R.string.session_favorite),
                                                            modifier = Modifier.size(14.dp),
                                                            tint = MaterialTheme.colorScheme.primary,
                                                        )
                                                    }
                                                    Text(
                                                        text = item.session.title
                                                            ?: stringResource(R.string.session_untitled),
                                                        modifier = Modifier.weight(1f),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                if (item.category != null || subtitle.isNotBlank()) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        item.category?.let { category ->
                                                            val categoryColor = sessionCategoryColor(category.color)
                                                            Icon(
                                                                sessionCategoryIcon(category.icon),
                                                                contentDescription = category.name,
                                                                modifier = Modifier.size(13.dp),
                                                                tint = categoryColor,
                                                            )
                                                            Text(
                                                                text = category.name,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = categoryColor,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                            )
                                                        }
                                                        if (subtitle.isNotBlank()) {
                                                            Text(
                                                                text = subtitle,
                                                                modifier = Modifier.weight(1f),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            Text(
                                                text = dateFormat.format(Date(item.session.time.updated)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            for (server in activeServers) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onNewSession(server) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = if (activeServers.size > 1)
                                            stringResource(R.string.sessions_new_on_server, server.displayName)
                                        else
                                            stringResource(R.string.sessions_new_short),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}