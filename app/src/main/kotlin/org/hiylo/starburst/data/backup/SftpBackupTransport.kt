/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SftpBackupTransport.kt
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.backup

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hiylo.starburst.service.SshRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** SFTP 备份目标的可持久化配置（不含口令，口令走 Keystore 加密存储）。 */
data class SftpBackupSettings(
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    val remoteDir: String = "",
)

/** 完整的 SFTP 备份目标配置（含明文口令，仅在内存中用于连接，不落盘）。 */
data class SftpBackupConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val remoteDir: String,
)

/**
 * 通过 SFTP 上传/下载加密备份文件到 NAS 等远程主机。
 *
 * 复用 [SshRunner] 的 known_hosts TOFU 会话策略（首次信任、之后变更即拒绝），
 * 连接与会话均在 [Dispatchers.IO] 上执行，避免阻塞主线程。
 */
@Singleton
class SftpBackupTransport @Inject constructor() {

    /** 将 [bytes] 以 [filename] 写入 [config] 指定的远程目录（目录不存在时逐级创建）。 */
    suspend fun upload(bytes: ByteArray, config: SftpBackupConfig, filename: String) =
        withContext(Dispatchers.IO) {
            val session = openSession(config)
            try {
                val channel = session.openChannel("sftp") as ChannelSftp
                channel.connect()
                try {
                    ensureDir(channel, config.remoteDir)
                    channel.cd(config.remoteDir)
                    channel.put(ByteArrayInputStream(bytes), filename)
                } finally {
                    channel.disconnect()
                }
            } finally {
                session.disconnect()
            }
        }

    /** 从 [config] 指定的远程目录读取 [filename]，返回其字节内容。 */
    suspend fun download(config: SftpBackupConfig, filename: String): ByteArray =
        withContext(Dispatchers.IO) {
            val session = openSession(config)
            try {
                val channel = session.openChannel("sftp") as ChannelSftp
                channel.connect()
                try {
                    channel.cd(config.remoteDir)
                    val out = ByteArrayOutputStream()
                    channel.get(filename, out)
                    out.toByteArray()
                } finally {
                    channel.disconnect()
                }
            } finally {
                session.disconnect()
            }
        }

    private fun openSession(config: SftpBackupConfig): Session {
        val session = SshRunner.buildSession(config.host, config.port, config.username, config.password)
        session.connect()
        return session
    }

    /** 确保远程目录存在：先尝试进入，失败则从根（或相对当前目录）逐级创建。 */
    private fun ensureDir(channel: ChannelSftp, dir: String) {
        if (dir.isBlank()) return
        val path = dir.trimEnd('/')
        if (path.isEmpty() || path == "/") return
        try {
            channel.cd(path)
            return
        } catch (_: Exception) {
            // 目录不存在，逐级创建后进入。
        }
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return
        var current = if (path.startsWith("/")) "/" else ""
        for (part in parts) {
            current = if (current.endsWith("/")) current + part else "$current/$part"
            try {
                channel.cd(current)
            } catch (_: Exception) {
                channel.mkdir(current)
                channel.cd(current)
            }
        }
    }
}
