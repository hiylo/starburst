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
internal fun ToolCallCard(tool: Part.Tool) {
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
internal fun extractToolInput(tool: Part.Tool): Map<String, kotlinx.serialization.json.JsonElement> {
    return when (val state = tool.state) {
        is ToolState.Pending -> state.input
        is ToolState.Running -> state.input
        is ToolState.Completed -> state.input
        is ToolState.Error -> state.input
    }
}

internal fun extractToolOutput(tool: Part.Tool): String {
    return when (val s = tool.state) {
        is ToolState.Completed -> s.output
        is ToolState.Error -> s.error
        else -> ""
    }
}

@Composable
internal fun ApplyPatchToolCard(tool: Part.Tool) {
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
internal fun EditToolCard(tool: Part.Tool) {
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

internal data class DiffLineStyle(val prefix: String, val text: String, val bgColor: Color, val fgColor: Color)

internal enum class DiffLineType { REMOVED, ADDED, UNCHANGED }
internal data class DiffLine(val type: DiffLineType, val text: String)

/**
 * Simple diff algorithm: find common prefix/suffix lines, show removed and added lines in between.
 * Not a full LCS but good enough for typical edit tool changes.
 */
internal fun computeSimpleDiff(before: List<String>, after: List<String>): List<DiffLine> {
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
