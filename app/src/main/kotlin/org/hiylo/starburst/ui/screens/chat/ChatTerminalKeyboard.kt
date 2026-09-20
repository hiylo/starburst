/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatTerminalKeyboard.kt
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.ui.screens.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.key
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.hiylo.starburst.domain.model.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.hiylo.starburst.ui.components.isAmoledTheme

/**
 * Virtual Ctrl/Fn/arrow key row of the terminal overlay and the key-transformation
 * helpers it and the terminal input bar share. Extracted verbatim from ChatTerminal.kt.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TerminalKeyboardOverlay(
    connected: Boolean,
    ctrlLatched: Boolean,
    altLatched: Boolean,
    cursorApp: Boolean,
    onToggleDrawer: () -> Unit,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onSendInput: (String) -> Unit,
    onCtrlC: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isAmoled = isAmoledTheme()
    // Arrow / Home / End sequences depend on DECCKM
    val arrowUp    = if (cursorApp) "\u001BOA" else "\u001B[A"
    val arrowDown  = if (cursorApp) "\u001BOB" else "\u001B[B"
    val arrowRight = if (cursorApp) "\u001BOC" else "\u001B[C"
    val arrowLeft  = if (cursorApp) "\u001BOD" else "\u001B[D"
    val home       = if (cursorApp) "\u001BOH" else "\u001B[H"
    val end        = if (cursorApp) "\u001BOF" else "\u001B[F"

    Surface(
        modifier = modifier,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 0.dp),
        ) {
            // Row 1: matches Termux default extra keys
            TerminalKeyRow(
                isAmoled = isAmoled,
                keys = listOf(
                    TerminalKey("ESC", popupLabel = "☰", popupAction = onToggleDrawer) { onSendInput("\u001B") },
                    TerminalKey("/") { onSendInput("/") },
                    TerminalKey("-", popupLabel = "|", popupAction = { onSendInput("|") }) { onSendInput("-") },
                    TerminalKey("HOME") { onSendInput(home) },
                    TerminalKey(arrow = TerminalArrowDirection.UP, repeatable = true) { onSendInput(arrowUp) },
                    TerminalKey("END") { onSendInput(end) },
                    TerminalKey("PGUP") { onSendInput("\u001B[5~") },
                )
            )
            if (!isAmoled) {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
            // Row 2: matches Termux default extra keys
            TerminalKeyRow(
                isAmoled = isAmoled,
                keys = listOf(
                    TerminalKey("\u21B9") { onSendInput("\t") },
                    TerminalKey("CTRL", active = ctrlLatched, action = onToggleCtrl),
                    TerminalKey("ALT", active = altLatched, action = onToggleAlt),
                    TerminalKey(arrow = TerminalArrowDirection.LEFT, repeatable = true) { onSendInput(arrowLeft) },
                    TerminalKey(arrow = TerminalArrowDirection.DOWN, repeatable = true) { onSendInput(arrowDown) },
                    TerminalKey(arrow = TerminalArrowDirection.RIGHT, repeatable = true) { onSendInput(arrowRight) },
                    TerminalKey("PGDN") { onSendInput("\u001B[6~") },
                )
            )
        }
    }
}

private data class TerminalKey(
    val label: String = "",
    val active: Boolean = false,
    val popupLabel: String? = null,
    val popupAction: (() -> Unit)? = null,
    val arrow: TerminalArrowDirection? = null,
    val repeatable: Boolean = false,
    val action: () -> Unit
)

private enum class TerminalArrowDirection { UP, DOWN, LEFT, RIGHT }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TerminalKeyRow(keys: List<TerminalKey>, isAmoled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        keys.forEachIndexed { index, key ->
            if (index > 0 && !isAmoled) {
                // Thin vertical divider between keys
                Box(
                    Modifier
                        .width(1.dp)
                        .height(34.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
            val keyColor = when {
                isAmoled && key.active -> MaterialTheme.colorScheme.primary
                isAmoled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                key.active -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val interactionModifier = if (key.repeatable) {
                Modifier.pointerInput(key.action) {
                    detectTapGestures(
                        onPress = {
                            key.action()
                            kotlinx.coroutines.coroutineScope {
                                val repeatJob = launch {
                                    delay(400)
                                    while (true) {
                                        key.action()
                                        delay(70)
                                    }
                                }
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    repeatJob.cancel()
                                }
                            }
                        },
                    )
                }
            } else {
                Modifier.combinedClickable(
                    onClick = key.action,
                    onLongClick = { key.popupAction?.invoke() },
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .then(
                        when {
                            isAmoled -> Modifier.border(
                                width = 0.5.dp,
                                color = if (key.active) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                },
                            )
                            key.active -> Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            else -> Modifier
                        }
                    )
                    .then(interactionModifier)
            ) {
                if (key.arrow != null) {
                    TerminalArrow(key.arrow, keyColor)
                } else {
                    Text(
                        text = key.label,
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = if (key.label.length == 1) 16.sp else 14.sp,
                        ),
                        color = keyColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalArrow(direction: TerminalArrowDirection, color: Color) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val shaft = 8.dp.toPx()
        val head = 5.dp.toPx()
        val stroke = 1.25.dp.toPx()
        val (start, end) = when (direction) {
            TerminalArrowDirection.UP -> Offset(center.x, center.y + shaft) to Offset(center.x, center.y - shaft)
            TerminalArrowDirection.DOWN -> Offset(center.x, center.y - shaft) to Offset(center.x, center.y + shaft)
            TerminalArrowDirection.LEFT -> Offset(center.x + shaft, center.y) to Offset(center.x - shaft, center.y)
            TerminalArrowDirection.RIGHT -> Offset(center.x - shaft, center.y) to Offset(center.x + shaft, center.y)
        }
        drawLine(color, start, end, stroke, cap = StrokeCap.Round)
        val heads = when (direction) {
            TerminalArrowDirection.UP -> listOf(Offset(end.x - head, end.y + head), Offset(end.x + head, end.y + head))
            TerminalArrowDirection.DOWN -> listOf(Offset(end.x - head, end.y - head), Offset(end.x + head, end.y - head))
            TerminalArrowDirection.LEFT -> listOf(Offset(end.x + head, end.y - head), Offset(end.x + head, end.y + head))
            TerminalArrowDirection.RIGHT -> listOf(Offset(end.x - head, end.y - head), Offset(end.x - head, end.y + head))
        }
        heads.forEach { drawLine(color, end, it, stroke, cap = StrokeCap.Round) }
    }
}

internal fun applyTerminalModifiers(input: String, ctrl: Boolean, alt: Boolean): String {
    if (input.isEmpty()) return input
    var out = input
    if (ctrl) {
        out = out.map { ch -> ctrlTransform(ch) }.joinToString("")
    }
    if (alt) {
        out = "\u001B$out"
    }
    return out
}

internal data class FnBindingResult(
    val output: String,
    val showVolumeUi: Boolean = false,
    val toggleKeyboard: Boolean = false,
)

internal fun applyTermuxFnBindings(input: String, cursorApp: Boolean): FnBindingResult {
    if (input.isEmpty()) return FnBindingResult(output = "")

    val up = if (cursorApp) "\u001BOA" else "\u001B[A"
    val down = if (cursorApp) "\u001BOB" else "\u001B[B"
    val right = if (cursorApp) "\u001BOC" else "\u001B[C"
    val left = if (cursorApp) "\u001BOD" else "\u001B[D"

    val out = StringBuilder()
    var showVolumeUi = false
    var toggleKeyboard = false
    for (ch in input) {
        when (ch.lowercaseChar()) {
            'w' -> out.append(up)
            'a' -> out.append(left)
            's' -> out.append(down)
            'd' -> out.append(right)

            'p' -> out.append("\u001B[5~")
            'n' -> out.append("\u001B[6~")

            't' -> out.append('\t')
            'i' -> out.append("\u001B[2~")
            'h' -> out.append('~')
            'u' -> out.append('_')
            'l' -> out.append('|')

            '1' -> out.append("\u001BOP")
            '2' -> out.append("\u001BOQ")
            '3' -> out.append("\u001BOR")
            '4' -> out.append("\u001BOS")
            '5' -> out.append("\u001B[15~")
            '6' -> out.append("\u001B[17~")
            '7' -> out.append("\u001B[18~")
            '8' -> out.append("\u001B[19~")
            '9' -> out.append("\u001B[20~")
            '0' -> out.append("\u001B[21~")

            'e' -> out.append('\u001B')
            '.' -> out.append(28.toChar()) // Ctrl+\

            'b', 'f', 'x' -> {
                out.append('\u001B')
                out.append(ch.lowercaseChar())
            }

            // Termux also handles FN+v (volume UI) and FN+q/k (toggle toolbar),
            // which are app-specific actions. We consume them with no terminal output.
            'v' -> showVolumeUi = true
            'q', 'k' -> toggleKeyboard = true

            else -> Unit
        }
    }
    return FnBindingResult(
        output = out.toString(),
        showVolumeUi = showVolumeUi,
        toggleKeyboard = toggleKeyboard,
    )
}

private fun ctrlTransform(ch: Char): Char {
    return when {
        ch in 'a'..'z' -> (ch.code - 96).toChar()
        ch in 'A'..'Z' -> (ch.code - 64).toChar()
        ch == ' ' -> 0.toChar()
        ch == '[' -> 27.toChar()
        ch == '\\' -> 28.toChar()
        ch == ']' -> 29.toChar()
        ch == '^' -> 30.toChar()
        ch == '_' -> 31.toChar()
        else -> ch
    }
}
