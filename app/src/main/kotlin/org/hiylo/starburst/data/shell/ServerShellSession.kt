/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ServerShellSession.kt
 * Date : 2026/09/08 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.shell

import org.hiylo.starburst.data.api.OpenCodeApi
import org.hiylo.starburst.data.api.PtySocket
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.api.createPty
import org.hiylo.starburst.data.api.openPtySocket
import org.hiylo.starburst.data.api.removePty
import org.hiylo.starburst.data.api.updatePtySize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 一条 shell 命令的执行结果：退出码与清洗后的输出文本。
 */
data class ShellCommandResult(val exitCode: Int, val output: String)

/**
 * 连接级常驻 PTY 会话。
 *
 * 为同一台服务器复用一条终端连接，所有命令通过 [Mutex] 串行执行并读取到唯一标记，
 * 避免每条命令都新建 PTY（每次都要等远端 shell 启动，开销大）。会话在首次执行命令时
 * 惰性建立连接，读流异常断开后会在下一次执行命令时自动重连。
 *
 * 会话持有一个独立的 [SupervisorJob] 作用域，生命周期由 [ServerShellRegistry] 以引用
 * 计数管理，因此可被 Git 页与服务器管理页共用，而不再绑定到某个 ViewModel。
 */
class ServerShellSession internal constructor(
    private val api: OpenCodeApi,
    private val conn: ServerConnection,
    private val directory: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var socket: PtySocket? = null
    private var ptyId: String? = null
    private var readerJob: Job? = null
    private val lock = Any()
    private val mutex = Mutex()
    private val buffer = StringBuilder()
    private val tail = StringBuilder()
    private var activeMarker: String? = null
    private var activeDeferred: CompletableDeferred<Unit>? = null
    private var connected = false

    private suspend fun connect() {
        if (connected) return
        val dirArg = directory.takeIf { it.isNotBlank() }
        val pty = api.createPty(conn, title = "shell", cwd = dirArg, directory = dirArg)
        ptyId = pty.id
        val sock = api.openPtySocket(conn, pty.id, cursor = 0, directory = dirArg)
        socket = sock
        runCatching { api.updatePtySize(conn, pty.id, cols = 240, rows = 40, directory = dirArg) }

        readerJob = scope.launch(Dispatchers.IO) {
            try {
                sock.readLoop { chunk ->
                    synchronized(lock) {
                        buffer.append(chunk)
                        tail.append(chunk)
                        if (tail.length > 4000) tail.delete(0, tail.length - 2000)
                        val m = activeMarker
                        if (m != null && tail.contains(m)) {
                            activeDeferred?.complete(Unit)
                        }
                    }
                }
            } catch (_: Exception) {
                connected = false
            }
        }

        // 等远端 shell 起来，再换成轻量 sh 并关闭输入回显（保证标记只在真实输出里出现）。
        delay(800)
        sock.send("exec sh 2>/dev/null\n")
        delay(300)
        sock.send("stty -echo 2>/dev/null\n")
        delay(200)

        val ready = "OPENSHELL_READY_${System.currentTimeMillis()}"
        val deferred = CompletableDeferred<Unit>()
        synchronized(lock) {
            buffer.setLength(0)
            tail.setLength(0)
            activeMarker = ready
            activeDeferred = deferred
        }
        sock.send("printf '$ready\\n'\n")
        val handshakeOk = withTimeoutOrNull(10_000L) { deferred.await() } != null
        synchronized(lock) {
            activeMarker = null
            activeDeferred = null
            buffer.setLength(0)
            tail.setLength(0)
        }
        if (!handshakeOk) {
            // 远端 shell 未就绪：标记断开并抛错，避免在未完成 exec sh / stty -echo 的
            // shell 上执行命令（回显污染标记、section 匹配失败、空输出误重试）。
            connected = false
            socket = null
            ptyId = null
            runCatching { sock.close() }
            throw IOException("Shell not ready (handshake timeout)")
        }
        connected = true
    }

    /**
     * 串行执行 [script]，读取到 [endMarker] 后返回自本命令开始累积的原始输出。
     * 同一 PTY 同一时刻只能执行一条命令，避免并发命令互相覆盖标记。
     */
    suspend fun run(script: String, endMarker: String, timeoutMs: Long): String {
        return mutex.withLock {
            if (!connected) connect()
            val sock = socket ?: return@withLock ""
            val deferred = CompletableDeferred<Unit>()
            synchronized(lock) {
                buffer.setLength(0)
                tail.setLength(0)
                activeMarker = endMarker
                activeDeferred = deferred
            }
            sock.send(script)
            val timedOut = withTimeoutOrNull(timeoutMs) { deferred.await() } == null
            synchronized(lock) {
                activeMarker = null
                activeDeferred = null
            }
            if (timedOut) {
                // 超时后远端命令仍在执行、输出继续写入 buffer：清理缓冲并强制断开，
                // 下次执行会重建 PTY，避免旧命令 end 标记与新命令输出交错导致结果错乱/截断。
                synchronized(lock) { buffer.setLength(0); tail.setLength(0) }
                connected = false
                throw ShellCommandTimeoutException("Command timed out after ${timeoutMs}ms")
            }
            synchronized(lock) { buffer.toString() }
        }
    }

    /**
     * 执行单条 shell 命令并返回 begin/end 标记之间清洗后的输出。
     * 命令输出会重定向 stderr 到 stdout，并对 ANSI 转义序列做清理。
     */
    suspend fun runCommand(command: String, timeoutMs: Long = 30_000): String {
        var result = runCommandOnce(command, timeoutMs)
        if (result.isBlank()) {
            // PTY 偶发空输出（shell 尚未就绪/标记丢失），重试一次提升成功率。
            result = runCommandOnce(command, timeoutMs)
        }
        return result
    }

    /**
     * 执行单条 shell 命令并返回退出码与清洗后的输出，用于需要区分成功/失败的场景。
     * 命令超时（[run] 抛 [ShellCommandTimeoutException]）时返回退出码 -1，绝不误报成功。
     */
    suspend fun runCommandResult(command: String, timeoutMs: Long = 60_000): ShellCommandResult {
        val id = UUID.randomUUID().toString().replace("-", "")
        val begin = "OPENSHELL_B_$id"
        val end = "OPENSHELL_E_$id"
        val exit = "OPENSHELL_X_$id"
        val script = "printf '$begin\\n'\n$command 2>&1\nprintf '${exit}%d\\n' \"\$?\"\nprintf '\\n$end\\n'\n"
        val raw = run(script, end, timeoutMs)
        val body = extract(begin, end, raw)
        val lines = body.lineSequence().toList()
        val exitLine = lines.lastOrNull { it.startsWith(exit) }
        // 缺失退出码行说明命令超时/标记丢失，此时绝不能回退成 0（误报成功）。
        val code = exitLine?.removePrefix(exit)?.trim()?.toIntOrNull() ?: -1
        val output = lines.filterNot { it.startsWith(exit) }.joinToString("\n").trim()
        return ShellCommandResult(code, output)
    }

    private suspend fun runCommandOnce(command: String, timeoutMs: Long): String {
        val id = UUID.randomUUID().toString().replace("-", "")
        val begin = "OPENSHELL_B_$id"
        val end = "OPENSHELL_E_$id"
        val script = "printf '$begin\\n'\n$command 2>&1\nprintf '\\n$end\\n'\n"
        // 读类命令超时保持宽容：返回空串由调用方降级/重试，不向上抛（[]run] 超时抛异常已在[]runCommandResult] 用于失败判定）。
        val raw = runCatching { run(script, end, timeoutMs) }.getOrElse {
            if (it is ShellCommandTimeoutException) return ""
            throw it
        }
        return extract(begin, end, raw)
    }

    private fun extract(begin: String, end: String, raw: String): String {
        val lines = cleanTerminal(raw).lines()
        val beginIdx = lines.indexOf(begin)
        val endIdx = lines.indexOf(end)
        if (beginIdx == -1 || endIdx == -1 || endIdx <= beginIdx) return ""
        return lines.subList(beginIdx + 1, endIdx).joinToString("\n").trim()
    }

    /** 关闭会话：取消读流并异步移除远端 PTY。 */
    fun close() {
        readerJob?.cancel()
        val sock = socket
        val id = ptyId
        connected = false
        socket = null
        ptyId = null
        scope.launch(Dispatchers.IO) {
            runCatching { sock?.close() }
            if (id != null) runCatching { api.removePty(conn, id) }
        }
    }

    private fun cleanTerminal(raw: String): String = raw
        .replace(ANSI_OSC, "")
        .replace(ANSI_CSI, "")
        .replace("\u001B", "")
        .replace("\r", "")

    private companion object {
        val ANSI_CSI = Regex("\u001B\\[[0-9;?]*[A-Za-z]")
        val ANSI_OSC = Regex("\u001B\\][^\u001B\u0007]*(?:\u0007|\u001B\\\\)")
    }
}

/**
 * 按服务器复用常驻 PTY 会话的注册表。
 *
 * 以 serverId 为键缓存一条 [ServerShellSession]，并做引用计数：每有一个页面（如 Git 页、
 * 服务器管理页）获取会话，计数加一；页面销毁时调用 [release] 减一，归零后关闭会话并
 * 移除对应 PTY，避免页面切换即断线，也避免 PTY 泄漏。
 */
@Singleton
class ServerShellRegistry @Inject constructor() {

    private class Entry(val session: ServerShellSession, var refs: Int = 0)

    private val lock = Any()
    private val sessions = mutableMapOf<String, Entry>()

    /**
     * 获取（或创建）[serverId] 对应的共享会话并增加引用计数。
     *
     * @param serverId 服务器标识，用于跨页面复用同一条 PTY
     * @param api 用于创建/操作 PTY 的 API 客户端
     * @param conn 服务器连接信息
     * @param directory 会话初始工作目录，可空
     */
    fun acquire(serverId: String, api: OpenCodeApi, conn: ServerConnection, directory: String): ServerShellSession {
        synchronized(lock) {
            val entry = sessions.getOrPut(serverId) { Entry(ServerShellSession(api, conn, directory)) }
            entry.refs++
            return entry.session
        }
    }

    /**
     * 释放 [serverId] 会话的引用，引用归零时关闭会话并移除 PTY。
     */
    fun release(serverId: String) {
        synchronized(lock) {
            val entry = sessions[serverId] ?: return
            entry.refs--
            if (entry.refs <= 0) {
                sessions.remove(serverId)
                entry.session.close()
            }
        }
    }
}

/** 常驻 PTY 命令执行超时：退出码不可靠，调用方应视为失败而非成功。 */
class ShellCommandTimeoutException(message: String) : IOException(message)
