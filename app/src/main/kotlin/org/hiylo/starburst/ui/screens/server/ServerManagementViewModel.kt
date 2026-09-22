/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ServerManagementViewModel.kt
 * Date : 2026/09/08 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.server

import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.ui.util.launchWhileStarted
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.Lifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.hiylo.starburst.data.api.OpenCodeApi
import org.hiylo.starburst.data.api.ServerConfigPatch
import org.hiylo.starburst.data.api.ServerConfigResponse
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.api.BackendApi
import org.hiylo.starburst.data.api.AlertsSnapshot
import org.hiylo.starburst.data.api.AlertsUpdateRequest
import org.hiylo.starburst.data.api.getConfig
import org.hiylo.starburst.data.api.listSessionStatuses
import org.hiylo.starburst.data.api.updateConfig
import org.hiylo.starburst.data.repository.AlertHistoryEntry
import org.hiylo.starburst.data.repository.AlertHistoryRepository
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.data.shell.ServerShellRegistry
import org.hiylo.starburst.domain.model.SessionStatus
import org.hiylo.starburst.service.StarBurstConnectionService
import org.hiylo.starburst.service.ServerConnectionMetrics
import org.hiylo.starburst.service.ServerConnectionStatus
import org.hiylo.starburst.service.SshRunner
import org.hiylo.starburst.ui.gate.BackendGate
import org.hiylo.starburst.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ServerManagementViewModel"

/** 服务器管理页的 UI 状态。 */
data class ServerManagementUiState(
    val serverName: String = "",
    val version: String? = null,
    val activeSessions: Int? = null,
    val busySessions: Int? = null,
    val memory: String? = null,
    val disk: String? = null,
    val loadAverage: String? = null,
    val processCpu: String? = null,
    val processMemory: String? = null,
    val config: ServerConfigResponse = ServerConfigResponse(),
    val connectionHealth: ServerConnectionHealthUi? = null,
    val isLoading: Boolean = true,
    val isRestarting: Boolean = false,
    val isSavingConfig: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    /** 探测到的 starburst-backend 是否可用（GET /api/health）。null 表示探测中。 */
    val backendAvailable: Boolean? = null,
    /** 探测到的后端自身版本（GET /api/system 的 version 字段）。 */
    val backendVersion: String? = null,
    /** 后端版本是否低于 App 要求的最低版本（需要升级）。 */
    val backendNeedsUpgrade: Boolean = false,
)

/** 单个服务器连接的实时健康状态。 */
data class ServerConnectionHealthUi(
    val status: ServerConnectionStatus = ServerConnectionStatus.DISCONNECTED,
    val latencyMs: Long? = null,
    val lastHeartbeatAt: Long? = null,
)

/** 监控告警卡片状态。 */
data class AlertsUiState(
    val loading: Boolean = true,
    val snapshot: AlertsSnapshot? = null,
    val error: String? = null,
)

data class AlertHistoryUiState(
    val loading: Boolean = true,
    val entries: List<AlertHistoryEntry> = emptyList(),
    val error: String? = null,
)

/**
 * 服务器管理页 ViewModel。
 *
 * 展示服务基本信息（版本号、活跃会话数）、服务器资源（CPU/内存/磁盘，通过共享 PTY 执行
 * shell 命令获取），支持查看/修改服务配置（默认模型、默认 Agent），以及二次确认后重启服务。
 */
@HiltViewModel
class ServerManagementViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val backendApi: BackendApi,
    private val shellRegistry: ServerShellRegistry,
    private val serverRepository: ServerRepository,
    private val alertHistoryRepository: AlertHistoryRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val serverUrl: String = savedStateHandle.get<String>("serverUrl").orEmpty()
    val username: String = savedStateHandle.get<String>("username").orEmpty()
    val password: String = savedStateHandle.get<String>("password").orEmpty()
    val serverId: String = savedStateHandle.get<String>("serverId").orEmpty()
    val serverName: String = savedStateHandle.get<String>("serverName").orEmpty()
    private val directory: String = savedStateHandle.get<String>("directory").orEmpty()

    private val conn = ServerConnection.from(serverUrl, username, password.ifEmpty { null })

    /** 连接级共享 PTY 会话：与 Git 页按 server 复用同一条 PTY。 */
    private var shellAcquired = false
    private val shell by lazy {
        shellAcquired = true
        shellRegistry.acquire(serverId.ifBlank { conn.baseUrl }, api, conn, directory)
    }

    private val _uiState = MutableStateFlow(ServerManagementUiState(serverName = serverName, isLoading = true))
    val uiState: StateFlow<ServerManagementUiState> = _uiState.asStateFlow()

    private val _alertsUiState = MutableStateFlow(AlertsUiState())
    val alertsUiState: StateFlow<AlertsUiState> = _alertsUiState.asStateFlow()

    private val _alertHistoryUiState = MutableStateFlow(AlertHistoryUiState())
    val alertHistoryUiState: StateFlow<AlertHistoryUiState> = _alertHistoryUiState.asStateFlow()

    /** 后端是否「正常可用」（健康 + 版本达标）。后端相关功能入口的显隐统一使用该判定。 */
    val isBackendReady: Boolean
        get() = BackendGate.isReady(_uiState.value.backendAvailable, _uiState.value.backendVersion)

    private var serviceBinder: StarBurstConnectionService.LocalBinder? = null
    private var healthObserverJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serviceBinder = service as? StarBurstConnectionService.LocalBinder
            observeConnectionHealth()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBinder = null
            healthObserverJob?.cancel()
            healthObserverJob = null
            _uiState.update { it.copy(connectionHealth = null) }
        }
    }

    init {
        refresh()
        bindToService()
        loadAlertHistory()
    }

    /** 界面可见性驱动的轮询任务；退后台时由 [launchWhileStarted] 自动挂起（耗电优化）。 */
    private var pollJob: Job? = null

    /** 硬件告警快照兜底轮询任务；同样随界面可见性挂起/恢复。 */
    private var alertPollJob: Job? = null

    /** 由 ServerManagementScreen 传入 lifecycle；仅在界面 STARTED 期间刷新系统资源信息。 */
    fun attachLifecycle(lifecycle: Lifecycle) {
        if (pollJob?.isActive != true) {
            pollJob = viewModelScope.launchWhileStarted(lifecycle) {
                while (true) {
                    delay(SYSTEM_INFO_REFRESH_INTERVAL_MS)
                    loadSystemInfo()
                }
            }
        }
        if (alertPollJob?.isActive != true) {
            alertPollJob = viewModelScope.launchWhileStarted(lifecycle) {
                while (true) {
                    delay(ALERT_POLL_INTERVAL_MS)
                    reconcileAlerts()
                }
            }
        }
    }

    override fun onCleared() {
        healthObserverJob?.cancel()
        try {
            context.unbindService(serviceConnection)
        } catch (_: Exception) {
            // 服务可能未绑定
        }
        if (shellAcquired) {
            shellRegistry.release(serverId.ifBlank { conn.baseUrl })
        }
        super.onCleared()
    }

    private fun bindToService() {
        val intent = Intent(context, StarBurstConnectionService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /** 订阅服务的连接状态与实时指标，计算当前服务器的健康状态。 */
    private fun observeConnectionHealth() {
        val service = serviceBinder?.getService() ?: return
        healthObserverJob?.cancel()
        healthObserverJob = viewModelScope.launch {
            combine(
                service.connectedServerIds,
                service.connectingServerIds,
                service.connectionErrors,
                service.serverMetrics,
            ) { connected, connecting, errors, metrics ->
                resolveConnectionHealth(connected, connecting, errors, metrics)
            }.collect { health ->
                _uiState.update { it.copy(connectionHealth = health) }
            }
        }
    }

    private fun resolveConnectionHealth(
        connected: Set<String>,
        connecting: Set<String>,
        errors: Map<String, String>,
        metrics: Map<String, ServerConnectionMetrics>,
    ): ServerConnectionHealthUi {
        if (serverId.isBlank()) return ServerConnectionHealthUi()
        val status = when {
            errors.containsKey(serverId) -> ServerConnectionStatus.FAILED
            serverId in connected -> ServerConnectionStatus.CONNECTED
            serverId in connecting -> ServerConnectionStatus.RECONNECTING
            else -> ServerConnectionStatus.DISCONNECTED
        }
        val metric = metrics[serverId]
        return ServerConnectionHealthUi(status, metric?.latencyMs, metric?.lastHeartbeatAt)
    }

    /** 重新加载服务信息、活跃会话数与服务器资源。 */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            loadServiceInfo()
            loadSystemInfo()
            loadConfig()
            probeBackend()
            loadAlerts()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /** 拉取硬件告警快照（后端可用时）。 */
    fun loadAlerts() {
        viewModelScope.launch {
            _alertsUiState.update { it.copy(loading = true, error = null) }
            val server = serverRepository.getServer(serverId)
            val url = server?.backendResolvedUrl.orEmpty()
            val token = server?.backendResolvedToken.orEmpty()
            if (url.isBlank() || token.isBlank()) {
                _alertsUiState.update { it.copy(loading = false, snapshot = null) }
                return@launch
            }
            try {
                val snapshot = backendApi.alertsSnapshot(url, token)
                _alertsUiState.update { it.copy(loading = false, snapshot = snapshot) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _alertsUiState.update {
                    it.copy(loading = false, error = context.getString(R.string.alert_load_failed))
                }
            }
        }
    }

    /** 更新告警配置并回写最新快照。 */
    fun updateAlerts(request: AlertsUpdateRequest) {
        viewModelScope.launch {
            val server = serverRepository.getServer(serverId)
            val url = server?.backendResolvedUrl.orEmpty()
            val token = server?.backendResolvedToken.orEmpty()
            if (url.isBlank() || token.isBlank()) return@launch
            try {
                val snapshot = backendApi.updateAlerts(url, token, request)
                _alertsUiState.update { it.copy(snapshot = snapshot, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _alertsUiState.update {
                    it.copy(error = context.getString(R.string.alert_update_failed))
                }
            }
        }
    }

    /** 拉取本地告警历史（越线/恢复事件，时间倒序）。 */
    fun loadAlertHistory() {
        viewModelScope.launch {
            _alertHistoryUiState.update { it.copy(loading = true, error = null) }
            try {
                val entries = alertHistoryRepository.latest(serverId)
                _alertHistoryUiState.update { it.copy(loading = false, entries = entries) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _alertHistoryUiState.update {
                    it.copy(loading = false, error = context.getString(R.string.alert_history_load_failed))
                }
            }
        }
    }

    /** 拉取告警快照并对齐本地历史：仅对「状态迁移」（进入告警 / 恢复）落库，稳态越线不重复记录。 */
    private fun reconcileAlerts() {
        viewModelScope.launch {
            val server = serverRepository.getServer(serverId)
            val url = server?.backendResolvedUrl.orEmpty()
            val token = server?.backendResolvedToken.orEmpty()
            if (url.isBlank() || token.isBlank()) return@launch
            val snapshot = try {
                backendApi.alertsSnapshot(url, token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
            val previous = _alertsUiState.value.snapshot?.alerts.orEmpty()
            val current = snapshot.alerts
            if (current != previous) {
                val recent = alertHistoryRepository.latest(serverId, limit = 200)
                val now = System.currentTimeMillis()
                (current.keys + previous.keys).forEach { metric ->
                    val wasAlert = previous[metric] == true
                    val isAlert = current[metric] == true
                    if (wasAlert != isAlert) {
                        val state = if (isAlert) "alert" else "ok"
                        // 去重：时间窗内同 metric + 同 state 已有记录（推送路径已落库）则跳过，避免双路重复。
                        val duplicate = recent.any {
                            it.metric == metric && it.state == state && now - it.timestamp < ALERT_DEDUP_WINDOW_MS
                        }
                        if (!duplicate) {
                            alertHistoryRepository.record(
                                serverId = serverId,
                                metric = metric,
                                value = metricValue(snapshot, metric),
                                threshold = metricThreshold(snapshot, metric),
                                state = state,
                            )
                        }
                    }
                }
            }
            _alertsUiState.update { it.copy(snapshot = snapshot) }
        }
    }

    private fun metricValue(snapshot: AlertsSnapshot, metric: String): Double = when (metric) {
        "cpu" -> snapshot.metrics?.cpuPct ?: 0.0
        "mem" -> snapshot.metrics?.memPct ?: 0.0
        else -> snapshot.metrics?.diskPct ?: 0.0
    }

    private fun metricThreshold(snapshot: AlertsSnapshot, metric: String): Double = when (metric) {
        "cpu" -> snapshot.thresholds?.cpuPct ?: 0.0
        "mem" -> snapshot.thresholds?.memPct ?: 0.0
        else -> snapshot.thresholds?.diskPct ?: 0.0
    }

    /** 探测当前服务器对应的 starburst-backend 是否已部署并存活（health + 版本）。 */
    fun probeBackend() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(backendAvailable = null, backendVersion = null, backendNeedsUpgrade = false)
            }
            val server = serverRepository.getServer(serverId)
            val probe = BackendGate.probe(backendApi, server, serverUrl)
            _uiState.update {
                it.copy(
                    backendAvailable = probe.available,
                    backendVersion = probe.version,
                    backendNeedsUpgrade = probe.needsUpgrade,
                )
            }
        }
    }

    /** 加载服务版本号与活跃会话数。 */
    private suspend fun loadServiceInfo() {
        runCatching { api.getHealth(conn) }
            .onSuccess { health ->
                _uiState.update { it.copy(version = health.version) }
            }
            .onFailure { e -> Log.w(TAG, "Failed to load health", e) }

        runCatching { api.listSessionStatuses(conn, directory.takeIf { it.isNotBlank() }) }
            .onSuccess { statuses ->
                val busy = statuses.values.count { it !is SessionStatus.Idle }
                _uiState.update { it.copy(activeSessions = statuses.size, busySessions = busy) }
            }
            .onFailure { e -> Log.w(TAG, "Failed to load session statuses", e) }
    }

    /** 通过共享 PTY 执行 shell 命令获取内存、磁盘与负载信息。 */
    private suspend fun loadSystemInfo() {
        runCatching { shell.runCommand("free -m", timeoutMs = 20_000) }
            .onSuccess { output ->
                _uiState.update { it.copy(memory = parseMemory(output)) }
            }
            .onFailure { e -> Log.w(TAG, "Failed to read memory", e) }

        runCatching { shell.runCommand("df -h /", timeoutMs = 20_000) }
            .onSuccess { output ->
                _uiState.update { it.copy(disk = parseDisk(output)) }
            }
            .onFailure { e -> Log.w(TAG, "Failed to read disk", e) }

        runCatching { shell.runCommand("cat /proc/loadavg", timeoutMs = 20_000) }
            .onSuccess { output ->
                _uiState.update { it.copy(loadAverage = parseLoadAverage(output)) }
            }
            .onFailure { e -> Log.w(TAG, "Failed to read load average", e) }

        runCatching {
            shell.runCommand(
                "ps -eo pcpu,pmem,rss,args | grep -i -- 'opencode' | grep -v grep | head -1",
                timeoutMs = 20_000,
            )
        }
            .onSuccess { output ->
                _uiState.update {
                    it.copy(
                        processCpu = parseProcessCpu(output),
                        processMemory = parseProcessMemory(output),
                    )
                }
            }
            .onFailure { e -> Log.w(TAG, "Failed to read opencode process", e) }
    }

    /** 加载服务配置（GET /config）。 */
    private suspend fun loadConfig() {
        runCatching { api.getConfig(conn) }
            .onSuccess { config -> _uiState.update { it.copy(config = config) } }
            .onFailure { e -> Log.w(TAG, "Failed to load config", e) }
    }

    /** 修改默认模型并 PATCH /config。 */
    fun updateDefaultModel(model: String?) {
        updateConfigPatch(ServerConfigPatch(model = model?.trim()?.ifBlank { null }))
    }

    /** 修改默认 Agent 并 PATCH /config。 */
    fun updateDefaultAgent(agent: String?) {
        updateConfigPatch(ServerConfigPatch(defaultAgent = agent?.trim()?.ifBlank { null }))
    }

    private fun updateConfigPatch(patch: ServerConfigPatch) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingConfig = true, message = null, error = null) }
            val before = _uiState.value.config
            try {
                api.updateConfig(conn, patch)
                _uiState.update { it.copy(config = api.getConfig(conn)) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update config", e)
                _uiState.update { it.copy(config = before, error = e.message) }
            } finally {
                _uiState.update { it.copy(isSavingConfig = false) }
            }
        }
    }

    /** 重启 opencode 服务：配置了 SSH 时走 SSH，否则走共享 PTY。 */
    fun restartServer() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRestarting = true, error = null, message = null) }
            try {
                val server = serverRepository.getServer(serverId)
                val output = if (server?.useSsh == true) {
                    SshRunner.runCommand(server, RESTART_COMMAND)
                } else {
                    shell.runCommandResult(RESTART_COMMAND, timeoutMs = 30_000).output
                }
                _uiState.update {
                    it.copy(
                        isRestarting = false,
                        message = output.ifBlank { null },
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart server", e)
                _uiState.update { it.copy(isRestarting = false, error = e.message) }
            }
        }
    }

    /** 清除一次性提示信息。 */
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** 解析 `free -m` 输出中的内存总量/已用（单位 MB）。 */
    private fun parseMemory(output: String): String? {
        val line = output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("Mem:") } ?: return null
        val fields = line.split(Regex("\\s+"))
        if (fields.size < 3) return null
        val total = fields[1].toIntOrNull() ?: return null
        val used = fields[2].toIntOrNull() ?: return null
        return "${used}MB / ${total}MB"
    }

    /** 解析 `df -h /` 输出中根分区的已用/总量与使用率。 */
    private fun parseDisk(output: String): String? {
        val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val line = lines.firstOrNull { it.contains(" /") && !it.startsWith("Filesystem") } ?: return null
        val fields = line.split(Regex("\\s+"))
        if (fields.size < 5) return null
        val size = fields[1]
        val used = fields[2]
        val use = fields[4]
        return "$used / $size ($use)"
    }

    /** 解析 `/proc/loadavg` 输出的 1/5/15 分钟负载。 */
    private fun parseLoadAverage(output: String): String? {
        val fields = output.trim().split(Regex("\\s+"))
        if (fields.size < 3) return null
        return "${fields[0]} ${fields[1]} ${fields[2]}"
    }

    /** 解析 `ps` 输出中 opencode 进程的 CPU 占用百分比。 */
    private fun parseProcessCpu(output: String): String? {
        val fields = output.trim().split(Regex("\\s+"))
        if (fields.size < 2) return null
        return "${fields[0]}%"
    }

    /** 解析 `ps` 输出中 opencode 进程的常驻内存（RSS，单位自适应 KB/MB/GB）。 */
    private fun parseProcessMemory(output: String): String? {
        val fields = output.trim().split(Regex("\\s+"))
        if (fields.size < 3) return null
        val rssKb = fields[2].toLongOrNull() ?: return null
        return when {
            rssKb >= 1024 * 1024 -> String.format("%.2f GB", rssKb / 1024.0 / 1024.0)
            rssKb >= 1024 -> String.format("%.1f MB", rssKb / 1024.0)
            else -> "$rssKb KB"
        }
    }

    private companion object {
        /** 服务重启命令；可按部署方式调整（如 `sudo systemctl restart opencode`）。 */
        const val RESTART_COMMAND: String = "systemctl restart opencode"

        /** 系统资源信息自动刷新间隔（毫秒）。 */
        // 每条 SSH 命令超时 20s、每轮 3~4 条；VPN 高 RTT 下 5s 一轮容易堆积重叠，放宽到 10s。
        const val SYSTEM_INFO_REFRESH_INTERVAL_MS = 10_000L

        /** 硬件告警快照兜底轮询间隔（毫秒）；与后端采样间隔（60s）对齐，补偿 WS 丢帧。 */
        const val ALERT_POLL_INTERVAL_MS = 60_000L

        /** 告警去重时间窗（毫秒）：窗内同 metric + 同 state 已有记录则跳过落库。 */
        const val ALERT_DEDUP_WINDOW_MS = 5L * 60L * 1000L
    }
}
