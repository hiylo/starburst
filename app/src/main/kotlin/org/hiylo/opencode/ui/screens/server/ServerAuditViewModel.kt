/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : ServerAuditViewModel.kt
 * Date : 2026/09/13 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.opencode.ui.screens.server

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
import org.hiylo.opencode.data.api.BackendApi
import org.hiylo.opencode.data.api.BackendAuditEntry
import org.hiylo.opencode.data.repository.ServerRepository
import org.hiylo.opencode.logging.AppLogger as Log
import javax.inject.Inject

private const val TAG = "ServerAuditViewModel"

/** 审计日志界面状态。 */
data class ServerAuditUiState(
    val entries: List<BackendAuditEntry> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * 审计日志 ViewModel：读取后端 /api/audit 展示最近的鉴权操作记录。
 *
 * @author Hsi Chu
 * @since V1.4.0
 */
@HiltViewModel
class ServerAuditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepository: ServerRepository,
    private val api: BackendApi,
) : ViewModel() {
    private val serverId = savedStateHandle.get<String>("serverId").orEmpty()

    private val _uiState = MutableStateFlow(ServerAuditUiState())
    val uiState: StateFlow<ServerAuditUiState> = _uiState.asStateFlow()

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

    /** 拉取审计日志。 */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val entries = api.listAudit(backendUrl, backendToken)
                _uiState.update { it.copy(entries = entries, isLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load audit", e)
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "加载失败") }
            }
        }
    }
}
