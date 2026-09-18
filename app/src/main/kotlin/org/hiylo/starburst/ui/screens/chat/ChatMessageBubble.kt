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
import android.net.Uri
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
                        settings.blockNetworkLoads = true
                        settings.blockNetworkImage = true
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
internal fun MarkdownContent(
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
                    return
                }
                val scheme = runCatching { Uri.parse(uri).scheme }.getOrNull()?.lowercase()
                if (scheme == "http" || scheme == "https") {
                    defaultUriHandler.openUri(uri)
                }
                // 其它 scheme（intent:/tel:/自定义）一律忽略，避免不可信内容触发系统能力。
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
