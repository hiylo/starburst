/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatViewModelLoaderExt.kt
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.ui.screens.chat

import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.BuildConfig
import androidx.lifecycle.viewModelScope
import org.hiylo.starburst.data.api.getProviders
import org.hiylo.starburst.data.api.listAgents
import org.hiylo.starburst.data.api.listCommands
import org.hiylo.starburst.data.api.listPendingPermissions
import org.hiylo.starburst.data.api.listPendingQuestions
import org.hiylo.starburst.domain.model.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * REST 一次性加载器：待决问题/授权快照，以及 providers / agents / commands
 * 清单与模型、agent、variant 的选择。
 * 从 ChatViewModel 原样搬移为同包扩展函数，无行为变更。
 */

/**
 * Load pending questions from the server REST API.
 * Converts QuestionRequest DTOs to SseEvent.QuestionAsked domain objects.
 * Must be called after loadSession() so sessionDirectory is set.
 */
internal suspend fun ChatViewModel.loadPendingRequests() {
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

internal fun ChatViewModel.loadProviders() {
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

internal fun ChatViewModel.applyProviderFilter() {
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

internal fun ChatViewModel.loadAgents() {
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

fun ChatViewModel.selectAgent(name: String) {
    _selectedAgent.value = name to true
}

internal fun ChatViewModel.loadCommands() {
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

fun ChatViewModel.selectVariant(name: String?) {
    _selectedVariant.value = name?.takeIf { it in uiState.value.variantNames }
}

fun ChatViewModel.selectModel(providerId: String, modelId: String) {
    _selectedProviderId.value = providerId
    _selectedModelId.value = modelId
    _selectedVariant.value = null
    isModelExplicitlySelected = true
}

