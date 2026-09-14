/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ServerSettingsViewModel.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.server

import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.BuildConfig
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.AgentInfo
import org.hiylo.starburst.data.api.OpenCodeApi
import org.hiylo.starburst.data.api.ProviderAuthMethod
import org.hiylo.starburst.data.api.ProviderInfo
import org.hiylo.starburst.data.api.ProviderModel
import org.hiylo.starburst.data.api.ProviderOauthAuthorization
import org.hiylo.starburst.data.api.ProviderAuthException
import org.hiylo.starburst.data.api.ProviderConfigDefinition
import org.hiylo.starburst.data.api.ProviderModelDefinition
import org.hiylo.starburst.data.api.ServerConfigPatch
import org.hiylo.starburst.data.api.ServerConfigResponse
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.api.BackendApi
import org.hiylo.starburst.data.api.authorizeProviderOauth
import org.hiylo.starburst.data.api.completeProviderOauth
import org.hiylo.starburst.data.api.disposeGlobal
import org.hiylo.starburst.data.api.getConfig
import org.hiylo.starburst.data.api.getGlobalConfig
import org.hiylo.starburst.data.api.getProviderAuthMethods
import org.hiylo.starburst.data.api.getProviders
import org.hiylo.starburst.data.api.listAgents
import org.hiylo.starburst.data.api.listProviderCatalog
import org.hiylo.starburst.data.api.removeProviderAuth
import org.hiylo.starburst.data.api.setProviderApiKey
import org.hiylo.starburst.data.api.updateGlobalConfig
import org.hiylo.starburst.data.api.updateProviderConfig
import org.hiylo.starburst.data.repository.SettingsRepository
import org.hiylo.starburst.data.repository.DiagnosticLogRepository
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.service.SshRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject

private const val TAG = "ServerSettingsViewModel"

data class ServerSettingsUiState(
    val serverName: String = "",
    val providers: List<ProviderToggle> = emptyList(),
    val modelOptions: List<ModelOption> = emptyList(),
    val agentOptions: List<String> = emptyList(),
    val selectedModel: String? = null,
    val selectedSmallModel: String? = null,
    val selectedDefaultAgent: String? = null,
    val groups: List<ModelGroup> = emptyList(),
    val authMethods: Map<String, List<ProviderAuthMethod>> = emptyMap(),
    val pendingOauth: PendingOauth? = null,
    val isSaving: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val oauthProxyHint: Boolean = false,
    val customProviders: List<ProviderConfigEntry> = emptyList(),
    /** 探测到的 starburst-backend 是否可用（GET /api/health）。null 表示探测中。 */
    val backendAvailable: Boolean? = null,
    /** 探测到的后端自身版本（GET /api/system 的 version 字段）。 */
    val backendVersion: String? = null,
    /** 后端版本是否低于 App 要求的最低版本（需要升级）。 */
    val backendNeedsUpgrade: Boolean = false,
    /** 是否正在通过 SSH 安装 starburst-backend。 */
    val isInstallingBackend: Boolean = false,
    /** 最近一次安装输出/错误。 */
    val backendInstallLog: String? = null,
)

data class PendingOauth(
    val providerId: String,
    val providerName: String,
    val methodIndex: Int,
    val authorization: ProviderOauthAuthorization,
    val fallbackFromHeadless: Boolean = false,
)

data class ProviderToggle(
    val providerId: String,
    val providerName: String,
    val source: String? = null,
    val connected: Boolean = false,
    val hasPaidModels: Boolean = false,
    val enabled: Boolean
)

data class ModelOption(
    val key: String,
    val label: String
)

data class ModelGroup(
    val providerId: String,
    val providerName: String,
    val models: List<ModelToggle>
)

data class ModelToggle(
    val modelId: String,
    val modelName: String,
    val visible: Boolean
)

/**
 * 自定义服务商在界面上的展示条目（由 /config 的 provider 映射派生）。
 *
 * @author Hsi Chu
 * @since 1.0
 */
data class ProviderConfigEntry(
    val providerId: String,
    val name: String,
    val npm: String? = null,
    val baseUrl: String = "",
    val models: Map<String, String> = emptyMap(),
)

@HiltViewModel
class ServerSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val backendApi: BackendApi,
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
    private val diagnosticLogRepository: DiagnosticLogRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val serverUrl: String = savedStateHandle.get<String>("serverUrl").orEmpty()
    private val username: String = savedStateHandle.get<String>("username").orEmpty()
    private val password: String = savedStateHandle.get<String>("password").orEmpty()
    private val serverId: String = savedStateHandle.get<String>("serverId").orEmpty()
    private val serverName: String = savedStateHandle.get<String>("serverName").orEmpty()

    private val conn = ServerConnection.from(serverUrl, username, password.ifEmpty { null })

    private val _allProviders = MutableStateFlow<List<ProviderInfo>>(emptyList())
    private val _providerCatalog = MutableStateFlow<List<ProviderInfo>>(emptyList())
    private val _providerConnected = MutableStateFlow<Set<String>>(emptySet())
    private val _agents = MutableStateFlow<List<AgentInfo>>(emptyList())
    private val _config = MutableStateFlow(ServerConfigResponse())
    private val _providerConfig = MutableStateFlow<Map<String, ProviderConfigDefinition>>(emptyMap())
    private val _authMethods = MutableStateFlow<Map<String, List<ProviderAuthMethod>>>(emptyMap())
    private val _hiddenModels = MutableStateFlow<Set<String>>(emptySet())
    private val _uiState = MutableStateFlow(ServerSettingsUiState(serverName = serverName, isLoading = true))
    val uiState: StateFlow<ServerSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.hiddenModels(serverId).collect { hidden ->
                _hiddenModels.value = hidden
                rebuildUi()
            }
        }
        loadProviders()
        loadConfig()
        loadProviderConfig()
        loadAgents()
        loadAuthMethods()
        probeBackend()
    }

    /**
     * 探测当前服务器对应的 starburst-backend 是否已部署并存活。
     * 结果写入 uiState.backendAvailable：null=探测中，true=可用，false=不可用。
     */
    fun probeBackend() {
        viewModelScope.launch {
            _uiState.update { it.copy(backendAvailable = null, backendVersion = null, backendNeedsUpgrade = false) }
            _serverConfig = serverRepository.getServer(serverId)
            val server = _serverConfig
            val backendUrl = server?.backendResolvedUrl
                ?: "http://${hostFrom(serverUrl)}:18880"
            val token = server?.backendResolvedToken ?: "ocb_default"
            val available = backendApi.isHealthy(backendUrl)
            // 后端可用时顺带读 /api/system 拿自身版本，用于判断是否需要升级。
            val system = if (available) backendApi.getSystemInfo(backendUrl, token) else null
            val version = system?.version.orEmpty()
            val needsUpgrade = version.isNotBlank() && compareVersions(version, REQUIRED_BACKEND_VERSION) < 0
            _uiState.update {
                it.copy(
                    backendAvailable = available,
                    backendVersion = version.ifBlank { null },
                    backendNeedsUpgrade = needsUpgrade,
                )
            }
        }
    }

    /**
     * 通过 SSH 在远端一键安装 starburst-backend（install.sh）。
     * 需要服务器已配置 SSH（[ServerConfig.useSsh]），否则报错提示。
     */
    fun installBackend() {
        if (_uiState.value.isInstallingBackend) return
        viewModelScope.launch {
            val server = _serverConfig ?: serverRepository.getServer(serverId)
            if (server == null) {
                _uiState.update { it.copy(backendInstallLog = "服务器信息缺失，无法安装") }
                return@launch
            }
            if (!server.useSsh) {
                _uiState.update {
                    it.copy(backendInstallLog = "未配置 SSH，无法远程安装。请先在服务器配置里填写 SSH 账号。")
                }
                return@launch
            }
            _uiState.update { it.copy(isInstallingBackend = true, backendInstallLog = null, error = null) }
            try {
                val scriptUrl = "https://raw.githubusercontent.com/hiylo/starburst-backend/main/scripts/install.sh"
                // 远端以 sudo 执行安装脚本（install.sh 内部需要 root 写 /usr/local/bin、systemd）。
                // sudo 需可免密（或 SSH 用户本身是 root），否则会返回提示后失败。
                val command = "curl -fsSL $scriptUrl | sudo bash -- --port 18880 --default-token ocb_default"
                val output = SshRunner.runCommand(server, command, timeoutMs = 300_000)
                // 安装完成后再实测 /api/health，避免把「命令成功但服务未起」误判为可用。
                val available = backendApi.isHealthy(
                    server.backendResolvedUrl ?: "http://${hostFrom(serverUrl)}:18880"
                )
                _uiState.update {
                    it.copy(
                        isInstallingBackend = false,
                        backendInstallLog = output,
                        backendAvailable = available,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install backend via SSH", e)
                _uiState.update {
                    it.copy(
                        isInstallingBackend = false,
                        backendInstallLog = e.message ?: "安装失败",
                    )
                }
            }
        }
    }

    /** 当前服务器的 ServerConfig（含 SSH/后端配置），供 UI 判断是否可一键安装。 */
    val serverConfig: ServerConfig?
        get() = _serverConfig

    private var _serverConfig: ServerConfig? = null

    /** 从 opencode 服务地址推导主机名（不含端口），用于默认后端地址。 */
    private fun hostFrom(rawUrl: String): String =
        runCatching { java.net.URL(rawUrl).host }.getOrNull()
            ?: rawUrl.substringAfter("://").substringBefore(":")

    fun loadProviders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = api.getProviders(conn)
                _allProviders.value = response.providers
                val catalog = api.listProviderCatalog(conn)
                if (BuildConfig.DEBUG) Log.d(TAG, "loadProviders: catalog.connected=${catalog.connected}")
                _providerCatalog.value = catalog.all
                _providerConnected.value = catalog.connected.toSet()
                _config.value = api.getGlobalConfig(conn)
                rebuildUi()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load providers", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load providers"
                    )
                }
            }
        }
    }

    private fun loadAuthMethods() {
        viewModelScope.launch {
            try {
                _authMethods.value = api.getProviderAuthMethods(conn)
                rebuildUi()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load auth methods", e)
            }
        }
    }

    private fun loadConfig() {
        viewModelScope.launch {
            try {
                _config.value = api.getGlobalConfig(conn)
                rebuildUi()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load config", e)
            }
        }
    }

    private fun loadProviderConfig() {
        viewModelScope.launch {
            try {
                _providerConfig.value = api.getConfig(conn).provider.orEmpty()
                rebuildUi()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load provider config", e)
            }
        }
    }

    private fun loadAgents() {
        viewModelScope.launch {
            try {
                _agents.value = api.listAgents(conn)
                rebuildUi()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load agents", e)
            }
        }
    }

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        viewModelScope.launch {
            val before = _config.value
            val current = before.disabledProviders.toSet()
            val next = if (enabled) current - providerId else current + providerId
            _config.value = before.copy(disabledProviders = next.toList().sorted())
            rebuildUi()
            try {
                api.updateGlobalConfig(conn, ServerConfigPatch(disabledProviders = next.toList().sorted()))
                _config.value = api.getGlobalConfig(conn)
                rebuildUi()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update provider state", e)
                _config.value = before
                _uiState.update { it.copy(error = e.message ?: "Failed to update provider") }
                rebuildUi()
            }
        }
    }

    fun connectProviderApi(providerId: String, apiKey: String) {
        if (apiKey.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val updated = api.setProviderApiKey(conn, providerId, apiKey.trim())
                if (!updated) {
                    _uiState.update { it.copy(isSaving = false, error = "Failed to connect provider") }
                    return@launch
                }
                // Ensure provider is enabled after successful connect
                val disabled = _config.value.disabledProviders.toSet() - providerId
                api.updateGlobalConfig(conn, ServerConfigPatch(disabledProviders = disabled.toList().sorted()))
                _config.value = api.getGlobalConfig(conn)
                loadProviders()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect provider via API key", e)
                _uiState.update { it.copy(error = e.message ?: "Failed to connect provider") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun startProviderOauth(providerId: String, methodIndex: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                var auth = api.authorizeProviderOauth(conn, providerId, methodIndex)

                if (auth == null) {
                    _uiState.update { it.copy(isSaving = false, error = "OAuth is not available for this provider") }
                    return@launch
                }
                val providerName = (_providerCatalog.value.find { it.id == providerId }?.name ?: providerId)
                _uiState.update {
                    it.copy(
                        pendingOauth = PendingOauth(
                            providerId = providerId,
                            providerName = providerName,
                            methodIndex = methodIndex,
                            authorization = auth,
                            fallbackFromHeadless = false,
                        ),
                        isSaving = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start provider oauth", e)
                val proxyHint = recordOauthFailure("authorize", providerId, methodIndex, e)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = e.message ?: "Failed to start OAuth",
                        oauthProxyHint = proxyHint,
                    )
                }
            }
        }
    }

    fun completeProviderOauth(code: String?) {
        val pending = _uiState.value.pendingOauth ?: return
        // Prevent duplicate calls while already in progress
        if (_uiState.value.isSaving) return
        // Set isSaving synchronously before launching coroutine to prevent race
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val oauthCode = if (pending.authorization.method == "code") code?.trim()?.ifEmpty { null } else null
                if (BuildConfig.DEBUG) Log.d(TAG, "completeProviderOauth: calling callback for ${pending.providerId}, method=${pending.methodIndex}")
                val completed = api.completeProviderOauth(conn, pending.providerId, pending.methodIndex, oauthCode)
                if (!completed) {
                    // Some server builds complete auth out-of-band and callback can return non-success.
                    // Refresh provider catalog before surfacing an error.
                    val catalog = api.listProviderCatalog(conn)
                    _providerCatalog.value = catalog.all
                    _providerConnected.value = catalog.connected.toSet()
                    _config.value = api.getGlobalConfig(conn)
                    if (pending.providerId in catalog.connected) {
                        _uiState.update { it.copy(pendingOauth = null) }
                        rebuildUi()
                        return@launch
                    }
                    _uiState.update { it.copy(isSaving = false, error = "Failed to complete OAuth") }
                    return@launch
                }
                val disabled = _config.value.disabledProviders.toSet() - pending.providerId
                api.updateGlobalConfig(conn, ServerConfigPatch(disabledProviders = disabled.toList().sorted()))
                _config.value = api.getGlobalConfig(conn)
                _uiState.update { it.copy(pendingOauth = null) }
                loadProviders()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to complete provider oauth", e)
                val proxyHint = recordOauthFailure("callback", pending.providerId, pending.methodIndex, e)
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to complete OAuth", oauthProxyHint = proxyHint)
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun cancelProviderOauth() {
        _uiState.update { it.copy(pendingOauth = null, error = null, oauthProxyHint = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, oauthProxyHint = false) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun saveProvider(providerId: String, name: String, baseUrl: String, models: Map<String, String>) {
        if (providerId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                // 保存前重新拉取最新配置作为合并基底，缩小并发写覆盖的窗口。
                val fresh = api.getConfig(conn).provider.orEmpty()
                _providerConfig.value = fresh
                val existing = fresh[providerId]
                val options = (existing?.options ?: emptyMap()).toMutableMap().apply {
                    put("baseURL", JsonPrimitive(baseUrl.trim()))
                }
                val modelDefs = models.mapValues { (_, modelName) ->
                    ProviderModelDefinition(name = modelName.trim().ifBlank { null })
                }
                val definition = ProviderConfigDefinition(
                    name = name.trim().ifBlank { null },
                    npm = existing?.npm ?: "@ai-sdk/openai-compatible",
                    options = options,
                    models = modelDefs,
                )
                val updated = fresh + (providerId to definition)
                api.updateProviderConfig(conn, updated)
                // 乐观更新：直接使用刚提交的 map，避免立即 GET 回读到服务器尚未生效的旧配置。
                _providerConfig.value = updated
                loadProviders()
                _uiState.update { it.copy(message = context.getString(R.string.server_settings_provider_saved)) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save provider", e)
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.server_settings_provider_save_failed)) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun deleteProvider(providerId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val updated = _providerConfig.value - providerId
                api.updateProviderConfig(conn, updated)
                // 乐观更新：避免立即 GET 回读旧配置。
                _providerConfig.value = updated
                loadProviders()
                _uiState.update { it.copy(message = context.getString(R.string.server_settings_provider_removed)) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete provider", e)
                _uiState.update { it.copy(error = e.message ?: context.getString(R.string.server_settings_provider_remove_failed)) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private suspend fun recordOauthFailure(
        stage: String,
        providerId: String,
        methodIndex: Int,
        error: Exception,
    ): Boolean {
        val statusCode = (error as? ProviderAuthException)?.statusCode
        val message = error.message ?: error::class.simpleName.orEmpty()
        diagnosticLogRepository.record(
            level = "ERROR",
            category = "Provider OAuth",
            message = message,
            details = mapOf(
                "stage" to stage,
                "provider" to providerId,
                "methodIndex" to methodIndex.toString(),
                "httpStatus" to (statusCode?.toString() ?: "unknown"),
            ),
        )
        return false
    }

    fun disconnectProvider(providerId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                if (BuildConfig.DEBUG) Log.d(TAG, "disconnectProvider: calling DELETE /auth/$providerId")
                val removed = api.removeProviderAuth(conn, providerId)
                if (BuildConfig.DEBUG) Log.d(TAG, "disconnectProvider: removed=$removed")
                if (!removed) {
                    _uiState.update { it.copy(isSaving = false, error = "Failed to disconnect provider") }
                    return@launch
                }

                val disposed = runCatching { api.disposeGlobal(conn) }.getOrElse { false }
                if (BuildConfig.DEBUG) Log.d(TAG, "disconnectProvider: disposed=$disposed")

                // Optimistically remove from connected set before reload
                _providerConnected.update { it - providerId }
                rebuildUi()
                loadProviders()

                if (!disposed) {
                    _uiState.update { it.copy(error = "Provider credentials removed, but failed to refresh server instance") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to disconnect provider", e)
                _uiState.update { it.copy(error = e.message ?: "Failed to disconnect provider") }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun setDefaultModel(model: String?) {
        viewModelScope.launch {
            updateConfigPatch(ServerConfigPatch(model = model))
        }
    }

    fun setSmallModel(model: String?) {
        viewModelScope.launch {
            updateConfigPatch(ServerConfigPatch(smallModel = model))
        }
    }

    fun setDefaultAgent(agent: String?) {
        viewModelScope.launch {
            updateConfigPatch(ServerConfigPatch(defaultAgent = agent))
        }
    }

    private suspend fun updateConfigPatch(patch: ServerConfigPatch) {
        val before = _config.value
        try {
            api.updateGlobalConfig(conn, patch)
            _config.value = api.getGlobalConfig(conn)
            rebuildUi()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update config", e)
            _config.value = before
            _uiState.update { it.copy(error = e.message ?: "Failed to update config") }
            rebuildUi()
        }
    }

    fun setModelVisible(providerId: String, modelId: String, visible: Boolean) {
        viewModelScope.launch {
            settingsRepository.setModelVisibility(serverId, providerId, modelId, visible)
        }
    }

    private fun rebuildUi() {
        val hidden = _hiddenModels.value
        val disabled = _config.value.disabledProviders.toSet()

        val customProviders = _providerConfig.value.entries
            .map { (id, def) ->
                ProviderConfigEntry(
                    providerId = id,
                    name = def.name ?: id,
                    npm = def.npm,
                    baseUrl = (def.options["baseURL"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    models = def.models.mapValues { (_, m) -> m.name ?: "" },
                )
            }
            .sortedBy { it.name.lowercase() }

        val providerSource = if (_providerCatalog.value.isNotEmpty()) _providerCatalog.value else _allProviders.value
        val providerToggles = providerSource
            .map {
                ProviderToggle(
                    providerId = it.id,
                    providerName = it.name.ifEmpty { it.id },
                    source = it.source,
                    connected = (it.id in _providerConnected.value) && (it.id !in disabled),
                    hasPaidModels = it.models.values.any { model -> (model.cost?.input ?: 0.0) > 0.0 },
                    enabled = it.id !in disabled
                )
            }
            .sortedWith(
                compareByDescending<ProviderToggle> { it.connected }
                    .thenBy { it.providerName.lowercase() }
            )

        val modelOptions = _allProviders.value
            .filter { it.id !in disabled }
            .flatMap { provider ->
                provider.models.values
                    .filter { modelVisible(hidden, provider.id, it) }
                    .map { model ->
                    ModelOption(
                        key = "${provider.id}/${model.id}",
                        label = "${provider.name.ifEmpty { provider.id }} / ${model.name}"
                    )
                    }
            }
            .sortedBy { it.label.lowercase() }

        val agentOptions = _agents.value
            .filter { it.mode != "subagent" && !it.hidden }
            .map { it.name }
            .distinct()
            .sorted()

        val groups = _allProviders.value
            .mapNotNull { provider ->
                val models = provider.models.values
                    .sortedBy { it.name.lowercase() }
                    .map { model ->
                        ModelToggle(
                            modelId = model.id,
                            modelName = model.name,
                            visible = modelVisible(hidden, provider.id, model)
                        )
                    }
                if (models.isEmpty()) return@mapNotNull null
                ModelGroup(
                    providerId = provider.id,
                    providerName = provider.name.ifEmpty { provider.id },
                    models = models
                )
            }
            .sortedBy { it.providerName.lowercase() }

        _uiState.update {
            it.copy(
                serverName = serverName,
                providers = providerToggles,
                modelOptions = modelOptions,
                agentOptions = agentOptions,
                selectedModel = _config.value.model,
                selectedSmallModel = _config.value.smallModel,
                selectedDefaultAgent = _config.value.defaultAgent,
                groups = groups,
                authMethods = _authMethods.value,
                pendingOauth = it.pendingOauth,
                isSaving = it.isSaving,
                isLoading = false,
                error = it.error,
                customProviders = customProviders
            )
        }
    }

    private fun modelVisible(hidden: Set<String>, providerId: String, model: ProviderModel): Boolean {
        return "$providerId:${model.id}" !in hidden
    }

}

/** App 要求的最低的 starburst-backend 版本。低于该版本时提示升级（后端可能缺 Endpoint）。 */
private const val REQUIRED_BACKEND_VERSION = "1.0.0"

/** 简单的语义化版本比较：取数字段逐个比较，返回 <0 / 0 / >0。无法解析时按相等处理。 */
private fun compareVersions(a: String, b: String): Int {
    val pa = a.trim().trimStart('v').split('.').mapNotNull { it.toIntOrNull() }
    val pb = b.trim().trimStart('v').split('.').mapNotNull { it.toIntOrNull() }
    val n = maxOf(pa.size, pb.size)
    for (i in 0 until n) {
        val av = pa.getOrElse(i) { 0 }
        val bv = pb.getOrElse(i) { 0 }
        if (av != bv) return av.compareTo(bv)
    }
    return 0
}
