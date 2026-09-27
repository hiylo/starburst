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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.hiylo.starburst.BuildConfig
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.BackendApi
import org.hiylo.starburst.data.api.MessageIdGenerator
import org.hiylo.starburst.data.api.StarBurstApi
import org.hiylo.starburst.data.api.StarBurstGateway
import org.hiylo.starburst.data.api.PromptPart
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.api.SessionEventRecord
import org.hiylo.starburst.data.api.createdAtEpochMillis
import org.hiylo.starburst.data.api.abortSession
import org.hiylo.starburst.data.api.deleteSession
import org.hiylo.starburst.data.api.listMessages
import org.hiylo.starburst.data.api.listPendingPermissions
import org.hiylo.starburst.data.api.listPendingQuestions
import org.hiylo.starburst.data.api.getSession
import org.hiylo.starburst.data.api.listSessions
import org.hiylo.starburst.data.api.listSessionStatusesForDirectories
import org.hiylo.starburst.data.api.updateSession
import org.hiylo.starburst.data.api.QuestionInfo
import org.hiylo.starburst.data.api.QuestionRequest
import org.hiylo.starburst.data.api.PermissionRequest
import org.hiylo.starburst.data.api.sendPrompt
import org.hiylo.starburst.data.api.replyToPermission
import org.hiylo.starburst.data.api.replyToQuestion
import org.hiylo.starburst.data.backend.BackendPushListener
import org.hiylo.starburst.data.backend.PushSessionEvent
import org.hiylo.starburst.data.repository.ServerConnectionStateRepository
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.domain.model.Message
import org.hiylo.starburst.domain.model.MessageWithParts
import org.hiylo.starburst.domain.model.Part
import org.hiylo.starburst.domain.model.Session
import org.hiylo.starburst.domain.model.SessionStatus
import org.hiylo.starburst.domain.model.ToolState
import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.ui.util.launchWhileStarted
import org.hiylo.starburst.ml.AsrSession
import org.hiylo.starburst.ml.MnnAsr
import org.hiylo.starburst.ml.MnnAsrRecorder
import org.hiylo.starburst.ml.ServerAsrApi
import org.hiylo.starburst.ml.ServerAsrRecorder
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
/** 会话列表 / 状态轮询间隔（毫秒）。 */
private const val SESSION_POLL_INTERVAL_MS = 10_000L
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
/** 面板「查看全部消息」的最大消息条数。 */
private const val PANEL_ALL_LIMIT = 200
/** 卡片未展开时「最后消息预览」的最大字符数。 */
private const val PREVIEW_CHAR_LIMIT = 96
/** 卡片预览懒加载拉取的消息条数（够取最后一条有效消息即可）。 */
private const val PREVIEW_MESSAGE_LIMIT = 6

/** 工作台会话条目：会话 + 派生状态 + 该会话的待决问题（若有）+ 未读新消息标记。 */
data class WorkbenchSession(
    val session: Session,
    val status: SessionStatus = SessionStatus.Idle,
    val pendingQuestion: QuestionRequest? = null,
    /** 待处理的权限请求（等待授权/拒绝）。 */
    val pendingPermissions: List<PermissionRequest> = emptyList(),
    /** 后端 session_unread 记录的有新消息未读（Web/App 共享，仅后端可用时有意义）。 */
    val unread: Boolean = false,
    /** 最后一条有效消息预览（懒加载，null 表示尚未加载）。 */
    val aiPreview: String? = null,
    /** 本地置顶（本机持久化，跨重启保留）。 */
    val pinned: Boolean = false,
    /** 最近推送出现过失败（用于「出错」筛选 / 置顶 / 面板重试横幅）。 */
    val hasError: Boolean = false,
)

/** AI 工作台看板的完整 UI 状态。 */
data class WorkbenchUiState(
    val serverName: String = "",
    val sessions: List<WorkbenchSession> = emptyList(),
    val loadingSessions: Boolean = true,
    val sessionsError: String? = null,
)

/** 决策面板里的一条最近对话消息。 */
data class PanelMessage(
    val role: String,
    val text: String,
    /** true 表示最近一条 AI 回复：完整显示不截断。 */
    val full: Boolean = false,
    /** 该消息里的非文本部件（图片 / 工具 / 文件）。 */
    val attachments: List<PanelAttachment> = emptyList(),
)

/** 决策面板消息里的非文本部件。 */
data class PanelAttachment(
    /** image / tool / file。 */
    val kind: String,
    val label: String,
    val detail: String = "",
    val url: String? = null,
)

/** 展开中的会话「决策面板」内容。 */
data class DecisionPanelState(
    val sessionId: String,
    val aiSummary: String = "",
    val questions: List<QuestionInfo> = emptyList(),
    /** 待决提问的 requestId，用于把选项作为问题答案提交（而非当作普通消息发送）。 */
    val questionRequestId: String? = null,
    /** 待处理的权限请求（面板内快捷授权/拒绝）。 */
    val permissions: List<PermissionRequest> = emptyList(),
    val recentMessages: List<PanelMessage> = emptyList(),
    val sessionTitle: String = "",
    val sessionDirectory: String = "",
    val sessionStatus: SessionStatus = SessionStatus.Idle,
    val loading: Boolean = true,
    /** 是否展开「全部消息」视图。 */
    val showAll: Boolean = false,
    /** 「全部消息」内容（null 表示尚未加载）。 */
    val allMessages: List<PanelMessage>? = null,
    /** 最近一轮模型上下文占用（token）。 */
    val contextTokens: Int = 0,
    /** 模型上下文窗口上限（token），未知为 0。 */
    val contextWindow: Int = 0,
)

/** 工作台会话列表筛选维度：全部 / 待回复(提问中) / 待授权 / 处理中 / 出错 / 空闲。 */
enum class WorkbenchFilter { All, Question, Permission, Busy, Error, Idle }

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
    private val api: StarBurstApi,
    private val backendApi: BackendApi,
    private val serverRepository: ServerRepository,
    private val connectionStateRepository: ServerConnectionStateRepository,
    private val backendPushListener: BackendPushListener,
    @ApplicationContext private val context: Context,
    private val serverAsrApi: ServerAsrApi,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val serverId = savedStateHandle.get<String>("serverId").orEmpty()
    private val serverUrlArg = savedStateHandle.get<String>("serverUrl").orEmpty()
    private val usernameArg = savedStateHandle.get<String>("username").orEmpty()
    private val passwordArg = savedStateHandle.get<String>("password").orEmpty()
    private val serverNameArg = savedStateHandle.get<String>("serverName").orEmpty()

    /** 服务器直连连接（会话列表 / 消息 / prompt_async 用）。 */
    private var conn: ServerConnection? = null
    /** starburst-backend 地址与 APP token（镜像通道 / 用量统计 / 任务中心用）。 */
    private var backendUrl = ""
    private var backendToken = ""

    private val _uiState = MutableStateFlow(WorkbenchUiState(serverName = serverNameArg))
    val uiState: StateFlow<WorkbenchUiState> = _uiState.asStateFlow()

    /** 展开中的决策面板集合（多会话可同时展开，互不互斥）。 */
    private val _panels = MutableStateFlow<Map<String, DecisionPanelState>>(emptyMap())
    val panels: StateFlow<Map<String, DecisionPanelState>> = _panels.asStateFlow()

    /** 会话列表筛选维度。 */
    private val _filter = MutableStateFlow(WorkbenchFilter.All)
    val filter: StateFlow<WorkbenchFilter> = _filter.asStateFlow()

    /** 正在发送快捷回复的会话集合（按会话粒度并发，互不阻塞）。 */
    private val _sendingSessionIds = MutableStateFlow<Set<String>>(emptySet())
    val sendingSessionIds: StateFlow<Set<String>> = _sendingSessionIds.asStateFlow()

    /** 会话列表搜索关键词（标题 / 目录 / 模型）。 */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** 是否处于批量选择模式。 */
    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    /** 批量选择模式下的已选会话集合。 */
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    /** 下拉刷新进行中标记。 */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** 本机置顶会话集合（DataStore 持久化，服务器作用域）。 */
    private val _pinned = MutableStateFlow<Set<String>>(emptySet())
    val pinned: StateFlow<Set<String>> = _pinned.asStateFlow()

    /** 置顶集合的 DataStore key。 */
    private val pinnedKey = stringSetPreferencesKey("workbench_pinned_$serverId")

    /** 快捷回复模板（本机持久化，跨服务器共享）。 */
    private val _templates = MutableStateFlow<List<String>>(emptyList())
    val templates: StateFlow<List<String>> = _templates.asStateFlow()

    /** 快捷回复模板的 DataStore key（JSON 数组字符串）。 */
    private val templatesKey = stringPreferencesKey("workbench_quick_reply_templates")

    /** 各会话最近一次推送的失败信息（面板展示错误摘要 + 一键重试）。 */
    private val _sessionErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val sessionErrors: StateFlow<Map<String, String>> = _sessionErrors.asStateFlow()

    /** 预览懒加载中的会话集合（防并发重复加载）。 */
    private val previewLoading = mutableSetOf<String>()

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

        init {
        viewModelScope.launch {
            val server = serverRepository.servers.first().firstOrNull { it.id == serverId }
            val baseUrl = server?.url?.trimEnd('/') ?: serverUrlArg
            val username = server?.username ?: usernameArg
            val password = server?.password ?: passwordArg.ifEmpty { null }
            conn = connectionStateRepository.resolvedConnectionFor(serverId, baseUrl)
                ?: ServerConnection.from(baseUrl, username, password)
            backendUrl = server?.backendResolvedUrl.orEmpty()
            backendToken = server?.backendResolvedToken.orEmpty()
            // 后端可用时走镜像通道：状态/列表走后端增强接口（快照+事件+活跃度聚合，准确）。
            // token 为空（未配置/显式禁用后端）时保持直连服务器，避免用空 Bearer 请求镜像。
            if (backendUrl.isNotBlank() && backendToken.isNotBlank()) {
                conn = ServerConnection(
                    baseUrl = backendUrl.trimEnd('/') + StarBurstGateway.BACKEND_API_PREFIX,
                    authHeader = "Bearer $backendToken",
                )
            }
            if (conn == null) {
                _uiState.update { it.copy(loadingSessions = false, sessionsError = context.getString(R.string.workbench_error_resolve_conn)) }
                return@launch
            }

            refreshSessions()
            startPushStream()
        }
        // 本机置顶集合：DataStore 持久化，变更即同步到列表排序/卡片。
        viewModelScope.launch {
            dataStore.data.map { it[pinnedKey].orEmpty() }.collect { set ->
                _pinned.value = set
                _uiState.update { s ->
                    s.copy(sessions = s.sessions.map { it.copy(pinned = it.session.id in set) })
                }
            }
        }
        // 快捷回复模板：DataStore 持久化（JSON 数组），断点续集无需额外处理。
        viewModelScope.launch {
            dataStore.data.map { it[templatesKey] }.collect { raw ->
                _templates.value = raw?.let { decodeTemplates(it) }.orEmpty()
            }
        }
    }

    private fun decodeTemplates(raw: String): List<String> =
        runCatching { Json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())

    private fun encodeTemplates(list: List<String>): String =
        runCatching { Json.encodeToString(list) }.getOrDefault("[]")

    /** 推送流是否在线：在线时不轮询，断线期间由周期刷新兜底。 */
    @Volatile
    private var pushActive = false

    /** 界面可见性驱动的轮询任务；退后台时由 [launchWhileStarted] 自动挂起（耗电优化）。 */
    private var pollJob: Job? = null

    /** 由 WorkbenchScreen 传入 lifecycle；仅在界面 STARTED 期间轮询会话列表兜底。 */
    fun attachLifecycle(lifecycle: Lifecycle) {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launchWhileStarted(lifecycle) {
            while (true) {
                delay(SESSION_POLL_INTERVAL_MS)
                if (!pushActive) refreshSessions()
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
                    backendPushListener.eventFlow(backendUrl, backendToken)
                        // 每次（重）连推送前从后端兜底同步一次未读集合，补偿断线期间丢失的事件。
                        .onStart { resyncUnread() }
                        .collect { ev ->
                            pushActive = true
                            handlePushEvent(ev)
                        }
                    // 正常断开（WS 被服务端按 idle 掐断）不算故障：
                    // 退避时间回到基础值，避免空闲断流后重连延迟越拖越长。
                    backoffMs = 2_000L
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "push stream stopped: ${e.message}")
                }
                pushActive = false
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }

    private fun handlePushEvent(ev: PushSessionEvent) {
        // 未读状态优先走推送：后端把「有未读活动」的这些事件标记会话未读（口径与 isUnreadTriggerEvent
        // 一致），不再每 10s 轮询 /api/unread。正在查看的会话有新活动视为已读（任一端看过后全端清除）。
        if (isUnreadTriggerEventType(ev.eventType)) {
            if (ev.sessionId in _panels.value) {
                markSessionRead(ev.sessionId)
            } else {
                markSessionUnread(ev.sessionId)
            }
        }
        when (ev.eventType) {
            "session.status", "session.updated" -> {
                ev.status()?.let { applyPushedStatus(ev.sessionId, it) }
                refreshSessionsSoon()
            }
            "session.idle" -> {
                applyPushedStatus(ev.sessionId, SessionStatus.Idle)
                refreshSessionsSoon()
                refreshPreviewSoon(ev.sessionId)
            }
            "session.error", "session.failed" -> {
                val msg = ev.errorMessage() ?: context.getString(R.string.workbench_error_unknown)
                _sessionErrors.update { it + (ev.sessionId to msg) }
                refreshSessionsSoon()
                refreshPanelSoon(ev.sessionId)
            }
            "question.asked", "question.updated", "permission.asked", "permission.updated",
            "message.complete",
            -> {
                // 有新的成功活动发生即视作已恢复，清除之前的失败摘要。
                _sessionErrors.update { it - ev.sessionId }
                refreshSessionsSoon()
                refreshPanelSoon(ev.sessionId)
                refreshPreviewSoon(ev.sessionId)
            }
            "message.created", "message.updated",
            -> {
                _sessionErrors.update { it - ev.sessionId }
                refreshSessionsSoon()
                refreshPanelSoon(ev.sessionId)
            }
            else -> {}
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
        // 加单调守卫：聚合快照可能滞后，旧 idle 不得抢跑真实 busy/retry/question。
        // 否则父会话等待子会话（subagent）期间，滞后推送的 parent idle 会把
        // childBusyByParent 归并出来的「处理中」覆盖回空闲，造成列表显示空闲。
        val current = _uiState.value.sessions.firstOrNull { it.session.id == sessionId }?.status
        val applicable = when (current) {
            is SessionStatus.Busy, is SessionStatus.Question, is SessionStatus.Permission, is SessionStatus.Retry ->
                status !is SessionStatus.Idle
            else -> true
        }
        if (!applicable) return
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

    /** 拉取全量会话 + 状态 + 待决问题/授权，派生排序后刷新列表。 */
    private suspend fun refreshSessions() {
        val activeConn = conn ?: return
        try {
            val sessions = api.listSessions(activeConn)
            // 服务器 /question 与 /permission 都按 query 参数 directory 过滤，按会话目录分组聚合查询。
            val directories = sessions.asSequence()
                .map { it.directory }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()
            // 待决问题/授权按目录并发拉取；状态走后端聚合（镜像）或目录并发（直连上游）自适配。
            val pendingBySession = mutableMapOf<String, List<QuestionRequest>>()
            val pendingPermissionBySession = mutableMapOf<String, List<PermissionRequest>>()
            coroutineScope {
                directories.map { dir ->
                    async {
                        runCatching {
                            val questions = api.listPendingQuestions(activeConn, directory = dir)
                            val permissions = api.listPendingPermissions(activeConn, directory = dir)
                            questions to permissions
                        }.getOrElse { e ->
                            if (e is CancellationException) throw e
                            if (BuildConfig.DEBUG) Log.d(TAG, "Failed to load pending interactions for $dir: ${e.message}")
                            emptyList<QuestionRequest>() to emptyList<PermissionRequest>()
                        }
                    }
                }.awaitAll().forEach { (questions, permissions) ->
                    questions.groupBy { it.sessionId }.forEach { (sid, list) -> pendingBySession[sid] = list }
                    permissions.groupBy { it.sessionId }.forEach { (sid, list) -> pendingPermissionBySession[sid] = list }
                }
            }
            val statuses = api.listSessionStatusesForDirectories(activeConn, directories)
            // 补齐缺失的 busy/retry 子会话对象，保证父会话能归并子会话的处理中状态。
            val sessionsWithChildren = hydrateBusyChildren(activeConn, sessions, statuses)
            val items = buildWorkbenchSessions(sessionsWithChildren, statuses, pendingBySession, pendingPermissionBySession)
            // 未读不再随每次刷新轮询 /api/unread：保留当前本地已跟踪的未读集合（由推送标记/读后清除驱动）。
            val currentUnread = _uiState.value.sessions.asSequence().filter { it.unread }.map { it.session.id }.toSet()
            val previews = _uiState.value.sessions.associate { it.session.id to it.aiPreview }
            val pinnedSet = _pinned.value
            val errorSet = _sessionErrors.value
            val enriched = items.map {
                it.copy(
                    unread = it.session.id in currentUnread,
                    aiPreview = previews[it.session.id],
                    pinned = it.session.id in pinnedSet,
                    hasError = it.session.id in errorSet,
                )
            }
            _uiState.update { current ->
                current.copy(
                    sessions = enriched,
                    loadingSessions = false,
                    sessionsError = null,
                )
            }
            syncOpenPanels(enriched)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "refresh sessions failed: ${e.message}")
            _uiState.update { it.copy(loadingSessions = false, sessionsError = e.message ?: context.getString(R.string.workbench_error_sessions)) }
        }
    }

    /** 用最新会话数据同步已展开面板的标题/目录/状态/待决问题字段（消息内容在收到推送时单独刷新）。 */
    private fun syncOpenPanels(items: List<WorkbenchSession>) {
        val open = _panels.value
        if (open.isEmpty()) return
        val byId = items.associateBy { it.session.id }
        _panels.update { panels ->
            panels.mapValues { (sessionId, panel) ->
                val item = byId[sessionId] ?: return@mapValues panel
                panel.copy(
                    sessionTitle = item.session.title.orEmpty(),
                    sessionDirectory = item.session.directory,
                    sessionStatus = item.status,
                    questions = item.pendingQuestion?.questions.orEmpty(),
                    questionRequestId = item.pendingQuestion?.id,
                    permissions = item.pendingPermissions,
                )
            }
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
     * 子会话的忙/待决问题/待决授权归并到父会话，与现有 SessionListViewModel 的口径一致。
     */
    private fun buildWorkbenchSessions(
        sessions: List<Session>,
        statuses: Map<String, SessionStatus>,
        pendingBySession: Map<String, List<QuestionRequest>>,
        pendingPermissionBySession: Map<String, List<PermissionRequest>>,
    ): List<WorkbenchSession> {
        val childBusyByParent = mutableMapOf<String, SessionStatus>()
        val parentWithQuestion = mutableSetOf<String>()
        val parentWithPermission = mutableSetOf<String>()
        // 子会话的权限请求归并到父会话（授权面板在父会话上处理）。
        val permissionsBySessionId = pendingPermissionBySession.toMutableMap()
        for (child in sessions) {
            val parentId = child.parentId ?: continue
            if (child.id in pendingBySession) parentWithQuestion += parentId
            if (child.id in pendingPermissionBySession) parentWithPermission += parentId
            val childPermissions = pendingPermissionBySession[child.id].orEmpty()
            if (childPermissions.isNotEmpty()) {
                permissionsBySessionId[parentId] = permissionsBySessionId[parentId].orEmpty() + childPermissions
            }
            val childStatus = statuses[child.id] ?: continue
            if (childStatus is SessionStatus.Busy || childStatus is SessionStatus.Retry) {
                childBusyByParent[parentId] = childStatus
            }
        }
        return sessions
            .filter { it.parentId == null && !it.isArchived }
            .map { session ->
                val status = when {
                    session.id in pendingPermissionBySession || session.id in parentWithPermission -> SessionStatus.Permission
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
                    pendingPermissions = permissionsBySessionId[session.id].orEmpty(),
                )
            }
            .sortedWith(workbenchSessionComparator)
    }

    /** 会话排序：置顶 > 出错 > 提问中 > 处理中 > 空闲；同优先级内未读优先、最近更新优先，状态不变时顺序不跳动。 */
    private val workbenchSessionComparator: Comparator<WorkbenchSession> =
        compareBy<WorkbenchSession> { if (it.pinned) 0 else 1 }
            .thenBy { if (it.hasError) 0 else 1 }
            .thenBy { statusRank(it.status) }
            .thenByDescending { it.unread }
            .thenByDescending { it.session.time.updated }
            .thenByDescending { it.session.time.created }
            .thenByDescending { it.session.id }

    private fun statusRank(status: SessionStatus): Int = when (status) {
        is SessionStatus.Permission, is SessionStatus.Question -> 0
        is SessionStatus.Busy, is SessionStatus.Retry -> 1
        is SessionStatus.Idle -> 2
    }

    /** 展开 / 收起某会话的决策面板；展开时异步加载 AI 最近回复摘要与待决问题（可多会话同时展开）。 */
    fun togglePanel(sessionId: String) {
        val open = _panels.value
        if (sessionId in open) {
            _panels.value = open - sessionId
            return
        }
        _panels.value = open + (sessionId to DecisionPanelState(sessionId = sessionId, loading = true))
        markSessionRead(sessionId)
        viewModelScope.launch {
            val loaded = loadPanel(sessionId)
            _panels.update { it + (sessionId to loaded) }
        }
    }

    /** 收起全部已展开的决策面板。 */
    fun collapseAllPanels() {
        _panels.value = emptyMap()
    }

    /** 切换会话列表筛选维度。 */
    fun setFilter(f: WorkbenchFilter) {
        _filter.value = f
    }

    /** 重新加载某会话的决策面板内容（发送 / 回复 / 推送触发）。 */
    private fun refreshPanel(sessionId: String) {
        if (sessionId !in _panels.value) return
        viewModelScope.launch {
            val loaded = loadPanel(sessionId)
            _panels.update { if (sessionId in it) it + (sessionId to loaded) else it }
        }
    }

    /** 推送触发的面板刷新去抖：流式输出期间 message.updated 密集，合并为约 800ms 一次。 */
    private val panelRefreshAt = mutableMapOf<String, Long>()

    private fun refreshPanelSoon(sessionId: String) {
        val now = System.currentTimeMillis()
        if (now - (panelRefreshAt[sessionId] ?: 0L) < PANEL_REFRESH_DEBOUNCE_MS) return
        panelRefreshAt[sessionId] = now
        refreshPanel(sessionId)
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

    /** 推送命中未读触发事件时，把会话标记为本地未读（口径与后端 session_unread 一致）。 */
    private fun markSessionUnread(sessionId: String) {
        _uiState.update { current ->
            if (current.sessions.none { it.session.id == sessionId }) return@update current
            current.copy(sessions = current.sessions.map { item ->
                if (item.session.id == sessionId && !item.unread) item.copy(unread = true) else item
            })
        }
    }

    /** 从后端一次性拉取未读集合作为兜底（进工作台/推送重连时），不做周期轮询。 */
    private fun resyncUnread() {
        if (backendUrl.isBlank()) return
        viewModelScope.launch {
            val unread = runCatching { backendApi.listUnread(backendUrl, backendToken) }
                .getOrElse { e ->
                    if (e is CancellationException) throw e
                    if (BuildConfig.DEBUG) Log.d(TAG, "resync unread failed: ${e.message}")
                    return@launch
                }
            _uiState.update { state ->
                state.copy(sessions = state.sessions.map { item ->
                    item.copy(unread = item.session.id in unread)
                })
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
        val session = _uiState.value.sessions.firstOrNull { it.session.id == sessionId }
        val pending = session?.pendingQuestion
        val status = session?.status ?: SessionStatus.Idle
        val prev = _panels.value[sessionId]
        val (contextTokens, contextWindow) = sessionContextStats(messages, session?.session?.model?.id.orEmpty())
        return DecisionPanelState(
            sessionId = sessionId,
            aiSummary = buildAiSummary(messages),
            questions = pending?.questions.orEmpty(),
            questionRequestId = pending?.id,
            permissions = session?.pendingPermissions.orEmpty(),
            recentMessages = buildRecentMessages(messages),
            sessionTitle = session?.session?.title.orEmpty(),
            sessionDirectory = session?.session?.directory.orEmpty(),
            sessionStatus = status,
            loading = false,
            showAll = prev?.showAll ?: false,
            allMessages = prev?.allMessages,
            contextTokens = contextTokens,
            contextWindow = contextWindow,
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

    /** 取最近几轮有内容（文本或附件）的对话，最近一条 assistant 完整显示。 */
    private fun buildRecentMessages(messages: List<MessageWithParts>): List<PanelMessage> {
        val withText = messages.asReversed().mapNotNull { buildPanelMessage(it) }.take(PANEL_RECENT_COUNT)
        val lastAssistantIndex = withText.indexOfFirst { it.role == "assistant" }
        return withText.mapIndexed { index, msg ->
            if (msg.role == "assistant" && index == lastAssistantIndex) {
                msg.copy(full = true)
            } else {
                msg.copy(text = msg.text.take(PANEL_MESSAGE_CHAR_LIMIT))
            }
        }
    }

    /** 由一条消息构件面板行：提取文本 + 附件；无任何内容返回 null。 */
    private fun buildPanelMessage(mw: MessageWithParts): PanelMessage? {
        val text = mw.parts
            .filterIsInstance<Part.Text>()
            .filter { it.synthetic != true && it.ignored != true }
            .joinToString("\n") { it.text }
            .trim()
        val attachments = buildPanelAttachments(mw)
        if (text.isBlank() && attachments.isEmpty()) return null
        val role = if (mw.info is Message.Assistant) "assistant" else "user"
        return PanelMessage(role = role, text = text, attachments = attachments)
    }

    /** 提取一条消息里的非文本部件（图片 / 工具 / 文件）供面板展示。 */
    private fun buildPanelAttachments(mw: MessageWithParts): List<PanelAttachment> {
        val out = mutableListOf<PanelAttachment>()
        mw.parts.forEach { part ->
            when (part) {
                is Part.File -> {
                    if (part.mime.startsWith("image/") && !part.url.isNullOrBlank()) {
                        out += PanelAttachment("image", part.filename ?: "image", detail = part.mime, url = part.url)
                    } else {
                        out += PanelAttachment("file", part.filename ?: part.mime, detail = part.mime)
                    }
                }
                is Part.Tool -> {
                    val detail = when (val s = part.state) {
                        is ToolState.Running -> s.title ?: "running"
                        is ToolState.Completed -> "completed"
                        is ToolState.Error -> "error"
                        is ToolState.Pending -> "pending"
                    }
                    out += PanelAttachment("tool", part.tool, detail = detail)
                }
                else -> {}
            }
        }
        return out
    }

    /** 最近一轮模型上下文占用（token）+ 模型窗口上限；窗口未知时上限为 0。 */
    private fun sessionContextStats(messages: List<MessageWithParts>, modelId: String): Pair<Int, Int> {
        val lastWithOutput = messages.lastOrNull { mw ->
            mw.info is Message.Assistant && mw.parts.any { it is Part.StepFinish && (it.tokens?.output ?: 0) > 0 }
        }
        val tokens = lastWithOutput?.parts
            ?.filterIsInstance<Part.StepFinish>()
            ?.lastOrNull { (it.tokens?.output ?: 0) > 0 }
            ?.tokens
        val used = tokens?.let { it.total ?: (it.input + it.output) } ?: 0
        return used to modelContextWindow(modelId)
    }

    /** 常见模型上下文窗口（token）兜底映射；未知模型返回 0（面板不显示占比）。 */
    private fun modelContextWindow(modelId: String): Int {
        val id = modelId.lowercase()
        return when {
            "gemini" in id -> 1_048_576
            "claude" in id -> 200_000
            "gpt-5" in id || "gpt-4o" in id || "gpt-4.1" in id || "o3" in id || "o4" in id -> 128_000
            "gpt-4" in id || "gpt-3.5" in id -> 16_384
            "deepseek" in id || "qwen" in id || "glm" in id || "llama" in id || "kimi" in id -> 128_000
            "mistral" in id -> 32_768
            else -> 0
        }
    }

    /**
     * 快捷回复发送：复用现有发送链路 POST {conn.baseUrl}/session/{id}/prompt_async。
     * 按会话粒度并发门控（同一会话防重复提交，不同会话可同时发送），成功后把该会话乐观置为 Busy，并刷新决策面板。
     */
    fun sendQuickReply(sessionId: String, text: String, onResult: (Boolean) -> Unit = {}) {
        val trimmed = text.trim()
        val activeConn = conn ?: return
        val session = _uiState.value.sessions.firstOrNull { it.session.id == sessionId }?.session ?: return
        if (trimmed.isEmpty() || sessionId in _sendingSessionIds.value) return
        _sendingSessionIds.update { it + sessionId }
        viewModelScope.launch {
            try {
                api.sendPrompt(
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
                refreshPanel(sessionId)
                if (BuildConfig.DEBUG) Log.d(TAG, "Quick reply sent to session $sessionId")
                onResult(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Quick reply failed", e)
                onResult(false)
            } finally {
                _sendingSessionIds.update { it - sessionId }
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
                    sessionId = requestSessionId,
                    requestId = requestId,
                    answers = answers,
                    directory = session?.directory?.takeIf { it.isNotBlank() },
                )
                if (success) {
                    refreshPanel(requestSessionId)
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
                if (_panels.value.containsKey(sessionId)) {
                    _panels.update { it - sessionId }
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Deleted session $sessionId")
            }
            onResult(ok)
        }
    }

    // ============ 搜索 / 筛选 / 批量 / 重命名 / 置顶 / 刷新 ============

    /** 更新会话搜索关键词（标题 / 目录 / 模型）。 */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** 进入 / 退出批量选择模式（切换时清空已选）。 */
    fun toggleSelectionMode() {
        _selectionMode.value = !_selectionMode.value
        _selected.value = emptySet()
    }

    /** 勾选 / 取消勾选单个会话。 */
    fun toggleSelected(sessionId: String) {
        _selected.update { if (sessionId in it) it - sessionId else it + sessionId }
    }

    /** 全选当前可见会话。 */
    fun selectAllVisible(ids: Set<String>) {
        _selected.value = ids
    }

    /** 批量删除已选会话（并发请求，完成后退出选择模式）。 */
    fun deleteSelected(onResult: (Boolean) -> Unit = {}) {
        val activeConn = conn ?: return
        val ids = _selected.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val results = coroutineScope {
                ids.map { id -> async { runCatching { api.deleteSession(activeConn, id) }.getOrDefault(false) } }
                    .awaitAll()
            }
            val deletedIds = ids.filterIndexed { i, _ -> results[i] }.toSet()
            if (deletedIds.isNotEmpty()) {
                _uiState.update { s ->
                    s.copy(sessions = s.sessions.filterNot { it.session.id in deletedIds })
                }
                _panels.update { panels -> panels.filterKeys { it !in deletedIds } }
            }
            _selectionMode.value = false
            _selected.value = emptySet()
            onResult(deletedIds.isNotEmpty())
        }
    }

    /** 批量标记已选会话为已读（后端镜像通道 + 本地未读集合）。 */
    fun markSelectedRead() {
        val ids = _selected.value
        if (ids.isEmpty()) return
        _uiState.update { s ->
            s.copy(sessions = s.sessions.map { if (it.session.id in ids) it.copy(unread = false) else it })
        }
        if (backendUrl.isNotBlank() && backendToken.isNotBlank()) {
            viewModelScope.launch {
                ids.forEach { id ->
                    runCatching { backendApi.markSessionRead(backendUrl, backendToken, id) }
                }
            }
        }
        _selectionMode.value = false
        _selected.value = emptySet()
    }

    /** 重命名会话（PATCH /session/{id} 更新 title）。 */
    fun renameSession(sessionId: String, title: String, onResult: (Boolean) -> Unit = {}) {
        val activeConn = conn ?: return
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                val updated = api.updateSession(activeConn, sessionId, title = trimmed)
                _uiState.update { s ->
                    s.copy(sessions = s.sessions.map { if (it.session.id == sessionId) it.copy(session = updated) else it })
                }
                syncOpenPanels(_uiState.value.sessions)
                onResult(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Rename session $sessionId failed", e)
                onResult(false)
            }
        }
    }

    /** 置顶 / 取消置顶会话（本机持久化，仅影响本工作台排序）。 */
    fun togglePin(sessionId: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                val cur = prefs[pinnedKey].orEmpty()
                prefs[pinnedKey] = if (sessionId in cur) cur - sessionId else cur + sessionId
            }
        }
    }

    // ============ 快捷回复模板（D2） ============

    /** 把当前草稿存为快捷回复模板（已存在则忽略）。 */
    fun addTemplate(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            dataStore.edit { prefs ->
                val cur = decodeTemplates(prefs[templatesKey].orEmpty())
                if (t !in cur) prefs[templatesKey] = encodeTemplates(cur + t)
            }
        }
    }

    /** 删除一条快捷回复模板。 */
    fun removeTemplate(text: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                val cur = decodeTemplates(prefs[templatesKey].orEmpty())
                prefs[templatesKey] = encodeTemplates(cur.filterNot { it == text })
            }
        }
    }

    // ============ 面板「查看全部消息」（B2）与失败重试（B3） ============

    /** 展开 / 收起面板里的完整消息列表；首次展开时异步拉全量消息。 */
    fun toggleAllMessages(sessionId: String) {
        val current = _panels.value[sessionId] ?: return
        val show = !current.showAll
        _panels.value = _panels.value + (sessionId to current.copy(showAll = show))
        if (show) loadAllMessages(sessionId)
    }

    private fun loadAllMessages(sessionId: String) {
        viewModelScope.launch {
            val activeConn = conn ?: return@launch
            val messages = runCatching {
                api.listMessages(activeConn, sessionId, limit = PANEL_ALL_LIMIT)
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                if (BuildConfig.DEBUG) Log.d(TAG, "load all messages failed: ${e.message}")
                emptyList()
            }
            val all = buildAllMessages(messages)
            _panels.update { ps ->
                val cur = ps[sessionId] ?: return@update ps
                ps + (sessionId to cur.copy(allMessages = all))
            }
        }
    }

    /** 完整消息列表（每条完整显示，不截断）。 */
    private fun buildAllMessages(messages: List<MessageWithParts>): List<PanelMessage> =
        messages.asReversed().mapNotNull { buildPanelMessage(it)?.copy(full = true) }

    /** 失败一键重试：abort 掉可能的自动重试后，重发该会话最后一条有文本的用户消息。 */
    fun retrySession(sessionId: String, onResult: (Boolean) -> Unit = {}) {
        val activeConn = conn ?: return
        val session = _uiState.value.sessions.firstOrNull { it.session.id == sessionId }?.session ?: return
        if (sessionId in _sendingSessionIds.value) return
        viewModelScope.launch {
            try {
                val messages = runCatching { api.listMessages(activeConn, sessionId, limit = PANEL_MESSAGE_LIMIT) }.getOrElse { emptyList() }
                val lastUser = messages.lastOrNull { mw ->
                    mw.info is Message.User && mw.parts.any {
                        it is Part.Text && it.synthetic != true && it.ignored != true && it.text.isNotBlank()
                    }
                }
                val text = lastUser?.parts
                    ?.filterIsInstance<Part.Text>()
                    ?.filter { it.synthetic != true && it.ignored != true }
                    ?.joinToString("\n") { it.text }
                    ?.trim()
                if (text.isNullOrBlank()) { onResult(false); return@launch }
                runCatching { api.abortSession(activeConn, sessionId) }
                _sendingSessionIds.update { it + sessionId }
                api.sendPrompt(
                    conn = activeConn,
                    sessionId = sessionId,
                    messageId = MessageIdGenerator.next(),
                    parts = listOf(PromptPart(type = "text", text = text)),
                    directory = session.directory,
                )
                _uiState.update { s ->
                    s.copy(sessions = s.sessions.map { if (it.session.id == sessionId) it.copy(status = SessionStatus.Busy) else it })
                }
                _sessionErrors.update { it - sessionId }
                refreshPanel(sessionId)
                if (BuildConfig.DEBUG) Log.d(TAG, "Retried session $sessionId")
                onResult(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Retry session $sessionId failed", e)
                onResult(false)
            } finally {
                _sendingSessionIds.update { it - sessionId }
            }
        }
    }

    /** 面板内快捷处理权限请求（once / always / reject）。 */
    fun replyToPermission(sessionId: String, requestId: String, reply: String, onResult: (Boolean) -> Unit = {}) {
        val activeConn = conn ?: return
        val session = _uiState.value.sessions.firstOrNull { it.session.id == sessionId }?.session ?: return
        viewModelScope.launch {
            try {
                val ok = api.replyToPermission(
                    conn = activeConn,
                    sessionId = sessionId,
                    requestId = requestId,
                    reply = reply,
                    directory = session.directory,
                )
                if (ok) {
                    refreshSessionsSoon()
                    refreshPanel(sessionId)
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Permission $requestId -> $reply (ok=$ok)")
                onResult(ok)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Reply permission $requestId failed", e)
                onResult(false)
            }
        }
    }

    /** 下拉刷新：手动触发一次全量刷新（推送断线兜底）。 */
    fun manualRefresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            refreshSessions()
            _refreshing.value = false
        }
    }

    /** 卡片懒加载「最后消息预览」：仅加载屏幕上可见的会话，已加载或加载中则跳过。 */
    fun ensurePreview(sessionId: String) {
        val current = _uiState.value.sessions.firstOrNull { it.session.id == sessionId }
        if (current?.aiPreview != null) return
        if (sessionId in previewLoading) return
        previewLoading += sessionId
        viewModelScope.launch {
            val preview = loadPreview(sessionId)
            previewLoading -= sessionId
            if (preview != null) {
                _uiState.update { s ->
                    s.copy(sessions = s.sessions.map {
                        if (it.session.id == sessionId && it.aiPreview == null) it.copy(aiPreview = preview) else it
                    })
                }
            }
        }
    }

    /** 推送驱动的预览刷新（完成类事件去抖，只对已加载过预览的会话生效）。 */
    private val previewRefreshAt = mutableMapOf<String, Long>()

    private fun refreshPreviewSoon(sessionId: String) {
        val now = System.currentTimeMillis()
        if (now - (previewRefreshAt[sessionId] ?: 0L) < PANEL_REFRESH_DEBOUNCE_MS) return
        previewRefreshAt[sessionId] = now
        viewModelScope.launch {
            val preview = loadPreview(sessionId)
            if (preview != null) {
                _uiState.update { s ->
                    s.copy(sessions = s.sessions.map {
                        if (it.session.id == sessionId) it.copy(aiPreview = preview) else it
                    })
                }
            }
        }
    }

    private suspend fun loadPreview(sessionId: String): String? {
        val activeConn = conn ?: return null
        val messages = runCatching {
            api.listMessages(activeConn, sessionId, limit = PREVIEW_MESSAGE_LIMIT)
        }.getOrElse { e ->
            if (e is CancellationException) throw e
            if (BuildConfig.DEBUG) Log.d(TAG, "load preview failed: ${e.message}")
            return null
        }
        return buildPreview(messages)
    }

    /** 取最近一条有文本内容的消息（任一角色），压成单行预览。 */
    private fun buildPreview(messages: List<MessageWithParts>): String? {
        val last = messages.asReversed().mapNotNull { mw ->
            val text = mw.parts
                .filterIsInstance<Part.Text>()
                .filter { it.synthetic != true && it.ignored != true }
                .joinToString(" ") { it.text }
                .trim()
            text.takeIf { it.isNotBlank() }
        }.firstOrNull() ?: return null
        return last.replace(Regex("\\s+"), " ").take(PREVIEW_CHAR_LIMIT)
    }
}

/** 状态类事件刷新的去抖窗口（毫秒）。 */
private const val STATUS_REFRESH_DEBOUNCE_MS = 2_000L
/** 推送触发决策面板刷新的去抖窗口（毫秒）。 */
private const val PANEL_REFRESH_DEBOUNCE_MS = 800L
