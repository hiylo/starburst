/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BackendGate.kt
 * Date : 2026/09/14 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.gate

import org.hiylo.starburst.data.api.BackendApi
import org.hiylo.starburst.domain.model.ServerConfig

/** 一次 starburst-backend 探测的结果。 */
data class BackendProbeResult(
    /** /api/health 是否返回 2xx（后端是否可达/已部署）。 */
    val healthy: Boolean,
    /** 后端是否「可用」：可达 且 token 能鉴权读取真实 /api/system。 */
    val available: Boolean,
    /** 后端自身版本（/api/system 的 version 字段），探测失败或为空时为 null。 */
    val version: String?,
    /** 版本是否低于 App 要求的最低版本（需要升级）。 */
    val needsUpgrade: Boolean,
)

/**
 * starburst-backend 可用性门控的统一判定。
 *
 * 「后端正常可用」= 健康探测通过（/api/health OK）且后端自身版本不低于 [REQUIRED_BACKEND_VERSION]。
 * 各页面统一复用本文件的判定与探测逻辑，避免重复实现版本比较与地址推导。
 *
 * @author Hsi Chu
 * @since 1.0
 */
object BackendGate {

    /** App 要求的最低的 starburst-backend 版本。低于该版本时视为后端状态异常（需要升级）。 */
    const val REQUIRED_BACKEND_VERSION: String = "1.0.0"

    /**
     * 后端是否「正常可用」（健康且版本达标）。后端相关功能入口的显隐统一使用该判定。
     *
     * @param available 健康探测结果：null=探测中，true=健康，false=不可达/异常
     * @param version 后端自身版本，可为 null/空
     * @return true 表示后端可连接且版本满足要求，后端功能入口可显示
     */
    fun isReady(available: Boolean?, version: String?): Boolean =
        available == true && !needsUpgrade(version)

    /**
     * 后端版本是否需要升级（低于 [REQUIRED_BACKEND_VERSION]）。
     * 版本未知（null/空）时视为无需升级，由 [isReady] 的 available 条件兜底。
     */
    fun needsUpgrade(version: String?): Boolean {
        val v = version?.trim().orEmpty()
        if (v.isEmpty()) return false
        return compareVersions(v, REQUIRED_BACKEND_VERSION) < 0
    }

    /**
     * 对给定服务器做一次后端探测（/api/health + /api/system），供各 ViewModel 复用，避免重复写探测流程。
     *
     * @param backendApi 后端 HTTP 客户端
     * @param server 当前服务器的配置；为 null 时使用默认后端地址（服务器主机名 + 18880）与默认 token
     * @param serverUrl opencode 服务地址，用于推导默认后端主机名
     */
    suspend fun probe(backendApi: BackendApi, server: ServerConfig?, serverUrl: String): BackendProbeResult {
        val backendUrl = (server?.backendResolvedUrl ?: "http://${hostFrom(serverUrl)}:18880").trimEnd('/')
        val token = server?.backendResolvedToken.orEmpty()
        // 先探测后端是否已部署/可达（/api/health 无鉴权，token 空/无效均可探测）。
        val healthy = backendApi.isHealthy(backendUrl)
        // token 为空（显式禁用后端）：可达也不算「可用」，避免仅凭 /api/health 通过就误显示
        // 后端功能入口（自动化规则 / API 令牌 / 审计日志等）；healthy 保留用于区分「未安装」。
        if (token.isBlank()) {
            return BackendProbeResult(healthy = healthy, available = false, version = null, needsUpgrade = false)
        }
        // 后端「可用」必须同时满足：/api/health 通过 且 token 能鉴权读取真实 /api/system。
        // 注意：token 无效时后端返回 {"error":"invalid token"}，会反序列化为各字段为空的
        // BackendSystemInfo（非 null），因此必须校验关键字段而非仅判空。
        val system = if (healthy) backendApi.getSystemInfo(backendUrl, token) else null
        val available = healthy && system != null && system.backend.isNotBlank()
        val version = system?.version.orEmpty()
        return BackendProbeResult(
            healthy = healthy,
            available = available,
            version = version.ifBlank { null },
            needsUpgrade = needsUpgrade(version),
        )
    }

    /** 从 opencode 服务地址推导主机名（不含端口），用于默认后端地址。 */
    private fun hostFrom(rawUrl: String): String =
        runCatching { java.net.URL(rawUrl).host }.getOrNull()
            ?: rawUrl.substringAfter("://").substringBefore(":")

    /** 简单的语义化版本比较：取数字段逐个比较，返回 <0 / 0 / >0。无法解析时按相等处理。 */
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