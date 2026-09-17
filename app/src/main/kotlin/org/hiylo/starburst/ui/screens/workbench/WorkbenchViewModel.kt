/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : WorkbenchViewModel.kt
 * Date : 2026/09/14 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.workbench

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.hiylo.starburst.BuildConfig
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.BackendApi
import org.hiylo.starburst.data.api.BackendTokenUsage
import org.hiylo.starburst.data.api.MessageIdGenerator
import org.hiylo.starburst.data.api.OpenCodeApi
import org.hiylo.starburst.data.api.OpenCodeGateway
import org.hiylo.starburst.data.api.PromptPart
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.api.SessionEventRecord
import org.hiylo.starburst.data.api.createdAtEpochMillis
import org.hiylo.starburst.data.api.deleteSession
import org.hiylo.starburst.data.api.listMessages
import org.hiylo.starburst.data.api.listPendingQuestions
import org.hiylo.starburst.data.api.getSession
import org.hiylo.starburst.data.api.listSessionEvents
import org.hiylo.starburst.data.api.listSessions
import org.hiylo.starburst.data.api.listSessionStatuses
import org.hiylo.starburst.data.api.QuestionInfo
import org.hiylo.starburst.data.api.QuestionRequest
import org.hiylo.starburst.data.api.promptAsync
import org.hiylo.starburst.data.api.replyToQuestion
import org.hiylo.starburst.data.backend.BackendPushListener
import org.hiylo.starburst.data.backend.PushSessionEvent
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.domain.model.Message
import org.hiylo.starburst.domain.model.MessageWithParts
import org.hiylo.starburst.domain.model.Part
import org.hiylo.starburst.domain.model.Session
import org.hiylo.starburst.domain.model.SessionStatus
import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.ml.AsrSession
import org.hiylo.starburst.ml.MnnAsr
import org.hiylo.starburst.ml.MnnAsrRecorder
import org.hiylo.starburst.ml.ServerAsrApi
import org.hiylo.starburst.ml.ServerAsrRecorder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import javax.inject.Inject

private const val TAG = "WorkbenchViewModel"
/** 实时事件流轮询间隔（毫秒）。 */
private const val EVENT_POLL_INTERVAL_MS = 5_000L
/** 会话列表 / 状态轮询间隔（毫秒）。 */
private const val SESSION_POLL_INTERVAL_MS = 10_000L
/** 用量统计轮询间隔（毫秒）。 */
private const val USAGE_POLL_INTERVAL_MS = 60_000L
/** 事件流单页大小与本地保留上限。 */
private const val EVENT_PAGE_LIMIT = 100
private const val MAX_EVENTS = 100
/** 初始化/兜底状态时从最近事件里提取 session.status 的事件条数。 */
private const val EVENT_STATUS_PAGE = 200
/** 决策面板 AI 最近回复摘要的最大字符数。 */
private const val AI_SUMMARY_CHAR_LIMIT = 600
/** 决策面板拉取会话消息的数量上限。 */
private const val PANEL_MESSAGE_LIMIT = 40
/** 决策面板「最近对话」保留的最近消息条数。 */
private const val PANEL_RECENT_COUNT = 8
/** 决策面板单条消息的最大字符数。 */
private const val PANEL_MESSAGE_CHAR_LIMIT = 400

/** 工作台会话条目：会话 + 派生状态 + 该会话的待决问题（若有）+ 未读新消息标记。 */
data class WorkbenchSession(
    val session: Session,
    val status: SessionStatus = SessionStatus.Idle,
    val pendingQuestion: QuestionRequest? = null,
    /** 后端 session_unread 记录的有新消息未读（Web/App 共享，仅后端可用时有意义）。 */
    val unread: Boolean = false,
)

/** 事件流条目：按会话聚合的最新动态（标题 + 路径 + AI 回复/事件摘要）。 */
data class WorkbenchEventItem(
    val sessionId: String,
    val title: String = "",
    val directory: String = "",
    val summary: String = "",
    val eventType: String = "",
    val createdAt: String = "",
)

/** AI 工作台看板的完整 UI 状态。 */
data class WorkbenchUiState(
    val serverName: String = "",
    val events: List<WorkbenchEventItem> = emptyList(),
    val sessions: List<WorkbenchSession> = emptyList(),
    val loadingSessions: Boolean = true,
    val eventsError: String? = null,
    val sessionsError: String? = null,
    val tokenUsage: List<BackendTokenUsage> = emptyList(),
    val loadingUsage: Boolean = true,
    val usageError: String? = null,
)

/** 决策面板里的一条最近对话消息。 */
data class PanelMessage(
    val role: String,
    val text: String,
    /** true 表示最近一条 AI 回复：完整显示不截断。 */
    val full: Boolean = false,
)

/** 展开中的会话「决策面板」内容。 */
data class DecisionPanelState(
    val sessionId: String,
    val aiSummary: String = "",
    val questions: List<QuestionInfo> = emptyList(),
    /** 待决提问的 requestId，用于把选项作为问题答案提交（而非当作普通消息发送）。 */
    val questionRequestId: String? = null,
    val recentMessages: List<PanelMessage> = emptyList(),
    val sessionTitle: String = "",
    val sessionDirectory: String = "",
    val sessionStatus: SessionStatus = SessionStatus.Idle,
    val loading: Boolean = true,
)

/**
 * AI 工作台看板 ViewModel（服务器级）：轮询后端 /api/events 事件流 + 会话列表/状态，
 * 排序「提问中(Question) > 处理中(Busy) > 空闲(Idle/其它)」；决策面板复用 promptAsync 发送快捷回复。
 *
 * @author Hsi Chu
 * @since 2.0.0
 */
@HiltViewModel
class WorkbenchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val api: OpenCodeApi,
    private val backendApi: BackendApi,
    private val serverRepository: ServerRepository,
    private val backendPushListener: BackendPushListener,
    @ApplicationContext private val context: Context,
    private val serverAsrApi: ServerAsrApi,
) : ViewModel() {

    private val serverId = savedStateHandle.get<String>("serverId").orEmpty()
    private val serverUrlArg = savedStateHandle.get<String>("serverUrl").orEmpty()
    private val usernameArg = savedStateHandle.get<String>("username").orEmpty()
    private val passwordArg = savedStateHandle.get<String>("password").orEmpty()
    private val serverNameArg = savedStateHandle.get<String>("serverName").orEmpty()

    /** OpenCode 直连连接（会话列表 / 消息 / prompt_async 用）。 */
    private var conn: ServerConnection? = null
    /** starburst-backend 地址与 APP token（/api/events 用）。 */
    private var backendUrl = ""
    private var backendToken = "ocb_default"
    /** 事件流正向游标：上一次响应的最后一条 createdAt。 */
    private var eventsCursor: String? = null

    private val _uiState = MutableStateFlow(WorkbenchUiState(serverName = serverNameArg))
    val uiState: StateFlow<WorkbenchUiState> = _uiState.asStateFlow()

    private val _panel = MutableStateFlow<DecisionPanelState?>(null)
    val panel: StateFlow<DecisionPanelState?> = _panel.asStateFlow()

    private val _sendingSessionId = MutableStateFlow<String?>(null)
    val sendingSessionId: StateFlow<String?> = _sendingSessionId.asStateFlow()

    // ============ 快捷回复语音输入（按住/点击说话，复用聊天页 ASR 管线） ============

    private val _voiceActive = MutableStateFlow(false)
    val voiceActive: StateFlow<Boolean> = _voiceActive.asStateFlow()

    private val _recognizedText = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val recognizedText: SharedFlow<String> = _recognizedText.asSharedFlow()

    private var asrRecorder: AsrSession? = null

    /** 独立作用域，专用于停止录音会话：ViewModel 清理时 viewModelScope 已取消，须用独立作用域避免麦克风常驻。 */
    private val asrCleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** start 进行中的门控，防止快速双击并发创建 recorder / 录音常驻后无法停止。 */
    private var voiceStarting = false

    /** 点击切换：空闲→开始录音；录音中→结束并提交识别文本。 */
    fun toggleVoice() {
        if (voiceStarting) return
        if (_voiceActive.value) {
            stopVoice()
            return
        }
        voiceStarting = true
        viewModelScope.launch {
            val session = createAsrSession()
            if (session == null) {
                voiceStarting = false
                _voiceActive.value = false
                return@launch
            }
            asrRecorder = session
            val ok = session.start(object : AsrSession.Listener {
                override fun onStart() {
                    voiceStarting = false
                    _voiceActive.value = true
                }

                override fun onPartialResult(text: String) {
                    _recognizedText.tryEmit(text)
                }

                override fun onError(message: String) {
                    voiceStarting = false
                    _voiceActive.value = false
                    if (asrRecorder === session) asrRecorder = null
                }

                override fun onStopped() {
                    voiceStarting = false
                    _voiceActive.value = false
                    if (asrRecorder === session) asrRecorder = null
                }
            })
            if (!ok) {
                voiceStarting = false
                _voiceActive.value = false
                if (asrRecorder === session) asrRecorder = null
            }
        }
    }

    fun stopVoice() {
        val recorder = asrRecorder
        asrRecorder = null
        _voiceActive.value = false
        if (recorder != null) viewModelScope.launch { recorder.stop() }
    }

    /** 退出工作台时释放录音会话：独立协程作用域不随 ViewModel 取消，必须显式停止避免麦克风常驻。 */
    override fun onCleared() {
        val recorder = asrRecorder
        asrRecorder = null
        _voiceActive.value = false
        if (recorder != null) {
            asrCleanupScope.launch { recorder.stop() }
        }
        super.onCleared()
    }

    /** 创建录音识别会话：优先后端代理流式引擎；不可用时回退端侧 MNN。 */
    private suspend fun createAsrSession(): AsrSession? {
        val endpoint = backendAsrEndpoint()
        if (endpoint != null && serverAsrApi.isAvailable(endpoint.first, endpoint.second)) {
            return ServerAsrRecorder(serverAsrApi, endpoint.first, endpoint.second)
        }
        if (MnnAsr.ensureLoaded(context)) return MnnAsrRecorder(context)
        return null
    }

    /** 解析后端地址与 token（工作台已是镜像连接，直接复用 backendUrl/backendToken）。 */
    private suspend fun backendAsrEndpoint(): Pair<String, String>? {
        val host = runCatching { java.net.URL(serverUrlArg).host }.getOrNull()
            ?: serverUrlArg.substringAfter("://").substringBefore(":")
        if (host.isBlank()) return null
        val url = (backendUrl.ifBlank { "http://$host:18880" }).trimEnd('/')
        if (url.isBlank()) return null
        return url to backendToken
    }

    /** 看板「实时动态」忽略的低层事件（流式分片/同步心跳），只展示有意义的会话级动态。 */
    private val HIGH_FREQUENCY_EVENT_TYPES = setOf(
        "heartbeat",
        "server.heartbeat",
        "sync",
        "server.connected",
        "message.part.delta",
        "message.part.updated",
        "message.part.removed",
        "message.updated",
        "session.updated",
    )

    init {
        viewModelScope.launch {
            val server = serverRepository.servers.first().firstOrNull { it.id == serverId }
            val baseUrl = server?.url?.trimEnd('/') ?: serverUrlArg
            val username = server?.username ?: usernameArg
            val password = server?.password ?: passwordArg.ifEmpty { null }
            conn = ServerConnection.from(baseUrl, username, password)
            backendUrl = server?.backendResolvedUrl.orEmpty()
            backendToken = server?.backendResolvedToken ?: "ocb_default"
            // 后端可用时走镜像通道：状态/列表走后端增强接口（快照+事件+活跃度聚合，准确）。
            if (backendUrl.isNotBlank()) {
                conn = ServerConnection(
                    baseUrl = backendUrl.trimEnd('/') + OpenCodeGateway.BACKEND_API_PREFIX,
                    authHeader = "Bearer $backendToken",
                )
            }
            if (conn == null) {
                _uiState.update { it.copy(loadingSessions = false, sessionsError = context.getString(R.string.workbench_error_resolve_conn)) }
                return@launch
            }
            refreshSessions()
            refreshEvents()
            refreshUsage()
            startPushStream()
            // 推送断线时的轮询兜底。
            viewModelScope.launch {
                while (isActive) {
                    delay(EVENT_POLL_INTERVAL_MS)
                    refreshEvents()
                }
            }
            viewModelScope.launch {
                while (isActive) {
                    delay(SESSION_POLL_INTERVAL_MS)
                    refreshSessions()
                }
            }
            viewModelScope.launch {
                while (isActive) {
                    delay(USAGE_POLL_INTERVAL_MS)
                    refreshUsage()
                }
            }
        }
    }

    /** 订阅后端 `/api/ws` 推送：事件流实时聚合；状态类事件立即全量刷新会话状态。 */
    private fun startPushStream() {
        if (backendUrl.isBlank()) return
        viewModelScope.launch {
            var backoffMs = 2_000L
            while (isActive) {
                try {
                    backendPushListener.eventFlow(backendUrl, backendToken).collect { ev ->
                        handlePushEvent(ev)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "push stream stopped: ${e.message}")
                }
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    private fun handlePushEvent(ev: PushSessionEvent) {
        // 正在查看的会话有新活动视为已读（与后端/Web 一致：任一端看过后全端清除）。
        if (ev.sessionId == _panel.value?.sessionId && isUnreadTriggerEventType(ev.eventType)) {
            markSessionRead(ev.sessionId)
        }
        when (ev.eventType) {
            "session.status", "session.updated" -> {
                ev.status()?.let { applyPushedStatus(ev.sessionId, it) }
                refreshSessionsSoon()
            }
            "session.idle" -> {
                applyPushedStatus(ev.sessionId, SessionStatus.Idle)
                refreshSessionsSoon()
            }
            "question.asked", "question.updated", "permission.asked", "permission.updated",
            "session.error", "session.failed", "message.complete",
            -> refreshSessionsSoon()
            else -> {}
        }
        if (ev.eventType !in HIGH_FREQUENCY_EVENT_TYPES) {
            mergePushEvent(ev)
        }
    }

    /** 与后端 isUnreadTriggerEvent 保持一致：这些事件会把会话标记为「有新消息未读」。 */
    private fun isUnreadTriggerEventType(t: String): Boolean = t in setOf(
        "message.complete", "message.created", "message.updated",
        "question.asked", "question.updated", "permission.asked",
        "session.idle", "session.status", "session.error", "session.failed",
    )

    /** 推送解析出的会话状态立即写入列表（不等待下一次轮询）。 */
    private fun applyPushedStatus(sessionId: String, status: SessionStatus) {
        _uiState.update { state ->
            state.copy(
                sessions = state.sessions.map { item ->
                    if (item.session.id == sessionId) item.copy(status = status) else item
                },
            )
        }
    }

    /** 状态类事件节流刷新（2s 内合并多次，避免事件风暴时频繁全量拉取）。 */
    @Volatile
    private var lastStatusRefreshAt = 0L

    private fun refreshSessionsSoon() {
        val now = System.currentTimeMillis()
        if (now - lastStatusRefreshAt < STATUS_REFRESH_DEBOUNCE_MS) return
        lastStatusRefreshAt = now
        viewModelScope.launch { refreshSessions() }
    }

    /** 把一条推送事件按会话聚合进事件流（每会话一行最新动态）。 */
    private fun mergePushEvent(ev: PushSessionEvent) {
        _uiState.update { current ->
            val session = current.sessions.firstOrNull { it.session.id == ev.sessionId }?.session
            val (payloadTitle, payloadDirectory, payloadFile) = payloadMeta(ev.payload)
            // 文件事件直接用完整文件路径作标题（目录+文件名），不再匹配会话标题，避免同目录多会话误配。
            val title = payloadFile.ifBlank {
                payloadTitle.ifBlank { payloadDirectory.substringAfterLast('/') }
            }
            val item = WorkbenchEventItem(
                sessionId = ev.sessionId,
                title = title,
                directory = session?.directory?.takeIf { it.isNotBlank() } ?: payloadDirectory,
                summary = summarizeEvent(context, ev.eventType, ev.payload),
                eventType = ev.eventType,
                createdAt = Instant.now().toString(),
            )
            current.copy(
                events = (current.events + item)
                    .sortedByDescending { parseEventTime(it.createdAt) }
                    .distinctBy { it.sessionId }
                    .take(MAX_EVENTS),
                eventsError = null,
            )
        }
    }

    /** 拉取事件流增量（兜底轮询），同样按会话聚合。 */
    private suspend fun refreshEvents() {
        if (backendUrl.isBlank()) {
            _uiState.update { it.copy(eventsError = context.getString(R.string.workbench_error_no_backend)) }
            return
        }
        try {
            val fresh = api.listSessionEvents(
                backendUrl = backendUrl,
                token = backendToken,
                since = eventsCursor,
                limit = EVENT_PAGE_LIMIT,
            )
            if (fresh.isNotEmpty()) {
                eventsCursor = fresh.last().createdAt
            }
            val items = fresh
                .filterNot { it.eventType in HIGH_FREQUENCY_EVENT_TYPES }
                .map { record ->
                    val session = _uiState.value.sessions.firstOrNull { it.session.id == record.sessionId }?.session
                    val (payloadTitle, payloadDirectory, payloadFile) = payloadMeta(record.payload)
                    // 文件事件直接用完整文件路径作标题（目录+文件名），不再匹配会话标题，避免同目录多会话误配。
                    val title = payloadFile.ifBlank {
                        payloadTitle.ifBlank { payloadDirectory.substringAfterLast('/') }
                    }
                    WorkbenchEventItem(
                        sessionId = record.sessionId,
                        title = title,
                        directory = session?.directory?.takeIf { it.isNotBlank() } ?: payloadDirectory,
                        summary = summarizeEvent(context, record.eventType, record.payload),
                        eventType = record.eventType,
                        createdAt = record.createdAt,
                    )
                }
            if (items.isNotEmpty()) {
                _uiState.update { current ->
                    current.copy(
                        events = (current.events + items)
                            .sortedByDescending { parseEventTime(it.createdAt) }
                            .distinctBy { it.sessionId }
                            .take(MAX_EVENTS),
                        eventsError = null,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "refresh events failed: ${e.message}")
            _uiState.update { it.copy(eventsError = it.eventsError ?: e.message ?: context.getString(R.string.workbench_error_events)) }
        }
    }

    /** 拉取后端用量统计（`GET /api/stats`）：token 调用量，读多写少，慢轮询。 */
    private suspend fun refreshUsage() {
        if (backendUrl.isBlank()) {
            _uiState.update { it.copy(loadingUsage = false, usageError = context.getString(R.string.usage_no_backend)) }
            return
        }
        try {
            val stats = backendApi.getStats(backendUrl, backendToken)
            _uiState.update {
                it.copy(tokenUsage = stats.tokenUsage, loadingUsage = false, usageError = null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "refresh usage failed: ${e.message}")
            _uiState.update {
                it.copy(loadingUsage = false, usageError = e.message ?: context.getString(R.string.usage_error))
            }
        }
    }

    /** 拉取全量会话 + 状态 + 待决问题，派生排序后刷新列表。 */
    private suspend fun refreshSessions() {
        val activeConn = conn ?: return
        try {
            val sessions = api.listSessions(activeConn)
            // 服务器 /question 按 query 参数 directory 过滤，按会话目录分组聚合查询。
            val directories = sessions.asSequence()
                .map { it.directory }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()
            val pendingBySession = directories.flatMap { dir ->
                runCatching { api.listPendingQuestions(activeConn, directory = dir) }
                    .getOrElse { e ->
                        if (e is CancellationException) throw e
                        if (BuildConfig.DEBUG) Log.d(TAG, "Failed to load pending questions for $dir: ${e.message}")
                        emptyList()
                    }
            }.groupBy { it.sessionId }
            // 镜像通道下 listSessionStatuses 走后端增强接口（快照+事件聚合），状态准确。
            // /session/status 只认 query 参数 directory，按会话目录分组聚合查询。
            val statuses = directories.flatMap { dir ->
                runCatching { api.listSessionStatuses(activeConn, directory = dir) }
                    .getOrElse { e ->
                        if (e is CancellationException) throw e
                        if (BuildConfig.DEBUG) Log.d(TAG, "Failed to load session status for $dir: ${e.message}")
                        emptyMap()
                    }
                    .toList()
            }.toMap()
            // 补齐缺失的 busy/retry 子会话对象，保证父会话能归并子会话的处理中状态。
            val sessionsWithChildren = hydrateBusyChildren(activeConn, sessions, statuses)
            val items = buildWorkbenchSessions(sessionsWithChildren, statuses, pendingBySession)
            // 未读新消息：仅后端已配置/可达时才有意义（后端 session_unread 为权威状态）。
            val unread = if (backendUrl.isBlank()) emptySet() else {
                runCatching { backendApi.listUnread(backendUrl, backendToken) }
                    .getOrElse { e ->
                        if (e is CancellationException) throw e
                        if (BuildConfig.DEBUG) Log.d(TAG, "load unread failed: ${e.message}")
                        emptySet()
                    }
            }
            val enriched = items.map { if (it.session.id in unread) it.copy(unread = true) else it }
            _uiState.update { current ->
                current.copy(
                    sessions = enriched,
                    loadingSessions = false,
                    sessionsError = null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "refresh sessions failed: ${e.message}")
            _uiState.update { it.copy(loadingSessions = false, sessionsError = e.message ?: context.getString(R.string.workbench_error_sessions)) }
        }
    }

    /**
     * 服务器 /session/status 只返回 busy/retry 会话。当子会话（subagent）正在运行而全量列表
     * 只含根会话时，父会话无法归并子会话的处理中状态。这里补齐缺失的 busy/retry 子会话对象。
     */
    private suspend fun hydrateBusyChildren(
        activeConn: ServerConnection,
        sessions: List<Session>,
        statuses: Map<String, SessionStatus>,
    ): List<Session> {
        val knownIds = sessions.asSequence().map { it.id }.toSet()
        val missingBusyIds = statuses.asSequence()
            .filter { (_, status) -> status is SessionStatus.Busy || status is SessionStatus.Retry }
            .map { it.key }
            .filterNot { it in knownIds }
            .toList()
        if (missingBusyIds.isEmpty()) return sessions
        val hydrated = missingBusyIds.mapNotNull { id ->
            runCatching { api.getSession(activeConn, id) }.getOrNull()
        }
        return sessions + hydrated
    }

    /**
     * 把服务器全量会话归并为根会话条目并计算派生状态：
     * 子会话的忙/待决问题归并到父会话，与现有 SessionListViewModel 的口径一致。
     */
    private fun buildWorkbenchSessions(
        sessions: List<Session>,
        statuses: Map<String, SessionStatus>,
        pendingBySession: Map<String, List<QuestionRequest>>,
    ): List<WorkbenchSession> {
        val childBusyByParent = mutableMapOf<String, SessionStatus>()
        val parentWithQuestion = mutableSetOf<String>()
        for (child in sessions) {
            val parentId = child.parentId ?: continue
            if (child.id in pendingBySession) parentWithQuestion += parentId
            val childStatus = statuses[child.id] ?: continue
            if (childStatus is SessionStatus.Busy || childStatus is SessionStatus.Retry) {
                childBusyByParent[parentId] = childStatus
            }
        }
        return sessions
            .filter { it.parentId == null && !it.isArchived }
            .map { session ->
                val status = when {
                    session.id in pendingBySession || session.id in parentWithQuestion -> SessionStatus.Question
                    else -> {
                        val self = statuses[session.id]
                        when {
                            self is SessionStatus.Busy || self is SessionStatus.Retry -> self ?: SessionStatus.Idle
                            else -> childBusyByParent[session.id] ?: self ?: SessionStatus.Idle
                        }
                    }
                }
                WorkbenchSession(
                    session = session,
                    status = status,
                    pendingQuestion = pendingBySession[session.id]?.firstOrNull(),
                )
            }
            .sortedWith(workbenchSessionComparator)
    }

    /** 会话排序：提问中(Question) > 处理中(Busy/Retry) > 空闲(Idle)；同组内用稳定的创建时间，状态不变时顺序不跳动。 */
    private val workbenchSessionComparator: Comparator<WorkbenchSession> =
        compareBy<WorkbenchSession> { statusRank(it.status) }
            .thenByDescending { it.session.time.created }
            .thenByDescending { it.session.id }

    private fun statusRank(status: SessionStatus): Int = when (status) {
        is SessionStatus.Question -> 0
        is SessionStatus.Busy, is SessionStatus.Retry -> 1
        is SessionStatus.Idle -> 2
    }

    /** 展开 / 收起某会话的决策面板；展开时异步加载 AI 最近回复摘要与待决问题。 */
    fun togglePanel(sessionId: String) {
        if (_panel.value?.sessionId == sessionId) {
            _panel.value = null
            return
        }
        _panel.value = DecisionPanelState(sessionId = sessionId, loading = true)
        markSessionRead(sessionId)
        viewModelScope.launch {
            _panel.value = loadPanel(sessionId)
        }
    }

    /** 点开会话视为已读：清本地未读标记并通知后端（任一端读过后全端不再显示未读）。 */
    private fun markSessionRead(sessionId: String) {
        _uiState.update { current ->
            val updated = current.sessions.map { item ->
                if (item.session.id == sessionId && item.unread) item.copy(unread = false) else item
            }
            current.copy(sessions = updated)
        }
        if (backendUrl.isBlank()) return
        viewModelScope.launch {
            runCatching { backendApi.markSessionRead(backendUrl, backendToken, sessionId) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    if (BuildConfig.DEBUG) Log.d(TAG, "mark session read failed: ${e.message}")
                }
        }
    }

    private suspend fun loadPanel(sessionId: String): DecisionPanelState {
        val activeConn = conn ?: return DecisionPanelState(sessionId = sessionId, loading = false)
        val messages = runCatching {
            api.listMessages(activeConn, sessionId, limit = PANEL_MESSAGE_LIMIT)
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            if (BuildConfig.DEBUG) Log.d(TAG, "load panel messages failed: ${e.message}")
            emptyList()
        }
        val session = _uiState.value.sessions.firstOrNull { it.session.id == sessionId }?.session
        val pending = _uiState.value.sessions.firstOrNull { it.session.id == sessionId }?.pendingQuestion
        val status = _uiState.value.sessions.firstOrNull { it.session.id == sessionId }?.status ?: SessionStatus.Idle
        return DecisionPanelState(
            sessionId = sessionId,
            aiSummary = buildAiSummary(messages),
            questions = pending?.questions.orEmpty(),
            questionRequestId = pending?.id,
            recentMessages = buildRecentMessages(messages),
            sessionTitle = session?.title.orEmpty(),
            sessionDirectory = session?.directory.orEmpty(),
            sessionStatus = status,
            loading = false,
        )
    }

    /** 取最后一条 assistant 消息的文本 parts 拼接作为「最近回复摘要」。 */
    private fun buildAiSummary(messages: List<MessageWithParts>): String {
        val assistant = messages.asReversed().firstOrNull { it.info is Message.Assistant } ?: return ""
        val text = assistant.parts
            .filterIsInstance<Part.Text>()
            .filter { it.synthetic != true && it.ignored != true }
            .joinToString("\n") { it.text }
            .trim()
        return text.take(AI_SUMMARY_CHAR_LIMIT)
    }

    /** 取最近几轮有文本内容的对话（user/assistant 交替），最近一条 assistant 完整显示。 */
    private fun buildRecentMessages(messages: List<MessageWithParts>): List<PanelMessage> {
        val withText = messages.asReversed().mapNotNull { mw ->
            val text = mw.parts
                .filterIsInstance<Part.Text>()
                .filter { it.synthetic != true && it.ignored != true }
                .joinToString("\n") { it.text }
                .trim()
            if (text.isBlank()) return@mapNotNull null
            val role = if (mw.info is Message.Assistant) "assistant" else "user"
            PanelMessage(role = role, text = text)
        }.take(PANEL_RECENT_COUNT)
        val lastAssistantIndex = withText.indexOfFirst { it.role == "assistant" }
        return withText.mapIndexed { index, msg ->
            if (msg.role == "assistant" && index == lastAssistantIndex) {
                msg.copy(full = true)
            } else {
                msg.copy(text = msg.text.take(PANEL_MESSAGE_CHAR_LIMIT))
            }
        }
    }

    /**
     * 快捷回复发送：复用现有发送链路 POST {conn.baseUrl}/session/{id}/prompt_async。
     * 成功后把该会话乐观置为 Busy，并刷新决策面板。
     */
    fun sendQuickReply(sessionId: String, text: String, onResult: (Boolean) -> Unit = {}) {
        val trimmed = text.trim()
        val activeConn = conn ?: return
        val session = _uiState.value.sessions.firstOrNull { it.session.id == sessionId }?.session ?: return
        if (trimmed.isEmpty() || _sendingSessionId.value != null) return
        _sendingSessionId.value = sessionId
        viewModelScope.launch {
            try {
                api.promptAsync(
                    conn = activeConn,
                    sessionId = sessionId,
                    messageId = MessageIdGenerator.next(),
                    parts = listOf(PromptPart(type = "text", text = trimmed)),
                    directory = session.directory,
                )
                _uiState.update { state ->
                    state.copy(
                        sessions = state.sessions.map { item ->
                            if (item.session.id == sessionId) item.copy(status = SessionStatus.Busy) else item
                        },
                    )
                }
                if (_panel.value?.sessionId == sessionId) {
                    _panel.value = loadPanel(sessionId)
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Quick reply sent to session $sessionId")
                onResult(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Quick reply failed", e)
                onResult(false)
            } finally {
                _sendingSessionId.value = null
            }
        }
    }

    /**
     * 把决策面板里的问题选项作为问题答案提交（POST /question/{id}/reply），而不是当作普通消息发送。
     * 成功后重拉面板让该提问消失。
     */
    fun replyToQuestion(
        requestSessionId: String,
        requestId: String,
        answers: List<List<String>>,
        onResult: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            try {
                val activeConn = conn ?: return@launch
                val session = _uiState.value.sessions.firstOrNull { it.session.id == requestSessionId }?.session
                val success = api.replyToQuestion(
                    conn = activeConn,
                    requestId = requestId,
                    answers = answers,
                    directory = session?.directory?.takeIf { it.isNotBlank() },
                )
                if (success && _panel.value?.sessionId == requestSessionId) {
                    _panel.value = loadPanel(requestSessionId)
                }
                onResult(success)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reply to question $requestId", e)
                onResult(false)
            }
        }
    }

    /**
     * 删除会话：调删除接口后从列表移除（含正在展开的面板）。
     */
    fun deleteSession(sessionId: String, onResult: (Boolean) -> Unit = {}) {
        val activeConn = conn ?: return
        viewModelScope.launch {
            val ok = runCatching { api.deleteSession(activeConn, sessionId) }.getOrDefault(false)
            if (ok) {
                _uiState.update { state ->
                    state.copy(sessions = state.sessions.filterNot { it.session.id == sessionId })
                }
                if (_panel.value?.sessionId == sessionId) {
                    _panel.value = null
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Deleted session $sessionId")
            }
            onResult(ok)
        }
    }
}

/** 状态类事件刷新的去抖窗口（毫秒）。 */
private const val STATUS_REFRESH_DEBOUNCE_MS = 2_000L

/** 事件流排序用时间戳解析（RFC3339Nano；失败回退 0）。 */
private fun parseEventTime(createdAt: String): Long =
    runCatching { Instant.parse(createdAt).toEpochMilli() }.getOrDefault(0L)

/**
 * 从事件 payload 里提取展示信息：目录、会话标题、文件路径。
 * 目录优先取 payload 顶层 directory，其次 session 对象，最后从 file 路径反推；
 * 标题优先 session.title；文件事件直接用完整文件路径作标题，不再显示会话标题。
 */
private fun payloadMeta(payload: JsonElement?): Triple<String, String, String> {
    val obj = payload as? JsonObject ?: return Triple("", "", "")
    val props = obj["properties"] as? JsonObject ?: obj["data"] as? JsonObject
    val sessionObj = obj["session"] as? JsonObject ?: props?.get("session") as? JsonObject
    val title = sessionObj?.get("title")?.jsonPrimitive?.contentOrNull.orEmpty()
    val filePath = filePathFrom(obj, props).orEmpty()
    val directory = (obj["directory"] as? JsonPrimitive)?.contentOrNull
        ?: sessionObj?.get("directory")?.jsonPrimitive?.contentOrNull
        ?: filePath.let { fp ->
            val slash = fp.lastIndexOf('/')
            if (slash > 0) fp.substring(0, slash) else ""
        }
        .orEmpty()
    return Triple(title, directory, filePath)
}

/**
 * 从事件里取文件路径：v1.18 file 事件中 file 是字符串（直接是路径），
 * 兼容旧版 { "filePath": ... } 对象形态。
 */
private fun filePathFrom(obj: JsonObject, props: JsonObject?): String? {
    val file = obj["file"] ?: props?.get("file")
    return when (file) {
        is JsonPrimitive -> file.contentOrNull
        is JsonObject -> file["filePath"]?.jsonPrimitive?.contentOrNull
        else -> null
    }
}

/**
 * 从事件里提取用于「实时动态」行的摘要：优先 AI 回复文本（message 的 content），
 * 其次流式 part 文本 / 问题文本 / 权限，最后按事件类型给中文文案。
 */
    /** 事件行摘要：优先从 payload 提取动作文本，其次按事件类型给本地化文案。 */
    private fun summarizeEvent(context: Context, eventType: String, payload: JsonElement?): String {
        val obj = (payload as? JsonObject) ?: return fallbackEventText(context, eventType)
        val props = obj["properties"] as? JsonObject ?: obj["data"] as? JsonObject

        // session.status：解析出「开始处理 / 处理完成 / 重试中」等动作。
        if (eventType == "session.status" || eventType == "session.updated") {
            val st = props?.get("status")
            val type = when (st) {
                is JsonObject -> st["type"]?.jsonPrimitive?.contentOrNull
                is JsonPrimitive -> st.contentOrNull
                else -> null
            }
            when (type) {
                "busy" -> return context.getString(R.string.workbench_event_busy)
                "idle" -> return context.getString(R.string.workbench_event_idle)
                "retry" -> return context.getString(R.string.workbench_event_retry)
                "error" -> return context.getString(R.string.workbench_event_error)
                else -> {}
            }
        }

        // question：显示等待回答的问题内容。
        val questionsArray = obj["questions"] as? JsonArray ?: props?.get("questions") as? JsonArray
        val questionObj = questionsArray?.firstOrNull() as? JsonObject
        val questionText = questionObj?.get("question")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        if (questionText != null) return context.getString(R.string.workbench_event_question_waiting, questionText.trim().take(60))

        // permission：显示需要授权的工具/权限类型。
        val permissionText = (obj["permission"] as? JsonObject ?: props?.get("permission") as? JsonObject)
            ?.get("type")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        if (permissionText != null) return context.getString(R.string.workbench_event_permission, permissionText)

        // error：显示错误摘要。
        val errorText = (obj["error"] as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
        if (errorText != null) return context.getString(R.string.workbench_event_error_detail, errorText.trim().take(60))

        // tool / file：显示正在执行的工具或修改的文件，比「状态更新」更有用。
        val toolText = (obj["tool"] as? JsonObject ?: props?.get("tool") as? JsonObject)
            ?.get("type")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        if (toolText != null) return context.getString(R.string.workbench_event_tool, toolText)

        val fileText = filePathFrom(obj, props)?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        if (fileText != null) return context.getString(R.string.workbench_event_file, fileText)

        // message.updated / part：AI 回复文本，只截取可读的开头。
        val contentArray = (obj["message"] as? JsonObject ?: props?.get("message") as? JsonObject)
            ?.get("content") as? JsonArray
        val messageText = contentArray
            ?.mapNotNull { it as? JsonObject }
            ?.mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }
            ?.joinToString(" ")
            ?.takeIf { it.isNotBlank() }
        if (messageText != null) return messageText.trim().replace('\n', ' ').take(50)

        val partText = (obj["part"] as? JsonObject ?: props?.get("part") as? JsonObject)
            ?.get("text")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        if (partText != null) return partText.trim().replace('\n', ' ').take(50)

        return fallbackEventText(context, eventType)
    }

    private fun fallbackEventText(context: Context, eventType: String): String = when {
        eventType.contains("error") || eventType.contains("failed") -> context.getString(R.string.workbench_event_error)
        eventType == "message.complete" || eventType.contains("idle") -> context.getString(R.string.workbench_event_idle)
        eventType.startsWith("question.") -> context.getString(R.string.workbench_event_question)
        eventType.startsWith("permission.") -> context.getString(R.string.workbench_event_permission_short)
        eventType.contains("busy") -> context.getString(R.string.workbench_event_busy)
        eventType.startsWith("file.") -> context.getString(R.string.workbench_event_file_short)
        eventType.startsWith("tool") -> context.getString(R.string.workbench_event_tool_short)
        eventType.startsWith("message.") || eventType.contains("assistant") -> context.getString(R.string.workbench_event_reply)
        eventType.startsWith("user.") || eventType.contains("prompt") -> context.getString(R.string.workbench_event_user)
        eventType.startsWith("project.") -> context.getString(R.string.workbench_event_project)
        eventType.contains("status") || eventType.contains("updated") || eventType.contains("created") -> context.getString(R.string.workbench_event_status)
        else -> context.getString(R.string.workbench_event_misc)
    }