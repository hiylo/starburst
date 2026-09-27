/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : StarBurstApiMessages.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import org.hiylo.starburst.domain.model.MessageWithParts
import org.hiylo.starburst.logging.AppLogger as Log
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ============ Messages ============

/**
 * V2 信封拆包：`{data:...}` 返回 data 元素；body 非对象/解析失败返回 null。
 * 供 export/listMessagesRaw 等需要原始 JSON 字节的链路使用（不经过 ContentNegotiation 解码）。
 */
internal fun v2EnvelopeDataOrNull(body: String): JsonElement? {
    val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
    return root["data"]
}

/** 解析 body 为根对象；非对象/失败返回 null。 */
internal fun parseRootObjectOrNull(body: String): JsonObject? =
    runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject

suspend fun StarBurstApi.listMessages(
    conn: ServerConnection,
    sessionId: String,
    limit: Int? = null,
    directory: String? = null,
): List<MessageWithParts> {
    return listMessagesPage(conn, sessionId, limit, directory = directory).messages
}

@OptIn(ExperimentalSerializationApi::class)
suspend fun StarBurstApi.listMessagesPage(
    conn: ServerConnection,
    sessionId: String,
    limit: Int? = null,
    before: String? = null,
    directory: String? = null,
): MessagePage {
    val maxResponseBytes = settingsRepository.messageHistoryResponseLimitMb.first() * StarBurstApi.BYTES_PER_MEGABYTE
    return listMessagesPage(conn, sessionId, limit, before, directory, maxResponseBytes)
}

/** Returns messages as raw JSON array string (V2: unwraps `{data:[...],cursor}`). */
suspend fun StarBurstApi.listMessagesRaw(conn: ServerConnection, sessionId: String): String {
    val body = httpClient.get("${conn.baseUrl}/api/session/$sessionId/message") {
        conn.authHeader?.let { header("Authorization", it) }
    }.bodyAsText()
    val data = v2EnvelopeDataOrNull(body)
    return (data as? kotlinx.serialization.json.JsonArray)?.toString() ?: body
}

/**
 * Stream session export JSON directly to an OutputStream.
 * Writes: {"info":<session>,"messages":[...]}
 *
 * V2 形状兼容：`GET /api/session/{id}` 返回 `{data:{...}}`（导出 info 取内层对象，
 * V1 直接是对象则原样）；`GET /api/session/{id}/message` 返回 `{cursor:{next},data:[...]}`
 * 分页信封（导出 messages 拆包 `data` 数组并按 `cursor.next` 逐页拉取）。
 * @param onProgress called with bytes written so far
 */
suspend fun StarBurstApi.exportSessionToStream(
    conn: ServerConnection,
    sessionId: String,
    outputStream: java.io.OutputStream,
    onProgress: (Long) -> Unit = {}
) {
    var bytesWritten = 0L
    // Write session info (small, safe to hold in memory)
    val sessionJson = httpClient.get("${conn.baseUrl}/api/session/$sessionId") {
        conn.authHeader?.let { header("Authorization", it) }
    }.bodyAsText()
    val infoJson = (v2EnvelopeDataOrNull(sessionJson) as? JsonObject)?.toString() ?: sessionJson
    val header = """{"info":$infoJson,"messages":["""
    outputStream.write(header.toByteArray())
    bytesWritten += header.toByteArray().size
    outputStream.flush()
    onProgress(bytesWritten)

    // Stream messages page by page (cursor keyset), writing items incrementally.
    var cursor: String? = null
    var first = true
    while (true) {
        val pageText = httpClient.get("${conn.baseUrl}/api/session/$sessionId/message") {
            conn.authHeader?.let { header("Authorization", it) }
            parameter("limit", 200)
            cursor?.let { parameter("cursor", it) }
        }.bodyAsText()
        val root = parseRootObjectOrNull(pageText)
        val items = (root?.get("data") as? kotlinx.serialization.json.JsonArray) ?: break
        for (item in items) {
            val chunk = if (first) item.toString() else ",${item}"
            first = false
            val bytes = chunk.toByteArray()
            outputStream.write(bytes)
            bytesWritten += bytes.size
        }
        outputStream.flush()
        onProgress(bytesWritten)

        cursor = (root["cursor"] as? JsonObject)?.get("next")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: break
    }

    outputStream.write("]}".toByteArray())
    bytesWritten += 2
    outputStream.flush()
    onProgress(bytesWritten)
}

suspend fun StarBurstApi.getMessage(conn: ServerConnection, sessionId: String, messageId: String): MessageWithParts {
    // V2：GET /api/session/{id}/message/{mid} 返回 {data: SessionMessage}。
    val resp = httpClient.get("${conn.baseUrl}/api/session/$sessionId/message/$messageId") {
        conn.authHeader?.let { header("Authorization", it) }
    }
    if (!resp.status.isSuccess()) {
        throw RuntimeException("getMessage failed: ${resp.status.value}")
    }
    val body: JsonObject = resp.body()
    val data = body["data"] ?: throw RuntimeException("getMessage: missing data for $messageId")
    return v2Json.decodeFromJsonElement<V2SessionMessage>(data).toMessageWithParts(sessionId)
}

/**
 * @Deprecated("V1 prompt_async：仅服务器无 V2 端点时兜底")
 * Send a prompt asynchronously (fire-and-forget).
 * Returns 204 No Content immediately.
 * @param directory The session's working directory, sent as x-starburst-directory header
 *                  so the server resolves the correct project context.
 */
suspend fun StarBurstApi.promptAsync(
    conn: ServerConnection,
    sessionId: String,
    messageId: String,
    parts: List<PromptPart>,
    model: ModelSelection? = null,
    agent: String? = null,
    variant: String? = null,
    directory: String? = null,
    system: String? = null,
    tools: Map<String, Boolean>? = null
) {
    val textPreview = parts.asSequence()
        .filter { it.type == "text" }
        .joinToString(" ") { it.text.orEmpty() }
        .trim()
        .take(120)
    val textBytes = parts.sumOf { it.text?.length ?: 0 }
    val startedAt = System.currentTimeMillis()
    try {
        val response = httpClient.post("${conn.baseUrl}/session/$sessionId/prompt_async") {
            conn.authHeader?.let { header("Authorization", it) }
            directory?.let { header("x-starburst-directory", it) }
            contentType(ContentType.Application.Json)
            setBody(PromptRequest(
                messageId = messageId,
                parts = parts,
                model = model,
                agent = agent,
                variant = variant,
                system = system,
                tools = tools
            ))
        }
        val elapsedMs = System.currentTimeMillis() - startedAt
        Log.i(
            "PromptAsyncTrace",
            "send sid=$sessionId mid=$messageId dir=${directory ?: "-"} " +
                "types=${parts.joinToString(",") { it.type }} bytes=$textBytes http=${response.status.value} " +
                "elapsed=${elapsedMs}ms text='$textPreview'",
        )
        if (!response.status.isSuccess()) {
            throw RuntimeException("prompt_async failed: ${response.status}")
        }
    } catch (error: Exception) {
        val elapsedMs = System.currentTimeMillis() - startedAt
        Log.e(
            "PromptAsyncTrace",
            "failed sid=$sessionId mid=$messageId dir=${directory ?: "-"} bytes=$textBytes elapsed=${elapsedMs}ms " +
                "text='$textPreview' error=${error.message}",
            error,
        )
        throw error
    }
}

suspend fun StarBurstApi.promptV2(
    conn: ServerConnection,
    sessionId: String,
    request: V2PromptRequest,
    directory: String? = null,
    workspaceId: String? = null,
): V2AdmittedPrompt? {
    val response = httpClient.post("${conn.baseUrl}/api/session/$sessionId/prompt") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
        workspaceId?.let { parameter("workspace", it) }
        contentType(ContentType.Application.Json)
        setBody(request)
    }
    if (!response.status.isSuccess()) {
        // 携带服务器错误信封的 message（{_tag, message, kind?, field?}），供上层友好展示。
        throw V2HttpException(
            response.status.value,
            extractV2ErrorMessage(response).ifBlank { "V2 prompt admission failed" },
        )
    }
    // 承认式投递：2xx 即视为已受理，正文只作诊断（流经 SSE 回流），解析失败不影响判定。
    return try {
        response.body<V2DataResponse<V2AdmittedPrompt>>().data
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(StarBurstApi.TAG, "V2 admission body not parsed (treated as accepted)", e)
        null
    }
}

/** V2 端点返回非 2xx 时抛出，携带状态码供回退判定（404/405=服务器不支持 V2 端点）。 */
internal class V2HttpException(val statusCode: Int, message: String) :
    RuntimeException("$message: $statusCode")

/** 提取 V2 错误信封的 message（{_tag, message, kind?, field?}），解不出返回空串。 */
internal suspend fun StarBurstApi.extractV2ErrorMessage(response: io.ktor.client.statement.HttpResponse): String {
    val text = runCatching { response.bodyAsText() }.getOrNull() ?: return ""
    return parseV2ErrorMessage(text)
}

/** 从 V2 错误响应体文本解析 message 字段（纯函数，便于单测）。 */
internal fun parseV2ErrorMessage(text: String): String =
    runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(text)
            .jsonObject["message"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.takeIf { it.isNotBlank() }.orEmpty()

// ==== 以下为 V2 发送辅助：sendPrompt 统一走 V2 承认式投递，V1 prompt_async 仅作 404/405 兜底 ====

/** 进程内记录每个会话最近一次已成功应用的 agent/model，避免每条消息重复切换。 */
private val v2AppliedSelection = java.util.concurrent.ConcurrentHashMap<String, Pair<String?, String?>>()

private fun selectionCacheKey(baseUrl: String, sessionId: String): String = "$baseUrl|$sessionId"

private fun modelSelectionKey(model: ModelSelection?, variant: String?): String? =
    model?.let { "${it.providerId}/${it.modelId}/${variant.orEmpty()}" }

/**
 * 把 V1 的 prompt parts 映射为 V2 的 [V2PromptRequest]：
 *  - text parts 合并为单一 `prompt.text`（换行连接，保持原顺序）；
 *  - file parts 映射为 `prompt.files`（uri=path 或 url，name=filename，description=mime）。
 *
 * delivery/resume 语义（V2 契约 steer|queue，缺省 steer；resume 表示是否启动/续跑回合）：
 *  - **resume 必须为 true**：starburst-agent 的 `promptSession` 只在 resume=true 时
 *    发 `session.next.prompted` 并调度回合（resume=false 只记录输入不执行）。旧的
 *    opencode 1.18.30 的 V2 发送不可用（工具调用触发 `Failed to drain Session`），
 *    其 V2 端点以 404/405 暴露，由 [sendPrompt] 统一回退 V1 `prompt_async`。
 *  - delivery 取 `queue`（作为新一轮，会话不忙即启动）。
 */
internal fun buildV2PromptRequest(messageId: String, parts: List<PromptPart>): V2PromptRequest {
    val text = parts.asSequence()
        .filter { it.type == "text" }
        .mapNotNull { it.text }
        .filter { it.isNotBlank() }
        .joinToString("\n")
    val files = parts.asSequence()
        .filter { it.type == "file" }
        .mapNotNull { p ->
            val uri = (p.path ?: p.url)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            V2FileAttachment(uri = uri, name = p.filename, description = p.mime)
        }
        .toList()
    return V2PromptRequest(
        id = messageId,
        prompt = V2Prompt(text = text, files = files),
        delivery = "queue",
        resume = true,
    )
}

/**
 * 发送一条 prompt，统一走 V2 承认式投递（V1 `prompt_async` 仅作 404/405 兜底）：
 *
 * 先按需切换 agent/model（`/api/session/{id}/agent|model`），再 `POST /api/session/{id}/prompt`
 * （body 见 [buildV2PromptRequest]）。starburst-agent 的 `promptSession` 忽略 delivery/resume、
 * 无条件启动回合（含工具+权限门禁），故 V2 发送正常。V2 端点缺失（404/405）判定服务器仅 V1
 * → 回退 V1 `prompt_async`；[system]/[tools] 仅 V1 生效应、V2 契约忽略，不再作为走 V1 的入口条件。
 */
suspend fun StarBurstApi.sendPrompt(
    conn: ServerConnection,
    sessionId: String,
    messageId: String,
    parts: List<PromptPart>,
    model: ModelSelection? = null,
    agent: String? = null,
    variant: String? = null,
    directory: String? = null,
    system: String? = null,
    tools: Map<String, Boolean>? = null,
    workspaceId: String? = null,
) {
    if (system != null || tools != null) {
        Log.w(StarBurstApi.TAG, "system/tools 仅 V1 生效，V2 投递忽略")
    }
    try {
        switchSelectionIfNeeded(conn, sessionId, model, agent, variant, directory, workspaceId)
        promptV2(conn, sessionId, buildV2PromptRequest(messageId, parts), directory, workspaceId)
        return
    } catch (e: V2HttpException) {
        // 仅 404/405 表示服务器没有 V2 端点（V1-only 服务器）→ 回退 V1 prompt_async；
        // 其余（400/500 等）是 V2 服务器对投递的真实拒绝，必须原样抛出，
        // 否则 V1 兜底会以「prompt_async 404」掩盖真实原因（quota/参数/会话状态）。
        if (e.statusCode != 404 && e.statusCode != 405) {
            throw e
        }
        Log.w(StarBurstApi.TAG, "V2 endpoint unavailable (${e.statusCode}); V1 fallback: prompt_async", e)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    }
    promptAsync(conn, sessionId, messageId, parts, model, agent, variant, directory, system, tools)
}

/** 仅在会话当前的 agent/model 与目标不一致时才调用切换端点，并记录已应用值。 */
private suspend fun StarBurstApi.switchSelectionIfNeeded(
    conn: ServerConnection,
    sessionId: String,
    model: ModelSelection?,
    agent: String?,
    variant: String?,
    directory: String?,
    workspaceId: String?,
) {
    if (model == null && agent == null) return
    val key = selectionCacheKey(conn.baseUrl, sessionId)
    val applied = v2AppliedSelection[key]
    val appliedAgent = applied?.first
    val appliedModel = applied?.second
    val modelKey = modelSelectionKey(model, variant)
    var newAgent = appliedAgent
    var newModel = appliedModel
    if (model != null && appliedModel != modelKey) {
        switchSessionModelV2(conn, sessionId, model, variant, directory, workspaceId)
        newModel = modelKey
    }
    if (agent != null && appliedAgent != agent) {
        switchSessionAgentV2(conn, sessionId, agent, directory, workspaceId)
        newAgent = agent
    }
    if (newAgent != appliedAgent || newModel != appliedModel) {
        v2AppliedSelection[key] = newAgent to newModel
    }
}