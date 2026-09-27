/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : HomeViewModel.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.home

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import org.hiylo.starburst.logging.AppLogger as Log
import androidx.annotation.StringRes
import org.hiylo.starburst.BuildConfig
import org.hiylo.starburst.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.hiylo.starburst.data.api.StarBurstApi
import org.hiylo.starburst.data.api.ServerAuthenticationException
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.api.ServerHealthHttpException
import org.hiylo.starburst.data.api.BackendApi
import org.hiylo.starburst.data.api.getProviders
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.data.repository.ServerConnectionStateRepository
import org.hiylo.starburst.data.repository.normalizeServerUrl
import org.hiylo.starburst.data.repository.SettingsRepository
import org.hiylo.starburst.data.repository.DiagnosticLogRepository
import org.hiylo.starburst.data.repository.favoriteSessionIds
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.service.StarBurstConnectionService
import org.hiylo.starburst.service.SshRunner
import android.widget.Toast
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "HomeViewModel"
/** 首页未读角标的轮询间隔（毫秒）。 */
private const val UNREAD_POLL_INTERVAL_MS = 12_000L

private const val RESTART_SERVER_COMMAND = "systemctl restart opencode"

data class HomeUiState(
    val servers: List<ServerConfig> = emptyList(),
    val connectedServerIds: Set<String> = emptySet(),
    val serverSettingsReadyIds: Set<String> = emptySet(),
    val connectingServerIds: Set<String> = emptySet(),
    val connectionErrors: Map<String, String> = emptyMap(),
    val restartingServerIds: Set<String> = emptySet(),
    val showAddServerDialog: Boolean = false,
    val editingServer: ServerConfig? = null,
    val isLoading: Boolean = true,
    val hasFavoriteSessions: Boolean? = null,
    /** 各已连接服务器（配置了后端时）的未读会话数，用于首页入口角标。 */
    val unreadCounts: Map<String, Int> = emptyMap(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val serverRepository: ServerRepository,
    private val api: StarBurstApi,
    private val backendApi: BackendApi,
    private val settingsRepository: SettingsRepository,
    private val diagnosticLogRepository: DiagnosticLogRepository,
    private val connectionStateRepository: ServerConnectionStateRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var serviceBinder: StarBurstConnectionService.LocalBinder? = null
    private var sseObserverJob: Job? = null
    private val serverSettingsCheckJobs = mutableMapOf<String, Job>()
    private val connectionAttemptJobs = mutableMapOf<String, Job>()
    private val connectionAttemptGenerations = mutableMapOf<String, Int>()
    private var unreadPollJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            serviceBinder = service as? StarBurstConnectionService.LocalBinder
            restoreConnectionStateFromService()
            observeServiceConnectionState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBinder = null
            sseObserverJob?.cancel()
            sseObserverJob = null
            serverSettingsCheckJobs.values.forEach { it.cancel() }
            serverSettingsCheckJobs.clear()
            _uiState.update {
                it.copy(
                    connectedServerIds = emptySet(),
                    serverSettingsReadyIds = emptySet(),
                )
            }
        }
    }

    init {
        loadServers()
        bindToService()
        observeSettings()
        observeFavoriteSessions()
    }

    private fun observeSettings() {
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeFavoriteSessions() {
        viewModelScope.launch {
            serverRepository.servers.flatMapLatest { servers ->
                if (servers.isEmpty()) {
                    flowOf(false)
                } else {
                    combine(servers.map { settingsRepository.favoriteSessionIds(it.id) }) { favoritesByServer ->
                        favoritesByServer.any(List<String>::isNotEmpty)
                    }
                }
            }.collect { hasFavorites ->
                _uiState.update { it.copy(hasFavoriteSessions = hasFavorites) }
            }
        }
    }

    /**
     * Restore connected state from the already-running service.
     */
    private fun restoreConnectionStateFromService() {
        val service = serviceBinder?.getService() ?: return
        val ids = service.connectedServerIds.value
        if (ids.isNotEmpty()) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Restoring connected state from service: serverIds=$ids")
            _uiState.update { it.copy(connectedServerIds = ids) }
        }
    }

    /**
     * Observe connectedServerIds and connectingServerIds from the service.
     */
    private fun observeServiceConnectionState() {
        sseObserverJob?.cancel()
        val service = serviceBinder?.getService() ?: return
        sseObserverJob = viewModelScope.launch {
            launch {
                service.connectedServerIds.collect { ids ->
                    if (BuildConfig.DEBUG) Log.d(TAG, "Service connected server IDs changed: $ids")
                    _uiState.update {
                        it.copy(
                            connectedServerIds = ids,
                            serverSettingsReadyIds = it.serverSettingsReadyIds.intersect(ids)
                        )
                    }
                    refreshServerSettingsAvailability(ids)
                    startUnreadPolling(ids)
                }
            }
            launch {
                service.connectingServerIds.collect { ids ->
                    if (BuildConfig.DEBUG) Log.d(TAG, "Service connecting server IDs changed: $ids")
                    _uiState.update { it.copy(connectingServerIds = ids) }
                }
            }
            launch {
                service.connectionErrors.collect { errs ->
                    _uiState.update { it.copy(connectionErrors = errs) }
                }
            }
        }
    }

    private fun loadServers() {
        viewModelScope.launch {
            serverRepository.getAllServers().collect { servers ->
                _uiState.update { 
                    it.copy(
                        servers = servers,
                        isLoading = false
                    )
                }
                refreshServerSettingsAvailability(_uiState.value.connectedServerIds)
            }
        }
    }

    /**
     * 首页服务器入口未读角标：对每个已连接且配置了后端的服务器轮询 `GET /api/unread`，
     * 得到该服务器的未读会话总数。未连接/无后端时清零。
     */
    private fun startUnreadPolling(serverIds: Set<String>) {
        unreadPollJob?.cancel()
        if (serverIds.isEmpty()) {
            _uiState.update { it.copy(unreadCounts = emptyMap()) }
            return
        }
        unreadPollJob = viewModelScope.launch {
            while (true) {
                val serversById = _uiState.value.servers.associateBy { it.id }
                val counts = buildMap {
                    serverIds.forEach { id ->
                        val server = serversById[id] ?: return@forEach
                        val url = server.backendResolvedUrl.takeIf { it.isNotBlank() } ?: return@forEach
                        val token = server.backendResolvedToken.takeIf { it.isNotBlank() } ?: return@forEach
                        val n = runCatching { backendApi.listUnread(url, token).size }.getOrDefault(0)
                        put(id, n)
                    }
                }
                _uiState.update { it.copy(unreadCounts = counts) }
                delay(UNREAD_POLL_INTERVAL_MS)
            }
        }
    }

    private fun refreshServerSettingsAvailability(connectedIds: Set<String>) {
        // Cancel checks for disconnected servers
        val disconnected = serverSettingsCheckJobs.keys - connectedIds
        disconnected.forEach { id ->
            serverSettingsCheckJobs.remove(id)?.cancel()
        }

        // Start or restart checks for connected servers
        connectedIds.forEach { serverId ->
            serverSettingsCheckJobs.remove(serverId)?.cancel()
            serverSettingsCheckJobs[serverId] = viewModelScope.launch {
                val server = _uiState.value.servers.find { it.id == serverId }
                if (server == null) {
                    _uiState.update { it.copy(serverSettingsReadyIds = it.serverSettingsReadyIds - serverId) }
                    return@launch
                }

                try {
                    val conn = connectionStateRepository.resolvedConnectionFor(server.id, server.url)
                        ?: ServerConnection.from(server.url, server.username, server.password)
                    api.getProviders(conn)
                    _uiState.update {
                        it.copy(
                            serverSettingsReadyIds = resolveServerSettingsReadyIds(
                                readyIds = it.serverSettingsReadyIds,
                                connectedIds = it.connectedServerIds,
                                serverId = serverId,
                                probeSucceeded = true,
                            )
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            serverSettingsReadyIds = resolveServerSettingsReadyIds(
                                readyIds = it.serverSettingsReadyIds,
                                connectedIds = it.connectedServerIds,
                                serverId = serverId,
                                probeSucceeded = false,
                            )
                        )
                    }
                    if (BuildConfig.DEBUG) Log.d(TAG, "Providers check failed for $serverId: ${e.message}")
                }
            }
        }
    }

    private fun bindToService() {
        val intent = Intent(getApplication(), StarBurstConnectionService::class.java)
        getApplication<Application>().bindService(
            intent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    fun showAddServerDialog() {
        _uiState.update { it.copy(showAddServerDialog = true, editingServer = null) }
    }

    fun showEditServerDialog(server: ServerConfig) {
        _uiState.update { it.copy(showAddServerDialog = true, editingServer = server) }
    }

    fun hideServerDialog() {
        _uiState.update { it.copy(showAddServerDialog = false, editingServer = null) }
    }

    fun saveServer(
        name: String,
        url: String,
        username: String,
        password: String,
        autoConnect: Boolean,
        sshPort: Int,
        sshUsername: String,
        sshPassword: String?,
        backendToken: String?,
    ) {
        viewModelScope.launch {
            val editingServer = _uiState.value.editingServer
            
            if (editingServer != null) {
                val updatedServer = editingServer.copy(
                    name = name,
                    url = url,
                    username = username,
                    password = password,
                    autoConnect = autoConnect,
                    sshPort = sshPort,
                    sshUsername = sshUsername,
                    sshPassword = sshPassword,
                    backendToken = backendToken,
                )
                serverRepository.updateServer(updatedServer)
            } else {
                serverRepository.addServer(
                    url = url,
                    username = username,
                    password = password,
                    name = name,
                    autoConnect = autoConnect,
                    sshPort = sshPort,
                    sshUsername = sshUsername,
                    sshPassword = sshPassword,
                    backendToken = backendToken,
                )
            }
            
            hideServerDialog()
        }
    }

    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            // Disconnect first if connected or connecting
            if (_uiState.value.connectedServerIds.contains(serverId) ||
                _uiState.value.connectingServerIds.contains(serverId)) {
                disconnectFromServer(serverId)
            }
            serverRepository.deleteServer(serverId)
        }
    }

    /**
     * Connect to a specific server. Multiple servers can be connected simultaneously.
     */
    fun connectToServer(serverId: String) {
        val server = _uiState.value.servers.find { it.id == serverId } ?: return

        // Already connected or connecting? No-op.
        val equivalentServerIds = _uiState.value.servers
            .filter { normalizeServerUrl(it.url) == normalizeServerUrl(server.url) }
            .mapTo(mutableSetOf()) { it.id }
        if (_uiState.value.connectedServerIds.any(equivalentServerIds::contains) ||
            _uiState.value.connectingServerIds.any(equivalentServerIds::contains)) return

        _uiState.update {
            it.copy(
                connectingServerIds = it.connectingServerIds + serverId,
                connectionErrors = it.connectionErrors - serverId
            )
        }

        connectionAttemptJobs.remove(serverId)?.cancel()
        val generation = (connectionAttemptGenerations[serverId] ?: 0) + 1
        connectionAttemptGenerations[serverId] = generation
        val job = viewModelScope.launch {
            try {
                val healthResult = serverRepository.checkHealth(server)
                if (connectionAttemptGenerations[serverId] != generation) return@launch
                if (healthResult.getOrNull()?.healthy != true) {
                    val error = healthResult.exceptionOrNull()
                    val message = when (error) {
                        is ServerAuthenticationException -> s(R.string.home_server_auth_failed)
                        is ServerHealthHttpException -> s(R.string.home_server_health_http_error, error.statusCode)
                        else -> s(R.string.home_server_not_responding)
                    }
                    _uiState.update {
                        it.copy(
                            connectingServerIds = it.connectingServerIds - serverId,
                            connectionErrors = it.connectionErrors + (serverId to message)
                        )
                    }
                    return@launch
                }
                if (serverId !in _uiState.value.connectingServerIds) return@launch

                val context = getApplication<Application>()
                val intent = Intent(context, StarBurstConnectionService::class.java).apply {
                    putExtra("server_id", server.id)
                    putExtra("server_name", server.name)
                    putExtra("server_url", server.url)
                    putExtra("server_username", server.username)
                    putExtra("server_ssh_port", server.sshPort)
                    putExtra("server_ssh_username", server.sshUsername)
                    server.backendToken?.let { putExtra("server_backend_token", it) }
                    server.backendUrl?.let { putExtra("server_backend_url", it) }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }

                // Connection state will be updated by the service via
                // observeServiceConnectionState() — no optimistic update needed.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (connectionAttemptGenerations[serverId] != generation) return@launch
                _uiState.update {
                    it.copy(
                        connectingServerIds = it.connectingServerIds - serverId,
                        connectionErrors = it.connectionErrors + (serverId to (e.message ?: "Connection failed"))
                    )
                }
            }
        }
        connectionAttemptJobs[serverId] = job
        job.invokeOnCompletion { connectionAttemptJobs.remove(serverId, job) }
    }


    private fun s(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)




    /**
     * Disconnect from a specific server.
     */
    fun disconnectFromServer(serverId: String) {
        connectionAttemptGenerations[serverId] = (connectionAttemptGenerations[serverId] ?: 0) + 1
        connectionAttemptJobs.remove(serverId)?.cancel()
        serviceBinder?.getService()?.disconnect(serverId)
        _uiState.update {
            it.copy(
                connectedServerIds = it.connectedServerIds - serverId,
                connectingServerIds = it.connectingServerIds - serverId,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        sseObserverJob?.cancel()
        serverSettingsCheckJobs.values.forEach { it.cancel() }
        serverSettingsCheckJobs.clear()
        connectionAttemptJobs.values.forEach { it.cancel() }
        connectionAttemptJobs.clear()
        connectionAttemptGenerations.clear()
        try {
            getApplication<Application>().unbindService(serviceConnection)
        } catch (e: Exception) {
            // Service might not be bound
        }
    }

    /**
     * 通过 SSH 远程重启 系统服务（用于 后端崩溃后仍能恢复服务）。
     *
     * 独立于 服务器的 HTTP 连接，仅依赖配置的 SSH 凭据。
     */
    fun restartServerViaSsh(serverId: String) {
        if (serverId in _uiState.value.restartingServerIds) return
        viewModelScope.launch {
            val server = serverRepository.getServer(serverId) ?: return@launch
            _uiState.update { it.copy(restartingServerIds = it.restartingServerIds + serverId) }
            try {
                SshRunner.runCommand(server, RESTART_SERVER_COMMAND)
                _uiState.update {
                    it.copy(
                        restartingServerIds = it.restartingServerIds - serverId,
                        connectionErrors = it.connectionErrors - serverId,
                    )
                }
                Toast.makeText(
                    getApplication(),
                    getApplication<Application>().getString(R.string.home_ssh_restart_success),
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        restartingServerIds = it.restartingServerIds - serverId,
                        connectionErrors = it.connectionErrors + (serverId to (e.message ?: "SSH restart failed")),
                    )
                }
                Toast.makeText(
                    getApplication(),
                    getApplication<Application>().getString(R.string.home_ssh_restart_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
