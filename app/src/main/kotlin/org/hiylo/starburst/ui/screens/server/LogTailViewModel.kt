/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : LogTailViewModel.kt
 * Date : 2026/09/17 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.server

import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.ui.util.launchWhileStarted
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.Lifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.OpenCodeApi
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.repository.ServerConnectionStateRepository
import org.hiylo.starburst.data.shell.ServerShellRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

private const val TAG = "LogTailViewModel"

/** 服务日志实时跟踪（tail）的默认日志文件路径，使用 `~` 由 shell 展开为用户主目录。 */
const val DEFAULT_LOG_PATH = "~/.local/share/opencode/log/opencode.log"

/**
 * 服务日志实时跟踪（tail）页面的 UI 状态。
 *
 * @property logPath 当前要跟踪的日志文件路径（可编辑）
 * @property content 已读取到的日志正文（增量追加，仅保留末尾若干行）
 * @property isStreaming 是否正在持续跟踪（轮询读取新字节）
 * @property isLoading 是否正在执行首次读取
 * @property error 读取失败（文件不存在/无权限/命令异常）时的错误信息
 */
data class LogTailUiState(
    val logPath: String = DEFAULT_LOG_PATH,
    val content: String = "",
    val isStreaming: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * 服务日志实时跟踪 ViewModel。
 *
 * 复用连接级共享 PTY 会话，以轮询方式增量读取远端日志文件：每次先 `wc -c` 获取文件字节数，
 * 首次读取最近 [INITIAL_BYTES] 字节，之后仅读取自上次偏移之后新增的字节（`tail -c +N`），
 * 从而在避免重复输出的同时实现近似 `tail -f` 的实时效果；文件被截断/轮转时自动回退重读末尾。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@HiltViewModel
class LogTailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val shellRegistry: ServerShellRegistry,
    private val connectionStateRepository: ServerConnectionStateRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val conn = ServerConnection.from(
        url = savedStateHandle.get<String>("serverUrl").orEmpty(),
        username = savedStateHandle.get<String>("username").orEmpty().ifBlank { "opencode" },
        password = savedStateHandle.get<String>("password").orEmpty().ifEmpty { null },
    )
    private val serverId = savedStateHandle.get<String>("serverId").orEmpty()
    private val directory = savedStateHandle.get<String>("directory").orEmpty()

    /**
     * 实际用于 PTY 的连接：优先复用连接服务解析后的直连地址（SSH 隧道 `127.0.0.1:localPort`），
     * 否则回退到导航传入的 `serverUrl`。蜂窝/VPN 下裸 `serverUrl` 常不可达。
     */
    private val effectiveConn: ServerConnection
        get() = connectionStateRepository.resolvedDirectConnections.value[serverId] ?: conn

    /** 连接级共享 PTY 会话：与服务器管理页、Git 页按 server 复用同一条 PTY。 */
    private var shellAcquired = false
    private val shell by lazy {
        shellAcquired = true
        shellRegistry.acquire(serverId.ifBlank { conn.baseUrl }, api, effectiveConn, directory)
    }

    private val _uiState = MutableStateFlow(LogTailUiState())
    val uiState: StateFlow<LogTailUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null

    /** 界面生命周期，用于退后台时挂起日志轮询（耗电优化）。 */
    private var lifecycle: Lifecycle? = null

    /** 由 LogTailDialog 传入 lifecycle；App 退后台时挂起日志轮询，回前台恢复。 */
    fun attachLifecycle(lifecycle: Lifecycle) {
        this.lifecycle = lifecycle
    }

    /** 已读取的日志文件字节数，用于增量读取新字节（相对文件原始字节，非清洗后文本长度）。 */
    private var bytesRead = 0L

    /** 是否已完成首次读取（建立字节偏移基准）。 */
    private var initialized = false

    override fun onCleared() {
        stop()
        if (shellAcquired) {
            shellRegistry.release(serverId.ifBlank { conn.baseUrl })
        }
        super.onCleared()
    }

    /** 更新用户输入的日志文件路径。 */
    fun setLogPath(path: String) {
        _uiState.update { it.copy(logPath = path, error = null) }
    }

    /** 启动实时跟踪：先读取末尾内容，再按固定间隔轮询新增字节。 */
    fun start() {
        val path = _uiState.value.logPath.trim()
        if (path.isBlank()) {
            _uiState.update { it.copy(error = context.getString(R.string.log_tail_path_blank)) }
            return
        }
        if (_uiState.value.isStreaming) return
        stop()
        bytesRead = 0L
        initialized = false
        _uiState.update { it.copy(isStreaming = true, isLoading = true, error = null, content = "") }
        val lf = lifecycle
        streamJob = if (lf != null) {
            viewModelScope.launchWhileStarted(lf) { tailLoop(path) }
        } else {
            viewModelScope.launch(Dispatchers.IO) { tailLoop(path) }
        }
    }

    /** 轮询体：持续增量读取远端日志，直到取消或出错（取消向上抛，配合 lifecycle 挂起/恢复）。 */
    private suspend fun tailLoop(path: String) {
        while (true) {
            try {
                pollOnce(path)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "log tail failed for $path", e)
                _uiState.update {
                    it.copy(isStreaming = false, isLoading = false, error = e.message ?: e.javaClass.simpleName)
                }
                break
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    /** 停止实时跟踪（可随时取消，不阻塞主线程）。 */
    fun stop() {
        streamJob?.cancel()
        streamJob = null
        _uiState.update { it.copy(isStreaming = false, isLoading = false) }
    }

    /** 单次轮询：获取文件大小并增量读取新增字节，或按需重读末尾。 */
    private suspend fun pollOnce(path: String) {
        val sizeResult = shell.runCommandResult("wc -c < ${quotePath(path)}", timeoutMs = CMD_TIMEOUT_MS)
        if (sizeResult.exitCode != 0) {
            throw IOException(sizeResult.output.ifBlank { context.getString(R.string.log_tail_empty) })
        }
        val size = sizeResult.output.trim().toLongOrNull()
            ?: throw IOException("unexpected wc output: ${sizeResult.output}")

        if (!initialized) {
            val content = shell.runCommand("tail -c $INITIAL_BYTES ${quotePath(path)}", timeoutMs = CMD_TIMEOUT_MS)
            initialized = true
            bytesRead = size
            _uiState.update { it.copy(content = content, isLoading = false) }
            return
        }

        when {
            size > bytesRead -> {
                val appended = shell.runCommand("tail -c +${bytesRead + 1} ${quotePath(path)}", timeoutMs = CMD_TIMEOUT_MS)
                bytesRead = size
                if (appended.isNotBlank()) {
                    _uiState.update { st -> st.copy(content = trimContent(st.content + appended)) }
                }
            }
            size < bytesRead -> {
                // 文件被截断或轮转：重新读取末尾。
                val content = shell.runCommand("tail -c $INITIAL_BYTES ${quotePath(path)}", timeoutMs = CMD_TIMEOUT_MS)
                bytesRead = size
                _uiState.update { it.copy(content = content) }
            }
        }
        _uiState.update { it.copy(isLoading = false) }
    }

    /** 限制内存占用：仅保留末尾 [MAX_LINES] 行。 */
    private fun trimContent(content: String): String {
        val lines = content.lineSequence().toList()
        if (lines.size <= MAX_LINES) return content
        return lines.takeLast(MAX_LINES).joinToString("\n")
    }

    /**
     * 将日志路径转成可在 `sh` 中安全使用的参数：支持 `~/` 前缀（展开为 `$HOME`），
     * 其余部分用双引号包裹并转义特殊字符，防止路径中的空白/引号破坏命令。
     */
    private fun quotePath(raw: String): String {
        val path = raw.trim()
        if (path.startsWith("~/")) return "\$HOME/" + dqQuote(path.substring(2))
        return dqQuote(path)
    }

    /** 双引号包裹并转义反斜杠、双引号、反引号与美元符号。 */
    private fun dqQuote(value: String): String =
        "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("`", "\\`")
            .replace("$", "\\$") + "\""

    private companion object {
        /** 首次读取的日志尾部字节数（约 200KB）。 */
        const val INITIAL_BYTES = 200_000L

        /** 内存中保留的最大日志行数。 */
        const val MAX_LINES = 5_000

        /** 轮询读取新日志的间隔（毫秒）。 */
        // 每轮两条 SSH 命令；VPN 高 RTT 下 1.5s 太密，放宽到 3s 仍够跟日志。
        const val POLL_INTERVAL_MS = 3_000L

        /** 单条 shell 命令的超时时间（毫秒）。 */
        const val CMD_TIMEOUT_MS = 20_000L
    }
}
