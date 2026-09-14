/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ServerRulesViewModel.kt
 * Date : 2026/09/13 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.starburst.ui.screens.server

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.BackendApi
import org.hiylo.starburst.data.api.BackendRule
import org.hiylo.starburst.data.api.BackendRuleDraft
import org.hiylo.starburst.data.api.BackendRuleExecution
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.logging.AppLogger as Log
import javax.inject.Inject

private const val TAG = "ServerRulesViewModel"

/** 自动化规则管理界面状态。 */
data class ServerRulesUiState(
    val rules: List<BackendRule> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val generateDraft: BackendRuleDraft? = null,
    val isGenerating: Boolean = false,
    val generateError: String? = null,
    val isSaving: Boolean = false,
    val executions: List<BackendRuleExecution> = emptyList(),
    val executionsRuleName: String = "",
    val executionsLoading: Boolean = false,
)

/**
 * 自动化规则（Rules）管理 ViewModel：读取后端 /api/rules 并支持 AI 生成草稿、新建、删除与执行记录。
 *
 * @author Hsi Chu
 * @since V1.4.0
 */
@HiltViewModel
class ServerRulesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val serverRepository: ServerRepository,
    private val api: BackendApi,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val serverId = savedStateHandle.get<String>("serverId").orEmpty()

    private val _uiState = MutableStateFlow(ServerRulesUiState())
    val uiState: StateFlow<ServerRulesUiState> = _uiState.asStateFlow()

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
                _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.server_backend_not_configured)) }
            }
        }
    }

    /** 拉取规则列表。 */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val rules = api.listRules(backendUrl, backendToken)
                _uiState.update { it.copy(rules = rules, isLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load rules", e)
                _uiState.update { it.copy(isLoading = false, error = e.message ?: context.getString(R.string.server_load_failed)) }
            }
        }
    }

    /** 用自然语言描述生成规则草稿，填充到表单。 */
    fun generate(description: String) {
        val trimmed = description.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, generateError = null) }
            try {
                val draft = api.generateRule(backendUrl, backendToken, trimmed)
                _uiState.update { it.copy(isGenerating = false, generateDraft = draft) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate rule", e)
                _uiState.update { it.copy(isGenerating = false, generateError = e.message ?: context.getString(R.string.server_generate_failed)) }
            }
        }
    }

    /** 新建规则并刷新列表。 */
    fun createRule(
        name: String,
        kind: String,
        schedule: String,
        directory: String,
        prompt: String,
        enabled: Boolean,
        onDone: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                api.createRule(
                    backendUrl,
                    backendToken,
                    BackendApi.BackendCreateRuleRequest(
                        name = name,
                        kind = kind,
                        schedule = schedule,
                        directory = directory,
                        prompt = prompt,
                        enabled = enabled,
                    ),
                )
                _uiState.update { it.copy(isSaving = false, generateDraft = null) }
                refresh()
                onDone(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create rule", e)
                _uiState.update { it.copy(isSaving = false) }
                onDone(false)
            }
        }
    }

    /** 删除规则。 */
    fun deleteRule(id: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val ok = api.deleteRule(backendUrl, backendToken, id)
                if (ok) refresh()
                onDone(ok)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete rule", e)
                onDone(false)
            }
        }
    }

    /** 加载某规则的执行记录。 */
    fun loadExecutions(rule: BackendRule) {
        viewModelScope.launch {
            _uiState.update { it.copy(executionsLoading = true, executionsRuleName = rule.name) }
            try {
                val resp = api.listRuleExecutions(backendUrl, backendToken, rule.id)
                _uiState.update {
                    it.copy(executions = resp.executions, executionsLoading = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load executions", e)
                _uiState.update { it.copy(executions = emptyList(), executionsLoading = false) }
            }
        }
    }

    /** 关闭执行记录弹窗。 */
    fun closeExecutions() {
        _uiState.update { it.copy(executions = emptyList(), executionsRuleName = "") }
    }

    /** 清除当前生成草稿。 */
    fun clearDraft() {
        _uiState.update { it.copy(generateDraft = null, generateError = null) }
    }
}
