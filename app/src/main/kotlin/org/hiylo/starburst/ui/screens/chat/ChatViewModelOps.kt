/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatViewModelOps.kt
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
import java.util.Locale
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

// ============ Voice input (ASR) ============

/** 开始语音识别（按住说话）。调用方需已确保 RECORD_AUDIO 权限。 */
internal fun ChatViewModel.startListening() {
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
private suspend fun ChatViewModel.createAsrSession(): AsrSession? {
    val endpoint = backendAsrEndpoint()
    if (endpoint != null && serverAsrApi.isAvailable(endpoint.first, endpoint.second)) {
        return ServerAsrRecorder(serverAsrApi, endpoint.first, endpoint.second)
    }
    if (MnnAsr.ensureLoaded(context)) return MnnAsrRecorder(context)
    return null
}

/** 解析当前 server 对应的后端地址与 token，用于服务端语音识别。 */
internal suspend fun ChatViewModel.backendAsrEndpoint(): Pair<String, String>? {
    val servers = serverRepository.servers.first()
    val backend = servers.firstOrNull { it.id == serverId }
    val host = runCatching { java.net.URL(serverUrl).host }.getOrNull()
        ?: serverUrl.substringAfter("://").substringBefore(":")
    if (host.isBlank()) return null
    val url = (backend?.backendResolvedUrl ?: "http://$host:18880").trimEnd('/')
    if (url.isBlank()) return null
    return url to (backend?.backendResolvedToken.orEmpty())
}

/** 停止语音识别（松手上屏）。 */
internal fun ChatViewModel.stopListening() {
    val recorder = asrRecorder
    asrRecorder = null
    _isListening.value = false
    _voiceLevel.value = 0f
    if (recorder != null) {
        viewModelScope.launch { recorder.stop() }
    }
}

/** 取消语音识别（上滑取消），不产生结果。 */
internal fun ChatViewModel.cancelListening() {
    val recorder = asrRecorder
    asrRecorder = null
    _isListening.value = false
    _voiceLevel.value = 0f
    if (recorder != null) {
        viewModelScope.launch { recorder.cancel() }
    }
}

/** 消费并清除当前的 ASR 错误提示。 */
internal fun ChatViewModel.consumeSpeechError() {
    _speechError.value = null
}

/** Get the session directory for building file:// URLs */
internal fun ChatViewModel.getSessionDirectory(): String? = sessionDirectory

/**
 * 刷新当前项目是否为 Git 仓库的状态。
 * 依据 [Project.vcs] 是否为 "git" 判定，优先按会话目录匹配 [OpenCodeApi.listProjects] 结果，
 * 未匹配时回退到 [OpenCodeApi.getCurrentProject]。
 */
internal suspend fun ChatViewModel.refreshGitRepositoryState() {
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

internal fun ChatViewModel.sendMessage(text: String, attachments: List<PromptPart> = emptyList()): Boolean {
    if (text.isBlank() && attachments.isEmpty()) return false
    val parts = mutableListOf<PromptPart>()
    if (text.isNotBlank()) {
        parts.add(PromptPart(type = "text", text = text))
    }
    parts.addAll(attachments)
    return sendParts(parts)
}

/** Send pre-built prompt parts (used when @-file mentions need structured parts). */
internal fun ChatViewModel.sendMessage(promptParts: List<PromptPart>, attachments: List<PromptPart>): Boolean {
    val parts = promptParts + attachments
    if (parts.isEmpty()) return false
    return sendParts(parts)
}

internal fun ChatViewModel.sendParts(parts: List<PromptPart>): Boolean {
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

private suspend fun ChatViewModel.reconcilePendingMessage(messageId: String) {
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

internal fun ChatViewModel.reconcilePendingPrompts(authoritative: List<MessageWithParts>, minimumAgeMs: Long) {
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

private fun ChatViewModel.restoreMissingPendingPrompt(pending: PendingPromptRecord) {
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

private fun ChatViewModel.restoreDraftAfterFailedSend(snapshot: Draft) {
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
internal fun ChatViewModel.replyToPermission(
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

internal fun ChatViewModel.abortSession() {
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
internal fun ChatViewModel.continueSession(onResult: (Boolean) -> Unit = {}) {
    val ok = sendMessage(context.getString(R.string.chat_continue_task_prompt))
    onResult(ok)
}

/**
 * 发送新消息覆盖提问、或中止会话后，把本会话及其子会话的待决提问在服务端驳回并移除本地卡片，
 * 避免提问只从当前界面消失、重进会话又从 /question 拉回来。
 */
private suspend fun ChatViewModel.dismissPendingQuestions() {
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
internal fun ChatViewModel.replyToQuestion(
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
internal fun ChatViewModel.rejectQuestion(
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

private fun ChatViewModel.requestDirectory(requestSessionId: String): String? =
    eventReducer.sessions.value.firstOrNull { it.id == requestSessionId }
        ?.directory
        ?.takeIf { it.isNotBlank() }
        ?: sessionDirectory

// ============ Slash Command Actions ============

/** Share the current session. Returns the share URL or null on failure. */
internal fun ChatViewModel.shareSession(onResult: (String?) -> Unit) {
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

internal fun ChatViewModel.unshareSession(onResult: (Boolean) -> Unit) {
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
internal fun ChatViewModel.compactSession(onResult: (Boolean) -> Unit) {
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
internal fun ChatViewModel.exportSession(context: android.content.Context, uri: android.net.Uri, onResult: (Boolean) -> Unit) {
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
                        val mb = String.format(Locale.ROOT, "%.1f MB", bytesWritten / 1_000_000.0)
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
internal fun ChatViewModel.undoMessage(onResult: (Boolean) -> Unit) {
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
internal fun ChatViewModel.revertMessage(messageId: String, revertedText: String? = null, onResult: (Boolean) -> Unit) {
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

internal fun ChatViewModel.extractRevertedDraft(message: ChatMessage): RevertedDraftPayload {
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

internal fun ChatViewModel.restoreRevertedDraft(payload: RevertedDraftPayload) {
    _draftText.value = payload.text
    _draftAttachmentUris.value = payload.attachmentUris
    _confirmedFilePaths.value = emptySet()
    _revertedDraftEvent.tryEmit(payload)
}

/** Redo the last undone message. */
internal fun ChatViewModel.redoMessage(onResult: (Boolean) -> Unit) {
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
internal fun ChatViewModel.forkSession(onResult: (Session?) -> Unit) {
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
internal fun ChatViewModel.renameSession(title: String, onResult: (Boolean) -> Unit) {
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
internal fun ChatViewModel.executeCommand(command: String, arguments: String = "", onResult: (Boolean) -> Unit) {
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
internal fun ChatViewModel.runShellCommand(command: String, onResult: (Boolean) -> Unit) {
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

internal fun ChatViewModel.openTerminalSession(onResult: (Boolean) -> Unit = {}) {
    viewModelScope.launch {
        // Wait for loadSession() to finish so sessionDirectory is populated.
        // This prevents the race condition where the PTY is created with directory=null
        // and then resize is attempted with the real directory.
        sessionLoaded.await()
        if (BuildConfig.DEBUG) Log.d(TAG, "openTerminalSession: sessionDirectory=$sessionDirectory")
        terminalWorkspace.ensureActiveTab(cwd = sessionDirectory, directory = sessionDirectory, onResult = onResult)
    }
}

internal fun ChatViewModel.createTerminalTab(onResult: (Boolean) -> Unit = {}) {
    viewModelScope.launch {
        sessionLoaded.await()
        terminalWorkspace.createTab(cwd = sessionDirectory, directory = sessionDirectory, onResult = onResult)
    }
}

internal fun ChatViewModel.switchTerminalTab(tabId: String) {
    terminalWorkspace.switchTab(tabId)
}

internal fun ChatViewModel.closeTerminalTab(tabId: String) {
    terminalWorkspace.closeTab(tabId)
}

internal fun ChatViewModel.recoverTerminalTab(tabId: String, onResult: (Boolean) -> Unit = {}) {
    terminalWorkspace.recoverTab(tabId, onResult)
}

internal fun ChatViewModel.setTerminalFontSize(fontSizeSp: Float) {
    terminalWorkspace.setActiveFontSize(fontSizeSp)
}

internal fun ChatViewModel.sendTerminalInput(input: String) {
    terminalWorkspace.sendActiveInput(input)
}

internal fun ChatViewModel.clearTerminalBuffer() {
    terminalWorkspace.clearActiveBuffer()
}

internal fun ChatViewModel.resizeTerminal(cols: Int, rows: Int) {
    terminalWorkspace.resizeActive(cols, rows)
}

internal fun ChatViewModel.closeTerminalSession() {
    // Global terminal workspaces are server-scoped and survive chat screen changes.
}
