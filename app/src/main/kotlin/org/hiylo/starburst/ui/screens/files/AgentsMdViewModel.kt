/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : AgentsMdViewModel.kt
 * Date : 2026/09/13 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.starburst.ui.screens.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hiylo.starburst.data.api.BackendApi
import org.hiylo.starburst.data.api.OpenCodeApi
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.api.listDirectory
import org.hiylo.starburst.data.api.readFile
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.data.shell.ServerShellRegistry
import org.hiylo.starburst.data.shell.ShellCommandResult
import org.hiylo.starburst.logging.AppLogger as Log
import java.util.Base64
import javax.inject.Inject

private const val TAG = "AgentsMdViewModel"
private const val AGENTS_FILE = "AGENTS.md"

/** AGENTS.md 界面状态。 */
data class AgentsMdUiState(
    val directory: String = "",
    val existingContent: String = "",
    val exists: Boolean? = null,
    val detectError: String? = null,
    val draft: String = "",
    val isGenerating: Boolean = false,
    val generateError: String? = null,
    val saveState: FileSaveState = FileSaveState(),
)

/** 生成 AGENTS.md 时喂给模型的 system 指令。 */
private const val AGENTS_MD_SYSTEM = """你是一名资深软件工程师，负责为项目编写或完善 AGENTS.md 文件。
AGENTS.md 是 AI 编码助手读取的项目级规则文档，内容应包含：
1. 项目简介与技术栈；
2. 构建、测试、运行的常用命令；
3. 代码风格与目录结构约定；
4. 开发/提交规范与其他 AI 需要遵守的约定。

要求：
- 直接输出 AGENTS.md 的完整 Markdown 内容，不要包含任何解释、前后缀或代码块围栏。
- 语言与用户提供的上下文保持一致（中文项目用中文）。
- 保持简洁、可执行、可检索，避免空话。
- 如果是「完善」模式，保留已有内容中仍然有效的部分，只做补充和修正。"""

/**
 * AGENTS.md 初始化 / 完善 ViewModel：检测项目根 AGENTS.md，用后端 LLM 生成或完善，
 * 再通过共享 PTY 写回项目目录。
 *
 * @author Hsi Chu
 * @since V1.4.0
 */
@HiltViewModel
class AgentsMdViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val backendApi: BackendApi,
    private val serverRepository: ServerRepository,
    private val shellRegistry: ServerShellRegistry,
) : ViewModel() {
    private val connection = ServerConnection.from(
        url = savedStateHandle.get<String>("serverUrl").orEmpty(),
        username = savedStateHandle.get<String>("username").orEmpty().ifBlank { "opencode" },
        password = savedStateHandle.get<String>("password").orEmpty().ifBlank { null },
    )
    private val directory = savedStateHandle.get<String>("directory").orEmpty()
    private val serverId = savedStateHandle.get<String>("serverId").orEmpty().ifBlank { connection.baseUrl }

    private val _uiState = MutableStateFlow(AgentsMdUiState(directory = directory))
    val uiState: StateFlow<AgentsMdUiState> = _uiState.asStateFlow()

    private var backendUrl = ""
    private var backendToken = ""

    private var ptySessionAcquired = false
    private val ptySession by lazy {
        ptySessionAcquired = true
        shellRegistry.acquire(serverId, api, connection, directory)
    }

    override fun onCleared() {
        if (ptySessionAcquired) {
            shellRegistry.release(serverId)
        }
        super.onCleared()
    }

    init {
        viewModelScope.launch {
            val server = serverRepository.servers.first().firstOrNull { it.id == serverId }
            backendUrl = server?.backendResolvedUrl.orEmpty()
            backendToken = server?.backendResolvedToken ?: "ocb_default"
            detect()
        }
    }

    /** 检测项目根是否存在 AGENTS.md，并读取现有内容。 */
    fun detect() {
        viewModelScope.launch {
            _uiState.update { it.copy(exists = null, detectError = null) }
            try {
                val content = api.readFile(connection, AGENTS_FILE, directory)
                // opencode 对不存在的文件返回 200 + 空 content（而非 404），
                // 因此必须以内容是否为空来判断文件是否存在，否则空文件会被误判为「已存在」而卡在空预览。
                val hasContent = content.content.isNotBlank()
                _uiState.update {
                    it.copy(exists = hasContent, existingContent = if (hasContent) content.content else "", draft = "")
                }
            } catch (e: Exception) {
                Log.d(TAG, "AGENTS.md not found, entering init mode", e)
                _uiState.update { it.copy(exists = false, existingContent = "") }
            }
        }
    }

    /** 用后端 LLM 生成（或完善）AGENTS.md 草稿。 */
    fun generate() {
        if (backendUrl.isBlank()) {
            _uiState.update { it.copy(generateError = "后端未配置，无法生成") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, generateError = null) }
            try {
                val context = buildContext()
                val text = backendApi.completeText(backendUrl, backendToken, AGENTS_MD_SYSTEM, context)
                _uiState.update { it.copy(isGenerating = false, draft = text.trim()) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate AGENTS.md", e)
                _uiState.update { it.copy(isGenerating = false, generateError = e.message ?: "生成失败") }
            }
        }
    }

    /** 用自然语言指令让 LLM 修改当前 AGENTS.md 草稿。 */
    fun modifyWithInstruction(instruction: String) {
        val trimmed = instruction.trim()
        if (trimmed.isEmpty()) return
        if (backendUrl.isBlank()) {
            _uiState.update { it.copy(generateError = "后端未配置，无法修改") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, generateError = null) }
            try {
                val context = buildModifyContext(trimmed)
                val text = backendApi.completeText(backendUrl, backendToken, AGENTS_MD_SYSTEM, context)
                _uiState.update { it.copy(isGenerating = false, draft = text.trim()) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to modify AGENTS.md", e)
                _uiState.update { it.copy(isGenerating = false, generateError = e.message ?: "修改失败") }
            }
        }
    }

    /** 构建给模型的用户上下文：目录名 + 顶层结构 + 现有内容。 */
    private suspend fun buildContext(): String {
        val existing = _uiState.value.existingContent
        val structure = runCatching { api.listDirectory(connection, "", directory) }.getOrNull()
        val topLevel = structure
            ?.take(40)
            ?.joinToString(", ") { it.name + if (it.type == "directory") "/" else "" }
            .orEmpty()
        val dirName = directory.trimEnd('/').substringAfterLast('/').ifBlank { directory }
        return buildString {
            append("项目目录：$dirName\n")
            if (topLevel.isNotBlank()) append("顶层内容：$topLevel\n")
            if (existing.isNotBlank()) {
                append("\n现有 AGENTS.md 内容如下，请在此基础上完善（保留仍有效的内容）：\n")
                append("```\n").append(existing).append("\n```\n")
            } else {
                append("\n请为该项目生成一份全新的 AGENTS.md。\n")
            }
        }
    }

    /** 构建指令式修改的上下文：当前草稿 + 用户自然语言指令。 */
    private fun buildModifyContext(instruction: String): String {
        val current = _uiState.value.draft.ifBlank { _uiState.value.existingContent }
        return buildString {
            if (current.isNotBlank()) {
                append("当前 AGENTS.md 内容如下：\n```\n").append(current).append("\n```\n\n")
            }
            append("用户修改指令：").append(instruction).append("\n\n")
            append("请根据上述指令修改 AGENTS.md，输出修改后的完整 Markdown 内容。")
        }
    }

    /** 更新可编辑的草稿内容。 */
    fun updateDraft(text: String) {
        _uiState.update { it.copy(draft = text) }
    }

    /** 进入编辑模式：把现有内容加载到草稿，供手动修改。 */
    fun startEdit() {
        _uiState.update { it.copy(draft = it.existingContent) }
    }

    /** 取消编辑：清空草稿，回到预览 / 空状态。 */
    fun cancelEdit() {
        _uiState.update { it.copy(draft = "", generateError = null) }
    }

    /** 把草稿写回项目根 AGENTS.md。 */
    fun saveDraft() {
        val content = _uiState.value.draft
        if (content.isBlank()) return
        if (_uiState.value.saveState.status == FileSaveStatus.Saving) return
        viewModelScope.launch {
            _uiState.update { it.copy(saveState = FileSaveState(status = FileSaveStatus.Saving)) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val encoded = Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))
                    val path = shellQuote(AGENTS_FILE)
                    var first = true
                    var offset = 0
                    var last = ShellCommandResult(0, "")
                    while (offset < encoded.length) {
                        val end = minOf(offset + 3000, encoded.length)
                        val chunk = encoded.substring(offset, end)
                        val redirect = if (first) ">" else ">>"
                        last = ptySession.runCommandResult("printf '%s' '$chunk' | base64 -d $redirect $path")
                        if (last.exitCode != 0) break
                        first = false
                        offset = end
                    }
                    last
                }
            }
            result.onSuccess { r ->
                if (r.exitCode == 0) {
                    _uiState.update {
                        it.copy(
                            saveState = FileSaveState(status = FileSaveStatus.Saved),
                            exists = true,
                            existingContent = content,
                            draft = "",
                        )
                    }
                } else {
                    _uiState.update { it.copy(saveState = FileSaveState(status = FileSaveStatus.Error, message = r.output.ifBlank { null })) }
                }
            }.onFailure { e ->
                Log.e(TAG, "Failed to save AGENTS.md", e)
                _uiState.update { it.copy(saveState = FileSaveState(status = FileSaveStatus.Error, message = e.message)) }
            }
        }
    }

    /** shell 单引号转义：包裹路径，内部单引号按 `'\''` 转义。 */
    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
