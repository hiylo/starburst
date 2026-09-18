/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : TaskListViewModel.kt
 * Date : 2026/09/11 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.starburst.ui.screens.tasks

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.BackendPlanDraft
import org.hiylo.starburst.data.api.BackendPlanStepDraft
import org.hiylo.starburst.data.api.BackendStats
import org.hiylo.starburst.data.api.BackendTaskTarget
import org.hiylo.starburst.data.repository.BackendRepository
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.domain.model.BackendArchive
import org.hiylo.starburst.domain.model.BackendTask
import org.hiylo.starburst.logging.AppLogger as Log
import javax.inject.Inject

private const val TAG = "TaskListViewModel"

/**
 * 任务中心 ViewModel：从对应 server 读取 Backend 配置，拉取任务列表并订阅 WS 实时状态。
 *
 * @author Hsi Chu
 * @since V1.3.0
 */
@HiltViewModel
 class TaskListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepository: ServerRepository,
    private val backendRepository: BackendRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val serverId = savedStateHandle.get<String>("serverId").orEmpty()

    val tasks: StateFlow<List<BackendTask>> = backendRepository.tasks(serverId)
    val connected: StateFlow<Boolean> = backendRepository.connected(serverId)
    val archives: StateFlow<List<BackendArchive>> = backendRepository.archives(serverId)

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _archiveLoading = MutableStateFlow(false)
    val archiveLoading: StateFlow<Boolean> = _archiveLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _archiveError = MutableStateFlow<String?>(null)
    val archiveError: StateFlow<String?> = _archiveError.asStateFlow()

    private val _stats = MutableStateFlow<BackendStats?>(null)
    val stats: StateFlow<BackendStats?> = _stats.asStateFlow()

    private val _statsLoading = MutableStateFlow(false)
    val statsLoading: StateFlow<Boolean> = _statsLoading.asStateFlow()

    private val _statsError = MutableStateFlow<String?>(null)
    val statsError: StateFlow<String?> = _statsError.asStateFlow()

    private val _clarifyStreamText = MutableStateFlow("")
    val clarifyStreamText: StateFlow<String> = _clarifyStreamText.asStateFlow()

    private var backendUrl = ""
    private var backendToken = ""

    init {
        viewModelScope.launch {
            val server = serverRepository.servers.first().firstOrNull { it.id == serverId }
            // 地址自动推导（opencode 同主机 :18880）、token 默认 ocb_default，零配置可用。
            backendUrl = server?.backendResolvedUrl.orEmpty()
            backendToken = server?.backendResolvedToken.orEmpty()
            if (backendUrl.isNotBlank()) {
                refresh()
                backendRepository.connectWs(serverId, backendUrl, backendToken)
            } else {
                _loading.value = false
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                backendRepository.loadTasks(serverId, backendUrl, backendToken)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load tasks", e)
                _error.value = e.message ?: context.getString(R.string.task_load_failed)
            } finally {
                _loading.value = false
            }
        }
    }

    fun createTask(
        prompt: String,
        name: String? = null,
        directory: String? = null,
        scheduledAt: String? = null,
        cron: String? = null,
        onDone: (Boolean) -> Unit = {},
    ) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                backendRepository.createTask(serverId, backendUrl, backendToken, trimmed, name = name, directory = directory, scheduledAt = scheduledAt, cron = cron)
                onDone(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create task", e)
                _error.value = e.message ?: context.getString(R.string.task_create_failed)
                onDone(false)
            }
        }
    }

    fun createPlan(
        name: String,
        steps: List<BackendPlanStepDraft>,
        scheduledAt: String? = null,
        cron: String? = null,
        onDone: (Boolean) -> Unit = {},
    ) {
        if (steps.isEmpty()) return
        viewModelScope.launch {
            try {
                backendRepository.createPlan(serverId, backendUrl, backendToken, name, steps, scheduledAt, cron)
                onDone(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create plan", e)
                _error.value = e.message ?: context.getString(R.string.task_create_plan_failed)
                onDone(false)
            }
        }
    }

    /**
     * 流式生成/修正任务计划草稿。
     * [description] 首次生成；[draft]+[instruction] 基于既有草案多轮修正。
     * 生成过程实时更新 [clarifyStreamText]，结束回传 [onResult]。
     */
    fun generatePlan(
        description: String = "",
        draft: BackendPlanDraft? = null,
        instruction: String = "",
        onDelta: (String) -> Unit = {},
        onResult: (Result<BackendPlanDraft>) -> Unit = {},
    ) {
        viewModelScope.launch {
            _clarifyStreamText.value = ""
            val result = try {
                backendRepository.generatePlanStream(
                    backendUrl,
                    backendToken,
                    description,
                    draft,
                    instruction,
                ) { delta ->
                    _clarifyStreamText.value += delta
                    onDelta(delta)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to generate plan", e)
                Result.failure(e)
            }
            onResult(result)
        }
    }

    fun batch(prompt: String, targets: List<BackendTaskTarget>, onDone: (Boolean) -> Unit = {}) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty() || targets.isEmpty()) return
        viewModelScope.launch {
            try {
                backendRepository.batch(serverId, backendUrl, backendToken, trimmed, targets)
                onDone(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to batch tasks", e)
                _error.value = e.message ?: context.getString(R.string.task_batch_create_failed)
                onDone(false)
            }
        }
    }

    fun cancelTask(id: String) {
        viewModelScope.launch {
            try {
                backendRepository.cancelTask(serverId, backendUrl, backendToken, id)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel task", e)
                _error.value = e.message ?: context.getString(R.string.task_cancel_failed)
            }
        }
    }

    fun unblockTask(id: String) {
        viewModelScope.launch {
            try {
                backendRepository.unblockTask(serverId, backendUrl, backendToken, id)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unblock task", e)
                _error.value = e.message ?: context.getString(R.string.task_unblock_failed)
            }
        }
    }

    fun purgeFinishedTasks(onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val deleted = backendRepository.purgeFinishedTasks(serverId, backendUrl, backendToken)
                onDone(deleted)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to purge finished tasks", e)
                _error.value = e.message ?: context.getString(R.string.task_purge_failed)
                onDone(0)
            }
        }
    }

    fun loadArchives() {
        viewModelScope.launch {
            _archiveLoading.value = true
            _archiveError.value = null
            try {
                backendRepository.loadArchives(serverId, backendUrl, backendToken)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load archives", e)
                _archiveError.value = e.message ?: context.getString(R.string.task_load_archive_failed)
            } finally {
                _archiveLoading.value = false
            }
        }
    }

    /** 加载后端用量统计（任务计数 + token 调用量 + 归档数）。 */
    fun loadStats() {
        viewModelScope.launch {
            _statsLoading.value = true
            _statsError.value = null
            try {
                _stats.value = backendRepository.getStats(backendUrl, backendToken)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load stats", e)
                _statsError.value = e.message ?: context.getString(R.string.task_load_stats_failed)
            } finally {
                _statsLoading.value = false
            }
        }
    }

    fun archiveSession(sessionId: String, format: String = "markdown", onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                backendRepository.archiveSession(serverId, backendUrl, backendToken, sessionId, format)
                onDone(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to archive session", e)
                _archiveError.value = e.message ?: context.getString(R.string.task_archive_failed)
                onDone(false)
            }
        }
    }

    fun deleteArchive(id: String) {
        viewModelScope.launch {
            try {
                backendRepository.deleteArchive(serverId, backendUrl, backendToken, id)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete archive", e)
                _archiveError.value = e.message ?: context.getString(R.string.task_delete_archive_failed)
            }
        }
    }

    override fun onCleared() {
        backendRepository.disconnectWs(serverId)
        super.onCleared()
    }
}
