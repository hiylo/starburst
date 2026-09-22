/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : OpenCodeGateway.kt
 * Date : 2026/09/14 14:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

/**
 * starburst-backend 的健康与版本状态（由调用方探测后传入，本解析器不发起网络请求）。
 *
 * 典型来源：ServerSettingsViewModel.probeBackend 结合 [BackendApi.isHealthy]（health）
 * 与 [BackendApi.getSystemInfo]（backendVersion 取返回的 version 字段）；
 * backendUrl / backendToken 对应 [org.hiylo.starburst.domain.model.ServerConfig.backendResolvedUrl]
 * 与 backendResolvedToken。
 *
 * @author Hsi Chu
 * @since V1.0
 */
data class BackendStatus(
    /** 后端基础地址（无尾部斜杠约定，内部自行 trim）；null/空白视为未配置。 */
    val backendUrl: String? = null,
    /** 后端 APP token（Bearer 鉴权）；null/空白视为未配置。 */
    val backendToken: String? = null,
    /** 后端是否存活（`GET /api/health` 返回 2xx）。 */
    val backendAvailable: Boolean = false,
    /** 后端自身版本（`GET /api/system` 的 version 字段）；null/空白视为未知。 */
    val backendVersion: String? = null,
)

/**
 * OpenCode 双通道网关解析器（M-OC 的 App 侧基础，纯逻辑、无 UI、无网络）。
 *
 * 决定「实际请求用的 [ServerConnection]」：
 *  - 后端可用（health 通过）且版本不低于 [OpenCodeGateway.BACKEND_MIN_VERSION] 且已配置
 *    url/token → baseUrl 取 `${backendUrl}${BACKEND_API_PREFIX}`（镜像 opencode REST 端点），
 *    authHeader 取 `Bearer <backendToken>`（后端 APP token）；
 *  - 否则 → 原样直连 opencode（baseUrl 与 authHeader 均保持不变，行为与现状一致）。
 *
 * 全部为纯函数/数据类，health 与版本等判定输入由调用方传入，便于单元测试。
 *
 * @author Hsi Chu
 * @since V1.0
 */
object OpenCodeGateway {

    /** 后端镜像端点前缀：后端 `${backendUrl}/api/opencode` 对应 opencode `/` 的 REST 接口。 */
    const val BACKEND_API_PREFIX = "/api/opencode"

    /**
     * App 要求的最低 starburst-backend 版本（后端/镜像能力的最低下限，
     * 与该镜像接口自身的可用性判定一致；整体功能门槛使用 `BackendGate.MIN_BACKEND_VERSION`）。
     */
    const val BACKEND_MIN_VERSION = "1.0.0"

    /**
     * 解析器入口：输入「要连的 opencode 连接」+「该服务器后端状态」，返回实际请求用的连接。
     */
    fun resolve(conn: ServerConnection, backend: BackendStatus): ServerConnection {
        val backendUrl = backend.backendUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
        val token = backend.backendToken?.trim()?.takeIf { it.isNotBlank() }
        if (!isBackendUsable(backendUrl, token, backend.backendAvailable, backend.backendVersion)) {
            return conn
        }
        return ServerConnection(
            baseUrl = "$backendUrl$BACKEND_API_PREFIX",
            authHeader = "Bearer $token",
        )
    }

    /**
     * 后端可用判定（health + 版本比较）：
     * url/token 均已配置 且 health 通过 且版本不低于最低要求。
     */
    fun isBackendUsable(
        backendUrl: String?,
        backendToken: String?,
        backendAvailable: Boolean,
        backendVersion: String?,
    ): Boolean {
        if (backendUrl.isNullOrBlank() || backendToken.isNullOrBlank()) return false
        if (!backendAvailable) return false
        return versionSatisfies(backendVersion)
    }

    /**
     * 版本是否满足最低要求。null/空白（版本未知）按不满足处理，保守回退到直连。
     */
    fun versionSatisfies(version: String?, minVersion: String = BACKEND_MIN_VERSION): Boolean {
        val normalized = version?.trim()?.trimStart('v').orEmpty()
        if (normalized.isBlank()) return false
        return compareVersions(normalized, minVersion) >= 0
    }

    /**
     * 简单的语义化版本比较：取数字段逐个比较，返回 <0 / 0 / >0；无法解析的片段按 0 处理。
     *
     * 等价于 ServerSettingsViewModel.kt 顶层 private 函数 compareVersions 的实现——
     * 原函数未导出，此处复制一份等价实现以保证判定规则一致，来源：ServerSettingsViewModel.kt:725。
     */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.trim().trimStart('v').split('.').mapNotNull { it.toIntOrNull() }
        val pb = b.trim().trimStart('v').split('.').mapNotNull { it.toIntOrNull() }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val av = pa.getOrElse(i) { 0 }
            val bv = pb.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }
}