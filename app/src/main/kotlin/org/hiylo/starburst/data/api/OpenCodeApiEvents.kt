/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : OpenCodeApiEvents.kt
 * Date : 2026/09/14 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.OffsetDateTime

/** `GET /api/events` 返回的单条会话事件（后端 store.SessionEvent 的 JSON 形状）。 */
@Serializable
data class SessionEventRecord(
    val id: Long = 0,
    val sessionId: String = "",
    val eventType: String = "",
    val payload: JsonElement? = null,
    val createdAt: String = "",
)

/** `GET /api/events` 的响应包装：仅含 events 数组，按 created_at 升序。 */
@Serializable
internal data class SessionEventsResponse(val events: List<SessionEventRecord> = emptyList())

/** createdAt 是 RFC3339Nano 字符串；解析失败返回 0（用于排序与展示回退）。 */
fun SessionEventRecord.createdAtEpochMillis(): Long =
    runCatching { OffsetDateTime.parse(createdAt).toInstant().toEpochMilli() }.getOrDefault(0L)

/**
 * 拉取后端记录的全量会话事件（AI 工作台看板的「实时事件流」）。
 *
 * 鉴权与 [org.hiylo.starburst.data.api.BackendApi] 一致：`Authorization: Bearer <token>`。
 * [since] 传上一次返回的最后一条 createdAt（后端按 created_at ASC、id ASC 返回，形成稳定的正向游标）；
 * 省略时返回最近 [limit] 条（后端默认 200、上限 1000）。
 */
suspend fun OpenCodeApi.listSessionEvents(
    backendUrl: String,
    token: String,
    since: String? = null,
    limit: Int? = null,
): List<SessionEventRecord> {
    val response = httpClient.get("${backendUrl.trimEnd('/')}/api/events") {
        header("Authorization", "Bearer $token")
        since?.let { parameter("since", it) }
        limit?.let { parameter("limit", it) }
        timeout { requestTimeoutMillis = 10_000L }
    }
    if (!response.status.isSuccess()) {
        throw RuntimeException("list events failed: ${response.status}")
    }
    return response.body<SessionEventsResponse>().events
}