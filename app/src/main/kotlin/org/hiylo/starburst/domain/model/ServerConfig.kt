/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ServerConfig.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.domain.model

import kotlinx.serialization.Serializable

/**
 * Server Configuration - stored server connection details
 */
@Serializable
data class ServerConfig(
    val id: String, // UUID
    val url: String, // e.g. http://192.0.2.100:4096
    val username: String = "starburst",
    val password: String? = null,
    val name: String? = null, // User-friendly name
    val autoConnect: Boolean = false,
    val lastConnected: Long? = null,
    val isHealthy: Boolean = false,
    // SSH 隧道（可选）：配置后通过 SSH 本地端口转发连接并重启服务。
    val sshPort: Int = 22,
    val sshUsername: String = "",
    val sshPassword: String? = null,
    // OpenCode Backend 扩展（可选）：配置后解锁任务中心 / 归档等扩展能力。
    val backendUrl: String? = null,
    val backendToken: String? = null,
) {
    val displayName: String
        get() = name ?: url

    /** 是否启用 SSH 隧道（以是否填写了 SSH 用户名判定）。 */
    val useSsh: Boolean
        get() = sshUsername.isNotBlank()

    /** 是否配置了 OpenCode Backend 扩展（地址自动推导、token 有默认，故始终可用）。 */
    val useBackend: Boolean
        get() = true

    /** 解析后的 Backend 地址：优先用显式 [backendUrl]，否则推导为 opencode 同主机的 18880 端口。 */
    val backendResolvedUrl: String
        get() = backendUrl?.takeIf { it.isNotBlank() }?.trimEnd('/') ?: "http://$host:18880"

    /**
     * 解析后的 Backend token：
     * - 显式配置则使用配置值（后端镜像启用）；
     * - 未配置/空串时为**空串**，语义为「禁用后端」：后端入口隐藏、连接保持直连 opencode。
     *   注意：不要回退到默认令牌 `ocb_default`——那是后端侧的默认值，客户端显式禁用
     *   时必须能表达「不连镜像」，回退会把禁用语义静默变成「用默认令牌连镜像」（401 误报）。
     */
    val backendResolvedToken: String
        get() = backendToken?.takeIf { it.isNotBlank() }?.trim() ?: ""

    /** OpenCode 服务端口（显式端口，否则回退 http/https 默认端口）。 */
    val openCodePort: Int
        get() = try {
            val parsed = java.net.URL(url)
            val explicitPort = parsed.port
            if (explicitPort != -1) explicitPort else parsed.defaultPort
        } catch (e: Exception) {
            url.substringAfterLast(":").toIntOrNull() ?: 80
        }

    val host: String
        get() = try {
            java.net.URL(url).host
        } catch (e: Exception) {
            url.substringAfter("://").substringBefore(":")
        }

    val port: Int
        get() = openCodePort
}

/**
 * Server Health - result of health check
 */
@Serializable
data class ServerHealth(
    val healthy: Boolean,
    val version: String? = null
)
