/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BackendPushListener.kt
 * Date : 2026/09/14 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.backend

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hiylo.starburst.domain.model.SessionStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * starburst-backend 主动推送的会话事件（后端 `/api/ws` 广播的 `session.event`）。
 *
 * 后端广播结构：`{"type":"session.event","payload":{"sessionId":..,"eventType":..,"payload":<原始事件>}}`。
 *
 * @author Hsi Chu
 * @since V1.0
 */
data class PushSessionEvent(
    val sessionId: String,
    val eventType: String,
    val payload: JsonObject = JsonObject(emptyMap()),
) {
    /** 从原始事件里宽容提取「问题文本」（question.asked）。取不到返回 null。 */
    fun questionText(): String? = extractAny(listOf("question", "text"), arrayKeys = listOf("questions"))

    /** 从 `question.replied`/`question.rejected` 事件里提取问题请求 id（requestID/id）。 */
    fun questionId(): String? = extractAny(listOf("requestID", "requestId", "id"))

    /** 从 `permission.replied`/`permission.denied`/`permission.granted` 事件里提取授权请求 id。 */
    fun permissionId(): String? = extractAny(listOf("requestID", "requestId", "id"))

    /** 从原始事件里宽容提取「权限类型」（permission.asked）。 */
    fun permission(): String? = extractAny(listOf("permission", "tool", "name"))

    /** 从 `session.status` 事件解析会话状态（properties.status.type）。 */
    fun status(): SessionStatus? {
        val map = container() ?: return null
        val st = map["status"]
        val type = when (st) {
            is JsonObject -> st["type"]?.jsonPrimitive?.contentOrNull
            is JsonPrimitive -> st.contentOrNull
            else -> null
        }
        return when (type) {
            "busy" -> SessionStatus.Busy
            "idle" -> SessionStatus.Idle
            "retry" -> SessionStatus.Retry(
                attempt = (st as? JsonObject)?.get("attempt")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                message = (st as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty(),
                next = (st as? JsonObject)?.get("next")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0,
            )
            else -> null
        }
    }

    /** 从原始事件里宽容提取「错误摘要」（session.error）。 */
    fun errorMessage(): String? {
        val map = container() ?: return null
        val err = map["error"]
        when (err) {
            is JsonObject -> err["message"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
            is JsonPrimitive -> err.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
            else -> {}
        }
        return null
    }

    /** 定位事件主体容器：优先 `properties`/`data`，否则用 `payload` 自身。 */
    private fun container(): JsonObject? {
        payload["properties"]?.jsonObject?.let { return it }
        payload["data"]?.jsonObject?.let { return it }
        payload["payload"]?.jsonObject?.let { return it }
        return payload
    }

    private fun extractAny(keys: List<String>, arrayKeys: List<String> = emptyList()): String? {
        val map = container() ?: return null
        for (k in keys) {
            map[k]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        }
        for (ak in arrayKeys) {
            map[ak]?.jsonArray?.firstOrNull()?.jsonObject?.let { first ->
                keys.firstNotNullOfOrNull { first[it]?.jsonPrimitive?.contentOrNull?.takeIf { v -> v.isNotBlank() } }
            }?.let { return it }
        }
        return null
    }
}

/**
 * 订阅后端 `/api/ws` 广播通道（Bearer APP token 鉴权），把 `session.event` 作为
 * [PushSessionEvent] 流式产出。连接关闭即结束（由调用方负责重连/退避）。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Singleton
class BackendPushListener @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
) {

    fun eventFlow(backendUrl: String, token: String): Flow<PushSessionEvent> = flow {
        val base = backendUrl.trim().trimEnd('/').takeIf { it.isNotBlank() } ?: return@flow
        val wsUrl = when {
            base.startsWith("https://") -> base.replaceFirst("https://", "wss://")
            base.startsWith("http://") -> base.replaceFirst("http://", "ws://")
            else -> base
        }
        val session = httpClient.webSocketSession("$wsUrl/api/ws") {
            method = HttpMethod.Get
            header("Authorization", "Bearer $token")
        }
        // 健康空闲 WS 无应用层心跳时会被 OkHttp readTimeout 掐断，导致 120s 后频繁重连。
        // Ktor 2.3.x 支持 pingIntervalMillis：启动内置 pinger，每 20s 发一次 Ping 保活，
        // 让底层 socket 在 120s readTimeout 内持续有读写，空闲也被判定为健康。
        session.pingIntervalMillis = 20_000L
        for (frame in session.incoming) {
            if (frame !is Frame.Text) continue
            val root = runCatching { json.parseToJsonElement(frame.readText()).jsonObject }.getOrNull() ?: continue
            if (root["type"]?.jsonPrimitive?.content != "session.event") continue
            val payload = root["payload"]?.jsonObject ?: continue
            val sessionId = payload["sessionId"]?.jsonPrimitive?.contentOrNull ?: continue
            val eventType = payload["eventType"]?.jsonPrimitive?.contentOrNull ?: continue
            val raw = payload["payload"]?.jsonObject ?: JsonObject(emptyMap())
            emit(PushSessionEvent(sessionId = sessionId, eventType = eventType, payload = raw))
        }
    }
}