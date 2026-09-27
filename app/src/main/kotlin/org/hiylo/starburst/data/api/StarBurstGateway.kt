/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : StarBurstGateway.kt
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
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 计算「发送消息用的连接」：后端镜像已配置且可用 → MIRROR（`{backendUrl}/api/opencode` + Bearer）；
 * 否则直连服务器。探测结果由调用方缓存（[cached] 非 null 直接复用，避免每次发送都探测）。
 *
 * 这是把 App「消息发送」接入后端镜像的关键入口：走后端 `/api/opencode` 后，后端会执行
 * 知识库 RAG-in-Prompt 检索并注入上下文，App 会话因此能使用知识库。
 */
suspend fun resolveSendingConnection(
    backendApi: BackendApi,
    backendUrl: String?,
    backendToken: String?,
    direct: ServerConnection,
    cached: ServerConnection?,
): ServerConnection {
    cached?.let { return it }
    val url = backendUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
    val token = backendToken?.trim()?.takeIf { it.isNotBlank() }
    if (url == null || token == null) return direct
    val status = runCatching {
        withTimeoutOrNull(2_500L) {
            if (!backendApi.isHealthy(url)) return@withTimeoutOrNull null
            val info = backendApi.getSystemInfo(url, token)
            if (info == null || info.backend.isBlank()) return@withTimeoutOrNull null
            BackendStatus(
                backendUrl = url,
                backendToken = token,
                backendAvailable = true,
                backendVersion = info.version,
            )
        }
    }.getOrNull()
    return if (status != null) StarBurstGateway.resolve(direct, status) else direct
}

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
 * StarBurst 双通道网关解析器（M-OC 的 App 侧基础，纯逻辑、无 UI、无网络）。
 *
 * 决定「实际请求用的 [ServerConnection]」：
 *  - 后端可用（health 通过）且版本不低于 [StarBurstGateway.BACKEND_MIN_VERSION] 且已配置
 *    url/token → baseUrl 取 `${backendUrl}${BACKEND_API_PREFIX}`（镜像服务器 REST 端点），
 *    authHeader 取 `Bearer <backendToken>`（后端 APP token）；
 *  - 否则 → 原样直连服务器（baseUrl 与 authHeader 均保持不变，行为与现状一致）。
 *
 * 全部为纯函数/数据类，health 与版本等判定输入由调用方传入，便于单元测试。
 *
 * @author Hsi Chu
 * @since V1.0
 */
object StarBurstGateway {

    /** 后端镜像端点前缀：后端 `${backendUrl}/api/opencode` 对应服务器 `/` 的 REST 接口。 */
    const val BACKEND_API_PREFIX = "/api/opencode"

    /**
     * App 要求的最低 starburst-backend 版本（后端/镜像能力的最低下限，
     * 与该镜像接口自身的可用性判定一致；整体功能门槛使用 `BackendGate.MIN_BACKEND_VERSION`）。
     */
    const val BACKEND_MIN_VERSION = "1.0.0"

    /**
     * 解析器入口：输入「要连的服务器连接」+「该服务器后端状态」，返回实际请求用的连接。
     *
     * 三种形态：
     *  - 后端与服务器同 URL（starburst-agent：自身即 V2 `/api` 面）→ 直连但 authHeader
     *    改为 `Bearer <backendToken>`，不走镜像代理前缀，也不受版本门槛约束（版本仅描述
     *    镜像接口，V2 直连面与它无关）；
     *  - 独立后端镜像（App → backend → 上游 opencode）→ baseUrl 加 `/api/opencode` 前缀、Bearer，
     *    并要求版本 ≥ [BACKEND_MIN_VERSION]；
     *  - 否则 → 原样直连（baseUrl 与 authHeader 不变）。
     */
    fun resolve(conn: ServerConnection, backend: BackendStatus): ServerConnection {
        val backendUrl = backend.backendUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
        val token = backend.backendToken?.trim()?.takeIf { it.isNotBlank() }
        if (backendUrl == null || token == null || !backend.backendAvailable) return conn
        // 同主机直连型后端（新式 starburst-agent）：与直连服务器同一 URL，直接以 Bearer 走 V2。
        val selfBearer = applySelfBackendBearer(conn, backendUrl, token)
        if (selfBearer !== conn) return selfBearer
        // 独立后端镜像：要求版本不低于镜像接口可用下限。
        if (!versionSatisfies(backend.backendVersion)) return conn
        return ServerConnection(
            baseUrl = "$backendUrl$BACKEND_API_PREFIX",
            authHeader = "Bearer $token",
        )
    }

    /**
     * 同主机直连型后端（starburst-agent）专用：当 [conn] 的 baseUrl 与后端 URL 相同且
     * 配置了 token 时，把 authHeader 改为 `Bearer <token>`，其余原样返回；不匹配则原样返回。
     * 供连接服务发布「已解析直连连接」给各 ViewModel 复用，保证列表/会话/模型等 REST 调用
     * 对 token 鉴权的 V2 服务器都能带上 Bearer。
     */
    fun applySelfBackendBearer(conn: ServerConnection, backendUrl: String?, backendToken: String?): ServerConnection {
        val url = backendUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
        val token = backendToken?.trim()?.takeIf { it.isNotBlank() }
        if (url == null || token == null) return conn
        if (conn.baseUrl.trim().trimEnd('/') == url) return conn.copy(authHeader = "Bearer $token")
        return conn
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