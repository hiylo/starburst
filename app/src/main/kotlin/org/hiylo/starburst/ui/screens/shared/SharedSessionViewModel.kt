/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SharedSessionViewModel.kt
 * Date : 2026/09/11 09:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.3.0
 */
package org.hiylo.starburst.ui.screens.shared

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.repository.SharedSessionRepository
import org.hiylo.starburst.domain.model.SharedSession
import org.hiylo.starburst.logging.AppLogger as Log
import javax.inject.Inject

private const val TAG = "SharedSessionViewModel"

/** 分享后端基础地址：分享读取与本地服务器无关，conn 仅作为占位传递。 */
private const val SHARE_PLACEHOLDER_BASE_URL = "https://opncd.ai"

/**
 * 会话分享只读查看的 UI 状态。
 */
sealed interface SharedSessionUiState {
    /** 加载中。 */
    data object Loading : SharedSessionUiState

    /** 加载失败，携带错误信息。 */
    data class Error(val message: String) : SharedSessionUiState

    /** 加载成功，携带只读会话。 */
    data class Session(val session: SharedSession) : SharedSessionUiState
}

/**
 * 会话分享只读查看 ViewModel：根据分享 ID 加载只读会话，仅供展示，不做任何编辑操作。
 *
 * @author Hsi Chu
 * @since V1.3.0
 */
@HiltViewModel
class SharedSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SharedSessionRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** 分享 ID，由导航参数传入。 */
    private val shareId: String = savedStateHandle.get<String>("shareId").orEmpty()

    /** 占位连接：分享读取走公开分享后端，conn 仅用于保持仓库签名一致。 */
    private val conn: ServerConnection = ServerConnection.from(SHARE_PLACEHOLDER_BASE_URL)

    private val _state = MutableStateFlow<SharedSessionUiState>(SharedSessionUiState.Loading)
    val state: StateFlow<SharedSessionUiState> = _state.asStateFlow()

    init {
        load(shareId)
    }

    /**
     * 通过分享 ID 加载只读会话。
     *
     * @param shareId 分享 ID
     */
    fun load(shareId: String) {
        viewModelScope.launch {
            _state.value = SharedSessionUiState.Loading
            try {
                val session = repository.loadSharedSession(conn, shareId)
                _state.value = SharedSessionUiState.Session(session)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load shared session", e)
                _state.value = SharedSessionUiState.Error(e.message ?: context.getString(R.string.share_load_failed))
            }
        }
    }

    /** 重试加载。 */
    fun retry() {
        load(shareId)
    }
}
