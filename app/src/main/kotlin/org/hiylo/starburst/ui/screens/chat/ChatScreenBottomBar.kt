/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatScreenBottomBar.kt
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.ui.screens.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import org.hiylo.starburst.domain.model.*
import org.hiylo.starburst.data.api.PromptPart
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import android.os.Build
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.AppHaptics
import org.hiylo.starburst.ui.components.AppHapticConfig
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.platform.ClipboardManager


/**
 * Chat Scaffold's bottomBar slot: the composer (ChatInputBar) with its send,
 * slash-command, attachment, voice-input and file-mention wiring. Extracted
 * verbatim from ChatScreen; every captured local arrives as a parameter
 * (mutable ones as MutableState so the writes stay on the same state object).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ChatScreenBottomBar(
    viewModel: ChatViewModel,
    uiState: ChatUiState,
    onNavigateToSession: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    context: Context,
    clipboardManager: ClipboardManager,
    view: android.view.View,
    isListening: Boolean,
    voiceLevel: Float,
    voiceEnabled: Boolean,
    asrSuppressedState: MutableState<Boolean>,
    startVoiceInput: () -> Unit,
    fileSearchResults: List<String>,
    confirmedFilePaths: Set<String>,
    customCommands: List<CustomSlashCommand>,
    confirmBeforeSend: Boolean,
    hapticEnabled: Boolean,
    hapticDurationMillis: Int,
    hapticAmplitude: Int,
    isShellMode: Boolean,
    attachments: MutableList<ImageAttachment>,
    requestSaveImage: (ByteArray, String, String?) -> Unit,
    isBusy: Boolean,
    inputTextState: MutableState<TextFieldValue>,
    inputModeState: MutableState<String>,
    isTerminalModeState: MutableState<Boolean>,
    showModelPickerState: MutableState<Boolean>,
    showRenameDialogState: MutableState<Boolean>,
    showCustomCommandsDialogState: MutableState<Boolean>,
    showAttachmentOptionsState: MutableState<Boolean>,
    showTemplatePickerState: MutableState<Boolean>,
    showDocumentGenerateDialogState: MutableState<Boolean>,
    showSendConfirmDialogState: MutableState<Boolean>,
    pendingSendActionState: MutableState<(() -> Unit)?>,
) {
    var asrSuppressed by asrSuppressedState
    var inputText by inputTextState
    var inputMode by inputModeState
    var isTerminalMode by isTerminalModeState
    var showModelPicker by showModelPickerState
    var showRenameDialog by showRenameDialogState
    var showCustomCommandsDialog by showCustomCommandsDialogState
    var showAttachmentOptions by showAttachmentOptionsState
    var showTemplatePicker by showTemplatePickerState
    var showSendConfirmDialog by showSendConfirmDialogState
    var pendingSendAction by pendingSendActionState

    val modelLabel = if (uiState.selectedModelId != null && uiState.providers.isNotEmpty()) {
        val provider = uiState.providers.find { it.id == uiState.selectedProviderId }
        val model = provider?.models?.get(uiState.selectedModelId)
        model?.name ?: uiState.selectedModelId ?: ""
    } else ""
    val hasRunningTool = uiState.messages.any { message ->
        message.parts.any { part -> part is Part.Tool && part.state is ToolState.Running }
    }

    if (!isTerminalMode && uiState.sessionLoaded && uiState.parentSessionId == null) {
    ChatInputBar(
        textFieldValue = inputText,
        onTextFieldValueChange = { newValue ->
            val shouldAutoShell = !isShellMode && newValue.text.startsWith("!")
            val normalizedValue = if (shouldAutoShell) {
                val stripped = newValue.text.drop(1).trimStart()
                val newCursor = (newValue.selection.start - 1).coerceAtLeast(0)
                TextFieldValue(
                    text = stripped,
                    selection = TextRange(newCursor.coerceAtMost(stripped.length))
                )
            } else {
                newValue
            }

            if (shouldAutoShell) {
                inputMode = ChatInputMode.SHELL.name
                viewModel.clearSuggestions()
            }

            inputText = normalizedValue
            viewModel.updateDraftText(normalizedValue.text)
            if (isShellMode || shouldAutoShell) {
                viewModel.clearFileSearch()
                return@ChatInputBar
            }
            // Detect @query before cursor for file mention
            val cursorPos = normalizedValue.selection.start
            val textBefore = normalizedValue.text.substring(0, cursorPos)
            val atMatch = Regex("@(\\S*)$").find(textBefore)
            if (atMatch != null) {
                val query = atMatch.groupValues[1]
                viewModel.searchFilesForMention(query)
            } else {
                viewModel.clearFileSearch()
            }
        },
        onSend = {
            val doSend = doSend@{
                AppHaptics.perform(
                    view,
                    AppHapticConfig(hapticEnabled, hapticDurationMillis, hapticAmplitude),
                )
                val rawText = inputText.text
                val shellCommand = when {
                    isShellMode -> rawText.trim()
                    rawText.startsWith("!") -> rawText.drop(1).trimStart()
                    else -> null
                }
                if (shellCommand != null) {
                    if (shellCommand.isBlank()) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.chat_shell_empty))
                        }
                        return@doSend
                    }
                    if (attachments.isNotEmpty()) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.chat_shell_attachments_unsupported))
                        }
                        return@doSend
                    }
                    viewModel.runShellCommand(shellCommand) { ok ->
                        if (!ok) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.chat_shell_failed))
                            }
                        }
                    }
                    inputText = TextFieldValue("")
                    asrSuppressed = true
                    if (isShellMode) {
                        inputMode = ChatInputMode.NORMAL.name
                    }
                    viewModel.clearConfirmedPaths()
                    viewModel.clearFileSearch()
                    viewModel.clearDraft()
                    return@doSend
                }
                // Build prompt parts: split text around confirmed @file mentions
                val allParts = buildPromptParts(rawText, confirmedFilePaths, viewModel.getSessionDirectory())
                // Image attachments require vision support from the selected model.
                val hasImageAttachments = attachments.any { it.isImage }
                if (hasImageAttachments && !uiState.modelSupportsVision) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.chat_model_vision_unsupported))
                    }
                    return@doSend
                }
                // Add attachments: images become image parts, everything else stays a file part.
                val attachmentParts = attachments.map { att ->
                    PromptPart(
                        type = if (att.isImage) "image" else "file",
                        mime = att.mime,
                        url = att.dataUrl,
                        filename = att.filename
                    )
                }
                if (viewModel.sendMessage(allParts, attachmentParts)) {
                    inputText = TextFieldValue("")
                    asrSuppressed = true
                    attachments.clear()
                    viewModel.clearConfirmedPaths()
                    viewModel.clearFileSearch()
                    viewModel.clearDraft()
                }
            }
            if (confirmBeforeSend) {
                pendingSendAction = doSend
                showSendConfirmDialog = true
            } else {
                doSend()
            }
        },
        inputMode = if (isShellMode) ChatInputMode.SHELL else ChatInputMode.NORMAL,
        onInputModeChange = {
            inputMode = it.name
            if (it == ChatInputMode.SHELL) {
                viewModel.clearFileSearch()
                // Invalidate any in-flight suggestions; the shell has no suggestion UI.
                viewModel.clearSuggestions()
            }
        },
        onStop = viewModel::abortSession,
        isSending = uiState.isSending,
        isBusy = isWorkingSessionStatus(uiState.sessionStatus) || hasRunningTool,
        sessionStatus = uiState.sessionStatus,
        messages = uiState.messages,
        attachments = attachments,
        onAttach = { showAttachmentOptions = true },
        onTemplateClick = { showTemplatePicker = true },
        onDocumentGenerateClick = { showDocumentGenerateDialogState.value = true },
        isListening = isListening,
        voiceLevel = voiceLevel,
        onMicPress = { startVoiceInput() },
        onMicRelease = { viewModel.stopListening() },
        onMicCancel = { viewModel.cancelListening() },
        voiceEnabled = voiceEnabled,
        onRemoveAttachment = { index ->
            if (index in attachments.indices) {
                attachments.removeAt(index)
                viewModel.removeDraftAttachment(index)
            }
        },
        onSaveAttachment = { bytes, mime, filename ->
            requestSaveImage(bytes, mime, filename)
        },
        modelLabel = modelLabel,
        selectedProviderId = uiState.selectedProviderId,
        onModelClick = { showModelPicker = true },
        agents = uiState.agents,
        selectedAgent = uiState.selectedAgent,
        onAgentSelect = { viewModel.selectAgent(it) },
        variantNames = uiState.variantNames,
        selectedVariant = uiState.selectedVariant,
        onVariantSelect = { viewModel.selectVariant(it) },
        commands = uiState.commands,
        customCommands = customCommands,
        onManageCustomCommands = { showCustomCommandsDialog = true },
        fileSearchResults = fileSearchResults,
        confirmedFilePaths = confirmedFilePaths,
        onFileSelected = { path ->
            // Replace @query with @path in text
            val cursorPos = inputText.selection.start
            val textBefore = inputText.text.substring(0, cursorPos)
            val atMatch = Regex("@(\\S*)$").find(textBefore)
            if (atMatch != null) {
                val matchStart = atMatch.range.first
                val replacement = "@$path "
                val newText = inputText.text.substring(0, matchStart) + replacement +
                        inputText.text.substring(cursorPos)
                val newCursor = matchStart + replacement.length
                inputText = TextFieldValue(
                    text = newText,
                    selection = TextRange(newCursor)
                )
            }
            viewModel.confirmFilePath(path)
            viewModel.clearFileSearch()
        },
        onSlashCommand = { cmd ->
            when (cmd.name) {
                "new" -> {
                    // Create a new session and navigate to it
                    viewModel.createNewSession { session ->
                        if (session != null) {
                            onNavigateToSession(session.id)
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.chat_session_create_failed))
                            }
                        }
                    }
                }
                "compact" -> {
                    viewModel.compactSession { ok ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                if (ok) context.getString(R.string.chat_session_compacted) else context.getString(R.string.chat_session_compact_failed)
                            )
                        }
                    }
                }
                "fork" -> {
                    viewModel.forkSession { session ->
                        if (session != null) {
                            onNavigateToSession(session.id)
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.chat_fork_failed))
                            }
                        }
                    }
                }
                "share" -> {
                    viewModel.shareSession { url ->
                        coroutineScope.launch {
                            if (url != null) {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(url))
                                snackbarHostState.showSnackbar(context.getString(R.string.chat_share_url_copied))
                            } else {
                                snackbarHostState.showSnackbar(context.getString(R.string.chat_share_failed))
                            }
                        }
                    }
                }
                "unshare" -> {
                    viewModel.unshareSession { ok ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                if (ok) context.getString(R.string.chat_session_unshared) else context.getString(R.string.chat_session_unshare_failed)
                            )
                        }
                    }
                }
                "undo" -> {
                    viewModel.undoMessage { ok ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                if (ok) context.getString(R.string.chat_message_undone) else context.getString(R.string.chat_message_undo_failed)
                            )
                        }
                    }
                }
                "redo" -> {
                    viewModel.redoMessage { ok ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                if (ok) context.getString(R.string.chat_message_redone) else context.getString(R.string.chat_message_redo_failed)
                            )
                        }
                    }
                }
                "rename" -> {
                    showRenameDialog = true
                }
                "shell" -> {
                    inputMode = ChatInputMode.SHELL.name
                }
                "review" -> {
                    viewModel.executeCommand("review") { ok ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                if (ok) context.getString(R.string.chat_command_executed, "review") else context.getString(R.string.chat_command_failed, "review")
                            )
                        }
                    }
                }
                else -> {
                    if (cmd.type == "custom" && !cmd.prompt.isNullOrBlank()) {
                        inputText = TextFieldValue(cmd.prompt, TextRange(cmd.prompt.length))
                        viewModel.updateDraftText(cmd.prompt)
                    } else {
                        // Server command — execute via API
                        viewModel.executeCommand(cmd.name) { ok ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    if (ok) context.getString(R.string.chat_command_executed, cmd.name) else context.getString(R.string.chat_command_failed, cmd.name)
                                )
                            }
                        }
                    }
                }
            }
        },
        contextWindow = uiState.contextWindow,
        estimatedContextTokens = uiState.estimatedContextTokens,
        effectiveContextWindow = uiState.effectiveContextWindow,
        contextUsage = uiState.contextUsage,
        contextMessages = uiState.messages,
        suggestions = uiState.suggestions,
        suggestionsSource = uiState.suggestionsSource,
        isGeneratingSuggestions = uiState.isGeneratingSuggestions,
        suggestionsError = uiState.suggestionsError,
        suggestionsStreamText = uiState.suggestionsStreamText,
        modelNeedsDownload = uiState.modelNeedsDownload,
        modelDownloading = uiState.modelDownloading,
        modelDownloadProgress = uiState.modelDownloadProgress,
        onDownloadModel = { viewModel.downloadModel() },
        onSuggestionClick = { suggestion ->
            viewModel.sendMessage(suggestion)
            viewModel.clearSuggestions()
        },
        onGenerateSuggestions = { viewModel.generateSuggestions() },
        onDismissSuggestions = { viewModel.clearSuggestions() },
    )
    }
}
