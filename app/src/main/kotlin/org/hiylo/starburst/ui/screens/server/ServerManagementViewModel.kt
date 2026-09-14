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
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.hiylo.starburst.data.api.OpenCodeApi
import org.hiylo.starburst.data.api.ServerConfigPatch
import org.hiylo.starburst.data.api.ServerConfigResponse
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.api.getConfig
import org.hiylo.starburst.data.api.listSessionStatuses
import org.hiylo.starburst.data.api.updateConfig
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.data.shell.ServerShellRegistry
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.domain.model.SessionStatus
import org.hiylo.starburst.service.StarBurstConnectionService
import org.hiylo.starburst.service.ServerConnectionMetrics
import org.hiylo.starburst.service.ServerConnectionStatus
import org.hiylo.starburst.service.SshRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
)

/** 单个服务器连接的实时健康状态。 */
data class ServerConnectionHealthUi(
    val status: ServerConnectionStatus = ServerConnectionStatus.DISCONNECTED,
    val latencyMs: Long? = null,
    val lastHeartbeatAt: Long? = null,
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
    private val shellRegistry: ServerShellRegistry,
    private val serverRepository: ServerRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val serverUrl: String = savedStateHandle.get<String>("serverUrl").orEmpty()
    private val username: String = savedStateHandle.get<String>("username").orEmpty()
    private val password: String = savedStateHandle.get<String>("password").orEmpty()
    private val serverId: String = savedStateHandle.get<String>("serverId").orEmpty()
    private val serverName: String = savedStateHandle.get<String>("serverName").orEmpty()
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
        // 定时刷新系统资源信息（内存/磁盘/负载/opencode 进程占用），便于观察卡死时的瞬时占用。
        viewModelScope.launch {
            while (isActive) {
                delay(SYSTEM_INFO_REFRESH_INTERVAL_MS)
                loadSystemInfo()
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
            _uiState.update { it.copy(isLoading = false) }
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
                "ps -eo pcpu,pmem,rss,args | grep -i opencode | grep -v grep | head -1",
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
        const val SYSTEM_INFO_REFRESH_INTERVAL_MS = 5_000L
    }
}
