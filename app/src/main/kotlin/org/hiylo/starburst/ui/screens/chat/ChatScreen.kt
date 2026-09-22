/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatScreen.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import org.hiylo.starburst.service.SessionNotificationCoordinator
import androidx.compose.ui.unit.LayoutDirection
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.hiylo.starburst.domain.model.*
import org.hiylo.starburst.MainActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import android.net.Uri
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.core.content.ContextCompat
import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.BuildConfig
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.AppHapticConfig
import org.hiylo.starburst.ml.MnnAsr
import org.hiylo.starburst.ui.components.isAmoledTheme



@Composable
private fun ImeVisibilityTracker(onChanged: (Boolean) -> Unit) {
    val density = LocalDensity.current
    val visible = WindowInsets.ime.getBottom(density) > 0
    LaunchedEffect(visible) { onChanged(visible) }
}


/**
 * Chat Screen - conversation view with native markdown rendering.
 * Shows messages with streaming text rendered via mikepenz markdown renderer.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSession: (sessionId: String) -> Unit = {},
    onNavigateToChildSession: (sessionId: String) -> Unit = {},
    onOpenInWebView: () -> Unit = {},
    onOpenWorkspace: (directory: String) -> Unit = {},
    onOpenAgentsMd: (directory: String) -> Unit = {},
    onOpenGit: () -> Unit = {},
    onManageModels: () -> Unit = {},
    initialSharedAttachments: List<Uri> = emptyList(),
    onSharedAttachmentsConsumed: () -> Unit = {},
    startInTerminalMode: Boolean = false,
    isServerConnected: Boolean = true,
    serverBaseUrl: String = "",
    onOpenChatLink: (url: String) -> Unit = {},
    onOpenSharedSession: (shareId: String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val draftText by viewModel.draftText.collectAsState()
    val draftAttachmentUris by viewModel.draftAttachmentUris.collectAsState()
    val isSummarizing by viewModel.isSummarizing.collectAsState()
    val summaryText by viewModel.summaryText.collectAsState()
    val summaryError by viewModel.summaryError.collectAsState()
    val summaryVisible by viewModel.summaryVisible.collectAsState()
    val inputTextState = remember { mutableStateOf(TextFieldValue("")) }
    var inputText by inputTextState
    // Sync inputText once from draft on first composition
    var draftTextInitialized by remember { mutableStateOf(false) }
    if (!draftTextInitialized && draftText.isNotEmpty()) {
        inputText = TextFieldValue(draftText, TextRange(draftText.length))
        draftTextInitialized = true
    } else if (!draftTextInitialized) {
        draftTextInitialized = true
    }
    // Listen for revert events that should restore text to the input field
    LaunchedEffect(Unit) {
        viewModel.revertedDraftEvent.collect { payload ->
            inputText = TextFieldValue(payload.text, TextRange(payload.text.length))
        }
    }
    val listState = rememberLazyListState()
    val showModelPickerState = remember { mutableStateOf(false) }
    var showModelPicker by showModelPickerState
    val showRenameDialogState = remember { mutableStateOf(false) }
    var showRenameDialog by showRenameDialogState
    val showCustomCommandsDialogState = remember { mutableStateOf(false) }
    var showCustomCommandsDialog by showCustomCommandsDialogState
    val showMenuState = remember { mutableStateOf(false) }
    var showMenu by showMenuState
    val showSessionDiffDialogState = remember { mutableStateOf(false) }
    var showSessionDiffDialog by showSessionDiffDialogState
    val showTimelineDialogState = remember { mutableStateOf(false) }
    var showTimelineDialog by showTimelineDialogState
    val showProjectOverviewState = remember { mutableStateOf(false) }
    var showProjectOverview by showProjectOverviewState
    val showAttachmentOptionsState = remember { mutableStateOf(false) }
    var showAttachmentOptions by showAttachmentOptionsState
    val showTemplatePickerState = remember { mutableStateOf(false) }
    var showTemplatePicker by showTemplatePickerState
    val showDocumentGenerateDialogState = remember { mutableStateOf(false) }
    var showDocumentGenerateDialog by showDocumentGenerateDialogState
    val showSubagentContextDetailsState = remember { mutableStateOf(false) }
    var showSubagentContextDetails by showSubagentContextDetailsState
    val isTerminalModeState = rememberSaveable { mutableStateOf(startInTerminalMode) }
    var isTerminalMode by isTerminalModeState
    val terminalCtrlLatchedState = rememberSaveable { mutableStateOf(false) }
    var terminalCtrlLatched by terminalCtrlLatchedState
    val terminalAltLatchedState = rememberSaveable { mutableStateOf(false) }
    var terminalAltLatched by terminalAltLatchedState
    var terminalVirtualCtrlDown by remember { mutableStateOf(false) }
    var terminalVirtualFnDown by remember { mutableStateOf(false) }
    val showTerminalPanelHintOverlayState = remember { mutableStateOf(false) }
    var showTerminalPanelHintOverlay by showTerminalPanelHintOverlayState
    val terminalFocusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val isListening by viewModel.isListening.collectAsState()
    val speechError by viewModel.speechError.collectAsState()
    val voiceLevel by viewModel.voiceLevel.collectAsState()
    // 麦克风按钮：端侧 MNN 模型已下载，或后端流式识别引擎可用（服务端回退）。
    val backendAsrAvailable by viewModel.backendAsrAvailable.collectAsState()
    val sessionDiffs by viewModel.sessionDiffs.collectAsState()
    val sessionTodos by viewModel.sessionTodos.collectAsState()
    val voiceEnabled = remember { MnnAsr.modelDirectory(context) != null } || backendAsrAvailable

    // 录音开始前输入框里已有的文字。识别引擎返回的是「累积全文」而不是增量片段，
    // 所以每次都用「前缀 + 累积文本」整体重写输入框，绝不能往末尾追加——引擎中途
    // 回改前文时（"昨天是" → "昨天是 MONDAY"），追加会把上一版的字留在原地，
    // 表现为重字。
    var asrPrefix by remember { mutableStateOf("") }

    // 发送后置 true：识别完成（含后台 refine 迟到回调）不得再写回输入框，
    // 否则用户刚发送、输入框已清空，校对结果又把它塞回来，表现为"发了还在还变多"。
    // 下一次按下麦克风时重置为 false。
    val asrSuppressedState = remember { mutableStateOf(false) }
    var asrSuppressed by asrSuppressedState

    // Fill recognized ASR text back into the input field and persist to the draft.
    LaunchedEffect(Unit) {
        viewModel.partialRecognizedText.collect { transcript ->
            if (asrSuppressed) return@collect
            val prefix = asrPrefix
            val separator = if (prefix.isBlank() || prefix.endsWith(" ") || prefix.endsWith("\n")) "" else " "
            val merged = prefix + separator + transcript
            inputText = TextFieldValue(merged, TextRange(merged.length))
            viewModel.updateDraftText(merged)
        }
    }

    // Surface ASR errors as a snackbar, then clear the one-shot state.
    LaunchedEffect(speechError) {
        val messageRes = speechError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(context.getString(messageRes))
        viewModel.consumeSpeechError()
    }

    // Mic button: request RECORD_AUDIO on first use, then hold-to-talk.
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startListening()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.chat_voice_input_permission_denied))
            }
        }
    }
    val startVoiceInput = {
        // 记住按住说话之前输入框里的内容，识别结果只替换它后面的部分。
        asrSuppressed = false
        asrPrefix = inputText.text
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startListening()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val isAmoled = isAmoledTheme()
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val view = LocalView.current
    val density = LocalDensity.current
    var imeVisible by remember { mutableStateOf(false) }
    ImeVisibilityTracker { imeVisible = it }
    val usesGestureNavigation = WindowInsets.systemGestures.getLeft(density, LayoutDirection.Ltr) > 0
    val terminalOverlayHeightPxState = remember { mutableStateOf(0) }
    var terminalOverlayHeightPx by terminalOverlayHeightPxState

    // @ file mention state
    val fileSearchResults by viewModel.fileSearchResults.collectAsState()
    val confirmedFilePaths by viewModel.confirmedFilePaths.collectAsState()
    val customCommands by viewModel.customCommands.collectAsState()
    val promptTemplates by viewModel.promptTemplates.collectAsState()
    // Settings
    val chatFontSize by viewModel.chatFontSize.collectAsState()
    val chatLineHeight by viewModel.chatLineHeight.collectAsState()
    val codeWordWrap by viewModel.codeWordWrap.collectAsState()
    val confirmBeforeSend by viewModel.confirmBeforeSend.collectAsState()
    val compactMessages by viewModel.compactMessages.collectAsState()
    val collapseTools by viewModel.collapseTools.collectAsState()
    val expandReasoning by viewModel.expandReasoning.collectAsState()
    val showTurnDividers by viewModel.showTurnDividers.collectAsState()
    val hapticEnabled by viewModel.hapticFeedback.collectAsState()
    val hapticDurationMillis by viewModel.hapticDurationMillis.collectAsState()
    val hapticAmplitude by viewModel.hapticAmplitude.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val compressImageAttachments by viewModel.compressImageAttachments.collectAsState()
    val imageAttachmentMaxLongSide by viewModel.imageAttachmentMaxLongSide.collectAsState()
    val imageAttachmentWebpQuality by viewModel.imageAttachmentWebpQuality.collectAsState()
    val terminalVersion by viewModel.terminalVersion.collectAsState()
    val terminalConnected by viewModel.terminalConnected.collectAsState()
    val terminalTabs by viewModel.terminalTabs.collectAsState()
    val activeTerminalTabId by viewModel.activeTerminalTabId.collectAsState()
    val activeTerminalTab = terminalTabs.firstOrNull { it.id == activeTerminalTabId }
    val isGitRepository by viewModel.isGitRepository.collectAsState()
    val terminalFontSizeSp by viewModel.terminalFontSizeSp.collectAsState()
    val terminalDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) { viewModel.attachLifecycle(lifecycleOwner.lifecycle) }
    val showSendConfirmDialogState = remember { mutableStateOf(false) }
    var showSendConfirmDialog by showSendConfirmDialogState
    // Pending send action: stored so the confirm dialog can trigger it
    val pendingSendActionState = remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingSendAction by pendingSendActionState
    // Pending template prompt: stored so the confirm dialog can send it
    val pendingTemplatePromptState = remember { mutableStateOf<String?>(null) }
    var pendingTemplatePrompt by pendingTemplatePromptState
    val inputModeState = rememberSaveable { mutableStateOf(ChatInputMode.NORMAL.name) }
    var inputMode by inputModeState
    val isShellMode = inputMode == ChatInputMode.SHELL.name

    BackHandler(enabled = isTerminalMode) {
        if (terminalDrawerState.isOpen) {
            coroutineScope.launch { terminalDrawerState.close() }
        } else if (startInTerminalMode) {
            // Opened directly in terminal mode (e.g. from sessions list) —
            // back should navigate away, not show the chat view.
            onNavigateBack()
        } else {
            isTerminalMode = false
        }
    }

    LaunchedEffect(isTerminalMode) {
        if (isTerminalMode) {
            if (viewModel.shouldShowTerminalPanelHint() && TerminalPanelHintCoordinator.tryShow()) {
                showTerminalPanelHintOverlay = true
                launch {
                    delay(8_000)
                    showTerminalPanelHintOverlay = false
                }
            }
            viewModel.openTerminalSession { ok ->
                if (!ok) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.chat_terminal_connect_failed))
                    }
                    isTerminalMode = false
                }
            }
        } else {
            showTerminalPanelHintOverlay = false
            terminalCtrlLatched = false
            terminalAltLatched = false
            terminalVirtualCtrlDown = false
            terminalVirtualFnDown = false
        }
    }

    DisposableEffect(lifecycleOwner, viewModel.serverId, viewModel.sessionId) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            SessionNotificationCoordinator.activate(context, viewModel.serverId, viewModel.sessionId)
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    SessionNotificationCoordinator.activate(context, viewModel.serverId, viewModel.sessionId)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    SessionNotificationCoordinator.deactivate(viewModel.serverId, viewModel.sessionId)
                }
                Lifecycle.Event.ON_STOP -> {
                    terminalCtrlLatched = false
                    terminalAltLatched = false
                    terminalVirtualCtrlDown = false
                    terminalVirtualFnDown = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            SessionNotificationCoordinator.deactivate(viewModel.serverId, viewModel.sessionId)
        }
    }

    DisposableEffect(isTerminalMode) {
        val activity = context as? MainActivity
        if (isTerminalMode && activity != null) {
            activity.setTerminalKeyInterceptor { event ->
                when (event.keyCode) {
                    android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        terminalVirtualCtrlDown = event.action == android.view.KeyEvent.ACTION_DOWN
                        true
                    }
                    android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                        terminalVirtualFnDown = event.action == android.view.KeyEvent.ACTION_DOWN
                        if (BuildConfig.DEBUG) {
                            Log.d("TerminalInput", "VOL_UP: action=${if (event.action == android.view.KeyEvent.ACTION_DOWN) "DOWN" else "UP"} nowDown=$terminalVirtualFnDown")
                        }
                        true
                    }
                    else -> false
                }
            }
        } else {
            activity?.setTerminalKeyInterceptor(null)
        }
        onDispose {
            activity?.setTerminalKeyInterceptor(null)
            terminalVirtualCtrlDown = false
            terminalVirtualFnDown = false
        }
    }

    // Force status bar black while terminal is visible.
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    DisposableEffect(isTerminalMode) {
        val activity = context as? android.app.Activity
        if (isTerminalMode && activity != null) {
            activity.window.statusBarColor = android.graphics.Color.BLACK
            androidx.core.view.WindowCompat.getInsetsController(
                activity.window, activity.window.decorView
            ).isAppearanceLightStatusBars = false
        }
        onDispose {
            val act = context as? android.app.Activity ?: return@onDispose
            act.window.statusBarColor = android.graphics.Color.TRANSPARENT
            androidx.core.view.WindowCompat.getInsetsController(
                act.window, act.window.decorView
            ).isAppearanceLightStatusBars = !isDarkTheme
        }
    }

    LaunchedEffect(isTerminalMode, terminalConnected) {
        if (isTerminalMode && terminalConnected) {
            terminalFocusRequester.requestFocus()
        }
    }

    fun pasteClipboardToTerminal() {
        if (!terminalConnected) return
        val clip = clipboardManager.getText()?.text ?: return
        if (clip.isEmpty()) return
        val cleaned = clip
            .replace(Regex("[\u001B\u0080-\u009F]"), "")
            .replace("\r\n", "\r")
            .replace('\n', '\r')
        if (cleaned.isNotEmpty()) {
            viewModel.sendTerminalInput(cleaned)
        }
    }

    fun sendTerminalChunk(chunk: String) {
        if (BuildConfig.DEBUG) {
            val codes = chunk.map { String.format("%04x", it.code) }
            Log.d("TerminalInput", "sendTerminalChunk: chunk=$codes fnDown=$terminalVirtualFnDown")
        }

        val ctrlActive = terminalCtrlLatched || terminalVirtualCtrlDown
        val altActive = terminalAltLatched

        // Termux-compatible shortcut: Ctrl+Alt+V pastes clipboard into terminal.
        if (!terminalVirtualFnDown && ctrlActive && altActive && chunk.length == 1 && chunk[0].lowercaseChar() == 'v') {
            pasteClipboardToTerminal()
            if (terminalCtrlLatched) terminalCtrlLatched = false
            if (terminalAltLatched) terminalAltLatched = false
            return
        }

        val processed = if (terminalVirtualFnDown) {
            val fnResult = applyTermuxFnBindings(chunk, viewModel.terminalEmulator.cursorKeysApplicationMode)
            if (fnResult.showVolumeUi) {
                val audio = context.getSystemService(AudioManager::class.java)
                audio?.adjustSuggestedStreamVolume(
                    AudioManager.ADJUST_SAME,
                    AudioManager.USE_DEFAULT_STREAM_TYPE,
                    AudioManager.FLAG_SHOW_UI
                )
            }
            if (fnResult.toggleKeyboard) {
                if (imeVisible) {
                    keyboardController?.hide()
                } else {
                    terminalFocusRequester.requestFocus()
                    keyboardController?.show()
                }
            }
            fnResult.output
        } else {
            applyTerminalModifiers(
                input = chunk,
                ctrl = ctrlActive,
                alt = altActive
            )
        }
        if (processed.isEmpty()) return
        if (BuildConfig.DEBUG && processed.contains('~')) {
            Log.d("TerminalInput", "SENDING to server: '${processed.map { String.format("%04x", it.code) }}' fnDown=$terminalVirtualFnDown")
        }
        viewModel.sendTerminalInput(processed)
        if (terminalCtrlLatched) terminalCtrlLatched = false
        if (terminalAltLatched) terminalAltLatched = false
    }

    // Keep screen on while on chat screen (if enabled in settings)
    DisposableEffect(keepScreenOn) {
        val window = (context as? android.app.Activity)?.window
        if (keepScreenOn) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Image attachments — backed by ViewModel URIs for draft persistence
    val attachments = remember { mutableStateListOf<ImageAttachment>() }

    // Rebuild attachment objects from persisted draft URIs on first composition
    LaunchedEffect(draftAttachmentUris, compressImageAttachments, imageAttachmentMaxLongSide, imageAttachmentWebpQuality) {
        // Only rebuild if attachments list doesn't match URIs (e.g. on session restore)
        val currentUris = attachments.map { it.uri.toString() }.toSet()
        val draftUriSet = draftAttachmentUris.toSet()
        if (currentUris == draftUriSet) return@LaunchedEffect

        val restored = mutableListOf<ImageAttachment>()
        for (uriStr in draftAttachmentUris) {
            // Skip URIs already present
            if (uriStr in currentUris) {
                val existing = attachments.first { it.uri.toString() == uriStr }
                restored.add(existing)
                continue
            }
            try {
                val uri = android.net.Uri.parse(uriStr)
                if (uriStr.startsWith("data:image/", ignoreCase = true)) {
                    val mime = uriStr.substringAfter("data:").substringBefore(';').ifBlank { "image/png" }
                    val syntheticName = "image.${mime.substringAfter('/', "png")}".lowercase()
                    restored.add(
                        ImageAttachment(
                            uri = uri,
                            mime = mime,
                            filename = syntheticName,
                            dataUrl = uriStr,
                        )
                    )
                    continue
                }
                val prepared = buildAttachmentFromUri(
                    contentResolver = context.contentResolver,
                    uri = uri,
                    compressImages = compressImageAttachments,
                    maxLongSidePx = imageAttachmentMaxLongSide,
                    webpQuality = imageAttachmentWebpQuality
                )
                if (prepared != null) {
                    restored.add(prepared.attachment)
                }
            } catch (e: Exception) {
                Log.w("ChatScreen", "Failed to restore attachment $uriStr: ${e.message}")
                // Remove invalid URI from draft
                viewModel.removeDraftAttachment(draftAttachmentUris.indexOf(uriStr))
            }
        }
        attachments.clear()
        attachments.addAll(restored)
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        coroutineScope.launch {
            val optimizedComparisons = mutableListOf<AttachmentComparison>()
            for (uri in uris) {
                try {
                    // Take persistable URI permission so the URI survives app restarts
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        // Not all URIs support persistable permissions — that's OK
                    }

                    val prepared = buildAttachmentFromUri(
                        contentResolver = context.contentResolver,
                        uri = uri,
                        compressImages = compressImageAttachments,
                        maxLongSidePx = imageAttachmentMaxLongSide,
                        webpQuality = imageAttachmentWebpQuality
                    ) ?: continue

                    attachments.add(prepared.attachment)
                    viewModel.addDraftAttachment(uri.toString())
                    prepared.comparison?.let { optimizedComparisons.add(it) }
                } catch (_: Exception) {
                    // Skip files that fail to read
                }
            }
            if (optimizedComparisons.isNotEmpty()) {
                val totalOriginal = optimizedComparisons.sumOf { it.originalBytes }
                val totalOptimized = optimizedComparisons.sumOf { it.optimizedBytes }
                val totalTokensBefore = optimizedComparisons.sumOf { it.originalEstimatedTokens }
                val totalTokensAfter = optimizedComparisons.sumOf { it.optimizedEstimatedTokens }
                snackbarHostState.showSnackbar(
                    context.getString(
                        R.string.chat_images_optimized_summary,
                        optimizedComparisons.size,
                        formatFileSize(totalOriginal),
                        formatFileSize(totalOptimized),
                        totalTokensBefore,
                        totalTokensAfter
                    )
                )
            }
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris: List<Uri> ->
        coroutineScope.launch {
            var rejected = 0
            for (uri in uris) {
                try {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val prepared = buildAttachmentFromUri(
                        contentResolver = context.contentResolver,
                        uri = uri,
                        compressImages = compressImageAttachments,
                        maxLongSidePx = imageAttachmentMaxLongSide,
                        webpQuality = imageAttachmentWebpQuality,
                    )
                    if (prepared == null) {
                        rejected++
                    } else {
                        attachments.add(prepared.attachment)
                        viewModel.addDraftAttachment(uri.toString())
                    }
                } catch (e: Exception) {
                    Log.w("ChatScreen", "Failed to attach document $uri", e)
                    rejected++
                }
            }
            if (rejected > 0) {
                snackbarHostState.showSnackbar(context.getString(R.string.chat_file_attachment_rejected, rejected))
            }
        }
    }

    // Session export via SAF (Storage Access Framework)
    // Flow: menu click → SAF file picker → stream API responses directly to file
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportSession(context, uri) { success ->
                coroutineScope.launch {
                    if (success) {
                        snackbarHostState.showSnackbar(context.getString(R.string.chat_session_exported))
                    } else {
                        snackbarHostState.showSnackbar(context.getString(R.string.chat_session_export_failed))
                    }
                }
            }
        }
    }

    var pendingImageSave by remember { mutableStateOf<ImageSaveRequest?>(null) }
    val saveImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/*")
    ) { uri: Uri? ->
        val request = pendingImageSave
        pendingImageSave = null
        if (uri == null || request == null) return@rememberLauncherForActivityResult

        coroutineScope.launch {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(request.bytes) }
                    ?: error("Unable to open output stream")
            }.onSuccess {
                snackbarHostState.showSnackbar(context.getString(R.string.chat_image_saved))
            }.onFailure {
                snackbarHostState.showSnackbar(context.getString(R.string.chat_image_save_failed))
            }
        }
    }

    val requestSaveImage: (ByteArray, String, String?) -> Unit = { bytes, mime, filenameHint ->
        val baseName = filenameHint
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }
            ?: "image_${System.currentTimeMillis()}"
        val fileName = "$baseName.${extensionForMime(mime)}"
        pendingImageSave = ImageSaveRequest(bytes = bytes, mime = mime, filename = fileName)
        saveImageLauncher.launch(fileName)
    }

    // Consume attachments shared from other apps via ACTION_SEND (one-shot)
    LaunchedEffect(initialSharedAttachments) {
        if (initialSharedAttachments.isEmpty()) return@LaunchedEffect
        val optimizedComparisons = mutableListOf<AttachmentComparison>()
        var rejected = 0
        for (uri in initialSharedAttachments) {
            try {
                // Take persistable URI permission so the URI survives app restarts
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Not all URIs support persistable permissions — that's OK
                }

                val prepared = buildAttachmentFromUri(
                    contentResolver = context.contentResolver,
                    uri = uri,
                    compressImages = compressImageAttachments,
                    maxLongSidePx = imageAttachmentMaxLongSide,
                    webpQuality = imageAttachmentWebpQuality
                )
                if (prepared == null) {
                    rejected++
                    continue
                }

                attachments.add(prepared.attachment)
                prepared.comparison?.let { optimizedComparisons.add(it) }
                viewModel.addDraftAttachment(uri.toString())
            } catch (e: Exception) {
                Log.w("ChatScreen", "Failed to read shared attachment $uri", e)
                rejected++
            }
        }
        if (rejected > 0) {
            snackbarHostState.showSnackbar(context.getString(R.string.chat_file_attachment_rejected, rejected))
        }
        if (optimizedComparisons.isNotEmpty()) {
            val totalOriginal = optimizedComparisons.sumOf { it.originalBytes }
            val totalOptimized = optimizedComparisons.sumOf { it.optimizedBytes }
            val totalTokensBefore = optimizedComparisons.sumOf { it.originalEstimatedTokens }
            val totalTokensAfter = optimizedComparisons.sumOf { it.optimizedEstimatedTokens }
            snackbarHostState.showSnackbar(
                context.getString(
                    R.string.chat_images_optimized_summary,
                    optimizedComparisons.size,
                    formatFileSize(totalOriginal),
                    formatFileSize(totalOptimized),
                    totalTokensBefore,
                    totalTokensAfter
                )
            )
        }
        onSharedAttachmentsConsumed()
    }

    // Show errors as snackbar when messages are already loaded
    LaunchedEffect(uiState.error) {
        val error = uiState.error
        if (error != null && uiState.messages.isNotEmpty()) {
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Whether auto-scroll should follow new content.
    // Disabled when user manually scrolls up; re-enabled when user scrolls back to bottom.
    val autoScrollEnabledState = remember { mutableStateOf(true) }
    var autoScrollEnabled by autoScrollEnabledState

    // 未读新消息：用户上滑离开底部后又有新消息（messageCount 增加）时为 true，
    // 点击回底部或滚动到底后清除，用于回底部按钮的小红点。
    val hasUnreadMessagesState = remember { mutableStateOf(false) }
    var hasUnreadMessages by hasUnreadMessagesState
    var lastSeenMessageCount by remember { mutableStateOf(0) }

    // True when the very bottom of the list is visible (accounting for offset within tall items)
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val visibleItems = info.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf true
            // With reverseLayout, index 0 is the bottommost item (conversation_bottom spacer)
            // Check if it's visible in the viewport
            val bottomItem = visibleItems.firstOrNull { it.index == 0 }
            if (bottomItem == null) return@derivedStateOf false
            // Bottom-most item is visible — check if its bottom edge is within the viewport
            val itemBottom = bottomItem.offset + bottomItem.size
            val viewportEnd = info.viewportEndOffset
            itemBottom <= viewportEnd + 50 // 50px tolerance
        }
    }

    // Track if we're doing a programmatic scroll to avoid disabling auto-scroll
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    // When user touches the list, disable auto-scroll; re-enable when they reach the bottom
    LaunchedEffect(listState.isScrollInProgress, isAtBottom) {
        if (listState.isScrollInProgress && !isProgrammaticScroll) {
            // User is actively dragging/flinging — disable auto-scroll
            autoScrollEnabled = false
        } else if (isAtBottom) {
            // User stopped scrolling and ended up at the bottom — re-enable
            autoScrollEnabled = true
            hasUnreadMessages = false
        }
    }

    // Reset programmatic scroll flag when scroll completes
    LaunchedEffect(isProgrammaticScroll) {
        if (isProgrammaticScroll) {
            snapshotFlow { listState.isScrollInProgress }
                .filter { !it }
                .first()
            isProgrammaticScroll = false
        }
    }


    // Auto-scroll to bottom when new content arrives (only if auto-scroll is enabled)
    // Track message count, part count, and content length of the last part to catch streaming updates
    val messageCount = uiState.messages.size
    val lastPartCount = uiState.messages.lastOrNull()?.parts?.size ?: 0
    val lastContentLength = uiState.messages.lastOrNull()?.parts?.lastOrNull()?.let { part ->
        when (part) {
            is Part.Text -> part.text.length
            is Part.Reasoning -> part.text.length
            is Part.Tool -> when (val s = part.state) {
                is ToolState.Completed -> s.output.length
                is ToolState.Error -> s.error.length
                is ToolState.Running -> s.title?.length ?: 1
                is ToolState.Pending -> 0
            }
            else -> 0
        }
    } ?: 0
    val pendingInteractions = uiState.pendingInteractions
    val pendingCount = pendingInteractions.size
    val isBusy = isWorkingSessionStatus(uiState.sessionStatus)
    LaunchedEffect(messageCount, lastPartCount, lastContentLength, pendingCount, isBusy, imeVisible) {
        if (messageCount > lastSeenMessageCount) {
            if (!autoScrollEnabled && !isAtBottom) {
                hasUnreadMessages = true
            }
            lastSeenMessageCount = messageCount
        } else if (messageCount < lastSeenMessageCount) {
            lastSeenMessageCount = messageCount
        }
        if (messageCount > 0 && autoScrollEnabled) {
            isProgrammaticScroll = true
            listState.scrollToItem(0)
        }
    }

    // Also auto-scroll when first loading
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && messageCount > 0) {
            isProgrammaticScroll = true
            listState.scrollToItem(0)
            autoScrollEnabled = true
        }
    }

    CompositionLocalProvider(
        LocalChatFontSize provides chatFontSize,
        LocalChatLineHeight provides chatLineHeight,
        LocalCodeWordWrap provides codeWordWrap,
        LocalCompactMessages provides compactMessages,
        LocalCollapseTools provides collapseTools,
        LocalExpandReasoning provides expandReasoning,
        LocalShowTurnDividers provides showTurnDividers,
        LocalHapticFeedbackEnabled provides AppHapticConfig(
            enabled = hapticEnabled,
            durationMillis = hapticDurationMillis,
            amplitude = hapticAmplitude,
        ),
        LocalImageSaveRequest provides requestSaveImage,
    ) {
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChatScreenTopBar(
                viewModel = viewModel,
                uiState = uiState,
                isServerConnected = isServerConnected,
                isAmoled = isAmoled,
                isGitRepository = isGitRepository,
                sessionDiffs = sessionDiffs,
                snackbarHostState = snackbarHostState,
                coroutineScope = coroutineScope,
                context = context,
                clipboardManager = clipboardManager,
                exportLauncher = exportLauncher,
                inputTextState = inputTextState,
                isTerminalModeState = isTerminalModeState,
                inputModeState = inputModeState,
                showMenuState = showMenuState,
                showRenameDialogState = showRenameDialogState,
                showSessionDiffDialogState = showSessionDiffDialogState,
                showTimelineDialogState = showTimelineDialogState,
                showProjectOverviewState = showProjectOverviewState,
                showAttachmentOptionsState = showAttachmentOptionsState,
                showSubagentContextDetailsState = showSubagentContextDetailsState,
                onNavigateBack = onNavigateBack,
                onNavigateToSession = onNavigateToSession,
                onOpenInWebView = onOpenInWebView,
                onOpenWorkspace = onOpenWorkspace,
                onOpenAgentsMd = onOpenAgentsMd,
                onOpenGit = onOpenGit,
                onOpenSharedSession = onOpenSharedSession,
            )
        },
        bottomBar = {
            ChatScreenBottomBar(
                viewModel = viewModel,
                uiState = uiState,
                onNavigateToSession = onNavigateToSession,
                snackbarHostState = snackbarHostState,
                coroutineScope = coroutineScope,
                context = context,
                clipboardManager = clipboardManager,
                view = view,
                isListening = isListening,
                voiceLevel = voiceLevel,
                voiceEnabled = voiceEnabled,
                asrSuppressedState = asrSuppressedState,
                startVoiceInput = startVoiceInput,
                fileSearchResults = fileSearchResults,
                confirmedFilePaths = confirmedFilePaths,
                customCommands = customCommands,
                confirmBeforeSend = confirmBeforeSend,
                hapticEnabled = hapticEnabled,
                hapticDurationMillis = hapticDurationMillis,
                hapticAmplitude = hapticAmplitude,
                isShellMode = isShellMode,
                attachments = attachments,
                requestSaveImage = requestSaveImage,
                isBusy = isBusy,
                inputTextState = inputTextState,
                inputModeState = inputModeState,
                isTerminalModeState = isTerminalModeState,
                showModelPickerState = showModelPickerState,
                showRenameDialogState = showRenameDialogState,
                showCustomCommandsDialogState = showCustomCommandsDialogState,
                showAttachmentOptionsState = showAttachmentOptionsState,
                showTemplatePickerState = showTemplatePickerState,
                showDocumentGenerateDialogState = showDocumentGenerateDialogState,
                showSendConfirmDialogState = showSendConfirmDialogState,
                pendingSendActionState = pendingSendActionState,
            )
        }
    ) { padding ->
        ChatScreenMessageBody(
            viewModel = viewModel,
            padding = padding,
            uiState = uiState,
            inputTextState = inputTextState,
            listState = listState,
            isTerminalModeState = isTerminalModeState,
            terminalCtrlLatchedState = terminalCtrlLatchedState,
            terminalAltLatchedState = terminalAltLatchedState,
            showTerminalPanelHintOverlayState = showTerminalPanelHintOverlayState,
            terminalOverlayHeightPxState = terminalOverlayHeightPxState,
            terminalFocusRequester = terminalFocusRequester,
            snackbarHostState = snackbarHostState,
            coroutineScope = coroutineScope,
            context = context,
            isAmoled = isAmoled,
            keyboardController = keyboardController,
            clipboardManager = clipboardManager,
            density = density,
            usesGestureNavigation = usesGestureNavigation,
            terminalVersion = terminalVersion,
            terminalConnected = terminalConnected,
            terminalTabs = terminalTabs,
            activeTerminalTabId = activeTerminalTabId,
            activeTerminalTab = activeTerminalTab,
            terminalFontSizeSp = terminalFontSizeSp,
            terminalDrawerState = terminalDrawerState,
            pasteClipboardToTerminal = ::pasteClipboardToTerminal,
            sendTerminalChunk = ::sendTerminalChunk,
            autoScrollEnabledState = autoScrollEnabledState,
            hasUnreadMessagesState = hasUnreadMessagesState,
            isAtBottom = isAtBottom,
            showDocumentGenerateDialogState = showDocumentGenerateDialogState,
            pendingInteractions = pendingInteractions,
            isBusy = isBusy,
            onNavigateToChildSession = onNavigateToChildSession,
            serverBaseUrl = serverBaseUrl,
            onOpenChatLink = onOpenChatLink,
        )
    }

    ChatScreenOverlayDialogs(
        viewModel = viewModel,
        uiState = uiState,
        onManageModels = onManageModels,
        isSummarizing = isSummarizing,
        summaryText = summaryText,
        summaryError = summaryError,
        summaryVisible = summaryVisible,
        sessionDiffs = sessionDiffs,
        sessionTodos = sessionTodos,
        customCommands = customCommands,
        promptTemplates = promptTemplates,
        confirmBeforeSend = confirmBeforeSend,
        isAmoled = isAmoled,
        snackbarHostState = snackbarHostState,
        coroutineScope = coroutineScope,
        context = context,
        pendingInteractions = pendingInteractions,
        attachments = attachments,
        imagePickerLauncher = imagePickerLauncher,
        documentPickerLauncher = documentPickerLauncher,
        inputTextState = inputTextState,
        inputModeState = inputModeState,
        showModelPickerState = showModelPickerState,
        showRenameDialogState = showRenameDialogState,
        showCustomCommandsDialogState = showCustomCommandsDialogState,
        showSessionDiffDialogState = showSessionDiffDialogState,
        showTimelineDialogState = showTimelineDialogState,
        showProjectOverviewState = showProjectOverviewState,
        showAttachmentOptionsState = showAttachmentOptionsState,
        showTemplatePickerState = showTemplatePickerState,
        showSubagentContextDetailsState = showSubagentContextDetailsState,
        showSendConfirmDialogState = showSendConfirmDialogState,
        pendingSendActionState = pendingSendActionState,
        pendingTemplatePromptState = pendingTemplatePromptState,
    )
    } // CompositionLocalProvider
}


