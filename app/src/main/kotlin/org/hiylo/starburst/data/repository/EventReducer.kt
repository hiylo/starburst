/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : EventReducer.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.repository

import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.BuildConfig
import org.hiylo.starburst.data.search.MessageFtsIndex
import org.hiylo.starburst.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EventReducer"
internal const val MAX_PENDING_DELTA_KEYS = 128
internal const val MAX_PENDING_DELTA_CHARS = 65_536

/** callId → messageId 索引容量上限：超过后驱逐最旧条目，防止长时间运行无界增长。 */
internal const val MAX_CALL_ID_INDEX = 512

/** 流式 delta 累积 flush 间隔（毫秒），与 UI 的节流采样对齐。 */
private const val DELTA_FLUSH_INTERVAL_MS = 50L

internal fun compactSessionForCache(session: Session): Session = session.copy(
    summary = session.summary?.copy(diffs = null),
    permission = null,
)

internal data class PendingDeltaKey(
    val sessionId: String,
    val messageId: String,
    val partId: String,
)

data class DirectoryScope(val serverId: String, val directory: String, val workspaceId: String? = null)

enum class PromptDeliveryState { ADMITTED, PROMOTED }
data class PromptDeliveryInfo(val sessionId: String, val state: PromptDeliveryState)
data class WorkspaceScope(val serverId: String, val workspaceId: String)

/**
 * Event Reducer - processes SSE events and updates app state
 * 
 * This is the central state management for the app.
 * All SSE events flow through here and mutate the reactive state.
 * 
 * Supports multiple servers simultaneously. Session UUIDs are globally unique,
 * so all data maps are keyed by sessionId. A separate serverId→sessionIds map
 * tracks which sessions belong to which server for per-server cleanup.
 * 
 * Similar to the event-reducer.ts in the WebUI.
 */
@Singleton
class EventReducer @Inject constructor(
    private val messageFtsIndex: MessageFtsIndex,
) {
    /** 用于异步写入全文索引，避免阻塞事件处理。 */
    private val indexScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 流式 delta 累积缓冲：把高频 text delta 合并后定期 flush，避免每个 delta 都复制整段已累计文本（O(n²)）。 */
    internal val deltaAccumulator = ConcurrentHashMap<PendingDeltaKey, StringBuilder>()

    init {
        // 定期 flush 累积的 delta（与 UI 的 50ms 节流采样对齐）。
        indexScope.launch {
            while (isActive) {
                delay(DELTA_FLUSH_INTERVAL_MS)
                flushAccumulatedDeltas()
            }
        }
    }

    private val pendingLock = Any()
    private var pendingRevision = 0L
    internal val deltaLock = Any()
    internal val pendingDeltas = LinkedHashMap<PendingDeltaKey, StringBuilder>()
    internal val removedMessageLock = Any()
    internal val removedMessageSessions = mutableMapOf<String, String>()

    /**
     * callId → messageId 索引：shell/tool 结束类事件（如 next.shell.ended）不带 messageId，
     * 用该索引 O(1) 定位所属消息，避免每次全库扫描所有会话的所有 parts。
     * 带容量上限，防止长时间运行累积。
     */
    internal val callIdIndex = ConcurrentHashMap<String, String>()
    internal val callIndexLock = Any()

    // ============ State ============

    /** Maps serverId → set of sessionIds belonging to that server */
    private val _serverSessions = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val serverSessions: StateFlow<Map<String, Set<String>>> = _serverSessions.asStateFlow()

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    internal val _sessionStatuses = MutableStateFlow<Map<String, SessionStatus>>(emptyMap())
    val sessionStatuses: StateFlow<Map<String, SessionStatus>> = _sessionStatuses.asStateFlow()

    /** Sessions that finished processing (Busy -> Idle) but have not yet been confirmed/opened by the user.
     *  Maps sessionId -> first completion timestamp, used for stable pin ordering. */
    private val _unconfirmedCompletedSessions = MutableStateFlow<Map<String, Long>>(emptyMap())
    val unconfirmedCompletedSessions: StateFlow<Map<String, Long>> = _unconfirmedCompletedSessions.asStateFlow()

    internal val _messages = MutableStateFlow<Map<String, List<Message>>>(emptyMap()) // sessionId -> messages
    val messages: StateFlow<Map<String, List<Message>>> = _messages.asStateFlow()

    /** Last time the user sent a message in each session (sessionId -> epoch millis).
     *  Tracked separately from [messages] so session lists can sort by it without
     *  recomposing on every streaming part update. */
    internal val _lastUserMessageAt = MutableStateFlow<Map<String, Long>>(emptyMap())
    val lastUserMessageAt: StateFlow<Map<String, Long>> = _lastUserMessageAt.asStateFlow()

    internal val _parts = MutableStateFlow<Map<String, List<Part>>>(emptyMap()) // messageId -> parts
    val parts: StateFlow<Map<String, List<Part>>> = _parts.asStateFlow()

    private val _sessionDiffs = MutableStateFlow<Map<String, List<FileDiff>>>(emptyMap())
    val sessionDiffs: StateFlow<Map<String, List<FileDiff>>> = _sessionDiffs.asStateFlow()

    private val _sessionErrors = MutableStateFlow<Map<String, Message.Assistant.ErrorInfo>>(emptyMap())
    val sessionErrors: StateFlow<Map<String, Message.Assistant.ErrorInfo>> = _sessionErrors.asStateFlow()

    private val _pendingInteractions = MutableStateFlow<List<PendingInteraction>>(emptyList())
    val pendingInteractions: StateFlow<List<PendingInteraction>> = _pendingInteractions.asStateFlow()

    private val _todos = MutableStateFlow<Map<String, List<SseEvent.TodoUpdated.Todo>>>(emptyMap())
    val todos: StateFlow<Map<String, List<SseEvent.TodoUpdated.Todo>>> = _todos.asStateFlow()

    private val _vcsBranches = MutableStateFlow<Map<DirectoryScope, String?>>(emptyMap())
    val vcsBranches: StateFlow<Map<DirectoryScope, String?>> = _vcsBranches.asStateFlow()

    private val _projectInfo = MutableStateFlow<Map<DirectoryScope, Project>>(emptyMap())
    val projectInfo: StateFlow<Map<DirectoryScope, Project>> = _projectInfo.asStateFlow()

    internal val _promptDeliveries = MutableStateFlow<Map<String, PromptDeliveryInfo>>(emptyMap())
    val promptDeliveries: StateFlow<Map<String, PromptDeliveryInfo>> = _promptDeliveries.asStateFlow()

    private val _workspaceStatuses = MutableStateFlow<Map<WorkspaceScope, String>>(emptyMap())
    val workspaceStatuses: StateFlow<Map<WorkspaceScope, String>> = _workspaceStatuses.asStateFlow()

    // ============ Event Processing ============

    /**
     * Process an SSE event and update state.
     * @param event The SSE event to process
     * @param serverId The server this event came from (used for session tracking)
     */
    fun processEvent(event: SseEvent, serverId: String, directory: String? = null, workspaceId: String? = null) {
        when (event) {
            is SseEvent.ServerConnected -> handleServerConnected()
            is SseEvent.ServerHeartbeat -> { /* No-op */ }
            is SseEvent.ServerInstanceDisposed -> handleServerInstanceDisposed(event, serverId)
            is SseEvent.GlobalDisposed -> clearTransientForServer(serverId)
            is SseEvent.WorkspaceStatus -> _workspaceStatuses.update {
                it + (WorkspaceScope(serverId, event.workspaceId) to event.status)
            }
            is SseEvent.WorkspaceReady -> event.workspaceId?.let { workspaceId ->
                _workspaceStatuses.update { it + (WorkspaceScope(serverId, workspaceId) to "connected") }
            }
            is SseEvent.WorkspaceFailed -> event.workspaceId?.let { workspaceId ->
                _workspaceStatuses.update { it + (WorkspaceScope(serverId, workspaceId) to "error") }
            }
            is SseEvent.WorktreeReady, is SseEvent.WorktreeFailed -> Unit

            is SseEvent.SessionCreated -> handleSessionCreated(event, serverId)
            is SseEvent.SessionUpdated -> handleSessionUpdated(event, serverId)
            is SseEvent.SessionDeleted -> handleSessionDeleted(event)
            is SseEvent.SessionStatus -> handleSessionStatus(event, serverId)
            is SseEvent.SessionIdle -> handleSessionIdle(event, serverId)
            is SseEvent.SessionCompacted -> Unit
            is SseEvent.SessionDiff -> handleSessionDiff(event)
            is SseEvent.SessionError -> handleSessionError(event)
            is SseEvent.PromptAdmitted -> _promptDeliveries.update {
                it + (event.messageId to PromptDeliveryInfo(event.sessionId, PromptDeliveryState.ADMITTED))
            }
            is SseEvent.Prompted -> handleNextPrompted(event, serverId)
            is SseEvent.NextStepStarted -> handleNextStepStarted(event, serverId)
            is SseEvent.NextStepEnded -> handleNextStepEnded(event)
            is SseEvent.NextStepFailed -> handleNextStepFailed(event)
            is SseEvent.NextAgentSwitched -> updateMessage(event.sessionId, event.messageId) { message ->
                when (message) {
                    is Message.User -> message.copy(agent = event.agent)
                    is Message.Assistant -> message.copy(agent = event.agent)
                }
            }
            is SseEvent.NextModelSwitched -> updateMessage(event.sessionId, event.messageId) { message ->
                val model = event.model.jsonObject
                when (message) {
                    is Message.User -> message.copy(
                        model = Message.User.Model(
                            model["providerID"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            model["modelID"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        ),
                        variant = model["variant"]?.jsonPrimitive?.contentOrNull,
                    )
                    is Message.Assistant -> message.copy(
                        providerId = model["providerID"]?.jsonPrimitive?.contentOrNull,
                        modelId = model["modelID"]?.jsonPrimitive?.contentOrNull,
                        variant = model["variant"]?.jsonPrimitive?.contentOrNull,
                    )
                }
            }
            is SseEvent.NextContextUpdated -> handleMessagePartUpdated(SseEvent.MessagePartUpdated(Part.Text(
                "${event.messageId}-context", event.sessionId, event.messageId, event.text,
                time = Part.Text.Time(event.timestamp, event.timestamp),
            )))
            is SseEvent.NextSynthetic -> handleMessagePartUpdated(SseEvent.MessagePartUpdated(Part.Text(
                "${event.messageId}-synthetic", event.sessionId, event.messageId, event.text, synthetic = true,
                time = Part.Text.Time(event.timestamp, event.timestamp),
            )))
            is SseEvent.NextShellStarted -> handleNextShellStarted(event)
            is SseEvent.NextShellEnded -> handleNextShellEnded(event)
            is SseEvent.NextTextStarted -> handleMessagePartFinal(SseEvent.MessagePartUpdated(
                Part.Text(event.textId, event.sessionId, event.messageId, time = Part.Text.Time(event.timestamp)),
            ))
            is SseEvent.NextTextDelta -> handleMessagePartDelta(SseEvent.MessagePartDelta(
                event.sessionId, event.messageId, event.textId, "text", event.delta,
            ))
            is SseEvent.NextTextEnded -> handleMessagePartFinal(SseEvent.MessagePartUpdated(
                Part.Text(event.textId, event.sessionId, event.messageId, event.text, time = Part.Text.Time(event.timestamp, event.timestamp)),
            ))
            is SseEvent.NextReasoningStarted -> handleMessagePartFinal(SseEvent.MessagePartUpdated(
                Part.Reasoning(event.reasoningId, event.sessionId, event.messageId, time = Part.Reasoning.Time(event.timestamp)),
            ))
            is SseEvent.NextReasoningDelta -> handleMessagePartDelta(SseEvent.MessagePartDelta(
                event.sessionId, event.messageId, event.reasoningId, "text", event.delta,
            ))
            is SseEvent.NextReasoningEnded -> handleMessagePartFinal(SseEvent.MessagePartUpdated(
                Part.Reasoning(event.reasoningId, event.sessionId, event.messageId, event.text, time = Part.Reasoning.Time(event.timestamp, event.timestamp)),
            ))
            is SseEvent.NextToolInputStarted -> handleNextToolInputStarted(event)
            is SseEvent.NextToolInputDelta -> handleNextToolInputDelta(event)
            is SseEvent.NextToolInputEnded -> handleNextToolInputEnded(event)
            is SseEvent.NextToolCalled -> handleNextToolCalled(event)
            is SseEvent.NextToolProgress -> handleNextToolProgress(event)
            is SseEvent.NextToolSuccess -> handleNextToolSuccess(event)
            is SseEvent.NextToolFailed -> handleNextToolFailed(event)

            is SseEvent.MessageUpdated -> handleMessageUpdated(event)
            is SseEvent.MessageRemoved -> handleMessageRemoved(event)

            is SseEvent.MessagePartUpdated -> handleMessagePartUpdated(event)
            is SseEvent.MessagePartDelta -> handleMessagePartDelta(event)
            is SseEvent.MessagePartRemoved -> handleMessagePartRemoved(event)

            is SseEvent.PermissionAsked -> handlePermissionAsked(event, serverId)
            is SseEvent.PermissionReplied -> handlePermissionReplied(event)

            is SseEvent.QuestionAsked -> handleQuestionAsked(event, serverId)
            is SseEvent.QuestionReplied -> handleQuestionReplied(event)
            is SseEvent.QuestionRejected -> handleQuestionRejected(event)

            is SseEvent.TodoUpdated -> handleTodoUpdated(event)
            is SseEvent.VcsBranchUpdated -> handleVcsBranchUpdated(event, serverId, directory, workspaceId)
            is SseEvent.LspUpdated -> { /* LSP events not needed in mobile */ }
            is SseEvent.ProjectUpdated -> handleProjectUpdated(event, serverId, directory, workspaceId)
        }
    }

    // ============ Server Events ============

    private fun handleServerConnected() {
        if (BuildConfig.DEBUG) Log.d(TAG, "Server connected")
    }

    private fun handleServerInstanceDisposed(event: SseEvent.ServerInstanceDisposed, serverId: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Server instance disposed")
        _vcsBranches.update { current -> current.filterKeys { it.serverId != serverId || it.directory != event.directory } }
        _projectInfo.update { current -> current.filterKeys { it.serverId != serverId || it.directory != event.directory } }
    }

    // ============ Session Events ============

    private fun handleSessionCreated(event: SseEvent.SessionCreated, serverId: String) {
        trackSession(serverId, event.info.id)
        _sessions.update { current ->
            (current.filterNot { it.id == event.info.id } + event.info)
                .sortedByDescending { it.time.updated }
        }
        _sessionStatuses.update { current ->
            if (event.info.id in current) current else current + (event.info.id to SessionStatus.Idle)
        }
    }

    // Next* 流式生命周期（prompt/step/shell/tool）处理见 EventReducerStreamingExt.kt。

    private fun handleSessionUpdated(event: SseEvent.SessionUpdated, serverId: String) {
        upsertSession(serverId, event.info)
    }

    fun upsertSession(serverId: String, session: Session) {
        val compacted = compactSessionForCache(session)
        trackSession(serverId, compacted.id)
        _sessions.update { current ->
            val existingIndex = current.indexOfFirst { it.id == compacted.id }
            val updated = if (existingIndex >= 0) {
                val existing = current[existingIndex]
                if (existing.time.updated > compacted.time.updated) return@update current
                current.toMutableList().apply { set(existingIndex, compacted) }
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "Session ${compacted.id} not found, upserting (title=${compacted.title})")
                current + compacted
            }
            updated.sortedByDescending { it.time.updated }
        }
    }

    /** Register a session as belonging to a server */
    internal fun trackSession(serverId: String, sessionId: String) {
        _serverSessions.update { current ->
            val existing = current[serverId] ?: emptySet()
            current + (serverId to (existing + sessionId))
        }
    }

    private fun handleSessionDeleted(event: SseEvent.SessionDeleted) {
        val sessionId = event.info.id
        removeSession(sessionId)
    }

    /**
     * Removes a session from all in-memory state. Used both by the SSE SessionDeleted
     * handler and by callers that delete a session via the HTTP API, so the row does
     * not linger in the session list after a successful delete.
     */
    fun removeSession(sessionId: String) {
        val messageIds = _messages.value[sessionId].orEmpty().map { it.id }.toSet()
        _serverSessions.update { current ->
            current.mapValues { (_, sessionIds) -> sessionIds - sessionId }
                .filterValues { it.isNotEmpty() }
        }
        _sessions.update { it.filter { session -> session.id != sessionId } }
        _sessionStatuses.update { it - sessionId }
        _messages.update { it - sessionId }
        _parts.update { current -> current.filterKeys { it !in messageIds } }
        _sessionDiffs.update { it - sessionId }
        _sessionErrors.update { it - sessionId }
        _unconfirmedCompletedSessions.update { it - sessionId }
        _lastUserMessageAt.update { it - sessionId }
        removePendingForSessions(setOf(sessionId))
        _todos.update { it - sessionId }
        synchronized(deltaLock) {
            pendingDeltas.keys.removeAll { it.sessionId == sessionId }
            deltaAccumulator.keys.removeAll { it.sessionId == sessionId }
        }
        synchronized(removedMessageLock) {
            removedMessageSessions.entries.removeAll { it.value == sessionId }
        }
    }

    private fun handleSessionStatus(event: SseEvent.SessionStatus, serverId: String) {
        trackSession(serverId, event.sessionId)
        val previous = _sessionStatuses.value[event.sessionId]
        _sessionStatuses.update { it + (event.sessionId to event.status) }
        if (event.status is SessionStatus.Busy) {
            _sessionErrors.update { it - event.sessionId }
        } else if (event.status is SessionStatus.Idle && previous is SessionStatus.Busy) {
            // Finished processing — mark as unconfirmed so the session list can pin it.
            markUnconfirmedCompleted(event.sessionId)
        }
    }

    private fun handleSessionIdle(event: SseEvent.SessionIdle, serverId: String) {
        trackSession(serverId, event.sessionId)
        val previous = _sessionStatuses.value[event.sessionId]
        _sessionStatuses.update { it + (event.sessionId to SessionStatus.Idle) }
        if (previous is SessionStatus.Busy) {
            markUnconfirmedCompleted(event.sessionId)
        }
    }

    /** Marks a session as confirmed once the user has opened/acknowledged it. */
    fun confirmSession(sessionId: String) {
        _unconfirmedCompletedSessions.update { it - sessionId }
    }

    /** Records a Busy -> Idle completion. Keeps the first completion timestamp for stable ordering. */
    private fun markUnconfirmedCompleted(sessionId: String) {
        _unconfirmedCompletedSessions.update { current ->
            if (sessionId in current) current else current + (sessionId to System.currentTimeMillis())
        }
    }

    private fun handleSessionDiff(event: SseEvent.SessionDiff) {
        _sessionDiffs.update { it + (event.sessionId to event.diff) }
    }

    /** 主动写入某会话的文件变更列表（REST /session/{id}/diff 拉取结果），供「查看变更」初始状态。 */
    fun setSessionDiffs(sessionId: String, diffs: List<FileDiff>) {
        _sessionDiffs.update { it + (sessionId to diffs) }
    }

    private fun handleSessionError(event: SseEvent.SessionError) {
        Log.e(TAG, "Session ${event.sessionId} error: ${event.error.message}")
        event.sessionId?.let { sessionId ->
            _sessionErrors.update { it + (sessionId to event.error) }
            _sessionStatuses.update { it + (sessionId to SessionStatus.Idle) }
        }
    }

    // 消息/part 归并与流式 delta 缓冲见 EventReducerMessageExt.kt。

    // ============ Permission Events ============

    private fun handlePermissionAsked(event: SseEvent.PermissionAsked, serverId: String) {
        trackSession(serverId, event.sessionId)
        upsertPending(PendingInteraction.Permission(event))
    }

    private fun handlePermissionReplied(event: SseEvent.PermissionReplied) {
        synchronized(pendingLock) {
            removePending(PendingInteraction.Permission::class.java, event.sessionId, event.requestId)
            pendingRevision++
        }
    }

    // ============ Question Events ============

    private fun handleQuestionAsked(event: SseEvent.QuestionAsked, serverId: String) {
        trackSession(serverId, event.sessionId)
        upsertPending(PendingInteraction.Question(event))
        Log.i(
            TAG,
            "Question pending: session=${event.sessionId} request=${event.id} " +
                "questions=${event.questions.size} pending=${_pendingInteractions.value.size}",
        )
    }

    private fun handleQuestionReplied(event: SseEvent.QuestionReplied) {
        synchronized(pendingLock) {
            removePending(PendingInteraction.Question::class.java, event.sessionId, event.requestId)
            pendingRevision++
            Log.i(
                TAG,
                "Question replied: session=${event.sessionId} request=${event.requestId} " +
                    "pending=${_pendingInteractions.value.size}",
            )
        }
    }

    private fun handleQuestionRejected(event: SseEvent.QuestionRejected) {
        synchronized(pendingLock) {
            removePending(PendingInteraction.Question::class.java, event.sessionId, event.requestId)
            pendingRevision++
            Log.i(
                TAG,
                "Question rejected: session=${event.sessionId} request=${event.requestId} " +
                    "pending=${_pendingInteractions.value.size}",
            )
        }
    }

    /**
     * Optimistically remove a question from the pending list.
     * Called after a successful API reply/reject, in case the SSE event doesn't arrive.
     */
    fun removeQuestion(sessionId: String, questionId: String) {
        synchronized(pendingLock) {
            removePending(PendingInteraction.Question::class.java, sessionId, questionId)
            pendingRevision++
            Log.i(
                TAG,
                "Question removed after REST success: session=$sessionId request=$questionId " +
                    "pending=${_pendingInteractions.value.size}",
            )
        }
    }

    fun removePermission(sessionId: String, permissionId: String) {
        synchronized(pendingLock) {
            removePending(PendingInteraction.Permission::class.java, sessionId, permissionId)
            pendingRevision++
        }
    }

    /**
     * 清除某会话的全部 pending 交互（问题与授权）。当推送事件缺失请求 id（replied/rejected
     * 不带 requestID）时作为兜底，避免 web 端已答复/授权的题在本端永久滞留。
     */
    fun clearPendingForSession(sessionId: String) {
        synchronized(pendingLock) {
            if (sessionId.isBlank()) return
            _pendingInteractions.update { current ->
                current.filterNot { it.sessionId == sessionId }
            }
            pendingRevision++
        }
    }

    /**
     * 清除某会话的待决问题（提问）但不影响待决授权。用户发送新消息覆盖旧提问、或中止会话时，
     * 之前的提问已不再等待答复，应即时消失而不是要求手动 dismiss。
     */
    fun clearQuestionsForSession(sessionId: String) {
        synchronized(pendingLock) {
            if (sessionId.isBlank()) return
            _pendingInteractions.update { current ->
                current.filterNot { it is PendingInteraction.Question && it.sessionId == sessionId }
            }
            pendingRevision++
        }
    }

    private fun upsertPending(interaction: PendingInteraction) {
        synchronized(pendingLock) {
            _pendingInteractions.update { current ->
                val index = current.indexOfFirst { it.sameIdentity(interaction) }
                if (index >= 0) current.toMutableList().apply { set(index, interaction) } else current + interaction
            }
            pendingRevision++
        }
    }

    private fun removePending(type: Class<out PendingInteraction>, sessionId: String, requestId: String) {
        _pendingInteractions.update { current ->
            current.filterNot { type.isInstance(it) && it.sessionId == sessionId && it.id == requestId }
        }
    }

    private fun removePendingForSessions(sessionIds: Set<String>) {
        synchronized(pendingLock) {
            _pendingInteractions.update { current -> current.filterNot { it.sessionId in sessionIds } }
            pendingRevision++
        }
    }

    fun pendingSnapshotRevision(): Long = synchronized(pendingLock) { pendingRevision }

    fun replacePendingRequests(
        serverId: String,
        permissions: List<SseEvent.PermissionAsked>,
        questions: List<SseEvent.QuestionAsked>,
        expectedRevision: Long,
    ): Boolean = synchronized(pendingLock) {
        if (pendingRevision != expectedRevision) return@synchronized false
        val sessionIds = _serverSessions.value[serverId].orEmpty() +
            permissions.map { it.sessionId } + questions.map { it.sessionId }
        sessionIds.forEach { trackSession(serverId, it) }
        replacePendingForSessionsLocked(sessionIds, permissions, questions)
        pendingRevision++
        true
    }

    fun replacePendingRequestsForSessions(
        sessionIds: Set<String>,
        permissions: List<SseEvent.PermissionAsked>,
        questions: List<SseEvent.QuestionAsked>,
        expectedRevision: Long,
    ): Boolean = synchronized(pendingLock) {
        if (pendingRevision != expectedRevision) return@synchronized false
        replacePendingForSessionsLocked(sessionIds, permissions, questions)
        pendingRevision++
        true
    }

    private fun replacePendingForSessionsLocked(
        sessionIds: Set<String>,
        permissions: List<SseEvent.PermissionAsked>,
        questions: List<SseEvent.QuestionAsked>,
    ) {
        val snapshotItems = (permissions.map { PendingInteraction.Permission(it) } +
            questions.map { PendingInteraction.Question(it) })
            .filter { it.sessionId in sessionIds }
        // 按会话判断权威性：只要 REST 快照对该会话返回了 pending 项，就以 REST 为权威
        // 替换该会话（清理服务器端已处理完的项）；REST 对该会话返回为空则保留现有项，
        // 避免 REST 与 SSE 状态不一致（服务器 /question 可能暂时查不到实时 pending）时误删。
        val authoritativeSessions = snapshotItems.asSequence().map { it.sessionId }.toSet()
        val snapshotByKey = snapshotItems.associateBy { it.identityKey() }
        val retained = _pendingInteractions.value.mapNotNull { current ->
            if (current.sessionId !in sessionIds) current
            else if (current.sessionId !in authoritativeSessions) current
            else snapshotByKey[current.identityKey()]
        }
        val retainedKeys = retained.asSequence().map { it.identityKey() }.toSet()
        val additions = snapshotItems
            .filterNot { it.identityKey() in retainedKeys }
            .sortedWith(compareBy<PendingInteraction>({ it.sessionId }, { it.typeRank() }, { it.id }))
        _pendingInteractions.value = retained + additions
    }

    private fun PendingInteraction.sameIdentity(other: PendingInteraction): Boolean =
        identityKey() == other.identityKey()

    private fun PendingInteraction.identityKey(): Triple<String, Int, String> =
        Triple(sessionId, typeRank(), id)

    private fun PendingInteraction.typeRank(): Int = when (this) {
        is PendingInteraction.Permission -> 0
        is PendingInteraction.Question -> 1
    }

    fun replaceSessionStatuses(
        serverId: String,
        sessionIds: Set<String>,
        statuses: Map<String, SessionStatus>,
        connected: Boolean = true,
    ) {
        val scope = sessionIds + statuses.keys
        scope.forEach { trackSession(serverId, it) }
        _sessionStatuses.update { current ->
            // The /session/status endpoint only reports busy/retry sessions.
            //
            // While the SSE stream is connected, it is the real-time source of truth: it pushes
            // Busy the instant the session starts and Idle the instant it completes. A poll
            // snapshot can lag (it may not yet include a session that SSE just marked Busy), so
            // we must NOT reconcile omitted sessions to Idle here — doing so clobbers a genuinely
            // running session and makes the "busy" badge flicker on/off. Instead we only apply the
            // statuses the endpoint explicitly reported, leaving omitted sessions untouched.
            //
            // While disconnected (no SSE), the poll is our only signal, so an omitted session is
            // treated as Idle to clear a stale busy badge left behind by a missed completion.
            val next = current.toMutableMap()
            for (id in scope) {
                val newStatus = statuses[id] ?: if (connected) null else SessionStatus.Idle
                if (newStatus == null) continue
                val previous = current[id]
                next[id] = newStatus
                if (newStatus is SessionStatus.Idle && previous is SessionStatus.Busy) {
                    markUnconfirmedCompleted(id)
                }
            }
            if (next == current) return@update current
            next
        }
    }

    // ============ Batch Updates ============

    /**
     * Load initial session list for a server.
     * Registers all session IDs as belonging to the given serverId.
     *
     * The server's root-session list is authoritative: it fully replaces the previous snapshot
     * for this server, so root sessions that were archived / deleted / moved elsewhere are dropped
     * from the local cache instead of lingering as stale "ghost" entries. Child sessions (tracked
     * via SSE) are preserved because they are not part of the roots list.
     */
    fun setSessions(serverId: String, sessions: List<Session>) {
        val compactedSessions = sessions.map(::compactSessionForCache)
        val sessionIds = compactedSessions.map { it.id }.toSet()
        val oldSessionIds = _serverSessions.value[serverId] ?: emptySet()
        // Only drop *root* sessions that the server no longer reports; keep child sessions.
        val oldRootIds = _sessions.value.asSequence()
            .filter { it.id in oldSessionIds && it.parentId == null }
            .map { it.id }
            .toSet()
        val removedIds = oldRootIds - sessionIds

        _serverSessions.update { current ->
            val newSet = (oldSessionIds - removedIds) + sessionIds
            if (current[serverId] == newSet) return@update current
            current + (serverId to newSet)
        }
        _sessions.update { current ->
            // Drop stale root sessions, then merge/refresh the reported ones.
            val updated = current
                .filter { it.id !in removedIds }
                .toMutableList()
            for (session in compactedSessions) {
                val idx = updated.indexOfFirst { it.id == session.id }
                if (idx >= 0) {
                    if (updated[idx].time.updated <= session.time.updated) updated[idx] = session
                } else {
                    updated.add(session)
                }
            }
            val sorted = updated.sortedByDescending { it.time.updated }
            if (sorted == current) return@update current // no change -> don't recompose downstream lists
            sorted
        }
    }

    /**
     * Manually update the session status.
     * Useful for optimistic updates (e.g. aborting a session).
     */
    fun updateSessionStatus(sessionId: String, status: SessionStatus) {
        _sessionStatuses.update { current ->
            val previous = current[sessionId]
            if (status is SessionStatus.Idle && previous is SessionStatus.Busy) {
                markUnconfirmedCompleted(sessionId)
            }
            if (current[sessionId] == status) {
                return@update current // no change -> don't trigger downstream recomposition
            }
            current + (sessionId to status)
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "Manually updated session $sessionId status to $status")
    }

    /**
     * Load messages for a session
     */
    fun mergeMessages(sessionId: String, messages: List<MessageWithParts>, serverId: String = "") {
        val visibleMessages = messages.filterNot { isMessageRemoved(it.info.id) }
        visibleMessages.forEach { recordLastUserMessage(it.info) }
        _messages.update { current ->
            val merged = (current[sessionId].orEmpty() + visibleMessages.map { it.info })
                .associateBy { it.id }
                .values
                .sortedBy { it.time.created }
            if (merged.isEmpty()) current - sessionId else current + (sessionId to merged)
        }

        val partsMap = visibleMessages.associate { msg ->
            msg.info.id to msg.parts
        }
        _parts.update { current ->
            current + partsMap.mapValues { (messageId, loadedParts) ->
                mergeLoadedParts(current[messageId].orEmpty(), loadedParts)
            }
        }
        indexMessages(serverId, sessionId, visibleMessages)
    }

    /** 异步把消息文本写入全文索引（用于全文消息搜索）。 */
    private fun indexMessages(serverId: String, sessionId: String, messages: List<MessageWithParts>) {
        if (serverId.isBlank() || sessionId.isBlank()) return
        val title = _sessions.value.firstOrNull { it.id == sessionId }?.title ?: ""
        indexScope.launch {
            messages.forEach { msg ->
                val text = msg.parts.filterIsInstance<Part.Text>().joinToString("\n") { it.text }
                if (text.isNotBlank()) {
                    messageFtsIndex.index(serverId, sessionId, msg.info.id, title, text)
                }
            }
        }
    }

    /** Remove only locally cached history so the session can be fetched again without affecting server state. */
    fun clearSessionHistory(sessionId: String) {
        val messageIds = _messages.value[sessionId].orEmpty().mapTo(mutableSetOf()) { it.id }
        _messages.update { it - sessionId }
        if (messageIds.isNotEmpty()) {
            _parts.update { current -> current - messageIds }
        }
        synchronized(removedMessageLock) {
            removedMessageSessions.entries.removeAll { it.value == sessionId }
        }
    }

    private fun mergeLoadedParts(current: List<Part>, loaded: List<Part>): List<Part> {
        val currentById = current.associateBy { it.id }
        val merged = loaded.map { loadedPart ->
            when (val currentPart = currentById[loadedPart.id]) {
                is Part.Text -> if (loadedPart is Part.Text && currentPart.text.length > loadedPart.text.length) currentPart else loadedPart
                is Part.Reasoning -> if (loadedPart is Part.Reasoning && currentPart.text.length > loadedPart.text.length) currentPart else loadedPart
                else -> loadedPart
            }
        }
        val loadedIds = loaded.asSequence().map { it.id }.toSet()
        return merged + current.filterNot { it.id in loadedIds }
    }

    /**
     * Clear all state (used when ALL servers disconnect)
     */
    fun clearAll() {
        _serverSessions.value = emptyMap()
        _sessions.value = emptyList()
        _sessionStatuses.value = emptyMap()
        _messages.value = emptyMap()
        _parts.value = emptyMap()
        _sessionDiffs.value = emptyMap()
        _sessionErrors.value = emptyMap()
        synchronized(pendingLock) {
            _pendingInteractions.value = emptyList()
            pendingRevision++
        }
        synchronized(deltaLock) {
            pendingDeltas.clear()
            deltaAccumulator.clear()
        }
        synchronized(removedMessageLock) { removedMessageSessions.clear() }
        _todos.value = emptyMap()
        _vcsBranches.value = emptyMap()
        _projectInfo.value = emptyMap()
        _promptDeliveries.value = emptyMap()
        _workspaceStatuses.value = emptyMap()
    }

    /**
     * Clear state for a single server.
     * Removes sessions belonging to that server and all associated data.
     */
    fun clearForServer(serverId: String) {
        val sessionIds = _serverSessions.value[serverId] ?: emptySet()
        if (sessionIds.isEmpty()) {
            _serverSessions.update { it - serverId }
            return
        }

        // Remove the server's session tracking
        _serverSessions.update { it - serverId }

        // Remove sessions
        _sessions.update { it.filter { s -> s.id !in sessionIds } }
        _sessionStatuses.update { it - sessionIds }
        _sessionDiffs.update { it - sessionIds }
        _sessionErrors.update { it - sessionIds }
        removePendingForSessions(sessionIds)
        _todos.update { it - sessionIds }
        _promptDeliveries.update { current -> current.filterValues { it.sessionId !in sessionIds } }
        _vcsBranches.update { current -> current.filterKeys { it.serverId != serverId } }
        _projectInfo.update { current -> current.filterKeys { it.serverId != serverId } }
        _workspaceStatuses.update { current -> current.filterKeys { it.serverId != serverId } }

        // Remove messages and their parts
        val messageIds = _messages.value
            .filterKeys { it in sessionIds }
            .values
            .flatten()
            .map { it.id }
            .toSet()
        _messages.update { it - sessionIds }
        _parts.update { it - messageIds }
        _lastUserMessageAt.update { it - sessionIds }
        synchronized(deltaLock) {
            pendingDeltas.keys.removeAll { it.sessionId in sessionIds }
        }
        synchronized(removedMessageLock) {
            removedMessageSessions.entries.removeAll { it.value in sessionIds }
        }
    }

    /** Clear connection-scoped state without erasing persisted session history. */
    fun clearTransientForServer(serverId: String) {
        val sessionIds = _serverSessions.value[serverId].orEmpty()
        _pendingInteractions.update { interactions -> interactions.filterNot { it.sessionId in sessionIds } }
        _sessionStatuses.update { statuses ->
            statuses.mapValues { (sessionId, status) ->
                if (sessionId in sessionIds && status !is SessionStatus.Idle) SessionStatus.Idle else status
            }
        }
        _vcsBranches.update { current -> current.filterKeys { it.serverId != serverId } }
        _projectInfo.update { current -> current.filterKeys { it.serverId != serverId } }
        _workspaceStatuses.update { current -> current.filterKeys { it.serverId != serverId } }
    }

    // ============ Todo Events ============

    private fun handleTodoUpdated(event: SseEvent.TodoUpdated) {
        _todos.update { it + (event.sessionId to event.todos) }
    }

    // ============ VCS Events ============

    private fun handleVcsBranchUpdated(event: SseEvent.VcsBranchUpdated, serverId: String, directory: String?, workspaceId: String?) {
        val scope = DirectoryScope(serverId, directory ?: return, workspaceId)
        _vcsBranches.update { it + (scope to event.branch) }
    }

    // ============ Project Events ============

    private fun handleProjectUpdated(event: SseEvent.ProjectUpdated, serverId: String, directory: String?, workspaceId: String?) {
        val scope = DirectoryScope(serverId, directory ?: return, workspaceId)
        _projectInfo.update { it + (scope to event.info) }
    }
}
