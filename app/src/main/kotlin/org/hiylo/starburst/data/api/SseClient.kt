/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SseClient.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.BuildConfig
import org.hiylo.starburst.domain.model.*
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SseClient"
private const val HEARTBEAT_TIMEOUT_MS = 90_000L

data class ScopedSseEvent(
    val event: SseEvent,
    val directory: String? = null,
    val projectId: String? = null,
    val workspaceId: String? = null,
    val eventId: String? = null,
    val durableSeq: Long? = null,
)

internal fun sseEventData(payload: JsonObject): JsonObject =
    payload["properties"] as? JsonObject
        ?: payload["data"] as? JsonObject
        ?: payload

/** 内容对象缺 sessionID 时，回退取信封顶层的 sessionID（V2 事件可能把 sessionID 放在 data 外层）。 */
private fun JsonObject.withEnvelopeSessionId(envelope: JsonObject): JsonObject {
    if (this["sessionID"] != null) return this
    val envelopeSessionId = envelope["sessionID"] ?: return this
    return JsonObject(this.toMutableMap().apply { put("sessionID", envelopeSessionId) })
}

/**
 * 归一化 V2 `message.updated` 的 info 对象后再交给 parseMessage：
 * 1. V2 把 sessionID 放在事件 data 层而非 info 内 → 缺失时从 props 注入；
 * 2. V2 的 model 是嵌套 {id, providerID}（无顶层 modelID）→ 摊平为顶层 modelID/providerID，
 *    供 [Message.Assistant] 直接解码。
 */
internal fun enrichMessageInfo(info: JsonObject, props: JsonObject): JsonObject {
    val mutable = info.toMutableMap()
    var changed = false
    if (mutable["sessionID"] == null && props["sessionID"] != null) {
        mutable["sessionID"] = props["sessionID"]!!
        changed = true
    }
    val model = info["model"] as? JsonObject
    if (model != null) {
        if (mutable["modelID"] == null && model["id"] != null) {
            mutable["modelID"] = model["id"]!!
            changed = true
        }
        if (mutable["providerID"] == null && model["providerID"] != null) {
            mutable["providerID"] = model["providerID"]!!
            changed = true
        }
    }
    return if (changed) JsonObject(mutable) else info
}

internal fun isHighFrequencySseEvent(event: SseEvent): Boolean = when (event) {
    is SseEvent.MessagePartDelta,
    is SseEvent.MessagePartUpdated,
    is SseEvent.NextTextDelta,
    is SseEvent.NextReasoningDelta,
    is SseEvent.NextToolInputDelta,
    is SseEvent.NextToolProgress,
    is SseEvent.ServerHeartbeat -> true
    else -> false
}

/**
 * SSE (Server-Sent Events) Client
 *
 * Stateless — all connection info comes from the [ServerConnection] parameter.
 * Safe to use for multiple servers concurrently.
 */
@Singleton
class SseClient @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json
) {

    /**
     * Connect to the global event stream.
     * Returns a Flow that emits SSE events.
     * The flow does NOT auto-reconnect internally — callers should handle
     * reconnection themselves (the service already does exponential backoff).
     */
    fun connectToGlobalEvents(
        conn: ServerConnection,
        directory: String? = null,
        onOpen: suspend () -> Unit = {},
    ): Flow<ScopedSseEvent> = flow {
        val sseUrl = "${conn.baseUrl}/api/event"
        Log.i(TAG, "Connecting to global SSE (auth=${conn.authHeader != null})")

        val statement = httpClient.prepareGet(sseUrl) {
            conn.authHeader?.let { header("Authorization", it) }
            header("Accept", "text/event-stream")
            directory?.let { header("x-starburst-directory", it) }

            timeout {
                requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
            }
        }

        statement.execute { response ->
            val statusCode = response.status.value
            Log.i(TAG, "SSE response: status=$statusCode, contentType=${response.headers["content-type"]}")

            // 401 与 403 语义同源：均为鉴权失败。403 常见于密码被拒（Basic 认证）
            // 以及镜像 Bearer token 失效；统一走 SseAuthException 终止重连并给出
            // 认证错误提示，避免落入 retryable=false 的「服务器不响应」误导性文案。
            if (statusCode == 401 || statusCode == 403) {
                Log.e(TAG, "SSE auth failed ($statusCode). Check username/password or token.")
                throw SseAuthException("Authentication failed ($statusCode)")
            }

            if (statusCode !in 200..299) {
                Log.e(TAG, "SSE failed with HTTP $statusCode")
                throw SseConnectionException(
                    message = "HTTP $statusCode",
                    retryable = statusCode == 408 || statusCode == 429 || statusCode >= 500,
                )
            }

            val channel = response.bodyAsChannel()
            val decoder = SseFrameDecoder()
            var eventCount = 0
            var oversizedFrameCount = 0

            Log.i(TAG, "SSE stream opened, reading events...")
            onOpen()

            while (!channel.isClosedForRead) {
                val line = withTimeoutOrNull(HEARTBEAT_TIMEOUT_MS) { channel.readUTF8Line() }
                    ?: if (channel.isClosedForRead) break else {
                        throw SseConnectionException("SSE stream timed out")
                    }
                try {
                    decoder.accept(line)?.let { data ->
                        eventCount += processFrame(data) { emit(it) }
                    }
                } catch (e: SseFrameTooLargeException) {
                    // 单帧超限（如附件 data URL / 大 patch）不应终止整条 SSE 流：
                    // 解码器已 clear()，跳过该帧继续读取，避免服务端反复推同帧时
                    // 陷入 15 分钟重连风暴。
                    oversizedFrameCount++
                    Log.w(
                        TAG,
                        "Skipping oversized SSE frame (${e.message}); skipped=$oversizedFrameCount",
                    )
                }
            }

            decoder.finish()?.let { data ->
                eventCount += processFrame(data) { emit(it) }
            }

            if (currentCoroutineContext().isActive) {
                Log.w(TAG, "SSE stream closed after $eventCount events")
            } else if (BuildConfig.DEBUG) {
                Log.d(TAG, "SSE stream cancelled after $eventCount events")
            }
        }
    }

    private suspend fun processFrame(data: String, emitEvent: suspend (ScopedSseEvent) -> Unit): Int {
        return try {
            val event = parseEvent(data) ?: return 0
            if (event.event !is SseEvent.ServerHeartbeat) {
                if (BuildConfig.DEBUG && !isHighFrequencySseEvent(event.event)) {
                    Log.d(TAG, "Event: ${event.event::class.simpleName}")
                }
                emitEvent(event)
            }
            1
        } catch (e: Exception) {
            Log.e(TAG, "SSE event parse failed", e)
            0
        }
    }

    /**
     * Parse SSE event from raw JSON.
     * V1 global endpoint wraps events: {directory, payload: {type, properties}}
     * V1 per-instance endpoint sends directly: {type, properties}
     * V2 endpoint sends: {id, type, data, durable?, location?}
     * V2 权限/问题事件为 permission.v2.* / question.v2.*（data 平铺字段），
     * 在此映射到与 V1 相同的 SseEvent 模型。
     * Content object resolves from payload.properties, or data, or the root;
     * sessionID falls back to the envelope top level when absent from content.
     */
    private fun parseEvent(data: String): ScopedSseEvent? {
        val root = json.parseToJsonElement(data).jsonObject

        val payload = (root["payload"] as? JsonObject) ?: root
        val type = payload["type"]?.jsonPrimitive?.content
            ?: root["type"]?.jsonPrimitive?.content ?: return null
        val properties = sseEventData(payload).withEnvelopeSessionId(root)
        val directory = root["directory"]?.jsonPrimitive?.contentOrNull

        return ScopedSseEvent(
            event = parseEventByType(
                type,
                properties,
                directory,
                root["workspace"]?.jsonPrimitive?.contentOrNull,
            ) ?: return null,
            directory = directory,
            projectId = root["project"]?.jsonPrimitive?.contentOrNull,
            workspaceId = root["workspace"]?.jsonPrimitive?.contentOrNull,
            eventId = root["id"]?.jsonPrimitive?.contentOrNull ?: payload["id"]?.jsonPrimitive?.contentOrNull,
            durableSeq = (payload["durable"] as? JsonObject)?.get("seq")?.jsonPrimitive?.longOrNull
                ?: (root["durable"] as? JsonObject)?.get("seq")?.jsonPrimitive?.longOrNull,
        )
    }

    private fun parseEventByType(
        type: String,
        props: JsonObject,
        envelopeDirectory: String? = null,
        envelopeWorkspace: String? = null,
    ): SseEvent? {
        return try {
            when (type) {
                "server.connected" -> SseEvent.ServerConnected
                "server.heartbeat" -> SseEvent.ServerHeartbeat
                "server.instance.disposed" -> SseEvent.ServerInstanceDisposed(
                    directory = props.str("directory").ifBlank { envelopeDirectory.orEmpty() },
                )
                "global.disposed" -> SseEvent.GlobalDisposed
                "workspace.status" -> SseEvent.WorkspaceStatus(
                    workspaceId = props.str("workspaceID"),
                    status = props.str("status"),
                )
                "workspace.ready" -> SseEvent.WorkspaceReady(envelopeWorkspace, props.str("name"))
                "workspace.failed" -> SseEvent.WorkspaceFailed(envelopeWorkspace, props.str("message"))
                "worktree.ready" -> SseEvent.WorktreeReady(
                    directory = envelopeDirectory,
                    name = props.str("name"),
                    branch = props["branch"]?.jsonPrimitive?.contentOrNull,
                )
                "worktree.failed" -> SseEvent.WorktreeFailed(envelopeDirectory, props.str("message"))

                "session.next.prompt.admitted" -> SseEvent.PromptAdmitted(
                    sessionId = props.str("sessionID"),
                    messageId = props.str("messageID"),
                    delivery = props.str("delivery"),
                    prompt = props["prompt"],
                    timestamp = props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                )
                "session.next.prompted" -> SseEvent.Prompted(
                    sessionId = props.str("sessionID"),
                    messageId = props.str("messageID"),
                    delivery = props.str("delivery"),
                    prompt = props["prompt"],
                    timestamp = props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                )
                "session.next.step.started" -> SseEvent.NextStepStarted(
                    sessionId = props.str("sessionID"),
                    assistantMessageId = props.str("assistantMessageID"),
                    agent = props.str("agent"),
                    model = props["model"] ?: JsonObject(emptyMap()),
                    timestamp = props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                )
                "session.next.step.ended" -> SseEvent.NextStepEnded(
                    sessionId = props.str("sessionID"),
                    assistantMessageId = props.str("assistantMessageID"),
                    finish = props.str("finish"),
                    cost = props["cost"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    tokens = props["tokens"] ?: JsonObject(emptyMap()),
                    timestamp = props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                )
                "session.next.step.failed" -> SseEvent.NextStepFailed(
                    props.str("sessionID"), props.str("assistantMessageID"),
                    props["error"] ?: JsonObject(emptyMap()), props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                )
                "session.next.agent.switched" -> SseEvent.NextAgentSwitched(
                    props.str("sessionID"), props.str("messageID"), props.str("agent"),
                )
                "session.next.model.switched" -> SseEvent.NextModelSwitched(
                    props.str("sessionID"), props.str("messageID"), props["model"] ?: JsonObject(emptyMap()),
                )
                "session.next.context.updated" -> SseEvent.NextContextUpdated(
                    props.str("sessionID"), props.str("messageID"), props.str("text"),
                    props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                )
                "session.next.synthetic" -> SseEvent.NextSynthetic(
                    props.str("sessionID"), props.str("messageID"), props.str("text"),
                    props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                )
                "session.next.compaction.started" -> SseEvent.NextCompactionStarted(
                    props.str("sessionID"), props.str("messageID"), props.str("reason"),
                    props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                )
                "session.next.compaction.ended" -> SseEvent.NextCompactionEnded(
                    props.str("sessionID"), props.str("messageID"), props.str("reason"),
                    props.str("text"), props.str("recent"),
                    props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                )
                "session.next.compaction.delta" -> SseEvent.NextCompactionDelta(
                    props.str("sessionID"), props.str("messageID"), props.str("delta"),
                    props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                )
                "session.next.retried" -> SseEvent.NextRetried(
                    props.str("sessionID"),
                    props["attempt"]?.jsonPrimitive?.intOrNull ?: 0,
                    (props["error"] as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty(),
                    (props["error"] as? JsonObject)?.get("isRetryable")?.jsonPrimitive?.booleanOrNull ?: false,
                    props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                )
                "session.next.shell.started" -> {
                    val msgId = props.str("messageID")
                    SseEvent.NextShellStarted(
                        props.str("sessionID"), msgId,
                        props.str("callID").ifBlank { "shell-$msgId" }, props.str("command"),
                        props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                    )
                }
                "session.next.shell.ended" -> {
                    val msgId = props.str("messageID")
                    SseEvent.NextShellEnded(
                        props.str("sessionID"), msgId,
                        props.str("callID").ifBlank { "shell-$msgId" }, props.str("output"),
                        props["exitCode"]?.jsonPrimitive?.intOrNull,
                        props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                    )
                }
                "session.next.text.started" -> SseEvent.NextTextStarted(
                    props.str("sessionID"), props.str("assistantMessageID"), props.str("textID"),
                    props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                )
                "session.next.text.delta" -> SseEvent.NextTextDelta(
                    props.str("sessionID"), props.str("assistantMessageID"), props.str("textID"), props.str("delta"),
                )
                "session.next.text.ended" -> SseEvent.NextTextEnded(
                    props.str("sessionID"), props.str("assistantMessageID"), props.str("textID"), props.str("text"),
                    props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                )
                "session.next.reasoning.started" -> SseEvent.NextReasoningStarted(
                    props.str("sessionID"), props.str("assistantMessageID"), props.str("reasoningID"),
                    props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                )
                "session.next.reasoning.delta" -> SseEvent.NextReasoningDelta(
                    props.str("sessionID"), props.str("assistantMessageID"), props.str("reasoningID"), props.str("delta"),
                )
                "session.next.reasoning.ended" -> SseEvent.NextReasoningEnded(
                    props.str("sessionID"), props.str("assistantMessageID"), props.str("reasoningID"), props.str("text"),
                    props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                )
                "session.next.tool.input.started" -> SseEvent.NextToolInputStarted(
                    props.str("sessionID"), props.str("assistantMessageID"), props.str("callID"), props.str("name"),
                    props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                )
                "session.next.tool.input.delta" -> SseEvent.NextToolInputDelta(
                    props.str("sessionID"), props.str("assistantMessageID"), props.str("callID"), props.str("delta"),
                )
                "session.next.tool.input.ended" -> SseEvent.NextToolInputEnded(
                    props.str("sessionID"), props.str("assistantMessageID"), props.str("callID"), props.str("text"),
                )
                "session.next.tool.called" -> SseEvent.NextToolCalled(
                    props.str("sessionID"), props.str("assistantMessageID"), props.str("callID"), props.str("tool"),
                    props["input"] ?: JsonObject(emptyMap()), props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                )
                "session.next.tool.progress" -> SseEvent.NextToolProgress(
                    props.str("sessionID"), props.str("assistantMessageID"), props.str("callID"),
                    props["structured"] ?: JsonObject(emptyMap()), props["content"] ?: JsonArray(emptyList()),
                    props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                )
                "session.next.tool.success" -> SseEvent.NextToolSuccess(
                    props.str("sessionID"), props.str("assistantMessageID"),
                    props.str("callID").ifBlank { props.str("toolCallID") },
                    props["structured"] ?: JsonObject(emptyMap()), props["content"] ?: JsonArray(emptyList()),
                    props.str("result"),
                    props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                )
                "session.next.tool.failed" -> SseEvent.NextToolFailed(
                    props.str("sessionID"), props.str("assistantMessageID"),
                    props.str("callID").ifBlank { props.str("toolCallID") },
                    props["error"] ?: JsonObject(emptyMap()), props["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                )

                "session.status" -> {
                    val sessionId = props.str("sessionID")
                    val statusObj = props["status"] as? JsonObject
                    val statusType = statusObj?.get("type")?.jsonPrimitive?.content ?: "idle"

                    if (statusType != "idle" && statusType != "busy" && statusType != "retry") {
                        // 未知 status type：跳过整条事件而非降级 Idle——降级会把活跃会话
                        // 误判为空闲，进而抑制完成/忙碌状态，造成通知丢失。
                        if (BuildConfig.DEBUG) Log.d(TAG, "Unknown session status type: $statusType")
                        return null
                    }
                    val status = when (statusType) {
                        "idle" -> SessionStatus.Idle
                        "busy" -> SessionStatus.Busy
                        else -> SessionStatus.Retry(
                            // intOrNull/longOrNull：字段缺失或类型不符时不抛异常丢整条事件。
                            attempt = statusObj?.get("attempt")?.jsonPrimitive?.intOrNull ?: 0,
                            message = statusObj?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty(),
                            next = statusObj?.get("next")?.jsonPrimitive?.longOrNull ?: 0
                        )
                    }

                    SseEvent.SessionStatus(sessionId = sessionId, status = status)
                }

                "session.idle" -> {
                    val sessionId = props.str("sessionID")
                    SseEvent.SessionIdle(sessionId = sessionId)
                }

                "session.compacted" -> SseEvent.SessionCompacted(props.str("sessionID"))

                "session.created" -> {
                    val infoObj = (props["info"] as? JsonObject) ?: props
                    val info = json.decodeFromJsonElement<V2SessionInfo>(infoObj).toSession()
                    SseEvent.SessionCreated(info)
                }

                "session.updated" -> {
                    val infoObj = (props["info"] as? JsonObject) ?: props
                    val info = json.decodeFromJsonElement<V2SessionInfo>(infoObj).toSession()
                    SseEvent.SessionUpdated(info)
                }

                "session.deleted" -> {
                    val sid = props.str("sessionID")
                    val info = props["info"]?.let { itElement ->
                        json.decodeFromJsonElement<V2SessionInfo>(itElement as? JsonObject ?: return@let null).toSession()
                    }
                    SseEvent.SessionDeleted(sid, info)
                }

                "session.error" -> {
                    parseSessionError(props, json)
                }

                "session.diff" -> {
                    val sessionId = props.str("sessionID")
                    val diffArr = props["diff"] as? JsonArray
                    val diffs = diffArr?.mapNotNull { element ->
                        runCatching { json.decodeFromJsonElement<FileDiff>(element) }.getOrNull()
                    } ?: emptyList()
                    SseEvent.SessionDiff(sessionId = sessionId, diff = diffs)
                }

                "message.updated" -> {
                    val infoObj = props["info"] as? JsonObject ?: return null
                    val message = parseMessage(enrichMessageInfo(infoObj, props)) ?: return null
                    // 用户消息的正文在顶层 text（content 恒空），且 agent 不为用户消息发 part
                    // 事件——把正文带上，由 reducer 在缺 part 时合成，否则这条路径单独到达时
                    // 用户气泡会是空的（AI 回复照常显示，表现为「我发的消息不见了」）。
                    val topText = infoObj["text"]
                        ?.jsonPrimitive?.contentOrNull
                        ?.takeIf { message is Message.User && it.isNotBlank() }
                    SseEvent.MessageUpdated(info = message, text = topText)
                }

                "message.removed" -> {
                    val sessionId = props.str("sessionID")
                    val messageId = props.str("messageID")
                    SseEvent.MessageRemoved(sessionId = sessionId, messageId = messageId)
                }

                "message.part.updated" -> {
                    val partObj = props["part"] as? JsonObject ?: return null
                    val part = parsePart(partObj) ?: return null
                    SseEvent.MessagePartUpdated(part = part)
                }

                "message.part.delta" -> {
                    val sessionId = props.str("sessionID")
                    val messageId = props.str("messageID")
                    val partId = props.str("partID")
                    val field = props.str("field", "text")
                    val delta = props.str("delta")
                    SseEvent.MessagePartDelta(
                        sessionId = sessionId,
                        messageId = messageId,
                        partId = partId,
                        field = field,
                        delta = delta
                    )
                }

                "message.part.removed" -> {
                    val sessionId = props.str("sessionID")
                    val messageId = props.str("messageID")
                    val partId = props.str("partID")
                    SseEvent.MessagePartRemoved(
                        sessionId = sessionId,
                        messageId = messageId,
                        partId = partId
                    )
                }

                "permission.asked" -> {
                    val id = props.str("id")
                    val sessionId = props.str("sessionID")
                    val permission = props.str("permission")
                    val patterns = (props["patterns"] as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
                    val always = (props["always"] as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
                    val metadata = (props["metadata"] as? JsonObject)?.let {
                        it.mapValues { (_, v) -> v }
                    }
                    val toolRef = (props["tool"] as? JsonObject)?.let { toolObj ->
                        ToolRef(
                            messageId = toolObj.str("messageID"),
                            callId = toolObj.str("callID")
                        )
                    }

                    Log.i(TAG, "Permission asked: $permission for session $sessionId")
                    SseEvent.PermissionAsked(
                        id = id,
                        sessionId = sessionId,
                        permission = permission,
                        patterns = patterns,
                        always = always,
                        metadata = metadata,
                        tool = toolRef
                    )
                }

                // V2 权限事件：字段为平铺 {id,sessionID,action,resources,save?,metadata?,source?}，
                // 与 V1 的 permission/patterns/tool 命名不同，需单独映射到同一 SseEvent 模型。
                "permission.v2.asked" -> parsePermissionV2Asked(props)

                "permission.replied", "permission.v2.replied" -> {
                    val sessionId = props.str("sessionID")
                    val requestId = props.str("requestID")
                    SseEvent.PermissionReplied(sessionId = sessionId, requestId = requestId)
                }

                "question.asked", "question.updated", "question.v2.asked" -> {
                    val id = props.str("id")
                    val sessionId = props.str("sessionID")
                    val toolRef = (props["tool"] as? JsonObject)?.let { toolObj ->
                        ToolRef(
                            messageId = toolObj.str("messageID"),
                            callId = toolObj.str("callID")
                        )
                    }
                    val questionsArr = props["questions"] as? JsonArray
                    val questions = questionsArr?.mapNotNull { qElement ->
                        val qObj = qElement as? JsonObject ?: return@mapNotNull null
                        val optionsArr = qObj["options"] as? JsonArray ?: JsonArray(emptyList())
                        val options = optionsArr.mapNotNull { oElement ->
                            val oObj = oElement as? JsonObject ?: return@mapNotNull null
                            SseEvent.QuestionAsked.Option(
                                label = oObj.str("label"),
                                description = oObj.str("description")
                            )
                        }
                        SseEvent.QuestionAsked.Question(
                            header = qObj.str("header"),
                            question = qObj.str("question"),
                            multiple = qObj["multiple"]?.jsonPrimitive?.booleanOrNull ?: false,
                            custom = qObj["custom"]?.jsonPrimitive?.booleanOrNull ?: true,
                            options = options
                        )
                    } ?: emptyList()
                    Log.i(
                        TAG,
                        "Question asked: session=$sessionId request=$id questions=${questions.size}",
                    )
                    SseEvent.QuestionAsked(
                        id = id,
                        sessionId = sessionId,
                        questions = questions,
                        tool = toolRef
                    )
                }

                "question.replied", "question.v2.replied" -> {
                    val sessionId = props.str("sessionID")
                    val requestId = props.str("requestID")
                    SseEvent.QuestionReplied(sessionId = sessionId, requestId = requestId)
                }

                "question.rejected", "question.v2.rejected" -> {
                    val sessionId = props.str("sessionID")
                    val requestId = props.str("requestID")
                    SseEvent.QuestionRejected(sessionId = sessionId, requestId = requestId)
                }

                "todo.updated" -> {
                    val sessionId = props.str("sessionID")
                    val todosArr = props["todos"] as? JsonArray
                    val todos = todosArr?.mapNotNull { tElement ->
                        val tObj = tElement as? JsonObject ?: return@mapNotNull null
                        SseEvent.TodoUpdated.Todo(
                            content = tObj.str("content"),
                            status = tObj.str("status", "pending"),
                            priority = tObj.str("priority", "medium")
                        )
                    } ?: emptyList()
                    SseEvent.TodoUpdated(sessionId = sessionId, todos = todos)
                }

                "vcs.branch.updated" -> {
                    val branch = props.str("branch")
                    SseEvent.VcsBranchUpdated(branch = branch)
                }

                "lsp.updated" -> SseEvent.LspUpdated

                "project.updated" -> {
                    val infoObj = (props["info"] as? JsonObject) ?: props
                    val info = json.decodeFromJsonElement<Project>(infoObj)
                    SseEvent.ProjectUpdated(info)
                }

                "sync", "pty.updated" -> null

                else -> {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Unhandled event: $type")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse $type: ${e.message}", e)
            null
        }
    }

    // ============ Message Parsing ============

    /**
     * Parse a Message from JSON, dispatching on "role" field.
     */
    private fun parseMessage(obj: JsonObject): Message? {
        val discriminator = obj["type"]?.jsonPrimitive?.content ?: obj["role"]?.jsonPrimitive?.content ?: return null
        return when (discriminator) {
            "user" -> json.decodeFromJsonElement<Message.User>(obj)
            "assistant" -> json.decodeFromJsonElement<Message.Assistant>(obj)
            else -> {
                Log.w(TAG, "Unknown message type: $discriminator")
                null
            }
        }
    }

    /**
     * Parse a Part from JSON, dispatching on "type" field.
     */
    private fun parsePart(obj: JsonObject): Part? {
        val type = obj["type"]?.jsonPrimitive?.content ?: return null
        return try {
            when (type) {
                "text" -> json.decodeFromJsonElement<Part.Text>(obj)
                "reasoning" -> json.decodeFromJsonElement<Part.Reasoning>(obj)
                "tool" -> json.decodeFromJsonElement<Part.Tool>(obj)
                "step-start" -> json.decodeFromJsonElement<Part.StepStart>(obj)
                "step-finish" -> json.decodeFromJsonElement<Part.StepFinish>(obj)
                "file" -> json.decodeFromJsonElement<Part.File>(obj)
                "snapshot" -> json.decodeFromJsonElement<Part.Snapshot>(obj)
                "patch" -> json.decodeFromJsonElement<Part.Patch>(obj)
                "subtask" -> json.decodeFromJsonElement<Part.Subtask>(obj)
                "compaction" -> json.decodeFromJsonElement<Part.Compaction>(obj)
                "retry" -> json.decodeFromJsonElement<Part.Retry>(obj)
                "agent" -> json.decodeFromJsonElement<Part.Agent>(obj)
                // 与 V2ContentItem.toPart 对齐：permission/question/abort 此前落到 else
                // 变成 Part.Unknown，于是流式期间不可见、历史重载后却显示为内联摘要——
                // 同一个请求在屏幕上出现两种表现。
                "permission" -> json.decodeFromJsonElement<Part.Permission>(obj)
                "question" -> json.decodeFromJsonElement<Part.Question>(obj)
                "abort" -> json.decodeFromJsonElement<Part.Abort>(obj)
                "session-turn" -> json.decodeFromJsonElement<Part.SessionTurn>(obj)
                else -> {
                    Log.w(TAG, "Unknown part type: $type")
                    // Return an Unknown part so it's at least tracked
                    Part.Unknown(
                        id = obj.str("id"),
                        sessionId = obj.str("sessionID"),
                        messageId = obj.str("messageID")
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse part type=$type: ${e.message}", e)
            null
        }
    }

    // ============ Helpers ============

    /** Safe string extraction with default. */
    private fun JsonObject.str(key: String, default: String = ""): String =
        this[key]?.jsonPrimitive?.content ?: default
}

/**
 * V2 `permission.v2.asked` 事件 data → [SseEvent.PermissionAsked]。
 * V2 字段平铺且命名与 V1 不同：action→permission、resources→patterns、save→always、source→tool。
 */
internal fun parsePermissionV2Asked(props: JsonObject): SseEvent.PermissionAsked {
    val resources = (props["resources"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
    val save = (props["save"] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
    val metadata = (props["metadata"] as? JsonObject)?.let { obj ->
        obj.mapValues { (_, v) -> v }
    }
    val toolRef = (props["source"] as? JsonObject)?.let { src ->
        ToolRef(
            messageId = src["messageID"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            callId = src["callID"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }
    return SseEvent.PermissionAsked(
        id = props["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        sessionId = props["sessionID"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        permission = props["action"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        patterns = resources,
        always = save,
        metadata = metadata,
        tool = toolRef,
    )
}

internal fun parseSessionError(props: JsonObject, json: Json): SseEvent.SessionError {
    val sessionId = props["sessionID"]?.jsonPrimitive?.contentOrNull
    val error = when (val value = props["error"]) {
        is JsonObject -> {
            val name = value["name"]?.jsonPrimitive?.contentOrNull
                ?: value["type"]?.jsonPrimitive?.contentOrNull
                ?: "Unknown error"
            Message.Assistant.ErrorInfo(name = name, data = value["message"] ?: value["data"])
        }
        is JsonPrimitive -> Message.Assistant.ErrorInfo(name = value.content)
        else -> Message.Assistant.ErrorInfo(name = "Unknown error")
    }
    return SseEvent.SessionError(sessionId = sessionId, error = error)
}

/** Thrown when SSE returns 401 or 403 (authentication failure, non-retryable). */
class SseAuthException(message: String) : Exception(message)

/** Thrown for SSE transport and HTTP failures. */
class SseConnectionException(
    message: String,
    val retryable: Boolean = true,
) : Exception(message)
