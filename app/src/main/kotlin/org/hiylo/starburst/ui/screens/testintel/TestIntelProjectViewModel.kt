/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : TestIntelProjectViewModel.kt
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.BackendApi
import org.hiylo.starburst.data.api.IntelFeature
import org.hiylo.starburst.data.api.IntelFeatureChat
import org.hiylo.starburst.data.api.IntelFix
import org.hiylo.starburst.data.api.IntelIssue
import org.hiylo.starburst.data.api.IntelTestResult
import org.hiylo.starburst.data.api.IntelTestRun
import org.hiylo.starburst.data.repository.ServerRepository
import javax.inject.Inject

/** 项目详情页一个分块的通用状态。 */
data class ProjectBlockState<T>(
    val loading: Boolean = true,
    val error: String? = null,
    val items: List<T> = emptyList(),
)

/** 功能点 AI 对话弹窗状态。 */
data class FeatureChatState(
    val feature: IntelFeature? = null,
    val chats: List<IntelFeatureChat> = emptyList(),
    val question: String = "",
    val loadingChat: Boolean = false,
    val sending: Boolean = false,
)

/**
 * 测试智能：项目详情 ViewModel。
 *
 * 并发拉取功能点 / 单测 / 问题 / 修复建议四个分块（各自独立 catch，互不影响），并承载
 * 发起单测、标记解决、挂功能点、应用/回滚/驳回修复、功能点 AI 对话等闭环操作。
 *
 * @author Hsi Chu
 * @since 3.1.0
 */
@HiltViewModel
class TestIntelProjectViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val backendApi: BackendApi,
    private val serverRepository: ServerRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val serverId = savedStateHandle.get<String>("serverId").orEmpty()
    private val projectId: Long = savedStateHandle.get<String>("projectId")?.toLongOrNull() ?: 0L

    private var backendUrl = ""
    private var backendToken = ""

    private val _features = MutableStateFlow(ProjectBlockState<IntelFeature>())
    val features: StateFlow<ProjectBlockState<IntelFeature>> = _features.asStateFlow()

    private val _runs = MutableStateFlow(ProjectBlockState<IntelTestRun>())
    val runs: StateFlow<ProjectBlockState<IntelTestRun>> = _runs.asStateFlow()

    private val _issues = MutableStateFlow(ProjectBlockState<IntelIssue>())
    val issues: StateFlow<ProjectBlockState<IntelIssue>> = _issues.asStateFlow()

    private val _fixes = MutableStateFlow(ProjectBlockState<IntelFix>())
    val fixes: StateFlow<ProjectBlockState<IntelFix>> = _fixes.asStateFlow()

    /** 已加载的逐 run 用例结果。 */
    private val _runResults = MutableStateFlow<Map<Long, List<IntelTestResult>>>(emptyMap())
    val runResults: StateFlow<Map<Long, List<IntelTestResult>>> = _runResults.asStateFlow()

    private val _startingRun = MutableStateFlow(false)
    val startingRun: StateFlow<Boolean> = _startingRun.asStateFlow()

    private val _featureChat = MutableStateFlow(FeatureChatState())
    val featureChat: StateFlow<FeatureChatState> = _featureChat.asStateFlow()

    private val _oneShot = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val oneShot: SharedFlow<String> = _oneShot.asSharedFlow()

    private fun emitOneShot(message: String) {
        _oneShot.tryEmit(message)
    }

    private fun backendUnavailableError(): String = context.getString(R.string.test_intel_error_backend)

    init {
        viewModelScope.launch {
            val server = serverRepository.getServer(serverId)
            backendUrl = server?.backendResolvedUrl.orEmpty()
            backendToken = server?.backendResolvedToken.orEmpty()
            if (backendUrl.isBlank() || backendToken.isBlank()) {
                val error = backendUnavailableError()
                _features.update { it.copy(loading = false, error = error) }
                _runs.update { it.copy(loading = false, error = error) }
                _issues.update { it.copy(loading = false, error = error) }
                _fixes.update { it.copy(loading = false, error = error) }
                return@launch
            }
            loadAll()
        }
    }

    private suspend fun <T> loadBlock(
        state: MutableStateFlow<ProjectBlockState<T>>,
        errorMessage: String,
        fetch: suspend () -> List<T>,
    ) {
        try {
            val items = fetch()
            state.update { it.copy(loading = false, error = null, items = items) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.update { it.copy(loading = false, error = e.message ?: errorMessage) }
        }
    }

    /** 并发加载四个分块；任一失败只影响自己的错误态。 */
    fun loadAll() {
        viewModelScope.launch {
            coroutineScope {
                listOf(
                    async {
                        loadBlock(_features, context.getString(R.string.test_intel_error_load)) {
                            backendApi.intelFeatures(backendUrl, backendToken, projectId)
                        }
                    },
                    async {
                        loadBlock(_runs, context.getString(R.string.test_intel_error_load)) {
                            backendApi.intelRuns(backendUrl, backendToken, projectId)
                        }
                    },
                    async {
                        loadBlock(_issues, context.getString(R.string.test_intel_issues_load_failed)) {
                            backendApi.intelIssues(backendUrl, backendToken, projectId)
                        }
                    },
                    async {
                        loadBlock(_fixes, context.getString(R.string.test_intel_fixes_load_failed)) {
                            backendApi.intelFixes(backendUrl, backendToken, projectId)
                        }
                    },
                ).awaitAll()
            }
        }
    }

    fun refreshFeatures() {
        viewModelScope.launch {
            _features.update { it.copy(loading = true, error = null) }
            loadBlock(_features, context.getString(R.string.test_intel_error_load)) {
                backendApi.intelFeatures(backendUrl, backendToken, projectId)
            }
        }
    }

    fun refreshRuns() {
        viewModelScope.launch {
            _runs.update { it.copy(loading = true, error = null) }
            loadBlock(_runs, context.getString(R.string.test_intel_error_load)) {
                backendApi.intelRuns(backendUrl, backendToken, projectId)
            }
        }
    }

    fun refreshIssues() {
        viewModelScope.launch {
            loadBlock(_issues, context.getString(R.string.test_intel_issues_load_failed)) {
                backendApi.intelIssues(backendUrl, backendToken, projectId)
            }
        }
    }

    fun refreshFixes() {
        viewModelScope.launch {
            loadBlock(_fixes, context.getString(R.string.test_intel_fixes_load_failed)) {
                backendApi.intelFixes(backendUrl, backendToken, projectId)
            }
        }
    }

    /** 发起单测：根模块（moduleId=0）；成功后刷新 runs 列表。 */
    fun startRun() {
        if (_startingRun.value) return
        _startingRun.value = true
        viewModelScope.launch {
            try {
                val run = backendApi.startIntelRun(backendUrl, backendToken, projectId, moduleId = 0L)
                emitOneShot(context.getString(R.string.test_intel_run_started))
                _runs.update {
                    it.copy(
                        loading = false,
                        error = null,
                        items = listOf(run) + it.items,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitOneShot(context.getString(R.string.test_intel_run_failed))
            } finally {
                _startingRun.value = false
            }
        }
    }

    /** 拉取某次运行的逐用例结果（幂等，重复调用直接返回已缓存结果）。 */
    fun loadRunResults(runId: Long) {
        if (_runResults.value.containsKey(runId)) return
        viewModelScope.launch {
            try {
                val results = backendApi.intelRunResults(backendUrl, backendToken, runId)
                _runResults.update { it + (runId to results) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitOneShot(context.getString(R.string.test_intel_run_results_failed))
                _runResults.update { it + (runId to emptyList()) }
            }
        }
    }

    /** 标记问题已解决（ack）；成功后刷新问题列表。 */
    fun acknowledgeIssue(issueId: Long) {
        viewModelScope.launch {
            val ok = try {
                backendApi.ackIntelIssue(backendUrl, backendToken, issueId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitOneShot(context.getString(R.string.test_intel_issue_ack_failed))
                return@launch
            }
            if (ok) {
                emitOneShot(context.getString(R.string.test_intel_issue_ack_msg))
                refreshIssues()
            } else {
                emitOneShot(context.getString(R.string.test_intel_issue_ack_failed))
            }
        }
    }

    /** 把问题挂接到功能点；成功后刷新问题列表。 */
    fun linkIssueToFeature(issueId: Long, featureId: Long) {
        viewModelScope.launch {
            val ok = try {
                backendApi.linkIssueToFeature(backendUrl, backendToken, issueId, featureId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitOneShot(context.getString(R.string.test_intel_issue_link_failed))
                return@launch
            }
            if (ok) {
                emitOneShot(context.getString(R.string.test_intel_issue_link_msg))
                refreshIssues()
            } else {
                emitOneShot(context.getString(R.string.test_intel_issue_link_failed))
            }
        }
    }

    /** 应用修复；成功后刷新修复列表。 */
    fun applyFix(fixId: Long, writeMode: String = "file") {
        viewModelScope.launch {
            val ok = try {
                backendApi.applyIntelFix(backendUrl, backendToken, fixId, writeMode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitOneShot(context.getString(R.string.test_intel_fix_action_failed))
                return@launch
            }
            if (ok) {
                emitOneShot(context.getString(R.string.test_intel_fix_apply_msg))
                refreshFixes()
            } else {
                emitOneShot(context.getString(R.string.test_intel_fix_action_failed))
            }
        }
    }

    /** 回滚修复；成功后刷新修复列表。 */
    fun rollbackFix(fixId: Long) {
        viewModelScope.launch {
            val ok = try {
                backendApi.rollbackIntelFix(backendUrl, backendToken, fixId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitOneShot(context.getString(R.string.test_intel_fix_action_failed))
                return@launch
            }
            if (ok) {
                emitOneShot(context.getString(R.string.test_intel_fix_rollback_msg))
                refreshFixes()
            } else {
                emitOneShot(context.getString(R.string.test_intel_fix_action_failed))
            }
        }
    }

    /** 驳回修复；成功后刷新修复列表。 */
    fun rejectFix(fixId: Long) {
        viewModelScope.launch {
            val ok = try {
                backendApi.rejectIntelFix(backendUrl, backendToken, fixId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitOneShot(context.getString(R.string.test_intel_fix_action_failed))
                return@launch
            }
            if (ok) {
                emitOneShot(context.getString(R.string.test_intel_fix_reject_msg))
                refreshFixes()
            } else {
                emitOneShot(context.getString(R.string.test_intel_fix_action_failed))
            }
        }
    }

    /** 打开功能点 AI 对话并加载历史。 */
    fun openFeatureChat(feature: IntelFeature) {
        _featureChat.value = FeatureChatState(feature = feature, loadingChat = true)
        viewModelScope.launch {
            try {
                val chats = backendApi.intelFeatureChats(backendUrl, backendToken, feature.id, projectId)
                _featureChat.update { it.copy(loadingChat = false, chats = chats) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _featureChat.update { it.copy(loadingChat = false) }
                emitOneShot(context.getString(R.string.test_intel_chat_failed))
            }
        }
    }

    fun closeFeatureChat() {
        _featureChat.update { it.copy(feature = null, chats = emptyList(), question = "") }
    }

    fun setFeatureChatQuestion(text: String) {
        _featureChat.update { it.copy(question = text) }
    }

    /** 向功能点提问并追加回答。 */
    fun sendFeatureChat() {
        val feature = _featureChat.value.feature ?: return
        val question = _featureChat.value.question.trim()
        if (question.isEmpty() || _featureChat.value.sending) return
        _featureChat.update { it.copy(sending = true) }
        viewModelScope.launch {
            try {
                val resp = backendApi.askIntelFeature(backendUrl, backendToken, feature.id, projectId, question)
                _featureChat.update {
                    it.copy(sending = false, question = "", chats = it.chats + resp.chat)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _featureChat.update { it.copy(sending = false) }
                emitOneShot(context.getString(R.string.test_intel_chat_failed))
            }
        }
    }
}