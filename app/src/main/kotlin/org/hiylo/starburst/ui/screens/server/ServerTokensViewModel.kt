/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ServerTokensViewModel.kt
 * Date : 2026/09/13 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.starburst.ui.screens.server

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.hiylo.starburst.data.api.BackendApi
import org.hiylo.starburst.data.api.BackendToken
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.logging.AppLogger as Log
import javax.inject.Inject

private const val TAG = "ServerTokensViewModel"

/** APP token 管理界面状态。 */
data class ServerTokensUiState(
    val tokens: List<BackendToken> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val createdToken: String? = null,
    val isCreating: Boolean = false,
)

/**
 * APP token 管理 ViewModel：列出 / 新建 / 吊销后端访问 token。
 *
 * @author Hsi Chu
 * @since V1.4.0
 */
@HiltViewModel
class ServerTokensViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepository: ServerRepository,
    private val api: BackendApi,
) : ViewModel() {
    private val serverId = savedStateHandle.get<String>("serverId").orEmpty()

    private val _uiState = MutableStateFlow(ServerTokensUiState())
    val uiState: StateFlow<ServerTokensUiState> = _uiState.asStateFlow()

    private var backendUrl = ""
    private var backendToken = ""

    init {
        viewModelScope.launch {
            val server = serverRepository.servers.first().firstOrNull { it.id == serverId }
            backendUrl = server?.backendResolvedUrl.orEmpty()
            backendToken = server?.backendResolvedToken ?: "ocb_default"
            if (backendUrl.isNotBlank()) {
                refresh()
            } else {
                _uiState.update { it.copy(isLoading = false, error = "后端未配置") }
            }
        }
    }

    /** 拉取 token 列表。 */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val tokens = api.listTokens(backendUrl, backendToken)
                _uiState.update { it.copy(tokens = tokens, isLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load tokens", e)
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
            }
        }
    }

    /** 新建 token，成功后明文仅展示一次。 */
    fun createToken(name: String, onDone: (Boolean) -> Unit = {}) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true) }
            try {
                val raw = api.createToken(backendUrl, backendToken, trimmed)
                _uiState.update { it.copy(isCreating = false, createdToken = raw) }
                refresh()
                onDone(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create token", e)
                _uiState.update { it.copy(isCreating = false) }
                onDone(false)
            }
        }
    }

    /** 吊销 token。 */
    fun revokeToken(id: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val ok = api.revokeToken(backendUrl, backendToken, id)
                if (ok) refresh()
                onDone(ok)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to revoke token", e)
                onDone(false)
            }
        }
    }

    /** 关闭明文展示弹窗。 */
    fun dismissCreatedToken() {
        _uiState.update { it.copy(createdToken = null) }
    }
}
