/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : StarBurstApiMeta.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.BuildConfig
import org.hiylo.starburst.domain.model.Project
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ============ Project ============

suspend fun StarBurstApi.listProjects(conn: ServerConnection): List<Project> {
    // V2 无等价端点（agent 尚未实现，联调再定），保持 V1 路径。
    return httpClient.get("${conn.baseUrl}/project") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
}

suspend fun StarBurstApi.getCurrentProject(conn: ServerConnection): Project {
    // V2：GET /api/location 返回 {location:{directory, project:{id, directory}}}，
    // project.id == "global" 表示非 git 仓库；否则为 git HEAD，映射成 vcs="git"。
    val v2 = runCatching {
        httpClient.get("${conn.baseUrl}/api/location") {
            conn.authHeader?.let { header("Authorization", it) }
        }
    }.getOrNull()
    if (v2 != null && v2.status.isSuccess()) {
        val body = runCatching { json.parseToJsonElement(v2.bodyAsText()).jsonObject }.getOrNull()
        val location = body?.get("location") as? JsonObject ?: body
        val project = location?.get("project") as? JsonObject
        val directory = location?.get("directory")?.jsonPrimitive?.contentOrNull.orEmpty()
        val projectId = project?.get("id")?.jsonPrimitive?.contentOrNull.orEmpty()
        val projectDir = project?.get("directory")?.jsonPrimitive?.contentOrNull.orEmpty()
        if (directory.isNotBlank() || projectId.isNotBlank()) {
            return Project(
                id = projectId,
                worktree = projectDir,
                path = directory,
                directory = directory,
                vcs = if (projectId.isNotBlank() && projectId != "global") "git" else null,
            )
        }
    }
    // V1 回退：/project/current
    return httpClient.get("${conn.baseUrl}/project/current") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
}

// ============ Agents ============

/**
 * List available agents (build, plan, etc.).
 * GET /api/agent
 * Returns agents filtered to primary/visible ones for the mode selector.
 */
suspend fun StarBurstApi.listAgents(conn: ServerConnection): List<AgentInfo> {
    // V2：GET /api/agent 返回 {location, data:[...]} 信封，解出 data 数组；V2 条目缺字段容忍。
    return httpClient.get("${conn.baseUrl}/api/agent") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body<JsonObject>()
        .get("data")
        ?.let { json.decodeFromJsonElement<List<AgentInfo>>(it) }
        .orEmpty()
}

// ============ Permissions ============

/**
 * Reply to a permission request.
 * V2：POST /api/session/{sessionID}/permission/{requestID}/reply
 * Body: { reply: "once" | "always" | "reject", message?: string }
 * 参数 sessionId 保持可空仅为兼容既有调用方，为 null 时直接抛错。
 */
suspend fun StarBurstApi.replyToPermission(
    conn: ServerConnection,
    requestId: String,
    reply: String, // "once", "always", or "reject"
    message: String? = null,
    directory: String? = null,
    sessionId: String? = null,
    workspaceId: String? = null,
): Boolean {
    val body = buildMap<String, String> {
        put("reply", reply)
        message?.let { put("message", it) }
    }
    // V1 分支已移除（V2 面无此路径）：仅走 V2 会话上下文回复。
    if (sessionId == null) throw IllegalArgumentException("sessionId required (V2)")
    val url = "${conn.baseUrl}/api/session/$sessionId/permission/$requestId/reply"
    val result = httpClient.post(url) {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
        workspaceId?.let { parameter("workspace", it) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    return result.status.isSuccess()
}

/**
 * List pending permission requests.
 * GET /api/permission/request
 */
suspend fun StarBurstApi.listPendingPermissions(conn: ServerConnection, directory: String? = null, workspaceId: String? = null): List<PermissionRequest> {
    // V2：GET /api/permission/request 返回 {location, data:[...]} 信封，解出 data 数组。
    return httpClient.get("${conn.baseUrl}/api/permission/request") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
        workspaceId?.let { parameter("workspace", it) }
    }.body<JsonObject>()
        .get("data")
        ?.let { json.decodeFromJsonElement<List<PermissionRequest>>(it) }
        .orEmpty()
}

// ============ MCP ============

suspend fun StarBurstApi.getMcpStatus(conn: ServerConnection): Map<String, McpStatus> {
    // V2 无等价端点（agent 尚未实现，联调再定），保持 V1 路径。
    // 404/405 表示 V1-only 端点不存在（V2 服务器无 MCP）：返回空态而非抛解码异常，
    // MCP 屏显示空列表而非原始错误文案。其余 4xx/5xx 原样抛出。
    val response = httpClient.get("${conn.baseUrl}/mcp") {
        conn.authHeader?.let { header("Authorization", it) }
    }
    if (response.status.value == 404 || response.status.value == 405) return emptyMap()
    if (!response.status.isSuccess()) throw RuntimeException("MCP status failed: ${response.status}")
    return response.body()
}

suspend fun StarBurstApi.connectMcp(conn: ServerConnection, name: String): Boolean {
    // V2 无等价端点（agent 尚未实现，联调再定），保持 V1 路径。
    return updateMcpConnection(conn, name, connect = true)
}

suspend fun StarBurstApi.disconnectMcp(conn: ServerConnection, name: String): Boolean {
    // V2 无等价端点（agent 尚未实现，联调再定），保持 V1 路径。
    return updateMcpConnection(conn, name, connect = false)
}

suspend fun StarBurstApi.startMcpAuth(conn: ServerConnection, name: String): McpAuthStart {
    // V2 无等价端点（agent 尚未实现，联调再定），保持 V1 路径。
    val response = httpClient.post("${conn.baseUrl}/mcp/${name.encodeURLPathPart()}/auth") {
        conn.authHeader?.let { header("Authorization", it) }
    }
    if (!response.status.isSuccess()) throw RuntimeException("MCP authentication failed: ${response.status}")
    return response.body()
}

// ============ Questions ============

/**
 * Reply to a question request.
 * V2：POST /api/session/{sessionID}/question/{requestID}/reply
 * Body: { answers: string[][] }
 * 参数 sessionId 保持可空仅为兼容既有调用方，为 null 时直接抛错。
 */
suspend fun StarBurstApi.replyToQuestion(
    conn: ServerConnection,
    requestId: String,
    answers: List<List<String>>,
    directory: String? = null,
    sessionId: String? = null,
    workspaceId: String? = null,
): Boolean {
    // V1 分支已移除（V2 面无此路径）：仅走 V2 会话上下文回复。
    if (sessionId == null) throw IllegalArgumentException("sessionId required (V2)")
    val url = "${conn.baseUrl}/api/session/$sessionId/question/$requestId/reply"
    val bodyJson = json.encodeToString(QuestionReplyBody.serializer(), QuestionReplyBody(answers = answers))
    val result = httpClient.post(url) {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
        workspaceId?.let { parameter("workspace", it) }
        setBody(TextContent(bodyJson, ContentType.Application.Json))
    }
    val ok = result.status.isSuccess()
    if (ok) {
        Log.i(StarBurstApi.TAG, "Question reply: request=$requestId dir=$directory answers=${answers.size} status=${result.status.value}")
    } else {
        val body = runCatching { result.bodyAsText() }.getOrDefault("")
        Log.e(
            StarBurstApi.TAG,
            "Question reply failed: request=$requestId dir=$directory answers=${answers.size} " +
                "status=${result.status.value} body=${body.take(300)}",
        )
    }
    return ok
}

/**
 * Reject a question request.
 * V2：POST /api/session/{sessionID}/question/{requestID}/reject
 * 参数 sessionId 保持可空仅为兼容既有调用方，为 null 时直接抛错。
 */
suspend fun StarBurstApi.rejectQuestion(
    conn: ServerConnection,
    requestId: String,
    directory: String? = null,
    sessionId: String? = null
): Boolean {
    // V1 分支已移除（V2 面无此路径）：仅走 V2 会话上下文拒绝。
    if (sessionId == null) throw IllegalArgumentException("sessionId required (V2)")
    val url = "${conn.baseUrl}/api/session/$sessionId/question/$requestId/reject"
    val result = httpClient.post(url) {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
    }
    val ok = result.status.isSuccess()
    if (ok) {
        Log.i(StarBurstApi.TAG, "Question reject: request=$requestId dir=$directory status=${result.status.value}")
    } else {
        val body = runCatching { result.bodyAsText() }.getOrDefault("")
        Log.e(
            StarBurstApi.TAG,
            "Question reject failed: request=$requestId dir=$directory status=${result.status.value} body=${body.take(300)}",
        )
    }
    return ok
}

/**
 * List pending question requests.
 * GET /api/question/request
 */
suspend fun StarBurstApi.listPendingQuestions(conn: ServerConnection, directory: String? = null, workspaceId: String? = null): List<QuestionRequest> {
    // V2：GET /api/question/request 返回 {location, data:[...]} 信封，解出 data 数组。
    val response = httpClient.get("${conn.baseUrl}/api/question/request") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
        workspaceId?.let { parameter("workspace", it) }
    }
    Log.i(StarBurstApi.TAG, "Pending questions request: status=${response.status.value}")
    return response.body<JsonObject>()
        .get("data")
        ?.let { json.decodeFromJsonElement<List<QuestionRequest>>(it) }
        .orEmpty()
}

// ============ Config / Providers ============

/**
 * Get available providers and models.
 * GET /config/providers
 * V2 后端（starburst-agent）无 `/config/providers`：404/405 时回退 `GET /api/model` 按 providerID 聚合。
 */
suspend fun StarBurstApi.getProviders(conn: ServerConnection): ProvidersResponse {
    // V1 响应优先按 JSON 解码：部分实现 content-type 为 text/html 但响应体是合法 JSON
    // （含配置化 provider 与各自 models）。绕过 Ktor ContentNegotiation（不认 text/html），
    // 用 bodyAsText + App json 直接解析；解码成功即用。
    fetchV1Providers(conn)?.let { return it }
    // 无 V1 端点（agent 返回 404）时先用 V2 /api/provider：它带 provider 真实名称与
    // 每 provider 的完整模型表，与设置屏「服务商」页取的是同一份数据，避免两处不一致。
    // 仅当它没给出任何模型时才退化到 /api/model 自行分组（名称会退化成 providerID）。
    val v2Providers = runCatching { listV2ProviderList(conn) }.getOrDefault(emptyList())
    val v2WithModels = v2Providers.filter { it.models.isNotEmpty() }
    if (v2WithModels.isNotEmpty()) {
        return ProvidersResponse(providers = v2WithModels)
    }
    return runCatching { listV2ModelCatalog(conn) }
        .getOrDefault(ProvidersResponse(providers = emptyList()))
}

/**
 * V2：`GET /api/provider` 的 `data` 数组（provider 基础信息 + 每 provider 模型表）。
 * 与 [listProviderCatalog] 同源，区别在于这里不做 connected 推导、只要原始列表。
 */
private suspend fun StarBurstApi.listV2ProviderList(conn: ServerConnection): List<ProviderInfo> =
    runCatching {
        httpClient.get("${conn.baseUrl}/api/provider") {
            conn.authHeader?.let { header("Authorization", it) }
        }.body<JsonObject>()
            .get("data")
            ?.let { json.decodeFromJsonElement<List<ProviderInfo>>(it) }
            .orEmpty()
    }.getOrDefault(emptyList())

/**
 * V2 兜底：`GET /api/model` 返回 `{data:[V2ModelInfo]}`，按 providerID 分组映射为 App 的 [ProviderInfo]。
 * 仅映射核心字段（id/name/family/status/capabilities.toolcall/cost/limit），其余保持模型默认空值。
 */
private suspend fun StarBurstApi.listV2ModelCatalog(conn: ServerConnection): ProvidersResponse {
    val payload = httpClient.get("${conn.baseUrl}/api/model") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body<JsonObject>()
    val models = payload["data"]
        ?.let { json.decodeFromJsonElement<List<V2ModelInfo>>(it) }
        .orEmpty()
    val providers = models
        .filter { it.enabled }
        .groupBy { it.providerId.ifBlank { "unknown" } }
        .map { (pid, list) ->
            ProviderInfo(
                id = pid,
                name = pid,
                models = list.associate { m ->
                    m.id to ProviderModel(
                        id = m.id,
                        providerId = m.providerId,
                        name = m.name.ifBlank { m.id },
                        family = m.family,
                        status = m.status,
                        // V2 capabilities 的键与 V1 不同（tools vs toolcall、input 为数组），
                        // 交给 ModelCapabilities 自己按两种形态解析，不再手工只取 tools。
                        capabilities = m.capabilities?.let { caps ->
                            json.decodeFromJsonElement<ModelCapabilities>(json.encodeToJsonElement(caps))
                        },
                        // V2 的 cost 是数组（V2ModelInfo.cost: List<ModelCost>），取首元素并
                        // 编码为 JsonElement —— ProviderModel.cost 用 JsonElement 接收以同时
                        // 容忍 V1 的对象形态与 V2 的数组形态。
                        cost = m.cost.firstOrNull()?.let { json.encodeToJsonElement(it) },
                        limit = m.limit?.let { lim ->
                            ModelLimit(
                                context = lim["context"]?.intOr(0) ?: 0,
                                input = lim["input"]?.intOr(0),
                                output = lim["output"]?.intOr(0) ?: 0,
                            )
                        },
                    )
                },
            )
        }
    return ProvidersResponse(providers = providers)
}

/** 从 V2 能力 map（工具/输入/输出位）提取布尔能力位；缺失或类型不符返回 [default]。 */
private fun JsonElement?.boolOr(default: Boolean): Boolean =
    (this as? JsonPrimitive)?.booleanOrNull ?: default

/** 从 V2 limit map 提取整数值；缺失或类型不符返回 [default]。 */
private fun JsonElement?.intOr(default: Int): Int =
    (this as? JsonPrimitive)?.intOrNull ?: default

/**
 * Get provider catalog with connection status.
 * GET /api/provider
 */
suspend fun StarBurstApi.listProviderCatalog(conn: ServerConnection): ProviderCatalogResponse {
    // 服务商页数据源：**必须合并 V1 与 V2 两个端点**，只取任一个都会与 opencode 不一致：
    //  - V1 `GET /config/providers`：只列**已配置**的服务商。opencode 实测为
    //    litellm / opencode / opencode-go / llama-4060ti（opencode-go 只在这里出现）。
    //  - V2 `GET /api/provider`：列**全部已知**服务商，含内置但未配置的 openai 等。
    // 只取 V2 → 漏 opencode-go；只取 V1 → 漏 openai。取并集：V1 条目优先（带真实
    // source 与配置），缺失字段用 V2 补。agent 只有 V2，此时退化为纯 V2 列表。
    val v1 = runCatching { fetchV1Providers(conn) }.getOrNull()
    val v2 = runCatching { listV2ProviderList(conn) }.getOrDefault(emptyList())

    val v1ById = v1?.providers.orEmpty().filter { it.id.isNotBlank() }.associateBy { it.id }
    val v2ById = v2.filter { it.id.isNotBlank() }.associateBy { it.id }
    if (v1ById.isEmpty() && v2ById.isEmpty()) {
        return ProviderCatalogResponse(all = emptyList())
    }

    // V1 列表即「已配置」集合；agent 无 V1 时，全部 V2 条目视为已连接（保持既有行为）。
    val connected = if (v1ById.isNotEmpty()) v1ById.keys.toList() else v2ById.keys.toList()
    return ProviderCatalogResponse(
        all = mergeProviderEntries(v1ById.values.toList(), v2ById.values.toList()),
        default = v1?.default.orEmpty(),
        connected = connected,
    )
}

/**
 * 合并 V1（`/config/providers`，仅已配置）与 V2（`/api/provider`，全部已知）的服务商条目。
 *
 * 顺序：V1 原有顺序在前，V2 独有的追加在后——避免 opencode 已配置项被内置项挤到后面。
 * 同 id 以 V1 为准（带真实 `source` 与配置），空字段用 V2 补。
 */
internal fun mergeProviderEntries(
    v1Entries: List<ProviderInfo>,
    v2Entries: List<ProviderInfo>,
): List<ProviderInfo> {
    val v1ById = v1Entries.filter { it.id.isNotBlank() }.associateBy { it.id }
    val v2ById = v2Entries.filter { it.id.isNotBlank() }.associateBy { it.id }
    if (v1ById.isEmpty() && v2ById.isEmpty()) return emptyList()
    val orderedIds = v1ById.keys.toList() + v2ById.keys.filter { it !in v1ById }
    return orderedIds.mapNotNull { id ->
        val fromV1 = v1ById[id]
        val fromV2 = v2ById[id]
        when {
            fromV1 == null -> fromV2
            fromV2 == null -> fromV1
            else -> fromV1.copy(
                name = fromV1.name.ifBlank { fromV2.name },
                source = fromV1.source.ifBlank { fromV2.source },
                env = fromV1.env.ifEmpty { fromV2.env },
                key = fromV1.key ?: fromV2.key,
                options = fromV1.options.ifEmpty { fromV2.options },
                models = fromV1.models.ifEmpty { fromV2.models },
            )
        }
    }
}


/**
 * 轻量版「服务商目录」：只取**已配置**的服务商，不拉全量注册表。
 *
 * 用于首屏立即渲染——`GET /provider` 在 opencode 上实测 6.4MB / 225 个内置服务商，
 * 手机上解析有明显延迟；首屏先显示这 4~5 个已配置的，全量注册表由
 * [fetchV1ProviderRegistry] 在后台补齐（见 `ServerSettingsViewModel.loadProviders`）。
 */
suspend fun StarBurstApi.listConnectedProviderCatalog(conn: ServerConnection): ProviderCatalogResponse {
    val v1 = runCatching { fetchV1Providers(conn) }.getOrNull()
    val v2 = runCatching { listV2ProviderList(conn) }.getOrDefault(emptyList())
    val v1ById = v1?.providers.orEmpty().filter { it.id.isNotBlank() }.associateBy { it.id }
    val v2ById = v2.filter { it.id.isNotBlank() }.associateBy { it.id }
    val merged = mergeProviderEntries(v1ById.values.toList(), v2ById.values.toList())
    val connected = if (v1ById.isNotEmpty()) v1ById.keys.toList() else v2ById.keys.toList()
    return ProviderCatalogResponse(all = merged, default = v1?.default.orEmpty(), connected = connected)
}

/**
 * 合并「已配置目录」与「全量注册表」：全量条目补在已配置项之后，同 id 以已配置为准
 * （已配置项带真实 `source`/凭据信息，不能被内置占位覆盖）。
 */
internal fun mergeProviderRegistry(
    connectedCatalog: ProviderCatalogResponse,
    registry: ProviderCatalogResponse,
): ProviderCatalogResponse {
    val registryById = registry.all.filter { it.id.isNotBlank() }.associateBy { it.id }
    val mergedAll = connectedCatalog.all.map { existing ->
        // 已配置项也用注册表回填空字段：agent 的 V2 /api/provider 不带 source，
        // 而 V1 /provider 带（source=config）——不回填会让来源显示成「其他」。
        val fromRegistry = registryById[existing.id] ?: return@map existing
        existing.copy(
            name = existing.name.ifBlank { fromRegistry.name },
            source = existing.source.ifBlank { fromRegistry.source },
            env = existing.env.ifEmpty { fromRegistry.env },
            options = existing.options.ifEmpty { fromRegistry.options },
            models = existing.models.ifEmpty { fromRegistry.models },
        )
    } + registry.all.filter { it.id !in connectedCatalog.all.map { c -> c.id }.toSet() }
    return ProviderCatalogResponse(
        all = mergedAll,
        default = registry.default.ifEmpty { connectedCatalog.default },
        // connected 逐级回退：首屏已配置 → 注册表已连接 → 合并后列表全部
        //（两侧都为空时不能留空，否则页面无任何可操作项）。
        connected = connectedCatalog.connected.ifEmpty { registry.connected }.ifEmpty { mergedAll.map { it.id } },
    )
}

/**
 * 取 V1 `GET /provider`（全量 provider 注册表）并映射为 [ProviderCatalogResponse]。
 *
 * opencode 1.18.30 在此返回 `{all, default, connected}`：`all` 含全部内置服务商
 * （实测 225 个，`source=custom`）+ 已配置的（`source=config|api`），`connected` 只含
 * 已配置项。返回体较大（实测 6.4MB），故仅用于服务商页，不用于模型选择器。
 *
 * 端点不存在（agent）或解析失败时返回 null，由调用方回退到 `/config/providers` + `/api/provider` 合并。
 */
internal suspend fun StarBurstApi.fetchV1ProviderRegistry(conn: ServerConnection): ProviderCatalogResponse? {
    val response = runCatching {
        httpClient.get("${conn.baseUrl}/provider") {
            conn.authHeader?.let { header("Authorization", it) }
        }
    }.getOrNull() ?: return null
    if (!response.status.isSuccess()) return null
    val payload = runCatching { json.parseToJsonElement(response.bodyAsText()) }.getOrNull() ?: return null
    val root = payload as? JsonObject ?: return null
    val all = root["all"]?.let { runCatching { json.decodeFromJsonElement<List<ProviderInfo>>(it) }.getOrNull() }
        ?: return null
    val default = root["default"]?.let { element ->
        runCatching { json.decodeFromJsonElement<Map<String, String>>(element) }.getOrNull()
    }.orEmpty()
    val connected = root["connected"]?.let { element ->
        runCatching { json.decodeFromJsonElement<List<String>>(element) }.getOrNull()
    }.orEmpty()
    return ProviderCatalogResponse(
        all = all.filter { it.id.isNotBlank() },
        default = default,
        connected = connected,
    )
}

/**
 * 取 V1 `GET /config/providers` 并解码为 [ProvidersResponse]。
 * 优先按 JSON 直接解码（部分实现 content-type 为 text/html 但响应体是合法 JSON），
 * 解码失败返回 null 交由调用方回退。
 */
private suspend fun StarBurstApi.fetchV1Providers(conn: ServerConnection): ProvidersResponse? {
    val response = runCatching {
        httpClient.get("${conn.baseUrl}/config/providers") {
            conn.authHeader?.let { header("Authorization", it) }
        }
    }.getOrNull() ?: return null
    if (!response.status.isSuccess()) return null
    return runCatching {
        json.decodeFromJsonElement<ProvidersResponse>(json.parseToJsonElement(response.bodyAsText()))
    }.getOrNull()
}

/**
 * Get available auth methods for providers.
 * GET /provider/auth
 */
suspend fun StarBurstApi.getProviderAuthMethods(conn: ServerConnection): Map<String, List<ProviderAuthMethod>> {
    // V2 无等价端点（agent 尚未实现，联调再定），保持 V1 路径。
    return httpClient.get("${conn.baseUrl}/provider/auth") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
}

/**
 * Start OAuth authorization for a provider.
 * POST /provider/{providerID}/oauth/authorize
 */
suspend fun StarBurstApi.authorizeProviderOauth(
    conn: ServerConnection,
    providerId: String,
    methodIndex: Int
): ProviderOauthAuthorization? {
    // V2 无等价端点（agent 尚未实现，联调再定），保持 V1 路径。
    val response = httpClient.post("${conn.baseUrl}/provider/$providerId/oauth/authorize") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(ProviderOauthAuthorizeRequest(method = methodIndex))
    }
    val body = response.bodyAsText().trim()
    if (BuildConfig.DEBUG) {
        Log.d("StarBurstApi", "authorizeProviderOauth: status=${response.status}")
    }

    if (!response.status.isSuccess()) {
        throw ProviderAuthException(
            response.status.value,
            providerAuthErrorMessage(json, response.status.value, body, "Failed to start OAuth"),
        )
    }
    if (body.isBlank() || body == "null") return ProviderOauthAuthorization()

    return runCatching {
        json.decodeFromString(ProviderOauthAuthorization.serializer(), body)
    }.getOrElse {
        // Some server builds return an empty object for headless mode.
        ProviderOauthAuthorization()
    }
}

/**
 * Complete OAuth authorization for a provider.
 * POST /provider/{providerID}/oauth/callback
 */
suspend fun StarBurstApi.completeProviderOauth(
    conn: ServerConnection,
    providerId: String,
    methodIndex: Int,
    code: String? = null
): Boolean {
    // V2 无等价端点（agent 尚未实现，联调再定），保持 V1 路径。
    val body = ProviderOauthCallbackRequest(method = methodIndex, code = code)
    if (BuildConfig.DEBUG) {
        Log.d(StarBurstApi.TAG, "completeProviderOauth: POST /provider/$providerId/oauth/callback method=$methodIndex hasCode=${code != null}")
    }
    val response = httpClient.post("${conn.baseUrl}/provider/$providerId/oauth/callback") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    val responseBody = response.bodyAsText().trim()
    if (BuildConfig.DEBUG) Log.d(StarBurstApi.TAG, "completeProviderOauth: status=${response.status}")
    if (!response.status.isSuccess()) {
        throw ProviderAuthException(
            response.status.value,
            providerAuthErrorMessage(json, response.status.value, responseBody, "Failed to complete OAuth"),
        )
    }
    return true
}

/**
 * Set API key auth for provider.
 * PUT /auth/{providerID}
 */
suspend fun StarBurstApi.setProviderApiKey(conn: ServerConnection, providerId: String, apiKey: String): Boolean {
    // V2 无等价端点（agent 尚未实现，联调再定），保持 V1 路径。
    val response = httpClient.put("${conn.baseUrl}/auth/$providerId") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(mapOf("type" to "api", "key" to apiKey))
    }
    return response.status.isSuccess()
}

/**
 * Remove stored auth for provider.
 * DELETE /auth/{providerID}
 */
suspend fun StarBurstApi.removeProviderAuth(conn: ServerConnection, providerId: String): Boolean {
    // V2 无等价端点（agent 尚未实现，联调再定），保持 V1 路径。
    if (BuildConfig.DEBUG) Log.d(StarBurstApi.TAG, "removeProviderAuth: request")
    val response = httpClient.delete("${conn.baseUrl}/auth/$providerId") {
        conn.authHeader?.let { header("Authorization", it) }
    }
    if (BuildConfig.DEBUG) {
        Log.d(StarBurstApi.TAG, "removeProviderAuth: status=${response.status}")
    }
    return response.status.isSuccess()
}

/**
 * Get current server config.
 * GET /config
 */
suspend fun StarBurstApi.getConfig(conn: ServerConnection): ServerConfigResponse {
    // V2 无等价端点（agent 尚未实现，联调再定），保持 V1 路径。
    return httpClient.get("${conn.baseUrl}/config") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
}

/**
 * Get global server config.
 * GET /global/config
 */
suspend fun StarBurstApi.getGlobalConfig(conn: ServerConnection): ServerConfigResponse {
    // V2 无等价端点（agent 尚未实现，联调再定），保持 V1 路径。
    return httpClient.get("${conn.baseUrl}/global/config") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
}

/**
 * Patch server config.
 * PATCH /config
 */
suspend fun StarBurstApi.updateConfig(conn: ServerConnection, patch: ServerConfigPatch): ServerConfigResponse {
    // V2 无等价端点（agent 尚未实现，联调再定），保持 V1 路径。
    return httpClient.patch("${conn.baseUrl}/config") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(patch)
    }.body()
}

/**
 * Patch global server config.
 * PATCH /global/config
 */
suspend fun StarBurstApi.updateGlobalConfig(conn: ServerConnection, patch: ServerConfigPatch): ServerConfigResponse {
    // V2 无等价端点（agent 尚未实现，联调再定），保持 V1 路径。
    return httpClient.patch("${conn.baseUrl}/global/config") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(patch)
    }.body()
}

/**
 * Patch custom provider configuration.
 * PATCH /config with only the provider map in the body.
 */
suspend fun StarBurstApi.updateProviderConfig(
    conn: ServerConnection,
    provider: Map<String, ProviderConfigDefinition>,
): ServerConfigResponse {
    // PATCH 语义对齐 opencode：请求体里出现的 provider id 为 upsert，**缺失的 id 等于删除**
    // （客户端的「删除服务商」就是把该 key 从 map 摘掉后再 PATCH）。agent 与 opencode 一致。
    return httpClient.patch("${conn.baseUrl}/config") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(ServerConfigPatch(provider = provider))
    }.body()
}

/**
 * Dispose global instances and force provider/auth state refresh.
 * POST /global/dispose
 */
suspend fun StarBurstApi.disposeGlobal(conn: ServerConnection): Boolean {
    // V2 无等价端点（agent 尚未实现，联调再定），保持 V1 路径。
    val response = httpClient.post("${conn.baseUrl}/global/dispose") {
        conn.authHeader?.let { header("Authorization", it) }
    }
    return response.status.isSuccess()
}

// ============ Commands ============

/**
 * List available slash commands.
 * GET /api/command
 */
suspend fun StarBurstApi.listCommands(conn: ServerConnection): List<CommandInfo> {
    // V2：GET /api/command 返回 {location, data:[...]} 信封，解出 data 数组。
    return httpClient.get("${conn.baseUrl}/api/command") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body<JsonObject>()
        .get("data")
        ?.let { json.decodeFromJsonElement<List<CommandInfo>>(it) }
        .orEmpty()
}

// ============ Session Agent / Model switch ============

suspend fun StarBurstApi.switchSessionAgentV2(
    conn: ServerConnection,
    sessionId: String,
    agent: String,
    directory: String? = null,
    workspaceId: String? = null,
) {
    val response = httpClient.post("${conn.baseUrl}/api/session/$sessionId/agent") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
        workspaceId?.let { parameter("workspace", it) }
        contentType(ContentType.Application.Json)
        setBody(mapOf("agent" to agent))
    }
    if (!response.status.isSuccess()) throw V2HttpException(response.status.value, "V2 agent switch failed: ${extractV2ErrorMessage(response)}")
}

suspend fun StarBurstApi.switchSessionModelV2(
    conn: ServerConnection,
    sessionId: String,
    model: ModelSelection,
    variant: String? = null,
    directory: String? = null,
    workspaceId: String? = null,
) {
    val response = httpClient.post("${conn.baseUrl}/api/session/$sessionId/model") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
        workspaceId?.let { parameter("workspace", it) }
        contentType(ContentType.Application.Json)
        setBody(V2ModelSwitchBody(V2ModelRef(model.providerId, model.modelId, variant)))
    }
    if (!response.status.isSuccess()) throw V2HttpException(response.status.value, "V2 model switch failed: ${extractV2ErrorMessage(response)}")
}
