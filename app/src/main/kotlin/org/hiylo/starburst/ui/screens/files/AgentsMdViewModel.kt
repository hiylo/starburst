/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : AgentsMdViewModel.kt
 * Date : 2026/09/13 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.starburst.ui.screens.files

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.BackendApi
import org.hiylo.starburst.data.api.OpenCodeApi
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.api.listDirectory
import org.hiylo.starburst.data.api.readFile
import org.hiylo.starburst.data.repository.ServerConnectionStateRepository
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


/** 扫描项目结构时识别为「技术栈标识文件」的文件名。 */
private val TECH_STACK_FILES = setOf(
    "go.mod", "go.sum", "pom.xml", "package.json", "pnpm-lock.yaml",
    "build.gradle.kts", "build.gradle", "settings.gradle.kts", "settings.gradle",
    "Cargo.toml", "pyproject.toml", "requirements.txt", "composer.json",
    "mix.exs", "pubspec.yaml", "gradlew", "manage.py", "mkdocs.yml",
)

/** 扫描结构时跳过的目录（构建产物 / VCS / IDE 噪音）。 */
private val SKIP_DIRS = setOf(
    ".git", ".gradle", ".idea", ".kotlin", "build", "node_modules", "dist",
    "out", "target", ".venv", "venv", "__pycache__", ".cxx", ".direnv", "release-notes",
)

/** 结构扫描最大递归深度与每层最大展开目录数（控制 token 预算）。 */
private const val MAX_SCAN_DEPTH = 4
private const val MAX_DIRS_PER_LEVEL = 10

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
    private val connectionStateRepository: ServerConnectionStateRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val connection = ServerConnection.from(
        url = savedStateHandle.get<String>("serverUrl").orEmpty(),
        username = savedStateHandle.get<String>("username").orEmpty().ifBlank { "opencode" },
        password = savedStateHandle.get<String>("password").orEmpty().ifBlank { null },
    )
    private val directory = savedStateHandle.get<String>("directory").orEmpty()
    private val serverId = savedStateHandle.get<String>("serverId").orEmpty().ifBlank { connection.baseUrl }

    /**
     * 实际用于 PTY/文件操作的连接：优先复用连接服务解析后的直连地址（SSH 隧道
     * `127.0.0.1:localPort`），否则回退到导航传入的 `serverUrl`。蜂窝/VPN 下裸 `serverUrl`
     * 常不可达，会导致 AGENTS.md 读写请求到不了服务器。
     */
    private val effectiveConn: ServerConnection
        get() = connectionStateRepository.resolvedDirectConnections.value[serverId] ?: connection

    private val _uiState = MutableStateFlow(AgentsMdUiState(directory = directory))
    val uiState: StateFlow<AgentsMdUiState> = _uiState.asStateFlow()

    private var backendUrl = ""
    private var backendToken = ""

    private var ptySessionAcquired = false
    private val ptySession by lazy {
        ptySessionAcquired = true
        shellRegistry.acquire(serverId, api, effectiveConn, directory)
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
            backendToken = server?.backendResolvedToken.orEmpty()
            detect()
        }
    }

    /** 检测项目根是否存在 AGENTS.md，并读取现有内容。 */
    fun detect() {
        viewModelScope.launch {
            _uiState.update { it.copy(exists = null, detectError = null) }
            try {
                val content = api.readFile(effectiveConn, AGENTS_FILE, directory)
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

    /** 生成 AGENTS.md 时喂给模型的 system 指令（按当前语言返回）。 */
    private fun agentsMdSystemPrompt(): String {
        val isZh = context.resources.configuration.locales[0].language == "zh"
        return if (isZh) {
            "你是一名资深软件工程师，负责为项目编写或完善 AGENTS.md 文件。\n" +
                "AGENTS.md 是 AI 编码助手读取的项目级规则文档，内容应包含：\n" +
                "1. 项目简介与技术栈；\n" +
                "2. 构建、测试、运行的常用命令；\n" +
                "3. 代码风格与目录结构约定；\n" +
                "4. 开发/提交规范与其他 AI 需要遵守的约定。\n\n" +
                "要求：\n" +
                "- 直接输出 AGENTS.md 的完整 Markdown 内容，不要包含任何解释、前后缀或代码块围栏。\n" +
                "- 语言与用户提供的上下文保持一致（中文项目用中文）。\n" +
                "- 保持简洁、可执行、可检索，避免空话。\n" +
                "- 如果是「完善」模式，保留已有内容中仍然有效的部分，只做补充和修正。"
        } else {
            "You are a senior software engineer tasked with writing or improving an AGENTS.md file for a project.\n" +
                "AGENTS.md is a project-level rules document read by AI coding assistants. It should include:\n" +
                "1. Project overview and tech stack;\n" +
                "2. Common commands for build, test and run;\n" +
                "3. Code style and directory structure conventions;\n" +
                "4. Development/commit conventions and other rules AI should follow.\n\n" +
                "Requirements:\n" +
                "- Output ONLY the complete Markdown content of AGENTS.md — no explanations, no prefixes/suffixes, no code fences.\n" +
                "- Match the language of the user-provided context (use English for English projects).\n" +
                "- Keep it concise, actionable and searchable; avoid filler.\n" +
                "- In \"improve\" mode, preserve still-valid parts of the existing content and only supplement or correct."
        }
    }

    /** 用后端 LLM 生成（或完善）AGENTS.md 草稿。 */
    fun generate() {
        if (backendUrl.isBlank()) {
            _uiState.update { it.copy(generateError = context.getString(R.string.agents_md_no_backend_generate)) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, generateError = null) }
            try {
                val context = buildContext()
                val text = backendApi.completeText(backendUrl, backendToken, agentsMdSystemPrompt(), context)
                _uiState.update { it.copy(isGenerating = false, draft = text.trim()) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate AGENTS.md", e)
                _uiState.update { it.copy(isGenerating = false, generateError = e.message ?: context.getString(R.string.agents_md_generate_failed)) }
            }
        }
    }

    /** 用自然语言指令让 LLM 修改当前 AGENTS.md 草稿。 */
    fun modifyWithInstruction(instruction: String) {
        val trimmed = instruction.trim()
        if (trimmed.isEmpty()) return
        if (backendUrl.isBlank()) {
            _uiState.update { it.copy(generateError = context.getString(R.string.agents_md_no_backend_modify)) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, generateError = null) }
            try {
                val context = buildModifyContext(trimmed)
                val text = backendApi.completeText(backendUrl, backendToken, agentsMdSystemPrompt(), context)
                _uiState.update { it.copy(isGenerating = false, draft = text.trim()) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to modify AGENTS.md", e)
                _uiState.update { it.copy(isGenerating = false, generateError = e.message ?: context.getString(R.string.agents_md_modify_failed)) }
            }
        }
    }

    /** 构建给模型的用户上下文：目录名 + 递归项目结构 + 现有内容。 */
    private suspend fun buildContext(): String {
        val isZh = context.resources.configuration.locales[0].language == "zh"
        val existing = _uiState.value.existingContent
        val dirName = directory.trimEnd('/').substringAfterLast('/').ifBlank { directory }
        val structure = scanProjectStructure()
        return buildString {
            if (isZh) {
                append("项目目录：$dirName\n")
            } else {
                append("Project directory: $dirName\n")
            }
            if (structure.isNotBlank()) {
                if (isZh) {
                    append("项目结构与技术栈（递归扫描自代码库，必须基于以下真实结构编写，不要臆测也不许建议重复创建）：\n")
                } else {
                    append("Project structure and tech stack (recursively scanned from the codebase; base the content on this real structure — don't guess and don't suggest duplicate creation):\n")
                }
                append("```\n").append(structure.trimEnd()).append("\n```\n")
            }
            if (existing.isNotBlank()) {
                if (isZh) {
                    append("\n现有 AGENTS.md 内容如下，请在此基础上完善（保留仍有效的内容）：\n")
                } else {
                    append("\nExisting AGENTS.md content follows; improve on it (keep the parts that are still valid):\n")
                }
                append("```\n").append(existing).append("\n```\n")
            } else {
                if (isZh) {
                    append("\n请为该项目生成一份全新的 AGENTS.md。\n")
                } else {
                    append("\nPlease generate a brand-new AGENTS.md for this project.\n")
                }
            }
        }
    }

    /**
     * 递归扫描项目目录树（深度受 [MAX_SCAN_DEPTH] 限制），输出 LLM 可读的结构摘要：
     * 保留源码/配置目录骨架，并在含技术栈标识文件（build.gradle.kts / go.mod / pom.xml 等）
     * 的目录上标注。单次扫描失败自动跳过，不中断整体生成。
     */
    private suspend fun scanProjectStructure(): String {
        val sb = StringBuilder()
        suspend fun scan(relPath: String, depth: Int) {
            if (depth > MAX_SCAN_DEPTH) return
            val nodes = runCatching { api.listDirectory(effectiveConn, relPath, directory) }.getOrNull() ?: return
            val dirs = nodes.filter {
                it.type == "directory" && it.name !in SKIP_DIRS && !it.name.startsWith('.')
            }
            val files = nodes.filter { it.type != "directory" }
            val tech = files.mapNotNull { f -> TECH_STACK_FILES.firstOrNull { f.name == it } }
            val indent = "  ".repeat(depth)
            val label = if (relPath.isEmpty()) "/" else relPath
            if (tech.isNotEmpty()) {
                sb.append(indent).append(label).append("  [").append(tech.joinToString(", ")).append("]\n")
            } else {
                sb.append(indent).append(label).append("/\n")
            }
            dirs.take(MAX_DIRS_PER_LEVEL).forEach { sub ->
                scan(if (relPath.isEmpty()) sub.name else "$relPath/${sub.name}", depth + 1)
            }
        }
        scan("", 0)
        return sb.toString()
    }

    /** 构建指令式修改的上下文：真实项目结构 + 当前草稿 + 用户自然语言指令。 */
    private suspend fun buildModifyContext(instruction: String): String {
        val isZh = context.resources.configuration.locales[0].language == "zh"
        val current = _uiState.value.draft.ifBlank { _uiState.value.existingContent }
        val structure = scanProjectStructure()
        return buildString {
            if (structure.isNotBlank()) {
                if (isZh) {
                    append("项目结构（递归扫描自代码库，基于此真实结构判断，不要凭空假设目录）：\n")
                } else {
                    append("Project structure (recursively scanned from the codebase; base your judgment on this real structure, don't assume directories):\n")
                }
                append("```\n").append(structure.trimEnd()).append("\n```\n\n")
            }
            if (current.isNotBlank()) {
                if (isZh) {
                    append("当前 AGENTS.md 内容如下：\n```\n").append(current).append("\n```\n\n")
                } else {
                    append("Current AGENTS.md content:\n```\n").append(current).append("\n```\n\n")
                }
            }
            if (isZh) {
                append("用户修改指令：").append(instruction).append("\n\n")
                append("请根据上述指令与真实项目结构修改 AGENTS.md，输出修改后的完整 Markdown 内容。")
            } else {
                append("User modification instruction: ").append(instruction).append("\n\n")
                append("Modify AGENTS.md according to the instruction and the real project structure above. Output the full updated Markdown content.")
            }
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
