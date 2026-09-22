/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : TestIntelViewModel.kt
 * Date : 2026/09/22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.testintel

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
import org.hiylo.starburst.data.api.BackendApi
import org.hiylo.starburst.data.api.IntelProject
import org.hiylo.starburst.data.repository.ServerRepository
import javax.inject.Inject

/** 测试智能项目列表的 UI 状态。 */
data class TestIntelUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val projects: List<IntelProject> = emptyList(),
)

/**
 * 测试智能入口：项目列表 ViewModel。
 *
 * 从路由参数读取 serverId → ServerRepository 解析后端地址与 token →
 * 拉取已分析的 Intel 项目。
 *
 * @author Hsi Chu
 * @since 3.1.0
 */
@HiltViewModel
class TestIntelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val backendApi: BackendApi,
    private val serverRepository: ServerRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val serverId = savedStateHandle.get<String>("serverId").orEmpty()

    private var backendUrl = ""
    private var backendToken = ""

    private val _uiState = MutableStateFlow(TestIntelUiState())
    val uiState: StateFlow<TestIntelUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val server = serverRepository.getServer(serverId)
            backendUrl = server?.backendResolvedUrl.orEmpty()
            backendToken = server?.backendResolvedToken.orEmpty()
            refresh()
        }
    }

    /** 重新拉取项目列表；后端地址缺失时给出友好错误。 */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            if (backendUrl.isBlank() || backendToken.isBlank()) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = context.getString(R.string.test_intel_error_backend),
                    )
                }
                return@launch
            }
            try {
                val projects = backendApi.intelProjects(backendUrl, backendToken)
                _uiState.update { it.copy(loading = false, error = null, projects = projects) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: context.getString(R.string.test_intel_error_load),
                    )
                }
            }
        }
    }
}
