/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatScreenTopBar.kt
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.ui.screens.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.hiylo.starburst.domain.model.*
import org.hiylo.starburst.ui.screens.settings.SessionExport
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.AppLoadingEdge
import org.hiylo.starburst.ui.components.appPopupBorder
import org.hiylo.starburst.ui.components.appPopupContainerColor
import org.hiylo.starburst.ui.components.isAmoledTheme
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.ui.platform.ClipboardManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import org.hiylo.starburst.domain.model.FileDiff


/**
 * Chat Scaffold's topBar slot: session title/subtitle, the overflow menu, the
 * context-usage gauge and the banners under the bar. Extracted verbatim from
 * ChatScreen; every captured local arrives as a parameter (mutable ones as
 * MutableState so the writes stay on the same state object).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ChatScreenTopBar(
    viewModel: ChatViewModel,
    uiState: ChatUiState,
    isServerConnected: Boolean,
    isAmoled: Boolean,
    isGitRepository: Boolean,
    sessionDiffs: List<FileDiff>,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    context: Context,
    clipboardManager: ClipboardManager,
    exportLauncher: ActivityResultLauncher<String>,
    inputTextState: MutableState<TextFieldValue>,
    isTerminalModeState: MutableState<Boolean>,
    inputModeState: MutableState<String>,
    showMenuState: MutableState<Boolean>,
    showRenameDialogState: MutableState<Boolean>,
    showSessionDiffDialogState: MutableState<Boolean>,
    showTimelineDialogState: MutableState<Boolean>,
    showAttachmentOptionsState: MutableState<Boolean>,
    showSubagentContextDetailsState: MutableState<Boolean>,
    onNavigateBack: () -> Unit,
    onNavigateToSession: (String) -> Unit,
    onOpenInWebView: () -> Unit,
    onOpenWorkspace: (String) -> Unit,
    onOpenAgentsMd: (String) -> Unit,
    onOpenGit: () -> Unit,
    onOpenSharedSession: (String) -> Unit,
) {
    var inputText by inputTextState
    var isTerminalMode by isTerminalModeState
    var inputMode by inputModeState
    var showMenu by showMenuState
    var showRenameDialog by showRenameDialogState
    var showSessionDiffDialog by showSessionDiffDialogState
    var showTimelineDialog by showTimelineDialogState
    var showAttachmentOptions by showAttachmentOptionsState
    var showSubagentContextDetails by showSubagentContextDetailsState

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
                // Subtitle: project path, total tokens and cost for the session
                val totalTokens = uiState.totalInputTokens + uiState.totalOutputTokens
                val hasTokenOrCost = totalTokens > 0 || uiState.totalCost > 0
                val hasDirectory = uiState.sessionDirectory.isNotBlank()
                if (hasDirectory || hasTokenOrCost) {
                    val parts = mutableListOf<String>()
                    if (hasDirectory) {
                        parts.add(uiState.sessionDirectory)
                    }
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
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                        text = { Text(stringResource(R.string.menu_session_timeline)) },
                        onClick = {
                            showMenu = false
                            showTimelineDialog = true
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Timeline, contentDescription = null)
                        },
                    )
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
}
