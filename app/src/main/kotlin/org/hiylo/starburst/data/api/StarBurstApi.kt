/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : StarBurstApi.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.data.repository.SettingsRepository
import org.hiylo.starburst.domain.model.MessageWithParts
import org.hiylo.starburst.domain.model.ServerHealth
import org.hiylo.starburst.domain.model.SharedMessage
import org.hiylo.starburst.domain.model.SharedSession
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.readAvailable
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds resolved connection info for a server.
 * Create one via [ServerConnection.from] and pass it to every API / SSE call.
 */
data class ServerConnection(
    val baseUrl: String,
    val authHeader: String?
) {
    companion object {
        fun from(url: String, username: String = "starburst", password: String? = null): ServerConnection {
            val base = url.trimEnd('/')
            val auth = if (password != null) {
                val credentials = "$username:$password"
                "Basic ${Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))}"
            } else {
                null
            }
            return ServerConnection(base, auth)
        }
    }
}

class ServerAuthenticationException(val statusCode: Int) : Exception("Server authentication failed (HTTP $statusCode)")

class ServerHealthHttpException(val statusCode: Int) : Exception("Server health check failed (HTTP $statusCode)")

internal fun healthStatusException(statusCode: Int): Exception? = when (statusCode) {
    in 200..299 -> null
    401, 403 -> ServerAuthenticationException(statusCode)
    else -> ServerHealthHttpException(statusCode)
}

/**
 * StarBurst REST API Client
 *
 * All methods take a [ServerConnection] so the client is stateless
 * and safe to use for multiple servers concurrently.
 *
 * The functional API methods live in this package as extension functions
 * (see StarBurstApiSessions.kt / StarBurstApiMessages.kt / StarBurstApiMeta.kt / StarBurstApiFiles.kt),
 * so this file only keeps the class skeleton plus shared infrastructure helpers.
 */
@Singleton
class StarBurstApi @Inject constructor(
    internal val httpClient: HttpClient,
    internal val json: Json,
    internal val settingsRepository: SettingsRepository,
    internal val messageImageCache: MessageImageCache,
) {
    companion object {
        internal const val TAG = "StarBurstApi"
        internal const val BYTES_PER_MEGABYTE = 1024L * 1024L

        /** 会话分享内容的只读后端基础地址；分享数据的读取与本地 服务器无关。 */
        internal const val SHARE_BASE_URL = "https://opncd.ai"
    }

    // ============ Global ============

    /**
     * 探测服务器健康状态。
     * V2 `GET /api/health`：响应仅 `{"healthy":true}`（无 version 字段），
     * `ServerHealth.version` 保持为空；真值源 1.18.30 实测 200。
     */
    suspend fun getHealth(conn: ServerConnection): ServerHealth {
        val response = httpClient.get("${conn.baseUrl}/api/health") {
            conn.authHeader?.let { header("Authorization", it) }
        }
        healthStatusException(response.status.value)?.let { throw it }
        return response.body()
    }

    /**
     * 获取服务器路径（home 目录等）。
     *
     * - `directory` 走 V2 `GET /api/location`（LocationInfo `{directory, workspaceID?, project}`，
     *   部分环境套 `{data:{...}}` 信封，宽容解析）；
     * - `home` 走 V1 `GET /path`（starburst-agent 与 opencode 都在这里返回 `home`）。
     *
     * 此前 V2 拿不到 home 就**拿 directory 顶替**，于是 `serverHomeDirectory` 变成工作区
     * 目录，路径折叠（[org.hiylo.starburst.ui.screens.sessions.SessionPathFormatter.display]）
     * 判定「目录 == home」把整条路径缩成一个 `~` —— 聊天顶栏只剩一个波浪号，信息全丢。
     * 现在取不到真实 home 就**留空**：留空即不折叠，显示完整路径（信息多于错误的简写）。
     */
    suspend fun getServerPaths(conn: ServerConnection): ServerPaths {
        val location = runCatching {
            val response = httpClient.get("${conn.baseUrl}/api/location") {
                conn.authHeader?.let { header("Authorization", it) }
            }
            healthStatusException(response.status.value)?.let { throw it }
            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            // 兼容 `{data:{...}}` 与顶层 LocationInfo 两种响应形状，directory 优先取信封内的值。
            val directoryElement = (root["data"] as? JsonObject)?.get("directory") ?: root["directory"]
            val homeElement = (root["data"] as? JsonObject)?.get("home") ?: root["home"]
            ServerPaths(
                home = homeElement?.jsonPrimitive?.contentOrNull.orEmpty(),
                directory = directoryElement?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }.getOrElse { e ->
            Log.w(TAG, "getServerPaths: 解析 V2 /api/location 失败", e)
            ServerPaths()
        }
        if (location.home.isNotBlank()) return location
        // V2 的 LocationInfo 不含 home（starburst-agent 实测只有 directory/project），
        // 回退 V1 /path 取真实 home；仍取不到就保持空串 → 上游不做 `~` 折叠。
        val v1 = runCatching {
            val response = httpClient.get("${conn.baseUrl}/path") {
                conn.authHeader?.let { header("Authorization", it) }
            }
            if (!response.status.isSuccess()) return@runCatching null
            json.parseToJsonElement(response.bodyAsText()).jsonObject
        }.getOrNull() ?: return location
        val v1Home = v1["home"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val v1Dir = v1["directory"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return location.copy(
            home = v1Home,
            directory = location.directory.ifBlank { v1Dir },
        )
    }

    // ============ Shared infrastructure helpers ============

    /** 将分享后端返回的条目流重组成只读会话（session + message + text part）。 */
    internal fun assembleSharedSession(items: List<ShareDataItem>): SharedSession {
        val sessionObj = items.firstOrNull { it.type == "session" }?.data as? JsonObject
        val sessionId = sessionObj?.get("id")?.jsonPrimitive?.contentOrNull.orEmpty()
        val title = sessionObj?.get("title")?.jsonPrimitive?.contentOrNull
        val createdAt = (sessionObj?.get("time") as? JsonObject)?.get("created")?.jsonPrimitive?.longOrNull

        val textByMessage = items.asSequence()
            .filter { it.type == "part" }
            .mapNotNull { it.data as? JsonObject }
            .filter { part ->
                part["type"]?.jsonPrimitive?.contentOrNull == "text" &&
                    part["synthetic"]?.jsonPrimitive?.contentOrNull != "true" &&
                    part["ignored"]?.jsonPrimitive?.contentOrNull != "true"
            }
            .groupBy { it["messageID"]?.jsonPrimitive?.contentOrNull.orEmpty() }
            .mapValues { (_, parts) ->
                parts.mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }.joinToString("\n")
            }

        val messages = items.asSequence()
            .filter { it.type == "message" }
            .mapNotNull { it.data as? JsonObject }
            .mapNotNull { message ->
                val messageId = message["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val time = message["time"] as? JsonObject
                SharedMessage(
                    id = messageId,
                    role = message["role"]?.jsonPrimitive?.contentOrNull ?: "user",
                    text = textByMessage[messageId].orEmpty(),
                    createdAt = time?.get("created")?.jsonPrimitive?.longOrNull,
                    completedAt = time?.get("completed")?.jsonPrimitive?.longOrNull,
                )
            }
            .sortedBy { it.createdAt }
            .toList()

        return SharedSession(id = sessionId, title = title, createdAt = createdAt, messages = messages)
    }

    internal fun parsePtyInfoFromCreateResponse(body: String, title: String?, cwd: String?): PtyInfo {
        val trimmed = body.trim()

        // Most servers return the full PtyInfo object.
        runCatching { return json.decodeFromString(PtyInfo.serializer(), trimmed) }

        // Some local builds return only an id or wrap it in data/pty.
        val id = extractPtyIdFromResponse(trimmed)
            ?: throw java.io.IOException("createPty: could not parse PTY id")

        return PtyInfo(
            id = id,
            title = title ?: "Tab",
            command = "/bin/sh",
            args = emptyList(),
            cwd = cwd ?: "/",
            status = "running",
            pid = 0,
        )
    }

    internal fun extractPtyIdFromResponse(responseBody: String): String? {
        // Raw string id: "pty_xxx" or pty_xxx
        val plain = responseBody.removeSurrounding("\"").trim()
        if (plain.startsWith("pty_")) return plain

        return runCatching {
            val root = json.parseToJsonElement(responseBody)
            findPtyId(root)
        }.getOrNull()
    }

    internal fun findPtyId(element: JsonElement): String? {
        val obj = element as? JsonObject ?: return null

        obj["id"]?.jsonPrimitive?.contentOrNull?.let {
            if (it.startsWith("pty_")) return it
        }

        obj["pty"]?.let { nested ->
            findPtyId(nested)?.let { return it }
        }
        obj["data"]?.let { nested ->
            findPtyId(nested)?.let { return it }
        }
        obj["result"]?.let { nested ->
            findPtyId(nested)?.let { return it }
        }

        return null
    }

    @OptIn(ExperimentalSerializationApi::class)
    internal suspend fun listMessagesPage(
        conn: ServerConnection,
        sessionId: String,
        limit: Int?,
        before: String?,
        directory: String?,
        maxResponseBytes: Long,
    ): MessagePage {
        return listMessagesPageLoop(conn, sessionId, limit, before, directory, maxResponseBytes)
    }

    /**
     * Fetches one page, halving the limit whenever the response exceeds the size cap
     * (loop instead of recursion, and aborts mid-download once the cap is crossed so
     * oversized chunked responses don't fill the disk first).
     */
    @OptIn(ExperimentalSerializationApi::class)
    internal suspend fun listMessagesPageLoop(
        conn: ServerConnection,
        sessionId: String,
        initialLimit: Int?,
        before: String?,
        directory: String?,
        maxResponseBytes: Long,
    ): MessagePage {
        var currentLimit = initialLimit

        while (true) {
            // V2：GET /api/session/{id}/message，分页用 cursor（非 before），返回 {data, cursor}。
            val response = httpClient.get("${conn.baseUrl}/api/session/$sessionId/message") {
                conn.authHeader?.let { header("Authorization", it) }
                currentLimit?.let { parameter("limit", it) }
                before?.let { parameter("cursor", it) }
                directory?.let { parameter("directory", it) }
            }
            val contentLength = response.contentLength()

            val canShrink = currentLimit != null && currentLimit > 1
            if (canShrink && contentLength != null && contentLength > maxResponseBytes) {
                response.bodyAsChannel().cancel(null)
                currentLimit = (currentLimit / 2).coerceAtLeast(1)
                Log.w(TAG, "Reducing message page limit from ${currentLimit * 2} to $currentLimit ($contentLength bytes) for session $sessionId")
                continue
            }

            val rawFile = withContext(Dispatchers.IO) { File.createTempFile("oc-messages-", ".json") }
            try {
                var downloaded = 0L
                val aborted = withContext(Dispatchers.IO) {
                    try {
                        FileOutputStream(rawFile).use { output ->
                            val channel = response.bodyAsChannel()
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (!channel.isClosedForRead) {
                                val read = channel.readAvailable(buffer)
                                if (read < 0) break
                                if (read > 0) {
                                    output.write(buffer, 0, read)
                                    downloaded += read
                                    if (canShrink && downloaded > maxResponseBytes) {
                                        throw OversizedMessageResponse(canShrink)
                                    }
                                }
                            }
                        }
                        false
                    } catch (e: OversizedMessageResponse) {
                        e.canShrink
                    }
                }
                if (aborted) {
                    response.bodyAsChannel().cancel(null)
                    val shrunk = currentLimit?.let { (it / 2).coerceAtLeast(1) } ?: currentLimit
                    currentLimit = shrunk
                    Log.w(TAG, "Aborted oversized message response at $downloaded bytes; retrying with limit=$currentLimit for session $sessionId")
                    continue
                }

                val v2Resp = withContext(Dispatchers.IO) {
                    FileInputStream(rawFile).use { json.decodeFromStream<V2MessagesResponse>(it) }
                }
                return MessagePage(
                    messages = v2Resp.data.map { it.toMessageWithParts(sessionId) },
                    nextCursor = v2Resp.cursor?.get("next")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                )
            } finally {
                withContext(Dispatchers.IO) {
                    rawFile.delete()
                }
            }
        }
    }

    /** Internal sentinel used to abort a page download once the size cap is crossed. */
    private class OversizedMessageResponse(val canShrink: Boolean) : RuntimeException()

    internal suspend fun updateMcpConnection(
        conn: ServerConnection,
        name: String,
        connect: Boolean,
    ): Boolean {
        val action = if (connect) "connect" else "disconnect"
        val response = httpClient.post("${conn.baseUrl}/mcp/${name.encodeURLPathPart()}/$action") {
            conn.authHeader?.let { header("Authorization", it) }
        }
        if (!response.status.isSuccess()) throw RuntimeException("MCP $action failed: ${response.status}")
        return response.body()
    }
}

class PtySocket(
    private val session: ClientWebSocketSession
) {
    suspend fun send(input: String) {
        session.send(input)
    }

    suspend fun close() {
        session.close(CloseReason(CloseReason.Codes.NORMAL, "closed"))
    }

    suspend fun readLoop(onText: suspend (String) -> Unit) {
        for (frame in session.incoming) {
            when (frame) {
                is Frame.Text -> onText(frame.readText())
                is Frame.Binary -> {
                    val data = frame.data
                    // Server sends cursor metadata as 0x00 + JSON. Skip it.
                    if (data.isNotEmpty() && data[0].toInt() == 0) continue
                    onText(data.toString(Charsets.UTF_8))
                }
                else -> { /* ignore */ }
            }
        }
    }
}
