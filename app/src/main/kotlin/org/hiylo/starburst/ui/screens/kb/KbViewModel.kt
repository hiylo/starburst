/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : KbViewModel.kt
 * Date : 2026/09/22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.kb

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.BackendKbApi
import org.hiylo.starburst.data.api.KbCollection
import org.hiylo.starburst.data.repository.ServerRepository
import javax.inject.Inject

/** 知识库集合列表页的 UI 状态。 */
data class KbCollectionListUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val collections: List<KbCollection> = emptyList(),
    val creating: Boolean = false,
)

/**
 * 知识库：集合列表 ViewModel。
 *
 * 从路由参数读取 serverId → ServerRepository 解析后端地址与 token →
 * 拉取 `/api/kb/collections`，并支持新建集合。
 *
 * @author Hsi Chu
 * @since 3.1.0
 */
@HiltViewModel
class KbCollectionListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val kbApi: BackendKbApi,
    private val serverRepository: ServerRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val serverId = savedStateHandle.get<String>("serverId").orEmpty()

    private var backendUrl = ""
    private var backendToken = ""

    private val _uiState = MutableStateFlow(KbCollectionListUiState())
    val uiState: StateFlow<KbCollectionListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val server = serverRepository.getServer(serverId)
            backendUrl = server?.backendResolvedUrl.orEmpty()
            backendToken = server?.backendResolvedToken.orEmpty()
            refresh()
        }
    }

    /** 重新拉取集合列表；后端地址缺失时给出友好错误。 */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            if (backendUrl.isBlank() || backendToken.isBlank()) {
                _uiState.update {
                    it.copy(loading = false, error = context.getString(R.string.kb_backend_error))
                }
                return@launch
            }
            try {
                val collections = kbApi.listCollections(backendUrl, backendToken)
                _uiState.update { it.copy(loading = false, error = null, collections = collections) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, error = e.message ?: context.getString(R.string.kb_load_error))
                }
            }
        }
    }

    /** 新建集合（`POST /api/kb/collections`）；成功后刷新列表。 */
    fun createCollection(name: String, description: String, onResult: (Boolean) -> Unit = {}) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || _uiState.value.creating) return
        viewModelScope.launch {
            _uiState.update { it.copy(creating = true) }
            try {
                kbApi.createCollection(backendUrl, backendToken, trimmedName, description.trim())
                _uiState.update { it.copy(creating = false) }
                onResult(true)
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(creating = false) }
                onResult(false)
            }
        }
    }
}
