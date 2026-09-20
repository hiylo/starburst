/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatTerminal.kt
 * Date : 2026/09/14 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.hiylo.starburst.domain.model.*
import org.hiylo.starburst.ui.theme.CodeTypography
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlin.math.abs
import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.BuildConfig
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.isAmoledTheme

internal fun terminalZoomFontSize(startFontSizeSp: Float, scale: Float): Float {
    return (startFontSizeSp * scale).coerceIn(6f, 20f)
}

internal fun terminalGestureIsPinch(pressedPointerCount: Int): Boolean = pressedPointerCount >= 2

@androidx.annotation.StringRes
internal fun terminalTabStateLabel(state: TerminalTabState): Int = when (state) {
    TerminalTabState.Starting -> R.string.chat_terminal_starting
    TerminalTabState.Connected -> R.string.chat_terminal_connected
    TerminalTabState.Reconnecting -> R.string.chat_terminal_reconnecting
    TerminalTabState.Disconnected -> R.string.chat_terminal_disconnected
    TerminalTabState.Exited -> R.string.chat_terminal_exited
}

@androidx.annotation.StringRes
internal fun terminalRecoveryLabel(action: TerminalRecoveryAction): Int = when (action) {
    TerminalRecoveryAction.Reconnect -> R.string.chat_terminal_reconnect_tab
    TerminalRecoveryAction.Restart -> R.string.chat_terminal_restart_tab
    TerminalRecoveryAction.None -> R.string.chat_terminal_connected
}

internal fun terminalFlingScrollVelocity(pointerVelocityY: Float, minimumFlingVelocity: Float): Float {
    val scrollVelocity = -pointerVelocityY
    return if (abs(scrollVelocity) >= minimumFlingVelocity) scrollVelocity else 0f
}

internal fun terminalInputDelta(previous: String, current: String): String {
    val commonPrefixLength = previous.commonPrefixWith(current).length
    val deleted = previous.length - commonPrefixLength
    return "\u007F".repeat(deleted) + current.drop(commonPrefixLength)
}

private data class TerminalMetrics(
    val fontSizePx: Float,
    val charWidthPx: Float,
    val rowHeightPx: Int,
    val baselinePx: Float,
    val columns: Int,
    val rows: Int,
)

@Composable
internal fun SessionTerminalInline(
    emulator: TerminalEmulator,
    terminalVersion: Long,
    connected: Boolean,
    focusRequester: FocusRequester,
    onSendInput: (String) -> Unit,
    onPaste: () -> Unit,
    onResize: (cols: Int, rows: Int) -> Unit,
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    contentBottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val isAmoled = isAmoledTheme()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val baseTextToolbar = LocalTextToolbar.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val minimumTerminalFlingVelocity = remember(context) {
        android.view.ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    }
    val coroutineScope = rememberCoroutineScope()
    var inputCapture by remember { mutableStateOf(TextFieldValue("")) }
    var sentInputCapture by remember { mutableStateOf("") }
    val terminalScrollState = rememberScrollState()
    var terminalFollowMode by rememberSaveable { mutableStateOf(true) }
    var terminalFlingJob by remember { mutableStateOf<Job?>(null) }
    var terminalLifecycleActive by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { source, _ ->
            terminalLifecycleActive = source.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (!terminalLifecycleActive) {
                terminalFlingJob?.cancel()
                terminalFlingJob = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val terminalTextToolbar = remember(baseTextToolbar, onPaste) {
        object : TextToolbar {
            override val status: TextToolbarStatus
                get() = baseTextToolbar.status

            override fun hide() {
                baseTextToolbar.hide()
            }

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                baseTextToolbar.showMenu(
                    rect = rect,
                    onCopyRequested = onCopyRequested,
                    onPasteRequested = {
                        onPaste()
                        onPasteRequested?.invoke()
                    },
                    onCutRequested = onCutRequested,
                    onSelectAllRequested = onSelectAllRequested
                )
            }
        }
    }

    var pinchActive by remember { mutableStateOf(false) }
    var pinchStartFontSizeSp by remember { mutableFloatStateOf(fontSizeSp) }
    var pinchPreviewFontSizeSp by remember { mutableFloatStateOf(fontSizeSp) }
    val latestFontSizeSp by rememberUpdatedState(fontSizeSp)

    LaunchedEffect(fontSizeSp, pinchActive) {
        if (!pinchActive) pinchPreviewFontSizeSp = fontSizeSp
    }
    val effectiveFontSizeSp = if (pinchActive) pinchPreviewFontSizeSp else fontSizeSp
    val terminalStyle = remember(effectiveFontSizeSp) {
        CodeTypography.copy(
            fontSize = effectiveFontSizeSp.sp,
            // Tight line spacing is required for continuous box-drawing in TUIs (mc, htop).
            lineHeight = effectiveFontSizeSp.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )
    }

    Column(
        modifier = modifier
            .background(Color.Black)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        BasicTextField(
            value = inputCapture,
            onValueChange = { next ->
                if (!connected) {
                    inputCapture = TextFieldValue("")
                    sentInputCapture = ""
                    return@BasicTextField
                }
                val delta = terminalInputDelta(sentInputCapture, next.text)
                if (delta.isNotEmpty()) {
                    if (BuildConfig.DEBUG && delta.contains('~')) {
                        Log.d(
                            "TerminalInput",
                            "IME delta='$delta' old='$sentInputCapture' now='${next.text}' " +
                                "composition=${next.composition}",
                        )
                    }
                    val mapped = delta
                        .replace("\r\n", "\r")
                        .replace('\n', '\r')
                    onSendInput(mapped)
                }
                sentInputCapture = next.text
                // Keep IME context (caps/symbol lock, composing state) stable by
                // preserving TextFieldValue instead of clearing it after each key.
                inputCapture = next.copy(selection = TextRange(next.text.length))
            },
            modifier = Modifier
                .size(1.dp)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            onSendInput("\r")
                            true
                        }
                        Key.Tab -> {
                            onSendInput("\t")
                            true
                        }
                        Key.Backspace -> {
                            onSendInput("\u007F")
                            true
                        }
                        else -> {
                            val native = event.nativeKeyEvent
                            val unicode = native.unicodeChar
                            if (unicode > 0 && (unicode and android.view.KeyCharacterMap.COMBINING_ACCENT) == 0) {
                                if (native.isCtrlPressed) {
                                    val lower = unicode.toChar().lowercaseChar()
                                    if (lower in 'a'..'z') {
                                        val ctrl = (lower.code - 'a'.code + 1).toChar().toString()
                                        onSendInput(ctrl)
                                        true
                                    } else {
                                        false
                                    }
                                } else {
                                    onSendInput(String(Character.toChars(unicode)))
                                    true
                                }
                            } else {
                                val baseLetter = when (event.key) {
                                    Key.A -> 'a'
                                    Key.B -> 'b'
                                    Key.C -> 'c'
                                    Key.D -> 'd'
                                    Key.E -> 'e'
                                    Key.F -> 'f'
                                    Key.G -> 'g'
                                    Key.H -> 'h'
                                    Key.I -> 'i'
                                    Key.J -> 'j'
                                    Key.K -> 'k'
                                    Key.L -> 'l'
                                    Key.M -> 'm'
                                    Key.N -> 'n'
                                    Key.O -> 'o'
                                    Key.P -> 'p'
                                    Key.Q -> 'q'
                                    Key.R -> 'r'
                                    Key.S -> 's'
                                    Key.T -> 't'
                                    Key.U -> 'u'
                                    Key.V -> 'v'
                                    Key.W -> 'w'
                                    Key.X -> 'x'
                                    Key.Y -> 'y'
                                    Key.Z -> 'z'
                                    else -> null
                                }
                                if (baseLetter != null) {
                                    val upper = native.isShiftPressed.xor(native.isCapsLockOn)
                                    val out = if (upper) baseLetter.uppercaseChar() else baseLetter
                                    if (native.isCtrlPressed) {
                                        val ctrl = (baseLetter.code - 'a'.code + 1).toChar().toString()
                                        onSendInput(ctrl)
                                    } else {
                                        onSendInput(out.toString())
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                        }
                    }
                },
            singleLine = false,
            textStyle = terminalStyle,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(
                onSend = { onSendInput("\r") },
                onDone = { onSendInput("\r") },
                onGo = { onSendInput("\r") }
            )
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = contentBottomPadding)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            focusRequester.requestFocus()
                            keyboard?.show()
                        }
                    )
                }
        ) {
            val density = LocalDensity.current
            val viewportWidthPx = constraints.maxWidth
            val viewportHeightPx = constraints.maxHeight
            val terminalMetrics = remember(
                effectiveFontSizeSp,
                density.density,
                density.fontScale,
                viewportWidthPx,
                viewportHeightPx,
            ) {
                // One native Paint supplies every grid and drawing metric so resize and Canvas stay aligned.
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = android.graphics.Typeface.MONOSPACE
                    textSize = with(density) { effectiveFontSizeSp.sp.toPx() }
                }
                val fm = paint.fontMetrics
                val charWidthPx = paint.measureText("X")
                val rowHeightPx = kotlin.math.ceil((fm.descent - fm.ascent).toDouble()).toInt()
                TerminalMetrics(
                    fontSizePx = paint.textSize,
                    charWidthPx = charWidthPx,
                    rowHeightPx = rowHeightPx,
                    baselinePx = -fm.ascent,
                    columns = if (viewportWidthPx > 0) {
                        (viewportWidthPx / charWidthPx).toInt().coerceAtLeast(20)
                    } else 80,
                    rows = if (viewportHeightPx > 0) {
                        (viewportHeightPx / rowHeightPx).coerceAtLeast(8)
                    } else 24,
                )
            }
            val charWidthPx = terminalMetrics.charWidthPx
            val rowHeightPx = terminalMetrics.rowHeightPx
            val termCols = terminalMetrics.columns
            val termRows = terminalMetrics.rows
            val maxScrollbackOffsetRows = remember(terminalVersion, termRows) {
                emulator.maxScrollbackOffset(termRows)
            }
            val totalRows = remember(terminalVersion) {
                emulator.totalRowsWithScrollback().coerceAtLeast(1)
            }
            val renderedRuns = remember(terminalVersion, totalRows) {
                emulator.renderRuns(
                    scrollbackOffsetRows = 0,
                    windowRows = totalRows,
                )
            }
            val maxScrollPx = maxScrollbackOffsetRows * rowHeightPx
            val followThresholdPx = (rowHeightPx * 2).coerceAtLeast(1)
            val isNearBottom = terminalScrollState.value >= (maxScrollPx - followThresholdPx).coerceAtLeast(0)
            LaunchedEffect(isNearBottom) {
                if (isNearBottom) {
                    terminalFollowMode = true
                }
            }
            LaunchedEffect(maxScrollPx, terminalVersion, terminalFollowMode) {
                when {
                    terminalFollowMode -> {
                        if (terminalScrollState.value != maxScrollPx) {
                            terminalScrollState.scrollTo(maxScrollPx)
                        }
                    }
                    terminalScrollState.value > maxScrollPx -> {
                        terminalScrollState.scrollTo(maxScrollPx)
                    }
                }
            }
            val firstVisibleRow = (terminalScrollState.value / rowHeightPx)
                .coerceIn(0, maxScrollbackOffsetRows)
            val scrollbackOffsetRows = (maxScrollbackOffsetRows - firstVisibleRow).coerceAtLeast(0)
            val verticalOffsetPx = firstVisibleRow * rowHeightPx
            LaunchedEffect(termCols, termRows, connected, pinchActive) {
                if (!pinchActive && connected && viewportWidthPx > 0 && viewportHeightPx > 0) {
                    onResize(termCols, termRows)
                }
            }

            val cursorPos = remember(terminalVersion, scrollbackOffsetRows, termRows) {
                emulator.getCursorPositionInWindow(
                    scrollbackOffsetRows = scrollbackOffsetRows,
                    windowRows = termRows,
                )
            }
            var cursorBlinkOn by remember { mutableStateOf(true) }
            LaunchedEffect(terminalLifecycleActive, emulator.cursorBlinkEnabled, terminalVersion) {
                cursorBlinkOn = true
                if (terminalLifecycleActive && emulator.cursorBlinkEnabled) {
                    while (true) {
                        delay(500)
                        cursorBlinkOn = !cursorBlinkOn
                    }
                }
            }

            val accessibilityOutput = remember(terminalVersion, scrollbackOffsetRows, termRows) {
                AnnotatedString(
                    emulator.renderSelectionText(
                        scrollbackOffsetRows = scrollbackOffsetRows,
                        windowRows = termRows,
                    ),
                )
            }

            val terminalBgColor = Color.Black
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { text = accessibilityOutput }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val firstDown = awaitFirstDown(requireUnconsumed = false)
                            terminalFlingJob?.cancel()
                            terminalFlingJob = null
                            val velocityTracker = VelocityTracker().apply {
                                addPosition(firstDown.uptimeMillis, firstDown.position)
                            }
                            var gestureIsPinch = false
                            var gestureScale = 1f
                            var previewFontSizeSp = latestFontSizeSp
                            var gestureContinues: Boolean
                            do {
                                val event = awaitPointerEvent()
                                event.changes.firstOrNull { it.id == firstDown.id }?.let {
                                    velocityTracker.addPosition(it.uptimeMillis, it.position)
                                }
                                val pressedPointers = event.changes.count { it.pressed }
                                if (terminalGestureIsPinch(pressedPointers)) {
                                    if (!gestureIsPinch) {
                                        gestureIsPinch = true
                                        pinchActive = true
                                        pinchStartFontSizeSp = latestFontSizeSp
                                        gestureScale = 1f
                                        previewFontSizeSp = pinchStartFontSizeSp
                                        keyboard?.hide()
                                        if (BuildConfig.DEBUG) Log.d("TerminalGesture", "Pinch started")
                                    }
                                    val zoom = event.calculateZoom()
                                    if (zoom.isFinite() && zoom > 0f) {
                                        gestureScale *= zoom
                                        previewFontSizeSp = terminalZoomFontSize(pinchStartFontSizeSp, gestureScale)
                                        pinchPreviewFontSizeSp = previewFontSizeSp
                                    }
                                    event.changes.forEach { it.consume() }
                                } else if (!gestureIsPinch && maxScrollbackOffsetRows > 0) {
                                    val pan = event.calculatePan()
                                    if (pan.y != 0f) {
                                        terminalScrollState.dispatchRawDelta(-pan.y)
                                        val nearBottomAfterPan = terminalScrollState.value >=
                                            (maxScrollPx - followThresholdPx).coerceAtLeast(0)
                                        terminalFollowMode = nearBottomAfterPan
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                                gestureContinues = event.changes.any { it.pressed }
                            } while (gestureContinues)
                            if (gestureIsPinch) {
                                onFontSizeChange(previewFontSizeSp)
                                pinchActive = false
                                if (BuildConfig.DEBUG) {
                                    Log.d("TerminalGesture", "Pinch committed: ${previewFontSizeSp}sp")
                                }
                            } else if (maxScrollbackOffsetRows > 0) {
                                val flingVelocity = terminalFlingScrollVelocity(
                                    pointerVelocityY = velocityTracker.calculateVelocity().y,
                                    minimumFlingVelocity = minimumTerminalFlingVelocity,
                                )
                                if (flingVelocity != 0f) {
                                    terminalFlingJob = coroutineScope.launch {
                                        var previousValue = 0f
                                        AnimationState(
                                            initialValue = 0f,
                                            initialVelocity = flingVelocity,
                                        ).animateDecay(exponentialDecay()) {
                                            val requestedDelta = value - previousValue
                                            val consumedDelta = terminalScrollState.dispatchRawDelta(requestedDelta)
                                            previousValue = value
                                            terminalFollowMode = terminalScrollState.value >=
                                                (maxScrollPx - followThresholdPx).coerceAtLeast(0)
                                            if (abs(consumedDelta - requestedDelta) > 0.5f) {
                                                cancelAnimation()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                // Canvas layer: draw each character at its exact grid position to
                // guarantee monospaced alignment for box-drawing characters.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val nativeCanvas = drawContext.canvas.nativeCanvas

                    // Paint for background fills — no anti-aliasing for pixel-perfect
                    // row tiling (matches Termux approach).
                    val bgPaint = android.graphics.Paint().apply {
                        isAntiAlias = false
                        style = android.graphics.Paint.Style.FILL
                    }

                    // Fill the entire terminal area with the default background.
                    bgPaint.color = terminalBgColor.toArgb()
                    nativeCanvas.drawRect(0f, 0f, size.width, size.height, bgPaint)

                    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = terminalMetrics.fontSizePx
                        typeface = android.graphics.Typeface.MONOSPACE
                    }
                    val baseline = terminalMetrics.baselinePx
                    val rowH = rowHeightPx.toFloat()

                    for ((rowIdx, runs) in renderedRuns.withIndex()) {
                        val y = ((rowIdx * rowHeightPx) - verticalOffsetPx).toFloat()
                        if (y + rowH <= 0f || y >= size.height) continue
                        for (run in runs) {
                            val x = run.col * charWidthPx
                            // Draw background rectangle for the whole run.
                            // Integer row height with integer y-positions tiles exactly —
                            // no overlap needed (matches Termux).
                            if (run.bg != Color.Unspecified && run.bg != terminalBgColor) {
                                bgPaint.color = run.bg.toArgb()
                                nativeCanvas.drawRect(
                                    x, y,
                                    x + run.text.length * charWidthPx, y + rowH,
                                    bgPaint
                                )
                            }
                            // Configure paint for this run's style.
                            textPaint.color = run.fg.toArgb()
                            val typefaceStyle = when {
                                run.bold && run.italic -> android.graphics.Typeface.BOLD_ITALIC
                                run.bold -> android.graphics.Typeface.BOLD
                                run.italic -> android.graphics.Typeface.ITALIC
                                else -> android.graphics.Typeface.NORMAL
                            }
                            textPaint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, typefaceStyle)
                            textPaint.isUnderlineText = run.underline
                            // Draw each character individually at its grid position.
                            val textY = y + baseline
                            for ((i, ch) in run.text.withIndex()) {
                                if (ch != ' ') {
                                    nativeCanvas.drawText(
                                        ch.toString(),
                                        x + i * charWidthPx,
                                        textY,
                                        textPaint
                                    )
                                }
                            }
                        }
                    }
                }

                // Invisible text layer for native text selection (long-press copy).
                // We strip all explicit span colors so text is invisible, but the
                // Compose SelectionContainer still draws a visible selection highlight.
                val selectionOutput = remember(terminalVersion) {
                    buildAnnotatedString {
                        append(
                            emulator.renderSelectionText(
                                scrollbackOffsetRows = 0,
                                windowRows = totalRows,
                            )
                        )
                    }
                }
                // Match the selection overlay line height to the canvas row height
                // so selection handles align with the rendered text.
                val selectionLineHeight = with(LocalDensity.current) { rowHeightPx.toSp() }
                val selectionStyle = remember(fontSizeSp, selectionLineHeight) {
                    terminalStyle.copy(
                        color = Color.Transparent,
                        lineHeight = selectionLineHeight,
                    )
                }
                val selectionColors = TextSelectionColors(
                    handleColor = MaterialTheme.colorScheme.tertiary,
                    backgroundColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                )
                CompositionLocalProvider(
                    LocalTextToolbar provides terminalTextToolbar,
                    LocalTextSelectionColors provides selectionColors
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(terminalScrollState)
                    ) {
                        SelectionContainer {
                            Text(
                                text = selectionOutput,
                                style = selectionStyle,
                                softWrap = false,
                                maxLines = Int.MAX_VALUE,
                                modifier = Modifier.fillMaxWidth()
                                    .clearAndSetSemantics { }
                            )
                        }
                    }
                }

                if (
                    connected &&
                    terminalLifecycleActive &&
                    emulator.cursorVisible &&
                    (!emulator.cursorBlinkEnabled || cursorBlinkOn) &&
                    cursorPos != null
                ) {
                    val cursorCol = cursorPos.second.coerceIn(0, (termCols - 1).coerceAtLeast(0))
                    val cursorRow = cursorPos.first.coerceIn(0, (termRows - 1).coerceAtLeast(0))
                    val cursorX = with(LocalDensity.current) { (cursorCol * charWidthPx).toDp() }
                    val cursorY = with(LocalDensity.current) { (cursorRow * rowHeightPx).toDp() }
                    val cursorW = with(LocalDensity.current) { charWidthPx.toDp() }
                    val cursorH = with(LocalDensity.current) { rowHeightPx.toDp() }

                    val cursorModifier = Modifier.offset(x = cursorX, y = cursorY).then(
                        when (emulator.cursorStyle) {
                            TerminalCursorStyle.BLOCK -> Modifier
                                .size(width = cursorW, height = cursorH)
                            TerminalCursorStyle.UNDERLINE -> Modifier
                                .offset(y = cursorH - 2.dp)
                                .size(width = cursorW, height = 2.dp)
                            TerminalCursorStyle.BAR -> Modifier
                                .size(width = 2.dp, height = cursorH)
                        },
                    )
                    Box(modifier = cursorModifier.background(Color(0xFFD3D7CF))) // Fixed palette — terminal cursor color, not themed
                }
            }
        }
    }
}

@Composable
internal fun TerminalPanelEdgeHighlight(
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "terminal_panel_hint")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "terminal_panel_hint_progress",
    )

    Box(
        modifier = modifier
            .graphicsLayer { alpha = 0.5f + progress * 0.5f }
            .background(accent.copy(alpha = 0.2f)),
    )
}

@Composable
internal fun TerminalPanelCoachmark(
    usesGestureNavigation: Boolean,
    modifier: Modifier = Modifier,
) {
    val isAmoled = isAmoledTheme()
    val accent = MaterialTheme.colorScheme.primary
    val coachmarkColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val transition = rememberInfiniteTransition(label = "terminal_panel_swipe_hint")
    val swipeProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "terminal_panel_swipe_progress",
    )
    Row(
        modifier = modifier.offset(x = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(
            modifier = Modifier
                .width(8.dp)
                .height(18.dp),
        ) {
            val path = Path().apply {
                moveTo(size.width, 0f)
                lineTo(0f, size.height / 2f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path = path, color = coachmarkColor)
        }
        Surface(
            modifier = Modifier
                .padding(end = 28.dp)
                .widthIn(max = 260.dp),
            shape = RoundedCornerShape(12.dp),
            color = coachmarkColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = if (isAmoled) 0.dp else 5.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!usesGestureNavigation) {
                    Canvas(
                        modifier = Modifier
                            .width(34.dp)
                            .height(24.dp),
                    ) {
                        val centerY = size.height / 2f
                        val startX = 3.dp.toPx()
                        val endX = 31.dp.toPx()
                        val movingX = startX + (endX - startX) * swipeProgress
                        drawLine(
                            color = accent.copy(alpha = 0.45f),
                            start = Offset(startX, centerY),
                            end = Offset(endX, centerY),
                            strokeWidth = 2.dp.toPx(),
                        )
                        drawLine(
                            color = accent,
                            start = Offset(endX - 6.dp.toPx(), centerY - 5.dp.toPx()),
                            end = Offset(endX, centerY),
                            strokeWidth = 2.dp.toPx(),
                        )
                        drawLine(
                            color = accent,
                            start = Offset(endX - 6.dp.toPx(), centerY + 5.dp.toPx()),
                            end = Offset(endX, centerY),
                            strokeWidth = 2.dp.toPx(),
                        )
                        drawCircle(accent, radius = 3.dp.toPx(), center = Offset(movingX, centerY))
                    }
                }
                Text(
                    text = stringResource(
                        if (usesGestureNavigation) {
                            R.string.chat_terminal_panel_hint_gestures
                        } else {
                            R.string.chat_terminal_panel_hint_swipe
                        },
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
