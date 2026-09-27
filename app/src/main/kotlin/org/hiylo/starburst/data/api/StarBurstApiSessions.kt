/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : StarBurstApiSessions.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.BuildConfig
import org.hiylo.starburst.domain.model.FileDiff
import org.hiylo.starburst.domain.model.Session
import org.hiylo.starburst.domain.model.SessionStatus
import org.hiylo.starburst.domain.model.SharedSession
import org.hiylo.starburst.domain.model.Skill
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "StarBurstApiSessions"

// ============ V2 会话 DTO（对齐 opencode V2 契约，真值源 1.18.30 实测） ============

/** `GET/POST /api/session` 与 `GET /api/session/{id}` 的会话条目（SessionV2Info）。 */
@Serializable
data class V2SessionInfo(
    val id: String = "",
    @SerialName("parentID") val parentId: String? = null,
    @SerialName("projectID") val projectId: String = "global",
    val agent: String? = null,
    val model: V2ModelRef? = null,
    val cost: Double = 0.0,
    val tokens: JsonObject? = null,
    val time: V2SessionTime = V2SessionTime(0, 0),
    val title: String = "",
    val location: V2Location = V2Location(""),
    val subpath: String? = null,
    val revert: JsonObject? = null,
)

@Serializable
data class V2SessionTime(val created: Long = 0, val updated: Long = 0)

@Serializable
data class V2Location(val directory: String = "", @SerialName("workspaceID") val workspaceId: String? = null)

@Serializable
data class V2SessionResponse(val data: V2SessionInfo)

@Serializable
data class V2SessionsResponse(
    val data: List<V2SessionInfo> = emptyList(),
    val cursor: JsonObject? = null,
)

/** V2 SessionV2Info → App 的 [Session]（UI 层模型，缺失字段用默认值）。 */
internal fun V2SessionInfo.toSession(): Session = Session(
    id = id,
    projectId = projectId,
    parentId = parentId,
    title = title.takeIf { it.isNotBlank() },
    time = Session.Time(created = time.created, updated = time.updated),
    directory = location.directory,
    workspaceId = location.workspaceId,
    model = model?.let { Session.SessionModel(it.modelId, it.providerId, it.variant) },
)

/** `GET /api/skill` 的响应信封：`{location:{directory}, data:[SkillV2Info]}`。 */
@Serializable
data class V2SkillResponse(
    val data: List<V2SkillInfo> = emptyList(),
    val location: V2Location = V2Location(""),
)

/** V2 skill 条目：字段对齐 [Skill]，`location` 容忍字符串/对象两种写法。 */
@Serializable
data class V2SkillInfo(
    val name: String = "",
    val description: String? = null,
    val location: JsonElement? = null,
    val content: String? = null,
)

/** V2 SkillV2Info → App 的 [Skill]（`location` 为对象时取其 directory，缺失用 null）。 */
internal fun V2SkillInfo.toSkill(): Skill = Skill(
    name = name,
    description = description,
    location = when (val loc = location) {
        null -> null
        is JsonPrimitive -> loc.contentOrNull
        is JsonObject -> loc["directory"]?.jsonPrimitive?.contentOrNull
        else -> null
    },
    content = content,
)

// ============ Session ============

suspend fun StarBurstApi.listSessions(conn: ServerConnection, directory: String? = null): List<Session> {
    // V2：GET /api/session 返回 {data:[SessionV2Info], cursor}。
    // 分页：agent/opencode 服务端对 limit 有硬上限（200），一次请求拿不全时按 cursor.next
    // 逐页拉取全部会话（cursor 已自含 order/方向，续页不再传 order，避免 cursor+order 400）。
    val all = mutableListOf<Session>()
    var cursor: String? = null
    var pages = 0
    while (true) {
        val response = httpClient.get("${conn.baseUrl}/api/session") {
            conn.authHeader?.let { header("Authorization", it) }
            directory?.let { parameter("directory", it) }
            parameter("limit", "200")
            if (cursor != null) parameter("cursor", cursor) else parameter("order", "desc")
        }.body<V2SessionsResponse>()
        all += response.data.map { it.toSession() }
        pages++
        val next = response.cursor?.get("next")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        if (next == null || next == cursor || pages >= MAX_SESSION_PAGES) break
        cursor = next
    }
    return all
}

/** 会话列表分页安全上限：单页 200 × 20 = 4000 会话，防非法游标死循环。 */
private const val MAX_SESSION_PAGES = 20

suspend fun StarBurstApi.listSessionStatuses(
    conn: ServerConnection,
    directory: String? = null,
): Map<String, SessionStatus> {
    // V2：GET /api/session/active 返回 {data:{sessionId:状态对象}}。
    val payload: JsonObject = httpClient.get("${conn.baseUrl}/api/session/active") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
    }.body()
    return parseSessionStatuses(payload)
}

/**
 * 解析会话状态快照（V2 `/api/session/active` 的 `data`：sessionId → 状态对象）。
 *
 * 状态对象的状态类型读 `type` 或 `status` 字段（V2 两种写法都容忍）：busy → Busy，
 * retry → Retry（attempt/message/next 缺失按默认值降级），其余一律 Idle。
 */
internal fun parseSessionStatuses(payload: JsonObject): Map<String, SessionStatus> =
    (payload["data"] as? JsonObject ?: payload).mapNotNull { (sessionId, value) ->
        val status = value as? JsonObject ?: return@mapNotNull null
        val type = status["type"]?.jsonPrimitive?.contentOrNull
            ?: (status["status"] as? JsonPrimitive)?.contentOrNull
        sessionId to when (type) {
            "busy", "running" -> SessionStatus.Busy
            "retry" -> SessionStatus.Retry(
                attempt = status["attempt"]?.jsonPrimitive?.intOrNull ?: 0,
                message = status["message"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                next = status["next"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0,
            )
            else -> SessionStatus.Idle
        }
    }.toMap()

// 连接是否走 starburst-backend 镜像通道：镜像连接的 baseUrl 以 /api/opencode 结尾。
private fun ServerConnection.isMirrorChannel(): Boolean =
    baseUrl.trimEnd('/').endsWith(StarBurstGateway.BACKEND_API_PREFIX)

// 镜像通道的全局状态快照：V2 `/api/session/active` 不带 directory 时返回全部活跃会话状态
// （HTTP 200，可能为空表）。非 2xx（直连上游不支持无 directory 查询）返回 null，
// 调用方据此退回按目录拉取。
private suspend fun StarBurstApi.listSessionStatusesAggregatedOrNull(conn: ServerConnection): Map<String, SessionStatus>? {
    val resp = httpClient.get("${conn.baseUrl}/api/session/active") {
        conn.authHeader?.let { header("Authorization", it) }
    }
    if (!resp.status.isSuccess()) return null
    val payload = resp.body<JsonObject>()
    return parseSessionStatuses(payload)
}

/**
 * V2 `/api/session/active` 按 `?directory=` 路由到对应 workspace。
 * 这里按目录分组聚合查询，合并成完整的状态快照（busy/retry）。
 */
suspend fun StarBurstApi.listSessionStatusesForDirectories(
    conn: ServerConnection,
    directories: Collection<String>,
): Map<String, SessionStatus> {
    if (directories.isEmpty()) return emptyMap()
    // 仅镜像通道（useBackend）才支持一次无 directory 请求拿全局聚合快照，把 N×RTT
    // 扇出降成单次往返；直连服务器 不支持无 directory 查询，不再发聚合探针，
    // 直接按目录并发拉取。镜像通道返回空表（HTTP 200 空 {}）也不能当作「无活动」——
    // 可能是快照尚未聚合，仍回退按目录并发拉取。
    if (conn.isMirrorChannel()) {
        val aggregated = runCatching { listSessionStatusesAggregatedOrNull(conn) }
            .getOrNull()
            ?: emptyMap()
        if (aggregated.isNotEmpty()) return aggregated
    }
    return coroutineScope {
        directories.map { dir ->
            async {
                runCatching { listSessionStatuses(conn, directory = dir) }
                    .getOrElse { emptyMap() }
            }
        }.awaitAll().fold(emptyMap()) { acc, m -> acc + m }
    }
}

suspend fun StarBurstApi.getSession(conn: ServerConnection, sessionId: String, directory: String? = null): Session {
    // V2：GET /api/session/{id} 返回 {data: SessionV2Info}；
    // directory 走 query param（agent 的 parseLocation 只认 query，不认 x-starburst-directory 头）。
    return httpClient.get("${conn.baseUrl}/api/session/$sessionId") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
    }.body<V2SessionResponse>().data.toSession()
}

suspend fun StarBurstApi.listChildSessions(
    conn: ServerConnection,
    sessionId: String,
    directory: String? = null,
): List<Session> {
    // V2 无 children 端点：拉取 /api/session 列表后按 parentID 本地分组过滤。
    return httpClient.get("${conn.baseUrl}/api/session") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
        parameter("order", "desc")
        parameter("limit", "200")
    }.body<V2SessionsResponse>().data.filter { it.parentId == sessionId }.map { it.toSession() }
}

/** 列出该服务器的所有 skill（含 SKILL.md 内容，只读）。 */
suspend fun StarBurstApi.listSkills(conn: ServerConnection): List<Skill> {
    // V2：GET /api/skill 返回 {location, data:[SkillV2Info]}，映射为 App 的 [Skill]。
    return httpClient.get("${conn.baseUrl}/api/skill") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body<V2SkillResponse>().data.map { it.toSkill() }
}

/** Returns session info as raw JSON string (for export without re-serialization). */
suspend fun StarBurstApi.getSessionRaw(conn: ServerConnection, sessionId: String): String {
    // V2：GET /api/session/{id} 返回 {data: SessionV2Info} 信封，此处保留原始文本供导出。
    return httpClient.get("${conn.baseUrl}/api/session/$sessionId") {
        conn.authHeader?.let { header("Authorization", it) }
    }.bodyAsText()
}

suspend fun StarBurstApi.createSession(conn: ServerConnection, title: String? = null, parentId: String? = null, directory: String? = null): Session {
    val body = buildJsonObject {
        title?.let { put("title", JsonPrimitive(it)) }
        parentId?.let { put("parentID", JsonPrimitive(it)) }
        directory?.let {
            putJsonObject("location") { put("directory", JsonPrimitive(it)) }
        }
    }
    if (BuildConfig.DEBUG) Log.d("CreateSession", "createSession directory=$directory title=$title parentId=$parentId")
    // V2：POST /api/session 返回 {data: SessionV2Info}，directory 放 location.directory。
    return httpClient.post("${conn.baseUrl}/api/session") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }.body<V2SessionResponse>().data.toSession()
}

suspend fun StarBurstApi.deleteSession(conn: ServerConnection, sessionId: String): Boolean {
    val response = httpClient.delete("${conn.baseUrl}/api/session/$sessionId") {
        conn.authHeader?.let { header("Authorization", it) }
    }
    return response.status.isSuccess()
}

suspend fun StarBurstApi.updateSession(
    conn: ServerConnection,
    sessionId: String,
    title: String? = null,
    archive: Boolean? = null,
): Session {
    // 用 @Serializable 数据类而非 Map<String, Any>：Map 的 Any 值在 kotlinx 下无序列化器，
    // 可能被序列化成空/错误形状（实测 title 未被服务端应用）。
    // time.archived 必须显式携带：归档传当前毫秒，取消归档传 JsonNull（服务端判 nil 决定动作）。
    val body = SessionPatchBody(
        title = title?.takeIf { it.isNotBlank() },
        time = archive?.let {
            buildJsonObject {
                put("archived", if (it) JsonPrimitive(System.currentTimeMillis()) else JsonNull)
            }
        },
    )
    // V2：PATCH /api/session/{id}，返回 {data: SessionV2Info} 信封，拆出 data 再映射。
    if (BuildConfig.DEBUG) {
        val encodedBody = kotlinx.serialization.json.Json.encodeToString(SessionPatchBody.serializer(), body)
        Log.d(StarBurstApi.TAG, "updateSession -> ${conn.baseUrl}/api/session/$sessionId body=$encodedBody")
    }
    return httpClient.patch("${conn.baseUrl}/api/session/$sessionId") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }.let { response ->
        val text = response.bodyAsText()
        val obj = json.parseToJsonElement(text).jsonObject
        val infoObj = obj["data"] ?: obj
        json.decodeFromJsonElement<V2SessionInfo>(infoObj).toSession()
    }
}

/** V2 会话 PATCH 请求体：title / time.archived（归档毫秒或 null=取消归档）。 */
@Serializable
internal data class SessionPatchBody(
    @SerialName("title") val title: String? = null,
    @SerialName("time") val time: JsonObject? = null,
)

suspend fun StarBurstApi.abortSession(conn: ServerConnection, sessionId: String, directory: String? = null, workspaceId: String? = null): Boolean {
    // V2：POST /api/session/{id}/interrupt（V1 的 /abort 在 V2 面不存在）。
    val response = httpClient.post("${conn.baseUrl}/api/session/$sessionId/interrupt") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
        workspaceId?.let { parameter("workspace", it) }
    }
    return response.status.isSuccess()
}

suspend fun StarBurstApi.getSessionDiff(conn: ServerConnection, sessionId: String): List<FileDiff> {
    // V2 后端（starburst-agent）：GET /api/session/{id}/diff 返回 {sessionID, stat, diff:<raw unified diff>}。
    // 404/405 表示服务器无 V2 端点（V1-only）→ 回退 V1 /session/{id}/diff（数组形状）；
    // 其余 4xx/5xx（如非 git 仓库的 400）是 V2 服务器的正常拒绝，直接返回空，不再回退 V1。
    val v2 = try {
        httpClient.get("${conn.baseUrl}/api/session/$sessionId/diff") {
            conn.authHeader?.let { header("Authorization", it) }
        }
    } catch (e: Exception) {
        Log.w(StarBurstApi.TAG, "V2 session diff request failed: ${e.message}")
        null
    }
    if (v2 != null && v2.status.isSuccess()) {
        val body = v2.bodyAsText()
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val rawDiff = obj?.get("diff")?.jsonPrimitive?.contentOrNull
        return when {
            !rawDiff.isNullOrBlank() -> parseUnifiedDiff(rawDiff)
            // 兼容 V2 直接返回数组的情况。
            obj == null || obj.isEmpty() ->
                runCatching { json.decodeFromString<List<FileDiff>>(body) }.getOrDefault(emptyList())
            else -> emptyList()
        }
    }
    val status = v2?.status?.value
    if (v2 != null && status != null && status != 404 && status != 405) {
        // V2 服务器但本次 diff 失败（非 git 仓库等）：无 V1 端点可回退，静默返回空。
        if (BuildConfig.DEBUG) Log.d(StarBurstApi.TAG, "V2 session diff unavailable ($status)")
        return emptyList()
    }

    // V1 回退：/session/{id}/diff 直接返回 FileDiff 数组。
    return try {
        httpClient.get("${conn.baseUrl}/session/$sessionId/diff") {
            conn.authHeader?.let { header("Authorization", it) }
        }.body()
    } catch (e: Exception) {
        Log.w(StarBurstApi.TAG, "V1 session diff failed: ${e.message}")
        emptyList()
    }
}

/**
 * 把 `git diff` 的 unified diff 文本解析为 [FileDiff] 列表。
 * 识别 `diff --git` 分块、`---/+++` 文件头、`@@` hunk 行，
 * 统计每个文件的增删行数并保留原始 patch 文本。
 */
internal fun parseUnifiedDiff(raw: String): List<FileDiff> {
    if (raw.isBlank()) return emptyList()
    val result = mutableListOf<FileDiff>()
    val lines = raw.split('\n')
    var idx = 0
    while (idx < lines.size) {
        val line = lines[idx]
        if (!line.startsWith("diff --git ")) {
            idx++
            continue
        }
        val blockStart = idx
        var filePath = ""
        var status = "modified"
        var additions = 0
        var deletions = 0
        var sawHeader = false
        idx++
        while (idx < lines.size && !lines[idx].startsWith("diff --git ")) {
            val l = lines[idx]
            when {
                l.startsWith("+++ b/") -> {
                    filePath = l.removePrefix("+++ b/").trim()
                    sawHeader = true
                }
                l.startsWith("--- /dev/null") -> {
                    status = "added"
                    sawHeader = true
                }
                l.startsWith("--- a/") -> {
                    if (filePath.isBlank()) filePath = l.removePrefix("--- a/").trim()
                    sawHeader = true
                }
                l.startsWith("+++ /dev/null") -> status = "deleted"
                l.startsWith("+") && !l.startsWith("+++") -> additions++
                l.startsWith("-") && !l.startsWith("---") -> deletions++
            }
            idx++
        }
        if (!sawHeader && filePath.isBlank()) continue
        val patch = lines.subList(blockStart, idx).joinToString("\n")
        result.add(FileDiff(file = filePath, additions = additions, deletions = deletions, status = status, patch = patch))
    }
    return result
}

/**
 * Share a session, creating a shareable link.
 * V2：POST /api/session/{sessionId}/share 返回 {sessionID, url}；
* V1 回退：/session/{sessionId}/share 返回 Session（取 share.url）。
  *
  * V2 的分享发布在公共分享后端（见 [getSharedSession]），agent 仅返回相对路径
  * `/share/{shareId}`（该路由在 agent 上无页面）。相对路径在此归一为
  * `https://opncd.ai/s/{shareId}`，保证复制给用户的链接可直接访问。
  *
  * @return 分享链接 URL；无链接时返回 null。
  */
suspend fun StarBurstApi.shareSession(conn: ServerConnection, sessionId: String): String? {
    val v2 = httpClient.post("${conn.baseUrl}/api/session/$sessionId/share") {
        conn.authHeader?.let { header("Authorization", it) }
    }
    if (v2.status.isSuccess()) {
        val body = runCatching { json.parseToJsonElement(v2.bodyAsText()).jsonObject }.getOrNull()
        val raw = body?.get("url")?.jsonPrimitive?.contentOrNull
            ?: (body?.get("share") as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull
            ?: return null
        return normalizeShareUrl(raw)
    }
    // V1 回退
    val session: Session = httpClient.post("${conn.baseUrl}/session/$sessionId/share") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
    return session.share?.url
}

/**
 * 将分享链接归一为可直接访问的完整 URL。
 *
 * V2 agent 返回相对路径 `/share/{shareId}`（该路由在 agent 上无页面，分享实际
 * 托管在公共分享后端 [StarBurstApi.SHARE_BASE_URL]）。完整 URL 原样返回；
 * 相对路径取末段 shareId 拼出 `https://opncd.ai/s/{shareId}`；无法解析时原样返回。
 */
internal fun normalizeShareUrl(raw: String): String {
    if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
    val shareId = raw.trim('/').substringAfterLast('/').takeIf { it.isNotBlank() } ?: return raw
    return "${StarBurstApi.SHARE_BASE_URL}/s/$shareId"
}

/**
 * Unshare a session, removing the shareable link.
 * V2：DELETE /api/session/{sessionId}/share。
 */
suspend fun StarBurstApi.unshareSession(conn: ServerConnection, sessionId: String): Boolean {
    val v2 = httpClient.delete("${conn.baseUrl}/api/session/$sessionId/share") {
        conn.authHeader?.let { header("Authorization", it) }
    }
    if (v2.status.isSuccess()) return true
    // V1 回退
    return httpClient.delete("${conn.baseUrl}/session/$sessionId/share") {
        conn.authHeader?.let { header("Authorization", it) }
    }.status.isSuccess()
}

/**
 * 通过分享 ID 加载只读会话（标题 + 消息列表）。
 *
 * 分享内容托管在公开的分享后端（`https://opncd.ai`），读取接口无需鉴权：
 * `GET https://opncd.ai/api/share/{shareId}/data`，返回一组按类型区分的条目
 * （`session` / `message` / `part` / `session_diff` / `model`），本方法将其重组成 [SharedSession]。
 *
 * @param conn 服务器连接（分享读取不走本地服务器，此处仅保留以与其它 API 签名保持一致）
 * @param shareId 分享 ID（分享链接 `https://opncd.ai/s/{shareId}` 的末段）
 * @return 重组后的只读会话
 *
 * 分享读取走公开分享后端（与服务器的 V2 迁移无关）。 */
suspend fun StarBurstApi.getSharedSession(conn: ServerConnection, shareId: String): SharedSession {
    val items: List<ShareDataItem> = httpClient.get("${StarBurstApi.SHARE_BASE_URL}/api/share/$shareId/data").body()
    return assembleSharedSession(items)
}

/**
 * Summarize (compact) a session to reduce context.
 * V2：POST /api/session/{sessionId}/summarize（body 可为空，agent 忽略 providerID/modelID）。
 */
suspend fun StarBurstApi.summarizeSession(
    conn: ServerConnection,
    sessionId: String,
    providerId: String? = null,
    modelId: String? = null
): Boolean {
    val body = buildMap<String, String> {
        providerId?.let { put("providerID", it) }
        modelId?.let { put("modelID", it) }
    }
    val v2 = httpClient.post("${conn.baseUrl}/api/session/$sessionId/summarize") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    if (v2.status.isSuccess()) return true
    // V1 回退：/session/{sessionId}/summarize
    val v1 = httpClient.post("${conn.baseUrl}/session/$sessionId/summarize") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(mapOf("providerID" to (providerId ?: ""), "modelID" to (modelId ?: "")))
    }
    return v1.status.isSuccess()
}

/**
 * Compact a session: replace the conversation context with an LLM summary
 * (V2 真正的压缩端点，区别于 summarize 只返回摘要文本）。
 * POST /api/session/{sessionId}/compact → {data: {summary}}。
 *
 * @return true 表示压缩成功（LLM 摘要已写入 compaction 消息）。
 */
suspend fun StarBurstApi.compactSessionV2(
    conn: ServerConnection,
    sessionId: String,
    directory: String? = null,
): Boolean {
    val v2 = httpClient.post("${conn.baseUrl}/api/session/$sessionId/compact") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
        contentType(ContentType.Application.Json)
        setBody("{}")
    }
    if (v2.status.isSuccess()) return true
    // V1 回退：/session/{sessionId}/summarize 承担压缩语义。
    return httpClient.post("${conn.baseUrl}/session/$sessionId/summarize") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody("{}")
    }.status.isSuccess()
}

/**
 * Revert (undo) messages starting from the given messageId.
 * POST /api/session/{sessionId}/revert/stage + POST /api/session/{sessionId}/revert/commit
 *
 * V2 两段式：先 stage 暂存回退点，再 commit 原子提交；两段均成功后再回拉最新会话返回。
 */
suspend fun StarBurstApi.revertSession(conn: ServerConnection, sessionId: String, messageId: String, directory: String? = null, workspaceId: String? = null): Session {
    val staged = httpClient.post("${conn.baseUrl}/api/session/$sessionId/revert/stage") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
        workspaceId?.let { parameter("workspace", it) }
        contentType(ContentType.Application.Json)
        setBody(mapOf("messageID" to messageId))
    }
    if (!staged.status.isSuccess()) {
        throw java.io.IOException("revert session stage failed: ${staged.status}")
    }
    val committed = httpClient.post("${conn.baseUrl}/api/session/$sessionId/revert/commit") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
        workspaceId?.let { parameter("workspace", it) }
        contentType(ContentType.Application.Json)
        setBody("{}")
    }
    if (!committed.status.isSuccess()) {
        throw java.io.IOException("revert session commit failed: ${committed.status}")
    }
    // V2 stage/commit 不返回会话对象，回拉一次最新 Session 供调用方 upsert。
    return getSession(conn, sessionId)
}

/**
 * Unrevert (redo) the last reverted message in a session.
 * POST /api/session/{sessionId}/revert/clear
 *
 * V2 的 clear 即 V1 的 unrevert；端点返回 204，故以是否成功返回 Boolean。
 */
suspend fun StarBurstApi.unrevertSession(conn: ServerConnection, sessionId: String, directory: String? = null, workspaceId: String? = null): Boolean {
    val response = httpClient.post("${conn.baseUrl}/api/session/$sessionId/revert/clear") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
        workspaceId?.let { parameter("workspace", it) }
        contentType(ContentType.Application.Json)
        setBody("{}")
    }
    return response.status.isSuccess()
}

/**
 * Fork a session (create a new session from a message point).
 *
 * V2 `POST /api/session/{sessionId}/fork` 优先（agent 有，响应 `{data:{...}}` 信封），
 * 失败再回退 V1 `POST /session/{sessionId}/fork`（opencode 形状，裸 Session）。
 * 此前只打 V1，而 agent 未注册 V1 `/session/` 子树 → 404，fork 功能在 agent 上完全不可用。
 */
suspend fun StarBurstApi.forkSession(conn: ServerConnection, sessionId: String, messageId: String? = null): Session {
    val body = buildMap<String, String> {
        messageId?.let { put("messageID", it) }
    }
    val v2 = httpClient.post("${conn.baseUrl}/api/session/$sessionId/fork") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    if (v2.status.isSuccess()) {
        runCatching {
            json.parseToJsonElement(v2.bodyAsText()).jsonObject.get("data")?.let {
                json.decodeFromJsonElement<Session>(it)
            }
        }.getOrNull()?.let { return it }
    }
    // V1 回退（opencode / 旧 V2 后端）
    return httpClient.post("${conn.baseUrl}/session/$sessionId/fork") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }.body()
}

/**
 * Execute a server-side command in a session.
 * POST /session/{sessionId}/command
 * Body: { command: String, arguments: String }
 *
 * agent 尚未实现该端点，联调再定；暂保留 V1 路径。
 */
suspend fun StarBurstApi.executeCommand(
    conn: ServerConnection,
    sessionId: String,
    command: String,
    arguments: String = "",
    directory: String? = null
): Boolean {
    // agent 尚未实现该端点，联调再定；暂保留 V1 路径。
    val response = httpClient.post("${conn.baseUrl}/session/$sessionId/command") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { header("x-starburst-directory", it) }
        contentType(ContentType.Application.Json)
        setBody(mapOf("command" to command, "arguments" to arguments))
    }
    return response.status.isSuccess()
}

/**
 * Run a shell command in a session.
 * POST /session/{sessionId}/shell
 *
 * agent 尚未实现该端点，联调再定；暂保留 V1 路径。
 */
suspend fun StarBurstApi.runShellCommand(
    conn: ServerConnection,
    sessionId: String,
    command: String,
    agent: String,
    model: ModelSelection? = null,
    directory: String? = null
): Boolean {
    val response = httpClient.post("${conn.baseUrl}/session/$sessionId/shell") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { header("x-starburst-directory", it) }
        contentType(ContentType.Application.Json)
        setBody(
            ShellRequest(
                agent = agent,
                model = model,
                command = command
            )
        )
    }
    return response.status.isSuccess()
}

// ===== PTY：V2 位于 /api/pty，create/remove/size 已迁移；WS 连接（openPtySocket）走
//       connect-token 票据流（x-opencode-ticket 头），兼容 V1 无票据直连 =====

suspend fun StarBurstApi.createPty(
    conn: ServerConnection,
    title: String? = null,
    cwd: String? = null,
    directory: String? = null
): PtyInfo {
    if (BuildConfig.DEBUG) {
        Log.d("StarBurstApi", "createPty: request")
    }
    // V2：POST /api/pty，目录参数走 query（x-starburst-directory 头在 V2 无效）。
    val response = httpClient.post("${conn.baseUrl}/api/pty") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
        contentType(ContentType.Application.Json)
        setBody(PtyCreateRequest(title = title, cwd = cwd))
    }
    val body = response.bodyAsText()
    if (BuildConfig.DEBUG) {
        Log.d("StarBurstApi", "createPty: status=${response.status}")
    }
    if (!response.status.isSuccess()) {
        throw java.io.IOException("createPty failed: ${response.status}")
    }

    val info = parsePtyInfoFromCreateResponse(body, title, cwd)
    if (BuildConfig.DEBUG) {
        Log.d("StarBurstApi", "createPty: response parsed")
    }
    return info
}

suspend fun StarBurstApi.removePty(conn: ServerConnection, ptyId: String): Boolean {
    // V2：DELETE /api/pty/{id} → 204。
    val response = httpClient.delete("${conn.baseUrl}/api/pty/$ptyId") {
        conn.authHeader?.let { header("Authorization", it) }
    }
    return response.status.isSuccess()
}

suspend fun StarBurstApi.updatePtySize(
    conn: ServerConnection,
    ptyId: String,
    cols: Int,
    rows: Int,
    directory: String? = null
): Boolean {
    val body = PtyUpdateRequest(size = PtySize(rows = rows, cols = cols))
    if (BuildConfig.DEBUG) {
        Log.d("StarBurstApi", "updatePtySize: ${cols}x$rows")
    }
    // V2：PUT /api/pty/{id}，目录参数走 query。
    val response = httpClient.put("${conn.baseUrl}/api/pty/$ptyId") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    if (BuildConfig.DEBUG) {
        Log.d("StarBurstApi", "updatePtySize: status=${response.status}")
    }
    return response.status.isSuccess()
}

suspend fun StarBurstApi.openPtySocket(
    conn: ServerConnection,
    ptyId: String,
    cursor: Int = -1,
    directory: String? = null
): PtySocket {
    val wsBase = when {
        conn.baseUrl.startsWith("https://") -> conn.baseUrl.replaceFirst("https://", "wss://")
        conn.baseUrl.startsWith("http://") -> conn.baseUrl.replaceFirst("http://", "ws://")
        else -> conn.baseUrl
    }
    val ticket = runCatching { createPtyConnectToken(conn, ptyId) }.getOrNull()
    val session = httpClient.webSocketSession {
        method = HttpMethod.Get
        url("$wsBase/api/pty/$ptyId/connect?cursor=$cursor")
        conn.authHeader?.let { header("Authorization", it) }
        ticket?.let { header("x-opencode-ticket", it) }
        directory?.let { header("x-starburst-directory", it) }
    }
    return PtySocket(session)
}

/**
 * 获取 PTY WebSocket 连接票据：`POST /api/pty/{id}/connect-token`（V2，需 `x-opencode-ticket: 1` 头）。
 * 返回 `{data:{ticket, expires_in}}`（agent）/`{ticket, expiresIn}`（opencode 真值）均可，取 `ticket`。
 * 票据一次性：connect 成功即失效，每次重连需重新获取。获取失败返回 null（由调用方忽略，保持向后兼容）。
 */
suspend fun StarBurstApi.createPtyConnectToken(conn: ServerConnection, ptyId: String): String? {
    val response = httpClient.post("${conn.baseUrl}/api/pty/$ptyId/connect-token") {
        conn.authHeader?.let { header("Authorization", it) }
        header("x-opencode-ticket", "1")
    }
    if (!response.status.isSuccess()) return null
    val body = runCatching { json.parseToJsonElement(response.bodyAsText()).jsonObject }.getOrNull() ?: return null
    val data = (body["data"] as? JsonObject) ?: body
    return data["ticket"]?.jsonPrimitive?.contentOrNull
}