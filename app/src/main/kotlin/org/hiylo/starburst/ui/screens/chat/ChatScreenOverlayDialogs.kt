/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatScreenOverlayDialogs.kt
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.ui.screens.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.domain.model.*
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.AppSecondaryButton
import androidx.activity.result.ActivityResultLauncher
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import org.hiylo.starburst.domain.model.FileDiff
import org.hiylo.starburst.data.repository.SettingsRepository
import org.hiylo.starburst.domain.model.PendingInteraction


/**
 * Everything layered above the Scaffold: model picker, summary sheet, attachment
 * options sheet, template picker, context usage, custom commands, rename,
 * session diff and timeline dialogs plus the two send confirmations.
 * Extracted verbatim from ChatScreen; every captured local arrives as a
 * parameter (mutable ones as MutableState so the writes stay on the same state
 * object).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ChatScreenOverlayDialogs(
    viewModel: ChatViewModel,
    uiState: ChatUiState,
    onManageModels: () -> Unit,
    isSummarizing: Boolean,
    summaryText: String?,
    summaryError: String?,
    summaryVisible: Boolean,
    sessionDiffs: List<FileDiff>,
    sessionTodos: List<SseEvent.TodoUpdated.Todo>,
    customCommands: List<CustomSlashCommand>,
    promptTemplates: List<SettingsRepository.PromptTemplate>,
    confirmBeforeSend: Boolean,
    isAmoled: Boolean,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    context: Context,
    pendingInteractions: List<PendingInteraction>,
    attachments: MutableList<ImageAttachment>,
    imagePickerLauncher: ActivityResultLauncher<String>,
    documentPickerLauncher: ActivityResultLauncher<Array<String>>,
    inputTextState: MutableState<TextFieldValue>,
    inputModeState: MutableState<String>,
    showModelPickerState: MutableState<Boolean>,
    showRenameDialogState: MutableState<Boolean>,
    showCustomCommandsDialogState: MutableState<Boolean>,
    showSessionDiffDialogState: MutableState<Boolean>,
    showTimelineDialogState: MutableState<Boolean>,
    showProjectOverviewState: MutableState<Boolean>,
    showAttachmentOptionsState: MutableState<Boolean>,
    showTemplatePickerState: MutableState<Boolean>,
    showSubagentContextDetailsState: MutableState<Boolean>,
    showSendConfirmDialogState: MutableState<Boolean>,
    pendingSendActionState: MutableState<(() -> Unit)?>,
    pendingTemplatePromptState: MutableState<String?>,
) {
    var inputText by inputTextState
    var inputMode by inputModeState
    var showModelPicker by showModelPickerState
    var showRenameDialog by showRenameDialogState
    var showCustomCommandsDialog by showCustomCommandsDialogState
    var showSessionDiffDialog by showSessionDiffDialogState
    var showTimelineDialog by showTimelineDialogState
    var showProjectOverview by showProjectOverviewState
    var showAttachmentOptions by showAttachmentOptionsState
    var showTemplatePicker by showTemplatePickerState
    var showSubagentContextDetails by showSubagentContextDetailsState
    var showSendConfirmDialog by showSendConfirmDialogState
    var pendingSendAction by pendingSendActionState
    var pendingTemplatePrompt by pendingTemplatePromptState

    val projectOverviewState by viewModel.projectOverview.collectAsState()

    // Model picker dialog
    if (showModelPicker) {
        ModelPickerDialog(
            providers = uiState.providers,
            selectedProviderId = uiState.selectedProviderId,
            selectedModelId = uiState.selectedModelId,
            onSelect = { providerId, modelId ->
                viewModel.selectModel(providerId, modelId)
                showModelPicker = false
            },
            onManageModels = {
                showModelPicker = false
                onManageModels()
            },
            onDismiss = { showModelPicker = false }
        )
    }

    // Summary dialog
    if (summaryVisible) {
        SummaryDialog(
            isGenerating = isSummarizing,
            summary = summaryText,
            error = summaryError,
            onDismiss = { viewModel.dismissSummary() },
        )
    }

    // Rename dialog
    if (showAttachmentOptions) {
        val sheetColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface
        val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        val sheetBorderColor = MaterialTheme.colorScheme.outlineVariant
        ModalBottomSheet(
            onDismissRequest = { showAttachmentOptions = false },
            dragHandle = null,
            shape = sheetShape,
            containerColor = sheetColor,
            tonalElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isAmoled) {
                            Modifier.drawBehind {
                                val strokeWidth = 1.dp.toPx()
                                val edge = strokeWidth / 2
                                val radius = 28.dp.toPx()
                                val outline = Path().apply {
                                    moveTo(edge, size.height)
                                    lineTo(edge, radius)
                                    arcTo(
                                        rect = Rect(edge, edge, radius * 2 - edge, radius * 2 - edge),
                                        startAngleDegrees = 180f,
                                        sweepAngleDegrees = 90f,
                                        forceMoveTo = false,
                                    )
                                    lineTo(size.width - radius, edge)
                                    arcTo(
                                        rect = Rect(
                                            size.width - radius * 2 + edge,
                                            edge,
                                            size.width - edge,
                                            radius * 2 - edge,
                                        ),
                                        startAngleDegrees = 270f,
                                        sweepAngleDegrees = 90f,
                                        forceMoveTo = false,
                                    )
                                    lineTo(size.width - edge, size.height)
                                }
                                drawPath(
                                    path = outline,
                                    color = sheetBorderColor,
                                    style = Stroke(strokeWidth),
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BottomSheetDefaults.DragHandle()
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.chat_attach_title),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                        )
                        AttachmentSourceCard(
                            icon = Icons.Default.Image,
                            title = stringResource(R.string.chat_attach_photo),
                            description = stringResource(R.string.chat_attach_photo_hint),
                            onClick = {
                                showAttachmentOptions = false
                                imagePickerLauncher.launch("image/*")
                            },
                        )
                        AttachmentSourceCard(
                            icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                            title = stringResource(R.string.chat_attach_device_file),
                            description = stringResource(R.string.chat_attach_device_file_hint),
                            onClick = {
                                showAttachmentOptions = false
                                documentPickerLauncher.launch(arrayOf("*/*"))
                            },
                        )
                        AttachmentSourceCard(
                            icon = Icons.Default.FolderOpen,
                            title = stringResource(R.string.chat_attach_project_file),
                            description = stringResource(R.string.chat_attach_project_file_hint),
                            onClick = {
                                showAttachmentOptions = false
                                inputMode = ChatInputMode.NORMAL.name
                                val updated = if (inputText.text.isBlank()) "@" else inputText.text + " @"
                                inputText = TextFieldValue(updated, TextRange(updated.length))
                                viewModel.updateDraftText(updated)
                                viewModel.searchFilesForMention("")
                            },
                        )
                    }
                    Spacer(Modifier.navigationBarsPadding().height(8.dp))
                }
            }
        }
    }

    if (showTemplatePicker) {
        TemplatePickerDialog(
            userTemplates = promptTemplates,
            onSelect = { template ->
                showTemplatePicker = false
                if (confirmBeforeSend) {
                    pendingTemplatePrompt = template
                } else {
                    viewModel.sendMessage(template)
                    inputText = TextFieldValue("")
                    attachments.clear()
                    viewModel.clearConfirmedPaths()
                    viewModel.clearFileSearch()
                    viewModel.clearDraft()
                }
            },
            onAdd = viewModel::addPromptTemplate,
            onEdit = viewModel::updatePromptTemplate,
            onDelete = viewModel::deletePromptTemplate,
            onMove = viewModel::movePromptTemplate,
            onDismiss = { showTemplatePicker = false },
        )
    }

    if (showSubagentContextDetails) {
        ContextUsageDialog(
            usage = uiState.contextUsage,
            contextWindow = uiState.effectiveContextWindow,
            messages = uiState.messages,
            estimatedContextTokens = uiState.estimatedContextTokens,
            onDismiss = { showSubagentContextDetails = false },
        )
    }

    if (showCustomCommandsDialog) {
        CustomCommandsDialog(
            commands = customCommands,
            onAdd = { name, prompt -> viewModel.addCustomCommand(name, prompt) },
            onRemove = { name -> viewModel.removeCustomCommand(name) },
            onDismiss = { showCustomCommandsDialog = false },
        )
    }

    if (showRenameDialog) {
        var renameText by remember { mutableStateOf(uiState.sessionTitle) }
        ChatDialog(onDismiss = { showRenameDialog = false }) {
            Text(stringResource(R.string.session_rename), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = renameText,
                onValueChange = { renameText = it },
                label = { Text(stringResource(R.string.session_rename_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AppSecondaryButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
                AppPrimaryButton(
                    onClick = {
                        viewModel.renameSession(renameText) { ok ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    if (ok) context.getString(R.string.chat_session_renamed) else context.getString(R.string.chat_session_rename_failed)
                                )
                            }
                        }
                        showRenameDialog = false
                    },
                    enabled = renameText.isNotBlank()
                ) {
                    Text(stringResource(R.string.session_rename_button))
                }
            }
        }
    }

    // Session changes (file diff) dialog
    if (showSessionDiffDialog) {
        SessionDiffDialog(
            diffs = sessionDiffs,
            onDismiss = { showSessionDiffDialog = false },
        )
    }

    if (showTimelineDialog) {
        SessionTimelineDialog(
            entries = remember(uiState.messages, uiState.pendingInteractions, uiState.childSessions, sessionTodos) {
                buildSessionTimeline(
                    messages = uiState.messages,
                    pendingInteractions = uiState.pendingInteractions,
                    childSessions = uiState.childSessions,
                    todos = sessionTodos,
                )
            },
            onDismiss = { showTimelineDialog = false },
        )
    }

    if (showProjectOverview) {
        ProjectOverviewDialog(
            state = projectOverviewState,
            onRetry = viewModel::loadProjectOverview,
            onDismiss = { showProjectOverview = false },
        )
    }

    // Send confirmation dialog
    if (showSendConfirmDialog) {
        ChatDialog(onDismiss = {
                showSendConfirmDialog = false
                pendingSendAction = null
            }) {
            Text(stringResource(R.string.settings_confirm_send_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.settings_confirm_send_body))
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AppSecondaryButton(onClick = {
                    showSendConfirmDialog = false
                    pendingSendAction = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
                AppPrimaryButton(onClick = {
                    showSendConfirmDialog = false
                    pendingSendAction?.invoke()
                    pendingSendAction = null
                }) {
                    Text(stringResource(R.string.settings_send))
                }
            }
        }
    }

    // Template send confirmation dialog
    pendingTemplatePrompt?.let { prompt ->
        ChatDialog(onDismiss = {
            pendingTemplatePrompt = null
        }) {
            Text(stringResource(R.string.settings_confirm_send_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Text(prompt, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AppSecondaryButton(onClick = {
                    pendingTemplatePrompt = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
                AppPrimaryButton(onClick = {
                    viewModel.sendMessage(prompt)
                    inputText = TextFieldValue("")
                    attachments.clear()
                    viewModel.clearConfirmedPaths()
                    viewModel.clearFileSearch()
                    viewModel.clearDraft()
                    pendingTemplatePrompt = null
                }) {
                    Text(stringResource(R.string.settings_send))
                }
            }
        }
    }
}
