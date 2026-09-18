/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatViewModel.kt
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

private const val TAG = "ChatViewModel"

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
private const val MAX_REVERT_RECOVERY_PAGES = 20
private const val MAX_CHILD_SESSIONS = 100
private const val FAST_INITIAL_MESSAGE_COUNT = 10
private const val BACKGROUND_MESSAGE_PAGE_COUNT = 25
/** 高频流式状态（parts/messages）的节流采样间隔：SSE 流式每个 delta 都更新一次，
 *  若直接喂给 uiState 的 28 路 combine 会导致每个 delta 全量重算 + 全量 recompose。 */
private const val STREAM_THROTTLE_MS = 50L
/** SSE 假死（心跳仍在但不再推消息）时，busy 期间通过 REST 拉取最新消息兜底的间隔。 */
private const val BUSY_MESSAGE_POLL_MS = 10_000L

/** 上下文预算估算的字符/token 折算比（约 4 字符折合 1 token）。 */
private const val ASCII_CHARS_PER_TOKEN = 4.0

/** 中文等 CJK 字符的粗略 token 密度（约 1.5 字符/token），比 ASCII 的 4 字符/token 更贴合中文场景。 */
private const val CJK_CHARS_PER_TOKEN = 1.5

/** 当模型元数据与每服务器覆盖都缺失时使用的默认上下文窗口（token 数）。 */
private const val DEFAULT_CONTEXT_WINDOW = 32768

/** 后端流式 ASR 引擎可用性的进程级缓存（按 serverId）。避免每次打开会话都发一次探测请求。 */
private val backendAsrAvailableCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Boolean, Long>>()

/** 后端 ASR 可用性缓存有效期（毫秒），过期后重新探测。 */
private const val BACKEND_ASR_CACHE_TTL_MS = 5 * 60 * 1000L

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
    val sessionLoaded: Boolean = false,
    val parentSessionId: String? = null,
    /** 当前会话 fork 出的直接子会话列表（用于分支切换对比）。 */
    val childSessions: List<Session> = emptyList(),
    val serverName: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val revert: Session.Revert? = null,
    val sessionStatus: SessionStatus = SessionStatus.Idle,
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

private fun PendingPromptRecord.toChatMessage(delivery: MessageDelivery = MessageDelivery.QUEUED): ChatMessage {
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

private fun PendingPromptRecord.toLocalParts(): List<Part> =
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

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val eventReducer: EventReducer,
    private val api: OpenCodeApi,
    private val draftRepository: DraftRepository,
    private val settingsRepository: SettingsRepository,
    private val pendingPromptRepository: PendingPromptRepository,
    private val suggestionProvider: SuggestionProvider,
    private val secretStore: LocalSyncSecretStore,
    private val bookmarkRepository: BookmarkRepository,
    private val backendRepository: BackendRepository,
    private val serverRepository: ServerRepository,
    private val serverAsrApi: ServerAsrApi,
) : ViewModel() {

    @Volatile
    private var sessionPromptable = false

    private val serverUrl: String = savedStateHandle.get<String>("serverUrl").orEmpty()
    private val username: String = savedStateHandle.get<String>("username").orEmpty()
    private val password: String = savedStateHandle.get<String>("password").orEmpty()
    val serverName: String = savedStateHandle.get<String>("serverName").orEmpty()
    val serverId: String = savedStateHandle.get<String>("serverId").orEmpty()
    val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty()
    private val retryOnOpen: Boolean = savedStateHandle.get<Boolean>("retry") ?: false

    private val conn = ServerConnection.from(serverUrl, username, password.ifEmpty { null })

    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)
    private val _isSending = MutableStateFlow(false)
    private val _pendingPrompts = MutableStateFlow<List<PendingPromptRecord>>(emptyList())
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    private val _suggestionsSource = MutableStateFlow<SuggestionSource?>(null)
    private val _isGeneratingSuggestions = MutableStateFlow(false)
    private val _suggestionsError = MutableStateFlow<String?>(null)
    /** Incremental text streamed while suggestions are being generated (for UI feedback). */
    private val _suggestionsStreamText = MutableStateFlow("")
    private val _modelNeedsDownload = MutableStateFlow(false)
    private val _modelDownloading = MutableStateFlow(false)
    private val _modelDownloadProgress = MutableStateFlow(0)
    /** 每服务器覆盖的上下文窗口（token 数，0 表示未覆盖），来自 SettingsRepository。 */
    private val _serverContextLimitOverride = MutableStateFlow(0)
    /** Whether the current project is a Git repository (Project.vcs == "git"). */
    private val _isGitRepository = MutableStateFlow(false)
    val isGitRepository: StateFlow<Boolean> = _isGitRepository
    // ============ Conversation summary ============
    /** True while a message/conversation summary is being generated. */
    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing
    /** The latest generated summary text, or null when no summary is shown. */
    private val _summaryText = MutableStateFlow<String?>(null)
    val summaryText: StateFlow<String?> = _summaryText
    /** Non-null when the last summary generation failed. */
    private val _summaryError = MutableStateFlow<String?>(null)
    val summaryError: StateFlow<String?> = _summaryError

    private val _summaryVisible = MutableStateFlow(false)
    val summaryVisible: StateFlow<Boolean> = _summaryVisible
    /** Monotonic token invalidating in-flight suggestion generations when the conversation changes. */
    private var suggestionsGeneration = 0L
    private val _allProviders = MutableStateFlow<List<ProviderInfo>>(emptyList())
    private val _providers = MutableStateFlow<List<ProviderInfo>>(emptyList())
    private val _hiddenModels = MutableStateFlow<Set<String>>(emptySet())
    private val _defaultModels = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _selectedProviderId = MutableStateFlow<String?>(null)
    private val _selectedModelId = MutableStateFlow<String?>(null)
    // Track if the model was explicitly selected by the user to avoid overwriting it with defaults/history
    private var isModelExplicitlySelected = false
    /** The directory of this session's project — sent as x-starburst-directory so the server resolves the correct project context. */
    private var sessionDirectory: String? = eventReducer.sessions.value
        .firstOrNull { it.id == sessionId }
        ?.directory
        ?.takeIf { it.isNotBlank() }
    /** Signals when [loadSession] has finished (successfully or with error), so that terminal
     *  creation can wait for [sessionDirectory] to be populated. */
    private val sessionLoaded = CompletableDeferred<Unit>()
    private val _agents = MutableStateFlow<List<AgentInfo>>(emptyList())
    /** Pair(agentName, explicitlySelected) — using a single flow avoids race between flag and value */
    private val _selectedAgent = MutableStateFlow("build" to false)
    private val _selectedVariant = MutableStateFlow<String?>(null)
    private val _commands = MutableStateFlow<List<CommandInfo>>(emptyList())
    private val terminalWorkspace = ServerTerminalRegistry.workspaceFor(serverId, api, conn)
    val terminalTabs: StateFlow<List<TerminalTabUi>> = terminalWorkspace.tabList
    val activeTerminalTabId: StateFlow<String?> = terminalWorkspace.activeTabId
    /** Incremented on active terminal tab updates — observe to trigger recomposition. */
    val terminalVersion: StateFlow<Long> = terminalWorkspace.activeVersion
    val terminalConnected: StateFlow<Boolean> = terminalWorkspace.activeConnected
    val terminalFontSizeSp: StateFlow<Float> = terminalWorkspace.activeFontSizeSp
    val terminalEmulator: TerminalEmulator get() = terminalWorkspace.activeEmulator()

    // ============ Draft Persistence ============
    /** Draft text for the input field — survives navigation / app restart. */
    private val _draftText = MutableStateFlow("")
    val draftText: StateFlow<String> = _draftText

    /** One-shot event: emits reverted draft payload (text + image attachments) for ChatScreen. */
    private val _revertedDraftEvent = MutableSharedFlow<RevertedDraftPayload>(extraBufferCapacity = 1)
    val revertedDraftEvent: SharedFlow<RevertedDraftPayload> = _revertedDraftEvent

    /** Draft attachment URIs (content:// URIs as strings) — survives navigation / app restart. */
    private val _draftAttachmentUris = MutableStateFlow<List<String>>(emptyList())
    val draftAttachmentUris: StateFlow<List<String>> = _draftAttachmentUris

    /** Set of file paths that have been confirmed by user selection from the popup */
    private val _confirmedFilePaths = MutableStateFlow<Set<String>>(emptySet())
    val confirmedFilePaths: StateFlow<Set<String>> = _confirmedFilePaths

    // ============ Voice input (ASR) ============
    /** Whether the mic is currently recording / recognizing. */
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening
    /** One-shot final recognition result (appended to the input). */
    private val _recognizedText = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val recognizedText: SharedFlow<String> = _recognizedText
    /** Real-time partial recognition results (replaces the previous partial in the input). */
    private val _partialRecognizedText = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val partialRecognizedText: SharedFlow<String> = _partialRecognizedText
    /** Live microphone volume level (rmsdB, ~0..10) for the waveform UI. */
    private val _voiceLevel = MutableStateFlow(0f)
    val voiceLevel: StateFlow<Float> = _voiceLevel
    /** One-shot ASR error message resource id (null when none). */
    private val _speechError = MutableStateFlow<Int?>(null)
    val speechError: StateFlow<Int?> = _speechError
    private var speechRecognition: SpeechRecognition? = null
    /** 端侧 MNN 模型不可用时回退到后端代理的流式引擎，两者共用同一套回调。 */
    private var asrRecorder: AsrSession? = null

    /** 后端流式识别引擎是否可用（启动时探测一次），决定麦克风按钮是否显示。 */
    private val _backendAsrAvailable = MutableStateFlow(false)
    val backendAsrAvailable: StateFlow<Boolean> = _backendAsrAvailable

    /** 当前会话的文件变更列表（agent 改动过哪些文件），用于「查看变更」面板。 */
    val sessionDiffs: StateFlow<List<FileDiff>> =
        eventReducer.sessionDiffs.map { it[sessionId].orEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前会话的最新 Todo 快照（agent 任务进度），用于「会话时间线」重建 todo 进度。 */
    val sessionTodos: StateFlow<List<SseEvent.TodoUpdated.Todo>> =
        eventReducer.todos.map { it[sessionId].orEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 用户自定义 Slash 命令。 */
    val customCommands: StateFlow<List<CustomSlashCommand>> =
        settingsRepository.customCommands.map { list ->
            list.map { CustomSlashCommand(it.name, it.prompt) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCustomCommand(name: String, prompt: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || prompt.isBlank()) return false
        if (customCommands.value.any { it.name == trimmed }) return false
        viewModelScope.launch { settingsRepository.addCustomCommand(trimmed, prompt) }
        return true
    }

    fun removeCustomCommand(name: String) {
        viewModelScope.launch { settingsRepository.removeCustomCommand(name) }
    }

    /** 用户自定义的命名 Prompt 模板（全局共享，内置模板由 UI 层以字符串资源提供）。 */
    val promptTemplates: StateFlow<List<SettingsRepository.PromptTemplate>> =
        settingsRepository.promptTemplates
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addPromptTemplate(name: String, prompt: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || prompt.isBlank()) return false
        if (promptTemplates.value.any { it.name == trimmed }) return false
        viewModelScope.launch { settingsRepository.savePromptTemplate(null, trimmed, prompt) }
        return true
    }

    fun updatePromptTemplate(id: String, name: String, prompt: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || prompt.isBlank()) return
        viewModelScope.launch { settingsRepository.savePromptTemplate(id, trimmed, prompt) }
    }

    fun deletePromptTemplate(id: String) {
        viewModelScope.launch { settingsRepository.deletePromptTemplate(id) }
    }

    fun movePromptTemplate(id: String, offset: Int) {
        viewModelScope.launch { settingsRepository.movePromptTemplate(id, offset) }
    }

    // ============ Settings (exposed for ChatScreen) ============
    val chatFontSize = settingsRepository.chatFontSize.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "medium"
    )
    val chatLineHeight = settingsRepository.chatLineHeight.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 1f
    )
    val codeWordWrap = settingsRepository.codeWordWrap.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    val confirmBeforeSend = settingsRepository.confirmBeforeSend.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    val compactMessages = settingsRepository.compactMessages.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    val collapseTools = settingsRepository.collapseTools.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    val expandReasoning = settingsRepository.expandReasoning.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    val showTurnDividers = settingsRepository.showTurnDividers.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )
    val hapticFeedback = settingsRepository.hapticFeedback.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )
    val hapticDurationMillis = settingsRepository.hapticDurationMillis.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 30
    )
    val hapticAmplitude = settingsRepository.hapticAmplitude.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 160
    )
    val keepScreenOn = settingsRepository.keepScreenOn.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )
    val compressImageAttachments = settingsRepository.compressImageAttachments.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), true
    )
    val imageAttachmentMaxLongSide = settingsRepository.imageAttachmentMaxLongSide.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 1440
    )
    val imageAttachmentWebpQuality = settingsRepository.imageAttachmentWebpQuality.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), 60
    )

    suspend fun shouldShowTerminalPanelHint(): Boolean = settingsRepository.showTerminalPanelHint.first()
    // ============ Pagination ============
    /** Current message limit (doubles each time user loads older messages). */
    private var currentMessageLimit = 50
    private var olderMessagesCursor: String? = null
    /** Whether there are more messages on the server beyond the current limit. */
    private val _hasOlderMessages = MutableStateFlow(false)
    /** Whether a "load older" request is in flight. */
    private val _isLoadingOlder = MutableStateFlow(false)

    // 高频状态节流采样：SSE 流式输出时每个 delta 都会更新 parts/messages，
    // 直接喂给 28 路 combine 会让每个 delta 都触发一次 O(N) 全量重算 + 全量 recompose，
    // 大会话下导致流式「一块一块蹦」、会话状态刷新慢。这里每 ~STREAM_THROTTLE_MS 采样一次
    // 最新值，把重算频率降到每 50ms 最多一次，流式依旧流畅。
    // 用 MutableStateFlow 桥接（而非纯 flow），以便在「加载完成」等关键时点通过
    // flushThrottledState() 立即同步，避免节流延迟导致瞬间出现「空会话」界面。
    private val throttledMessages = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    private val throttledParts = MutableStateFlow<Map<String, List<Part>>>(emptyMap())

    /** 立即把节流状态同步到最新值（加载完成、会话切换等关键时点调用，避免空状态闪现）。 */
    private fun flushThrottledState() {
        throttledMessages.value = eventReducer.messages.value
        throttledParts.value = eventReducer.parts.value
    }

    val uiState: StateFlow<ChatUiState> = combine(
        eventReducer.sessions,
        throttledMessages,
        throttledParts,
        eventReducer.sessionStatuses,
        eventReducer.pendingInteractions,
        _isLoading,
        _error,
        _isSending,
        _selectedProviderId,
        _selectedModelId,
        _allProviders,
        _providers,
        _defaultModels,
        _agents,
        _selectedAgent,
        _selectedVariant,
        _commands,
        _hasOlderMessages,
        _isLoadingOlder,
        _pendingPrompts,
        eventReducer.promptDeliveries,
        _suggestions,
        _suggestionsSource,
        _isGeneratingSuggestions,
        _suggestionsError,
        _suggestionsStreamText,
        _modelNeedsDownload,
        _modelDownloading,
        _modelDownloadProgress,
        _serverContextLimitOverride,
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val allSessions = args[0] as List<Session>
        val allMessages = args[1] as Map<String, List<Message>>
        val allParts = args[2] as Map<String, List<Part>>
        val statuses = args[3] as Map<String, SessionStatus>
        val pendingInteractions = args[4] as List<PendingInteraction>
        val loading = args[5] as Boolean
        val error = args[6] as String?
        val sending = args[7] as Boolean
        val selProviderId = args[8] as String?
        val selModelId = args[9] as String?
        val allProviders = args[10] as List<ProviderInfo>
        val providers = args[11] as List<ProviderInfo>
        val defaultModels = args[12] as Map<String, String>
        val agents = args[13] as List<AgentInfo>
        @Suppress("UNCHECKED_CAST")
        val agentSelection = args[14] as Pair<String, Boolean>
        val selectedAgent = agentSelection.first
        val isAgentExplicitlySelected = agentSelection.second
        val selectedVariant = args[15] as String?
        val commands = args[16] as List<CommandInfo>
        val hasOlderMessages = args[17] as Boolean
        val isLoadingOlder = args[18] as Boolean
        val pendingPrompts = args[19] as List<PendingPromptRecord>
        val promptDeliveries = args[20] as Map<String, PromptDeliveryInfo>
        val suggestions = args[21] as List<String>
        val suggestionsSource = args[22] as SuggestionSource?
        val isGeneratingSuggestions = args[23] as Boolean
        val suggestionsError = args[24] as String?
        val suggestionsStreamText = args[25] as String
        val modelNeedsDownload = args[26] as Boolean
        val modelDownloading = args[27] as Boolean
        val modelDownloadProgress = args[28] as Int
        val serverContextLimitOverride = args[29] as Int
        fun deliveryFor(messageId: String) = when (promptDeliveries[messageId]?.state) {
            PromptDeliveryState.PROMOTED -> MessageDelivery.PROMOTED
            else -> MessageDelivery.QUEUED
        }

        val session = allSessions.find { it.id == sessionId }
        val childSessions = allSessions.filter { it.parentId == sessionId }
        val interactionSessionIds = descendantSessionIds(allSessions, sessionId)
        val sessionMessages = allMessages[sessionId] ?: emptyList()
        val confirmedMessageIds = sessionMessages.asSequence().map { it.id }.toSet()
        val pendingById = pendingPrompts.associateBy { it.messageId }
        val optimisticMessages = pendingPrompts
            .filterNot { it.messageId in confirmedMessageIds }
            .map { it.toChatMessage(deliveryFor(it.messageId)) }
        val revertState = session?.revert

        val chatMessages = run {
            val sorted = sessionMessages.sortedBy { it.time.created }
            // Filter out reverted messages (at or after revert point)
            val visible = if (revertState != null) {
                sorted.filter { it.id < revertState.messageId }
            } else {
                sorted
            }
            suppressRepeatedPatchCards(
                (visible.map { msg ->
                    val pending = pendingById[msg.id]
                    val authoritativeParts = allParts[msg.id].orEmpty()
                    ChatMessage(
                        message = msg,
                        parts = authoritativeParts.ifEmpty { pending?.toLocalParts().orEmpty() },
                        delivery = pending?.let { deliveryFor(msg.id) },
                    )
                } + optimisticMessages).sortedBy { it.message.time.created },
            )
        }

        // Resolve model: explicit selection > last user message's model > provider default
        var effectiveProviderId = selProviderId
        var effectiveModelId = selModelId

        // If no explicit selection, try to resolve from history
        if (!isModelExplicitlySelected) {
             val lastUserWithModel = sessionMessages
                .filterIsInstance<Message.User>()
                .lastOrNull { it.model != null }
             if (lastUserWithModel?.model != null) {
                 effectiveProviderId = lastUserWithModel.model.providerId
                 effectiveModelId = lastUserWithModel.model.modelId
             } else if (effectiveModelId == null && defaultModels.isNotEmpty()) {
                 // Fallback to default if nothing in history and nothing selected
                 val entry = defaultModels.entries.first()
                 effectiveProviderId = entry.key
                 effectiveModelId = entry.value
             }
        }

        // Resolve agent from last user message if not explicitly changed
        val effectiveAgent = if (!isAgentExplicitlySelected) {
            val lastUserAgent = sessionMessages
                .filterIsInstance<Message.User>()
                .lastOrNull { it.agent != null }
                ?.agent
            lastUserAgent ?: selectedAgent
        } else {
            selectedAgent
        }

        // Keep raw state in sync so sendParts()/runShellCommand() always use the displayed value
        if (effectiveAgent != selectedAgent && !isAgentExplicitlySelected) {
            _selectedAgent.value = effectiveAgent to false
        }

        // Compute cost/token totals from assistant messages
        val assistantMessages = sessionMessages.filterIsInstance<Message.Assistant>()
        val totalCost = assistantMessages.sumOf { it.cost ?: 0.0 }
        val totalInputTokens = assistantMessages.sumOf { it.tokens?.input ?: 0 }
        val totalOutputTokens = assistantMessages.sumOf { it.tokens?.output ?: 0 }
        // Context usage: total tokens from the last assistant message with output > 0
        val lastWithOutput = assistantMessages.lastOrNull { (it.tokens?.output ?: 0) > 0 }
        val lastContextTokens = lastWithOutput?.tokens?.let { t ->
            t.input + t.output + t.reasoning + t.cache.read + t.cache.write
        } ?: 0
        // Extract system prompt from the last user message that has one
        val systemPrompt = sessionMessages
            .filterIsInstance<Message.User>()
            .sortedBy { it.time.created }
            .lastOrNull { !it.system.isNullOrBlank() }
            ?.system
        // Build context breakdown
        val breakdown = computeContextBreakdown(chatMessages, lastWithOutput?.tokens?.input ?: 0, systemPrompt)
        // Provider/model labels from the last assistant message
        val lastAssistant = lastWithOutput
        val providerLabel = lastAssistant?.providerId?.let { pid ->
            providers.find { it.id == pid }?.name ?: pid
        } ?: effectiveProviderId?.let { pid ->
            providers.find { it.id == pid }?.name ?: pid
        }
        val modelLabel = lastAssistant?.modelId?.let { mid ->
            providers.find { it.id == lastAssistant.providerId }?.models?.get(mid)?.name ?: mid
        } ?: effectiveModelId?.let { mid ->
            providers.find { it.id == effectiveProviderId }?.models?.get(mid)?.name ?: mid
        }
        val contextUsage = ContextUsageDetails(
            input = lastWithOutput?.tokens?.input ?: 0,
            output = lastWithOutput?.tokens?.output ?: 0,
            reasoning = lastWithOutput?.tokens?.reasoning ?: 0,
            cacheRead = lastWithOutput?.tokens?.cache?.read ?: 0,
            cacheWrite = lastWithOutput?.tokens?.cache?.write ?: 0,
            sessionInput = totalInputTokens,
            sessionOutput = totalOutputTokens,
            sessionReasoning = assistantMessages.sumOf { it.tokens?.reasoning ?: 0 },
            sessionCacheRead = assistantMessages.sumOf { it.tokens?.cache?.read ?: 0 },
            sessionCacheWrite = assistantMessages.sumOf { it.tokens?.cache?.write ?: 0 },
            totalCost = totalCost,
            userMessages = sessionMessages.count { it is Message.User },
            assistantMessages = assistantMessages.size,
            providerLabel = providerLabel,
            modelLabel = modelLabel,
            sessionTitle = session?.title,
            sessionCreatedAt = session?.time?.created,
            lastActivityAt = lastWithOutput?.time?.created,
            systemPrompt = systemPrompt,
            breakdown = breakdown,
        )

        // Resolve available variants for the currently selected model.
        // If selected model is no longer visible (filtered out), fall back to first visible model.
        var currentModel = if (effectiveProviderId != null && effectiveModelId != null) {
            providers.find { it.id == effectiveProviderId }
                ?.models?.get(effectiveModelId)
        } else null
        if (currentModel == null) {
            val firstProvider = providers.firstOrNull()
            val firstModel = firstProvider?.models?.values?.firstOrNull()
            if (firstProvider != null && firstModel != null) {
                effectiveProviderId = firstProvider.id
                effectiveModelId = firstModel.id
                currentModel = firstModel
            }
        }
        // Match the Web UI by preserving the variant order supplied by the server.
        val availableVariants = currentModel?.variants?.keys?.toList() ?: emptyList()

        // 上下文预算：估算当前会话 token 用量与有效上下文窗口（模型元数据 > 每服务器覆盖 > 默认 32k）。
        val estimatedContextTokens = estimateContextTokens(chatMessages)
        val effectiveContextWindow = currentModel?.limit?.context?.takeIf { it > 0 }
            ?: serverContextLimitOverride.takeIf { it > 0 }
            ?: DEFAULT_CONTEXT_WINDOW

        ChatUiState(
            sessionTitle = session?.title ?: "Chat",
            sessionLoaded = session != null,
            parentSessionId = session?.parentId,
            childSessions = childSessions,
            serverName = serverName,
            messages = chatMessages,
            revert = revertState,
            sessionStatus = statuses[sessionId] ?: SessionStatus.Idle,
            pendingInteractions = pendingInteractions.filter { it.sessionId in interactionSessionIds },
            isLoading = loading,
            error = error,
            isSending = sending,
            providers = providers,
            hasServerModelCatalog = allProviders.any { it.models.isNotEmpty() },
            defaultModels = defaultModels,
            selectedProviderId = effectiveProviderId,
            selectedModelId = effectiveModelId,
            totalCost = totalCost,
            totalInputTokens = totalInputTokens,
            totalOutputTokens = totalOutputTokens,
            agents = agents.filter { it.mode != "subagent" && !it.hidden },
            selectedAgent = effectiveAgent,
            variantNames = availableVariants,
            selectedVariant = if (selectedVariant != null && selectedVariant in availableVariants) selectedVariant else null,
            commands = commands,
            hasOlderMessages = hasOlderMessages,
            isLoadingOlder = isLoadingOlder,
            shareUrl = session?.share?.url,
            contextWindow = currentModel?.limit?.context ?: 0,
            lastContextTokens = lastContextTokens,
            estimatedContextTokens = estimatedContextTokens,
            effectiveContextWindow = effectiveContextWindow,
            contextUsage = contextUsage,
            suggestions = suggestions,
            suggestionsSource = suggestionsSource,
            isGeneratingSuggestions = isGeneratingSuggestions,
            suggestionsError = suggestionsError,
            suggestionsStreamText = suggestionsStreamText,
            modelNeedsDownload = modelNeedsDownload,
            modelDownloading = modelDownloading,
            modelDownloadProgress = modelDownloadProgress,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ChatUiState()
    )

    init {
        eventReducer.confirmSession(sessionId)
        _pendingPrompts.value = pendingPromptRepository.getForSession(sessionId)
        // 探测后端流式识别引擎是否可用：端侧 MNN 模型不可用的设备靠它提供语音输入。
        // 结果按 serverId 缓存 5 分钟，避免每次打开会话都发探测请求（后端不可达时尤其拖慢进入）。
        viewModelScope.launch {
            _backendAsrAvailable.value = cachedBackendAsrAvailable()
        }
        // 高频 parts/messages 的节流采样：每 ~STREAM_THROTTLE_MS 把最新值同步到节流状态，
        // 供 uiState 的 combine 使用，降低流式输出时 combine 全量重算 + 全量 recompose 的频率。
        viewModelScope.launch {
            while (isActive) {
                val m = eventReducer.messages.value
                if (m != throttledMessages.value) throttledMessages.value = m
                val p = eventReducer.parts.value
                if (p != throttledParts.value) throttledParts.value = p
                delay(STREAM_THROTTLE_MS)
            }
        }
        // 通知「重试」按钮触发的自动重试：等消息加载完成后，重新生成最后一条 assistant 消息。
        if (retryOnOpen) {
            viewModelScope.launch {
                delay(1500)
                val lastAssistantId = eventReducer.messages.value[sessionId]
                    .orEmpty()
                    .lastOrNull { it is Message.Assistant }?.id
                if (lastAssistantId != null) {
                    regenerateMessage(lastAssistantId)
                }
            }
        }
        viewModelScope.launch {
            eventReducer.messages.collect { messagesBySession ->
                val confirmedIds = messagesBySession[sessionId].orEmpty().mapTo(mutableSetOf()) { it.id }
                val confirmedPending = _pendingPrompts.value.filter {
                    it.messageId in confirmedIds
                }
                if (confirmedPending.isNotEmpty()) {
                    val reconciledIds = confirmedPending.mapTo(mutableSetOf()) { it.messageId }
                    confirmedPending.forEach { pendingPromptRepository.remove(it.messageId) }
                    _pendingPrompts.value = _pendingPrompts.value.filterNot { it.messageId in reconciledIds }
                }
            }
        }

        // Restore draft from disk（异步读盘，避免阻塞主线程导致进入聊天卡顿）
        viewModelScope.launch(Dispatchers.IO) {
            val draft = draftRepository.getDraft(sessionId)
            if (draft != null) {
                _draftText.value = draft.text
                _draftAttachmentUris.value = draft.imageUris
                if (draft.confirmedFilePaths.isNotEmpty()) {
                    _confirmedFilePaths.value = draft.confirmedFilePaths.toSet()
                }
                if (!draft.selectedAgent.isNullOrBlank()) {
                    _selectedAgent.value = draft.selectedAgent to true
                }
                if (!draft.selectedVariant.isNullOrBlank()) {
                    _selectedVariant.value = draft.selectedVariant
                }
            }
        }

        viewModelScope.launch {
            settingsRepository.hiddenModels(serverId).collect { hidden ->
                _hiddenModels.value = hidden
                applyProviderFilter()
            }
        }

        viewModelScope.launch {
            settingsRepository.contextLimit(serverId).collect { limit ->
                _serverContextLimitOverride.value = limit
            }
        }

        viewModelScope.launch {
            settingsRepository.terminalFontSize.collect { size ->
                terminalWorkspace.setDefaultFontSize(size)
            }
        }

        // Load initial message count from settings, then load data
        viewModelScope.launch {
            currentMessageLimit = settingsRepository.initialMessageCount.first()
            loadSession()
            loadMessages()
            loadPendingRequests()
        }
        viewModelScope.launch {
            sessionLoaded.await()
            // Poll adaptively: while idle (no running tool), back off to avoid needless
            // network + recomposition churn in the background; stay tight while busy so a
            // finished task is noticed promptly.
            while (true) {
                val busy = eventReducer.sessionStatuses.value[sessionId] is SessionStatus.Busy ||
                    eventReducer.messages.value[sessionId].orEmpty().any { message ->
                        eventReducer.parts.value[message.id].orEmpty().any {
                            it is Part.Tool && it.state is ToolState.Running
                        }
                    }
                delay(if (busy) 3_000L else 15_000L)
                reconcileActiveStatus()
            }
        }
        loadProviders()
        loadAgents()
        loadCommands()

    }

    /** 读取（或探测并缓存）后端 ASR 引擎是否可用。 */
    private suspend fun cachedBackendAsrAvailable(): Boolean {
        val now = System.currentTimeMillis()
        backendAsrAvailableCache[serverId]?.let { (available, ts) ->
            if (now - ts < BACKEND_ASR_CACHE_TTL_MS) return available
        }
        val available = backendAsrEndpoint()?.let { endpoint ->
            serverAsrApi.isAvailable(endpoint.first, endpoint.second)
        } ?: false
        backendAsrAvailableCache[serverId] = available to now
        return available
    }

    /** 上次通过 REST 拉取最新消息兜底的时间戳（节流，避免 SSE 假死时过于频繁地拉取）。 */
    private var lastMessagePollAt = 0L

    private suspend fun reconcileActiveStatus() {
        val localStatus = eventReducer.sessionStatuses.value[sessionId]
        val hasRunningTool = eventReducer.messages.value[sessionId].orEmpty().any { message ->
            eventReducer.parts.value[message.id].orEmpty().any { part ->
                part is Part.Tool && part.state is ToolState.Running
            }
        }
        val wasBusy = localStatus is SessionStatus.Busy || hasRunningTool

        try {
            // 即使 localStatus 是 Idle 也探测远程状态：SSE 假死时会收不到 session.status 事件，
            // localStatus 停留在假死前的 Idle；若不探测将永远无法发现「会话已经变 busy」。
            val remoteStatus = api.listSessionStatuses(conn, sessionDirectory)[sessionId] ?: SessionStatus.Idle
            val now = System.currentTimeMillis()
            // busy 期间每 BUSY_MESSAGE_POLL_MS 拉一次最新消息兜底；busy→idle 转场时再拉一次，
            // 捕获「服务端已输出完、但 App 因 SSE 假死没收到」的最终结果。
            val shouldPollMessages =
                (remoteStatus is SessionStatus.Busy && now - lastMessagePollAt >= BUSY_MESSAGE_POLL_MS) ||
                    (remoteStatus is SessionStatus.Idle && wasBusy)
            if (shouldPollMessages) {
                val messages = api.listMessages(conn, sessionId, limit = 50, directory = sessionDirectory)
                eventReducer.mergeMessages(sessionId, messages, serverId)
                lastMessagePollAt = now
            }
            eventReducer.updateSessionStatus(sessionId, remoteStatus)
        } catch (e: Exception) {
            e.rethrowCancellation()
            if (BuildConfig.DEBUG) Log.d(TAG, "Failed to reconcile active status for $sessionId: ${e.message}")
        }
    }

    /** Load the session info to get its directory for correct project context. */
    private suspend fun loadSession() {
        try {
            val session = api.getSession(conn, sessionId, directory = sessionDirectory)
            sessionPromptable = sessionAcceptsPrompts(session)
            if (session.directory.isNotBlank()) {
                sessionDirectory = session.directory
                if (BuildConfig.DEBUG) Log.d(TAG, "Session directory resolved")
            }
            val sessions = mutableListOf(session)
            val queue = ArrayDeque<String>().apply { add(session.id) }
            val visited = mutableSetOf(session.id)
            while (queue.isNotEmpty() && visited.size < MAX_CHILD_SESSIONS) {
                val parentId = queue.removeFirst()
                val children = try {
                    api.listChildSessions(conn, parentId, directory = sessionDirectory)
                        .filter { visited.add(it.id) }
                } catch (e: Exception) {
                    e.rethrowCancellation()
                    Log.e(TAG, "Failed to load children for session $parentId", e)
                    emptyList()
                }
                sessions += children
                children.forEach { queue.addLast(it.id) }
            }
            // 用 upsert 逐个合并当前会话及其子会话，避免用 setSessions（权威替换）
            // 传入局部列表会把列表里其它项目的根会话全部清掉，导致返回会话列表时被清空。
            sessions.forEach { eventReducer.upsertSession(serverId, it) }
            refreshGitRepositoryState()
        } catch (e: Exception) {
            e.rethrowCancellation()
            Log.e(TAG, "Failed to load session info", e)
        } finally {
            sessionLoaded.complete(Unit)
        }
    }

    fun loadMessages() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val initialLimit = fastInitialMessageLimit(currentMessageLimit)
                val firstPage = api.listMessagesPage(
                    conn,
                    sessionId,
                    limit = initialLimit,
                    directory = sessionDirectory,
                )
                val messages = firstPage.messages.toMutableList()
                val revertMessageId = eventReducer.sessions.value.find { it.id == sessionId }?.revert?.messageId
                var nextCursor = firstPage.nextCursor
                val knownIds = messages.mapTo(mutableSetOf()) { it.info.id }
                var recoveryPages = 0

                olderMessagesCursor = nextCursor
                eventReducer.mergeMessages(sessionId, messages, serverId)
                reconcilePendingPrompts(
                    authoritative = messages,
                    minimumAgeMs = 10_000L,
                )
                _hasOlderMessages.value = nextCursor != null
                // 先同步节流状态再结束 loading，避免 combine 在「messages 已就绪但节流态未采样」
                // 的窗口内重算出 messages 空 + isLoading=false 的空会话界面。
                flushThrottledState()
                _isLoading.value = false
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "Displayed initial ${messages.size} messages for session $sessionId " +
                            "(requested=$initialLimit, hasOlder=${_hasOlderMessages.value})",
                    )
                }

                while (nextCursor != null) {
                    val needsConfiguredHistory = messages.size < currentMessageLimit
                    val needsRevertHistory = !needsConfiguredHistory &&
                        recoveryPages < MAX_REVERT_RECOVERY_PAGES &&
                        needsOlderHistoryForRevert(knownIds, revertMessageId)
                    if (!needsConfiguredHistory && !needsRevertHistory) break

                    val requestedCursor = nextCursor
                    val pageLimit = if (needsConfiguredHistory) {
                        backgroundMessageLimit(messages.size, currentMessageLimit)
                    } else {
                        currentMessageLimit
                    }
                    _isLoadingOlder.value = true
                    val olderPage = try {
                        api.listMessagesPage(
                            conn,
                            sessionId,
                            limit = pageLimit,
                            before = requestedCursor,
                            directory = sessionDirectory,
                        )
                    } catch (e: Exception) {
                        e.rethrowCancellation()
                        Log.e(TAG, "Failed to preload older messages", e)
                        break
                    }
                    messages += olderPage.messages
                    knownIds += olderPage.messages.map { it.info.id }
                    eventReducer.mergeMessages(sessionId, olderPage.messages, serverId)
                    if (needsRevertHistory) recoveryPages++
                    nextCursor = olderPage.nextCursor?.takeUnless { it == requestedCursor }
                    olderMessagesCursor = nextCursor
                    _hasOlderMessages.value = nextCursor != null
                    if (olderPage.messages.isEmpty()) break
                }
                olderMessagesCursor = nextCursor
                _hasOlderMessages.value = nextCursor != null
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "Finished background history load: ${messages.size} messages for session $sessionId " +
                            "(limit=$currentMessageLimit, revertRecoveryPages=$recoveryPages, hasOlder=${_hasOlderMessages.value})",
                    )
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to load messages", e)
                // On OOM or other memory errors, retry with a smaller limit
                if (e is OutOfMemoryError || (e.cause is OutOfMemoryError)) {
                    Log.w(TAG, "OOM loading messages, retrying with smaller limit")
                    currentMessageLimit = (currentMessageLimit / 2).coerceAtLeast(10)
                    try {
                        val page = api.listMessagesPage(
                            conn,
                            sessionId,
                            limit = currentMessageLimit,
                            directory = sessionDirectory,
                        )
                        val messages = page.messages
                        olderMessagesCursor = page.nextCursor
                        eventReducer.mergeMessages(sessionId, messages, serverId)
                        _hasOlderMessages.value = page.nextCursor != null
                        if (BuildConfig.DEBUG) Log.d(TAG, "Retry succeeded: loaded ${messages.size} messages (limit=$currentMessageLimit)")
                    } catch (retryEx: Exception) {
                        retryEx.rethrowCancellation()
                        Log.e(TAG, "Retry also failed", retryEx)
                        _error.value = retryEx.friendlyErrorMessage(context).ifBlank { "Failed to load messages" }
                    }
                } else {
                    _error.value = e.friendlyErrorMessage(context).ifBlank { "Failed to load messages" }
                }
            } finally {
                flushThrottledState()
                _isLoading.value = false
                _isLoadingOlder.value = false
            }
        }
    }

    fun reloadSession() {
        _isLoading.value = true
        _error.value = null
        olderMessagesCursor = null
        _hasOlderMessages.value = false
        eventReducer.clearSessionHistory(sessionId)

        viewModelScope.launch {
            currentMessageLimit = settingsRepository.initialMessageCount.first()
            loadSession()
            loadPendingRequests()
            loadMessages()
        }
    }

    /**
     * Load older messages by doubling the limit and reloading.
     * The server returns the N most recent messages, so we simply request more.
     */
    fun loadOlderMessages() {
        viewModelScope.launch {
            var nextCursor: String? = olderMessagesCursor ?: return@launch
            _isLoadingOlder.value = true
            try {
                val messages = mutableListOf<MessageWithParts>()
                val knownIds = eventReducer.messages.value[sessionId].orEmpty().mapTo(mutableSetOf()) { it.id }
                val revertMessageId = eventReducer.sessions.value.find { it.id == sessionId }?.revert?.messageId
                var recoveryPages = 0
                do {
                    val requestedCursor = nextCursor
                    val page = api.listMessagesPage(
                        conn,
                        sessionId,
                        limit = currentMessageLimit,
                        before = requestedCursor,
                        directory = sessionDirectory,
                    )
                    messages += page.messages
                    knownIds += page.messages.map { it.info.id }
                    recoveryPages++
                    nextCursor = page.nextCursor?.takeUnless { it == requestedCursor }
                    if (page.messages.isEmpty()) break
                } while (
                    nextCursor != null &&
                    recoveryPages < MAX_REVERT_RECOVERY_PAGES &&
                    needsOlderHistoryForRevert(knownIds, revertMessageId)
                )
                eventReducer.mergeMessages(sessionId, messages, serverId)
                olderMessagesCursor = nextCursor
                _hasOlderMessages.value = nextCursor != null
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "Loaded older: ${messages.size} messages " +
                            "(revertRecoveryPages=$recoveryPages, hasOlder=${_hasOlderMessages.value})",
                    )
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to load older messages", e)
            } finally {
                flushThrottledState()
                _isLoadingOlder.value = false
            }
        }
    }

    /**
     * Load pending questions from the server REST API.
     * Converts QuestionRequest DTOs to SseEvent.QuestionAsked domain objects.
     * Must be called after loadSession() so sessionDirectory is set.
     */
    private suspend fun loadPendingRequests() {
        try {
            val revision = eventReducer.pendingSnapshotRevision()
            val allPermissions = api.listPendingPermissions(conn, directory = sessionDirectory)
            val allQuestions = api.listPendingQuestions(conn, directory = sessionDirectory)
            val interactionSessionIds = descendantSessionIds(eventReducer.sessions.value, sessionId)
            val sessionPermissions = allPermissions
                .filter { it.sessionId in interactionSessionIds }
                .map { req ->
                    SseEvent.PermissionAsked(
                        id = req.id,
                        sessionId = req.sessionId,
                        permission = req.permission,
                        patterns = req.patterns,
                        always = req.always,
                        metadata = req.metadata,
                        tool = req.tool,
                    )
                }
            val sessionQuestions = allQuestions
                .filter { it.sessionId in interactionSessionIds }
                .map { req ->
                    SseEvent.QuestionAsked(
                        id = req.id,
                        sessionId = req.sessionId,
                        questions = req.questions.map { q ->
                            SseEvent.QuestionAsked.Question(
                                header = q.header,
                                question = q.question,
                                multiple = q.multiple,
                                custom = q.custom,
                                options = q.options.map { o ->
                                    SseEvent.QuestionAsked.Option(
                                        label = o.label,
                                        description = o.description
                                    )
                                }
                            )
                        },
                        tool = req.tool
                    )
                }
            val applied = eventReducer.replacePendingRequestsForSessions(
                sessionIds = interactionSessionIds,
                permissions = sessionPermissions,
                questions = sessionQuestions,
                expectedRevision = revision,
            )
            Log.i(
                TAG,
                "Pending requests loaded: session=$sessionId descendants=${interactionSessionIds.size} " +
                    "permissions=${sessionPermissions.size}/${allPermissions.size} " +
                    "questions=${sessionQuestions.size}/${allQuestions.size} applied=$applied",
            )
        } catch (e: Exception) {
            e.rethrowCancellation()
            Log.e(TAG, "Failed to load pending requests: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    // Removed initModelFromMessages as it's handled reactively

    private fun loadProviders() {
        viewModelScope.launch {
            try {
                val response = api.getProviders(conn)
                _allProviders.value = response.providers
                applyProviderFilter()
                _defaultModels.value = response.default
                if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${response.providers.size} providers, defaults: ${response.default}")
                // No need to set default here, combine block handles fallback
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to load providers", e)
            }
        }
    }

    private fun applyProviderFilter() {
        val hidden = _hiddenModels.value
        val filtered = _allProviders.value
            .map { provider ->
                provider.copy(
                    models = provider.models.filterKeys { modelId ->
                        "${provider.id}:$modelId" !in hidden
                    }
                )
            }
            .filter { it.models.isNotEmpty() }
        _providers.value = filtered
    }

    private fun loadAgents() {
        viewModelScope.launch {
            try {
                val agents = api.listAgents(conn)
                _agents.value = agents
                if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${agents.size} agents: ${agents.map { it.name }}")
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to load agents", e)
            }
        }
    }

    fun selectAgent(name: String) {
        _selectedAgent.value = name to true
    }

    private fun loadCommands() {
        viewModelScope.launch {
            try {
                val commands = api.listCommands(conn)
                _commands.value = commands
                if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${commands.size} commands: ${commands.map { it.name }}")
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to load commands", e)
            }
        }
    }

    fun selectVariant(name: String?) {
        _selectedVariant.value = name?.takeIf { it in uiState.value.variantNames }
    }

    fun selectModel(providerId: String, modelId: String) {
        _selectedProviderId.value = providerId
        _selectedModelId.value = modelId
        _selectedVariant.value = null
        isModelExplicitlySelected = true
    }

    // ============ @ File Mention Search ============

    /** File search results for @-autocomplete */
    private val _fileSearchResults = MutableStateFlow<List<String>>(emptyList())
    val fileSearchResults: StateFlow<List<String>> = _fileSearchResults

    /** Debounce job for file search */
    private var fileSearchJob: Job? = null

    /** Search files and directories for @-mention autocomplete. Debounced by 200ms. */
    fun searchFilesForMention(query: String) {
        fileSearchJob?.cancel()
        if (query.isEmpty()) {
            // Show recent/top files immediately with no debounce
            fileSearchJob = viewModelScope.launch {
                try {
                    val results = api.findFiles(
                        conn = conn,
                        query = "",
                        dirs = "true",
                        directory = sessionDirectory,
                        limit = 15
                    )
                    _fileSearchResults.value = results
                } catch (e: Exception) {
                    e.rethrowCancellation()
                    Log.e(TAG, "File search failed", e)
                    _fileSearchResults.value = emptyList()
                }
            }
            return
        }
        fileSearchJob = viewModelScope.launch {
            delay(150) // debounce
            try {
                val results = api.findFiles(
                    conn = conn,
                    query = query,
                    dirs = "true",
                    directory = sessionDirectory,
                    limit = 15
                )
                _fileSearchResults.value = results
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "File search failed", e)
                _fileSearchResults.value = emptyList()
            }
        }
    }

    /** Add a confirmed file path (user selected it from the popup) */
    fun confirmFilePath(path: String) {
        _confirmedFilePaths.value = _confirmedFilePaths.value + path
    }

    /** Remove a confirmed file path */
    fun removeFilePath(path: String) {
        _confirmedFilePaths.value = _confirmedFilePaths.value - path
    }

    /** Clear file search results (e.g. when popup is closed) */
    fun clearFileSearch() {
        fileSearchJob?.cancel()
        _fileSearchResults.value = emptyList()
    }

    /** Clear confirmed file paths (e.g. after sending a message) */
    fun clearConfirmedPaths() {
        _confirmedFilePaths.value = emptySet()
    }

    // ============ Draft Management ============

    /** Update the draft text (called on every keystroke). */
    fun updateDraftText(text: String) {
        _draftText.value = text
    }

    /** Add an attachment URI to the draft. */
    fun addDraftAttachment(uri: String) {
        _draftAttachmentUris.value = _draftAttachmentUris.value + uri
    }

    /** Remove an attachment URI from the draft by index. */
    fun removeDraftAttachment(index: Int) {
        val current = _draftAttachmentUris.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _draftAttachmentUris.value = current
        }
    }

    /** Clear all draft state (called after sending a message). */
    fun clearDraft() {
        _draftText.value = ""
        _draftAttachmentUris.value = emptyList()
        draftRepository.clearDraft(sessionId)
    }

    /** Persist current draft to disk. */
    private fun saveDraft() {
        val agentPair = _selectedAgent.value
        val draft = org.hiylo.starburst.data.repository.Draft(
            text = _draftText.value,
            imageUris = _draftAttachmentUris.value,
            confirmedFilePaths = _confirmedFilePaths.value.toList(),
            selectedAgent = agentPair.first.takeIf { agentPair.second },
            selectedVariant = _selectedVariant.value
        )
        draftRepository.saveDraft(sessionId, draft)
    }

    override fun onCleared() {
        closeTerminalSession()
        cancelListening()
        super.onCleared()
        saveDraft()
        // 释放本会话在 EventReducer 中的消息/parts 缓存，避免所有打开过的会话常驻内存
        // （老会话累积会无界吃内存，让 app 内存水涨船高）。重新进入会话时会从服务器重新拉取。
        eventReducer.clearSessionHistory(sessionId)
    }

    // ============ Voice input (ASR) ============

    /** 开始语音识别（按住说话）。调用方需已确保 RECORD_AUDIO 权限。 */
    fun startListening() {
        if (_isListening.value) return
        _isListening.value = true
        _voiceLevel.value = 0f
        viewModelScope.launch {
            val session = createAsrSession()
            if (session == null) {
                _isListening.value = false
                _voiceLevel.value = 0f
                _speechError.value = R.string.chat_voice_input_error
                return@launch
            }
            asrRecorder = session
            val ok = session.start(object : AsrSession.Listener {
                override fun onStart() {
                    _isListening.value = true
                }

                override fun onPartialResult(text: String) {
                    _partialRecognizedText.tryEmit(text)
                }

                override fun onError(message: String) {
                    _speechError.value = R.string.chat_voice_input_error
                }

                override fun onStopped() {
                    _isListening.value = false
                    _voiceLevel.value = 0f
                    if (asrRecorder === session) asrRecorder = null
                }
            })
            if (!ok) {
                _isListening.value = false
                _voiceLevel.value = 0f
                if (asrRecorder === session) asrRecorder = null
            }
        }
    }

    /**
     * 创建一次录音识别会话：优先后端代理的流式引擎；后端未配置或不可用时，
     * 回退到端侧 MNN 模型（模型未下载、ABI 不匹配则返回 null）。
     */
    private suspend fun createAsrSession(): AsrSession? {
        val endpoint = backendAsrEndpoint()
        if (endpoint != null && serverAsrApi.isAvailable(endpoint.first, endpoint.second)) {
            return ServerAsrRecorder(serverAsrApi, endpoint.first, endpoint.second)
        }
        if (MnnAsr.ensureLoaded(context)) return MnnAsrRecorder(context)
        return null
    }

    /** 解析当前 server 对应的后端地址与 token，用于服务端语音识别。 */
    private suspend fun backendAsrEndpoint(): Pair<String, String>? {
        val servers = serverRepository.servers.first()
        val backend = servers.firstOrNull { it.id == serverId }
        val host = runCatching { java.net.URL(serverUrl).host }.getOrNull()
            ?: serverUrl.substringAfter("://").substringBefore(":")
        if (host.isBlank()) return null
        val url = (backend?.backendResolvedUrl ?: "http://$host:18880").trimEnd('/')
        if (url.isBlank()) return null
        return url to (backend?.backendResolvedToken ?: "ocb_default")
    }

    /** 停止语音识别（松手上屏）。 */
    fun stopListening() {
        val recorder = asrRecorder
        asrRecorder = null
        _isListening.value = false
        _voiceLevel.value = 0f
        if (recorder != null) {
            viewModelScope.launch { recorder.stop() }
        }
    }

    /** 取消语音识别（上滑取消），不产生结果。 */
    fun cancelListening() {
        val recorder = asrRecorder
        asrRecorder = null
        _isListening.value = false
        _voiceLevel.value = 0f
        if (recorder != null) {
            viewModelScope.launch { recorder.cancel() }
        }
    }

    /** 消费并清除当前的 ASR 错误提示。 */
    fun consumeSpeechError() {
        _speechError.value = null
    }

    /** Get the session directory for building file:// URLs */
    fun getSessionDirectory(): String? = sessionDirectory

    /**
     * 刷新当前项目是否为 Git 仓库的状态。
     * 依据 [Project.vcs] 是否为 "git" 判定，优先按会话目录匹配 [OpenCodeApi.listProjects] 结果，
     * 未匹配时回退到 [OpenCodeApi.getCurrentProject]。
     */
    private suspend fun refreshGitRepositoryState() {
        val directory = sessionDirectory
        if (directory.isNullOrBlank()) {
            _isGitRepository.value = false
            return
        }
        try {
            val projects = api.listProjects(conn)
            val normalized = directory.trimEnd('/')
            val project = projects.firstOrNull {
                it.worktree.trimEnd('/') == normalized ||
                    it.path.trimEnd('/') == normalized ||
                    it.directory?.trimEnd('/') == normalized
            }
            val vcs = project?.vcs
                ?: runCatching { api.getCurrentProject(conn).vcs }.getOrNull()
            _isGitRepository.value = vcs == "git"
        } catch (e: Exception) {
            e.rethrowCancellation()
            if (BuildConfig.DEBUG) Log.d(TAG, "Failed to resolve git repository state: ${e.message}")
        }
    }

    fun sendMessage(text: String, attachments: List<PromptPart> = emptyList()): Boolean {
        if (text.isBlank() && attachments.isEmpty()) return false
        val parts = mutableListOf<PromptPart>()
        if (text.isNotBlank()) {
            parts.add(PromptPart(type = "text", text = text))
        }
        parts.addAll(attachments)
        return sendParts(parts)
    }

    /** Send pre-built prompt parts (used when @-file mentions need structured parts). */
    fun sendMessage(promptParts: List<PromptPart>, attachments: List<PromptPart>): Boolean {
        val parts = promptParts + attachments
        if (parts.isEmpty()) return false
        return sendParts(parts)
    }

    private fun sendParts(parts: List<PromptPart>): Boolean {
        if (!sessionPromptable) return false
        if (_isSending.value) return false
        _isSending.value = true
        // Previous suggestions are stale once the user sends a new message.
        clearSuggestions()

        val model = if (_selectedProviderId.value != null && _selectedModelId.value != null) {
            ModelSelection(_selectedProviderId.value!!, _selectedModelId.value!!)
        } else {
            null
        }
        // 仅在会话首条用户消息注入自定义系统提示词（后续消息沿用服务端已持久化的 system）。
        val injectSystemPrompt = uiState.value.messages.none { it.isUser }
        val messageId = MessageIdGenerator.next()
        val draftSnapshot = Draft(
            text = _draftText.value,
            imageUris = _draftAttachmentUris.value,
            confirmedFilePaths = _confirmedFilePaths.value.toList(),
            selectedAgent = _selectedAgent.value.first.takeIf { _selectedAgent.value.second },
            selectedVariant = _selectedVariant.value,
        )
        val pending = PendingPromptRecord(
            messageId = messageId,
            sessionId = sessionId,
            parts = parts,
            model = model,
            agent = uiState.value.selectedAgent,
            variant = _selectedVariant.value,
            directory = sessionDirectory,
            createdAt = System.currentTimeMillis(),
        )
        pendingPromptRepository.save(pending)
        _pendingPrompts.value = _pendingPrompts.value + pending

        viewModelScope.launch {
            try {
                val systemPrompt = if (injectSystemPrompt) {
                    settingsRepository.systemPrompt(serverId).first().takeIf { it.isNotBlank() }
                } else {
                    null
                }
                api.promptAsync(
                    conn = conn,
                    sessionId = sessionId,
                    messageId = messageId,
                    parts = parts,
                    model = model,
                    agent = uiState.value.selectedAgent,
                    variant = _selectedVariant.value,
                    directory = sessionDirectory,
                    system = systemPrompt
                )
                eventReducer.updateSessionStatus(sessionId, SessionStatus.Busy)
                // 新消息覆盖之前的待决提问：服务端驳回 + 本地移除，避免重进会话又出现。
                dismissPendingQuestions()
                if (BuildConfig.DEBUG) Log.d(TAG, "Sent prompt to session $sessionId (${parts.size} parts)")
                reconcilePendingMessage(messageId)
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to send message", e)
                _error.value = e.friendlyErrorMessage(context).ifBlank { "Failed to send message" }
                val definiteHttpFailure = e is RuntimeException && e.message?.startsWith("prompt_async failed:") == true
                if (definiteHttpFailure) {
                    eventReducer.updateSessionStatus(sessionId, SessionStatus.Idle)
                    pendingPromptRepository.remove(messageId)
                    _pendingPrompts.value = _pendingPrompts.value.filterNot { it.messageId == messageId }
                    delay(50)
                    restoreDraftAfterFailedSend(draftSnapshot)
                } else {
                    reconcilePendingMessage(messageId)
                }
            } finally {
                _isSending.value = false
            }
        }
        return true
    }

    private suspend fun reconcilePendingMessage(messageId: String) {
        var latestMessages = emptyList<MessageWithParts>()
        for (delayMs in listOf(150L, 400L, 1_000L, 2_000L, 4_000L)) {
            delay(delayMs)
            if (_pendingPrompts.value.none { it.messageId == messageId }) return
            try {
                val messages = api.listMessages(conn, sessionId, limit = 20)
                latestMessages = messages
                eventReducer.mergeMessages(sessionId, messages, serverId)
                if (messages.any { it.info.id == messageId }) return
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to reconcile pending message $messageId", e)
            }
        }
        if (latestMessages.isNotEmpty()) {
            reconcilePendingPrompts(authoritative = latestMessages, minimumAgeMs = 0L)
        }
    }

    private fun reconcilePendingPrompts(authoritative: List<MessageWithParts>, minimumAgeMs: Long) {
        val staleIds = missingPendingPromptIds(
            pending = _pendingPrompts.value,
            authoritative = authoritative,
            now = System.currentTimeMillis(),
            minimumAgeMs = minimumAgeMs,
        )
        if (staleIds.isEmpty()) return
        val stale = _pendingPrompts.value.filter { it.messageId in staleIds }
        stale.forEach { pendingPromptRepository.remove(it.messageId) }
        _pendingPrompts.value = _pendingPrompts.value.filterNot { it.messageId in staleIds }
        stale.firstOrNull()?.let(::restoreMissingPendingPrompt)
        Log.w(TAG, "Removed ${stale.size} unconfirmed pending prompt(s) for session $sessionId")
    }

    private fun restoreMissingPendingPrompt(pending: PendingPromptRecord) {
        if (_draftText.value.isNotBlank() || _draftAttachmentUris.value.isNotEmpty()) return
        val text = pending.parts
            .filter { it.type == "text" }
            .mapNotNull(PromptPart::text)
            .filter(String::isNotBlank)
            .joinToString("\n")
        val imageUris = pending.parts
            .filter { it.type == "file" }
            .mapNotNull(PromptPart::url)
        val draft = Draft(
            text = text,
            imageUris = imageUris,
            selectedAgent = pending.agent,
            selectedVariant = pending.variant,
        )
        _draftText.value = draft.text
        _draftAttachmentUris.value = draft.imageUris
        draftRepository.saveDraft(sessionId, draft)
        _revertedDraftEvent.tryEmit(RevertedDraftPayload(draft.text, draft.imageUris))
    }

    private fun restoreDraftAfterFailedSend(snapshot: Draft) {
        if (_draftText.value.isNotBlank() || _draftAttachmentUris.value.isNotEmpty()) return
        _draftText.value = snapshot.text
        _draftAttachmentUris.value = snapshot.imageUris
        _confirmedFilePaths.value = snapshot.confirmedFilePaths.toSet()
        draftRepository.saveDraft(sessionId, snapshot)
        _revertedDraftEvent.tryEmit(RevertedDraftPayload(snapshot.text, snapshot.imageUris))
    }

    /**
     * Reply to a permission request.
     * @param requestId The permission request ID
     * @param reply One of: "once", "always", "reject"
     */
    fun replyToPermission(
        requestSessionId: String,
        requestId: String,
        reply: String,
        onResult: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            try {
                val success = api.replyToPermission(
                    conn = conn,
                    requestId = requestId,
                    reply = reply,
                    directory = requestDirectory(requestSessionId),
                )
                if (success) eventReducer.removePermission(requestSessionId, requestId)
                onResult(success)
                if (BuildConfig.DEBUG) Log.d(TAG, "Replied to permission $requestId with $reply: $success")
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to reply to permission", e)
                onResult(false)
            }
        }
    }

    fun abortSession() {
        viewModelScope.launch {
            try {
                api.abortSession(conn, sessionId, directory = sessionDirectory)
                if (BuildConfig.DEBUG) Log.d(TAG, "Aborted session $sessionId")
                // Optimistically update session status to Idle so UI reflects change immediately
                eventReducer.updateSessionStatus(sessionId, SessionStatus.Idle)
                // 中止后，之前的待决提问已不再等待答复：服务端驳回 + 本地移除。
                dismissPendingQuestions()
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to abort session", e)
            }
        }
    }

    /**
     * 继续处理会话：当 agent 因错误 / 中止而停止时，发送一条继续指令让 agent 接着处理。
     *
     * @param onResult 发送是否成功（会话可接收 prompt 且未在发送中）
     */
    fun continueSession(onResult: (Boolean) -> Unit = {}) {
        val ok = sendMessage(context.getString(R.string.chat_continue_task_prompt))
        onResult(ok)
    }

    /**
     * 发送新消息覆盖提问、或中止会话后，把本会话及其子会话的待决提问在服务端驳回并移除本地卡片，
     * 避免提问只从当前界面消失、重进会话又从 /question 拉回来。
     */
    private suspend fun dismissPendingQuestions() {
        val interactionIds = descendantSessionIds(eventReducer.sessions.value, sessionId)
        val pending = eventReducer.pendingInteractions.value
            .filterIsInstance<PendingInteraction.Question>()
            .filter { it.sessionId == sessionId || it.sessionId in interactionIds }
        if (pending.isEmpty()) return
        for (question in pending) {
            runCatching {
                api.rejectQuestion(
                    conn = conn,
                    requestId = question.id,
                    directory = requestDirectory(question.sessionId),
                )
            }.onFailure { e ->
                if (BuildConfig.DEBUG) Log.d(TAG, "Failed to reject question ${question.id}: ${e.message}")
            }
            eventReducer.removeQuestion(question.sessionId, question.id)
        }
    }

    /**
     * Reply to a question request.
     * @param requestId The question request ID
     * @param answers Answers for each question (list of selected labels per question)
     */
    fun replyToQuestion(
        requestSessionId: String,
        requestId: String,
        answers: List<List<String>>,
        onResult: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            try {
                val success = api.replyToQuestion(
                    conn = conn,
                    requestId = requestId,
                    answers = answers,
                    directory = requestDirectory(requestSessionId),
                )
                if (success) {
                    // Optimistically remove the question card — SSE event may arrive late or not at all
                    eventReducer.removeQuestion(requestSessionId, requestId)
                }
                onResult(success)
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to reply to question $requestId: ${e.javaClass.simpleName}: ${e.message}", e)
                onResult(false)
            }
        }
    }

    /**
     * Reject a question request.
     */
    fun rejectQuestion(
        requestSessionId: String,
        requestId: String,
        onResult: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            try {
                val success = api.rejectQuestion(
                    conn = conn,
                    requestId = requestId,
                    directory = requestDirectory(requestSessionId),
                )
                if (success) {
                    // Optimistically remove the question card
                    eventReducer.removeQuestion(requestSessionId, requestId)
                }
                onResult(success)
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to reject question $requestId: ${e.javaClass.simpleName}: ${e.message}", e)
                onResult(false)
            }
        }
    }

    private fun requestDirectory(requestSessionId: String): String? =
        eventReducer.sessions.value.firstOrNull { it.id == requestSessionId }
            ?.directory
            ?.takeIf { it.isNotBlank() }
            ?: sessionDirectory

    // ============ Slash Command Actions ============

    /** Share the current session. Returns the share URL or null on failure. */
    fun shareSession(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val session = api.shareSession(conn, sessionId)
                val url = session.share?.url
                if (BuildConfig.DEBUG) Log.d(TAG, "Session share completed")
                onResult(url)
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to share session", e)
                onResult(null)
            }
        }
    }

    fun unshareSession(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                api.unshareSession(conn, sessionId)
                if (BuildConfig.DEBUG) Log.d(TAG, "Unshared session $sessionId")
                onResult(true)
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to unshare session", e)
                onResult(false)
            }
        }
    }

    /** Compact (summarize) the current session. */
    fun compactSession(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val state = uiState.value
                val providerId = state.selectedProviderId
                val modelId = state.selectedModelId
                if (providerId == null || modelId == null) {
                    Log.e(TAG, "Cannot compact: no model selected")
                    onResult(false)
                    return@launch
                }
                api.summarizeSession(conn, sessionId, providerId, modelId)
                if (BuildConfig.DEBUG) Log.d(TAG, "Compacted session $sessionId")
                onResult(true)
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to compact session", e)
                onResult(false)
            }
        }
    }

    /**
     * Export the session as JSON directly to a file URI.
     * Streams API responses directly to the output stream to avoid OOM
     * on large sessions (messages can be 80+ MB).
     * Shows a notification with download progress.
     */
    fun exportSession(context: android.content.Context, uri: android.net.Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "starburst_export"
            val notificationId = 9999

            // Create notification channel
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    context.getString(R.string.menu_export_session),
                    android.app.NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notification_export_progress)
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(context.getString(R.string.menu_export_session))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(0, 0, true)

            try {
                Log.d(TAG, "exportSession: streaming to $uri")
                notificationManager.notify(notificationId, builder.build())

                var lastNotifyTime = 0L
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    api.exportSessionToStream(conn, sessionId, outputStream) { bytesWritten ->
                        val now = System.currentTimeMillis()
                        if (now - lastNotifyTime > 500) { // throttle to 2 updates/sec
                            lastNotifyTime = now
                            val mb = String.format("%.1f MB", bytesWritten / 1_000_000.0)
                            builder.setContentText(mb)
                            notificationManager.notify(notificationId, builder.build())
                        }
                    }
                }

                Log.d(TAG, "exportSession: done")
                notificationManager.cancel(notificationId)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(true)
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to export session", e)
                notificationManager.cancel(notificationId)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false)
                }
            }
        }
    }

    /** Undo the last user message in the session, restoring its text to the input field. */
    fun undoMessage(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                // Find the last user message (before any existing revert point)
                val messages = uiState.value.messages
                val lastUser = messages.lastOrNull { it.isUser }
                if (lastUser == null) {
                    onResult(false)
                    return@launch
                }
                val revertedSession = api.revertSession(conn, sessionId, lastUser.message.id)
                eventReducer.upsertSession(serverId, revertedSession)
                pendingPromptRepository.remove(lastUser.message.id)
                _pendingPrompts.value = _pendingPrompts.value.filterNot { it.messageId == lastUser.message.id }
                if (BuildConfig.DEBUG) Log.d(TAG, "Reverted session $sessionId to message ${lastUser.message.id}")
                // Restore the user message text to the input field
                restoreRevertedDraft(extractRevertedDraft(lastUser))
                onResult(true)
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to revert session", e)
                onResult(false)
            }
        }
    }

    /** Revert to a specific user message by ID, optionally restoring its text to the input field. */
    fun revertMessage(messageId: String, revertedText: String? = null, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val revertedSession = api.revertSession(conn, sessionId, messageId)
                eventReducer.upsertSession(serverId, revertedSession)
                pendingPromptRepository.remove(messageId)
                _pendingPrompts.value = _pendingPrompts.value.filterNot { it.messageId == messageId }
                if (BuildConfig.DEBUG) Log.d(TAG, "Reverted session $sessionId to message $messageId")
                val targetMessage = uiState.value.messages
                    .lastOrNull { it.message.id == messageId && it.isUser }
                val fallbackPayload = RevertedDraftPayload(text = revertedText.orEmpty())
                restoreRevertedDraft(targetMessage?.let { extractRevertedDraft(it) } ?: fallbackPayload)
                onResult(true)
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to revert to message $messageId", e)
                onResult(false)
            }
        }
    }

    private fun extractRevertedDraft(message: ChatMessage): RevertedDraftPayload {
        val revertedText = message.parts
            .filterIsInstance<Part.Text>()
            .joinToString("\n") { it.text }

        val imageUris = message.parts
            .filterIsInstance<Part.File>()
            .mapNotNull { part ->
                val mime = part.mime.lowercase()
                if (mime.startsWith("image/") && !part.url.isNullOrBlank()) part.url else null
            }

        return RevertedDraftPayload(
            text = revertedText,
            attachmentUris = imageUris,
        )
    }

    private fun restoreRevertedDraft(payload: RevertedDraftPayload) {
        _draftText.value = payload.text
        _draftAttachmentUris.value = payload.attachmentUris
        _confirmedFilePaths.value = emptySet()
        _revertedDraftEvent.tryEmit(payload)
    }

    /** Redo the last undone message. */
    fun redoMessage(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                api.unrevertSession(conn, sessionId)
                if (BuildConfig.DEBUG) Log.d(TAG, "Unreverted session $sessionId")
                onResult(true)
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to unrevert session", e)
                onResult(false)
            }
        }
    }

    /** Fork the current session. Returns the new session or null. */
    fun forkSession(onResult: (Session?) -> Unit) {
        viewModelScope.launch {
            try {
                val session = api.forkSession(conn, sessionId)
                if (BuildConfig.DEBUG) Log.d(TAG, "Forked session $sessionId -> ${session.id}")
                onResult(session)
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to fork session", e)
                onResult(null)
            }
        }
    }

    /** Rename the current session. */
    fun renameSession(title: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                api.updateSession(conn, sessionId, title)
                if (BuildConfig.DEBUG) Log.d(TAG, "Renamed session $sessionId to $title")
                onResult(true)
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to rename session", e)
                onResult(false)
            }
        }
    }

    /** Execute a server-side command (e.g. /init, /review, MCP commands). */
    fun executeCommand(command: String, arguments: String = "", onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                if (!sessionLoaded.isCompleted) {
                    sessionLoaded.await()
                }
                if (sessionDirectory.isNullOrBlank()) {
                    loadSession()
                }

                val normalizedCommand = command.removePrefix("/").trim()
                val effectiveDirectory = sessionDirectory
                    ?: eventReducer.sessions.value
                        .firstOrNull { it.id == sessionId }
                        ?.directory
                        ?.takeIf { it.isNotBlank() }
                // /init: when arguments are omitted, rely on x-starburst-directory only.
                // Passing an explicit path (absolute or ".") can lead to duplicated or
                // malformed path text in the generated init prompt.
                val effectiveArguments = if (
                    normalizedCommand.equals("init", ignoreCase = true) && arguments.isBlank()
                ) {
                    ""
                } else {
                    arguments
                }

                val ok = api.executeCommand(
                    conn = conn,
                    sessionId = sessionId,
                    command = normalizedCommand,
                    arguments = effectiveArguments,
                    directory = effectiveDirectory
                )
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "Executed command /$normalizedCommand in session $sessionId: $ok (directory=$effectiveDirectory, arguments=$effectiveArguments)"
                    )
                }
                onResult(ok)
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to execute command", e)
                onResult(false)
            }
        }
    }

    /** Execute shell command in current session. */
    fun runShellCommand(command: String, onResult: (Boolean) -> Unit) {
        if (!sessionPromptable) {
            onResult(false)
            return
        }
        val trimmed = command.trim()
        if (trimmed.isBlank()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            try {
                val model = if (_selectedProviderId.value != null && _selectedModelId.value != null) {
                    ModelSelection(
                        providerId = _selectedProviderId.value!!,
                        modelId = _selectedModelId.value!!
                    )
                } else null
                val ok = api.runShellCommand(
                    conn = conn,
                    sessionId = sessionId,
                    command = trimmed,
                    agent = uiState.value.selectedAgent,
                    model = model,
                    directory = sessionDirectory
                )
                if (BuildConfig.DEBUG) Log.d(TAG, "Executed shell command in session $sessionId: $ok")
                onResult(ok)
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to execute shell command", e)
                onResult(false)
            }
        }
    }

    fun openTerminalSession(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            // Wait for loadSession() to finish so sessionDirectory is populated.
            // This prevents the race condition where the PTY is created with directory=null
            // and then resize is attempted with the real directory.
            sessionLoaded.await()
            if (BuildConfig.DEBUG) Log.d(TAG, "openTerminalSession: sessionDirectory=$sessionDirectory")
            terminalWorkspace.ensureActiveTab(cwd = sessionDirectory, directory = sessionDirectory, onResult = onResult)
        }
    }

    fun createTerminalTab(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            sessionLoaded.await()
            terminalWorkspace.createTab(cwd = sessionDirectory, directory = sessionDirectory, onResult = onResult)
        }
    }

    fun switchTerminalTab(tabId: String) {
        terminalWorkspace.switchTab(tabId)
    }

    fun closeTerminalTab(tabId: String) {
        terminalWorkspace.closeTab(tabId)
    }

    fun recoverTerminalTab(tabId: String, onResult: (Boolean) -> Unit = {}) {
        terminalWorkspace.recoverTab(tabId, onResult)
    }

    fun setTerminalFontSize(fontSizeSp: Float) {
        terminalWorkspace.setActiveFontSize(fontSizeSp)
    }

    fun sendTerminalInput(input: String) {
        terminalWorkspace.sendActiveInput(input)
    }

    fun clearTerminalBuffer() {
        terminalWorkspace.clearActiveBuffer()
    }

    fun resizeTerminal(cols: Int, rows: Int) {
        terminalWorkspace.resizeActive(cols, rows)
    }

    fun closeTerminalSession() {
        // Global terminal workspaces are server-scoped and survive chat screen changes.
    }

    /** Create a new session and return it. */
    fun createNewSession(onResult: (Session?) -> Unit) {
        viewModelScope.launch {
            try {
                // 继承当前会话的工作目录，保证「新会话」与父会话落在同一个 project 下。
                val dir = sessionDirectory
                if (BuildConfig.DEBUG) Log.d(TAG, "createNewSession from=$sessionId directory=$dir")
                val session = api.createSession(conn, directory = dir)
                eventReducer.upsertSession(serverId, session)
                if (BuildConfig.DEBUG) Log.d(TAG, "Created new session: ${session.id}")
                onResult(session)
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to create session", e)
                onResult(null)
            }
        }
    }

    /** Connection parameters for navigation to other sessions. */
    fun getConnectionParams(): ConnectionParams = ConnectionParams(
        serverUrl = serverUrl,
        username = username,
        password = password,
        serverName = serverName,
        serverId = serverId
    )

    /** Get the last assistant message text for copying. */
    fun getLastAssistantText(): String? {
        val msgs = uiState.value.messages
        val last = msgs.lastOrNull { it.isAssistant } ?: return null
        return last.parts
            .filterIsInstance<Part.Text>()
            .joinToString("") { it.text }
            .ifBlank { null }
    }

    // ============ Next-step suggestions ============

    fun generateSuggestions() {
        if (_isGeneratingSuggestions.value) return
        if (!sessionPromptable) return
        val generation = ++suggestionsGeneration
        viewModelScope.launch {
            _isGeneratingSuggestions.value = true
            _suggestionsError.value = null
            _suggestionsStreamText.value = ""
            _suggestionsSource.value = null
            try {
                val prompt = buildSuggestionPrompt()

                // 1) Prefer the backend-configured LLM (starburst-backend /api/llm/generate),
                //    which the admin configures once (usually the shared cloud model).
                val backendParsed = runCatching { generateViaBackendLlm(prompt) }.getOrNull()
                if (!backendParsed.isNullOrEmpty() && generation == suggestionsGeneration) {
                    _suggestions.value = backendParsed
                    _suggestionsSource.value = SuggestionSource.BACKEND
                    return@launch
                }

                // 2) Prefer an externally configured LLM provider (cloud), if set.
                val llmBaseUrl = settingsRepository.llmProviderBaseUrl.first()
                val llmModel = settingsRepository.llmProviderModel.first()
                val apiKey = secretStore.get(LocalSyncSecretStore.SecretKey.LLM_PROVIDER_API_KEY)
                val providerCfg = if (llmBaseUrl.isNotBlank() && llmModel.isNotBlank()) {
                    SuggestionProvider.Config(baseUrl = llmBaseUrl, apiKey = apiKey.orEmpty(), model = llmModel)
                } else null
                if (providerCfg != null) {
                    val parsed = runCatching {
                        suggestionProvider.suggest(providerCfg, SUGGESTION_API_SYSTEM, prompt)
                    }.getOrNull()
                    if (!parsed.isNullOrEmpty() && generation == suggestionsGeneration) {
                        _suggestions.value = parsed
                        _suggestionsSource.value = SuggestionSource.CLOUD
                        return@launch
                    }
                    // External provider failed/returned nothing → silently fall back to on-device.
                    Log.w(TAG, "External LLM provider unavailable, falling back to on-device MNN")
                }

                // 3) On-device suggestion generation via MNN (no server round-trip, fully offline).
                // ensureLoaded() auto-extracts the bundled model when present; only prompt for a
                // download when no model is bundled and none exists on disk.
                val loaded = MnnLlm.ensureLoaded(context)
                if (!loaded) {
                    _modelNeedsDownload.value = true
                    _suggestionsError.value = getModelDownloadPrompt()
                    return@launch
                }
                MnnLlm.reset()
                // Throttle UI refreshes so per-token streaming doesn't trigger a recomposition
                // on every single token (keeps the list/UI responsive on slower devices).
                var lastFlush = 0L
                var pending = StringBuilder()
                fun flush() {
                    if (pending.isEmpty()) return
                    if (generation == suggestionsGeneration) {
                        _suggestionsStreamText.value =
                            (_suggestionsStreamText.value + pending).take(400)
                        pending.clear()
                    }
                }
                val text = MnnLlm.generateStreaming(prompt, maxTokens = SUGGESTION_MAX_TOKENS) { delta ->
                    pending.append(delta)
                    val now = System.currentTimeMillis()
                    if (now - lastFlush >= 50L) {
                        lastFlush = now
                        flush()
                    }
                }
                Log.d(TAG, "MNN raw response: $text")
                // Flush any remaining streamed tail before parsing.
                flush()
                // Ignore the result if the conversation changed while generating (e.g. the user sent a message).
                if (generation != suggestionsGeneration) return@launch
                val parsed = text.let(::parseSuggestionList)
                _suggestions.value = parsed
                if (parsed.isNotEmpty()) {
                    _suggestionsSource.value = SuggestionSource.ON_DEVICE
                }
                if (parsed.isEmpty() && text.isNotBlank()) {
                    _suggestionsError.value = context.getString(R.string.suggestions_parse_failed, text.take(200))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Suggestions generation failed, using fallback", e)
                _suggestions.value = fallbackSuggestions()
                _suggestionsSource.value = SuggestionSource.FALLBACK
            } finally {
                _isGeneratingSuggestions.value = false
                _suggestionsStreamText.value = ""
            }
        }
    }

    /**
     * 通过 OpenCode Backend 已配置的编排 LLM 生成下一步建议。
     * 后端未配置 LLM（503）或请求失败时抛异常，由调用方回退到 App 设置 provider / MNN。
     */
    private suspend fun generateViaBackendLlm(prompt: String): List<String>? {
        val servers = serverRepository.servers.first()
        val backend = servers.firstOrNull { it.id == serverId }
        val host = runCatching { java.net.URL(serverUrl).host }.getOrNull()
            ?: serverUrl.substringAfter("://").substringBefore(":")
        if (host.isBlank()) return null
        val backendUrl = (backend?.backendResolvedUrl ?: "http://$host:18880").trimEnd('/')
        if (backendUrl.isBlank()) return null
        val token = backend?.backendResolvedToken ?: "ocb_default"
        val parsed = try {
            backendRepository.generateSuggestions(backendUrl, token, SUGGESTION_API_SYSTEM, prompt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.rethrowCancellation()
            Log.w(TAG, "Backend LLM unavailable, falling back (url=$backendUrl)", e)
            null
        }
        if (!parsed.isNullOrEmpty()) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Suggestions generated via backend LLM")
            return parsed
        }
        return null
    }

    /** Localized fallback suggestions used when the on-device model is unavailable or fails. */
    private fun fallbackSuggestions(): List<String> {        val isZh = context.resources.configuration.locales[0].language == "zh"
        return if (isZh) {
            listOf("继续当前任务", "总结一下刚才的改动", "测试一下刚才的功能")
        } else {
            listOf("Continue the current task", "Summarize the recent changes", "Test what was just implemented")
        }
    }

    /** Localized prompt explaining that the on-device model must be downloaded first. */
    private fun getModelDownloadPrompt(): String {
        val isZh = context.resources.configuration.locales[0].language == "zh"
        return if (isZh) {
            "端侧模型尚未下载，无法生成建议。请先下载端侧模型（约 522 MB）。"
        } else {
            "The on-device model has not been downloaded yet. Download it (~522 MB) to enable suggestions."
        }
    }

    /**
     * Downloads the on-device model from GitHub Releases and reports progress to the UI.
     * Safe to call repeatedly; skips if already downloaded.
     */
    fun downloadModel() {
        if (_modelDownloading.value) return
        viewModelScope.launch {
            _modelDownloading.value = true
            _modelDownloadProgress.value = 0
            try {
                val ok = MnnLlm.downloadModel(context) { percent ->
                    if (isActive) _modelDownloadProgress.value = percent
                }
                if (ok) {
                    _modelNeedsDownload.value = false
                    _suggestionsError.value = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to download model", e)
                _suggestionsError.value = context.getString(R.string.settings_on_device_model_download_failed)
            } finally {
                _modelDownloading.value = false
            }
        }
    }

    /**
     * Builds the suggestion prompt from a trimmed view of the conversation:
     * only the latest [SUGGESTION_CONTEXT_MAX_ROUNDS] user/assistant turns are included,
     * keeping the request small and fast compared with sending the full session history.
     */
    private fun buildSuggestionPrompt(): String {
        val recent = buildRecentConversationText()
        val isChinese = context.resources.configuration.locales[0].language == "zh"
        val instruction = if (isChinese) {
            "你是一个编程助手。根据对话内容，建议3个用户可以采取的下一步操作。\n" +
                "只输出一个JSON数组，包含3条简短的中文建议字符串，不要输出其他任何内容。\n\n" +
                "示例：\n" +
                "用户：我需要修复 auth.py 里的 bug\n" +
                "助手：[\"添加日志来调试认证流程\", \"为登录函数编写单元测试\", \"检查 token 验证逻辑\"]\n\n" +
                "现在请根据上面的对话建议3个操作。只输出JSON数组。"
        } else {
            "You are a coding assistant. Based on the conversation, suggest 3 next actions.\n" +
                "Reply with ONLY a JSON array of 3 short strings, nothing else.\n\n" +
                "Example:\n" +
                "User: I need to fix a bug in auth.py\n" +
                "Assistant: [\"Add logging to debug the auth flow\", \"Write a unit test for the login function\", \"Review the token validation logic\"]\n\n" +
                "Now suggest 3 actions for the conversation above. Reply with the JSON array only."
        }
        return if (recent.isBlank()) {
            instruction
        } else {
            "$instruction\n\nConversation so far:\n$recent"
        }
    }

    /** Serializes the last few user/assistant turns into plain text for the suggestion prompt. */
    private fun buildRecentConversationText(maxTurns: Int = SUGGESTION_CONTEXT_MAX_ROUNDS): String {
        val turns = uiState.value.messages
            .filter { it.isUser || it.isAssistant }
            .takeLast(maxTurns * 2)
        val lines = turns.mapNotNull { msg ->
            val text = msg.parts
                .filterIsInstance<Part.Text>()
                .filterNot { it.ignored == true }
                .joinToString("\n") { it.text }
                .trim()
            if (text.isBlank()) return@mapNotNull null
            when {
                msg.isUser -> "User: $text"
                else -> "Assistant: $text"
            }
        }
        return lines.joinToString("\n\n")
    }

    fun clearSuggestions() {
        // Invalidate any in-flight suggestion generation; its result will be discarded.
        suggestionsGeneration++
        _suggestions.value = emptyList()
        _suggestionsError.value = null
        _suggestionsSource.value = null
    }

    // ============ Message actions ============

    /**
     * 重新生成：回退到该 assistant 消息之前最近的一条用户消息，再自动重发其文本与附件。
     * 复用 [OpenCodeApi.revertSession] 与 [sendParts]，与 /undo 后再发送等价。
     *
     * @param assistantMessageId 要重新生成的 assistant 消息 ID
     * @param onResult 完成回调，true 表示已触发重发
     */
    fun regenerateMessage(assistantMessageId: String, onResult: (Boolean) -> Unit = {}) {
        if (_isSending.value) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            try {
                val messages = uiState.value.messages
                val assistantIdx = messages.indexOfFirst { it.message.id == assistantMessageId && it.isAssistant }
                if (assistantIdx < 0) {
                    onResult(false)
                    return@launch
                }
                val precedingUser = messages.subList(0, assistantIdx).lastOrNull { it.isUser }
                if (precedingUser == null) {
                    onResult(false)
                    return@launch
                }
                val parts = promptPartsFromMessage(precedingUser)
                if (parts.isEmpty()) {
                    onResult(false)
                    return@launch
                }
                // 回退到该用户消息，丢弃其后的 assistant 回复与后续消息。
                val reverted = api.revertSession(conn, sessionId, precedingUser.message.id)
                eventReducer.upsertSession(serverId, reverted)
                pendingPromptRepository.remove(precedingUser.message.id)
                _pendingPrompts.value = _pendingPrompts.value.filterNot { it.messageId == precedingUser.message.id }
                val sent = sendParts(parts)
                onResult(sent)
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Failed to regenerate message", e)
                onResult(false)
            }
        }
    }

    /**
     * 编辑重发：把该用户消息的原文与图片回填到输入框，供用户修改后重发。
     * 不回退会话，只回填输入框（复用 [restoreRevertedDraft] 的事件机制）。
     *
     * @param messageId 目标用户消息 ID
     */
    fun editUserMessage(messageId: String) {
        val message = uiState.value.messages.lastOrNull { it.message.id == messageId && it.isUser } ?: return
        restoreRevertedDraft(extractRevertedDraft(message))
    }

    /** 引用回复一条消息：把其文本以 markdown 引用块填入输入框。 */
    fun quoteMessage(messageId: String) {
        val message = uiState.value.messages.lastOrNull { it.message.id == messageId } ?: return
        val text = message.parts
            .filterIsInstance<Part.Text>()
            .filterNot { it.ignored == true }
            .joinToString("\n") { it.text }
            .trim()
        if (text.isBlank()) return
        val quoted = text.lineSequence().joinToString("\n") { "> $it" }
        val current = _draftText.value
        val merged = if (current.isBlank()) "$quoted\n" else "$current\n$quoted\n"
        updateDraftText(merged)
    }

    /** 收藏一条消息到书签。 */
    fun addBookmark(messageId: String) {
        val message = uiState.value.messages.lastOrNull { it.message.id == messageId } ?: return
        val text = message.parts
            .filterIsInstance<Part.Text>()
            .filterNot { it.ignored == true }
            .joinToString("\n") { it.text }
            .trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            bookmarkRepository.add(
                MessageBookmark(
                    id = MessageBookmark.buildId(serverId, sessionId, messageId),
                    serverId = serverId,
                    sessionId = sessionId,
                    messageId = messageId,
                    messageText = text,
                    createdAt = System.currentTimeMillis(),
                )
            )
        }
    }

    /**
     * 总结一条消息：优先调用云端 LLM，失败或未配置时回退端侧 MNN 模型。
     *
     * @param messageId 要总结的消息 ID
     */
    fun summarizeMessage(messageId: String) {
        if (_isSummarizing.value) return
        val message = uiState.value.messages.lastOrNull { it.message.id == messageId } ?: return
        val text = message.parts
            .filterIsInstance<Part.Text>()
            .filterNot { it.ignored == true }
            .joinToString("\n") { it.text }
            .trim()
        if (text.isBlank()) return
        runSummary(text)
    }

    /**
     * 总结整个会话：优先调用云端 LLM，失败或未配置时回退端侧 MNN 模型。
     */
    fun summarizeSession() {
        if (_isSummarizing.value) return
        val text = buildRecentConversationText(maxTurns = uiState.value.messages.size)
        if (text.isBlank()) return
        runSummary(text)
    }

    /** 关闭总结弹窗；若生成仍在进行，生成会在后台继续但弹窗不再显示。 */
    fun dismissSummary() {
        _summaryVisible.value = false
        _summaryText.value = null
        _summaryError.value = null
    }

    /** 由一条用户消息构造可重发的 [PromptPart] 列表（文本 + 文件附件）。 */
    private fun promptPartsFromMessage(message: ChatMessage): List<PromptPart> {
        val parts = mutableListOf<PromptPart>()
        message.parts
            .filterIsInstance<Part.Text>()
            .filter { it.text.isNotBlank() }
            .joinToString("\n") { it.text }
            .takeIf { it.isNotBlank() }
            ?.let { parts.add(PromptPart(type = "text", text = it)) }
        message.parts.filterIsInstance<Part.File>().forEach { file ->
            parts.add(
                PromptPart(
                    type = "file",
                    mime = file.mime,
                    url = file.url,
                    filename = file.filename,
                )
            )
        }
        return parts
    }

    /** 双链路执行总结：云端优先、端侧回退，参考 GitViewModel.generateCommitMessage 的写法。 */
    private fun runSummary(text: String) {
        viewModelScope.launch {
            _isSummarizing.value = true
            _summaryText.value = null
            _summaryError.value = null
            _summaryVisible.value = true
            try {
                val prompt = buildSummaryPrompt(text)
                var summary: String? = null
                var onDeviceAvailable = false
                val baseUrl = settingsRepository.llmProviderBaseUrl.first()
                val model = settingsRepository.llmProviderModel.first()
                if (baseUrl.isNotBlank() && model.isNotBlank()) {
                    val apiKey = secretStore.get(LocalSyncSecretStore.SecretKey.LLM_PROVIDER_API_KEY).orEmpty()
                    summary = runCatching {
                        suggestionProvider.chat(
                            SuggestionProvider.Config(baseUrl = baseUrl, apiKey = apiKey, model = model),
                            prompt,
                            maxTokens = SUMMARY_MAX_TOKENS,
                        ).trim().takeIf { it.isNotBlank() }
                    }.getOrNull()
                }
                if (summary == null) {
                    onDeviceAvailable = MnnLlm.ensureLoaded(context)
                    if (onDeviceAvailable) {
                        MnnLlm.reset()
                        // 流式生成：边生成边把部分内容推给 UI，避免只有转圈无反馈。
                        var lastFlush = 0L
                        val pending = StringBuilder()
                        fun flush() {
                            if (pending.isEmpty()) return
                            _summaryText.value = (_summaryText.value ?: "") + pending.toString()
                            pending.clear()
                        }
                        val full = MnnLlm.generateStreaming(prompt, maxTokens = SUMMARY_MAX_TOKENS) { delta ->
                            pending.append(delta)
                            val now = System.currentTimeMillis()
                            if (now - lastFlush >= 50L) {
                                lastFlush = now
                                flush()
                            }
                        }
                        flush()
                        summary = full.trim().takeIf { it.isNotBlank() }
                    }
                }
                if (summary != null) _summaryText.value = summary
                _summaryError.value = when {
                    summary != null -> null
                    !onDeviceAvailable && (baseUrl.isBlank() || model.isBlank()) ->
                        context.getString(R.string.chat_summary_no_model)
                    else -> context.getString(R.string.chat_summary_failed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.rethrowCancellation()
                Log.e(TAG, "Summary generation failed", e)
                _summaryError.value = context.getString(R.string.chat_summary_failed)
            } finally {
                _isSummarizing.value = false
            }
        }
    }

    /** 构造总结提示词，要求只输出简洁摘要（中/英按当前语言）。 */
    private fun buildSummaryPrompt(text: String): String {
        val isZh = context.resources.configuration.locales[0].language == "zh"
        return if (isZh) {
            "请用简洁的中文总结下面这段对话内容，突出关键结论、决定与待办事项。\n" +
                "只输出总结本身，不要解释、不要 markdown。\n\n内容：\n$text"
        } else {
            "Summarize the following conversation content concisely, highlighting key conclusions, " +
                "decisions, and open items.\nOutput ONLY the summary — no explanation, no markdown.\n\nContent:\n$text"
        }
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
