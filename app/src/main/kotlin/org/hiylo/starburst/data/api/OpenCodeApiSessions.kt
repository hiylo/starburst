/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : OpenCodeApiSessions.kt
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ============ Session ============

suspend fun OpenCodeApi.listSessions(conn: ServerConnection, directory: String? = null): List<Session> {
    // 使用 /experimental/session 列出所有 project 的根会话；
    // /session 只返回当前 project（x-starburst-directory）的会话，会导致其它项目的会话缺失。
    return httpClient.get("${conn.baseUrl}/experimental/session") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { header("x-starburst-directory", it) }
        parameter("roots", "true")
    }.body()
}

suspend fun OpenCodeApi.listSessionStatuses(
    conn: ServerConnection,
    directory: String? = null,
): Map<String, SessionStatus> {
    val payload: JsonObject = httpClient.get("${conn.baseUrl}/session/status") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { header("x-starburst-directory", it) }
    }.body()
    return payload.mapValues { (_, value) ->
        val status = value.jsonObject
        when (status["type"]?.jsonPrimitive?.content) {
            "busy" -> SessionStatus.Busy
            "retry" -> SessionStatus.Retry(
                attempt = status["attempt"]?.jsonPrimitive?.intOrNull ?: 0,
                message = status["message"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                next = status["next"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0,
            )
            else -> SessionStatus.Idle
        }
    }
}

suspend fun OpenCodeApi.getSession(conn: ServerConnection, sessionId: String, directory: String? = null): Session {
    return httpClient.get("${conn.baseUrl}/session/$sessionId") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { header("x-starburst-directory", it) }
    }.body()
}

suspend fun OpenCodeApi.listChildSessions(
    conn: ServerConnection,
    sessionId: String,
    directory: String? = null,
): List<Session> {
    return httpClient.get("${conn.baseUrl}/session/$sessionId/children") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { header("x-starburst-directory", it) }
    }.body()
}

/** 列出该服务器的所有 skill（含 SKILL.md 内容，只读）。 */
suspend fun OpenCodeApi.listSkills(conn: ServerConnection): List<Skill> {
    return httpClient.get("${conn.baseUrl}/skill") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
}

/** Returns session info as raw JSON string (for export without re-serialization). */
suspend fun OpenCodeApi.getSessionRaw(conn: ServerConnection, sessionId: String): String {
    return httpClient.get("${conn.baseUrl}/session/$sessionId") {
        conn.authHeader?.let { header("Authorization", it) }
    }.bodyAsText()
}

suspend fun OpenCodeApi.createSession(conn: ServerConnection, title: String? = null, parentId: String? = null, directory: String? = null): Session {
    val body = buildMap<String, String> {
        title?.let { put("title", it) }
        parentId?.let { put("parentID", it) }
    }
    return httpClient.post("${conn.baseUrl}/session") {
        conn.authHeader?.let { header("Authorization", it) }
        // 工作目录走 query 参数（opencode 服务据此建会话）；旧 header 保留以兼容旧版。
        directory?.let {
            parameter("directory", it)
            header("x-starburst-directory", it)
        }
        contentType(ContentType.Application.Json)
        setBody(body)
    }.body()
}

suspend fun OpenCodeApi.deleteSession(conn: ServerConnection, sessionId: String): Boolean {
    val response = httpClient.delete("${conn.baseUrl}/session/$sessionId") {
        conn.authHeader?.let { header("Authorization", it) }
    }
    return response.status.isSuccess()
}

suspend fun OpenCodeApi.updateSession(
    conn: ServerConnection,
    sessionId: String,
    title: String? = null,
    archive: Boolean? = null,
): Session {
    val body = buildMap {
        title?.let { put("title", it) }
        // opencode 服务端的归档字段是嵌套的 time.archived（毫秒时间戳），
        // 归档传当前时间、取消归档传 null，而非顶层 archive 布尔。
        archive?.let {
            put("time", mapOf("archived" to if (it) System.currentTimeMillis() else null))
        }
    }
    return httpClient.patch("${conn.baseUrl}/session/$sessionId") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }.body()
}

suspend fun OpenCodeApi.abortSession(conn: ServerConnection, sessionId: String, directory: String? = null): Boolean {
    val response = httpClient.post("${conn.baseUrl}/session/$sessionId/abort") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { header("x-starburst-directory", it) }
    }
    return response.status.isSuccess()
}

suspend fun OpenCodeApi.getSessionDiff(conn: ServerConnection, sessionId: String): List<FileDiff> {
    return httpClient.get("${conn.baseUrl}/session/$sessionId/diff") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
}

/**
 * Share a session, creating a shareable link.
 * POST /session/{sessionId}/share
 */
suspend fun OpenCodeApi.shareSession(conn: ServerConnection, sessionId: String): Session {
    return httpClient.post("${conn.baseUrl}/session/$sessionId/share") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
}

/**
 * Unshare a session, removing the shareable link.
 * DELETE /session/{sessionId}/share
 */
suspend fun OpenCodeApi.unshareSession(conn: ServerConnection, sessionId: String): Session {
    return httpClient.delete("${conn.baseUrl}/session/$sessionId/share") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
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
 */
suspend fun OpenCodeApi.getSharedSession(conn: ServerConnection, shareId: String): SharedSession {
    val items: List<ShareDataItem> = httpClient.get("${OpenCodeApi.SHARE_BASE_URL}/api/share/$shareId/data").body()
    return assembleSharedSession(items)
}

/**
 * Summarize (compact) a session to reduce context.
 * POST /session/{sessionId}/summarize
 */
suspend fun OpenCodeApi.summarizeSession(
    conn: ServerConnection,
    sessionId: String,
    providerId: String,
    modelId: String
): Boolean {
    val response = httpClient.post("${conn.baseUrl}/session/$sessionId/summarize") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(mapOf("providerID" to providerId, "modelID" to modelId))
    }
    return response.status.isSuccess()
}

/**
 * Revert (undo) messages starting from the given messageId.
 * POST /session/{sessionId}/revert
 */
suspend fun OpenCodeApi.revertSession(conn: ServerConnection, sessionId: String, messageId: String): Session {
    return httpClient.post("${conn.baseUrl}/session/$sessionId/revert") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(mapOf("messageID" to messageId))
    }.body()
}

/**
 * Unrevert (redo) the last reverted message in a session.
 * POST /session/{sessionId}/unrevert
 */
suspend fun OpenCodeApi.unrevertSession(conn: ServerConnection, sessionId: String): Session {
    return httpClient.post("${conn.baseUrl}/session/$sessionId/unrevert") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
}

/**
 * Fork a session (create a new session from a message point).
 * POST /session/{sessionId}/fork
 */
suspend fun OpenCodeApi.forkSession(conn: ServerConnection, sessionId: String, messageId: String? = null): Session {
    val body = buildMap<String, String> {
        messageId?.let { put("messageID", it) }
    }
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
 */
suspend fun OpenCodeApi.executeCommand(
    conn: ServerConnection,
    sessionId: String,
    command: String,
    arguments: String = "",
    directory: String? = null
): Boolean {
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
 */
suspend fun OpenCodeApi.runShellCommand(
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

suspend fun OpenCodeApi.createPty(
    conn: ServerConnection,
    title: String? = null,
    cwd: String? = null,
    directory: String? = null
): PtyInfo {
    if (BuildConfig.DEBUG) {
        Log.d("OpenCodeApi", "createPty: request")
    }
    val response = httpClient.post("${conn.baseUrl}/pty") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { header("x-starburst-directory", it) }
        contentType(ContentType.Application.Json)
        setBody(PtyCreateRequest(title = title, cwd = cwd))
    }
    val body = response.bodyAsText()
    if (BuildConfig.DEBUG) {
        Log.d("OpenCodeApi", "createPty: status=${response.status}")
    }
    if (!response.status.isSuccess()) {
        throw java.io.IOException("createPty failed: ${response.status}")
    }

    val info = parsePtyInfoFromCreateResponse(body, title, cwd)
    if (BuildConfig.DEBUG) {
        Log.d("OpenCodeApi", "createPty: response parsed")
    }
    return info
}

suspend fun OpenCodeApi.removePty(conn: ServerConnection, ptyId: String): Boolean {
    val response = httpClient.delete("${conn.baseUrl}/pty/$ptyId") {
        conn.authHeader?.let { header("Authorization", it) }
    }
    return response.status.isSuccess()
}

suspend fun OpenCodeApi.updatePtySize(
    conn: ServerConnection,
    ptyId: String,
    cols: Int,
    rows: Int,
    directory: String? = null
): Boolean {
    val body = PtyUpdateRequest(size = PtySize(rows = rows, cols = cols))
    if (BuildConfig.DEBUG) {
        Log.d("OpenCodeApi", "updatePtySize: ${cols}x$rows")
    }
    val response = httpClient.put("${conn.baseUrl}/pty/$ptyId") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { header("x-starburst-directory", it) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    if (BuildConfig.DEBUG) {
        Log.d("OpenCodeApi", "updatePtySize: status=${response.status}")
    }
    return response.status.isSuccess()
}

suspend fun OpenCodeApi.openPtySocket(
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
    val session = httpClient.webSocketSession {
        method = HttpMethod.Get
        url("$wsBase/pty/$ptyId/connect?cursor=$cursor")
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { header("x-starburst-directory", it) }
    }
    return PtySocket(session)
}