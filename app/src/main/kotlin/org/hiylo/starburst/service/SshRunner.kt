/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SshRunner.kt
 * Date : 2026/09/09 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.starburst.service

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hiylo.starburst.data.shell.ShellCommandResult
import org.hiylo.starburst.domain.model.ServerConfig

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
        val jsch = JSch()
        val session = jsch.getSession(server.sshUsername, server.host, server.sshPort)
        try {
            session.setPassword(server.sshPassword ?: "")
            session.setConfig("StrictHostKeyChecking", "no")
            session.setConfig("PreferredAuthentications", "password")
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
        val jsch = JSch()
        val session = jsch.getSession(server.sshUsername, server.host, server.sshPort)
        try {
            session.setPassword(server.sshPassword ?: "")
            session.setConfig("StrictHostKeyChecking", "no")
            session.setConfig("PreferredAuthentications", "password")
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
}
