/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : WorkspaceFilesViewModel.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.files

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.FileContent
import org.hiylo.starburst.data.api.FileNode
import org.hiylo.starburst.data.api.OpenCodeApi
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.api.SuggestionProvider
import org.hiylo.starburst.data.api.listDirectory
import org.hiylo.starburst.data.api.readFile
import org.hiylo.starburst.data.repository.SettingsRepository
import org.hiylo.starburst.data.shell.ServerShellRegistry
import org.hiylo.starburst.data.shell.ShellCommandResult
import org.hiylo.starburst.data.sync.LocalSyncSecretStore
import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.ml.MnnLlm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLConnection
import java.util.Base64
import javax.inject.Inject

data class WorkspaceFilePreview(
    val node: FileNode,
    val content: FileContent,
)

data class WorkspaceFilesUiState(
    val directory: String = "",
    val currentPath: String = "",
    val entries: List<FileNode> = emptyList(),
    val preview: WorkspaceFilePreview? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** 文件保存状态的阶段。 */
enum class FileSaveStatus { Idle, Saving, Saved, Error }

/** 文件编辑保存状态：阶段 + 失败时的错误信息。 */
data class FileSaveState(
    val status: FileSaveStatus = FileSaveStatus.Idle,
    val message: String? = null,
)

/** 单文件 diff 的展示状态：内容、是否被截断、加载中与错误信息。 */
data class WorkspaceDiffState(
    val content: String = "",
    val truncated: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/** 编辑器内可执行的 AI 动作。 */
enum class AiAction { Explain, Refactor, WriteTests }

/** 编辑器内可执行的端侧（离线）代码辅助动作，完全依赖设备上的 MNN 模型。 */
enum class OfflineAction { Complete, Rewrite }

/** 编辑器 AI 动作的状态：当前动作、是否加载中、结果文本与错误信息。 */
data class AiEditState(
    val action: AiAction? = null,
    val loading: Boolean = false,
    val result: String? = null,
    val error: String? = null,
)

/** 编辑器端侧代码辅助动作的状态：当前动作、是否加载中、结果文本与错误信息。 */
data class OfflineEditState(
    val action: OfflineAction? = null,
    val loading: Boolean = false,
    val result: String? = null,
    val error: String? = null,
)

/** AI 生成内部结果：文本与「是否至少有一个模型可用」。 */
private data class AiRunResult(
    val text: String?,
    val anyModelAvailable: Boolean,
)

/** diff 视图单次最多展示的行数，超出部分截断并提示。 */
internal const val MAX_DIFF_LINES = 500

internal enum class WorkspaceFileKind {
    Directory,
    Code,
    Config,
    Image,
    Document,
    Pdf,
    Archive,
    Audio,
    Video,
    Table,
    Generic,
}

internal fun workspaceFileKind(node: FileNode): WorkspaceFileKind {
    if (node.type == "directory") return WorkspaceFileKind.Directory
    val name = node.name.lowercase()
    val extension = name.substringAfterLast('.', "")
    return when {
        extension in CODE_EXTENSIONS -> WorkspaceFileKind.Code
        extension in CONFIG_EXTENSIONS || name in CONFIG_FILENAMES -> WorkspaceFileKind.Config
        extension in IMAGE_EXTENSIONS -> WorkspaceFileKind.Image
        extension == "pdf" -> WorkspaceFileKind.Pdf
        extension in ARCHIVE_EXTENSIONS -> WorkspaceFileKind.Archive
        extension in AUDIO_EXTENSIONS -> WorkspaceFileKind.Audio
        extension in VIDEO_EXTENSIONS -> WorkspaceFileKind.Video
        extension in TABLE_EXTENSIONS -> WorkspaceFileKind.Table
        extension in DOCUMENT_EXTENSIONS -> WorkspaceFileKind.Document
        else -> WorkspaceFileKind.Generic
    }
}

internal fun workspaceSyntaxLanguage(filename: String): String? {
    return when (filename.lowercase().substringAfterLast('.', "")) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "js", "jsx", "mjs", "cjs" -> "javascript"
        "ts", "tsx" -> "typescript"
        "py" -> "python"
        "rb" -> "ruby"
        "go" -> "go"
        "rs" -> "rust"
        "c", "h" -> "c"
        "cpp", "cc", "cxx", "hpp" -> "cpp"
        "cs" -> "csharp"
        "swift" -> "swift"
        "sh", "bash", "zsh", "fish" -> "bash"
        "json", "jsonl" -> "json"
        "xml", "svg" -> "xml"
        "html", "htm" -> "html"
        "css", "scss", "sass", "less" -> "css"
        "yaml", "yml" -> "yaml"
        "sql" -> "sql"
        "md", "markdown" -> "markdown"
        else -> null
    }
}

internal fun isWorkspaceMarkdownFile(filename: String): Boolean =
    filename.substringAfterLast('.', "").lowercase() in setOf("md", "markdown")

internal fun workspaceParentPath(path: String): String = path.trimEnd('/').substringBeforeLast('/', "")

internal fun workspaceFileBytes(content: FileContent): ByteArray? {
    if (content.type == "binary" && content.content.isEmpty()) return null
    return runCatching {
        if (content.encoding == "base64") {
            Base64.getMimeDecoder().decode(content.content)
        } else {
            content.content.toByteArray(Charsets.UTF_8)
        }
    }.getOrNull()
}

internal fun workspaceFileMimeType(node: FileNode, content: FileContent): String {
    return content.mimeType
        ?: URLConnection.guessContentTypeFromName(node.name)
        ?: if (content.type == "text") "text/plain" else "application/octet-stream"
}

@HiltViewModel
class WorkspaceFilesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val settingsRepository: SettingsRepository,
    private val shellRegistry: ServerShellRegistry,
    private val suggestionProvider: SuggestionProvider,
    private val secretStore: LocalSyncSecretStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val connection = ServerConnection.from(
        url = savedStateHandle.get<String>("serverUrl").orEmpty(),
        username = savedStateHandle.get<String>("username").orEmpty().ifBlank { "opencode" },
        password = savedStateHandle.get<String>("password").orEmpty().ifBlank { null },
    )
    private val directory = savedStateHandle.get<String>("directory").orEmpty()
    private val serverId = savedStateHandle.get<String>("serverId").orEmpty().ifBlank { connection.baseUrl }
    private val _uiState = MutableStateFlow(WorkspaceFilesUiState(directory = directory))
    val uiState = _uiState.asStateFlow()
    private val _saveResults = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val saveResults = _saveResults.asSharedFlow()
    val wordWrap = settingsRepository.codeWordWrap.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false,
    )
    private val _editing = MutableStateFlow(false)
    val editing = _editing.asStateFlow()
    private val _editContent = MutableStateFlow<TextFieldValue?>(null)
    val editContent = _editContent.asStateFlow()
    private val _saveState = MutableStateFlow(FileSaveState())
    val saveState = _saveState.asStateFlow()
    private val _diffVisible = MutableStateFlow(false)
    val diffVisible = _diffVisible.asStateFlow()
    private val _diffState = MutableStateFlow(WorkspaceDiffState())
    val diffState = _diffState.asStateFlow()
    private val _aiState = MutableStateFlow(AiEditState())
    val aiState = _aiState.asStateFlow()
    private val _offlineState = MutableStateFlow(OfflineEditState())
    val offlineState = _offlineState.asStateFlow()
    private var loadJob: Job? = null
    private var aiJob: Job? = null
    private var offlineJob: Job? = null

    /** 连接级共享 PTY 会话：按 server 复用，与 Git 页、服务器管理页共用同一条 PTY。 */
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
        loadDirectory("")
    }

    fun loadDirectory(path: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(currentPath = path, preview = null, isLoading = true, error = null)
            }
            try {
                val entries = api.listDirectory(connection, path = path, directory = directory)
                _uiState.update { it.copy(entries = entries, isLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list workspace directory", e)
                _uiState.update { it.copy(entries = emptyList(), isLoading = false, error = e.message) }
            }
        }
    }

    fun open(node: FileNode) {
        if (node.type == "directory") {
            loadDirectory(node.path)
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val content = api.readFile(connection, node.path, directory)
                _uiState.update {
                    it.copy(preview = WorkspaceFilePreview(node, content), isLoading = false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read workspace file", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun navigateUp(): Boolean {
        val state = _uiState.value
        if (state.preview != null) {
            _uiState.update { it.copy(preview = null, error = null) }
            return true
        }
        if (state.currentPath.isNotEmpty()) {
            loadDirectory(workspaceParentPath(state.currentPath))
            return true
        }
        return false
    }

    fun retry() {
        val preview = _uiState.value.preview
        if (preview != null) open(preview.node) else loadDirectory(_uiState.value.currentPath)
    }

    fun setWordWrap(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCodeWordWrap(enabled) }
    }

    fun savePreview(uri: Uri) {
        val preview = _uiState.value.preview ?: return
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val bytes = workspaceFileBytes(preview.content) ?: return@withContext false
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("Unable to open destination")
                }.onFailure { Log.e(TAG, "Failed to save workspace file", it) }.isSuccess
            }
            _saveResults.emit(saved)
        }
    }

    /**
     * 进入编辑模式：把当前文本类预览内容填入编辑框。二进制/图片文件不提供编辑。
     */
    fun startEdit() {
        val preview = _uiState.value.preview ?: return
        if (!isTextPreview(preview)) return
        _editContent.value = TextFieldValue(preview.content.content)
        _editing.value = true
        _saveState.value = FileSaveState()
    }

    /**
     * 更新正在编辑的文本内容与选区。
     *
     * @param value 编辑框最新的文本与选区
     */
    fun updateEditContent(value: TextFieldValue) {
        _editContent.value = value
    }

    /** 取消编辑，丢弃未保存的修改。 */
    fun cancelEdit() {
        _editing.value = false
        _editContent.value = null
        _saveState.value = FileSaveState()
    }

    /**
     * 保存编辑内容：将文本 base64 编码后通过共享 PTY 写回服务器，成功后重新读取刷新预览。
     */
    fun saveEdit() {
        val preview = _uiState.value.preview ?: return
        val content = _editContent.value?.text ?: return
        if (_saveState.value.status == FileSaveStatus.Saving) return
        viewModelScope.launch {
            _saveState.value = FileSaveState(status = FileSaveStatus.Saving)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val encoded = Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))
                    val path = shellQuote(preview.node.path)
                    // 分块写入：每条命令的 base64 片段控制在 3000 字符内（4 的倍数，可独立解码），
                    // 避免超大文件单条命令超出 PTY 行缓冲上限。
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
                    _editing.value = false
                    _editContent.value = null
                    _saveState.value = FileSaveState(status = FileSaveStatus.Saved)
                    open(preview.node)
                } else {
                    _saveState.value = FileSaveState(status = FileSaveStatus.Error, message = r.output.ifBlank { null })
                }
            }.onFailure { e ->
                Log.e(TAG, "Failed to save workspace file", e)
                _saveState.value = FileSaveState(status = FileSaveStatus.Error, message = e.message)
            }
        }
    }

    /**
     * 展示当前文件的变更 diff：
     * - 编辑态：对比编辑框中的未保存内容与服务器上已保存的原始内容（写入临时文件后
     *   用 `git diff --no-index` / `diff -u` 计算）。
     * - 浏览态：对比工作区文件与 HEAD/暂存区（`git diff HEAD --` / `git diff --cached --`）。
     */
    fun showDiff() {
        val preview = _uiState.value.preview ?: return
        if (!isTextPreview(preview)) return
        if (_diffState.value.isLoading) return
        val editing = _editing.value
        val pending = _editContent.value?.text
        viewModelScope.launch {
            _diffVisible.value = true
            _diffState.value = WorkspaceDiffState(isLoading = true)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (editing && pending != null) diffPending(preview, pending)
                    else diffWorkingTree(preview)
                }
            }
            result.onSuccess { raw ->
                val lines = raw.lines()
                _diffState.value = WorkspaceDiffState(
                    content = lines.take(MAX_DIFF_LINES).joinToString("\n"),
                    truncated = lines.size > MAX_DIFF_LINES,
                    isLoading = false,
                )
            }.onFailure { e ->
                Log.e(TAG, "Failed to compute file diff", e)
                _diffState.value = WorkspaceDiffState(error = e.message, isLoading = false)
            }
        }
    }

    /** 关闭 diff 视图，回到编辑/浏览界面。 */
    fun closeDiff() {
        _diffVisible.value = false
        _diffState.value = WorkspaceDiffState()
    }

    /**
     * 在编辑器内执行 AI 动作：取当前选区（无选区时回退为整个文件）作为输入，
     * 优先调用配置的云端 LLM，失败或未配置时回退端侧 MNN 模型。结果写入 [AiEditState]。
     *
     * @param action 要执行的 AI 动作（解释 / 重构 / 写测试）
     */
    fun runAiAction(action: AiAction) {
        val preview = _uiState.value.preview ?: return
        if (!isTextPreview(preview)) return
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _aiState.value = AiEditState(action = action, loading = true)
            val (selected, whole) = currentSelection()
            val code = selected.ifBlank { whole }
            val language = workspaceSyntaxLanguage(preview.node.name)
            val result = withContext(Dispatchers.IO) {
                generateAiText(action, code, language)
            }
            _aiState.value = when {
                result.text != null -> AiEditState(action = action, result = result.text)
                result.anyModelAvailable -> AiEditState(action = action, error = aiFailedMessage())
                else -> AiEditState(action = action, error = aiNoModelMessage())
            }
        }
    }

    /**
     * 应用 AI 结果：把结果写入编辑框并进入编辑模式，随后打开 diff 预览
     * （对比服务器已保存的原文件与 AI 结果）。用户确认后通过既有的「保存」按钮
     * （[saveEdit] 的 chunked base64 PTY 写回路径）落盘。
     */
    fun applyAiResult() {
        val result = _aiState.value.result ?: return
        if (_uiState.value.preview == null) return
        _editContent.value = TextFieldValue(result)
        _editing.value = true
        _saveState.value = FileSaveState()
        _aiState.value = AiEditState()
        showDiff()
    }

    /** 关闭 AI 结果对话框，并取消仍在进行中的生成任务。 */
    fun dismissAiResult() {
        aiJob?.cancel()
        _aiState.value = AiEditState()
    }

    /**
     * 在编辑器内执行端侧（离线）代码辅助动作：完全通过端侧 MNN 模型生成，
     * 不访问任何服务器。取当前选区（无选区时回退为整个文件）作为输入。
     * 模型未加载时给出不可用提示，生成失败时给出失败提示。
     *
     * @param action 要执行的端侧动作（补全 / 重写）
     */
    fun runOfflineAction(action: OfflineAction) {
        val preview = _uiState.value.preview ?: return
        if (!isTextPreview(preview)) return
        offlineJob?.cancel()
        offlineJob = viewModelScope.launch {
            _offlineState.value = OfflineEditState(action = action, loading = true)
            val (selected, whole) = currentSelection()
            val code = selected.ifBlank { whole }
            val language = workspaceSyntaxLanguage(preview.node.name)
            val result = withContext(Dispatchers.IO) {
                generateOfflineText(action, code, language)
            }
            _offlineState.value = when {
                result.text != null -> OfflineEditState(action = action, result = result.text)
                result.anyModelAvailable -> OfflineEditState(action = action, error = offlineFailedMessage())
                else -> OfflineEditState(action = action, error = offlineUnavailableMessage())
            }
        }
    }

    /**
     * 应用端侧结果：把结果写入编辑框并进入编辑模式，随后打开 diff 预览
     * （对比服务器已保存的原文件与端侧结果），用户确认后通过既有的「保存」按钮落盘。
     */
    fun applyOfflineResult() {
        val result = _offlineState.value.result ?: return
        if (_uiState.value.preview == null) return
        _editContent.value = TextFieldValue(result)
        _editing.value = true
        _saveState.value = FileSaveState()
        _offlineState.value = OfflineEditState()
        showDiff()
    }

    /** 关闭端侧结果对话框，并取消仍在进行中的生成任务。 */
    fun dismissOfflineResult() {
        offlineJob?.cancel()
        _offlineState.value = OfflineEditState()
    }

    /** 返回当前编辑框的选中文本（未选中时为空）与完整文本。 */
    private fun currentSelection(): Pair<String, String> {
        val preview = _uiState.value.preview
        val whole = preview?.content?.content.orEmpty()
        val field = _editContent.value
        val text = field?.text ?: whole
        val selection = field?.selection
        val selected = if (selection != null && !selection.collapsed) {
            val start = selection.min.coerceIn(0, text.length)
            val end = selection.max.coerceIn(0, text.length)
            if (end > start) text.substring(start, end) else ""
        } else {
            ""
        }
        return selected to text
    }

    /**
     * 生成 AI 回复：优先云端 LLM，失败或未配置时回退端侧 MNN 模型。
     * 返回文本与「是否至少有一个模型可用」，用于区分「无模型」与「生成失败」。
     */
    private suspend fun generateAiText(action: AiAction, code: String, language: String?): AiRunResult {
        var attempted = false
        val baseUrl = settingsRepository.llmProviderBaseUrl.first()
        val model = settingsRepository.llmProviderModel.first()
        if (baseUrl.isNotBlank() && model.isNotBlank()) {
            attempted = true
            val apiKey = secretStore.get(LocalSyncSecretStore.SecretKey.LLM_PROVIDER_API_KEY).orEmpty()
            val text = runCatching {
                suggestionProvider.complete(
                    SuggestionProvider.Config(baseUrl = baseUrl, apiKey = apiKey, model = model),
                    systemPrompt = aiSystemPrompt(action, language),
                    userContent = aiUserContent(code, language),
                    maxTokens = AI_MAX_TOKENS,
                ).trim().takeIf { it.isNotBlank() }
            }.getOrNull()
            if (text != null) return AiRunResult(text, true)
        }
        val loaded = MnnLlm.ensureLoaded(context)
        if (!loaded) return AiRunResult(null, attempted)
        MnnLlm.reset()
        val text = MnnLlm.generate(aiMnnPrompt(action, code, language), maxTokens = AI_MNN_MAX_TOKENS)
            .trim().takeIf { it.isNotBlank() }
        return AiRunResult(text, true)
    }

    private fun aiNoModelMessage(): String = context.getString(R.string.ai_no_model)

    private fun aiFailedMessage(): String = context.getString(R.string.ai_failed)

    private fun offlineUnavailableMessage(): String = context.getString(R.string.offline_model_unavailable)

    private fun offlineFailedMessage(): String = context.getString(R.string.offline_failed)

    /**
     * 仅通过端侧 MNN 模型生成代码辅助结果，不访问任何服务器。
     * 返回文本与「模型是否可用」，用于区分「模型不可用」与「生成失败」。
     * 补全动作会把续写内容追加到原代码后，作为完整结果返回。
     */
    private suspend fun generateOfflineText(action: OfflineAction, code: String, language: String?): AiRunResult {
        val loaded = MnnLlm.ensureLoaded(context)
        if (!loaded) return AiRunResult(null, false)
        MnnLlm.reset()
        val generated = MnnLlm.generate(offlineMnnPrompt(action, code, language), maxTokens = AI_MNN_MAX_TOKENS)
            .trim().takeIf { it.isNotBlank() }
        if (generated == null) return AiRunResult(null, true)
        val text = if (action == OfflineAction.Complete) code + "\n" + generated else generated
        return AiRunResult(text, true)
    }

    /** 构造端侧模型的系统指令（按当前语言本地化）。 */
    private fun offlineSystemPrompt(action: OfflineAction, language: String?): String {
        val isZh = context.resources.configuration.locales[0].language == "zh"
        return when (action) {
            OfflineAction.Complete -> if (isZh) {
                "你是一名资深软件工程师。请续写下面的代码，补全剩余实现。" +
                    "只输出续写部分，不要使用 markdown 代码块，不要解释。"
            } else {
                "You are a senior software engineer. Continue the provided code and complete the " +
                    "remaining implementation. Output ONLY the continuation, no markdown fences, " +
                    "no explanation."
            }
            OfflineAction.Rewrite -> if (isZh) {
                "你是一名资深软件工程师。请重写下面的代码，提升可读性、正确性与可维护性。" +
                    "只输出重写后的代码，不要使用 markdown 代码块，不要解释。"
            } else {
                "You are a senior software engineer. Rewrite the following code for clarity, " +
                    "correctness and maintainability. Output ONLY the rewritten code, no markdown " +
                    "fences, no explanation."
            }
        }
    }

    /** 构造端侧 MNN 模型的单条提示词（系统指令 + 代码拼接）。 */
    private fun offlineMnnPrompt(action: OfflineAction, code: String, language: String?): String =
        offlineSystemPrompt(action, language) + "\n\n" + aiUserContent(code, language)

    /** 构造云端 LLM 的系统指令（按当前语言本地化）。 */
    private fun aiSystemPrompt(action: AiAction, language: String?): String {
        val isZh = context.resources.configuration.locales[0].language == "zh"
        return when (action) {
            AiAction.Explain -> if (isZh) {
                "你是一名资深软件工程师。请清晰、简洁地解释下面这段代码的作用、关键逻辑与值得注意的边界情况。"
            } else {
                "You are a senior software engineer. Explain the provided code clearly and " +
                    "concisely, covering its purpose, key logic, and notable edge cases."
            }
            AiAction.Refactor -> if (isZh) {
                "你是一名资深软件工程师。请重构下面的代码，提升可读性、正确性与可维护性。" +
                    "只输出重构后的代码，不要使用 markdown 代码块，不要解释。"
            } else {
                "You are a senior software engineer. Refactor the following code for clarity, " +
                    "correctness and maintainability. Output ONLY the refactored code, no markdown " +
                    "fences, no explanation."
            }
            AiAction.WriteTests -> if (isZh) {
                "你是一名资深软件工程师。请为下面的代码编写聚焦的单元测试。" +
                    "只输出测试代码，不要使用 markdown 代码块，不要解释。"
            } else {
                "You are a senior software engineer. Write focused unit tests for the following " +
                    "code. Output ONLY the test code, no markdown fences, no explanation."
            }
        }
    }

    /** 构造云端 LLM 的用户内容：以代码围栏包裹选中代码。 */
    private fun aiUserContent(code: String, language: String?): String {
        val lang = language ?: ""
        val snippet = code.take(AI_MAX_CODE_CHARS)
        return "```$lang\n$snippet\n```"
    }

    /** 构造端侧 MNN 模型的单条提示词（系统指令 + 代码拼接）。 */
    private fun aiMnnPrompt(action: AiAction, code: String, language: String?): String {
        val snippet = code.take(AI_MAX_CODE_CHARS)
        return aiSystemPrompt(action, language) + "\n\n" + aiUserContent(snippet, language)
    }

    /**
     * 计算编辑态 diff：把未保存内容 base64 写入临时文件，与服务器上的原始文件做
     * `git diff --no-index`（git 不可用时退化为 `diff -u`），结束后清理临时文件。
     */
    private suspend fun diffPending(preview: WorkspaceFilePreview, pending: String): String {
        val saved = preview.content.content
        if (pending == saved) return ""
        val target = preview.node.absolute ?: preview.node.path
        val temp = "$target.starburst-diff-${System.currentTimeMillis()}"
        return try {
            val encoded = Base64.getEncoder().encodeToString(pending.toByteArray(Charsets.UTF_8))
            var first = true
            var offset = 0
            var ok = true
            while (offset < encoded.length) {
                val end = minOf(offset + DIFF_CHUNK_SIZE, encoded.length)
                val chunk = encoded.substring(offset, end)
                val redirect = if (first) ">" else ">>"
                val r = ptySession.runCommandResult("printf '%s' '$chunk' | base64 -d $redirect ${shellQuote(temp)}")
                if (r.exitCode != 0) {
                    ok = false
                    break
                }
                first = false
                offset = end
            }
            if (ok) ptySession.runCommand(noIndexDiffCmd(target, temp), timeoutMs = 60_000) else ""
        } finally {
            runCatching { ptySession.runCommand("rm -f ${shellQuote(temp)}") }
        }
    }

    /** 计算浏览态 diff：展示文件相对 HEAD（或暂存区）的变更。 */
    private suspend fun diffWorkingTree(preview: WorkspaceFilePreview): String {
        val path = preview.node.absolute ?: preview.node.path
        val root = directory.trimEnd('/').ifBlank { workspaceParentPath(path) }
        val git = "git -c color.ui=false --no-pager -C ${shellQuote(root)}"
        val head = ptySession.runCommand("$git diff HEAD -- ${shellQuote(path)}", timeoutMs = 60_000)
        if (head.isNotBlank()) return head.filterGitError()
        val cached = ptySession.runCommand("$git diff --cached -- ${shellQuote(path)}", timeoutMs = 60_000)
        return cached.filterGitError()
    }

    /** 过滤 git 致命错误输出（如非仓库目录），避免把错误文本当作 diff 展示。 */
    private fun String.filterGitError(): String =
        if (lineSequence().any { it.startsWith("fatal:") || it.startsWith("error:") }) "" else this

    /** 构造 `git diff --no-index`（git 不可用时退化为 `diff -u`）的命令串。 */
    private fun noIndexDiffCmd(old: String, new: String): String =
        "if command -v git >/dev/null 2>&1; then git -c color.ui=false --no-pager diff --no-index -- " +
            "${shellQuote(old)} ${shellQuote(new)}; else diff -u ${shellQuote(old)} ${shellQuote(new)}; fi"

    /** 判断预览是否为可编辑的文本类文件（排除二进制与图片）。 */
    private fun isTextPreview(preview: WorkspaceFilePreview): Boolean {
        if (workspaceFileBytes(preview.content) == null) return false
        return !workspaceFileMimeType(preview.node, preview.content).startsWith("image/")
    }

    /** shell 单引号转义：包裹路径，内部单引号按 `'\''` 转义。 */
    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private companion object {
        const val TAG = "WorkspaceFilesVM"

        /** 写入临时文件时每条命令的 base64 片段上限（4 的倍数，可独立解码）。 */
        const val DIFF_CHUNK_SIZE = 3000

        /** 云端 LLM 生成的最大 token 数。 */
        const val AI_MAX_TOKENS = 4096

        /** 端侧 MNN 模型生成的最大 token 数。 */
        const val AI_MNN_MAX_TOKENS = 1024

        /** 送入模型的代码片段最大字符数，超出截断。 */
        const val AI_MAX_CODE_CHARS = 32_000
    }
}

private val CODE_EXTENSIONS = setOf(
    "kt", "kts", "java", "js", "jsx", "mjs", "cjs", "ts", "tsx", "py", "rb", "go", "rs",
    "c", "h", "cpp", "cc", "cxx", "hpp", "cs", "swift", "sh", "bash", "zsh", "fish", "sql",
    "html", "htm", "css", "scss", "sass", "less", "vue", "svelte", "dart", "lua", "php",
)
private val CONFIG_EXTENSIONS = setOf(
    "json", "jsonl", "xml", "yaml", "yml", "toml", "ini", "conf", "config", "properties", "gradle",
)
private val CONFIG_FILENAMES = setOf(
    ".env", ".gitignore", ".gitattributes", ".editorconfig", "dockerfile", "makefile",
)
private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico", "avif")
private val ARCHIVE_EXTENSIONS = setOf("zip", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar", "jar", "apk")
private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "ogg", "m4a", "flac", "aac")
private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv", "mov", "avi", "m4v")
private val TABLE_EXTENSIONS = setOf("csv", "tsv", "xls", "xlsx")
private val DOCUMENT_EXTENSIONS = setOf("txt", "md", "markdown", "rtf", "doc", "docx", "odt", "log")
