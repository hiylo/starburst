/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatScreenMessageBody.kt
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.ui.screens.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.domain.model.*
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.AppPrimaryButton
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.Density
import org.hiylo.starburst.data.api.GeneratedDocument
import org.hiylo.starburst.data.api.resolveDocumentUrl
import org.hiylo.starburst.domain.model.PendingInteraction
import org.hiylo.starburst.logging.AppLogger as Log


/**
 * The Scaffold's content lambda: the message list and its loading / error / empty
 * states, pending permission and question cards, the revert banner and the
 * scroll-to-bottom overlay. Extracted verbatim from ChatScreen; every captured
 * local arrives as a parameter (mutable ones as MutableState so the writes stay
 * on the same state object).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ChatScreenMessageBody(
    viewModel: ChatViewModel,
    padding: PaddingValues,
    uiState: ChatUiState,
    inputTextState: MutableState<TextFieldValue>,
    listState: LazyListState,
    isTerminalModeState: MutableState<Boolean>,
    terminalCtrlLatchedState: MutableState<Boolean>,
    terminalAltLatchedState: MutableState<Boolean>,
    showTerminalPanelHintOverlayState: MutableState<Boolean>,
    terminalOverlayHeightPxState: MutableState<Int>,
    terminalFocusRequester: FocusRequester,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    context: Context,
    isAmoled: Boolean,
    keyboardController: SoftwareKeyboardController?,
    clipboardManager: ClipboardManager,
    density: Density,
    usesGestureNavigation: Boolean,
    terminalVersion: Long,
    terminalConnected: Boolean,
    terminalTabs: List<TerminalTabUi>,
    activeTerminalTabId: String?,
    activeTerminalTab: TerminalTabUi?,
    terminalFontSizeSp: Float,
    terminalDrawerState: DrawerState,
    pasteClipboardToTerminal: () -> Unit,
    sendTerminalChunk: (String) -> Unit,
    autoScrollEnabledState: MutableState<Boolean>,
    hasUnreadMessagesState: MutableState<Boolean>,
    isAtBottom: Boolean,
    showDocumentGenerateDialogState: MutableState<Boolean>,
    documentGenerateInitialType: String? = null,
    documentGenerateInitialPrompt: String = "",
    pendingInteractions: List<PendingInteraction>,
    isBusy: Boolean,
    onNavigateToChildSession: (String) -> Unit,
    serverBaseUrl: String,
    onOpenChatLink: (String) -> Unit,
) {
    var inputText by inputTextState
    var isTerminalMode by isTerminalModeState
    var terminalCtrlLatched by terminalCtrlLatchedState
    var terminalAltLatched by terminalAltLatchedState
    var showTerminalPanelHintOverlay by showTerminalPanelHintOverlayState
    var terminalOverlayHeightPx by terminalOverlayHeightPxState
    var autoScrollEnabled by autoScrollEnabledState
    var hasUnreadMessages by hasUnreadMessagesState
    var showGenerateDocumentDialog by showDocumentGenerateDialogState

    // ============ Document generation ============
    // 本会话已生成的文档（聊天气泡之外的本地展示）与对应操作状态。
    val generatedDocuments by viewModel.generatedDocuments.collectAsState()
    val isGeneratingDocument by viewModel.isGeneratingDocument.collectAsState()
    val isRevisingDocument by viewModel.isRevisingDocument.collectAsState()
    val documentBackendUrl by viewModel.documentBackendUrl.collectAsState()
    val documentBackendToken by viewModel._documentBackendToken.collectAsState()
    var previewDocument by remember { mutableStateOf<GeneratedDocument?>(null) }
    var reviseDocument by remember { mutableStateOf<GeneratedDocument?>(null) }
    var downloadingDocId by remember { mutableStateOf<Long?>(null) }
    var pendingDocDownload by remember { mutableStateOf<GeneratedDocument?>(null) }

    // 文档操作提示（后端不可用 / 生成失败 / 下载失败）统一走 Snackbar。
    LaunchedEffect(Unit) {
        viewModel.documentToast.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // 下载走 SAF 让用户选择落盘位置：先请求系统文件，选中后拉取字节写入。
    val documentDownloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri: Uri? ->
        val doc = pendingDocDownload
        pendingDocDownload = null
        if (uri == null || doc == null) {
            downloadingDocId = null
            return@rememberLauncherForActivityResult
        }
        coroutineScope.launch {
            try {
                val bytes = viewModel.fetchDocumentBytes(doc)
                    ?: error("download returned empty")
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("unable to open output stream")
                snackbarHostState.showSnackbar(context.getString(R.string.document_downloaded))
            } catch (e: Exception) {
                Log.w("ChatScreenMessageBody", "Failed to write document ${doc.id}", e)
                snackbarHostState.showSnackbar(context.getString(R.string.document_download_failed))
            } finally {
                downloadingDocId = null
            }
        }
    }

    // 请求下载：把 id 标记为下载中并弹出系统文件保存器。
    fun requestDownloadDocument(doc: GeneratedDocument) {
        if (downloadingDocId != null) return
        downloadingDocId = doc.id
        pendingDocDownload = doc
        val extension = when (doc.docType) {
            "pptx" -> ".pptx"
            "docx" -> ".docx"
            "xlsx" -> ".xlsx"
            else -> ""
        }
        val baseName = doc.name.substringBeforeLast('.')
            .ifBlank { doc.name }
            .ifBlank { "document_${doc.id}" }
        documentDownloadLauncher.launch("$baseName$extension")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isTerminalMode) PaddingValues(0.dp) else padding)
    ) {
        when {
            isTerminalMode -> {
                ChatScreenTerminalArea(
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                    coroutineScope = coroutineScope,
                    context = context,
                    isAmoled = isAmoled,
                    terminalVersion = terminalVersion,
                    terminalConnected = terminalConnected,
                    terminalTabs = terminalTabs,
                    activeTerminalTabId = activeTerminalTabId,
                    activeTerminalTab = activeTerminalTab,
                    terminalFontSizeSp = terminalFontSizeSp,
                    terminalDrawerState = terminalDrawerState,
                    terminalFocusRequester = terminalFocusRequester,
                    keyboardController = keyboardController,
                    density = density,
                    usesGestureNavigation = usesGestureNavigation,
                    pasteClipboardToTerminal = pasteClipboardToTerminal,
                    sendTerminalChunk = sendTerminalChunk,
                    terminalCtrlLatchedState = terminalCtrlLatchedState,
                    terminalAltLatchedState = terminalAltLatchedState,
                    showTerminalPanelHintOverlayState = showTerminalPanelHintOverlayState,
                    terminalOverlayHeightPxState = terminalOverlayHeightPxState,
                )
            }
            uiState.isLoading && uiState.messages.isEmpty() -> {
                // Loading is shown consistently on the lower edge of the top app bar.
            }
            uiState.error != null && uiState.messages.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    ErrorPayloadContent(
                        text = uiState.error ?: stringResource(R.string.session_unknown_error),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        textColor = MaterialTheme.colorScheme.error,
                    )
                    AppPrimaryButton(onClick = { viewModel.loadMessages() }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            uiState.messages.isEmpty() && !uiState.isLoading -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.chat_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.chat_type_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.chat_empty_quick_start),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val quickPrompts = listOf(
                        stringResource(R.string.chat_empty_prompt_1),
                        stringResource(R.string.chat_empty_prompt_2),
                        stringResource(R.string.chat_empty_prompt_3),
                    )
                    quickPrompts.forEach { prompt ->
                        SuggestionChip(
                            onClick = {
                                inputText = TextFieldValue(prompt, TextRange(prompt.length))
                                viewModel.updateDraftText(prompt)
                            },
                            label = { Text(prompt) },
                        )
                    }
                    if (uiState.hasOlderMessages) {
                        AppPrimaryButton(
                            onClick = { viewModel.loadOlderMessages() },
                            enabled = !uiState.isLoadingOlder,
                        ) {
                            Text(stringResource(R.string.chat_load_earlier))
                        }
                    }
                }
            }
            else -> {
                val messageSpacing = if (LocalCompactMessages.current) 4.dp else 12.dp
                val timeline = remember(uiState.messages) { buildChatTimeline(uiState.messages) }

                LazyColumn(
                    reverseLayout = true,
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(messageSpacing)
                ) {
                    // A stable final item lets scrollToItem clamp to the true content bottom,
                    // including spacing and padding below a tall or streaming message.
                    item(key = "conversation_bottom") {
                        Spacer(Modifier.height(4.dp))
                    }

                    // 本会话生成的文档卡片（聊天气泡之外的本地展示）。
                    generatedDocuments.forEach { document ->
                        item(key = "generated_doc_${document.id}") {
                            GeneratedDocumentCard(
                                document = document,
                                backendUrl = documentBackendUrl,
                                isDownloading = downloadingDocId == document.id,
                                onDownload = { requestDownloadDocument(document) },
                                onPreview = { previewDocument = document },
                                onRevise = { reviseDocument = document },
                                onRemove = { viewModel.removeGeneratedDocument(document.id) },
                            )
                        }
                    }

                    // Blinking typing cursor while the assistant is generating a reply.
                    if (isBusy && uiState.messages.isNotEmpty()) {
                        item(key = "typing_cursor") {
                            TypingCursorIndicator(
                                modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
                                size = 8.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    pendingInteractions.forEachIndexed { index, interaction ->
                        item(key = "pending_${interaction::class.simpleName}_${interaction.sessionId}_${interaction.id}") {
                            val position = stringResource(R.string.pending_request_position, index + 1, pendingInteractions.size)
                            when (interaction) {
                                is PendingInteraction.Permission -> PermissionCard(
                                    permission = interaction.request,
                                    position = position,
                                    onReply = { reply, onResult ->
                                        viewModel.replyToPermission(
                                            interaction.sessionId,
                                            interaction.id,
                                            reply,
                                        ) { success ->
                                            onResult(success)
                                            if (!success) coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    context.getString(R.string.pending_request_reply_failed),
                                                )
                                            }
                                        }
                                    },
                                )
                                is PendingInteraction.Question -> QuestionCard(
                                    question = interaction.request,
                                    position = position,
                                    onSubmit = { answers, onResult ->
                                        viewModel.replyToQuestion(
                                            interaction.sessionId,
                                            interaction.id,
                                            answers,
                                        ) { success ->
                                            onResult(success)
                                            if (!success) coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    context.getString(R.string.pending_request_reply_failed),
                                                )
                                            }
                                        }
                                    },
                                    onReject = { onResult ->
                                        viewModel.rejectQuestion(interaction.sessionId, interaction.id) { success ->
                                            onResult(success)
                                            if (!success) coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    context.getString(R.string.pending_request_reply_failed),
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }

                    // Revert banner
                    if (uiState.revert != null) {
                        item(key = "revert_banner") {
                            RevertBanner(onRedo = {
                                viewModel.redoMessage { ok ->
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (ok) context.getString(R.string.chat_messages_restored) else context.getString(R.string.chat_message_redo_failed)
                                        )
                                    }
                                }
                            })
                        }
                    }

                    // 会话级错误横幅：session.error 且已空闲时提示异常结束原因，
                    // 避免「处理中 → 空闲」却毫无提示。
                    val sessionErrorText = uiState.sessionError
                    if (sessionErrorText != null && !isBusy) {
                        item(key = "session_error_banner") {
                            ChatSessionErrorBanner(message = sessionErrorText, retry = false)
                        }
                    }

                    items(
                        timeline.asReversed(),
                        key = { it.key },
                    ) { entry ->
                        when (entry) {
                            is ChatTimelineEntry.DateDivider -> DateDividerRow(entry.dayStartMillis)
                            is ChatTimelineEntry.Turn -> {
                        val chatTurn = entry.turn
                        val chatMessage = chatTurn.messages.firstOrNull() ?: return@items
                        // Detect compaction trigger messages (user messages with Part.Compaction)
                        val isCompactionTrigger = chatMessage.isUser &&
                            chatMessage.parts.any { it is Part.Compaction }

                        // Show compact system-style divider for compaction triggers.
                        if (isCompactionTrigger) {
                            var showRevertDialog by remember { mutableStateOf(false) }

                            if (showRevertDialog) {
                                RevertConfirmationDialog(
                                    onDismiss = { showRevertDialog = false },
                                    onConfirm = {
                                        showRevertDialog = false
                                        viewModel.revertMessage(chatMessage.message.id) { ok ->
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    if (ok) context.getString(R.string.chat_message_reverted) else context.getString(R.string.chat_message_revert_failed)
                                                )
                                            }
                                        }
                                    },
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 32.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                                Text(
                                    text = stringResource(R.string.chat_summarized),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clickable { showRevertDialog = true },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = stringResource(R.string.chat_revert),
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    )
                                }
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                            return@items
                        }

                        ChatLinkHandlerProvider(serverBaseUrl, onOpenChatLink) {
                        ChatMessageBubble(
                            chatMessages = chatTurn.messages,
                            onNavigateToChildSession = onNavigateToChildSession,
                            onRevert = if (chatMessage.isUser) {
                                {
                                    val revertText = chatMessage.parts
                                        .filterIsInstance<Part.Text>()
                                        .joinToString("\n") { it.text }
                                    viewModel.revertMessage(chatMessage.message.id, revertText) { ok ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (ok) context.getString(R.string.chat_message_reverted) else context.getString(R.string.chat_message_revert_failed)
                                            )
                                        }
                                    }
                                }
                            } else null,
                            onCopyText = {
                                val text = chatTurn.messages.flatMap { it.parts }
                                    .filterIsInstance<Part.Text>()
                                    .joinToString("\n") { it.text }
                                if (text.isNotBlank()) {
                                    clipboardManager.setText(
                                        androidx.compose.ui.text.AnnotatedString(text)
                                    )
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.chat_copied_clipboard))
                                    }
                                }
                            },
                            onRegenerate = if (chatMessage.isAssistant) {
                                {
                                    viewModel.regenerateMessage(chatMessage.message.id) { ok ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (ok) context.getString(R.string.chat_message_regenerated) else context.getString(R.string.chat_message_regenerate_failed)
                                            )
                                        }
                                    }
                                }
                            } else null,
                            onEdit = if (chatMessage.isUser) {
                                { viewModel.editUserMessage(chatMessage.message.id) }
                            } else null,
                            onSummarize = if (chatMessage.isAssistant) {
                                { viewModel.summarizeMessage(chatMessage.message.id) }
                            } else null,
                            onQuoteReply = {
                                viewModel.quoteMessage(chatMessage.message.id)
                            },
                            onBookmark = {
                                viewModel.addBookmark(chatMessage.message.id)
                            },
                            onContinue = if (chatMessage.isAssistant) {
                                {
                                    viewModel.continueSession { ok ->
                                        if (!ok) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(context.getString(R.string.chat_continue_failed))
                                            }
                                        }
                                    }
                                }
                            } else null,
                        )
                        }
                            }
                        }
                    }

                    // "Load earlier messages" button at the top
                    if (uiState.hasOlderMessages) {
                        item(key = "load_older") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (uiState.isLoadingOlder) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        PulsingDotsIndicator(
                                            dotSize = 6.dp,
                                            dotSpacing = 4.dp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = stringResource(R.string.chat_loading_earlier),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    TextButton(onClick = {
                                        viewModel.loadOlderMessages()
                                    }) {
                                        Text(stringResource(R.string.chat_load_earlier))
                                    }
                                }
                            }
                        }
                    }
                }

                // Scroll-to-bottom FAB
                if (!isAtBottom && !autoScrollEnabled) {
                    SmallFloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                listState.scrollToItem(0)
                                autoScrollEnabled = true
                                hasUnreadMessages = false
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Box {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(
                                    if (hasUnreadMessages) R.string.chat_unread_messages
                                    else R.string.chat_scroll_bottom
                                ),
                                modifier = Modifier.size(20.dp)
                            )
                            if (hasUnreadMessages) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 文档生成对话框（由输入区「生成文档」入口打开）。
        if (showGenerateDocumentDialog) {
            DocumentGenerateDialog(
                isGenerating = isGeneratingDocument,
                onGenerate = { type, prompt ->
                    viewModel.generateDocument(type, prompt) { ok ->
                        if (ok) showGenerateDocumentDialog = false
                    }
                },
                onDismiss = { showGenerateDocumentDialog = false },
                initialType = documentGenerateInitialType,
                initialPrompt = documentGenerateInitialPrompt,
            )
        }

        // 按意见修改对话框。
        reviseDocument?.let { document ->
            DocumentReviseDialog(
                docName = document.name,
                isRevising = isRevisingDocument,
                onRevise = { instruction ->
                    viewModel.reviseDocument(document.id, instruction) { ok ->
                        if (ok) reviseDocument = null
                    }
                },
                onDismiss = { reviseDocument = null },
            )
        }

        // 文档预览弹层（后端 doc/preview.html 整页加载）。
        previewDocument?.let { document ->
            DocumentPreviewSheet(
                backendUrl = documentBackendUrl,
                fileUrl = resolveDocumentUrl(documentBackendUrl, document.downloadUrl),
                onDismiss = { previewDocument = null },
                token = documentBackendToken,
                onDownload = { requestDownloadDocument(document) },
            )
        }
    }
}
