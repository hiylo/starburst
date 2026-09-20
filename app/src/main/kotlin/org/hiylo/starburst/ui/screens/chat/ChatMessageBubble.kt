/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatMessageBubble.kt
 * Date : 2026/09/14 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.domain.model.*
import java.util.Locale
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.isAmoledTheme
import org.hiylo.starburst.ui.components.cartoonChrome
import org.hiylo.starburst.ui.theme.isCartoonStyle



@Composable
internal fun toolOutputContainerColor(isAmoled: Boolean): Color {
    return when {
        isAmoled -> Color.Black
        isSystemInDarkTheme() -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f)
    }
}

@Composable
internal fun Modifier.expandableToolHeader(
    expanded: Boolean,
    onClick: () -> Unit,
): Modifier {
    val state = stringResource(if (expanded) R.string.chat_collapse else R.string.chat_expand)
    return semantics { stateDescription = state }
        .clickable(role = Role.Button, onClick = onClick)
}
@Composable
internal fun Modifier.codeHorizontalScroll(): Modifier {
    return if (!LocalCodeWordWrap.current) {
        this.horizontalScroll(rememberScrollState())
    } else {
        this
    }
}
/** Format a token count to a human-readable string (e.g., 1.2k, 45.3k, 1.2M). */
internal fun formatTokenCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.ROOT, "%.1fk", count / 1_000.0)
        else -> count.toString()
    }
}

private fun formatAssistantErrorMessage(error: Message.Assistant.ErrorInfo?): String? {
    if (error == null) return null
    val raw = error.message.ifBlank { error.name }
    return raw.ifBlank { null }
}

/**
 * Determine the "status text" for a group of step parts (like WebUI).
 * E.g., "Making edits", "Running commands", "Searching codebase", "Thinking"
 */
@Composable
private fun resolveStepsStatus(stepParts: List<Part>): String {
    val toolParts = stepParts.filterIsInstance<Part.Tool>()
    val hasRunning = toolParts.any { it.state is ToolState.Running }
    if (!hasRunning && toolParts.all { it.state is ToolState.Completed || it.state is ToolState.Error }) {
        // All done — summarize
        val editCount = toolParts.count { it.tool in listOf("edit", "write", "apply_patch", "multiedit") }
        val bashCount = toolParts.count { it.tool == "bash" }
        val searchCount = toolParts.count { it.tool in listOf("glob", "grep", "read", "list", "listDirectory") }
        return when {
            editCount > 0 && bashCount == 0 && searchCount == 0 -> {
                if (editCount == 1) 
                    stringResource(R.string.chat_status_edits, editCount)
                else 
                    stringResource(R.string.chat_status_edits_plural, editCount)
            }
            bashCount > 0 && editCount == 0 && searchCount == 0 -> {
                if (bashCount == 1)
                    stringResource(R.string.chat_status_commands, bashCount)
                else
                    stringResource(R.string.chat_status_commands_plural, bashCount)
            }
            else -> {
                if (toolParts.size == 1)
                    stringResource(R.string.chat_status_steps, toolParts.size)
                else
                    stringResource(R.string.chat_status_steps_plural, toolParts.size)
            }
        }
    }
    // Currently running — describe what's happening
    val runningTool = toolParts.lastOrNull { it.state is ToolState.Running }
    return when (runningTool?.tool) {
        "edit", "write", "multiedit" -> stringResource(R.string.chat_status_making_edits)
        "bash" -> stringResource(R.string.chat_status_running_commands)
        "read", "glob", "grep", "list", "listDirectory" -> stringResource(R.string.chat_status_searching)
        "webfetch" -> stringResource(R.string.chat_status_fetching_url)
        "task" -> stringResource(R.string.chat_status_running_subagent)
        "todowrite" -> stringResource(R.string.chat_status_updating_tasks)
        else -> stringResource(R.string.chat_status_thinking)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ChatMessageBubble(
    chatMessages: List<ChatMessage>,
    onRevert: (() -> Unit)? = null,
    onCopyText: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onSummarize: (() -> Unit)? = null,
    onQuoteReply: (() -> Unit)? = null,
    onBookmark: (() -> Unit)? = null,
    onNavigateToChildSession: (String) -> Unit = {},
    onContinue: (() -> Unit)? = null,
) {
    val chatMessage = chatMessages.last()
    val isUser = chatMessage.isUser
    val isAmoled = isAmoledTheme()
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val backgroundColor = if (isAmoled) {
        Color.Black
    } else if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isAmoled) {
        MaterialTheme.colorScheme.onSurface
    } else if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val bubbleBorder = if (isAmoled) {
        BorderStroke(
            1.dp,
            if (isUser) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
            }
        )
    } else {
        null
    }
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current

    // Separate parts into text/reasoning (shown directly) and step parts (behind toggle)
    val allParts = chatMessages.flatMap { it.parts }
    val visibleParts = if (isUser) {
        allParts.filter { part ->
            when (part) {
                is Part.Text -> part.synthetic != true && part.ignored != true && part.text.isNotBlank()
                else -> true
            }
        }
    } else {
        allParts
    }

    val userMessage = chatMessage.message as? Message.User
    val assistantMessage = chatMessages.mapNotNull { it.message as? Message.Assistant }.lastOrNull()
    val assistantErrorText = chatMessages.firstNotNullOfOrNull {
        formatAssistantErrorMessage((it.message as? Message.Assistant)?.error)
    }
    val userFallbackText = userMessage?.summary?.body?.takeIf { it.isNotBlank() }
        ?: userMessage?.summary?.title?.takeIf { it.isNotBlank() }
    val userCommandLabel = if (isUser) {
        resolveUserCommandLabel(chatMessage.parts)
    } else {
        null
    }

    // Tool cards belong in the response timeline. Each card owns its own collapse state.
    val contentParts: List<Part>
    val stepParts: List<Part>
    if (!isUser) {
        contentParts = visibleParts.filter { part ->
            part is Part.Text || part is Part.Reasoning || part is Part.Patch ||
                    part is Part.File || part is Part.Permission || part is Part.Question ||
                    part is Part.Abort || part is Part.Retry || part is Part.Tool
        }
        stepParts = emptyList()
    } else {
        contentParts = visibleParts
        stepParts = emptyList()
    }

    val hasRenderableUserPart = contentParts.any(::isBubbleRenderablePart)
    val hasRenderableUserContent = !isUser || hasRenderableUserPart || userFallbackText != null || userCommandLabel != null
    val hasRenderableAssistantContent = isUser ||
            contentParts.isNotEmpty() ||
            stepParts.isNotEmpty() ||
            assistantErrorText != null
    if (!hasRenderableUserContent || !hasRenderableAssistantContent) {
        return
    }

    val hasSteps = stepParts.isNotEmpty()
    val autoExpand = LocalCollapseTools.current
    var stepsExpanded by remember(autoExpand) { mutableStateOf(autoExpand) }

    // Check if any tool is currently running (show spinner)
    val hasRunningTool = stepParts.any { it is Part.Tool && it.state is ToolState.Running }
    var showRevertConfirmation by remember { mutableStateOf(false) }

    if (showRevertConfirmation && onRevert != null) {
        RevertConfirmationDialog(
            onDismiss = { showRevertConfirmation = false },
            onConfirm = {
                showRevertConfirmation = false
                onRevert()
            },
        )
    }

    // 卡通风格下气泡更圆、更不对称（右上角收成小尖），更像漫画对话框。
    val bubbleShape = if (isUser) {
        RoundedCornerShape(
            topStart = if (isCartoonStyle()) 26.dp else 12.dp,
            topEnd = if (isCartoonStyle()) 6.dp else 4.dp,
            bottomStart = if (isCartoonStyle()) 26.dp else 12.dp,
            bottomEnd = if (isCartoonStyle()) 22.dp else 12.dp,
        )
    } else {
        RoundedCornerShape(if (isCartoonStyle()) 24.dp else 12.dp)
    }

    val bubbleContent: @Composable (Modifier) -> Unit = { modifier ->
        Surface(
            shape = bubbleShape,
            color = backgroundColor,
            border = bubbleBorder,
            tonalElevation = 0.dp,
            modifier = modifier.fillMaxWidth()
        ) {
            val compact = LocalCompactMessages.current
            Box {
                Column(
                    modifier = Modifier
                        .padding(
                            PaddingValues(
                                start = if (isUser) 8.dp else if (compact) 10.dp else 12.dp,
                                end = if (isUser) 8.dp else if (compact) 10.dp else 12.dp,
                                top = if (isUser) 4.dp else if (compact) 6.dp else 8.dp,
                                bottom = if (isUser || compact) 2.dp else 4.dp,
                            ),
                        ),
                    verticalArrangement = Arrangement.spacedBy(if (isUser) 1.dp else 2.dp),
                ) {
                    // Steps toggle (like WebUI "Show/Hide steps")
                    if (hasSteps) {
                        val stepsStatus = resolveStepsStatus(stepParts)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { performHaptic(hapticView, hapticOn); stepsExpanded = !stepsExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasRunningTool) {
                                PulsingDotsIndicator(
                                    dotSize = 5.dp,
                                    dotSpacing = 3.dp,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            } else {
                                Icon(
                                    imageVector = if (stepsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = textColor.copy(alpha = 0.5f)
                                )
                            }
                            Text(
                                text = if (stepsExpanded) stringResource(R.string.chat_hide_steps) else stepsStatus,
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.6f)
                            )
                        }

                        // Expanded step parts
                        AnimatedVisibility(visible = stepsExpanded) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (part in stepParts) {
                                    PartContent(
                                        part = part,
                                        textColor = textColor,
                                        isUser = isUser,
                                        onNavigateToChildSession = onNavigateToChildSession,
                                        onContinue = onContinue,
                                    )
                                }
                            }
                        }
                    }

                    val contentGroups = if (isUser) {
                        listOf(contentParts)
                    } else {
                        chatMessages.map { message ->
                            message.parts.filter { part ->
                                part is Part.Text || part is Part.Reasoning || part is Part.Patch ||
                                    part is Part.File || part is Part.Permission || part is Part.Question ||
                                    part is Part.Abort || part is Part.Retry || part is Part.Tool
                            }
                        }.filter { it.isNotEmpty() }
                    }
                    var renderedContent = false
                    contentGroups.forEachIndexed { groupIndex, groupParts ->
                        val imageFiles = groupParts.filterIsInstance<Part.File>()
                            .filter { it.mime.startsWith("image/") && !it.url.isNullOrBlank() }
                        val renderableParts = groupParts.filter { part ->
                            !(part is Part.File && part.mime.startsWith("image/") && !part.url.isNullOrBlank())
                        }.filter(::isBubbleRenderablePart)
                        if (imageFiles.isNotEmpty()) {
                            ImageThumbnailRow(imageFiles = imageFiles)
                            renderedContent = true
                        }
                        renderableParts.forEach { part ->
                            PartContent(
                                part = part,
                                textColor = textColor,
                                isUser = isUser,
                                onNavigateToChildSession = onNavigateToChildSession,
                                onContinue = onContinue,
                            )
                            renderedContent = true
                        }
                        if (!isUser && LocalShowTurnDividers.current && groupIndex < contentGroups.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }

                    if (isUser && !renderedContent && userCommandLabel != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RateReview,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = textColor.copy(alpha = 0.7f)
                            )
                            Text(
                                text = userCommandLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor.copy(alpha = 0.85f)
                            )
                        }
                    }

                    if (!isUser && assistantErrorText != null) {
                        Surface(
                            color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = if (isAmoled) 0.75f else 0.35f)),
                            tonalElevation = 0.dp,
                        ) {
                            ErrorPayloadContent(
                                text = assistantErrorText,
                                textStyle = MaterialTheme.typography.bodySmall,
                                textColor = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }

                    // If text parts are absent but server provided a summary, render it.
                    if (visibleParts.isEmpty() && isUser && userFallbackText != null) {
                        Text(
                            text = userFallbackText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(alpha = 0.5f)
                        )
                    }

                    MessageMetadataRow(
                        message = chatMessage.message,
                        textColor = textColor,
                        delivery = chatMessage.delivery,
                        onRevert = if (isUser && onRevert != null) {
                            { showRevertConfirmation = true }
                        } else null,
                        onCopyText = onCopyText,
                        onRegenerate = onRegenerate,
                        onEdit = onEdit,
                        onSummarize = onSummarize,
                        onQuoteReply = onQuoteReply,
                        onBookmark = onBookmark,
                    )
                }
            }
        }
    }

    var longPressMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    performHaptic(hapticView, hapticOn)
                    longPressMenuExpanded = true
                },
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = alignment
        ) {
            bubbleContent(Modifier.cartoonChrome(bubbleShape))
        }
        DropdownMenu(
            expanded = longPressMenuExpanded,
            onDismissRequest = { longPressMenuExpanded = false },
        ) {
            if (onCopyText != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_copy)) },
                    onClick = {
                        longPressMenuExpanded = false
                        onCopyText()
                    },
                )
            }
            if (isUser && onEdit != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_edit_resend)) },
                    onClick = {
                        longPressMenuExpanded = false
                        onEdit()
                    },
                )
            }
            if (onQuoteReply != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_quote_reply)) },
                    onClick = {
                        longPressMenuExpanded = false
                        onQuoteReply()
                    },
                )
            }
            if (onBookmark != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_bookmark)) },
                    leadingIcon = { Icon(Icons.Default.BookmarkBorder, contentDescription = null) },
                    onClick = {
                        longPressMenuExpanded = false
                        onBookmark()
                    },
                )
            }
        }
    }
}

@Composable
private fun MessageMetadataRow(
    message: Message,
    textColor: Color,
    delivery: MessageDelivery?,
    onRevert: (() -> Unit)?,
    onCopyText: (() -> Unit)?,
    onRegenerate: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onSummarize: (() -> Unit)?,
    onQuoteReply: (() -> Unit)? = null,
    onBookmark: (() -> Unit)? = null,
) {
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current
    val timestamp = remember(message.time.created) {
        val millis = if (message.time.created < 10_000_000_000L) message.time.created * 1000 else message.time.created
        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(millis))
    }
    val agent = when (message) {
        is Message.User -> message.agent
        is Message.Assistant -> message.agent
    }
    val model = when (message) {
        is Message.User -> message.model?.modelId
        is Message.Assistant -> message.modelId
    }
    val tokenSummary = when (message) {
        is Message.User -> null
        is Message.Assistant -> message.tokens?.let { tokens ->
            val input = tokens.input + tokens.cache.read
            "↑${formatTokenCount(input)} ↓${formatTokenCount(tokens.output)}"
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(timestamp, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.42f))
        if (delivery != null) {
            Surface(
                shape = RoundedCornerShape(5.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Text(
                    text = stringResource(
                        if (delivery == MessageDelivery.PROMOTED) R.string.chat_message_sent
                        else R.string.chat_message_queued,
                    ),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        if (!agent.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = textColor.copy(alpha = 0.12f),
            ) {
                Text(
                    agent.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.68f),
                )
            }
        }
        if (!model.isNullOrBlank()) {
            Text(
                model,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.48f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        if (tokenSummary != null) {
            Text(tokenSummary, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.42f))
        }
        var menuExpanded by remember { mutableStateOf(false) }
        Box {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .semantics { role = Role.Button }
                    .clickable {
                        performHaptic(hapticView, hapticOn)
                        menuExpanded = true
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    modifier = Modifier.size(18.dp),
                    tint = textColor.copy(alpha = 0.7f),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                if (onCopyText != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_copy)) },
                        onClick = {
                            menuExpanded = false
                            onCopyText()
                        },
                    )
                }
                if (onRegenerate != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_regenerate)) },
                        onClick = {
                            menuExpanded = false
                            onRegenerate()
                        },
                    )
                }
                if (onEdit != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_edit_message)) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                }
                if (onSummarize != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_summarize)) },
                        onClick = {
                            menuExpanded = false
                            onSummarize()
                        },
                    )
                }
                if (onRevert != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_revert)) },
                        onClick = {
                            menuExpanded = false
                            onRevert()
                        },
                    )
                }
                if (onQuoteReply != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_quote_reply)) },
                        onClick = {
                            menuExpanded = false
                            onQuoteReply()
                        },
                    )
                }
            }
        }
    }
}
@Composable
private fun resolveUserCommandLabel(parts: List<Part>): String? {
    val subtaskParts = parts.filterIsInstance<Part.Subtask>()

    val commandFromSubtask = subtaskParts
        .firstNotNullOfOrNull { it.command }
        ?.removePrefix("/")
        ?.trim()
        ?.lowercase()

    val commandFromText = parts
        .filterIsInstance<Part.Text>()
        .firstNotNullOfOrNull { textPart ->
            val text = textPart.text.trim()
            if (!text.startsWith("/")) return@firstNotNullOfOrNull null
            text.removePrefix("/").substringBefore(' ').trim().lowercase().takeIf { it.isNotBlank() }
        }

    val inferredReviewFromPrompt = subtaskParts.any { subtask ->
        val prompt = subtask.prompt.lowercase()
        val description = subtask.description?.lowercase().orEmpty()
        "review changes" in prompt || "review" in description
    }

    val command = commandFromSubtask ?: commandFromText ?: if (inferredReviewFromPrompt) "review" else null

    return when (command) {
        "review" -> stringResource(R.string.menu_review_changes)
        null -> {
            val hasNonRenderableOnly = parts.any { part ->
                part !is Part.Text &&
                        part !is Part.Reasoning &&
                        part !is Part.Patch &&
                        part !is Part.File &&
                        part !is Part.Permission &&
                        part !is Part.Question &&
                        part !is Part.Abort &&
                        part !is Part.Retry
            }
            if (hasNonRenderableOnly) stringResource(R.string.chat_tool_running_command) else null
        }
        else -> stringResource(R.string.chat_tool_running_command)
    }
}
@Composable
internal fun ChatLinkHandlerProvider(
    serverBaseUrl: String,
    onOpenChatLink: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalChatLinkHandler provides ChatLinkHandler(
            serverBaseUrl = serverBaseUrl,
            openInApp = onOpenChatLink,
        ),
        content = content,
    )
}

@Composable
private fun PartContent(
    part: Part,
    textColor: Color,
    isUser: Boolean = false,
    onNavigateToChildSession: (String) -> Unit = {},
    onContinue: (() -> Unit)? = null,
) {
    when (part) {
        is Part.Text -> {
            // Hide synthetic/ignored text parts (internal system content)
            if (part.text.isNotBlank() && part.synthetic != true && part.ignored != true) {
                MarkdownContent(
                    markdown = part.text,
                    textColor = textColor,
                    isUser = isUser
                )
            }
        }
        is Part.Reasoning -> {
            if (part.text.isNotBlank()) {
                ReasoningBlock(part = part)
            }
        }
        is Part.Tool -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // todoread parts are filtered out entirely (WebUI convention)
                if (part.tool == "todoread") {
                    // skip
                } else if (part.tool == "todowrite") {
                    TodoListCard(tool = part)
                } else {
                    when (part.tool) {
                        "edit", "multiedit" -> EditToolCard(tool = part)
                        "write" -> WriteToolCard(tool = part)
                        "apply_patch" -> ApplyPatchToolCard(tool = part)
                        "bash" -> BashToolCard(tool = part)
                        "read" -> ReadToolCard(tool = part)
                        "glob", "grep" -> SearchToolCard(tool = part)
                        "task" -> TaskToolCard(tool = part, onNavigateToChildSession = onNavigateToChildSession)
                        else -> ToolCallCard(tool = part)
                    }
                }
                val attachments = (part.state as? ToolState.Completed)?.attachments.orEmpty()
                    .mapIndexed { index, attachment ->
                        Part.File(
                            id = attachment.id.ifBlank { "${part.id}-attachment-$index" },
                            sessionId = attachment.sessionId.ifBlank { part.sessionId },
                            messageId = attachment.messageId.ifBlank { part.messageId },
                            mime = attachment.mime,
                            filename = attachment.filename,
                            url = attachment.url ?: attachment.data,
                            source = attachment.source,
                        )
                    }
                val images = attachments.filter { it.mime.startsWith("image/") && !it.url.isNullOrBlank() }
                if (images.isNotEmpty()) ImageThumbnailRow(images)
                attachments.filterNot { it in images }.forEach { FileCard(it) }
            }
        }
        is Part.StepStart -> {
            // Visual separator between steps (hidden - WebUI doesn't show these)
        }
        is Part.StepFinish -> {
            // Token/cost info hidden from message bubbles (WebUI convention)
        }
        is Part.Patch -> {
            PatchCard(patch = part)
        }
        is Part.File -> {
            FileCard(file = part)
        }
        is Part.Permission -> {
            Text(
                text = stringResource(R.string.chat_permission_label, part.message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        is Part.Question -> {
            Text(
                text = stringResource(R.string.chat_question_inline, part.question),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        is Part.Abort -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.chat_aborted, part.reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                ContinueInlineAction(onContinue)
            }
        }
        is Part.Retry -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.chat_retry, part.attempt, part.errorMessage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                ContinueInlineAction(onContinue)
            }
        }
        // Ignore less relevant parts
        is Part.Snapshot, is Part.Subtask, is Part.Compaction,
        is Part.Agent, is Part.SessionTurn, is Part.Unknown -> { /* skip */ }
    }
}

/**
 * 错误信息里的「继续」按钮：agent 处理失败（中止 / 重试失败）时，
 * 点击后继续处理当前会话。
 */
@Composable
private fun ContinueInlineAction(onContinue: (() -> Unit)?) {
    if (onContinue == null) return
    TextButton(onClick = onContinue) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.chat_continue), style = MaterialTheme.typography.labelSmall)
    }
}


@Composable
private fun ReasoningBlock(part: Part.Reasoning) {
    val expandByDefault = LocalExpandReasoning.current
    var expanded by rememberSaveable(part.id, expandByDefault) { mutableStateOf(expandByDefault) }
    val extractedTitle = extractReasoningTitle(part.text)
    val reasoningBody = if (extractedTitle != null) removeReasoningTitleLine(part.text) else part.text
    val hasReasoningBody = reasoningBody.isNotBlank()
    val reasoningTitle = extractedTitle ?: part.time?.let { time ->
        time.end?.let { end ->
            val durationMs = (end - time.start).coerceAtLeast(0)
            val duration = if (durationMs < 1000) {
                "${durationMs}ms"
            } else {
                String.format(Locale.getDefault(), "%.1fs", durationMs / 1000.0)
            }
            stringResource(R.string.chat_reasoning_complete, duration)
        }
    } ?: stringResource(R.string.chat_reasoning_active)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { modifier ->
                    if (hasReasoningBody) {
                        modifier.expandableToolHeader(expanded) { expanded = !expanded }
                    } else modifier
                }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            Text(
                text = reasoningTitle,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (hasReasoningBody) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.chat_collapse else R.string.chat_expand),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                )
            }
        }
        AnimatedVisibility(visible = expanded && hasReasoningBody) {
            MarkdownContent(
                markdown = reasoningBody,
                textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                isUser = false,
            )
        }
    }
}
