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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownImage
import com.mikepenz.markdown.coil2.Coil2ImageTransformerImpl
import com.mikepenz.markdown.model.DefaultMarkdownAnnotator
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.hiylo.starburst.domain.model.*
import org.hiylo.starburst.ui.theme.CodeTypography
import org.hiylo.starburst.ui.theme.StatusConnected
import org.hiylo.starburst.ui.theme.StatusError
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.json.jsonArray
import java.util.Locale
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import android.os.Build
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.AppSecondaryButton
import org.hiylo.starburst.ui.components.isAmoledTheme


@Composable
private fun toolOutputContainerColor(isAmoled: Boolean): Color {
    return when {
        isAmoled -> Color.Black
        isSystemInDarkTheme() -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.82f)
    }
}

@Composable
private fun Modifier.expandableToolHeader(
    expanded: Boolean,
    onClick: () -> Unit,
): Modifier {
    val state = stringResource(if (expanded) R.string.chat_collapse else R.string.chat_expand)
    return semantics { stateDescription = state }
        .clickable(role = Role.Button, onClick = onClick)
}
@Composable
private fun Modifier.codeHorizontalScroll(): Modifier {
    return if (!LocalCodeWordWrap.current) {
        this.horizontalScroll(rememberScrollState())
    } else {
        this
    }
}
/** Format a token count to a human-readable string (e.g., 1.2k, 45.3k, 1.2M). */
internal fun formatTokenCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fk", count / 1_000.0)
        else -> count.toString()
    }
}

private fun formatAssistantErrorMessage(error: Message.Assistant.ErrorInfo?): String? {
    if (error == null) return null
    val raw = error.message.ifBlank { error.name }
    return raw.ifBlank { null }
}

private enum class HtmlErrorViewMode {
    Page,
    Code,
}

@Composable
internal fun ErrorPayloadContent(
    text: String,
    textStyle: TextStyle,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    if (!looksLikeHtmlPayload(text)) {
        SelectionContainer {
            Text(
                text = text,
                style = textStyle,
                color = textColor,
                modifier = modifier,
            )
        }
        return
    }

    var mode by rememberSaveable(text) { mutableStateOf(HtmlErrorViewMode.Code) }
    val htmlForPreview = remember(text) { normalizeHtmlForEmbeddedPreview(text) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == HtmlErrorViewMode.Code,
                onClick = { mode = HtmlErrorViewMode.Code },
                label = { Text(stringResource(R.string.chat_error_view_code)) },
            )
            FilterChip(
                selected = mode == HtmlErrorViewMode.Page,
                onClick = { mode = HtmlErrorViewMode.Page },
                label = { Text(stringResource(R.string.chat_error_view_page)) },
            )
        }

        if (mode == HtmlErrorViewMode.Page) {
            val isAmoled = isAmoledTheme()
            val bgColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = false
                        settings.domStorageEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.setSupportMultipleWindows(false)
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.textZoom = 85
                        settings.builtInZoomControls = false
                        settings.displayZoomControls = false
                        webViewClient = WebViewClient()
                        setOnTouchListener { v, event ->
                            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            false
                        }
                        setBackgroundColor(bgColor.toArgb())
                    }
                },
                update = { webView ->
                    if (webView.tag != htmlForPreview) {
                        webView.tag = htmlForPreview
                        webView.loadDataWithBaseURL(
                            "https://localhost/",
                            htmlForPreview,
                            "text/html",
                            "UTF-8",
                            null,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 360.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            SelectionContainer {
                Text(
                    text = text,
                    style = textStyle,
                    color = textColor,
                )
            }
        }
    }
}
private const val MAX_MARKDOWN_RENDER_CHARS = 40_000

/** 超长内容降级预览时最多展示的字符数。 */
private const val MARKDOWN_PREVIEW_CHARS = 8_000
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

    val bubbleContent: @Composable (Modifier) -> Unit = { modifier ->
        Surface(
            shape = if (isUser) {
                RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 4.dp,
                    bottomStart = 12.dp,
                    bottomEnd = 12.dp,
                )
            } else {
                RoundedCornerShape(12.dp)
            },
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
            bubbleContent(Modifier)
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

/**
 * Renders markdown content using mikepenz markdown renderer with code syntax highlighting.
 */
@Composable
private fun MarkdownContent(
    markdown: String,
    textColor: Color,
    isUser: Boolean
) {
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    val requestSaveImage = LocalImageSaveRequest.current
    val coroutineScope = rememberCoroutineScope()
    val normalizedMarkdown = remember(markdown) {
        normalizeTaskListMarkers(preserveRawHtmlPayload(markdown))
    }

    // 超长内容（含病态 markdown，如大量未闭合 HTML 标签）会让 mikepenz 解析器
    // 长时间占用主线程并耗尽堆内存。超过阈值时降级为可滚动纯文本预览，
    // 点击可展开查看全文（纯文本，不走 markdown 解析）。
    if (normalizedMarkdown.length > MAX_MARKDOWN_RENDER_CHARS) {
        LargeMarkdownFallback(
            text = markdown,
            textColor = textColor,
            isUser = isUser,
        )
        return
    }

    val isAmoled = isAmoledTheme()

    // Inline code: keep text styling, but no opaque background so selection remains visible.
    val inlineCodeFg = when {
        isAmoled -> MaterialTheme.colorScheme.onSurface
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.primary
    }
    // Code blocks: distinct background
    val codeBlockBg = when {
        isAmoled -> MaterialTheme.colorScheme.surfaceContainerLow
        isUser -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val codeBlockFg = when {
        isAmoled -> MaterialTheme.colorScheme.onSurface
        isUser -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    // Font size from settings: small=13sp, medium=14sp (default), large=16sp
    val fontSizeSetting = LocalChatFontSize.current
    val lineHeightMultiplier = LocalChatLineHeight.current
    val (bodyFontSize, bodyLineHeight) = when (fontSizeSetting) {
        "small" -> 13.sp to 18.sp * lineHeightMultiplier
        "large" -> 16.sp to 26.sp * lineHeightMultiplier
        else -> 14.sp to 22.sp * lineHeightMultiplier // medium
    }
    val (codeFontSize, codeLineHeight) = when (fontSizeSetting) {
        "small" -> 11.sp to 16.sp * lineHeightMultiplier
        "large" -> 15.sp to 22.sp * lineHeightMultiplier
        else -> 13.sp to 20.sp * lineHeightMultiplier // medium
    }

    // Balanced text style with better line-height for readability
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
        color = textColor,
        fontSize = bodyFontSize,
        lineHeight = bodyLineHeight
    )

    val colors = markdownColor(
        text = textColor,
        codeText = codeBlockFg,
        inlineCodeText = inlineCodeFg,
        linkText = when {
            isAmoled -> MaterialTheme.colorScheme.primary
            isUser -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.primary
        },
        codeBackground = codeBlockBg,
        inlineCodeBackground = Color.Transparent,
        dividerColor = textColor.copy(alpha = 0.32f)
    )

    val typography = markdownTypography(
        h1 = MaterialTheme.typography.titleLarge.copy(
            color = textColor,
            fontWeight = FontWeight.Bold,
            lineHeight = 32.sp
        ),
        h2 = MaterialTheme.typography.titleMedium.copy(
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 28.sp
        ),
        h3 = MaterialTheme.typography.titleSmall.copy(
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp
        ),
        h4 = MaterialTheme.typography.bodyLarge.copy(
            color = textColor,
            fontWeight = FontWeight.SemiBold
        ),
        h5 = MaterialTheme.typography.bodyMedium.copy(
            color = textColor,
            fontWeight = FontWeight.SemiBold
        ),
        h6 = MaterialTheme.typography.bodyMedium.copy(
            color = textColor.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        ),
        text = bodyStyle,
        code = CodeTypography.copy(color = codeBlockFg, fontSize = codeFontSize, lineHeight = codeLineHeight),
        inlineCode = CodeTypography.copy(
            color = inlineCodeFg,
            fontSize = codeFontSize,
            fontWeight = FontWeight.Medium
        ),
        quote = bodyStyle.copy(
            color = textColor.copy(alpha = 0.65f),
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        ),
        paragraph = bodyStyle,
        ordered = bodyStyle,
        bullet = bodyStyle,
        list = bodyStyle,
        link = bodyStyle.copy(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    )

    val components = markdownComponents(
        codeBlock = safeHighlightedCodeBlock,
        codeFence = safeHighlightedCodeFence,
        image = { model ->
            val imageUrl = remember(model.content, model.node) {
                markdownImageUrl(model.content, model.node)
            }
            Box(
                modifier = Modifier.clickable(enabled = imageUrl != null) {
                    previewImageUrl = imageUrl
                },
            ) {
                MarkdownImage(model.content, model.node)
                if (imageUrl != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.58f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f)),
                    ) {
                        IconButton(
                            onClick = { previewImageUrl = imageUrl },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = stringResource(R.string.chat_image),
                                modifier = Modifier.size(21.dp),
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        },
        table = horizontallyScrollableMarkdownTable,
    )
    val clickableImageTransformer = remember {
        object : ImageTransformer by Coil2ImageTransformerImpl {
            @Composable
            override fun transform(link: String): ImageData? {
                val image = Coil2ImageTransformerImpl.transform(link) ?: return null
                return image.copy(
                    modifier = image.modifier.clickable { previewImageUrl = link },
                )
            }
        }
    }

    val chatLinkHandler = LocalChatLinkHandler.current
    val defaultUriHandler = LocalUriHandler.current
    val chatUriHandler = remember(chatLinkHandler, defaultUriHandler) {
        object : UriHandler {
            override fun openUri(uri: String) {
                if (isSameServerUrl(uri, chatLinkHandler.serverBaseUrl)) {
                    chatLinkHandler.openInApp(uri)
                } else {
                    defaultUriHandler.openUri(uri)
                }
            }
        }
    }

    CompositionLocalProvider(LocalUriHandler provides chatUriHandler) {
        SelectionContainer {
            Markdown(
                content = normalizedMarkdown,
                colors = colors,
                typography = typography,
                flavour = ChatMarkdownFlavour,
                annotator = ChatMarkdownAnnotator,
                components = components,
                imageTransformer = clickableImageTransformer,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    previewImageUrl?.let { imageUrl ->
        ImagePreviewDialog(
            imageModel = imageUrl,
            contentDescription = null,
            onDismiss = { previewImageUrl = null },
            onSave = {
                coroutineScope.launch {
                    downloadMarkdownImage(imageUrl)?.let { image ->
                        requestSaveImage(image.bytes, image.mime, image.filename)
                    }
                }
            },
        )
    }
}

/**
 * 超长内容的降级渲染：不走 markdown 解析器，直接展示可滚动、可选择的纯文本。
 * 默认仅预览前 [MARKDOWN_PREVIEW_CHARS] 个字符，点击「查看全文」后展开完整内容，
 * 避免病态输入把主线程拖死或触发 OOM。
 */
@Composable
private fun LargeMarkdownFallback(
    text: String,
    textColor: Color,
    isUser: Boolean,
) {
    val isAmoled = isAmoledTheme()
    var showFullText by remember { mutableStateOf(false) }
    val displayText = if (showFullText) text else text.take(MARKDOWN_PREVIEW_CHARS)
    val previewTruncated = !showFullText && text.length > MARKDOWN_PREVIEW_CHARS

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (previewTruncated) {
            Text(
                text = stringResource(R.string.chat_large_content_notice, MARKDOWN_PREVIEW_CHARS),
                style = MaterialTheme.typography.bodySmall,
                color = if (isUser) textColor.copy(alpha = 0.75f) else MaterialTheme.colorScheme.tertiary,
            )
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
            border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
            tonalElevation = if (isAmoled) 0.dp else 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                SelectionContainer {
                    Text(
                        text = displayText,
                        style = CodeTypography.copy(fontSize = 12.sp),
                        color = textColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = (LocalConfiguration.current.screenHeightDp.dp / 2).coerceAtLeast(200.dp))
                            .verticalScroll(rememberScrollState()),
                    )
                }
                if (text.length > MARKDOWN_PREVIEW_CHARS) {
                    TextButton(
                        onClick = { showFullText = !showFullText },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(
                            text = stringResource(
                                if (showFullText) R.string.chat_collapse else R.string.chat_show_full_text,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private val MarkdownImageUrlRegex = Regex("""!\[[^]]*]\(\s*(?:<([^>]+)>|([^\s)]+))""")

private fun org.intellij.markdown.ast.ASTNode.findMarkdownDescendant(
    type: org.intellij.markdown.IElementType,
): org.intellij.markdown.ast.ASTNode? {
    if (this.type == type) return this
    return children.firstNotNullOfOrNull { it.findMarkdownDescendant(type) }
}

internal fun markdownImageUrl(
    content: String,
    node: org.intellij.markdown.ast.ASTNode,
): String? = node
    .findMarkdownDescendant(MarkdownElementTypes.LINK_DESTINATION)
    ?.getUnescapedTextInNode(content)
    ?.removeSurrounding("<", ">")
    ?.takeIf(String::isNotBlank)

internal fun markdownImageUrl(content: String, startOffset: Int, endOffset: Int): String? {
    val markdownImage = content.substring(
        startIndex = startOffset.coerceIn(0, content.length),
        endIndex = endOffset.coerceIn(startOffset.coerceIn(0, content.length), content.length),
    )
    val match = MarkdownImageUrlRegex.find(markdownImage) ?: return null
    return (match.groupValues[1].ifBlank { match.groupValues[2] }).takeIf(String::isNotBlank)
}

private val HtmlDocumentHintRegex = Regex("(?is)<!doctype\\s+html\\b|<\\s*html\\b")
private val HtmlTagRegex = Regex("(?is)<\\s*/?\\s*[a-z][^>]*>")
private val MarkdownFenceStartRegex = Regex("^ {0,3}(`{3,}|~{3,})")
private val TaskListMarkerRegex = Regex("^(\\s*[-+*]\\s+)\\[([ xX])]([ \\t]+)")
internal val ChatMarkdownFlavour = GFMFlavourDescriptor()

internal fun normalizeTaskListMarkers(markdown: String): String {
    var fenceMarker: Char? = null
    var minimumFenceLength = 0
    return markdown.split('\n').joinToString("\n") { line ->
        val marker = MarkdownFenceStartRegex.find(line)?.groupValues?.get(1)
        if (fenceMarker != null) {
            if (marker != null && marker.first() == fenceMarker && marker.length >= minimumFenceLength) {
                fenceMarker = null
                minimumFenceLength = 0
            }
            line
        } else if (marker != null) {
            fenceMarker = marker.first()
            minimumFenceLength = marker.length
            line
        } else {
            TaskListMarkerRegex.replace(line) { match ->
                val checkbox = if (match.groupValues[2].equals("x", ignoreCase = true)) "\u2611" else "\u2610"
                match.groupValues[1] + checkbox + match.groupValues[3]
            }
        }
    }
}
internal val ChatMarkdownAnnotator = DefaultMarkdownAnnotator { content, node ->
    markdownTokenReplacement(content, node)?.let { replacement ->
        append(replacement)
        true
    } ?: false
}

private val EmailAutolinkRegex = Regex("<[^<>\\s@]+@[^<>\\s@]+>")

internal fun markdownTokenReplacement(content: String, node: org.intellij.markdown.ast.ASTNode): String? {
    val raw = content.substring(node.startOffset, node.endOffset)
    return when {
        node.type == GFMTokenTypes.TILDE && node.parent?.type != GFMElementTypes.STRIKETHROUGH -> raw
        node.type == MarkdownTokenTypes.EMAIL_AUTOLINK -> raw
        node.type == MarkdownTokenTypes.LT && EmailAutolinkRegex.matchAt(content, node.startOffset) != null -> ""
        node.type == MarkdownTokenTypes.GT -> {
            val openingOffset = content.lastIndexOf('<', node.startOffset)
            val match = openingOffset.takeIf { it >= 0 }?.let { EmailAutolinkRegex.matchAt(content, it) }
            if (match?.range?.last == node.startOffset) "" else null
        }
        node.type == GFMTokenTypes.CHECK_BOX -> {
            if (raw.contains('x', ignoreCase = true)) "\u2611" else "\u2610"
        }
        else -> null
    }
}

private fun looksLikeHtmlPayload(text: String): Boolean {
    if (text.isBlank()) return false
    if (HtmlDocumentHintRegex.containsMatchIn(text)) return true
    return HtmlTagRegex.findAll(text).take(12).count() >= 6
}

private fun normalizeHtmlForEmbeddedPreview(html: String): String {
    if (html.isBlank()) return html
    val overrideCss = """
        html, body {
          margin: 0 !important;
          padding: 8px !important;
          min-height: auto !important;
          height: auto !important;
        }
        body {
          display: block !important;
          align-items: flex-start !important;
          justify-content: flex-start !important;
          overflow: auto !important;
        }
        .container {
          align-items: flex-start !important;
          justify-content: flex-start !important;
          height: auto !important;
          min-height: auto !important;
          width: 100% !important;
          margin: 0 !important;
        }
    """.trimIndent()

    val styleBlock = "<style>$overrideCss</style>"
    return if (html.contains("</head>", ignoreCase = true)) {
        html.replaceFirst(Regex("(?i)</head>"), "$styleBlock</head>")
    } else {
        "<head>$styleBlock</head>$html"
    }
}

private fun preserveRawHtmlPayload(markdown: String): String {
    if (markdown.isBlank()) return markdown
    if ("```" in markdown) return markdown

    val looksLikeHtmlDocument = HtmlDocumentHintRegex.containsMatchIn(markdown)
    val htmlTagCount = HtmlTagRegex.findAll(markdown).take(16).count()
    if (!looksLikeHtmlDocument && htmlTagCount < 8) return markdown

    return buildString(markdown.length + 16) {
        append("```text\n")
        append(markdown.trimEnd())
        append("\n```")
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

@Composable
private fun ToolCallCard(tool: Part.Tool) {
    val isAmoled = isAmoledTheme()
    val stateColor = when (tool.state) {
        is ToolState.Pending -> MaterialTheme.colorScheme.outline
        is ToolState.Running -> MaterialTheme.colorScheme.tertiary
        is ToolState.Completed -> MaterialTheme.colorScheme.primary
        is ToolState.Error -> MaterialTheme.colorScheme.error
    }

    // Extract input args for context-specific display
    val input = when (val state = tool.state) {
        is ToolState.Pending -> state.input
        is ToolState.Running -> state.input
        is ToolState.Completed -> state.input
        is ToolState.Error -> state.input
    }

    // Resolve display info based on tool type
    val toolDisplay = resolveToolDisplay(tool.tool, tool.state, input)

    val autoExpand = LocalCollapseTools.current
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current
    var expanded by remember(autoExpand) { mutableStateOf(autoExpand) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
        tonalElevation = if (isAmoled) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { mod ->
                        if (tool.state is ToolState.Completed || tool.state is ToolState.Error) {
                            mod.expandableToolHeader(expanded) {
                                performHaptic(hapticView, hapticOn)
                                expanded = !expanded
                            }
                        } else mod
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when (tool.state) {
                            is ToolState.Running -> Icons.Default.Sync
                            is ToolState.Completed -> toolDisplay.icon
                            is ToolState.Error -> Icons.Default.Error
                            else -> Icons.Default.PlayArrow
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (tool.state is ToolState.Error) stateColor else toolDisplay.iconTint ?: stateColor
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = toolDisplay.title,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (toolDisplay.subtitle != null) {
                            Text(
                                text = toolDisplay.subtitle,
                                style = CodeTypography.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                // Expand indicator for completed/errored tools
                if (tool.state is ToolState.Completed || tool.state is ToolState.Error) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) stringResource(R.string.chat_collapse) else stringResource(R.string.chat_expand),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                } else if (tool.state is ToolState.Running) {
                    PulsingDotsIndicator(
                        modifier = Modifier.padding(end = 2.dp),
                        dotSize = 5.dp,
                        dotSpacing = 3.dp,
                        color = stateColor
                    )
                }
            }

            // Expandable details
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val output = when (val s = tool.state) {
                        is ToolState.Completed -> s.output
                        is ToolState.Error -> s.error
                        else -> ""
                    }
                    if (output.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = toolOutputContainerColor(isAmoled),
                            border = if (isAmoled) BorderStroke(1.dp, stateColor.copy(alpha = 0.6f)) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = output.take(3000),
                                style = CodeTypography.copy(
                                    fontSize = 11.sp,
                                    color = if (isAmoled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier
                                    .padding(8.dp)
                                    .codeHorizontalScroll()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Display info for a tool call, resolved from tool name and input args.
 */
private data class ToolDisplayInfo(
    val title: String,
    val subtitle: String? = null,
    val icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Check,
    val iconTint: Color? = null
)

/**
 * Resolve display info for a tool call based on its type and input arguments.
 * Matches WebUI tool registry behavior with human-readable titles.
 */
@Composable
private fun resolveToolDisplay(
    toolName: String,
    state: ToolState,
    input: Map<String, kotlinx.serialization.json.JsonElement>
): ToolDisplayInfo {
    // Use server-provided title if available
    val serverTitle = when (state) {
        is ToolState.Running -> state.title
        is ToolState.Completed -> state.title
        else -> null
    }

    val filePath = input["filePath"]?.jsonPrimitive?.contentOrNull
        ?: input["path"]?.jsonPrimitive?.contentOrNull
        ?: input["file"]?.jsonPrimitive?.contentOrNull
    val shortPath = filePath?.substringAfterLast('/')

    return when (toolName) {
        "read" -> {
            ToolDisplayInfo(
                title = serverTitle ?: stringResource(R.string.tool_read_file),
                subtitle = shortPath ?: filePath,
                icon = Icons.Default.Description
            )
        }
        "write" -> {
            ToolDisplayInfo(
                title = serverTitle ?: stringResource(R.string.tool_write_file),
                subtitle = shortPath ?: filePath,
                icon = Icons.Default.EditNote
            )
        }
        "edit" -> {
            ToolDisplayInfo(
                title = serverTitle ?: stringResource(R.string.tool_edit_file),
                subtitle = shortPath ?: filePath,
                icon = Icons.Default.Edit
            )
        }
        "bash" -> {
            val command = input["command"]?.jsonPrimitive?.contentOrNull
            val shortCmd = command?.let {
                if (it.length > 60) it.take(57) + "..." else it
            }
            ToolDisplayInfo(
                title = serverTitle ?: stringResource(R.string.tool_terminal),
                subtitle = shortCmd,
                icon = Icons.Default.Terminal
            )
        }
        "glob" -> {
            val pattern = input["pattern"]?.jsonPrimitive?.contentOrNull
            ToolDisplayInfo(
                title = serverTitle ?: stringResource(R.string.tool_find_files),
                subtitle = pattern,
                icon = Icons.Default.FolderOpen
            )
        }
        "grep" -> {
            val pattern = input["pattern"]?.jsonPrimitive?.contentOrNull
            ToolDisplayInfo(
                title = serverTitle ?: stringResource(R.string.tool_search_code),
                subtitle = pattern,
                icon = Icons.Default.Search
            )
        }
        "list", "listDirectory" -> {
            ToolDisplayInfo(
                title = serverTitle ?: stringResource(R.string.tool_list_directory),
                subtitle = filePath,
                icon = Icons.Default.Folder
            )
        }
        "webfetch" -> {
            val url = input["url"]?.jsonPrimitive?.contentOrNull
            val shortUrl = url?.let {
                try { java.net.URI(it).host } catch (_: Exception) { it.take(40) }
            }
            ToolDisplayInfo(
                title = serverTitle ?: stringResource(R.string.tool_fetch_url),
                subtitle = shortUrl,
                icon = Icons.Default.Language
            )
        }
        "task" -> {
            val description = input["description"]?.jsonPrimitive?.contentOrNull
            ToolDisplayInfo(
                title = serverTitle ?: stringResource(R.string.tool_sub_agent),
                subtitle = description,
                icon = Icons.Default.AccountTree
            )
        }
        "apply_patch" -> {
            ToolDisplayInfo(
                title = serverTitle ?: stringResource(R.string.tool_apply_patch),
                subtitle = shortPath,
                icon = Icons.Default.Compare
            )
        }
        else -> {
            ToolDisplayInfo(
                title = serverTitle ?: toolName,
                subtitle = null,
                icon = Icons.Default.Build
            )
        }
    }
}

// ============================================================================
// Tool-specific card renderers (matching WebUI tool registry)
// ============================================================================

/**
 * Extract common tool input values.
 */
private fun extractToolInput(tool: Part.Tool): Map<String, kotlinx.serialization.json.JsonElement> {
    return when (val state = tool.state) {
        is ToolState.Pending -> state.input
        is ToolState.Running -> state.input
        is ToolState.Completed -> state.input
        is ToolState.Error -> state.input
    }
}

private fun extractToolOutput(tool: Part.Tool): String {
    return when (val s = tool.state) {
        is ToolState.Completed -> s.output
        is ToolState.Error -> s.error
        else -> ""
    }
}

@Composable
private fun ApplyPatchToolCard(tool: Part.Tool) {
    val isAmoled = isAmoledTheme()
    val input = extractToolInput(tool)
    val metadata = when (val state = tool.state) {
        is ToolState.Running -> state.metadata
        is ToolState.Completed -> state.metadata
        is ToolState.Error -> state.metadata
        is ToolState.Pending -> null
    }
    val files = metadata?.get("files")?.let { element ->
        runCatching { element.jsonArray.mapNotNull { it.jsonObject } }.getOrDefault(emptyList())
    }.orEmpty()
    val patch = metadata?.get("diff")?.jsonPrimitive?.contentOrNull
        ?: input["patchText"]?.jsonPrimitive?.contentOrNull
        ?: metadata?.get("patch")?.jsonPrimitive?.contentOrNull
        ?: input["patch"]?.jsonPrimitive?.contentOrNull
        ?: extractToolOutput(tool)
    val filePath = input["filePath"]?.jsonPrimitive?.contentOrNull
        ?: input["path"]?.jsonPrimitive?.contentOrNull
        ?: files.firstOrNull()?.get("relativePath")?.jsonPrimitive?.contentOrNull
        ?: files.firstOrNull()?.get("filePath")?.jsonPrimitive?.contentOrNull
    val stats = remember(patch, files) {
        if (files.isNotEmpty()) {
            files.sumOf { it["additions"]?.jsonPrimitive?.intOrNull ?: 0 } to
                files.sumOf { it["deletions"]?.jsonPrimitive?.intOrNull ?: 0 }
        } else {
            countUnifiedPatchChanges(patch)
        }
    }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current
    val autoExpand = LocalCollapseTools.current
    var expanded by rememberSaveable(tool.id, autoExpand) { mutableStateOf(autoExpand) }
    val isRunning = tool.state is ToolState.Running
    val hasContent = patch.isNotBlank()

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
        tonalElevation = if (isAmoled) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let {
                        if (hasContent) it.expandableToolHeader(expanded) {
                            performHaptic(hapticView, hapticOn)
                            expanded = !expanded
                        } else it
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(3.dp))
                Text(
                    text = buildString {
                        append(stringResource(R.string.tool_apply_patch))
                        filePath?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (stats.first > 0 || stats.second > 0) DiffChangesInline(stats.first, stats.second)
                if (isRunning) {
                    Spacer(Modifier.width(4.dp))
                    PulsingDotsIndicator(
                        modifier = Modifier.padding(end = 2.dp),
                        dotSize = 5.dp,
                        dotSpacing = 3.dp,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                } else if (hasContent) {
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(patch))
                            android.widget.Toast.makeText(context, R.string.chat_copied_clipboard, android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(22.dp),
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.chat_copy),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                        )
                    }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded && hasContent,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                UnifiedPatchView(patch)
            }
        }
    }
}

internal fun countUnifiedPatchChanges(patch: String): Pair<Int, Int> {
    var additions = 0
    var deletions = 0
    patch.lineSequence().forEach { line ->
        if (line.startsWith("+") && !line.startsWith("+++")) additions++
        if (line.startsWith("-") && !line.startsWith("---")) deletions++
    }
    return additions to deletions
}

@Composable
private fun UnifiedPatchView(patch: String) {
    val isAmoled = isAmoledTheme()
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = toolOutputContainerColor(isAmoled),
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 3.dp)
            .heightIn(max = (LocalConfiguration.current.screenHeightDp.dp / 2).coerceAtLeast(200.dp)),
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier
                    .codeHorizontalScroll()
                    .verticalScroll(rememberScrollState())
                    .padding(4.dp),
            ) {
                patch.lineSequence().forEach { line ->
                    val added = line.startsWith("+") && !line.startsWith("+++")
                    val removed = line.startsWith("-") && !line.startsWith("---")
                    val header = line.startsWith("@@") || line.startsWith("***") || line.startsWith("---") || line.startsWith("+++")
                    Text(
                        text = line,
                        style = CodeTypography.copy(
                            color = when {
                                added -> StatusConnected
                                removed -> StatusError
                                header -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                when {
                                    added -> StatusConnected.copy(alpha = 0.10f)
                                    removed -> StatusError.copy(alpha = 0.10f)
                                    else -> Color.Transparent
                                },
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Edit tool card — shows file path + diff with red/green colored lines.
 * Like WebUI: trigger = "Edit" + filename + DiffChanges, content = diff view.
 */
@Composable
private fun EditToolCard(tool: Part.Tool) {
    val isAmoled = isAmoledTheme()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current
    val input = extractToolInput(tool)
    val filePath = input["filePath"]?.jsonPrimitive?.contentOrNull ?: ""
    val shortPath = filePath.substringAfterLast('/')
    val dirPath = if (filePath.contains('/')) filePath.substringBeforeLast('/') else ""
    val oldString = input["oldString"]?.jsonPrimitive?.contentOrNull ?: ""
    val newString = input["newString"]?.jsonPrimitive?.contentOrNull ?: ""

    // Try to get filediff from metadata (full file before/after)
    val metadata = when (val s = tool.state) {
        is ToolState.Completed -> s.metadata
        is ToolState.Running -> s.metadata
        else -> null
    }
    val fileDiff = metadata?.get("filediff")?.jsonObject
    val authoritativePatch = fileDiff?.get("patch")?.jsonPrimitive?.contentOrNull
        ?: metadata?.get("diff")?.jsonPrimitive?.contentOrNull
    val filediffBefore = fileDiff?.get("before")?.jsonPrimitive?.contentOrNull
    val filediffAfter = fileDiff?.get("after")?.jsonPrimitive?.contentOrNull

    val diffBefore = filediffBefore ?: oldString
    val diffAfter = filediffAfter ?: newString

    // Compute additions/deletions
    val diffLines = remember(diffBefore, diffAfter) {
        computeSimpleDiff(
            if (diffBefore.isBlank()) emptyList() else diffBefore.lines(),
            if (diffAfter.isBlank()) emptyList() else diffAfter.lines(),
        )
    }
    val additions = fileDiff?.get("additions")?.jsonPrimitive?.intOrNull
        ?: authoritativePatch?.let(::countUnifiedPatchChanges)?.first
        ?: diffLines.count { it.type == DiffLineType.ADDED }
    val deletions = fileDiff?.get("deletions")?.jsonPrimitive?.intOrNull
        ?: authoritativePatch?.let(::countUnifiedPatchChanges)?.second
        ?: diffLines.count { it.type == DiffLineType.REMOVED }

    val autoExpand = LocalCollapseTools.current
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current
    var expanded by remember(autoExpand) { mutableStateOf(autoExpand) }
    val isRunning = tool.state is ToolState.Running
    val isError = tool.state is ToolState.Error
    val hasContent = !authoritativePatch.isNullOrBlank() || diffBefore.isNotBlank() || diffAfter.isNotBlank()

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
        tonalElevation = if (isAmoled) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { mod ->
                        if (hasContent && !isRunning) mod.expandableToolHeader(expanded) {
                            performHaptic(hapticView, hapticOn)
                            expanded = !expanded
                        } else mod
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Default.Error else Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.chat_edit_label),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                        if (shortPath.isNotBlank()) {
                            Text(
                                text = shortPath,
                                style = CodeTypography.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                // Diff stats + expand indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (additions > 0 || deletions > 0) {
                        DiffChangesInline(additions = additions, deletions = deletions)
                    }
                    if (isRunning) {
                        PulsingDotsIndicator(
                            modifier = Modifier.padding(end = 2.dp),
                            dotSize = 5.dp,
                            dotSpacing = 3.dp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    } else if (hasContent) {
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString("Edit: $filePath\n\n${authoritativePatch ?: diffAfter}"))
                                android.widget.Toast.makeText(context, R.string.chat_copied_clipboard, android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(22.dp),
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.chat_copy),
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                            )
                        }
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                        )
                    }
                }
            }

            // Expanded diff view
            AnimatedVisibility(visible = expanded && hasContent) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    if (isError) {
                        val errorText = (tool.state as ToolState.Error).error
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.errorContainer,
                            border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ErrorPayloadContent(
                                text = errorText,
                                textStyle = CodeTypography.copy(
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                                textColor = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    } else {
                        if (!authoritativePatch.isNullOrBlank()) {
                            UnifiedPatchView(authoritativePatch)
                        } else {
                            DiffView(before = diffBefore, after = diffAfter)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Inline diff change counts: +N -N with colors.
 */
@Composable
private fun DiffChangesInline(additions: Int, deletions: Int) {
    val addColor = StatusConnected
    val delColor = StatusError
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (additions > 0) {
            Text(
                text = "+$additions",
                style = CodeTypography.copy(fontSize = 11.sp, color = addColor)
            )
        }
        if (deletions > 0) {
            Text(
                text = "-$deletions",
                style = CodeTypography.copy(fontSize = 11.sp, color = delColor)
            )
        }
    }
}

/**
 * Unified diff view — shows old lines in red, new lines in green.
 * Simple approach: compute line-level diff between before and after.
 */
@Composable
private fun DiffView(before: String, after: String) {
    val isAmoled = isAmoledTheme()
    val addColor = StatusConnected
    val delColor = StatusError
    val addBg = StatusConnected.copy(alpha = 0.1f)
    val delBg = StatusError.copy(alpha = 0.1f)

    // Simple diff: show removed lines, then added lines
    // For a proper diff we'd need a diff library, but line-level comparison works for edit tools
    val beforeLines = if (before.isBlank()) emptyList() else before.lines()
    val afterLines = if (after.isBlank()) emptyList() else after.lines()

    // Compute simple LCS-based diff
    val diffLines = remember(before, after) { computeSimpleDiff(beforeLines, afterLines) }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
    ) {
        Column(
            modifier = Modifier
                .codeHorizontalScroll()
                .verticalScroll(rememberScrollState())
                .padding(4.dp)
        ) {
            for (line in diffLines) {
                val (prefix, text, bgColor, fgColor) = when (line.type) {
                    DiffLineType.REMOVED -> DiffLineStyle("-", line.text, delBg, delColor)
                    DiffLineType.ADDED -> DiffLineStyle("+", line.text, addBg, addColor)
                    DiffLineType.UNCHANGED -> DiffLineStyle(" ", line.text, Color.Transparent, if (isAmoled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor)
                ) {
                    Text(
                        text = "$prefix ",
                        style = CodeTypography.copy(fontSize = 13.sp, color = fgColor),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Text(
                        text = text,
                        style = CodeTypography.copy(fontSize = 13.sp, color = fgColor)
                    )
                }
            }
        }
    }
}

private data class DiffLineStyle(val prefix: String, val text: String, val bgColor: Color, val fgColor: Color)

private enum class DiffLineType { REMOVED, ADDED, UNCHANGED }
private data class DiffLine(val type: DiffLineType, val text: String)

/**
 * Simple diff algorithm: find common prefix/suffix lines, show removed and added lines in between.
 * Not a full LCS but good enough for typical edit tool changes.
 */
private fun computeSimpleDiff(before: List<String>, after: List<String>): List<DiffLine> {
    if (before.isEmpty() && after.isEmpty()) return emptyList()
    if (before.isEmpty()) return after.map { DiffLine(DiffLineType.ADDED, it) }
    if (after.isEmpty()) return before.map { DiffLine(DiffLineType.REMOVED, it) }

    // Find common prefix
    var commonPrefixLen = 0
    while (commonPrefixLen < before.size && commonPrefixLen < after.size &&
        before[commonPrefixLen] == after[commonPrefixLen]) {
        commonPrefixLen++
    }

    // Find common suffix (after prefix)
    var commonSuffixLen = 0
    while (commonSuffixLen < (before.size - commonPrefixLen) &&
        commonSuffixLen < (after.size - commonPrefixLen) &&
        before[before.size - 1 - commonSuffixLen] == after[after.size - 1 - commonSuffixLen]) {
        commonSuffixLen++
    }

    val result = mutableListOf<DiffLine>()

    // Show a few context lines from prefix (max 3)
    val contextLines = 3
    val prefixStart = (commonPrefixLen - contextLines).coerceAtLeast(0)
    for (i in prefixStart until commonPrefixLen) {
        result.add(DiffLine(DiffLineType.UNCHANGED, before[i]))
    }

    // Removed lines (from before, between prefix and suffix)
    for (i in commonPrefixLen until (before.size - commonSuffixLen)) {
        result.add(DiffLine(DiffLineType.REMOVED, before[i]))
    }

    // Added lines (from after, between prefix and suffix)
    for (i in commonPrefixLen until (after.size - commonSuffixLen)) {
        result.add(DiffLine(DiffLineType.ADDED, after[i]))
    }

    // Show a few context lines from suffix (max 3)
    val suffixEnd = commonSuffixLen.coerceAtMost(contextLines)
    for (i in 0 until suffixEnd) {
        result.add(DiffLine(DiffLineType.UNCHANGED, before[before.size - commonSuffixLen + i]))
    }

    return result
}

/**
 * Write tool card — shows file path + code content.
 * Like WebUI: trigger = "Write" + filename, content = code view.
 */
@Composable
private fun WriteToolCard(tool: Part.Tool) {
    val isAmoled = isAmoledTheme()
    val input = extractToolInput(tool)
    val filePath = input["filePath"]?.jsonPrimitive?.contentOrNull
        ?: input["path"]?.jsonPrimitive?.contentOrNull ?: ""
    val shortPath = filePath.substringAfterLast('/')
    val content = input["content"]?.jsonPrimitive?.contentOrNull ?: ""

    val autoExpand = LocalCollapseTools.current
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current
    var expanded by remember(autoExpand) { mutableStateOf(autoExpand) }
    val isRunning = tool.state is ToolState.Running
    val isError = tool.state is ToolState.Error
    val hasContent = content.isNotBlank()

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
        tonalElevation = if (isAmoled) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { mod ->
                        if (hasContent && !isRunning) mod.expandableToolHeader(expanded) {
                            performHaptic(hapticView, hapticOn)
                            expanded = !expanded
                        } else mod
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Default.Error else Icons.Default.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.chat_write_label),
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (shortPath.isNotBlank()) {
                            Text(
                                text = shortPath,
                                style = CodeTypography.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (isRunning) {
                    PulsingDotsIndicator(
                        modifier = Modifier.padding(end = 2.dp),
                        dotSize = 5.dp,
                        dotSpacing = 3.dp,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                } else if (hasContent) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

            AnimatedVisibility(visible = expanded && hasContent) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = toolOutputContainerColor(isAmoled),
                    border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .heightIn(max = 400.dp)
                ) {
                    Text(
                        text = content.take(5000),
                        style = CodeTypography.copy(fontSize = 12.sp, color = if (isAmoled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSecondaryContainer),
                        modifier = Modifier
                            .padding(8.dp)
                            .codeHorizontalScroll()
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

/**
 * Bash tool card — shows $ command + output.
 * Like WebUI: trigger = "Shell" + description, content = code block with command+output.
 */
@Composable
private fun BashToolCard(tool: Part.Tool) {
    val isAmoled = isAmoledTheme()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current
    val input = extractToolInput(tool)
    val command = input["command"]?.jsonPrimitive?.contentOrNull ?: ""
    val output = (tool.state as? ToolState.Running)
        ?.metadata
        ?.get("output")
        ?.jsonPrimitive
        ?.contentOrNull
        ?: extractToolOutput(tool)
    val cleanedOutput = output.replace(Regex("\u001B\\[[0-9;]*[a-zA-Z]"), "")
    val displayText = buildString {
        if (command.isNotBlank()) {
            append("$ $command")
        }
        if (cleanedOutput.isNotBlank()) {
            if (isNotEmpty()) append("\n\n")
            append(cleanedOutput.take(5000))
        }
    }

    val serverTitle = when (val s = tool.state) {
        is ToolState.Running -> s.title
        is ToolState.Completed -> s.title
        else -> null
    }

    val autoExpand = LocalCollapseTools.current
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current
    var expanded by rememberSaveable(tool.id, autoExpand) { mutableStateOf(autoExpand) }
    val isRunning = tool.state is ToolState.Running
    val isError = tool.state is ToolState.Error
    val hasContent = command.isNotBlank() || output.isNotBlank()

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
        tonalElevation = if (isAmoled) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { mod ->
                        if (hasContent) mod.expandableToolHeader(expanded) {
                            performHaptic(hapticView, hapticOn)
                            expanded = !expanded
                        } else mod
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Default.Error else Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = serverTitle ?: stringResource(R.string.tool_shell),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (isRunning) {
                    PulsingDotsIndicator(
                        modifier = Modifier.padding(end = 2.dp),
                        dotSize = 5.dp,
                        dotSpacing = 3.dp,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                } else if (hasContent) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (displayText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(displayText))
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.chat_copied_clipboard),
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.chat_copy),
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                                )
                            }
                        }
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded && hasContent,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = toolOutputContainerColor(isAmoled),
                    border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp)
                        .heightIn(max = (LocalConfiguration.current.screenHeightDp.dp / 2).coerceAtLeast(200.dp))
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = displayText,
                            style = CodeTypography.copy(color = if (isAmoled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSecondaryContainer),
                            modifier = Modifier
                                .padding(4.dp)
                                .codeHorizontalScroll()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Read tool card — shows file path only, no expandable content (like WebUI).
 */
@Composable
private fun ReadToolCard(tool: Part.Tool) {
    val isAmoled = isAmoledTheme()
    val input = extractToolInput(tool)
    val filePath = input["filePath"]?.jsonPrimitive?.contentOrNull
        ?: input["path"]?.jsonPrimitive?.contentOrNull ?: ""
    val shortPath = filePath.substringAfterLast('/')
    val offset = input["offset"]?.jsonPrimitive?.contentOrNull
    val limit = input["limit"]?.jsonPrimitive?.contentOrNull

    val serverTitle = when (val s = tool.state) {
        is ToolState.Running -> s.title
        is ToolState.Completed -> s.title
        else -> null
    }

    val isRunning = tool.state is ToolState.Running
    val isError = tool.state is ToolState.Error
    val output = extractToolOutput(tool)
    val autoExpand = LocalCollapseTools.current
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current
    var expanded by rememberSaveable(tool.id, autoExpand) { mutableStateOf(autoExpand) }
    val hasContent = output.isNotBlank()

    // Build args string like WebUI: [offset=N, limit=N]
    val args = buildList {
        offset?.let { add("offset=$it") }
        limit?.let { add("limit=$it") }
    }.takeIf { it.isNotEmpty() }?.joinToString(", ", "[", "]")

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
        tonalElevation = if (isAmoled) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let {
                        if (hasContent) it.expandableToolHeader(expanded) {
                            performHaptic(hapticView, hapticOn)
                            expanded = !expanded
                        } else it
                    },
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isError) Icons.Default.Error else Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = serverTitle ?: stringResource(R.string.tool_read),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (shortPath.isNotBlank()) {
                            Text(
                                text = shortPath,
                                style = CodeTypography.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (args != null) {
                            Text(
                                text = args,
                                style = CodeTypography.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                maxLines = 1
                            )
                        }
                    }
                }
                if (isRunning) {
                    PulsingDotsIndicator(
                        modifier = Modifier.padding(end = 2.dp),
                        dotSize = 5.dp,
                        dotSpacing = 3.dp,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                } else if (hasContent) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded && hasContent,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = toolOutputContainerColor(isAmoled),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp)
                        .heightIn(max = (LocalConfiguration.current.screenHeightDp.dp / 2).coerceAtLeast(200.dp))
                        .verticalScroll(rememberScrollState()),
                ) {
                    SelectionContainer {
                        Text(
                            text = output,
                            style = CodeTypography.copy(fontSize = 11.sp),
                            modifier = Modifier.padding(4.dp).codeHorizontalScroll(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Search tool card (glob/grep) — shows pattern + expandable output.
 * Like WebUI: trigger = "Glob"/"Grep" + directory + [pattern=...], content = markdown output.
 */
@Composable
private fun SearchToolCard(tool: Part.Tool) {
    val isAmoled = isAmoledTheme()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current
    val input = extractToolInput(tool)
    val pattern = input["pattern"]?.jsonPrimitive?.contentOrNull
    val include = input["include"]?.jsonPrimitive?.contentOrNull
    val dirPath = input["path"]?.jsonPrimitive?.contentOrNull
    val output = extractToolOutput(tool)

    val serverTitle = when (val s = tool.state) {
        is ToolState.Running -> s.title
        is ToolState.Completed -> s.title
        else -> null
    }

    val label = when (tool.tool) {
        "glob" -> serverTitle ?: stringResource(R.string.tool_find_files)
        "grep" -> serverTitle ?: stringResource(R.string.tool_search_code)
        else -> serverTitle ?: tool.tool
    }
    val title = pattern?.takeIf { it.isNotBlank() }?.let {
        "$label · ${if (it.length > 40) it.take(37) + "..." else it}"
    } ?: label

    val autoExpand = LocalCollapseTools.current
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current
    var expanded by rememberSaveable(tool.id, autoExpand) { mutableStateOf(autoExpand) }
    val isRunning = tool.state is ToolState.Running
    val hasOutput = output.isNotBlank()
    val hasContent = hasOutput || pattern != null || !dirPath.isNullOrBlank() || include != null

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
        tonalElevation = if (isAmoled) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { mod ->
                        if (hasContent) mod.expandableToolHeader(expanded) {
                            performHaptic(hapticView, hapticOn)
                            expanded = !expanded
                        } else mod
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (isRunning) {
                    PulsingDotsIndicator(
                        modifier = Modifier.padding(end = 2.dp),
                        dotSize = 5.dp,
                        dotSpacing = 3.dp,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                } else if (hasContent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(if (hasOutput) output else title))
                                android.widget.Toast.makeText(context, R.string.chat_copied_clipboard, android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(22.dp),
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.chat_copy),
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                            )
                        }
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded && hasContent,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(top = 3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (pattern != null || !dirPath.isNullOrBlank() || include != null) {
                        Surface(shape = RoundedCornerShape(4.dp), color = toolOutputContainerColor(isAmoled)) {
                            Text(
                                text = buildList {
                                    pattern?.let { add("pattern: $it") }
                                    dirPath?.takeIf { it.isNotBlank() }?.let { add("path: $it") }
                                    include?.let { add("include: $it") }
                                }.joinToString("\n"),
                                style = CodeTypography.copy(fontSize = 11.sp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    if (hasOutput) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = toolOutputContainerColor(isAmoled),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = (LocalConfiguration.current.screenHeightDp.dp / 2).coerceAtLeast(200.dp))
                                .verticalScroll(rememberScrollState()),
                        ) {
                            SelectionContainer {
                                if (tool.tool == "grep") {
                                    Box(Modifier.padding(4.dp)) {
                                        MarkdownContent(output, MaterialTheme.colorScheme.onSecondaryContainer, false)
                                    }
                                } else {
                                    Text(
                                        text = output,
                                        style = CodeTypography.copy(fontSize = 11.sp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Task (sub-agent) tool card — shows description + child info.
 * Like WebUI: trigger = "Agent (task)" + description, content = child tool list.
 */
@Composable
private fun TaskToolCard(
    tool: Part.Tool,
    onNavigateToChildSession: (String) -> Unit,
) {
    val isAmoled = isAmoledTheme()
    val input = extractToolInput(tool)
    val description = input["description"]?.jsonPrimitive?.contentOrNull
    val subagentType = input["subagent_type"]?.jsonPrimitive?.contentOrNull
        ?.takeIf { it.isNotBlank() }
    val output = extractToolOutput(tool)

    val serverTitle = when (val s = tool.state) {
        is ToolState.Running -> s.title
        is ToolState.Completed -> s.title
        else -> null
    }

    val autoExpand = LocalCollapseTools.current
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current
    var expanded by remember(autoExpand) { mutableStateOf(autoExpand) }
    val isRunning = tool.state is ToolState.Running
    val hasOutput = output.isNotBlank()
    val childSessionId = when (val state = tool.state) {
        is ToolState.Running -> state.metadata?.get("sessionId")?.jsonPrimitive?.contentOrNull
        is ToolState.Completed -> state.metadata?.get("sessionId")?.jsonPrimitive?.contentOrNull
        is ToolState.Error -> state.metadata?.get("sessionId")?.jsonPrimitive?.contentOrNull
        is ToolState.Pending -> null
    }?.takeIf { it.isNotBlank() }

    // Timeline status + duration
    val statusLabel: String
    val statusColor: Color
    val baseDurationText: String?
    when (val s = tool.state) {
        is ToolState.Running -> {
            statusLabel = stringResource(R.string.subagent_status_running)
            statusColor = MaterialTheme.colorScheme.tertiary
            baseDurationText = s.time?.start?.let { start ->
                formatDurationText((System.currentTimeMillis() - start).coerceAtLeast(0))
            }
        }
        is ToolState.Completed -> {
            statusLabel = stringResource(R.string.subagent_status_completed)
            statusColor = MaterialTheme.colorScheme.primary
            baseDurationText = s.time?.let { formatDurationText((it.end - it.start).coerceAtLeast(0)) }
        }
        is ToolState.Error -> {
            statusLabel = stringResource(R.string.subagent_status_failed)
            statusColor = MaterialTheme.colorScheme.error
            baseDurationText = s.time?.let { formatDurationText((it.end - it.start).coerceAtLeast(0)) }
        }
        is ToolState.Pending -> {
            statusLabel = stringResource(R.string.subagent_status_pending)
            statusColor = MaterialTheme.colorScheme.onSurfaceVariant
            baseDurationText = null
        }
    }
    var runningTicks by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1_000)
            runningTicks = System.currentTimeMillis()
        }
    }
    val runningDurationText = (tool.state as? ToolState.Running)?.time?.start?.let { start ->
        formatDurationText((runningTicks - start).coerceAtLeast(0))
    }
    val durationText = if (isRunning) runningDurationText else baseDurationText

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
        tonalElevation = if (isAmoled) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { mod ->
                        when {
                            childSessionId != null -> mod.clickable {
                                performHaptic(hapticView, hapticOn)
                                onNavigateToChildSession(childSessionId)
                            }
                            hasOutput && !isRunning -> mod.clickable {
                                performHaptic(hapticView, hapticOn)
                                expanded = !expanded
                            }
                            else -> mod
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Timeline status dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = description ?: cleanSessionTitle(serverTitle) ?: subagentType
                                ?: stringResource(R.string.tool_sub_agent),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val secondaryLabel = if (description != null) {
                            subagentType ?: cleanSessionTitle(serverTitle)
                        } else {
                            null
                        }
                        if (secondaryLabel != null) {
                            Text(
                                text = secondaryLabel,
                                style = CodeTypography.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                            )
                            if (durationText != null) {
                                Text(
                                    text = durationText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }
                if (isRunning) {
                    PulsingDotsIndicator(
                        modifier = Modifier.padding(end = 2.dp),
                        dotSize = 5.dp,
                        dotSpacing = 3.dp,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                } else if (childSessionId != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                    )
                } else if (hasOutput) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

            AnimatedVisibility(visible = expanded && hasOutput) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = toolOutputContainerColor(isAmoled),
                    border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .heightIn(max = 300.dp)
                ) {
                    Text(
                        text = output.take(5000),
                        style = CodeTypography.copy(fontSize = 12.sp, color = if (isAmoled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f) else MaterialTheme.colorScheme.onSecondaryContainer),
                        modifier = Modifier
                            .padding(8.dp)
                            .codeHorizontalScroll()
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}
/** Formats a millisecond duration as "1.2s" or "850ms" for sub-agent timeline display. */
private fun formatDurationText(durationMs: Long): String {
    return if (durationMs < 1000) {
        "${durationMs}ms"
    } else {
        String.format(Locale.getDefault(), "%.1fs", durationMs / 1000.0)
    }
}

@Composable
private fun TodoListCard(tool: Part.Tool) {
    val isAmoled = isAmoledTheme()
    // Extract todos from metadata first, then fall back to input
    val todos = remember(tool) {
        val source = when (val state = tool.state) {
            is ToolState.Completed -> state.metadata?.get("todos") ?: state.input["todos"]
            is ToolState.Running -> state.metadata?.get("todos") ?: state.input["todos"]
            is ToolState.Pending -> state.input["todos"]
            is ToolState.Error -> state.metadata?.get("todos") ?: state.input["todos"]
        }
        if (source != null) {
            try {
                source.jsonArray.mapNotNull { element ->
                    try {
                        val obj = element.jsonObject
                        val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val status = obj["status"]?.jsonPrimitive?.contentOrNull ?: "pending"
                        val priority = obj["priority"]?.jsonPrimitive?.contentOrNull ?: "medium"
                        TodoItem(content = content, status = status, priority = priority)
                    } catch (_: Exception) { null }
                }
            } catch (_: Exception) { emptyList() }
        } else {
            emptyList()
        }
    }

    if (todos.isEmpty()) {
        // Fallback to generic tool card if we can't parse todos
        ToolCallCard(tool = tool)
        return
    }

    val completedCount = todos.count { it.status == "completed" }
    val totalCount = todos.size
    var expanded by remember { mutableStateOf(true) }
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
        tonalElevation = if (isAmoled) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .expandableToolHeader(expanded) {
                        performHaptic(hapticView, hapticOn)
                        expanded = !expanded
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Checklist,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (completedCount == totalCount) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = stringResource(R.string.chat_tasks_label),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$completedCount/$totalCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) stringResource(R.string.chat_collapse) else stringResource(R.string.chat_expand),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // Real-time progress bar (completed / total)
            LinearProgressIndicator(
                progress = { if (totalCount == 0) 0f else completedCount.toFloat() / totalCount },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(4.dp),
                color = if (completedCount == totalCount) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )

            // Todo items
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (todo in todos) {
                        TodoItemRow(todo = todo)
                    }
                }
            }
        }
    }
}

private data class TodoItem(
    val content: String,
    val status: String,
    val priority: String
)

@Composable
private fun TodoItemRow(todo: TodoItem) {
    val isCompleted = todo.status == "completed"
    val isInProgress = todo.status == "in_progress"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isCompleted,
            onCheckedChange = null,
            modifier = Modifier.size(20.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = if (isInProgress) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
        )
        Text(
            text = todo.content,
            style = MaterialTheme.typography.bodySmall.copy(
                color = if (isCompleted) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StepFinishInfo(step: Part.StepFinish) {
    if (step.tokens != null || step.cost != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            step.tokens?.let { tokens ->
                Text(
                    text = stringResource(R.string.chat_tokens_format, tokens.input, tokens.output),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
            step.cost?.let { cost ->
                Text(
                    text = stringResource(R.string.chat_cost_format, String.format("%.4f", cost)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun PatchCard(patch: Part.Patch) {
    val isAmoled = isAmoledTheme()
    val autoExpand = LocalCollapseTools.current
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current
    var expanded by remember(autoExpand) { mutableStateOf(autoExpand) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
        tonalElevation = if (isAmoled) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .expandableToolHeader(expanded) {
                        performHaptic(hapticView, hapticOn)
                        expanded = !expanded
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (patch.files.size == 1)
                            stringResource(R.string.chat_files_changed, patch.files.size)
                        else
                            stringResource(R.string.chat_files_changed_plural, patch.files.size),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.chat_collapse) else stringResource(R.string.chat_expand),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }

            // Expanded file list
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (filePath in patch.files) {
                        Text(
                            text = filePath.substringAfterLast('/'),
                            style = CodeTypography.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact horizontal row of image thumbnails with tap-to-preview.
 */
@Composable
private fun ImageThumbnailRow(
    imageFiles: List<Part.File>,
) {
    var previewIndex by remember { mutableStateOf(-1) }
    val requestSaveImage = LocalImageSaveRequest.current
    val appCacheDirectory = LocalContext.current.cacheDir

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for ((index, file) in imageFiles.withIndex()) {
            val imageModel = remember(file.url, appCacheDirectory) {
                partFileImageModel(file, appCacheDirectory)
            }

            if (imageModel != null) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = file.filename ?: stringResource(R.string.chat_image),
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { previewIndex = index },
                    contentScale = ContentScale.Crop
                )
            } else {
                // Fallback placeholder for failed decode
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.BrokenImage,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }

    // Fullscreen image preview dialog
    if (previewIndex >= 0 && previewIndex < imageFiles.size) {
        val file = imageFiles[previewIndex]
        val imageBytes = remember(file.url, appCacheDirectory) {
            decodePartFileBytes(file, appCacheDirectory)
        }
        val imageModel = remember(file.url, appCacheDirectory) {
            partFileImageModel(file, appCacheDirectory)
        }

        if (imageModel != null) {
            ImagePreviewDialog(
                imageModel = imageModel,
                contentDescription = file.filename ?: stringResource(R.string.chat_image),
                onDismiss = { previewIndex = -1 },
                onSave = {
                    if (imageBytes != null) {
                        requestSaveImage(imageBytes, file.mime, file.filename)
                    }
                },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ImagePreviewDialog(
    imageModel: Any,
    contentDescription: String?,
    onDismiss: () -> Unit,
    onSave: (() -> Unit)? = null,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(imageModel) {
                        detectTapGestures(
                            onDoubleTap = { tapPosition ->
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2f
                                    offset = Offset(
                                        x = size.width / 2f - tapPosition.x,
                                        y = size.height / 2f - tapPosition.y,
                                    )
                                }
                            },
                        )
                    }
                    .pointerInput(imageModel) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val previousScale = scale
                            val newScale = (previousScale * zoom).coerceIn(1f, 5f)
                            if (newScale == 1f) {
                                scale = 1f
                                offset = Offset.Zero
                                return@detectTransformGestures
                            }

                            val appliedZoom = newScale / previousScale
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val requestedOffset = offset * appliedZoom +
                                (center - centroid) * (appliedZoom - 1f) + pan
                            val maxOffsetX = size.width * (newScale - 1f) / 2f
                            val maxOffsetY = size.height * (newScale - 1f) / 2f
                            offset = Offset(
                                x = requestedOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                y = requestedOffset.y.coerceIn(-maxOffsetY, maxOffsetY),
                            )
                            scale = newScale
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                    contentScale = ContentScale.Fit,
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val actionContainerColor = Color.Black.copy(alpha = 0.58f)
                val actionBorderColor = Color.White.copy(alpha = 0.32f)
                val actionTintColor = Color.White

                if (onSave != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = actionContainerColor,
                        border = BorderStroke(1.dp, actionBorderColor),
                    ) {
                        IconButton(onClick = onSave, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = stringResource(R.string.chat_save_image),
                                tint = actionTintColor,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = actionContainerColor,
                    border = BorderStroke(1.dp, actionBorderColor),
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = actionTintColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileCard(file: Part.File) {
    // Images are handled by ImageThumbnailRow, so FileCard only handles non-image files
    FileCardFallback(file)
}

@Composable
private fun FileCardFallback(file: Part.File) {
    val isAmoled = isAmoledTheme()
    val containerColor = if (isAmoled) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderColor = if (isAmoled) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)
    }
    val contentColor = if (isAmoled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = file.filename
                    ?: file.url?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                    ?: file.mime,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun PermissionCard(
    permission: SseEvent.PermissionAsked,
    position: String,
    onReply: (reply: String, onResult: (Boolean) -> Unit) -> Unit,
) {
    val isAmoled = isAmoledTheme()
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current
    val containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.tertiaryContainer
    val contentColor = if (isAmoled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onTertiaryContainer
    var submitting by remember(permission.sessionId, permission.id) { mutableStateOf(false) }
    var confirmAlways by remember(permission.sessionId, permission.id) { mutableStateOf(false) }

    if (confirmAlways) {
        ChatDialog(onDismiss = { confirmAlways = false }) {
            Text(stringResource(R.string.permission_always_confirm_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.permission_always_confirm_message))
                if (permission.always.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.permission_always_scope, permission.always.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AppSecondaryButton(onClick = { confirmAlways = false }) { Text(stringResource(R.string.cancel)) }
                AppPrimaryButton(onClick = {
                    confirmAlways = false
                    submitting = true
                    onReply("always") { success -> submitting = false }
                }) {
                    Text(stringResource(R.string.permission_allow_always))
                }
            }
        }
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isAmoled) MaterialTheme.colorScheme.tertiary else contentColor
                )
                Text(
                    text = stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor
                )
                Spacer(Modifier.weight(1f))
                Text(position, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.7f))
            }
            Text(
                text = permission.permission,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor
            )
            if (permission.patterns.isNotEmpty()) {
                Text(
                    text = permission.patterns.joinToString(", "),
                    style = CodeTypography.copy(
                        fontSize = 11.sp,
                        color = contentColor.copy(alpha = 0.7f)
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (permission.always.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = contentColor.copy(alpha = 0.6f)
                    )
                    Text(
                        text = stringResource(R.string.permission_always_scope, permission.always.joinToString(", ")),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppSecondaryButton(
                    onClick = {
                        performHaptic(hapticView, hapticOn)
                        submitting = true
                        onReply("reject") { submitting = false }
                    },
                    enabled = !submitting,
                    modifier = Modifier.weight(1f),
                    destructive = true,
                ) {
                    Text(stringResource(R.string.permission_deny), maxLines = 1)
                }
                AppSecondaryButton(
                    onClick = {
                        performHaptic(hapticView, hapticOn)
                        submitting = true
                        onReply("once") { submitting = false }
                    },
                    enabled = !submitting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.permission_allow_once), maxLines = 1)
                }
                AppPrimaryButton(
                    onClick = { performHaptic(hapticView, hapticOn); confirmAlways = true },
                    enabled = !submitting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.permission_allow_always), maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun QuestionCard(
    question: SseEvent.QuestionAsked,
    position: String,
    onSubmit: (answers: List<List<String>>, onResult: (Boolean) -> Unit) -> Unit,
    onReject: (onResult: (Boolean) -> Unit) -> Unit,
) {
    val isAmoled = isAmoledTheme()
    val isSingle = question.questions.size == 1 && question.questions[0].multiple != true

    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current

    // Prevent multiple submissions
    var submitted by remember(question) { mutableStateOf(false) }

    // Track answers per question
    val answersPerQuestion = remember(question) {
        mutableStateListOf<List<String>>().apply {
            repeat(question.questions.size) { add(emptyList()) }
        }
    }

    val containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isAmoled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row — matches PermissionCard style
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    @Suppress("DEPRECATION")
                    Icons.Default.HelpOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = accentColor
                )
                Text(
                    text = stringResource(R.string.chat_question_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor
                )
                Spacer(Modifier.weight(1f))
                Text(position, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.7f))
            }

            // Question sections
            question.questions.forEachIndexed { index, q ->
                if (q.header.isNotBlank()) {
                    Text(
                        text = q.header,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor
                    )
                }
                Text(
                    text = q.question,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f)
                )

                Spacer(Modifier.height(2.dp))

                if (q.multiple) {
                    // ── Multi-select: checkboxes ──
                    val selectedLabels = remember(question, index) { mutableStateListOf<String>() }

                    q.options.forEach { option ->
                        val checked = option.label in selectedLabels
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (checked) accentColor.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .toggleable(
                                    value = checked,
                                    enabled = !submitted,
                                    role = Role.Checkbox,
                                    onValueChange = {
                                        if (it) selectedLabels.add(option.label) else selectedLabels.remove(option.label)
                                        if (index < answersPerQuestion.size) {
                                            answersPerQuestion[index] = selectedLabels.toList()
                                        }
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = accentColor,
                                    uncheckedColor = contentColor.copy(alpha = 0.5f)
                                )
                            )
                            Column {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor
                                )
                                if (option.description.isNotBlank()) {
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = contentColor.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ── Single-select: tappable option rows ──
                    q.options.forEach { option ->
                        val isSelected = index < answersPerQuestion.size && option.label in answersPerQuestion[index]
                        Surface(
                            onClick = {
                                if (!submitted) {
                                    performHaptic(hapticView, hapticOn)
                                    if (isSingle) {
                                        submitted = true
                                        onSubmit(listOf(listOf(option.label))) { submitted = false }
                                    } else {
                                        if (index < answersPerQuestion.size) {
                                            answersPerQuestion[index] = listOf(option.label)
                                        }
                                    }
                                }
                            },
                                enabled = !submitted,
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) accentColor.copy(alpha = 0.12f) else if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                border = if (!isSelected && isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) accentColor else accentColor.copy(alpha = 0.7f)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) accentColor else contentColor
                                    )
                                    if (option.description.isNotBlank()) {
                                        Text(
                                            text = option.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = contentColor.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // "Type your own answer" — inline text field
                if (q.custom != false) {
                    val currentAnswers = if (index < answersPerQuestion.size) answersPerQuestion[index] else emptyList()
                    val customAnswer = currentAnswers.firstOrNull { ans -> q.options.none { it.label == ans } }
                    
                    if (customAnswer != null) {
                        // Show selected custom answer
                         Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.RadioButtonChecked,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = accentColor
                                )
                                Text(
                                    text = customAnswer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = accentColor,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        if (!submitted && index < answersPerQuestion.size) {
                                            answersPerQuestion[index] = emptyList()
                                        }
                                    },
                                    enabled = !submitted,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.chat_clear),
                                        modifier = Modifier.size(16.dp),
                                        tint = accentColor.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    } else {
                        var isEditingCustom by remember(question, index) { mutableStateOf(false) }
                        var customText by remember(question, index) { mutableStateOf("") }

                        if (!isEditingCustom) {
                            Surface(
                                onClick = {
                                    isEditingCustom = true
                                },
                                enabled = !submitted,
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = accentColor.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = stringResource(R.string.question_custom_answer),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accentColor.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = customText,
                                onValueChange = { customText = it },
                                enabled = !submitted,
                                placeholder = {
                                    Text(
                                        stringResource(R.string.chat_type_answer),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodySmall,
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                trailingIcon = {
                                    Row {
                                        IconButton(
                                            onClick = {
                                                val trimmed = customText.trim()
                                                if (trimmed.isNotBlank()) {
                                                    performHaptic(hapticView, hapticOn)
                                                    if (isSingle) {
                                                        submitted = true
                                                        onSubmit(listOf(listOf(trimmed))) { submitted = false }
                                                    } else {
                                                        if (index < answersPerQuestion.size) {
                                                            answersPerQuestion[index] = listOf(trimmed)
                                                        }
                                                        isEditingCustom = false
                                                        customText = "" 
                                                    }
                                                }
                                            },
                                            enabled = customText.isNotBlank() && !submitted
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Send,
                                                contentDescription = stringResource(R.string.question_submit),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(onClick = { isEditingCustom = false; customText = "" }) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(R.string.question_cancel),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Bottom actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(
                    onClick = {
                        performHaptic(hapticView, hapticOn)
                        submitted = true
                        onReject { submitted = false }
                    },
                    enabled = !submitted
                ) {
                    Text(stringResource(R.string.chat_dismiss), style = MaterialTheme.typography.labelMedium)
                }
                if (!isSingle) {
                    AppPrimaryButton(
                        onClick = {
                            performHaptic(hapticView, hapticOn)
                            submitted = true
                            onSubmit(answersPerQuestion.map { it.toList() }) { submitted = false }
                        },
                        enabled = answersPerQuestion.any { it.isNotEmpty() } && !submitted,
                    ) {
                        Text(stringResource(R.string.question_submit), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** 清理子会话/工具的服务器标题：去路径、去常见 agent 前缀与哈希后缀。 */
private fun cleanSessionTitle(title: String?): String? {
    if (title.isNullOrBlank()) return null
    var t = title.trim()
    val lastSlash = t.lastIndexOf('/')
    if (lastSlash >= 0 && lastSlash < t.length - 1) {
        t = t.substring(lastSlash + 1)
    }
    // 去掉 "agent-" / "subagent-" 之类前缀与形如 "-abc12345" 的哈希后缀。
    t = t.replace(Regex("^(agent|subagent|sub_agent)[-_]", RegexOption.IGNORE_CASE), "")
    t = t.replace(Regex("[-_][0-9a-f]{6,32}$", RegexOption.IGNORE_CASE), "")
    return t.ifBlank { null }
}
