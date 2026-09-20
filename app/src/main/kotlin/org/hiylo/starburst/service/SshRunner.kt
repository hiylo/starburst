/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SshRunner.kt
 * Date : 2026/09/09 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.service

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hiylo.starburst.data.shell.ShellCommandResult
import org.hiylo.starburst.domain.model.ServerConfig
import java.io.File

/**
 * 通过 SSH 在远程服务器上执行命令（独立于 OpenCode 的 HTTP 连接）。
 *
 * 用于 OpenCode 后端崩溃后仍能远程重启服务等运维场景。
 *
 * @author Hsi Chu
 * @since V1.2.0
 */
object SshRunner {

    /** 默认 SSH 命令超时（毫秒）。 */
    private const val DEFAULT_TIMEOUT_MS = 30_000

    /** 持久化 SSH known_hosts 的文件路径（App 启动时由 [org.hiylo.starburst.StarBurstApp] 设置）。 */
    @Volatile
    var knownHostsFile: File? = null

    /**
     * 在 [server] 指定的服务器上通过 SSH 执行 [command]，返回合并的 stdout/stderr 文本。
     *
     * 仅当 [server] 配置了 SSH 用户名时才可用，否则抛出 [IllegalStateException]。
     *
     * @param server 服务器配置（含 SSH 用户名/密码/端口）
     * @param command 要执行的 shell 命令
     * @param timeoutMs 连接与执行超时（毫秒）
     */
    suspend fun runCommand(
        server: ServerConfig,
        command: String,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): String = withContext(Dispatchers.IO) {
        if (!server.useSsh) {
            throw IllegalStateException("SSH 未配置，无法通过 SSH 执行命令")
        }
        val session = buildSession(server)
        try {
            session.connect(timeoutMs)
            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            val errBuffer = java.io.ByteArrayOutputStream()
            channel.setErrStream(errBuffer)
            channel.connect()
            val stdout = channel.inputStream.bufferedReader().use { it.readText() }
            val stderr = errBuffer.toString(Charsets.UTF_8.name())
            channel.disconnect()
            (stdout + stderr).trim()
        } finally {
            session.disconnect()
        }
    }

    /**
     * 通过 SSH 执行 [command]，返回退出码与合并的 stdout/stderr 文本。
     */
    suspend fun runCommandResult(
        server: ServerConfig,
        command: String,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): ShellCommandResult = withContext(Dispatchers.IO) {
        if (!server.useSsh) {
            throw IllegalStateException("SSH 未配置，无法通过 SSH 执行命令")
        }
        val session = buildSession(server)
        try {
            session.connect(timeoutMs)
            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            val errBuffer = java.io.ByteArrayOutputStream()
            channel.setErrStream(errBuffer)
            channel.connect()
            val stdout = channel.inputStream.bufferedReader().use { it.readText() }
            val stderr = errBuffer.toString(Charsets.UTF_8.name())
            val exitCode = channel.exitStatus
            channel.disconnect()
            ShellCommandResult(exitCode, (stdout + stderr).trim())
        } finally {
            session.disconnect()
        }
    }

    /**
     * 构建一个采用 known_hosts TOFU（首次信任、之后校验，防 MITM）的 SSH 会话。
     * 首次连接的主机指纹自动信任并持久化；已记录的主机指纹变化时 JSch 会直接拒绝。
     */
    internal fun buildSession(server: ServerConfig): Session =
        buildSession(server.host, server.sshPort, server.sshUsername, server.sshPassword ?: "")

    /**
     * 按独立主机参数构建 SSH 会话（供 SFTP 备份等非 OpenCode 服务器目标复用）。
     * 与 [buildSession] 共享同一 known_hosts TOFU 策略与 UserInfo。
     */
    internal fun buildSession(host: String, port: Int, username: String, password: String): Session {
        val jsch = JSch()
        knownHostsFile?.let { file ->
            file.parentFile?.mkdirs()
            // 载入（或首次创建）known_hosts，用于主机密钥 TOFU：首次信任、之后变更即拒绝。
            runCatching { jsch.setKnownHosts(file.absolutePath) }
        }
        val session = jsch.getSession(username, host, port)
        session.setPassword(password)
        // ask + UserInfo 自动确认 = 首次信任（TOFU）；已变更的主机密钥仍会被 JSch 硬性拒绝。
        session.setConfig("StrictHostKeyChecking", "ask")
        session.setConfig("PreferredAuthentications", "password")
        session.setUserInfo(TofuUserInfo())
        return session
    }

    /**
     * 仅在「主机真实性」询问时自动确认的用户交互实现，用于非交互式场景下的首次信任。
     * 其它交互（口令/密钥短语）不自动应答。
     */
    private class TofuUserInfo : UserInfo {
        override fun getPassphrase(): String? = null
        override fun getPassword(): String? = null
        override fun promptPassword(message: String): Boolean = false
        override fun promptPassphrase(message: String): Boolean = false
        override fun promptYesNo(message: String): Boolean =
            message.contains("authenticity", ignoreCase = true)
        override fun showMessage(message: String) = Unit
    }
}
