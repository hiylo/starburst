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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.drawscope.Stroke
import org.hiylo.starburst.service.SessionNotificationCoordinator
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import org.hiylo.starburst.domain.model.*
import org.hiylo.starburst.data.api.AgentInfo
import org.hiylo.starburst.data.api.CommandInfo
import org.hiylo.starburst.data.api.PromptPart
import org.hiylo.starburst.MainActivity
import org.hiylo.starburst.ui.screens.settings.SessionExport
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.abs
import android.net.Uri
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import org.hiylo.starburst.logging.AppLogger as Log
import android.webkit.WebView
import org.hiylo.starburst.BuildConfig
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.ProviderIcon
import org.hiylo.starburst.ui.components.AppHaptics
import org.hiylo.starburst.ui.components.AppHapticConfig
import org.hiylo.starburst.ui.components.AppLoadingEdge
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.AppSecondaryButton
import org.hiylo.starburst.ml.MnnAsr
import org.hiylo.starburst.ui.components.appAmoledBorder
import org.hiylo.starburst.ui.components.appPopupBorder
import org.hiylo.starburst.ui.components.appPopupContainerColor
import org.hiylo.starburst.ui.components.isAmoledTheme


/**
 * Chat Screen - conversation view with native markdown rendering.
 * Shows messages with streaming text rendered via mikepenz markdown renderer.
 */

// ============ Chat Settings via CompositionLocal ============

/** Chat font size setting: "small", "medium", "large". */
val LocalChatFontSize = compositionLocalOf { "medium" }

/** Chat line spacing multiplier (1.0–2.0). */
val LocalChatLineHeight = compositionLocalOf { 1f }

/** Whether code blocks use word wrap instead of horizontal scroll. */
val LocalCodeWordWrap = compositionLocalOf { false }

/** Whether compact message spacing is enabled. */
val LocalCompactMessages = compositionLocalOf { false }

/** Whether tool cards are collapsed by default. */
val LocalCollapseTools = compositionLocalOf { false }

val LocalExpandReasoning = compositionLocalOf { false }

val LocalShowTurnDividers = compositionLocalOf { true }

/** Whether haptic feedback is enabled. */
val LocalHapticFeedbackEnabled = compositionLocalOf { AppHapticConfig() }

/** Image save request callback available to image preview composables. */
val LocalImageSaveRequest = compositionLocalOf<(ByteArray, String, String?) -> Unit> { { _, _, _ -> } }

/**
 * Chat link handling config, provided around message content.
 * Same-origin links (relative to [serverBaseUrl]) are opened in-app via [openInApp];
 * any other link falls back to the system browser.
 */
internal data class ChatLinkHandler(
    val serverBaseUrl: String = "",
    val openInApp: (String) -> Unit = {},
)

internal val LocalChatLinkHandler = compositionLocalOf { ChatLinkHandler() }

/**
 * Returns true when [link] belongs to the same origin as [serverBaseUrl]
 * (scheme + host + port prefix match). Used to decide whether a conversation
 * link should open inside the built-in WebView instead of the system browser.
 */
internal fun isSameServerUrl(link: String, serverBaseUrl: String): Boolean {
    if (serverBaseUrl.isBlank() || link.isBlank()) return false
    val base = serverBaseUrl.trimEnd('/').lowercase()
    val target = link.trim().lowercase()
    return target == base || target.startsWith("$base/")
}


/**
 * Perform a light haptic tick if haptic feedback is enabled.
 * Call from composable context or from a click lambda that has access to a View.
 */
internal fun performHaptic(view: android.view.View, config: AppHapticConfig) {
    AppHaptics.perform(view, config)
}

/**
 * Agent color matching the TUI's opencode theme.
 * Color cycle: secondary, accent, success, warning, primary, error, info
 * (same order as TUI's local.tsx color array).
 * Fixed palette — tool-specific color, not themed.
 */
private val agentColorCycle = listOf(
    Color(0xFF5C9CF5), // secondary — build (blue)
    Color(0xFF9D7CD8), // accent — plan (purple)
    Color(0xFF7FD88F), // success (green)
    Color(0xFFF5A742), // warning (orange)
    Color(0xFFFAB283), // primary (peach)
    Color(0xFFE06C75), // error (red)
    Color(0xFF56B6C2)  // info (cyan)
)

private fun agentColor(agentName: String, agents: List<AgentInfo> = emptyList()): Color {
    val index = agents.indexOfFirst { it.name == agentName }
    return if (index >= 0) {
        agentColorCycle[index % agentColorCycle.size]
    } else {
        agentColorCycle[0]
    }
}

/**
 * Conditionally applies horizontalScroll for code blocks.
 * When word wrap is enabled, no horizontal scroll is applied.
 */

/**
 * Slash command definition for the suggestion popup.
 * @param name Command name without the "/" prefix
 * @param description Human-readable description
 * @param type "server" commands are sent via API, "client" commands trigger local actions
 */
private data class SlashCommand(
    val name: String,
    val description: String?,
    val type: String, // "server", "client", or "custom"
    val prompt: String? = null, // for "custom" commands: text inserted into the input
)

private enum class ChatInputMode {
    NORMAL,
    SHELL
}

/** Client-side slash commands that mirror the original opencode TUI. */
@Composable
private fun clientCommands(): List<SlashCommand> {
    return listOf(
        SlashCommand("new", stringResource(R.string.cmd_new), "client"),
        SlashCommand("compact", stringResource(R.string.cmd_compact), "client"),
        SlashCommand("fork", stringResource(R.string.cmd_fork), "client"),
        SlashCommand("share", stringResource(R.string.cmd_share), "client"),
        SlashCommand("unshare", stringResource(R.string.cmd_unshare), "client"),
        SlashCommand("undo", stringResource(R.string.cmd_undo), "client"),
        SlashCommand("redo", stringResource(R.string.cmd_redo), "client"),
        SlashCommand("rename", stringResource(R.string.cmd_rename), "client"),
        SlashCommand("shell", stringResource(R.string.cmd_shell_mode), "client"),
    )
}



@Composable
private fun ImeVisibilityTracker(onChanged: (Boolean) -> Unit) {
    val density = LocalDensity.current
    val visible = WindowInsets.ime.getBottom(density) > 0
    LaunchedEffect(visible) { onChanged(visible) }
}

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
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
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
    var showModelPicker by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showCustomCommandsDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSessionDiffDialog by remember { mutableStateOf(false) }
    var showAttachmentOptions by remember { mutableStateOf(false) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var showSubagentContextDetails by remember { mutableStateOf(false) }
    var isTerminalMode by rememberSaveable { mutableStateOf(startInTerminalMode) }
    var terminalCtrlLatched by rememberSaveable { mutableStateOf(false) }
    var terminalAltLatched by rememberSaveable { mutableStateOf(false) }
    var terminalVirtualCtrlDown by remember { mutableStateOf(false) }
    var terminalVirtualFnDown by remember { mutableStateOf(false) }
    var showTerminalPanelHintOverlay by remember { mutableStateOf(false) }
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
    val voiceEnabled = remember { MnnAsr.modelDirectory(context) != null } || backendAsrAvailable

    // 录音开始前输入框里已有的文字。识别引擎返回的是「累积全文」而不是增量片段，
    // 所以每次都用「前缀 + 累积文本」整体重写输入框，绝不能往末尾追加——引擎中途
    // 回改前文时（"昨天是" → "昨天是 MONDAY"），追加会把上一版的字留在原地，
    // 表现为重字。
    var asrPrefix by remember { mutableStateOf("") }

    // 发送后置 true：识别完成（含后台 refine 迟到回调）不得再写回输入框，
    // 否则用户刚发送、输入框已清空，校对结果又把它塞回来，表现为"发了还在还变多"。
    // 下一次按下麦克风时重置为 false。
    var asrSuppressed by remember { mutableStateOf(false) }

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
    var terminalOverlayHeightPx by remember { mutableStateOf(0) }

    // @ file mention state
    val fileSearchResults by viewModel.fileSearchResults.collectAsState()
    val confirmedFilePaths by viewModel.confirmedFilePaths.collectAsState()
    val customCommands by viewModel.customCommands.collectAsState()

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
    var showSendConfirmDialog by remember { mutableStateOf(false) }
    // Pending send action: stored so the confirm dialog can trigger it
    var pendingSendAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var inputMode by rememberSaveable { mutableStateOf(ChatInputMode.NORMAL.name) }
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
    var autoScrollEnabled by remember { mutableStateOf(true) }

    // 未读新消息：用户上滑离开底部后又有新消息（messageCount 增加）时为 true，
    // 点击回底部或滚动到底后清除，用于回底部按钮的小红点。
    var hasUnreadMessages by remember { mutableStateOf(false) }
    var lastSeenMessageCount by remember { mutableStateOf(0) }

    // True when the very bottom of the list is visible (accounting for offset within tall items)
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            val totalItems = info.totalItemsCount
            if (lastVisible.index < totalItems - 1) return@derivedStateOf false
            // Last item is visible — check if its bottom edge is within the viewport
            val itemBottom = lastVisible.offset + lastVisible.size
            val viewportEnd = info.viewportEndOffset
            itemBottom <= viewportEnd + 50 // 50px tolerance
        }
    }

    // When user touches the list, disable auto-scroll; re-enable when they reach the bottom
    LaunchedEffect(listState.isScrollInProgress, isAtBottom) {
        if (listState.isScrollInProgress) {
            // User is actively dragging/flinging — disable auto-scroll
            autoScrollEnabled = false
        } else if (isAtBottom) {
            // User stopped scrolling and ended up at the bottom — re-enable
            autoScrollEnabled = true
            hasUnreadMessages = false
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
            val lastIndex = listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1
            listState.scrollToItem(lastIndex)
        }
    }

    // Also auto-scroll when first loading
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && messageCount > 0) {
            val lastIndex = listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1
            listState.scrollToItem(lastIndex)
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
            if (!isTerminalMode && uiState.sessionLoaded) {
            Column {
            Box {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.sessionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Subtitle: total tokens and cost for the session
                        val totalTokens = uiState.totalInputTokens + uiState.totalOutputTokens
                        if (totalTokens > 0 || uiState.totalCost > 0) {
                            val parts = mutableListOf<String>()
                            if (totalTokens > 0) {
                                parts.add(stringResource(R.string.chat_tokens_summary, formatTokenCount(totalTokens)))
                            }
                            if (uiState.totalCost > 0) {
                                parts.add(stringResource(R.string.chat_cost_format, String.format("%.4f", uiState.totalCost)))
                            }
                            if (parts.isNotEmpty()) {
                                Text(
                                    text = parts.joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (
                        uiState.parentSessionId != null &&
                        uiState.contextWindow > 0 &&
                        uiState.lastContextTokens > 0
                    ) {
                        val percentage = Math.round(
                            uiState.lastContextTokens.toDouble() / uiState.contextWindow * 100,
                        ).toInt()
                        val indicatorColor = when {
                            percentage >= 90 -> MaterialTheme.colorScheme.error
                            percentage >= 70 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                        IconButton(onClick = { showSubagentContextDetails = true }) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = {
                                        (uiState.lastContextTokens.toFloat() / uiState.contextWindow)
                                            .coerceIn(0f, 1f)
                                    },
                                    modifier = Modifier.size(30.dp),
                                    color = indicatorColor,
                                    trackColor = indicatorColor.copy(alpha = 0.16f),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = "$percentage%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = indicatorColor,
                                )
                            }
                        }
                    }
                    if (uiState.parentSessionId == null) Box {
                        val isAmoled = isAmoledTheme()
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        if (inputText.text.isNotEmpty()) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = 3.dp, top = 3.dp)
                                    .size(15.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.AttachFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(10.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.appPopupBorder(),
                            containerColor = appPopupContainerColor(),
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_attach)) },
                                leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    inputMode = ChatInputMode.NORMAL.name
                                    showAttachmentOptions = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tool_terminal)) },
                                leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    isTerminalMode = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_workspace_files)) },
                                onClick = {
                                    showMenu = false
                                    onOpenWorkspace(viewModel.getSessionDirectory().orEmpty())
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_agents_md)) },
                                onClick = {
                                    showMenu = false
                                    onOpenAgentsMd(viewModel.getSessionDirectory().orEmpty())
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_open_in_web)) },
                                onClick = {
                                    showMenu = false
                                    onOpenInWebView()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Language, contentDescription = null)
                                },
                            )
                            if (isGitRepository) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_git)) },
                                    onClick = {
                                        showMenu = false
                                        onOpenGit()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.AccountTree, contentDescription = null)
                                    },
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_reload_session)) },
                                onClick = {
                                    showMenu = false
                                    viewModel.reloadSession()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_rename_session)) },
                                onClick = {
                                    showMenu = false
                                    showRenameDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                }
                            )
                            if (sessionDiffs.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_view_changes, sessionDiffs.size)) },
                                    onClick = {
                                        showMenu = false
                                        showSessionDiffDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Difference, contentDescription = null)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_new_session)) },
                                onClick = {
                                    showMenu = false
                                    viewModel.createNewSession { session ->
                                        if (session != null) {
                                            onNavigateToSession(session.id)
                                        } else {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(context.getString(R.string.chat_session_create_failed))
                                            }
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_fork_session)) },
                                onClick = {
                                    showMenu = false
                                    viewModel.forkSession { session ->
                                        if (session != null) {
                                            onNavigateToSession(session.id)
                                        } else {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(context.getString(R.string.chat_fork_failed))
                                            }
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.CopyAll, contentDescription = null)
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_compact_session)) },
                                onClick = {
                                    showMenu = false
                                    viewModel.compactSession { ok ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (ok) context.getString(R.string.chat_session_compacted) else context.getString(R.string.chat_session_compact_failed)
                                            )
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Compress, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_review_changes)) },
                                onClick = {
                                    showMenu = false
                                    viewModel.executeCommand("review") { ok ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (ok) context.getString(R.string.chat_command_executed, "review") else context.getString(R.string.chat_command_failed, "review")
                                            )
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.RateReview, contentDescription = null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_summarize_session)) },
                                onClick = {
                                    showMenu = false
                                    viewModel.summarizeSession()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                },
                            )
                            // Show Share or Unshare depending on current share status
                            if (uiState.shareUrl != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.cmd_unshare)) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.unshareSession { ok ->
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(
                                                    if (ok) context.getString(R.string.chat_session_unshared) else context.getString(R.string.chat_session_unshare_failed)
                                                )
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.LinkOff, contentDescription = null)
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_share_session)) },
                                    onClick = {
                                        showMenu = false
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
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Share, contentDescription = null)
                                    }
                                )
                            }
                            if (uiState.shareUrl != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_view_share)) },
                                    onClick = {
                                        showMenu = false
                                        val shareId = uiState.shareUrl.orEmpty().trimEnd('/').substringAfterLast('/')
                                        if (shareId.isNotBlank()) onOpenSharedSession(shareId)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Visibility, contentDescription = null)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_copy_share_link)) },
                                    onClick = {
                                        showMenu = false
                                        uiState.shareUrl?.let { url ->
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(url))
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(context.getString(R.string.chat_share_url_copied))
                                            }
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Link, contentDescription = null)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_export_session)) },
                                onClick = {
                                    showMenu = false
                                    val slug = uiState.sessionTitle
                                        .take(30)
                                        .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                                        .ifBlank { "session" }
                                    exportLauncher.launch("$slug.json")
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.FileDownload, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_export_markdown)) },
                                onClick = {
                                    showMenu = false
                                    val content = SessionExport.toMarkdown(uiState.sessionTitle, uiState.messages)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/markdown"
                                        putExtra(Intent.EXTRA_SUBJECT, uiState.sessionTitle)
                                        putExtra(Intent.EXTRA_TEXT, content)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, context.getString(R.string.menu_export_markdown))
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Description, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_export_json)) },
                                onClick = {
                                    showMenu = false
                                    val content = SessionExport.toJson(uiState.sessionTitle, uiState.messages)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_SUBJECT, uiState.sessionTitle)
                                        putExtra(Intent.EXTRA_TEXT, content)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, context.getString(R.string.menu_export_json))
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DataObject, contentDescription = null)
                                }
                            )
                            }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
                AppLoadingEdge(
                    active = uiState.isLoading && uiState.messages.isEmpty(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
                if (!isServerConnected) {
                    DisconnectedServerBanner()
                }
            }
            }
        },
        bottomBar = {
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
                lastContextTokens = uiState.lastContextTokens,
                contextUsage = uiState.contextUsage,
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isTerminalMode) PaddingValues(0.dp) else padding)
        ) {
            when {
                isTerminalMode -> {
                    val overlayHeightDp = with(density) { terminalOverlayHeightPx.toDp() }

                    ModalNavigationDrawer(
                        drawerState = terminalDrawerState,
                        gesturesEnabled = true,
                        drawerContent = {
                            ModalDrawerSheet(
                                drawerContainerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
                                drawerContentColor = MaterialTheme.colorScheme.onSurface,
                                drawerTonalElevation = 0.dp,
                                drawerShape = RoundedCornerShape(0.dp),
                                windowInsets = WindowInsets(0, 0, 0, 0),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .widthIn(min = 240.dp, max = 320.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .windowInsetsPadding(
                                                WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical),
                                            )
                                            .padding(vertical = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        items(terminalTabs, key = { it.id }) { tab ->
                                            val selected = tab.id == activeTerminalTabId
                                            val drawerItemShape = RoundedCornerShape(12.dp)
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(drawerItemShape)
                                                    .then(
                                                        if (isAmoled && selected) {
                                                            Modifier.border(
                                                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                                                drawerItemShape
                                                            )
                                                        } else Modifier
                                                    )
                                            ) {
                                                NavigationDrawerItem(
                                                    label = {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            Column(
                                                                modifier = Modifier.weight(1f),
                                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                                            ) {
                                                                Text(
                                                                    text = tab.title,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    style = MaterialTheme.typography.titleMedium,
                                                                    fontWeight = FontWeight.SemiBold
                                                                )
                                                                if (!tab.connected) {
                                                                    val statusText = stringResource(terminalTabStateLabel(tab.state))
                                                                    Surface(
                                                                        shape = RoundedCornerShape(999.dp),
                                                                        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                                                                    ) {
                                                                        Row(
                                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                                            verticalAlignment = Alignment.CenterVertically,
                                                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                                        ) {
                                                                            if (tab.state == TerminalTabState.Starting ||
                                                                                tab.state == TerminalTabState.Reconnecting
                                                                            ) {
                                                                                CircularProgressIndicator(
                                                                                    modifier = Modifier.size(8.dp),
                                                                                    strokeWidth = 1.5.dp,
                                                                                )
                                                                            } else {
                                                                                Box(
                                                                                    modifier = Modifier
                                                                                        .size(6.dp)
                                                                                        .background(
                                                                                            MaterialTheme.colorScheme.error,
                                                                                            CircleShape,
                                                                                        )
                                                                                )
                                                                            }
                                                                            Text(
                                                                                text = statusText,
                                                                                style = MaterialTheme.typography.labelSmall,
                                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            if (tab.recoveryAction != TerminalRecoveryAction.None) {
                                                                val recoveryDescription = stringResource(
                                                                    terminalRecoveryLabel(tab.recoveryAction),
                                                                )
                                                                IconButton(
                                                                    onClick = {
                                                                        viewModel.recoverTerminalTab(tab.id) { ok ->
                                                                            if (!ok) {
                                                                                coroutineScope.launch {
                                                                                    snackbarHostState.showSnackbar(context.getString(R.string.chat_terminal_connect_failed))
                                                                                }
                                                                            }
                                                                        }
                                                                    },
                                                                    modifier = Modifier
                                                                        .size(48.dp)
                                                                        .then(
                                                                            if (isAmoled) {
                                                                                Modifier.border(
                                                                                    1.dp,
                                                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                                                                    CircleShape,
                                                                                )
                                                                            } else Modifier
                                                                        ),
                                                                    colors = IconButtonDefaults.iconButtonColors(
                                                                        containerColor = if (isAmoled) {
                                                                            Color.Black
                                                                        } else {
                                                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                                                                        }
                                                                    )
                                                                ) {
                                                                    Icon(
                                                                        Icons.Default.Refresh,
                                                                        contentDescription = recoveryDescription,
                                                                    )
                                                                }
                                                            }
                                                            IconButton(
                                                                onClick = { viewModel.closeTerminalTab(tab.id) },
                                                                modifier = Modifier
                                                                    .size(48.dp)
                                                                    .then(
                                                                        if (isAmoled) {
                                                                            Modifier.border(
                                                                                1.dp,
                                                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                                                                CircleShape,
                                                                            )
                                                                        } else Modifier
                                                                    ),
                                                                colors = IconButtonDefaults.iconButtonColors(
                                                                    containerColor = if (isAmoled) {
                                                                        Color.Black
                                                                    } else {
                                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                                    }
                                                                )
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.Close,
                                                                    contentDescription = stringResource(R.string.chat_terminal_close_tab),
                                                                )
                                                            }
                                                        }
                                                    },
                                                    selected = selected,
                                                    shape = drawerItemShape,
                                                    colors = NavigationDrawerItemDefaults.colors(
                                                        selectedContainerColor = if (isAmoled) {
                                                            Color.Black
                                                        } else {
                                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                                                        },
                                                        unselectedContainerColor = if (isAmoled) Color.Black else Color.Transparent,
                                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                                        unselectedTextColor = MaterialTheme.colorScheme.onSurface
                                                    ),
                                                    onClick = {
                                                        viewModel.switchTerminalTab(tab.id)
                                                        coroutineScope.launch { terminalDrawerState.close() }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider()

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        AppSecondaryButton(
                                            onClick = {
                                                viewModel.createTerminalTab { ok ->
                                                    if (!ok) {
                                                        coroutineScope.launch {
                                                            snackbarHostState.showSnackbar(context.getString(R.string.chat_terminal_connect_failed))
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp),
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.chat_terminal_new_tab))
                                        }
                                        AppSecondaryButton(
                                            onClick = {
                                                keyboardController?.show()
                                                coroutineScope.launch { terminalDrawerState.close() }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp),
                                        ) {
                                            Icon(Icons.Default.Keyboard, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.chat_terminal_keyboard))
                                        }
                                    }

                                    }

                                    if (isAmoled) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .fillMaxHeight()
                                                .width(1.dp)
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                                        )
                                    }
                                }
                            }
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical),
                                ),
                        ) {
                            SessionTerminalInline(
                                emulator = viewModel.terminalEmulator,
                                terminalVersion = terminalVersion,
                                connected = terminalConnected,
                                focusRequester = terminalFocusRequester,
                                onSendInput = ::sendTerminalChunk,
                                onPaste = ::pasteClipboardToTerminal,
                                onResize = { cols, rows ->
                                    viewModel.resizeTerminal(cols, rows)
                                },
                                fontSizeSp = terminalFontSizeSp,
                                onFontSizeChange = viewModel::setTerminalFontSize,
                                contentBottomPadding = overlayHeightDp,
                                modifier = Modifier.fillMaxSize()
                            )

                            if (activeTerminalTab != null && !activeTerminalTab.connected) {
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(12.dp)
                                        .zIndex(2f),
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isAmoled) {
                                        Color.Black
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (activeTerminalTab.state == TerminalTabState.Starting ||
                                            activeTerminalTab.state == TerminalTabState.Reconnecting
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .background(MaterialTheme.colorScheme.error, CircleShape),
                                            )
                                        }
                                        Text(
                                            text = stringResource(terminalTabStateLabel(activeTerminalTab.state)),
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (activeTerminalTab.recoveryAction != TerminalRecoveryAction.None) {
                                            val recoveryDescription = stringResource(
                                                terminalRecoveryLabel(activeTerminalTab.recoveryAction),
                                            )
                                            IconButton(
                                                onClick = {
                                                    viewModel.recoverTerminalTab(activeTerminalTab.id) { ok ->
                                                        if (!ok) {
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar(
                                                                    context.getString(R.string.chat_terminal_connect_failed),
                                                                )
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(40.dp),
                                                colors = IconButtonDefaults.iconButtonColors(
                                                    containerColor = if (isAmoled) {
                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                                                    } else {
                                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                                                    },
                                                ),
                                            ) {
                                                Icon(
                                                    Icons.Default.Refresh,
                                                    contentDescription = recoveryDescription,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (showTerminalPanelHintOverlay && !terminalDrawerState.isOpen) {
                                TerminalPanelCoachmark(
                                    usesGestureNavigation = usesGestureNavigation,
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .zIndex(3f),
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .fillMaxHeight()
                                    .padding(bottom = overlayHeightDp)
                                    .width(18.dp)
                                    .zIndex(0f)
                                    .pointerInput(terminalDrawerState) {
                                        detectTapGestures(
                                            onLongPress = {
                                                if (!terminalDrawerState.isOpen) {
                                                    showTerminalPanelHintOverlay = false
                                                    coroutineScope.launch { terminalDrawerState.open() }
                                                }
                                            }
                                        )
                                    }
                                    .pointerInput(terminalDrawerState) {
                                        var dragged = 0f
                                        val openThreshold = 32.dp.toPx()
                                        detectHorizontalDragGestures(
                                            onHorizontalDrag = { _, dragAmount ->
                                                if (terminalDrawerState.isOpen) return@detectHorizontalDragGestures
                                                dragged += dragAmount
                                                if (dragged > openThreshold) {
                                                    showTerminalPanelHintOverlay = false
                                                    coroutineScope.launch { terminalDrawerState.open() }
                                                    dragged = 0f
                                                }
                                            },
                                            onDragEnd = { dragged = 0f },
                                            onDragCancel = { dragged = 0f }
                                        )
                                    }
                            ) {
                                if (showTerminalPanelHintOverlay && !terminalDrawerState.isOpen) {
                                    TerminalPanelEdgeHighlight(modifier = Modifier.fillMaxSize())
                                }
                            }

                        TerminalKeyboardOverlay(
                            connected = terminalConnected,
                            ctrlLatched = terminalCtrlLatched,
                            altLatched = terminalAltLatched,
                            cursorApp = viewModel.terminalEmulator.cursorKeysApplicationMode,
                            onToggleDrawer = { coroutineScope.launch { terminalDrawerState.apply { if (isOpen) close() else open() } } },
                            onToggleCtrl = { terminalCtrlLatched = !terminalCtrlLatched },
                            onToggleAlt = { terminalAltLatched = !terminalAltLatched },
                            onSendInput = ::sendTerminalChunk,
                            onCtrlC = { viewModel.sendTerminalInput("\u0003") },
                            onClear = { viewModel.clearTerminalBuffer() },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .zIndex(1f)
                                    .fillMaxWidth()
                                    .onSizeChanged { terminalOverlayHeightPx = it.height }
                            )

                        }
                    }
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
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = stringResource(R.string.chat_type_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.chat_empty_quick_start),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(messageSpacing)
                    ) {
                        // Fork 分支导航：子会话可返回父会话；父会话可查看 fork 分支。
                        if (uiState.parentSessionId != null || uiState.childSessions.isNotEmpty()) {
                            item(key = "fork_branches") {
                                ForkBranchBar(
                                    parentSessionId = uiState.parentSessionId,
                                    childSessions = uiState.childSessions,
                                    onNavigateToSession = onNavigateToSession,
                                )
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
                                        TextButton(onClick = { viewModel.loadOlderMessages() }) {
                                            Text(stringResource(R.string.chat_load_earlier))
                                        }
                                    }
                                }
                            }
                        }

                        items(
                            timeline,
                            key = { it.key },
                        ) { entry ->
                            when (entry) {
                                is ChatTimelineEntry.DateDivider -> DateDividerRow(entry.dayStartMillis)
                                is ChatTimelineEntry.Turn -> {
                            val chatTurn = entry.turn
                            val chatMessage = chatTurn.messages.first()
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

                        pendingInteractions.firstOrNull()?.let { interaction ->
                            item(key = "pending_${interaction::class.simpleName}_${interaction.sessionId}_${interaction.id}") {
                                val position = stringResource(R.string.pending_request_position, 1, pendingInteractions.size)
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

                        // A stable final item lets scrollToItem clamp to the true content bottom,
                        // including spacing and padding below a tall or streaming message.
                        item(key = "conversation_bottom") {
                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    // Scroll-to-bottom FAB
                    if (!isAtBottom && !autoScrollEnabled) {
                        SmallFloatingActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    val lastIndex = listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1
                                    listState.scrollToItem(lastIndex)
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
        }
    }

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
            onSelect = { template ->
                showTemplatePicker = false
                inputText = TextFieldValue(template, TextRange(template.length))
                keyboardController?.show()
            },
            onDismiss = { showTemplatePicker = false },
        )
    }

    if (showSubagentContextDetails) {
        ContextUsageDialog(
            usage = uiState.contextUsage,
            contextWindow = uiState.contextWindow,
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
    } // CompositionLocalProvider
}


@Composable
private fun DisconnectedServerBanner() {
    val isAmoled = isAmoledTheme()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = stringResource(R.string.chat_server_disconnected),
                style = MaterialTheme.typography.bodySmall,
                color = if (isAmoled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            )
        }
    }
}






/**
 * Banner shown when messages have been reverted.
 * Tapping restores (redo) the reverted messages.
 */
@Composable
private fun RevertBanner(onRedo: () -> Unit) {
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current
    val isAmoled = isAmoledTheme()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
        border = if (isAmoled) appAmoledBorder() else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { role = Role.Button }
            .clickable { performHaptic(hapticView, hapticOn); onRedo() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Undo,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isAmoled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onTertiaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.chat_messages_reverted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isAmoled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = stringResource(R.string.chat_tap_restore),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isAmoled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Default.Restore,
                contentDescription = stringResource(R.string.chat_restore),
                modifier = Modifier.size(20.dp),
                tint = if (isAmoled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}


private val placeholderHintResIds = listOf(
    R.string.chat_hint_ask,
    R.string.chat_hint_fix,
    R.string.chat_hint_refactor,
    R.string.chat_hint_tests,
    R.string.chat_hint_explain,
    R.string.chat_hint_help,
)

internal fun isWorkingSessionStatus(status: SessionStatus): Boolean =
    status is SessionStatus.Busy || status is SessionStatus.Retry

internal fun retryDelaySeconds(nextAtMillis: Long, nowMillis: Long): Long =
    ((nextAtMillis - nowMillis).coerceAtLeast(0) + 999) / 1_000

@Composable
private fun SuggestionRow(
    suggestions: List<String>,
    suggestionsSource: SuggestionSource? = null,
    isGenerating: Boolean,
    error: String?,
    streamText: String = "",
    modelNeedsDownload: Boolean = false,
    modelDownloading: Boolean = false,
    modelDownloadProgress: Int = 0,
    onDownloadModel: () -> Unit = {},
    onSuggestionClick: (String) -> Unit,
    onGenerate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isAmoled = isAmoledTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            isGenerating -> {
                // Show the model's streamed text live if we have any, otherwise a spinner.
                if (streamText.isNotBlank()) {
                    Text(
                        text = streamText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    PulsingDotsIndicator(dotSize = 5.dp, dotSpacing = 3.dp, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.chat_suggestions_generating),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
            error != null -> {
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (modelDownloading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.chat_suggestions_model_downloading, modelDownloadProgress),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        LinearProgressIndicator(
                            progress = { modelDownloadProgress / 100f },
                            modifier = Modifier.width(90.dp).height(4.dp),
                        )
                    }
                } else if (modelNeedsDownload) {
                    TextButton(onClick = onDownloadModel) {
                        Text(stringResource(R.string.chat_suggestions_download_model))
                    }
                } else {
                    TextButton(onClick = onGenerate) {
                        Text(stringResource(R.string.retry))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
            suggestions.isNotEmpty() -> {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        suggestionsSource?.let { source ->
                            Text(
                                text = stringResource(
                                    when (source) {
                                        SuggestionSource.BACKEND -> R.string.chat_suggestions_source_server
                                        SuggestionSource.CLOUD -> R.string.chat_suggestions_source_cloud
                                        SuggestionSource.ON_DEVICE -> R.string.chat_suggestions_source_on_device
                                        SuggestionSource.FALLBACK -> R.string.chat_suggestions_source_fallback
                                    }
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        suggestions.take(3).forEach { suggestion ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isAmoled) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSuggestionClick(suggestion) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Send,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    )
                                    Text(
                                        text = suggestion,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
            else -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isAmoled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    },
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.chat_suggestions_generate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onGenerate)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RetryStatusBanner(retry: SessionStatus.Retry) {
    var nowMillis by remember(retry.next) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(retry.next) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            val delayMillis = retry.next - nowMillis
            if (delayMillis <= 0) break
            kotlinx.coroutines.delay(minOf(1_000L, delayMillis))
        }
    }
    val remainingSeconds = retryDelaySeconds(retry.next, nowMillis)
    val isAmoled = isAmoledTheme()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.sessions_retrying),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = retry.message.ifBlank { stringResource(R.string.error_unknown) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (remainingSeconds > 0) {
                        stringResource(R.string.chat_retry_waiting, remainingSeconds, retry.attempt)
                    } else {
                        stringResource(R.string.chat_retry_now, retry.attempt)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun VoiceListeningBanner(voiceLevel: Float) {
    val isAmoled = isAmoledTheme()
    val level = (voiceLevel / 10f).coerceIn(0f, 1f)
    val bars = 16
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            // Volume waveform
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                repeat(bars) { index ->
                    val barLevel = if (level <= 0f) {
                        0.15f
                    } else {
                        // Animate bars relative to the voice level with a slight falloff.
                        val peak = 1f - (kotlin.math.abs(index - bars / 2).toFloat() / (bars / 2f)) * 0.6f
                        (level * peak).coerceIn(0.12f, 1f)
                    }
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height((6f + 22f * barLevel).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                    )
                }
            }
            Text(
                text = stringResource(R.string.chat_voice_input_listening),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatInputBar(
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isSending: Boolean,
    isBusy: Boolean = false,
    sessionStatus: SessionStatus = SessionStatus.Idle,
    messages: List<ChatMessage> = emptyList(),
    attachments: List<ImageAttachment> = emptyList(),
    onAttach: () -> Unit = {},
    onTemplateClick: () -> Unit = {},
    isListening: Boolean = false,
    voiceLevel: Float = 0f,
    onMicPress: () -> Unit = {},
    onMicRelease: () -> Unit = {},
    onMicCancel: () -> Unit = {},
    voiceEnabled: Boolean = false,
    onRemoveAttachment: (Int) -> Unit = {},
    onSaveAttachment: (bytes: ByteArray, mime: String, filename: String?) -> Unit = { _, _, _ -> },
    modelLabel: String = "",
    selectedProviderId: String? = null,
    onModelClick: () -> Unit = {},
    agents: List<AgentInfo> = emptyList(),
    selectedAgent: String = "build",
    onAgentSelect: (String) -> Unit = {},
    variantNames: List<String> = emptyList(),
    selectedVariant: String? = null,
    onVariantSelect: (String?) -> Unit = {},
    commands: List<CommandInfo> = emptyList(),
    customCommands: List<CustomSlashCommand> = emptyList(),
    onManageCustomCommands: () -> Unit = {},
    fileSearchResults: List<String> = emptyList(),
    confirmedFilePaths: Set<String> = emptySet(),
    onFileSelected: (String) -> Unit = {},
    onSlashCommand: (SlashCommand) -> Unit = {},
    inputMode: ChatInputMode = ChatInputMode.NORMAL,
    onInputModeChange: (ChatInputMode) -> Unit = {},
    contextWindow: Int = 0,
    lastContextTokens: Int = 0,
    contextUsage: ContextUsageDetails = ContextUsageDetails(),
    suggestions: List<String> = emptyList(),
    suggestionsSource: SuggestionSource? = null,
    isGeneratingSuggestions: Boolean = false,
    suggestionsError: String? = null,
    suggestionsStreamText: String = "",
    modelNeedsDownload: Boolean = false,
    modelDownloading: Boolean = false,
    modelDownloadProgress: Int = 0,
    onDownloadModel: () -> Unit = {},
    onSuggestionClick: (String) -> Unit = {},
    onGenerateSuggestions: () -> Unit = {},
    onDismissSuggestions: () -> Unit = {},
) {
    val isAmoled = isAmoledTheme()
    val isShellMode = inputMode == ChatInputMode.SHELL
    // Rotate placeholder hint every 4 seconds
    val hintIndex = remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(4000)
            hintIndex.intValue = (hintIndex.intValue + 1) % placeholderHintResIds.size
        }
    }
    val placeholder = if (isShellMode) {
        stringResource(R.string.chat_shell_placeholder)
    } else {
        stringResource(placeholderHintResIds[hintIndex.intValue])
    }

    val text = textFieldValue.text
    val showInlineAttach = text.isEmpty() && !isShellMode
    val hasDraft = text.isNotBlank() || attachments.isNotEmpty()
    val action = composerAction(isBusy, isSending, hasDraft, isShellMode)
    val canSend = action == ComposerAction.SEND
    val retryStatus = sessionStatus as? SessionStatus.Retry
    var showContextDetails by remember { mutableStateOf(false) }
    var previewAttachmentIndex by remember { mutableStateOf(-1) }
    var showVariantMenu by remember { mutableStateOf(false) }

    // Build merged slash commands: client commands + custom commands + server commands (deduplicated)
    val clientCmds = clientCommands()
    val allCommands = remember(commands, clientCmds, customCommands) {
        val clientNames = clientCmds.map { it.name }.toSet()
        val serverSlash = commands
            .filter { it.source != "skill" && it.name !in clientNames }
            .map { SlashCommand(it.name, it.description, "server") }
        val customSlash = customCommands.map { SlashCommand(it.name, it.prompt, "custom", it.prompt) }
        clientCmds + customSlash + serverSlash
    }

    // Slash command suggestions
    val showSlashSuggestions = !isShellMode && text.startsWith("/") && !text.contains(" ")
    val slashQuery = if (showSlashSuggestions) text.removePrefix("/").lowercase() else ""
    val filteredCommands = if (showSlashSuggestions) {
        allCommands.filter { cmd ->
            slashQuery.isEmpty() || cmd.name.lowercase().contains(slashQuery)
        }
    } else emptyList()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        // Thin divider
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            thickness = 0.5.dp
        )

        retryStatus?.let { retry ->
            RetryStatusBanner(retry)
        }

        // Slash command suggestions popup (scrollable, max 40% screen height)
        AnimatedVisibility(
            visible = showSlashSuggestions && filteredCommands.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val configuration = LocalConfiguration.current
            val maxHeight = (configuration.screenHeightDp * 0.4f).dp

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .background(if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(vertical = 4.dp)
            ) {
                items(filteredCommands, key = { it.name }) { cmd ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onTextFieldValueChange(TextFieldValue(""))
                                onSlashCommand(cmd)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "/${cmd.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace
                        )
                        if (cmd.description != null) {
                            Text(
                                text = cmd.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                item(key = "manage_custom_commands") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onManageCustomCommands)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = stringResource(R.string.custom_command_manage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // @ file mention suggestions popup
        AnimatedVisibility(
            visible = !isShellMode && fileSearchResults.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val configuration = LocalConfiguration.current
            val maxHeight = (configuration.screenHeightDp * 0.4f).dp

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .background(if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(vertical = 4.dp)
            ) {
                items(
                    fileSearchResults.take(10),
                    key = { it }
                ) { path ->
                    val isDir = path.endsWith("/")
                    // Split into directory part + filename for display
                    val displayPath = if (isDir) path.trimEnd('/') else path
                    val lastSlash = displayPath.lastIndexOf('/')
                    val dirPart = if (lastSlash >= 0) displayPath.substring(0, lastSlash + 1) else ""
                    val namePart = if (lastSlash >= 0) displayPath.substring(lastSlash + 1) else displayPath

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFileSelected(path) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isDir) Icons.Default.Folder else Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isDir)
                                MaterialTheme.colorScheme.tertiary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = buildAnnotatedString {
                                if (dirPart.isNotEmpty()) {
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))) {
                                        append(dirPart)
                                    }
                                }
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                                    append(namePart)
                                }
                                if (isDir) {
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))) {
                                        append("/")
                                    }
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        }

        // Working status row; context usage lives with the model controls below.
        val showContext = contextWindow > 0 && lastContextTokens > 0
        val contextPercentage = if (showContext) {
            Math.round(lastContextTokens.toDouble() / contextWindow * 100).toInt()
        } else {
            0
        }
        val contextColor = when {
            contextPercentage >= 90 -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            contextPercentage >= 70 -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
        }
        if (isBusy && retryStatus == null) {
            val lastRunningTool = if (isBusy) {
                messages.asReversed().firstNotNullOfOrNull { message ->
                    message.parts.filterIsInstance<Part.Tool>().lastOrNull { it.state is ToolState.Running }
                }
            } else null

            val statusText = if (isBusy) {
                if (lastRunningTool != null) {
                    val title = (lastRunningTool.state as ToolState.Running).title
                    when (lastRunningTool.tool) {
                        "read" -> title ?: stringResource(R.string.chat_tool_reading_file)
                        "write" -> title ?: stringResource(R.string.chat_tool_writing_file)
                        "edit" -> title ?: stringResource(R.string.chat_tool_editing_file)
                        "bash" -> title ?: stringResource(R.string.chat_tool_running_command)
                        "glob", "list" -> title ?: stringResource(R.string.chat_tool_searching_files)
                        "grep" -> title ?: stringResource(R.string.chat_tool_searching_code)
                        "webfetch" -> title ?: stringResource(R.string.chat_tool_fetching_url)
                        "task" -> title ?: stringResource(R.string.chat_tool_running_subagent)
                        "todowrite" -> title ?: stringResource(R.string.chat_tool_updating_tasks)
                        else -> title ?: stringResource(R.string.chat_tool_running_tool, lastRunningTool.tool)
                    }
                } else {
                    stringResource(R.string.chat_tool_thinking)
                }
            } else null

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 2.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: working status
                if (isBusy && statusText != null) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PulsingDotsIndicator(
                            dotSize = 4.dp,
                            dotSpacing = 3.dp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Agent + model + variant selectors followed by context usage.
            if (modelLabel.isNotEmpty() || agents.size > 1 || showContext) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Keep the full selector sequence horizontally scrollable on narrow screens.
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Agent selector — single button, tap to cycle
                        // Fixed width: all agent names rendered invisible to reserve max width
                        if (agents.size > 1) {
                            val agentColor = agentColor(selectedAgent, agents)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(agentColor.copy(alpha = 0.18f))
                                    .clickable {
                                        val currentIndex = agents.indexOfFirst { it.name == selectedAgent }
                                        val nextIndex = (currentIndex + 1) % agents.size
                                        onAgentSelect(agents[nextIndex].name)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                // Invisible ghost texts for all agent names — fixes width to the widest
                                agents.forEach { agent ->
                                    Text(
                                        text = agent.name.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Transparent
                                    )
                                }
                                // Visible label with accent color
                                Text(
                                    text = selectedAgent.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = agentColor
                                )
                            }
                        }

                        // Model selector — SECOND
                        if (modelLabel.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onModelClick() }
                                    .padding(horizontal = 3.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                if (selectedProviderId != null) {
                                    ProviderIcon(
                                        providerId = selectedProviderId,
                                        size = 13.dp,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                Text(
                                    text = modelLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Icon(
                                    Icons.Default.UnfoldMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }

                        // Variant selector (thinking effort) — THIRD
                        if (variantNames.isNotEmpty()) {
                            Box {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { showVariantMenu = true }
                                        .padding(horizontal = 3.dp, vertical = 3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = selectedVariant?.replaceFirstChar { it.uppercase() }
                                            ?: stringResource(R.string.chat_default_variant),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selectedVariant != null) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        },
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    )
                                }
                                DropdownMenu(
                                    expanded = showVariantMenu,
                                    onDismissRequest = { showVariantMenu = false },
                                    modifier = Modifier
                                        .widthIn(min = 150.dp)
                                        .appPopupBorder(),
                                    containerColor = appPopupContainerColor(),
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.chat_default_variant)) },
                                        leadingIcon = {
                                            if (selectedVariant == null) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        },
                                        onClick = {
                                            onVariantSelect(null)
                                            showVariantMenu = false
                                        },
                                    )
                                    variantNames.forEach { variant ->
                                        DropdownMenuItem(
                                            text = { Text(variant.replaceFirstChar { it.uppercase() }) },
                                            leadingIcon = {
                                                if (selectedVariant == variant) {
                                                    Icon(Icons.Default.Check, contentDescription = null)
                                                }
                                            },
                                            onClick = {
                                                onVariantSelect(variant)
                                                showVariantMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        if (showContext) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable { showContextDetails = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    progress = { (lastContextTokens.toFloat() / contextWindow).coerceIn(0f, 1f) },
                                    modifier = Modifier.size(27.dp),
                                    color = contextColor,
                                    trackColor = contextColor.copy(alpha = 0.16f),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = "$contextPercentage%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = contextColor,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            // Image attachment thumbnails
            if (attachments.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(attachments.size) { index ->
                        val attachment = attachments[index]
                        Box(
                            modifier = Modifier
                                .width(if (attachment.isImage) 56.dp else 180.dp)
                                .height(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                        ) {
                            if (attachment.isImage) {
                                AsyncImage(
                                    model = imageThumbnailModel(attachment),
                                    contentDescription = attachment.filename,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { previewAttachmentIndex = index },
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 10.dp, end = 26.dp, top = 8.dp, bottom = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = if (attachment.mime == "application/pdf") {
                                                Icons.Default.PictureAsPdf
                                            } else {
                                                Icons.Default.Description
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = attachment.filename,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (attachment.sizeBytes > 0) {
                                                Text(
                                                    text = formatFileSize(attachment.sizeBytes),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(18.dp)
                                    .clickable { onRemoveAttachment(index) },
                                shape = RoundedCornerShape(9.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.chat_remove),
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onError
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (previewAttachmentIndex >= 0 && previewAttachmentIndex < attachments.size) {
                val attachment = attachments[previewAttachmentIndex]
                val imageBytes = remember(attachment.dataUrl) { decodeDataUrlBytes(attachment.dataUrl) }
                val bitmap = remember(imageBytes) {
                    imageBytes?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                }

                if (bitmap != null) {
                    ImagePreviewDialog(
                        imageModel = bitmap,
                        contentDescription = attachment.filename,
                        onDismiss = { previewAttachmentIndex = -1 },
                        onSave = {
                            if (imageBytes != null) {
                                onSaveAttachment(imageBytes, attachment.mime, attachment.filename)
                            }
                        },
                    )
                }
            }

            AnimatedVisibility(
                visible = isShellMode,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isAmoled) {
                                Color.Black
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        )
                        .then(
                            if (isAmoled) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(10.dp),
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.chat_shell_mode_hold_send_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Suggestion chips
            if (!isShellMode) {
                SuggestionRow(
                suggestions = suggestions,
                suggestionsSource = suggestionsSource,
                isGenerating = isGeneratingSuggestions,
                    error = suggestionsError,
                    streamText = suggestionsStreamText,
                    modelNeedsDownload = modelNeedsDownload,
                    modelDownloading = modelDownloading,
                    modelDownloadProgress = modelDownloadProgress,
                    onDownloadModel = onDownloadModel,
                    onSuggestionClick = onSuggestionClick,
                    onGenerate = onGenerateSuggestions,
                    onDismiss = onDismissSuggestions,
                )
            }

            // Voice input listening banner — shown while holding the mic button.
            AnimatedVisibility(
                visible = isListening,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                VoiceListeningBanner(voiceLevel = voiceLevel)
            }

            // Input row
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                // Quick template button — fills the input with a preset prompt.
                if (!isShellMode) {
                    IconButton(
                        onClick = onTemplateClick,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Style,
                            contentDescription = stringResource(R.string.chat_template),
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        )
                    }
                }
                // Text field — minimal style, no heavy outline
                val mentionHighlightColor = MaterialTheme.colorScheme.primary
                val mentionBgColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                val visualTransformation = remember(confirmedFilePaths, mentionHighlightColor, mentionBgColor) {
                    if (isShellMode) {
                        VisualTransformation.None
                    } else {
                        FileMentionVisualTransformation(confirmedFilePaths, mentionHighlightColor, mentionBgColor)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (isAmoled) {
                                Color.Black
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                        .then(
                            when {
                                isShellMode -> Modifier.border(
                                    width = if (isAmoled) 1.5.dp else 1.dp,
                                    color = if (isAmoled) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                    },
                                    shape = RoundedCornerShape(22.dp)
                                )
                                isAmoled -> Modifier.border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(22.dp)
                                )
                                else -> Modifier
                            }
                        )
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = onTextFieldValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 16.dp,
                                end = if (showInlineAttach) 48.dp else 16.dp,
                                top = 10.dp,
                                bottom = 10.dp,
                            ),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = if (isShellMode) FontFamily.Monospace else FontFamily.Default
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        maxLines = 5,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = visualTransformation,
                        decorationBox = { innerTextField ->
                            if (text.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (showInlineAttach) {
                        Box(
                            modifier = Modifier.matchParentSize(),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            IconButton(
                                onClick = onAttach,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Default.AttachFile,
                                    contentDescription = stringResource(R.string.chat_attach),
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                )
                            }
                        }
                    }
                }

                // Voice input button — hold to talk, release to fill, slide up to cancel.
                // Only shown when the on-device ASR model has been downloaded in Settings.
                if (!isShellMode && voiceEnabled) {
                    var cancelThresholdPx by remember { mutableStateOf(0f) }
                    val density = LocalDensity.current
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    down.consume()
                                    onMicPress()
                                    var cancelled = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change == null) break
                                        if (!change.pressed) {
                                            if (cancelled) onMicCancel() else onMicRelease()
                                            change.consume()
                                            break
                                        }
                                        // Slide-up cancel: finger moves significantly above the button.
                                        if (change.position.y < -cancelThresholdPx) {
                                            cancelled = true
                                        } else {
                                            change.consume()
                                        }
                                    }
                                }
                            }
                            .background(
                                color = if (isListening) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(22.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        cancelThresholdPx = with(density) { 44.dp.toPx() }
                        Icon(
                            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = stringResource(
                                if (isListening) R.string.chat_voice_input_stop else R.string.chat_voice_input
                            ),
                            modifier = Modifier.size(22.dp),
                            tint = if (isListening) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                            },
                        )
                    }
                }

                // Send button — tap to send, long-press toggles shell mode
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (action == ComposerAction.STOP) {
                                if (isAmoled) Color.Transparent else MaterialTheme.colorScheme.errorContainer
                            } else if (isShellMode && !isSending) {
                                if (isAmoled) {
                                    Color.Black
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                }
                            } else {
                                Color.Transparent
                            }
                        )
                        .then(
                            if (action == ComposerAction.STOP && isAmoled) {
                                Modifier.border(
                                    width = 1.2.dp,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.88f),
                                    shape = RoundedCornerShape(22.dp),
                                )
                            } else if (isShellMode && !isSending) {
                                Modifier.border(
                                    width = if (isAmoled) 1.2.dp else 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (isAmoled) 0.88f else 0.75f),
                                    shape = RoundedCornerShape(22.dp),
                                )
                            } else {
                                Modifier
                            }
                        )
                        .combinedClickable(
                            onClick = {
                                when (action) {
                                    ComposerAction.SEND -> onSend()
                                    ComposerAction.STOP -> onStop()
                                    ComposerAction.DISABLED -> Unit
                                }
                            },
                            onLongClick = {
                                onInputModeChange(
                                    if (isShellMode) ChatInputMode.NORMAL else ChatInputMode.SHELL
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSending) {
                        BreathingCircleIndicator(
                            size = 14.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (action == ComposerAction.STOP) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = stringResource(R.string.chat_stop),
                            modifier = Modifier.size(14.dp),
                            tint = if (isAmoled) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.88f)
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (isShellMode) {
                                stringResource(R.string.chat_send_shell)
                            } else {
                                stringResource(R.string.chat_send)
                            },
                            modifier = Modifier.size(18.dp),
                            tint = if (canSend) {
                                MaterialTheme.colorScheme.primary
                            } else if (isShellMode && isAmoled && !isSending) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            }
                        )

                    }
                }
            }
        }
    }
    if (showContextDetails) {
        ContextUsageDialog(
            usage = contextUsage,
            contextWindow = contextWindow,
            onDismiss = { showContextDetails = false },
        )
    }
}



