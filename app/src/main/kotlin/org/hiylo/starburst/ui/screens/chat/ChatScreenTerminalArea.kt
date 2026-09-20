/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatScreenTerminalArea.kt
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.hiylo.starburst.domain.model.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.AppSecondaryButton
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.Density


/**
 * The isTerminalMode branch of the chat body: the tab drawer, the terminal
 * surface, the virtual Ctrl/Alt key row and the first-run panel hint.
 * Extracted verbatim from ChatScreen; every captured local arrives as a
 * parameter (mutable ones as MutableState so the writes stay on the same
 * state object).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ChatScreenTerminalArea(
    viewModel: ChatViewModel,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    context: Context,
    isAmoled: Boolean,
    terminalVersion: Long,
    terminalConnected: Boolean,
    terminalTabs: List<TerminalTabUi>,
    activeTerminalTabId: String?,
    activeTerminalTab: TerminalTabUi?,
    terminalFontSizeSp: Float,
    terminalDrawerState: DrawerState,
    terminalFocusRequester: FocusRequester,
    keyboardController: SoftwareKeyboardController?,
    density: Density,
    usesGestureNavigation: Boolean,
    pasteClipboardToTerminal: () -> Unit,
    sendTerminalChunk: (String) -> Unit,
    terminalCtrlLatchedState: MutableState<Boolean>,
    terminalAltLatchedState: MutableState<Boolean>,
    showTerminalPanelHintOverlayState: MutableState<Boolean>,
    terminalOverlayHeightPxState: MutableState<Int>,
) {
    var terminalCtrlLatched by terminalCtrlLatchedState
    var terminalAltLatched by terminalAltLatchedState
    var showTerminalPanelHintOverlay by showTerminalPanelHintOverlayState
    var terminalOverlayHeightPx by terminalOverlayHeightPxState

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
                onSendInput = sendTerminalChunk,
                onPaste = pasteClipboardToTerminal,
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
            onSendInput = sendTerminalChunk,
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
