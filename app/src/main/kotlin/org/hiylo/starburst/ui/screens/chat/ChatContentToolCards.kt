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
internal fun WriteToolCard(tool: Part.Tool) {
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
internal fun BashToolCard(tool: Part.Tool) {
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
internal fun ReadToolCard(tool: Part.Tool) {
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
internal fun SearchToolCard(tool: Part.Tool) {
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
internal fun TaskToolCard(
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
internal fun formatDurationText(durationMs: Long): String {
    return if (durationMs < 1000) {
        "${durationMs}ms"
    } else {
        String.format(Locale.getDefault(), "%.1fs", durationMs / 1000.0)
    }
}
