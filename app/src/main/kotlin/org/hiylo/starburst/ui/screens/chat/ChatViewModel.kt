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

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext internal val context: Context,
    savedStateHandle: SavedStateHandle,
    internal val eventReducer: EventReducer,
    internal val api: OpenCodeApi,
    internal val draftRepository: DraftRepository,
    internal val settingsRepository: SettingsRepository,
    internal val pendingPromptRepository: PendingPromptRepository,
    internal val suggestionProvider: SuggestionProvider,
    internal val secretStore: LocalSyncSecretStore,
    internal val bookmarkRepository: BookmarkRepository,
    internal val backendRepository: BackendRepository,
    internal val serverRepository: ServerRepository,
    internal val serverAsrApi: ServerAsrApi,
) : ViewModel() {

    @Volatile
    internal var sessionPromptable = false

    internal val serverUrl: String = savedStateHandle.get<String>("serverUrl").orEmpty()
    internal val username: String = savedStateHandle.get<String>("username").orEmpty()
    internal val password: String = savedStateHandle.get<String>("password").orEmpty()
    val serverName: String = savedStateHandle.get<String>("serverName").orEmpty()
    val serverId: String = savedStateHandle.get<String>("serverId").orEmpty()
    val sessionId: String = savedStateHandle.get<String>("sessionId").orEmpty()
    private val retryOnOpen: Boolean = savedStateHandle.get<Boolean>("retry") ?: false

    internal val conn = ServerConnection.from(serverUrl, username, password.ifEmpty { null })

    private val _isLoading = MutableStateFlow(true)
    internal val _error = MutableStateFlow<String?>(null)
    internal val _isSending = MutableStateFlow(false)
    internal val _pendingPrompts = MutableStateFlow<List<PendingPromptRecord>>(emptyList())
    internal val _suggestions = MutableStateFlow<List<String>>(emptyList())
    internal val _suggestionsSource = MutableStateFlow<SuggestionSource?>(null)
    internal val _isGeneratingSuggestions = MutableStateFlow(false)
    internal val _suggestionsError = MutableStateFlow<String?>(null)
    /** Incremental text streamed while suggestions are being generated (for UI feedback). */
    internal val _suggestionsStreamText = MutableStateFlow("")
    internal val _modelNeedsDownload = MutableStateFlow(false)
    internal val _modelDownloading = MutableStateFlow(false)
    internal val _modelDownloadProgress = MutableStateFlow(0)
    /** 每服务器覆盖的上下文窗口（token 数，0 表示未覆盖），来自 SettingsRepository。 */
    private val _serverContextLimitOverride = MutableStateFlow(0)
    /** Whether the current project is a Git repository (Project.vcs == "git"). */
    internal val _isGitRepository = MutableStateFlow(false)
    val isGitRepository: StateFlow<Boolean> = _isGitRepository
    // ============ Conversation summary ============
    /** True while a message/conversation summary is being generated. */
    internal val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing
    /** The latest generated summary text, or null when no summary is shown. */
    internal val _summaryText = MutableStateFlow<String?>(null)
    val summaryText: StateFlow<String?> = _summaryText
    /** Non-null when the last summary generation failed. */
    internal val _summaryError = MutableStateFlow<String?>(null)
    val summaryError: StateFlow<String?> = _summaryError

    internal val _summaryVisible = MutableStateFlow(false)
    val summaryVisible: StateFlow<Boolean> = _summaryVisible
    /** Monotonic token invalidating in-flight suggestion generations when the conversation changes. */
    internal var suggestionsGeneration = 0L
    private val _allProviders = MutableStateFlow<List<ProviderInfo>>(emptyList())
    private val _providers = MutableStateFlow<List<ProviderInfo>>(emptyList())
    private val _hiddenModels = MutableStateFlow<Set<String>>(emptySet())
    private val _defaultModels = MutableStateFlow<Map<String, String>>(emptyMap())
    internal val _selectedProviderId = MutableStateFlow<String?>(null)
    internal val _selectedModelId = MutableStateFlow<String?>(null)
    // Track if the model was explicitly selected by the user to avoid overwriting it with defaults/history
    private var isModelExplicitlySelected = false
    /** The directory of this session's project — sent as x-starburst-directory so the server resolves the correct project context. */
    internal var sessionDirectory: String? = eventReducer.sessions.value
        .firstOrNull { it.id == sessionId }
        ?.directory
        ?.takeIf { it.isNotBlank() }
    /** Signals when [loadSession] has finished (successfully or with error), so that terminal
     *  creation can wait for [sessionDirectory] to be populated. */
    internal val sessionLoaded = CompletableDeferred<Unit>()
    private val _agents = MutableStateFlow<List<AgentInfo>>(emptyList())
    /** Pair(agentName, explicitlySelected) — using a single flow avoids race between flag and value */
    internal val _selectedAgent = MutableStateFlow("build" to false)
    internal val _selectedVariant = MutableStateFlow<String?>(null)
    private val _commands = MutableStateFlow<List<CommandInfo>>(emptyList())
    internal val terminalWorkspace = ServerTerminalRegistry.workspaceFor(serverId, api, conn)
    val terminalTabs: StateFlow<List<TerminalTabUi>> = terminalWorkspace.tabList
    val activeTerminalTabId: StateFlow<String?> = terminalWorkspace.activeTabId
    /** Incremented on active terminal tab updates — observe to trigger recomposition. */
    val terminalVersion: StateFlow<Long> = terminalWorkspace.activeVersion
    val terminalConnected: StateFlow<Boolean> = terminalWorkspace.activeConnected
    val terminalFontSizeSp: StateFlow<Float> = terminalWorkspace.activeFontSizeSp
    val terminalEmulator: TerminalEmulator get() = terminalWorkspace.activeEmulator()

    // ============ Draft Persistence ============
    /** Draft text for the input field — survives navigation / app restart. */
    internal val _draftText = MutableStateFlow("")
    val draftText: StateFlow<String> = _draftText

    /** One-shot event: emits reverted draft payload (text + image attachments) for ChatScreen. */
    internal val _revertedDraftEvent = MutableSharedFlow<RevertedDraftPayload>(extraBufferCapacity = 1)
    val revertedDraftEvent: SharedFlow<RevertedDraftPayload> = _revertedDraftEvent

    /** Draft attachment URIs (content:// URIs as strings) — survives navigation / app restart. */
    internal val _draftAttachmentUris = MutableStateFlow<List<String>>(emptyList())
    val draftAttachmentUris: StateFlow<List<String>> = _draftAttachmentUris

    /** Set of file paths that have been confirmed by user selection from the popup */
    internal val _confirmedFilePaths = MutableStateFlow<Set<String>>(emptySet())
    val confirmedFilePaths: StateFlow<Set<String>> = _confirmedFilePaths

    // ============ Voice input (ASR) ============
    /** Whether the mic is currently recording / recognizing. */
    internal val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening
    /** One-shot final recognition result (appended to the input). */
    private val _recognizedText = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val recognizedText: SharedFlow<String> = _recognizedText
    /** Real-time partial recognition results (replaces the previous partial in the input). */
    internal val _partialRecognizedText = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val partialRecognizedText: SharedFlow<String> = _partialRecognizedText
    /** Live microphone volume level (rmsdB, ~0..10) for the waveform UI. */
    internal val _voiceLevel = MutableStateFlow(0f)
    val voiceLevel: StateFlow<Float> = _voiceLevel
    /** One-shot ASR error message resource id (null when none). */
    internal val _speechError = MutableStateFlow<Int?>(null)
    val speechError: StateFlow<Int?> = _speechError
    private var speechRecognition: SpeechRecognition? = null
    /** 端侧 MNN 模型不可用时回退到后端代理的流式引擎，两者共用同一套回调。 */
    internal var asrRecorder: AsrSession? = null

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
            sessionDirectory = session?.directory ?: "",
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
    internal suspend fun loadSession() {
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
}
