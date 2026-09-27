/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : EventReducerStreamingExt.kt
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.data.repository

import org.hiylo.starburst.domain.model.*
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.Json

/**
 * Prompt/step/shell/tool 流式生命周期：把 Next* 系列事件折算成 message 与 part
 * 的增量写入（含 callId→messageId 索引与工具输出归一化）。
 * 从 EventReducer 原样搬移为同包扩展函数，无行为变更。
 */

internal fun EventReducer.handleNextPrompted(event: SseEvent.Prompted, serverId: String) {
    trackSession(serverId, event.sessionId)
    _promptDeliveries.update {
        it + (event.messageId to PromptDeliveryInfo(event.sessionId, PromptDeliveryState.PROMOTED))
    }
    val timestamp = event.timestamp.takeIf { it > 0 } ?: System.currentTimeMillis()
    handleMessageUpdated(SseEvent.MessageUpdated(Message.User(
        id = event.messageId,
        sessionId = event.sessionId,
        time = TimeInfo(timestamp),
    )))
    val prompt = event.prompt as? JsonObject ?: return
    prompt["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { text ->
        handleMessagePartUpdated(SseEvent.MessagePartUpdated(Part.Text(
            id = "${event.messageId}-prompt",
            sessionId = event.sessionId,
            messageId = event.messageId,
            text = text,
            time = Part.Text.Time(timestamp, timestamp),
        )))
    }
    prompt["files"]?.let { it as? kotlinx.serialization.json.JsonArray }?.forEachIndexed { index, element ->
        val file = element as? JsonObject ?: return@forEachIndexed
        handleMessagePartUpdated(SseEvent.MessagePartUpdated(Part.File(
            id = "${event.messageId}-file-$index",
            sessionId = event.sessionId,
            messageId = event.messageId,
            mime = "application/octet-stream",
            filename = file["name"]?.jsonPrimitive?.contentOrNull,
            url = file["uri"]?.jsonPrimitive?.contentOrNull,
            source = file["source"],
        )))
    }
}

internal fun EventReducer.handleNextStepStarted(event: SseEvent.NextStepStarted, serverId: String) {
    trackSession(serverId, event.sessionId)
    val model = event.model as? JsonObject ?: JsonObject(emptyMap())
    val parentId = _messages.value[event.sessionId]
        ?.filterIsInstance<Message.User>()
        ?.maxByOrNull { it.time.created }
        ?.id
        ?: ""
    handleMessageUpdated(SseEvent.MessageUpdated(Message.Assistant(
        id = event.assistantMessageId,
        sessionId = event.sessionId,
        time = TimeInfo(event.timestamp.takeIf { it > 0 } ?: System.currentTimeMillis()),
        parentId = parentId,
        providerId = model["providerID"]?.jsonPrimitive?.contentOrNull,
        modelId = model["id"]?.jsonPrimitive?.contentOrNull
            ?: model["modelID"]?.jsonPrimitive?.contentOrNull,
        variant = model["variant"]?.jsonPrimitive?.contentOrNull,
        agent = event.agent,
    )))
    _sessionStatuses.update { it + (event.sessionId to SessionStatus.Busy) }
}

internal fun EventReducer.handleNextStepEnded(event: SseEvent.NextStepEnded) {
    val existing = _messages.value[event.sessionId]
        ?.filterIsInstance<Message.Assistant>()
        ?.firstOrNull { it.id == event.assistantMessageId }
        ?: return
    val tokens = event.tokens as? JsonObject ?: JsonObject(emptyMap())
    val cache = tokens["cache"] as? JsonObject
    handleMessageUpdated(SseEvent.MessageUpdated(existing.copy(
        time = existing.time.copy(completed = event.timestamp.takeIf { it > 0 } ?: System.currentTimeMillis()),
        finish = event.finish,
        cost = event.cost,
        tokens = Message.Assistant.Tokens(
            input = tokens["input"]?.jsonPrimitive?.intOrNull ?: 0,
            output = tokens["output"]?.jsonPrimitive?.intOrNull ?: 0,
            reasoning = tokens["reasoning"]?.jsonPrimitive?.intOrNull ?: 0,
            cache = Message.Assistant.Tokens.Cache(
                read = cache?.get("read")?.jsonPrimitive?.intOrNull ?: 0,
                write = cache?.get("write")?.jsonPrimitive?.intOrNull ?: 0,
            ),
        ),
    )))
}

internal fun EventReducer.updateMessage(sessionId: String, messageId: String, transform: (Message) -> Message) {
    _messages.update { current ->
        val messages = current[sessionId].orEmpty()
        if (messages.none { it.id == messageId }) current
        else current + (sessionId to messages.map { if (it.id == messageId) transform(it) else it })
    }
}

internal fun EventReducer.handleNextStepFailed(event: SseEvent.NextStepFailed) {
    val error = event.error as? JsonObject
    updateMessage(event.sessionId, event.assistantMessageId) { message ->
        val assistant = message as? Message.Assistant ?: return@updateMessage message
        assistant.copy(
            time = assistant.time.copy(completed = event.timestamp.takeIf { it > 0 } ?: System.currentTimeMillis()),
            error = Message.Assistant.ErrorInfo(
                name = error?.let { e ->
                    (e["type"] as? JsonPrimitive)?.contentOrNull
                        ?: (e["name"] as? JsonPrimitive)?.contentOrNull
                } ?: "Error",
                data = error?.get("message") ?: event.error,
            ),
        )
    }
}

internal fun EventReducer.handleNextShellStarted(event: SseEvent.NextShellStarted) {
    indexCallId(event.callId, event.messageId)
    handleMessagePartUpdated(SseEvent.MessagePartUpdated(Part.Tool(
        id = event.callId,
        sessionId = event.sessionId,
        messageId = event.messageId,
        callId = event.callId,
        tool = "bash",
        state = ToolState.Running(
            input = mapOf("command" to JsonPrimitive(event.command)),
            time = ToolState.Running.Time(event.timestamp),
        ),
    )))
}

internal fun EventReducer.handleNextShellEnded(event: SseEvent.NextShellEnded) {
    val existing = (event.messageId.takeIf { it.isNotBlank() }?.let { mid ->
        _parts.value[mid]?.filterIsInstance<Part.Tool>()?.firstOrNull { it.callId == event.callId }
    } ?: _parts.value[messageIdForCall(event.callId)]
        ?.filterIsInstance<Part.Tool>()
        ?.firstOrNull { it.callId == event.callId }
        ?: _parts.value.values.asSequence().flatten()
            .filterIsInstance<Part.Tool>()
            .firstOrNull { it.sessionId == event.sessionId && it.callId == event.callId })
        ?: return
    val running = existing.state as? ToolState.Running ?: return
    val failed = event.exitCode != null && event.exitCode != 0
    handleMessagePartUpdated(SseEvent.MessagePartUpdated(existing.copy(
        state = if (failed) ToolState.Error(
            input = running.input,
            error = event.output.ifBlank { "Exit code ${event.exitCode}" },
            time = ToolState.Error.Time(running.time?.start ?: event.timestamp, event.timestamp),
        ) else ToolState.Completed(
            input = running.input,
            result = event.output,
            time = ToolState.Completed.Time(running.time?.start ?: event.timestamp, event.timestamp),
        ),
    )))
}

internal fun EventReducer.findToolPart(messageId: String, callId: String): Part.Tool? =
    _parts.value[messageId]?.filterIsInstance<Part.Tool>()?.firstOrNull { it.callId == callId }

/** 记录 callId → messageId 映射，供不带 messageId 的结束类事件 O(1) 定位。 */
internal fun EventReducer.indexCallId(callId: String, messageId: String) {
    synchronized(callIndexLock) {
        callIdIndex[callId] = messageId
        if (callIdIndex.size > MAX_CALL_ID_INDEX) {
            val eldest = callIdIndex.keys.firstOrNull() ?: return
            callIdIndex.remove(eldest)
        }
    }
}

/** 通过 callId 索引反查 messageId；无索引时返回 null（调用方需兜底扫描）。 */
internal fun EventReducer.messageIdForCall(callId: String): String? =
    synchronized(callIndexLock) { callIdIndex[callId] }

internal fun EventReducer.handleNextToolInputStarted(event: SseEvent.NextToolInputStarted) {
    if (findToolPart(event.messageId, event.callId) != null) return
    indexCallId(event.callId, event.messageId)
    handleMessagePartUpdated(SseEvent.MessagePartUpdated(Part.Tool(
        id = event.callId,
        sessionId = event.sessionId,
        messageId = event.messageId,
        callId = event.callId,
        tool = event.name,
        state = ToolState.Pending(),
    )))
}

internal fun EventReducer.handleNextToolInputDelta(event: SseEvent.NextToolInputDelta) {
    val existing = findToolPart(event.messageId, event.callId) ?: return
    val pending = existing.state as? ToolState.Pending ?: return
    handleMessagePartUpdated(SseEvent.MessagePartUpdated(existing.copy(
        state = pending.copy(raw = pending.raw.orEmpty() + event.delta),
    )))
}

internal fun EventReducer.handleNextToolInputEnded(event: SseEvent.NextToolInputEnded) {
    val existing = findToolPart(event.messageId, event.callId) ?: return
    val input = runCatching { Json.parseToJsonElement(event.text).jsonObject }.getOrDefault(emptyMap())
    handleMessagePartUpdated(SseEvent.MessagePartUpdated(existing.copy(
        state = when (val state = existing.state) {
            is ToolState.Pending -> ToolState.Pending(input = input, raw = event.text)
            is ToolState.Running -> state.copy(input = input)
            is ToolState.Completed -> state.copy(input = input)
            is ToolState.Error -> state.copy(input = input)
        },
    )))
}

internal fun EventReducer.handleNextToolCalled(event: SseEvent.NextToolCalled) {
    val existing = findToolPart(event.messageId, event.callId)
    if (existing?.state is ToolState.Completed || existing?.state is ToolState.Error) return
    val running = existing?.state as? ToolState.Running
    handleMessagePartUpdated(SseEvent.MessagePartUpdated(Part.Tool(
        id = existing?.id ?: event.callId,
        sessionId = event.sessionId,
        messageId = event.messageId,
        callId = event.callId,
        tool = event.tool,
        state = ToolState.Running(
            input = event.input as? JsonObject ?: JsonObject(emptyMap()),
            title = running?.title,
            metadata = running?.metadata,
            time = ToolState.Running.Time(event.timestamp),
        ),
    )))
}

internal fun EventReducer.toolContentText(content: kotlinx.serialization.json.JsonElement): String =
    (content as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { item ->
        (item as? JsonObject)?.takeIf { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
            ?.get("text")?.jsonPrimitive?.contentOrNull
    }.joinToString("\n")

internal fun EventReducer.toolMetadata(structured: kotlinx.serialization.json.JsonElement, output: String): Map<String, kotlinx.serialization.json.JsonElement> =
    (structured as? JsonObject).orEmpty() + if (output.isNotBlank()) mapOf("output" to JsonPrimitive(output)) else emptyMap()

internal fun EventReducer.handleNextToolProgress(event: SseEvent.NextToolProgress) {
    val existing = findToolPart(event.messageId, event.callId) ?: return
    if (existing.state is ToolState.Completed || existing.state is ToolState.Error) return
    val input = when (val state = existing.state) {
        is ToolState.Pending -> state.input
        is ToolState.Running -> state.input
        is ToolState.Completed -> state.input
        is ToolState.Error -> state.input
    }
    val output = toolContentText(event.content)
    val start = (existing.state as? ToolState.Running)?.time?.start ?: event.timestamp
    val title = (existing.state as? ToolState.Running)?.title
    val metadata = (existing.state as? ToolState.Running)?.metadata.orEmpty() + toolMetadata(event.structured, output)
    handleMessagePartUpdated(SseEvent.MessagePartUpdated(existing.copy(
        state = ToolState.Running(input, title = title, metadata = metadata, time = ToolState.Running.Time(start)),
    )))
}

internal fun EventReducer.handleNextToolSuccess(event: SseEvent.NextToolSuccess) {
    val existing = findToolPart(event.messageId, event.callId) ?: return
    val running = existing.state as? ToolState.Running
    val input = running?.input ?: (existing.state as? ToolState.Pending)?.input.orEmpty()
    val output = event.result.takeIf { it.isNotBlank() }
        ?: toolContentText(event.content)
    val attachments = (event.content as? kotlinx.serialization.json.JsonArray).orEmpty()
        .mapIndexedNotNull { index, item ->
            val file = (item as? JsonObject)?.takeIf { it["type"]?.jsonPrimitive?.contentOrNull == "file" }
                ?: return@mapIndexedNotNull null
        ToolState.Completed.Attachment(
            id = "${event.callId}-file-$index",
            sessionId = event.sessionId,
            messageId = event.messageId,
            mime = file["mime"]?.jsonPrimitive?.contentOrNull ?: "application/octet-stream",
            filename = file["name"]?.jsonPrimitive?.contentOrNull,
            url = file["uri"]?.jsonPrimitive?.contentOrNull,
        )
    }
    handleMessagePartUpdated(SseEvent.MessagePartUpdated(existing.copy(
        state = ToolState.Completed(
            input = input,
            result = output,
            title = running?.title,
            metadata = running?.metadata.orEmpty() + (event.structured as? JsonObject ?: JsonObject(emptyMap())),
            time = ToolState.Completed.Time(running?.time?.start ?: event.timestamp, event.timestamp),
            attachments = attachments,
        ),
    )))
}

internal fun EventReducer.handleNextToolFailed(event: SseEvent.NextToolFailed) {
    val existing = findToolPart(event.messageId, event.callId) ?: return
    val running = existing.state as? ToolState.Running
    val input = running?.input ?: (existing.state as? ToolState.Pending)?.input.orEmpty()
    val errorObj = event.error as? JsonObject
    val error = errorObj?.let { e ->
        (e["message"] as? JsonPrimitive)?.contentOrNull
            ?: (e["type"] as? JsonPrimitive)?.contentOrNull
    }
        ?: (event.error as? JsonPrimitive)?.contentOrNull
        ?: event.error.toString()
    handleMessagePartUpdated(SseEvent.MessagePartUpdated(existing.copy(
        state = ToolState.Error(
            input = input,
            error = error,
            metadata = running?.metadata,
            time = ToolState.Error.Time(running?.time?.start ?: event.timestamp, event.timestamp),
        ),
    )))
}
