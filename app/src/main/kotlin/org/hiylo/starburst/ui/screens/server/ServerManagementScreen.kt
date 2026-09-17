/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ServerManagementScreen.kt
 * Date : 2026/09/08 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.AppCardShape
import org.hiylo.starburst.ui.components.AppDialog
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.AppSecondaryButton
import org.hiylo.starburst.ui.components.appAmoledBorder
import org.hiylo.starburst.ui.components.isAmoledTheme
import org.hiylo.starburst.service.ServerConnectionStatus
import org.hiylo.starburst.ui.theme.StatusConnected
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/**
 * 服务器管理页：展示服务/服务器信息、查看与修改服务配置，并支持重启服务。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: ServerManagementViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isAmoled = isAmoledTheme()
    var showRestartConfirm by rememberSaveable { mutableStateOf(false) }
    var showLogTail by rememberSaveable { mutableStateOf(false) }

    var modelText by rememberSaveable { mutableStateOf("") }
    var agentText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(uiState.config) {
        modelText = uiState.config.model.orEmpty()
        agentText = uiState.config.defaultAgent.orEmpty()
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.server_mgmt_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.server_mgmt_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            uiState.message?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // 连接健康度
            ConnectionHealthSection(isAmoled = isAmoled, health = uiState.connectionHealth)

            // starburst-backend 可用性（门控占位）：探测中/不可用时不展示任何后端功能入口，
            // 仅显示状态；「正常可用」（健康 + 版本达标）时才说明后端就绪。
            BackendStatusSection(
                isAmoled = isAmoled,
                probing = uiState.backendAvailable == null,
                ready = viewModel.isBackendReady,
            )

            // 服务基本信息
            SectionCard(isAmoled = isAmoled) {
                SectionHeader(stringResource(R.string.server_mgmt_basic_info))
                InfoRow(
                    label = stringResource(R.string.server_mgmt_version),
                    value = uiState.version ?: stringResource(R.string.server_mgmt_unknown),
                )
                // 服务端升级提示（界面壳，升级功能待实现）
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                R.string.server_mgmt_server_version,
                                uiState.version ?: stringResource(R.string.server_mgmt_unknown),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { /* 升级功能待实现 */ }) {
                            Text(stringResource(R.string.server_mgmt_upgrade))
                        }
                    }
                }
                InfoRow(
                    label = stringResource(R.string.server_mgmt_active_sessions),
                    value = when {
                        uiState.activeSessions == null -> stringResource(R.string.server_mgmt_unknown)
                        uiState.busySessions != null && uiState.busySessions!! > 0 ->
                            stringResource(R.string.server_mgmt_sessions_busy_value, uiState.activeSessions!!, uiState.busySessions!!)
                        else -> stringResource(R.string.server_mgmt_sessions_value, uiState.activeSessions!!)
                    },
                )
            }

            // 服务器资源信息
            SectionCard(isAmoled = isAmoled) {
                SectionHeader(stringResource(R.string.server_mgmt_system_info))
                InfoRow(
                    label = stringResource(R.string.server_mgmt_memory),
                    value = uiState.memory ?: stringResource(R.string.server_mgmt_unknown),
                )
                InfoRow(
                    label = stringResource(R.string.server_mgmt_disk),
                    value = uiState.disk ?: stringResource(R.string.server_mgmt_unknown),
                )
                InfoRow(
                    label = stringResource(R.string.server_mgmt_load),
                    value = uiState.loadAverage ?: stringResource(R.string.server_mgmt_unknown),
                )
                InfoRow(
                    label = stringResource(R.string.server_mgmt_process_cpu),
                    value = uiState.processCpu ?: stringResource(R.string.server_mgmt_unknown),
                )
                InfoRow(
                    label = stringResource(R.string.server_mgmt_process_memory),
                    value = uiState.processMemory ?: stringResource(R.string.server_mgmt_unknown),
                )
            }

            // 服务配置
            SectionCard(isAmoled = isAmoled) {
                SectionHeader(stringResource(R.string.server_mgmt_config))
                OutlinedTextField(
                    value = modelText,
                    onValueChange = { modelText = it },
                    label = { Text(stringResource(R.string.server_mgmt_default_model)) },
                    placeholder = { Text(stringResource(R.string.server_mgmt_default_model_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = agentText,
                    onValueChange = { agentText = it },
                    label = { Text(stringResource(R.string.server_mgmt_default_agent)) },
                    placeholder = { Text(stringResource(R.string.server_mgmt_default_agent_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AppPrimaryButton(
                    onClick = {
                        viewModel.updateDefaultModel(modelText)
                        viewModel.updateDefaultAgent(agentText)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSavingConfig,
                ) {
                    if (uiState.isSavingConfig) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(stringResource(R.string.server_mgmt_save))
                }
            }

            // 服务日志（实时跟踪）
            SectionCard(isAmoled = isAmoled) {
                SectionHeader(stringResource(R.string.log_tail_section_title))
                AppPrimaryButton(
                    onClick = { showLogTail = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.width(18.dp).height(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.log_tail_open))
                }
            }

            // 服务重启
            SectionCard(isAmoled = isAmoled) {
                SectionHeader(stringResource(R.string.server_mgmt_restart))
                AppPrimaryButton(
                    onClick = { showRestartConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isRestarting,
                    destructive = true,
                ) {
                    if (uiState.isRestarting) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.server_mgmt_restarting))
                    } else {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.width(18.dp).height(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.server_mgmt_restart))
                    }
                }
            }
        }
    }

    if (showRestartConfirm) {
        AppDialog(onDismissRequest = { showRestartConfirm = false }) {
            Text(
                text = stringResource(R.string.server_mgmt_restart_confirm_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp),
            )
            Text(
                text = stringResource(R.string.server_mgmt_restart_confirm_message),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                AppSecondaryButton(
                    onClick = { showRestartConfirm = false },
                    outlined = true,
                ) {
                    Text(stringResource(R.string.cancel))
                }
                AppPrimaryButton(
                    onClick = {
                        showRestartConfirm = false
                        viewModel.restartServer()
                    },
                    destructive = true,
                ) {
                    Text(stringResource(R.string.server_mgmt_confirm))
                }
            }
        }
    }

    if (showLogTail) {
        LogTailDialog(onDismiss = { showLogTail = false })
    }
}

@Composable
private fun SectionCard(isAmoled: Boolean, content: @Composable () -> Unit) {
    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = appAmoledBorder(0.65f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor ?: Color.Unspecified,
        )
    }
}

@Composable
private fun ConnectionHealthSection(
    isAmoled: Boolean,
    health: ServerConnectionHealthUi?,
) {
    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    SectionCard(isAmoled = isAmoled) {
        SectionHeader(stringResource(R.string.server_health_title))
        val status = health?.status ?: ServerConnectionStatus.DISCONNECTED
        val (statusText, statusColor) = when (status) {
            ServerConnectionStatus.CONNECTED -> stringResource(R.string.server_health_connected) to StatusConnected
            ServerConnectionStatus.RECONNECTING -> stringResource(R.string.server_health_reconnecting) to MaterialTheme.colorScheme.tertiary
            ServerConnectionStatus.FAILED -> stringResource(R.string.server_health_failed) to MaterialTheme.colorScheme.error
            ServerConnectionStatus.DISCONNECTED -> stringResource(R.string.server_health_disconnected) to MaterialTheme.colorScheme.onSurfaceVariant
        }
        InfoRow(
            label = stringResource(R.string.server_health_status),
            value = statusText,
            valueColor = statusColor,
        )
        InfoRow(
            label = stringResource(R.string.server_health_latency),
            value = health?.latencyMs?.let { stringResource(R.string.server_health_latency_value, it) }
                ?: stringResource(R.string.server_mgmt_unknown),
        )
        InfoRow(
            label = stringResource(R.string.server_health_last_heartbeat),
            value = health?.lastHeartbeatAt?.let { last ->
                val seconds = ((nowMillis - last) / 1_000).coerceAtLeast(0)
                stringResource(R.string.server_health_heartbeat_ago, seconds)
            } ?: stringResource(R.string.server_health_heartbeat_never),
        )
    }
}

/**
 * starburst-backend 状态区：探测中显示 checking，正常可用显示 ready，否则显示不可用占位。
 * 后端不可用/异常时不展示任何后端功能入口，仅保留该状态提示。
 */
@Composable
private fun BackendStatusSection(
    isAmoled: Boolean,
    probing: Boolean,
    ready: Boolean,
) {
    SectionCard(isAmoled = isAmoled) {
        SectionHeader(stringResource(R.string.backend_status_title))
        val (statusText, statusColor) = when {
            probing -> stringResource(R.string.backend_checking) to MaterialTheme.colorScheme.tertiary
            ready -> stringResource(R.string.backend_status_ready) to StatusConnected
            else -> stringResource(R.string.backend_status_unavailable) to MaterialTheme.colorScheme.error
        }
        InfoRow(
            label = stringResource(R.string.backend_status_label),
            value = statusText,
            valueColor = statusColor,
        )
    }
}
