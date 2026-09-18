/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : HomeScreen.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.hiylo.starburst.R
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.ui.theme.StatusConnected
import org.hiylo.starburst.data.update.UpdateState
import org.hiylo.starburst.data.update.UpdatePolicy
import org.hiylo.starburst.ui.components.AppCardShape
import org.hiylo.starburst.ui.components.AppDialog
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.AppSecondaryButton
import org.hiylo.starburst.ui.components.appAmoledBorder
import org.hiylo.starburst.ui.components.isAmoledTheme
import org.hiylo.starburst.ui.components.appPopupBorder
import org.hiylo.starburst.ui.components.appPopupContainerColor
import org.hiylo.starburst.ui.components.rememberUpdateInstallLauncher
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Legacy local server URL reserved by the previous Termux runtime feature; kept filtered out of the remote list. */
private const val LEGACY_LOCAL_SERVER_URL = "http://127.0.0.1:4096"

/** Pulsing dots loading indicator — 3 dots that scale up/down in sequence. */
@Composable
private fun PulsingDotsIndicator(
    modifier: Modifier = Modifier,
    dotSize: androidx.compose.ui.unit.Dp = 10.dp,
    dotSpacing: androidx.compose.ui.unit.Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "pulsing_dots")
    val scales2 = (0..2).map { index ->
        transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1200
                    val offset = index * 150
                    0.4f at 0 + offset
                    1.0f at 300 + offset
                    0.4f at 600 + offset
                    0.4f at 1200
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "dot_scale_$index"
        )
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        scales2.forEach { scale ->
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        alpha = 0.3f + 0.7f * ((scale.value - 0.4f) / 0.6f)
                    }
                    .background(color, CircleShape)
            )
        }
    }
}

/**
 * Home Screen - Server list and management
 * 
 * Each server card has Connect/Disconnect/Sessions buttons.
 * Multiple servers can be connected simultaneously.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    addServerRequest: Int = 0,
    onNavigateToSessions: (serverUrl: String, username: String, password: String, serverName: String, serverId: String) -> Unit = { _, _, _, _, _ -> },
    onNavigateToCrossServerSessions: () -> Unit = {},
    onNavigateToGlobalSearch: () -> Unit = {},
    onNavigateToServerSettings: (serverUrl: String, username: String, password: String, serverName: String, serverId: String) -> Unit = { _, _, _, _, _ -> },
    onNavigateToServerManagement: (serverUrl: String, username: String, password: String, serverName: String, serverId: String) -> Unit = { _, _, _, _, _ -> },
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val launchInstaller = rememberUpdateInstallLauncher(viewModel::installerLaunched)

    val readyUpdate = uiState.updateState as? UpdateState.ReadyToInstall
    LaunchedEffect(readyUpdate?.apkPath) {
        readyUpdate?.let { launchInstaller(it.apkPath) }
    }

    LaunchedEffect(addServerRequest) {
        if (addServerRequest > 0) viewModel.showAddServerDialog()
    }

    // Track battery optimization status, re-check when app resumes
    var isBatteryOptimized by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                isBatteryOptimized = !pm.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // We need to track which server requested notification permission so we
    // can resume the connect flow after the permission dialog.
    var pendingConnectServerId by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Whether granted or denied, proceed with connection
        pendingConnectServerId?.let { viewModel.connectToServer(it) }
        pendingConnectServerId = null
    }

    fun requestNotificationPermissionAndConnect(serverId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingConnectServerId = serverId
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.connectToServer(serverId)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = { viewModel.showAddServerDialog() }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.home_add_server))
                    }
                    IconButton(onClick = onNavigateToGlobalSearch) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.global_search_title))
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                    IconButton(onClick = onNavigateToAbout) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.about_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(padding)
        ) {
            when {
                uiState.isLoading || uiState.hasFavoriteSessions == null -> {
                    PulsingDotsIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        dotSize = 12.dp,
                        dotSpacing = 8.dp
                    )
                }
                else -> {
                    val remoteServers = uiState.servers.filterNot { it.url == LEGACY_LOCAL_SERVER_URL }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Battery optimization warning banner
                        if (isBatteryOptimized) {
                            item(key = "__battery_banner") {
                                BatteryOptimizationBanner(
                                    onDisable = {
                                        val intent = Intent(
                                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    }
                                )
                            }
                        }

                        if (uiState.updateState is UpdateState.Available || uiState.updateState is UpdateState.Error) {
                            item(key = "__app_update") {
                                UpdateAvailableCard(
                                    updateState = uiState.updateState,
                                    onPrepareInstall = viewModel::prepareInstall,
                                    onOpenInstaller = launchInstaller,
                                    onCheckUpdates = viewModel::checkForUpdates,
                                    onOpenRelease = { releaseUrl ->
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)),
                                        )
                                    },
                                )
                            }
                        }

                        if (uiState.hasFavoriteSessions == true) {
                            item(key = "__favorite_sessions") {
                                FavoritesCard(onClick = onNavigateToCrossServerSessions)
                            }
                        }

                        if (remoteServers.isEmpty()) {
                            item(key = "__empty_servers") {
                                EmptyServersView(
                                    onAddServer = { viewModel.showAddServerDialog() },
                                    modifier = Modifier.fillParentMaxHeight(0.8f),
                                )
                            }
                        }

                        items(remoteServers, key = { it.id }) { server ->
                            ServerCard(
                                server = server,
                                isConnected = server.id in uiState.connectedServerIds,
                                isConnecting = server.id in uiState.connectingServerIds,
                                connectionError = uiState.connectionErrors[server.id],
                                showServerSettings = server.id in uiState.serverSettingsReadyIds,
                                isRestartingViaSsh = server.id in uiState.restartingServerIds,
                                onConnect = { requestNotificationPermissionAndConnect(server.id) },
                                onDisconnect = { viewModel.disconnectFromServer(server.id) },
                                onOpenSessions = {
                                    onNavigateToSessions(
                                        server.url,
                                        server.username,
                                        server.password ?: "",
                                        server.displayName,
                                        server.id
                                    )
                                },
                                onServerSettings = {
                                    onNavigateToServerSettings(
                                        server.url,
                                        server.username,
                                        server.password ?: "",
                                        server.displayName,
                                        server.id
                                    )
                                },
                                onManageServer = {
                                    onNavigateToServerManagement(
                                        server.url,
                                        server.username,
                                        server.password ?: "",
                                        server.displayName,
                                        server.id
                                    )
                                },
                                onEdit = { viewModel.showEditServerDialog(server) },
                                onDelete = { viewModel.deleteServer(server.id) },
                                onRestartViaSsh = { viewModel.restartServerViaSsh(server.id) }
                            )
                        }
                    }
                }
            }
        }

        // Add/Edit Server Dialog
        if (uiState.showAddServerDialog) {
            ServerDialog(
                server = uiState.editingServer,
                onDismiss = { viewModel.hideServerDialog() },
                onSave = { name, url, username, password, autoConnect, sshPort, sshUsername, sshPassword, backendToken ->
                    viewModel.saveServer(name, url, username, password, autoConnect, sshPort, sshUsername, sshPassword, backendToken)
                }
            )
        }

    }
}

@Composable
private fun FavoritesCard(onClick: () -> Unit) {
    val isAmoled = isAmoledTheme()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = appAmoledBorder(alpha = 0.65f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.cross_sessions_favorites),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UpdateAvailableCard(
    updateState: UpdateState,
    onPrepareInstall: (org.hiylo.starburst.data.update.AvailableUpdate) -> Unit,
    onOpenInstaller: (String) -> Unit,
    onCheckUpdates: () -> Unit,
    onOpenRelease: (String) -> Unit,
) {
    val release = when (updateState) {
        is UpdateState.Available -> updateState.release
        is UpdateState.Error -> updateState.release
        else -> return
    }
    val isAmoled = isAmoledTheme()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.primaryContainer,
        ),
        border = appAmoledBorder(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.update_available_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    when (updateState) {
                        is UpdateState.Downloading -> updateState.progressPercent?.let {
                            stringResource(R.string.update_downloading_percent, it)
                        } ?: stringResource(R.string.update_downloading)

                        is UpdateState.Error -> if (release != null) {
                            stringResource(R.string.update_prepare_error)
                        } else {
                            stringResource(R.string.update_error)
                        }

                        else -> stringResource(R.string.update_available_message, requireNotNull(release).versionName)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when (updateState) {
                is UpdateState.Downloading -> TextButton(
                    onClick = {},
                    enabled = false,
                ) { Text(stringResource(R.string.update_downloading)) }

                is UpdateState.ReadyToInstall -> TextButton(
                    onClick = { onOpenInstaller(updateState.apkPath) },
                ) { Text(stringResource(R.string.update_opening_installer)) }

                is UpdateState.Available -> TextButton(
                    onClick = {
                        val availableRelease = requireNotNull(release)
                        if (UpdatePolicy.isInstallable(availableRelease)) onPrepareInstall(availableRelease)
                        else onOpenRelease(availableRelease.releaseUrl)
                    },
                ) {
                    Text(
                        stringResource(
                            if (UpdatePolicy.isInstallable(requireNotNull(release))) {
                                R.string.update_download_and_install
                            } else {
                                R.string.update_open_release
                            },
                        ),
                    )
                }

                is UpdateState.Error -> TextButton(
                    onClick = {
                        if (release == null) onCheckUpdates()
                        else if (UpdatePolicy.isInstallable(release)) onPrepareInstall(release)
                        else onOpenRelease(release.releaseUrl)
                    },
                ) {
                    Text(
                        stringResource(
                            when {
                                release == null -> R.string.about_check_updates
                                UpdatePolicy.isInstallable(release) -> R.string.update_retry
                                else -> R.string.update_open_release
                            },
                        ),
                    )
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun EmptyServersView(
    onAddServer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Text(
                text = stringResource(R.string.home_no_servers),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            AppPrimaryButton(onClick = onAddServer) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_add_server))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerCard(
    server: ServerConfig,
    isConnected: Boolean,
    isConnecting: Boolean,
    connectionError: String?,
    showServerSettings: Boolean,
    isRestartingViaSsh: Boolean = false,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenSessions: () -> Unit,
    onServerSettings: () -> Unit,
    onManageServer: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRestartViaSsh: () -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val isAmoled = isAmoledTheme()
    val cardContainerColor = if (isAmoled) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val cardContentColor = if (isConnected && !isAmoled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = cardContainerColor
        ),
        border = if (isAmoled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row: name, URL, status, menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = cardContentColor
                    )
                    Text(
                        text = server.url,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cardContentColor.copy(alpha = 0.7f)
                    )
                    if (isConnected) {
                        Text(
                            text = stringResource(R.string.home_server_health_good),
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusConnected
                        )
                    } else if (isConnecting) {
                        Text(
                            text = stringResource(R.string.home_connecting),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showServerSettings) {
                        IconButton(onClick = onServerSettings) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.server_settings_title))
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.appPopupBorder(),
                            containerColor = appPopupContainerColor(),
                        ) {
                            if (isConnected) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.server_mgmt_title)) },
                                    onClick = {
                                        showMenu = false
                                        onManageServer()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Settings, contentDescription = null)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_edit)) },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.server_delete),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirmation = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Connection error
            if (connectionError != null) {
                Text(
                    text = connectionError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isConnected) {
                    AppPrimaryButton(
                        onClick = onOpenSessions,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.sessions_title), maxLines = 1)
                    }
                }
            }
            if (isConnected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppSecondaryButton(
                        onClick = onDisconnect,
                        modifier = Modifier.fillMaxWidth(),
                        destructive = true,
                        outlined = true,
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.home_disconnect), maxLines = 1)
                    }
                }
            }
            if (!isConnected) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppPrimaryButton(
                        onClick = onConnect,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isConnecting,
                    ) {
                        if (isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = if (isAmoled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.home_connecting))
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.home_connect))
                        }
                    }
                    if (connectionError != null && server.useSsh) {
                        AppSecondaryButton(
                            onClick = onRestartViaSsh,
                            modifier = Modifier.fillMaxWidth(),
                            outlined = true,
                            enabled = !isRestartingViaSsh,
                        ) {
                            if (isRestartingViaSsh) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(6.dp))
                            } else {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(stringResource(R.string.home_ssh_restart), maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AppDialog(onDismissRequest = { showDeleteConfirmation = false }) {
            Text(
                text = stringResource(R.string.server_delete_confirm_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp),
            )
            Text(
                text = stringResource(R.string.server_delete_confirm_message, server.displayName),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                AppSecondaryButton(
                    onClick = { showDeleteConfirmation = false },
                    outlined = true,
                ) {
                    Text(stringResource(R.string.cancel))
                }
                AppPrimaryButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    destructive = true,
                ) {
                    Text(stringResource(R.string.server_delete))
                }
            }
        }
    }
}

@Composable
private fun BatteryOptimizationBanner(
    onDisable: () -> Unit
) {
    val isAmoled = isAmoledTheme()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.errorContainer,
        ),
        border = if (isAmoled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
        } else null,
        shape = AppCardShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BatteryAlert,
                contentDescription = null,
                tint = if (isAmoled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_battery_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isAmoled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(R.string.home_battery_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isAmoled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    },
                )
            }
            AppPrimaryButton(onClick = onDisable) {
                Text(stringResource(R.string.home_fix))
            }
        }
    }
}
