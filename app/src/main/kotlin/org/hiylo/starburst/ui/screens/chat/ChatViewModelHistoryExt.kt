/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatViewModelHistoryExt.kt
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.ui.screens.chat

import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.BuildConfig
import androidx.lifecycle.viewModelScope
import org.hiylo.starburst.data.api.getSession
import org.hiylo.starburst.data.api.getSessionDiff
import org.hiylo.starburst.data.api.listChildSessions
import org.hiylo.starburst.data.api.listMessages
import org.hiylo.starburst.data.api.listMessagesPage
import org.hiylo.starburst.data.api.listSessionStatuses
import org.hiylo.starburst.domain.model.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 会话加载与历史分页：首屏增量渲染、后台分页补齐、REST 兜底对账 busy→idle，
 * 以及 backend ASR 可用性缓存。
 * 从 ChatViewModel 原样搬移为同包扩展函数，无行为变更。
 */

/** 读取（或探测并缓存）后端 ASR 引擎是否可用。 */
internal suspend fun ChatViewModel.cachedBackendAsrAvailable(): Boolean {
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



internal suspend fun ChatViewModel.reconcileActiveStatus() {
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
internal suspend fun ChatViewModel.loadSession() {
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
        // 会话的文件变更列表：SSE session.diff 只在「本次连接期间 agent 改动」时推送，
        // 打开已有会话不会重放历史 diff，故用 REST /session/{id}/diff 拉一次初始状态，
        // 否则「查看变更」菜单在重进会话时不会出现。
        try {
            val diffs = api.getSessionDiff(conn, sessionId)
            eventReducer.setSessionDiffs(sessionId, diffs)
        } catch (e: Exception) {
            e.rethrowCancellation()
            Log.e(TAG, "Failed to load session diff", e)
        }
    } catch (e: Exception) {
        e.rethrowCancellation()
        Log.e(TAG, "Failed to load session info", e)
    } finally {
        sessionLoaded.complete(Unit)
    }
}

fun ChatViewModel.loadMessages() {
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

fun ChatViewModel.reloadSession() {
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
fun ChatViewModel.loadOlderMessages() {
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

