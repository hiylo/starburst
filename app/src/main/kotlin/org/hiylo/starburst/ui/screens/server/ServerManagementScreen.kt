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

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.AlertsSnapshot
import org.hiylo.starburst.data.api.AlertsThresholds
import org.hiylo.starburst.data.api.AlertsMetrics
import org.hiylo.starburst.data.api.AlertsUpdateRequest
import org.hiylo.starburst.data.repository.AlertHistoryEntry
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
    onNavigateToTestIntel: (serverUrl: String, username: String, password: String, serverName: String, serverId: String) -> Unit = { _, _, _, _, _ -> },
    onNavigateToKb: (serverUrl: String, username: String, password: String, serverName: String, serverId: String) -> Unit = { _, _, _, _, _ -> },
    viewModel: ServerManagementViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val alertsUiState by viewModel.alertsUiState.collectAsState()
    val alertHistoryUiState by viewModel.alertHistoryUiState.collectAsState()
    val isAmoled = isAmoledTheme()
    var showRestartConfirm by rememberSaveable { mutableStateOf(false) }
    var showLogTail by rememberSaveable { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) { viewModel.attachLifecycle(lifecycleOwner.lifecycle) }

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

            // 测试智能（后端就绪才展示）
            if (viewModel.isBackendReady) {
                TestIntelEntrySection(
                    isAmoled = isAmoled,
                    onClick = {
                        onNavigateToTestIntel(
                            viewModel.serverUrl,
                            viewModel.username,
                            viewModel.password,
                            viewModel.serverName,
                            viewModel.serverId,
                        )
                    },
                )
            }

            // 知识库（后端就绪才展示）
            if (viewModel.isBackendReady) {
                KbEntrySection(
                    isAmoled = isAmoled,
                    onClick = {
                        onNavigateToKb(
                            viewModel.serverUrl,
                            viewModel.username,
                            viewModel.password,
                            viewModel.serverName,
                            viewModel.serverId,
                        )
                    },
                )
            }

            // 监控告警（后端就绪才展示）
            if (viewModel.isBackendReady) {
                AlertsSection(
                    isAmoled = isAmoled,
                    uiState = alertsUiState,
                    onRefresh = viewModel::loadAlerts,
                    onToggleEnabled = { enabled ->
                        viewModel.updateAlerts(AlertsUpdateRequest(enabled = enabled))
                    },
                    onUpdateThreshold = { metric, value ->
                        viewModel.updateAlerts(
                            when (metric) {
                                "cpu" -> AlertsUpdateRequest(cpuPct = value)
                                "mem" -> AlertsUpdateRequest(memPct = value)
                                else -> AlertsUpdateRequest(diskPct = value)
                            },
                        )
                    },
                )
                AlertHistorySection(
                    isAmoled = isAmoled,
                    uiState = alertHistoryUiState,
                    onRefresh = viewModel::loadAlertHistory,
                )
            }

            // 服务基本信息
            SectionCard(isAmoled = isAmoled) {
                SectionHeader(stringResource(R.string.server_mgmt_basic_info))
                InfoRow(
                    label = stringResource(R.string.server_mgmt_version),
                    value = uiState.version ?: stringResource(R.string.server_mgmt_unknown),
                )
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

/** 知识库入口卡片：后端就绪时跳转到集合列表页。 */
@Composable
private fun TestIntelEntrySection(isAmoled: Boolean, onClick: () -> Unit) {
    SectionCard(isAmoled = isAmoled) {
        SectionHeader(stringResource(R.string.test_intel))
        AppPrimaryButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.width(18.dp).height(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.test_intel_enter))
        }
    }
}

@Composable
private fun KbEntrySection(isAmoled: Boolean, onClick: () -> Unit) {
    SectionCard(isAmoled = isAmoled) {
        SectionHeader(stringResource(R.string.kb_title))
        AppPrimaryButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.width(18.dp).height(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.kb_enter))
        }
    }
}

/** 监控告警卡片：启用开关 + CPU/内存/磁盘当前值与阈值编辑。 */
@Composable
private fun AlertsSection(
    isAmoled: Boolean,
    uiState: AlertsUiState,
    onRefresh: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onUpdateThreshold: (metric: String, value: Double) -> Unit,
) {
    var editingMetric by rememberSaveable { mutableStateOf<String?>(null) }

    SectionCard(isAmoled = isAmoled) {
        SectionHeader(stringResource(R.string.alert_title))
        when {
            uiState.loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            uiState.error != null -> {
                Text(
                    text = uiState.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                AppSecondaryButton(
                    onClick = onRefresh,
                    outlined = true,
                ) {
                    Text(stringResource(R.string.server_mgmt_refresh))
                }
            }
            uiState.snapshot == null -> {
                Text(
                    text = stringResource(R.string.server_backend_not_configured),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                val snapshot = uiState.snapshot!!
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.alert_enabled),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = snapshot.enabled,
                        onCheckedChange = onToggleEnabled,
                    )
                }

                AlertMetricRow(
                    label = stringResource(R.string.alert_cpu),
                    metric = "cpu",
                    snapshot = snapshot,
                    onEdit = { editingMetric = "cpu" },
                )
                AlertMetricRow(
                    label = stringResource(R.string.alert_memory),
                    metric = "mem",
                    snapshot = snapshot,
                    onEdit = { editingMetric = "mem" },
                )
                AlertMetricRow(
                    label = stringResource(R.string.alert_disk),
                    metric = "disk",
                    snapshot = snapshot,
                    onEdit = { editingMetric = "disk" },
                )
            }
        }
    }

    editingMetric?.let { metric ->
        val snapshot = uiState.snapshot
        val current = snapshot?.thresholds?.valueFor(metric) ?: 0.0
        val metricLabel = when (metric) {
            "cpu" -> stringResource(R.string.alert_cpu)
            "mem" -> stringResource(R.string.alert_memory)
            else -> stringResource(R.string.alert_disk)
        }
        ThresholdEditDialog(
            title = stringResource(R.string.alert_threshold_title, metricLabel),
            initialValue = current,
            onDismiss = { editingMetric = null },
            onConfirm = { value ->
                editingMetric = null
                onUpdateThreshold(metric, value)
            },
        )
    }
}

private fun AlertsThresholds.valueFor(metric: String): Double = when (metric) {
    "cpu" -> this.cpuPct
    "mem" -> this.memPct
    else -> this.diskPct
}

private fun AlertsMetrics.valueFor(metric: String): Double = when (metric) {
    "cpu" -> this.cpuPct
    "mem" -> this.memPct
    else -> this.diskPct
}

/** 告警历史卡片：最近越线/恢复事件的本地记录（时间倒序）。 */
@Composable
private fun AlertHistorySection(
    isAmoled: Boolean,
    uiState: AlertHistoryUiState,
    onRefresh: () -> Unit,
) {
    SectionCard(isAmoled = isAmoled) {
        SectionHeader(stringResource(R.string.alert_history))
        when {
            uiState.loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            uiState.error != null -> Text(
                text = uiState.error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            uiState.entries.isEmpty() -> Text(
                text = stringResource(R.string.alert_history_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> uiState.entries.forEach { entry ->
                AlertHistoryRow(entry = entry)
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.server_mgmt_refresh))
            }
        }
    }
}

@Composable
private fun AlertHistoryRow(entry: AlertHistoryEntry) {
    val alerting = entry.state == "alert"
    val metricLabel = when (entry.metric) {
        "cpu" -> stringResource(R.string.alert_cpu)
        "mem" -> stringResource(R.string.alert_memory)
        else -> stringResource(R.string.alert_disk)
    }
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (alerting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = metricLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(
                    if (alerting) R.string.alert_history_state_alert else R.string.alert_history_state_ok,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (alerting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = stringResource(R.string.alert_history_item, entry.value, entry.threshold),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = DateUtils.getRelativeTimeSpanString(entry.timestamp).toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun AlertMetricRow(
    label: String,
    metric: String,
    snapshot: AlertsSnapshot,
    onEdit: () -> Unit,
) {
    val current = snapshot.metrics?.valueFor(metric)
    val threshold = snapshot.thresholds?.valueFor(metric)
    val alerting = snapshot.alerts[metric] == true

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (alerting) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(8.dp),
                    ) {
                    }
                    Text(
                        text = stringResource(R.string.alert_active),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(
                text = if (current != null && threshold != null) {
                    stringResource(R.string.alert_current, current, threshold)
                } else if (current != null) {
                    stringResource(R.string.alert_current_no_threshold, current)
                } else {
                    stringResource(R.string.server_mgmt_unknown)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.alert_edit),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ThresholdEditDialog(
    title: String,
    initialValue: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var text by rememberSaveable(initialValue) { mutableStateOf(initialValue.toString()) }
    var invalid by rememberSaveable { mutableStateOf(false) }

    AppDialog(onDismissRequest = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                invalid = false
            },
            label = { Text(stringResource(R.string.alert_threshold_value)) },
            placeholder = { Text("0-100") },
            isError = invalid,
            supportingText = if (invalid) {
                { Text(stringResource(R.string.alert_threshold_invalid)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            AppSecondaryButton(
                onClick = onDismiss,
                outlined = true,
            ) {
                Text(stringResource(R.string.cancel))
            }
            AppPrimaryButton(
                onClick = {
                    val value = text.toDoubleOrNull()
                    if (value != null && value in 0.0..100.0) {
                        onConfirm(value)
                    } else {
                        invalid = true
                    }
                },
            ) {
                Text(stringResource(R.string.server_mgmt_save))
            }
        }
    }
}
