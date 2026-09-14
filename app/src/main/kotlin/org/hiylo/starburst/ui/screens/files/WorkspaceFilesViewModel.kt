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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.hiylo.starburst.data.api.FileContent
import org.hiylo.starburst.data.api.FileNode
import org.hiylo.starburst.data.api.OpenCodeApi
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.api.listDirectory
import org.hiylo.starburst.data.api.readFile
import org.hiylo.starburst.data.repository.SettingsRepository
import org.hiylo.starburst.data.shell.ServerShellRegistry
import org.hiylo.starburst.data.shell.ShellCommandResult
import org.hiylo.starburst.logging.AppLogger as Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val _editContent = MutableStateFlow<String?>(null)
    val editContent = _editContent.asStateFlow()
    private val _saveState = MutableStateFlow(FileSaveState())
    val saveState = _saveState.asStateFlow()
    private var loadJob: Job? = null

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
        _editContent.value = preview.content.content
        _editing.value = true
        _saveState.value = FileSaveState()
    }

    /**
     * 更新正在编辑的文本内容。
     *
     * @param text 编辑框最新内容
     */
    fun updateEditContent(text: String) {
        _editContent.value = text
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
        val content = _editContent.value ?: return
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

    /** 判断预览是否为可编辑的文本类文件（排除二进制与图片）。 */
    private fun isTextPreview(preview: WorkspaceFilePreview): Boolean {
        if (workspaceFileBytes(preview.content) == null) return false
        return !workspaceFileMimeType(preview.node, preview.content).startsWith("image/")
    }

    /** shell 单引号转义：包裹路径，内部单引号按 `'\''` 转义。 */
    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private companion object {
        const val TAG = "WorkspaceFilesVM"
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
