/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatViewModelSupport.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.BuildConfig
import org.hiylo.starburst.R
import org.hiylo.starburst.ml.AsrSession
import org.hiylo.starburst.ml.MnnLlm
import org.hiylo.starburst.ml.MnnAsr
import org.hiylo.starburst.ml.MnnAsrRecorder
import org.hiylo.starburst.ml.ServerAsrApi
import org.hiylo.starburst.ml.ServerAsrRecorder
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import org.hiylo.starburst.data.api.AgentInfo
import org.hiylo.starburst.data.api.CommandInfo
import org.hiylo.starburst.data.api.ModelSelection
import org.hiylo.starburst.data.api.MessageIdGenerator
import org.hiylo.starburst.data.api.OpenCodeApi
import org.hiylo.starburst.data.api.PromptPart
import org.hiylo.starburst.data.api.ProviderInfo
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.api.SuggestionProvider
import org.hiylo.starburst.data.api.SUGGESTION_API_SYSTEM
import org.hiylo.starburst.data.api.abortSession
import org.hiylo.starburst.data.api.createSession
import org.hiylo.starburst.data.api.executeCommand
import org.hiylo.starburst.data.api.exportSessionToStream
import org.hiylo.starburst.data.api.findFiles
import org.hiylo.starburst.data.api.forkSession
import org.hiylo.starburst.data.api.getCurrentProject
import org.hiylo.starburst.data.api.getProviders
import org.hiylo.starburst.data.api.getSession
import org.hiylo.starburst.data.api.listAgents
import org.hiylo.starburst.data.api.listChildSessions
import org.hiylo.starburst.data.api.listCommands
import org.hiylo.starburst.data.api.listMessages
import org.hiylo.starburst.data.api.listMessagesPage
import org.hiylo.starburst.data.api.listPendingPermissions
import org.hiylo.starburst.data.api.listPendingQuestions
import org.hiylo.starburst.data.api.listProjects
import org.hiylo.starburst.data.api.listSessionStatuses
import org.hiylo.starburst.data.api.promptAsync
import org.hiylo.starburst.data.api.rejectQuestion
import org.hiylo.starburst.data.api.replyToPermission
import org.hiylo.starburst.data.api.replyToQuestion
import org.hiylo.starburst.data.api.revertSession
import org.hiylo.starburst.data.api.runShellCommand
import org.hiylo.starburst.data.api.shareSession
import org.hiylo.starburst.data.api.summarizeSession
import org.hiylo.starburst.data.api.unrevertSession
import org.hiylo.starburst.data.api.unshareSession
import org.hiylo.starburst.data.api.updateSession
import org.hiylo.starburst.data.sync.LocalSyncSecretStore
import org.hiylo.starburst.data.repository.DraftRepository
import org.hiylo.starburst.data.repository.Draft
import org.hiylo.starburst.data.repository.EventReducer
import org.hiylo.starburst.data.repository.PendingPromptRecord
import org.hiylo.starburst.data.repository.PromptDeliveryInfo
import org.hiylo.starburst.data.repository.PromptDeliveryState
import org.hiylo.starburst.data.repository.PendingPromptRepository
import org.hiylo.starburst.data.repository.BookmarkRepository
import org.hiylo.starburst.data.repository.BackendRepository
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.data.repository.SettingsRepository
import org.hiylo.starburst.domain.model.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

internal const val TAG = "ChatViewModel"

internal fun Throwable.rethrowCancellation() {
    if (this is CancellationException) throw this
}

/**
 * 把异常 message 翻译为面向用户的友好文案（国际化）。
 *
 * 打开已删除/不存在的会话时，opencode 返回 404 + `{"name":"NotFoundError",...}` 之类的错误体，
 * 反序列化失败后的 message 会带着原始 JSON 直接上屏；这里统一识别并替换为「会话不存在」。
 * 若无法识别则回退到原始 message，保留其它错误信息可见。
 */
internal fun Throwable.friendlyErrorMessage(context: Context): String {
    val raw = message?.lowercase() ?: return ""
    val notFound = raw.contains("session not found") ||
        raw.contains("not found") ||
        raw.contains("notfounderror") ||
        raw.contains("404") ||
        raw.contains("no session")
    if (notFound) {
        return context.getString(R.string.session_not_found)
    }
    return message ?: ""
}
internal const val MAX_REVERT_RECOVERY_PAGES = 20
internal const val MAX_CHILD_SESSIONS = 100
private const val FAST_INITIAL_MESSAGE_COUNT = 10
private const val BACKGROUND_MESSAGE_PAGE_COUNT = 25
/** 高频流式状态（parts/messages）的节流采样间隔：SSE 流式每个 delta 都更新一次，
 *  若直接喂给 uiState 的 28 路 combine 会导致每个 delta 全量重算 + 全量 recompose。 */
internal const val STREAM_THROTTLE_MS = 50L
/** SSE 假死（心跳仍在但不再推消息）时，busy 期间通过 REST 拉取最新消息兜底的间隔。 */
internal const val BUSY_MESSAGE_POLL_MS = 10_000L

/** 上下文预算估算的字符/token 折算比（约 4 字符折合 1 token）。 */
private const val ASCII_CHARS_PER_TOKEN = 4.0

/** 中文等 CJK 字符的粗略 token 密度（约 1.5 字符/token），比 ASCII 的 4 字符/token 更贴合中文场景。 */
private const val CJK_CHARS_PER_TOKEN = 1.5

/** 当模型元数据与每服务器覆盖都缺失时使用的默认上下文窗口（token 数）。 */
internal const val DEFAULT_CONTEXT_WINDOW = 32768

/** 后端流式 ASR 引擎可用性的进程级缓存（按 serverId）。避免每次打开会话都发一次探测请求。 */
internal val backendAsrAvailableCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Boolean, Long>>()

/** 后端 ASR 可用性缓存有效期（毫秒），过期后重新探测。 */
internal const val BACKEND_ASR_CACHE_TTL_MS = 5 * 60 * 1000L

internal fun fastInitialMessageLimit(configuredLimit: Int): Int =
    configuredLimit.coerceAtLeast(1).coerceAtMost(FAST_INITIAL_MESSAGE_COUNT)

internal fun backgroundMessageLimit(loadedCount: Int, configuredLimit: Int): Int =
    (configuredLimit - loadedCount).coerceIn(1, BACKGROUND_MESSAGE_PAGE_COUNT)

internal fun descendantSessionIds(sessions: List<Session>, rootSessionId: String): Set<String> {
    val result = linkedSetOf(rootSessionId)
    var changed: Boolean
    do {
        changed = false
        sessions.forEach { session ->
            if (session.parentId in result && result.add(session.id)) changed = true
        }
    } while (changed)
    return result
}

@Immutable
/** 建议生成来源，用于在建议区显示小标签。 */
enum class SuggestionSource { BACKEND, CLOUD, ON_DEVICE, FALLBACK }

data class ChatUiState(
    val sessionTitle: String = "",
    /** 项目路径（会话所属项目目录），显示在标题栏副标题。 */
    val sessionDirectory: String = "",
    val sessionLoaded: Boolean = false,
    val parentSessionId: String? = null,
    /** 当前会话 fork 出的直接子会话列表（用于分支切换对比）。 */
    val childSessions: List<Session> = emptyList(),
    val serverName: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val revert: Session.Revert? = null,
    val sessionStatus: SessionStatus = SessionStatus.Idle,
    /** 会话级错误（session.error 事件），非空表示会话异常结束而非正常完成。 */
    val sessionError: String? = null,
    val pendingInteractions: List<PendingInteraction> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isSending: Boolean = false,
    val providers: List<ProviderInfo> = emptyList(),
    val hasServerModelCatalog: Boolean = false,
    val defaultModels: Map<String, String> = emptyMap(),
    val selectedProviderId: String? = null,
    val selectedModelId: String? = null,
    val totalCost: Double = 0.0,
    val totalInputTokens: Int = 0,
    val totalOutputTokens: Int = 0,
    val agents: List<AgentInfo> = emptyList(),
    val selectedAgent: String = "build",
    val variantNames: List<String> = emptyList(),
    val selectedVariant: String? = null,
    val commands: List<CommandInfo> = emptyList(),
    /** True when there are older messages on the server that haven't been loaded yet. */
    val hasOlderMessages: Boolean = false,
    /** True while a "load older" request is in flight. */
    val isLoadingOlder: Boolean = false,
    /** Share URL if session is shared, null otherwise. */
    val shareUrl: String? = null,
    /** Context window size of the current model (0 if unknown). */
    val contextWindow: Int = 0,
    /** Total tokens from the last assistant message with output > 0 (current context usage). */
    val lastContextTokens: Int = 0,
    /** 基于消息文本长度的保守上下文 token 估算（约 4 字符/token）。 */
    val estimatedContextTokens: Int = 0,
    /** 解析后的有效上下文窗口（模型元数据 > 每服务器覆盖 > 默认 32k）。 */
    val effectiveContextWindow: Int = 0,
    val contextUsage: ContextUsageDetails = ContextUsageDetails(),
    /** True while suggestions are being generated server-side. */
    val isGeneratingSuggestions: Boolean = false,
    /** Non-null when the last suggestion generation failed. */
    val suggestionsError: String? = null,
    /** Incremental text streamed from the on-device model while generating. */
    val suggestionsStreamText: String = "",
    /** Suggested next prompts for the current conversation (up to 3). */
    val suggestions: List<String> = emptyList(),
    /** 生成成功的建议的来源（后端 / 云端 / 端侧），用于 UI 显示来源标签。 */
    val suggestionsSource: SuggestionSource? = null,
    /** Whether the on-device model still needs to be downloaded (shown as a download prompt). */
    val modelNeedsDownload: Boolean = false,
    /** Whether the on-device model is being downloaded right now. */
    val modelDownloading: Boolean = false,
    /** Download progress percentage (0..100) while [modelDownloading]. */
    val modelDownloadProgress: Int = 0,
    /** Whether the currently selected model supports image/vision attachments. */
    val modelSupportsVision: Boolean = false,
)

/** 用户自定义的 Slash 命令（/name 插入 prompt）。 */
data class CustomSlashCommand(
    val name: String,
    val prompt: String,
)

/** 上下文 token 分布分类。 */
enum class ContextBreakdownKey {
    SYSTEM, USER, ASSISTANT, TOOL, OTHER;

    /** 分布条颜色（与 Web UI 一致的语义色）。 */
    val colorKey: ContextBreakdownColor get() = when (this) {
        SYSTEM -> ContextBreakdownColor.BLUE
        USER -> ContextBreakdownColor.GREEN
        ASSISTANT -> ContextBreakdownColor.PURPLE
        TOOL -> ContextBreakdownColor.ORANGE
        OTHER -> ContextBreakdownColor.GRAY
    }
}

/** 分布条分段颜色。 */
enum class ContextBreakdownColor {
    BLUE, GREEN, PURPLE, ORANGE, GRAY
}

/** 上下文 token 分布条的一个分段。 */
data class ContextBreakdownSegment(
    val key: ContextBreakdownKey,
    val tokens: Int,
    val percentage: Double,
)

data class ContextUsageDetails(
    val input: Int = 0,
    val output: Int = 0,
    val reasoning: Int = 0,
    val cacheRead: Int = 0,
    val cacheWrite: Int = 0,
    val sessionInput: Int = 0,
    val sessionOutput: Int = 0,
    val sessionReasoning: Int = 0,
    val sessionCacheRead: Int = 0,
    val sessionCacheWrite: Int = 0,
    val totalCost: Double = 0.0,
    val userMessages: Int = 0,
    val assistantMessages: Int = 0,
    val providerLabel: String? = null,
    val modelLabel: String? = null,
    val sessionTitle: String? = null,
    val sessionCreatedAt: Long? = null,
    val lastActivityAt: Long? = null,
    val systemPrompt: String? = null,
    val breakdown: List<ContextBreakdownSegment> = emptyList(),
) {
    val currentTotal: Int get() = input + output + reasoning + cacheRead + cacheWrite
    val sessionTotal: Int get() = sessionInput + sessionOutput + sessionReasoning + sessionCacheRead + sessionCacheWrite
}

/**
 * 估算上下文 token 在各类别中的分布（system / user / assistant / tool / other）。
 * 参考 OpenCode Web UI 的 estimateSessionContextBreakdown 逻辑。
 */
internal fun computeContextBreakdown(
    messages: List<ChatMessage>,
    input: Int,
    systemPrompt: String?,
): List<ContextBreakdownSegment> {
    if (input <= 0) return emptyList()

    var systemChars = systemPrompt?.length ?: 0
    var userChars = 0
    var assistantChars = 0
    var toolChars = 0

    for (msg in messages) {
        when (val m = msg.message) {
            is Message.User -> {
                for (part in msg.parts) {
                    when (part) {
                        is Part.Text -> userChars += part.text.length
                        is Part.File -> part.source?.let { source ->
                            val text = (source as? kotlinx.serialization.json.JsonObject)
                                ?.get("text")
                                ?.let { (it as? kotlinx.serialization.json.JsonObject)?.get("value") }
                            text?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.length ?: 0 }
                        }
                        is Part.Agent -> part.source?.let {
                            (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.length ?: 0
                        }
                        else -> {}
                    }
                }
            }
            is Message.Assistant -> {
                for (part in msg.parts) {
                    when (part) {
                        is Part.Text -> assistantChars += part.text.length
                        is Part.Reasoning -> assistantChars += part.text.length
                        is Part.Tool -> {
                            val state = part.state
                            val inputSize = when (state) {
                                is ToolState.Pending -> (state.input?.size ?: 0) * 16 + (state.raw?.length ?: 0)
                                is ToolState.Running -> (state.input?.size ?: 0) * 16
                                is ToolState.Completed -> (state.input?.size ?: 0) * 16 + (state.output?.length ?: 0)
                                is ToolState.Error -> (state.input?.size ?: 0) * 16 + (state.error?.length ?: 0)
                            }
                            toolChars += inputSize
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    val estimateTokens = { chars: Int -> Math.ceil(chars / 4.0).toInt() }
    val systemTokens = estimateTokens(systemChars)
    val userTokens = estimateTokens(userChars)
    val assistantTokens = estimateTokens(assistantChars)
    val toolTokens = estimateTokens(toolChars)
    val estimated = systemTokens + userTokens + assistantTokens + toolTokens

    val scale = if (estimated > input) input.toDouble() / estimated else 1.0
    val scaledSystem = Math.floor(systemTokens * scale).toInt()
    val scaledUser = Math.floor(userTokens * scale).toInt()
    val scaledAssistant = Math.floor(assistantTokens * scale).toInt()
    val scaledTool = Math.floor(toolTokens * scale).toInt()
    val total = scaledSystem + scaledUser + scaledAssistant + scaledTool
    val other = (input - total).coerceAtLeast(0)

    val toPercent = { tokens: Int -> if (input > 0) (tokens.toDouble() / input * 100) else 0.0 }

    return listOf(
        ContextBreakdownSegment(ContextBreakdownKey.SYSTEM, scaledSystem, toPercent(scaledSystem)),
        ContextBreakdownSegment(ContextBreakdownKey.USER, scaledUser, toPercent(scaledUser)),
        ContextBreakdownSegment(ContextBreakdownKey.ASSISTANT, scaledAssistant, toPercent(scaledAssistant)),
        ContextBreakdownSegment(ContextBreakdownKey.TOOL, scaledTool, toPercent(scaledTool)),
        ContextBreakdownSegment(ContextBreakdownKey.OTHER, other, toPercent(other)),
    ).filter { it.tokens > 0 }
}

/**
 * 基于当前会话消息文本长度做保守的 token 估算：ASCII 约 4 字符/token，CJK 约 1.5 字符/token。
 * 服务端未提供精确 token 计数时，供上下文预算指示器做轻量估算，
 * 只统计文本/推理/快照等文本型 part，忽略工具调用等难以折算的部分。
 */
internal fun estimateContextTokens(messages: List<ChatMessage>): Int {
    var asciiChars = 0
    var cjkChars = 0
    for (msg in messages) {
        for (part in msg.parts) {
            val text = when (part) {
                is Part.Text -> part.text
                is Part.Reasoning -> part.text
                is Part.Snapshot -> part.snapshot
                is Part.StepStart -> part.snapshot ?: ""
                else -> ""
            }
            for (c in text) {
                if (isCjk(c)) cjkChars++ else asciiChars++
            }
        }
    }
    return Math.ceil(asciiChars / ASCII_CHARS_PER_TOKEN + cjkChars / CJK_CHARS_PER_TOKEN).toInt()
}

/** 粗略判定 CJK 字符（中日韩统一表意文字 + 假名 + 谚文），用于区分 token 密度。 */
private fun isCjk(c: Char): Boolean {
    val code = c.code
    return code in 0x2E80..0x9FFF ||
        code in 0xF900..0xFAFF ||
        code in 0x3040..0x30FF ||
        code in 0xAC00..0xD7AF
}

internal fun sessionAcceptsPrompts(session: Session?): Boolean = session != null && session.parentId == null

internal fun needsOlderHistoryForRevert(messageIds: Collection<String>, revertMessageId: String?): Boolean {
    return revertMessageId != null && messageIds.none { it < revertMessageId }
}

internal fun missingPendingPromptIds(
    pending: List<PendingPromptRecord>,
    authoritative: List<MessageWithParts>,
    now: Long,
    minimumAgeMs: Long,
): Set<String> {
    val oldestMessageId = authoritative.minOfOrNull { it.info.id } ?: return emptySet()
    val authoritativeIds = authoritative.mapTo(mutableSetOf()) { it.info.id }
    return pending.asSequence()
        .filter { it.messageId !in authoritativeIds }
        .filter { it.messageId >= oldestMessageId }
        .filter { now - it.createdAt >= minimumAgeMs }
        .mapTo(mutableSetOf(), PendingPromptRecord::messageId)
}

data class RevertedDraftPayload(
    val text: String,
    val attachmentUris: List<String> = emptyList(),
)

/**
 * A flattened chat message for the UI.
 * Combines Message info with its parts.
 */
@Immutable
data class ChatMessage(
    val message: Message,
    val parts: List<Part>,
    val delivery: MessageDelivery? = null,
) {
    val isUser: Boolean get() = message is Message.User
    val isAssistant: Boolean get() = message is Message.Assistant
}

enum class MessageDelivery { QUEUED, PROMOTED }

@Immutable
data class ChatTurn(val messages: List<ChatMessage>) {
    val isUser: Boolean get() = messages.singleOrNull()?.isUser == true
    val key: String get() = if (isUser) "u_${messages.single().message.id}" else "t_${messages.first().message.id}"
}

internal fun groupChatTurns(messages: List<ChatMessage>): List<ChatTurn> {
    val result = mutableListOf<ChatTurn>()
    var assistantRun = mutableListOf<ChatMessage>()

    fun flushAssistantRun() {
        if (assistantRun.isNotEmpty()) {
            result += ChatTurn(assistantRun)
            assistantRun = mutableListOf()
        }
    }

    messages.forEach { message ->
        // 过滤回答过程中尚无任何可渲染 part 的空 assistant 消息（流式创建时 parts 未到达）。
        if (message.isAssistant && message.parts.none(::isBubbleRenderablePart)) return@forEach
        if (message.isAssistant) {
            assistantRun += message
        } else {
            flushAssistantRun()
            result += ChatTurn(listOf(message))
        }
    }
    flushAssistantRun()
    return result
}

internal fun PendingPromptRecord.toChatMessage(delivery: MessageDelivery = MessageDelivery.QUEUED): ChatMessage {
    val message = Message.User(
        id = messageId,
        sessionId = sessionId,
        time = TimeInfo(createdAt),
        agent = agent,
        model = model?.let { Message.User.Model(it.providerId, it.modelId) },
        variant = variant,
    )
    return ChatMessage(message, toLocalParts(), delivery)
}

internal fun PendingPromptRecord.toLocalParts(): List<Part> =
    parts.mapIndexedNotNull { index, part ->
        when (part.type) {
            "text" -> part.text?.takeIf { it.isNotBlank() }?.let {
                Part.Text("$messageId-local-$index", sessionId, messageId, text = it)
            }
            "file" -> Part.File(
                id = "$messageId-local-$index",
                sessionId = sessionId,
                messageId = messageId,
                mime = part.mime ?: "application/octet-stream",
                filename = part.filename ?: part.path,
                url = part.url,
            )
            else -> null
        }
    }

/** Number of recent user/assistant turns included in the suggestion prompt. */
internal const val SUGGESTION_CONTEXT_MAX_ROUNDS = 4

/** Maximum output tokens for on-device suggestion generation. */
internal const val SUGGESTION_MAX_TOKENS = 100

/** Maximum output tokens for on-device summary generation. */
internal const val SUMMARY_MAX_TOKENS = 256

/**
 * Extracts up to 3 suggestion strings from an assistant reply.
 * Tolerates thinking tags, markdown fences, and surrounding prose by scanning for a JSON array.
 */
internal fun parseSuggestionList(text: String?): List<String> {
    if (text.isNullOrBlank()) return emptyList()
    var trimmed = text.trim()

    // Strip Qwen thinking-mode wrappers: <think>...</think> or ☞thinking...☜
    trimmed = trimmed.replace(Regex("(?s)<think>.*?</think>"), "")
    trimmed = trimmed.replace(Regex("(?s)<!thinking>.*?<!/thinking>"), "")
    // If there's a "response:" or "</think>" marker, take everything after the last one.
    val markers = listOf("</think>", "<think>", "<|im_end|>")
    for (m in markers) {
        val idx = trimmed.lastIndexOf(m)
        if (idx >= 0) trimmed = trimmed.substring(idx + m.length).trim()
    }

    // Prefer a fenced code block if present.
    val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(trimmed)
    var candidate = fence?.groupValues?.get(1)?.trim() ?: trimmed

    // Extract the JSON array portion.
    val start = candidate.indexOf('[')
    val end = candidate.lastIndexOf(']')
    if (start >= 0 && end > start) {
        candidate = candidate.substring(start, end + 1)
    } else if (trimmed.count { it == '\n' } >= 2) {
        // Multi-line list fallback (e.g. numbered items) only when there are clearly
        // several lines — never for a single prose sentence.
        val lines = trimmed.split('\n').map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("```") }
            .map { it.replace(Regex("^\\d+[.)]\\s*"), "") }
            .filter { it.length in 2..80 }
        return lines.take(3)
    } else {
        return emptyList()
    }

    return runCatching {
        val json = Json { ignoreUnknownKeys = true }
        json.decodeFromString<List<String>>(candidate)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(3)
    }.getOrElse {
        // Fallback: split by newlines if JSON parse fails.
        val lines = candidate.split('\n').map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.trim('"', ' ', ',') }
            .filter { it.length in 2..80 }
        lines.take(3)
    }
}

/** Holds server connection info for navigation purposes. */
data class ConnectionParams(
    val serverUrl: String,
    val username: String,
    val password: String,
    val serverName: String,
    val serverId: String
)
