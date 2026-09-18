/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatViewModelSuggestions.kt
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


/** Create a new session and return it. */
internal fun ChatViewModel.createNewSession(onResult: (Session?) -> Unit) {
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
internal fun ChatViewModel.getConnectionParams(): ConnectionParams = ConnectionParams(
    serverUrl = serverUrl,
    username = username,
    password = password,
    serverName = serverName,
    serverId = serverId
)

/** Get the last assistant message text for copying. */
internal fun ChatViewModel.getLastAssistantText(): String? {
    val msgs = uiState.value.messages
    val last = msgs.lastOrNull { it.isAssistant } ?: return null
    return last.parts
        .filterIsInstance<Part.Text>()
        .joinToString("") { it.text }
        .ifBlank { null }
}

// ============ Next-step suggestions ============

internal fun ChatViewModel.generateSuggestions() {
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
private suspend fun ChatViewModel.generateViaBackendLlm(prompt: String): List<String>? {
    val servers = serverRepository.servers.first()
    val backend = servers.firstOrNull { it.id == serverId }
    val host = runCatching { java.net.URL(serverUrl).host }.getOrNull()
        ?: serverUrl.substringAfter("://").substringBefore(":")
    if (host.isBlank()) return null
    val backendUrl = (backend?.backendResolvedUrl ?: "http://$host:18880").trimEnd('/')
    if (backendUrl.isBlank()) return null
    val token = backend?.backendResolvedToken.orEmpty()
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
private fun ChatViewModel.fallbackSuggestions(): List<String> {        val isZh = context.resources.configuration.locales[0].language == "zh"
    return if (isZh) {
        listOf("继续当前任务", "总结一下刚才的改动", "测试一下刚才的功能")
    } else {
        listOf("Continue the current task", "Summarize the recent changes", "Test what was just implemented")
    }
}

/** Localized prompt explaining that the on-device model must be downloaded first. */
private fun ChatViewModel.getModelDownloadPrompt(): String {
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
internal fun ChatViewModel.downloadModel() {
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
private fun ChatViewModel.buildSuggestionPrompt(): String {
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
private fun ChatViewModel.buildRecentConversationText(maxTurns: Int = SUGGESTION_CONTEXT_MAX_ROUNDS): String {
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

internal fun ChatViewModel.clearSuggestions() {
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
internal fun ChatViewModel.regenerateMessage(assistantMessageId: String, onResult: (Boolean) -> Unit = {}) {
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
internal fun ChatViewModel.editUserMessage(messageId: String) {
    val message = uiState.value.messages.lastOrNull { it.message.id == messageId && it.isUser } ?: return
    restoreRevertedDraft(extractRevertedDraft(message))
}

/** 引用回复一条消息：把其文本以 markdown 引用块填入输入框。 */
internal fun ChatViewModel.quoteMessage(messageId: String) {
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
internal fun ChatViewModel.addBookmark(messageId: String) {
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
internal fun ChatViewModel.summarizeMessage(messageId: String) {
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
internal fun ChatViewModel.summarizeSession() {
    if (_isSummarizing.value) return
    val text = buildRecentConversationText(maxTurns = uiState.value.messages.size)
    if (text.isBlank()) return
    runSummary(text)
}

/** 关闭总结弹窗；若生成仍在进行，生成会在后台继续但弹窗不再显示。 */
internal fun ChatViewModel.dismissSummary() {
    _summaryVisible.value = false
    _summaryText.value = null
    _summaryError.value = null
}

/** 由一条用户消息构造可重发的 [PromptPart] 列表（文本 + 文件附件）。 */
private fun ChatViewModel.promptPartsFromMessage(message: ChatMessage): List<PromptPart> {
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
private fun ChatViewModel.runSummary(text: String) {
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
private fun ChatViewModel.buildSummaryPrompt(text: String): String {
    val isZh = context.resources.configuration.locales[0].language == "zh"
    return if (isZh) {
        "请用简洁的中文总结下面这段对话内容，突出关键结论、决定与待办事项。\n" +
            "只输出总结本身，不要解释、不要 markdown。\n\n内容：\n$text"
    } else {
        "Summarize the following conversation content concisely, highlighting key conclusions, " +
            "decisions, and open items.\nOutput ONLY the summary — no explanation, no markdown.\n\nContent:\n$text"
    }
}
