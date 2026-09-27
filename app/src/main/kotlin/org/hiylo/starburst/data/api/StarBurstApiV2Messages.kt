/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : StarBurstApiV2Messages.kt
 * Date : 2026/09/23 17:45:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import org.hiylo.starburst.domain.model.Message
import org.hiylo.starburst.domain.model.MessageWithParts
import org.hiylo.starburst.domain.model.Part
import org.hiylo.starburst.domain.model.TimeInfo
import org.hiylo.starburst.domain.model.ToolState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ============ V2 消息 DTO（对齐 opencode V2 契约，真值源 1.18.30 实测） ============

/** 解码 V2 消息片段（ToolState 多态）用的宽松 Json。 */
internal val v2Json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

/** `GET /api/session/{id}/message[/{mid}]` 的消息条目（SessionMessage）。 */
@Serializable
data class V2SessionMessage(
    val id: String = "",
    @SerialName("parentID") val parentId: String? = null,
    val metadata: JsonObject? = null,
    val time: V2MessageTime = V2MessageTime(0, null),
    val type: String = "", // user|assistant|system|synthetic|shell|compaction|agent-switched|model-switched
    val text: String? = null,
    val files: List<JsonElement>? = null,
    val agents: List<JsonElement>? = null,
    val agent: String? = null,
    val model: V2ModelRef? = null,
    val content: List<V2ContentItem> = emptyList(),
    val snapshot: JsonObject? = null,
    val finish: String? = null,
    val cost: Double = 0.0,
    val tokens: V2Tokens? = null,
    val error: JsonObject? = null,
)

@Serializable
data class V2MessageTime(val created: Long = 0, val completed: Long? = null)

@Serializable
data class V2Tokens(
    val input: Long = 0,
    val output: Long = 0,
    val reasoning: Long = 0,
    val cache: V2TokensCache? = null,
) {
    @Serializable
    data class V2TokensCache(val read: Long = 0, val write: Long = 0)
}

/** 消息 content[] 条目（等价 V1 的 Part，工具用 name 而非 tool）。 */
@Serializable
data class V2ContentItem(
    val id: String = "",
    val type: String = "",
    val text: String? = null,
    @SerialName("callID") val callId: String? = null,
    val name: String? = null, // V2 工具名在 name（V1 是 tool）
    val tool: String? = null,
    val state: JsonObject? = null,
    val time: JsonObject? = null,
    val snapshot: String? = null,
    val reason: String? = null,
    val cost: Double? = null,
    val tokens: JsonObject? = null,
    val synthetic: Boolean? = null,
    val mime: String? = null,
    val filename: String? = null,
    val url: String? = null,
    val source: JsonElement? = null,
    val files: List<String>? = null,
    val path: String? = null,
    val content: String? = null,
    val metadata: JsonObject? = null,
)

@Serializable
data class V2MessagesResponse(
    val data: List<V2SessionMessage> = emptyList(),
    val cursor: JsonObject? = null,
)

// ============ V2 → App 模型适配器 ============

/**
 * 归一化 V2 工具 state：opencode V2 的 `input` 在 pending/running 时可能是空字符串 `""`
 * 或字符串，而 App 的 ToolState.input 是 Map → 非对象时替换为 `{}`。
 */
internal fun sanitizeToolState(state: JsonObject): JsonObject {
    val input = state["input"]
    if (input != null && input !is kotlinx.serialization.json.JsonObject) {
        val patched = state.toMutableMap()
        patched["input"] = kotlinx.serialization.json.buildJsonObject { }
        return JsonObject(patched)
    }
    return state
}

/** V2 SessionMessage → App 的 [MessageWithParts]（UI 渲染模型）。 */
internal fun V2SessionMessage.toMessageWithParts(sessionId: String): MessageWithParts {
    val timeInfo = TimeInfo(created = time.created, completed = time.completed)
    val info: Message = when (type) {
        "user" -> Message.User(
            id = id,
            sessionId = sessionId,
            time = timeInfo,
            agent = agent,
            model = model?.let { Message.User.Model(providerId = it.providerId, modelId = it.modelId) },
        )
        "compaction" -> Message.User(
            id = id,
            sessionId = sessionId,
            time = timeInfo,
        )
        else -> Message.Assistant(
            id = id,
            sessionId = sessionId,
            time = timeInfo,
            parentId = parentId ?: "",
            modelId = model?.modelId,
            providerId = model?.providerId,
            agent = agent,
            cost = cost.takeIf { it > 0 },
            tokens = tokens?.let { t ->
                Message.Assistant.Tokens(
                    input = t.input.toInt(),
                    output = t.output.toInt(),
                    reasoning = t.reasoning.toInt(),
                    cache = Message.Assistant.Tokens.Cache(
                        read = t.cache?.read?.toInt() ?: 0,
                        write = t.cache?.write?.toInt() ?: 0,
                    ),
                )
            },
            finish = finish,
            error = error?.let { e ->
                Message.Assistant.ErrorInfo(
                    name = e["type"]?.jsonPrimitive?.contentOrNull ?: "unknown",
                    data = e["message"] ?: e,
                )
            },
        )
    }
    val parts: List<Part> = if (type == "compaction") {
        // V2 压缩消息：content 为空，渲染为「已总结」分隔条（Part.Compaction）。
        listOf(Part.Compaction(id = "$id-compaction", sessionId = sessionId, messageId = id))
    } else if (type == "user" && content.isEmpty()) {
        // V2 用户消息把文本/附件放在顶层 text/files（content 恒空）→ 合成 parts，
        // 否则重开会话时用户气泡会因 parts 为空而不显示文本。
        buildList {
            text?.takeIf { it.isNotBlank() }?.let { t ->
                add(Part.Text(id = "$id-text", sessionId = sessionId, messageId = id, text = t))
            }
            files?.forEachIndexed { idx, f ->
                val fo = f.jsonObject ?: return@forEachIndexed
                add(
                    Part.File(
                        id = "$id-file-$idx",
                        sessionId = sessionId,
                        messageId = id,
                        mime = fo["mime"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        filename = fo["name"]?.jsonPrimitive?.contentOrNull ?: fo["filename"]?.jsonPrimitive?.contentOrNull,
                        url = fo["uri"]?.jsonPrimitive?.contentOrNull ?: fo["url"]?.jsonPrimitive?.contentOrNull,
                        source = fo["source"],
                    )
                )
            }
        }
    } else {
        content.mapIndexed { idx, c -> c.toPart(sessionId, id, idx) }
    }
    return MessageWithParts(info = info, parts = parts)
}

/** V2 content 条目 → App 的 [Part]（类型判别 + name/tool 归一）。 */
internal fun V2ContentItem.toPart(sessionId: String, messageId: String, index: Int): Part {
    // 工具 part 的 id 兜底必须用 callId，不能用序号：流式侧 EventReducer 建工具 part 时
    // `id = callId`（tool.input.started / tool.called 都以 callID 为主键）。若这里用
    // "$messageId-$index" 兜底，历史重载后同一次调用会同时存在 callId 版与序号版两条，
    // 表现为「同一张工具卡显示两遍」。
    val partId = id.ifBlank { callId?.takeIf { it.isNotBlank() } ?: "$messageId-$index" }
    val toolName = name ?: tool
    val stateJson = state
    return when (type) {
        "text" -> Part.Text(
            id = partId, sessionId = sessionId, messageId = messageId,
            text = text.orEmpty(),
            synthetic = synthetic,
            time = time?.let { Part.Text.Time(start = it["start"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0) },
        )
        "reasoning" -> Part.Reasoning(
            id = partId, sessionId = sessionId, messageId = messageId,
            text = text.orEmpty(),
        )
        "tool" -> Part.Tool(
            id = partId, sessionId = sessionId, messageId = messageId,
            callId = callId ?: id,
            tool = toolName.orEmpty(),
            state = stateJson?.let { v2Json.decodeFromJsonElement<ToolState>(sanitizeToolState(it)) } ?: ToolState.Pending(),
        )
        "step-start" -> Part.StepStart(id = partId, sessionId = sessionId, messageId = messageId, snapshot = snapshot)
        "step-finish" -> Part.StepFinish(
            id = partId, sessionId = sessionId, messageId = messageId,
            reason = reason.orEmpty(), snapshot = snapshot, cost = cost,
        )
        "file" -> Part.File(
            id = partId, sessionId = sessionId, messageId = messageId,
            mime = mime.orEmpty(), filename = filename, url = url, source = source,
        )
        "snapshot" -> Part.Snapshot(id = partId, sessionId = sessionId, messageId = messageId, snapshot = snapshot.orEmpty())
        "patch" -> Part.Patch(
            id = partId, sessionId = sessionId, messageId = messageId,
            hash = (metadata?.get("hash")?.jsonPrimitive?.contentOrNull) ?: "",
            files = files.orEmpty(),
        )
        "agent" -> Part.Agent(id = partId, sessionId = sessionId, messageId = messageId, name = toolName.orEmpty(), source = source)
        "subtask" -> Part.Subtask(id = partId, sessionId = sessionId, messageId = messageId, prompt = text.orEmpty())
        "compaction" -> Part.Compaction(id = partId, sessionId = sessionId, messageId = messageId)
        "retry" -> Part.Retry(id = partId, sessionId = sessionId, messageId = messageId, attempt = metadata?.get("attempt")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0)
        "abort" -> Part.Abort(id = partId, sessionId = sessionId, messageId = messageId, reason = text.orEmpty())
        "permission" -> Part.Permission(id = partId, sessionId = sessionId, messageId = messageId, message = text.orEmpty())
        "question" -> Part.Question(id = partId, sessionId = sessionId, messageId = messageId, question = text.orEmpty())
        "session-turn" -> Part.SessionTurn(id = partId, sessionId = sessionId, messageId = messageId)
        // 未知类型：与流式侧 SseClient.parsePart 对齐（都落 Unknown）。此前一律降级成
        // Part.Text，导致同一内容在流式期间不可见、历史重载后却冒出一段文字。
        // 仅当确实带非空文本时才当文本渲染，避免空壳文本 part。
        else -> if (!text.isNullOrBlank()) {
            Part.Text(id = partId, sessionId = sessionId, messageId = messageId, text = text)
        } else {
            Part.Unknown(id = partId, sessionId = sessionId, messageId = messageId)
        }
    }
}
