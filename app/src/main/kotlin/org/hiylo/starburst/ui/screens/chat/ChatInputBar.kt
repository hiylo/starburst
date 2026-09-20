/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatInputBar.kt
 * Date : 2026-09-19 00:00:00
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.hiylo.starburst.domain.model.*
import org.hiylo.starburst.data.api.AgentInfo
import org.hiylo.starburst.data.api.CommandInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.CartoonInkIcon
import org.hiylo.starburst.ui.components.ProviderIcon
import org.hiylo.starburst.ui.components.appPopupBorder
import org.hiylo.starburst.ui.components.appPopupContainerColor
import org.hiylo.starburst.ui.components.isAmoledTheme



private val placeholderHintResIds = listOf(
    R.string.chat_hint_ask,
    R.string.chat_hint_fix,
    R.string.chat_hint_refactor,
    R.string.chat_hint_tests,
    R.string.chat_hint_explain,
    R.string.chat_hint_help,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatInputBar(
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
    estimatedContextTokens: Int = 0,
    effectiveContextWindow: Int = 0,
    contextUsage: ContextUsageDetails = ContextUsageDetails(),
    contextMessages: List<ChatMessage> = emptyList(),
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

        // 上下文预算指示器：估算用量 vs 有效窗口，与下方预算文字同源同门槛，>80% 告警色。
        val budgetRatio = contextBudgetRatio(estimatedContextTokens, effectiveContextWindow)
        val budgetColor = when (contextBudgetLevel(budgetRatio)) {
            ContextBudgetLevel.CRITICAL -> MaterialTheme.colorScheme.error
            ContextBudgetLevel.WARNING -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        }
        val budgetPercentage = contextBudgetPercentage(budgetRatio)
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
            if (modelLabel.isNotEmpty() || agents.size > 1 || effectiveContextWindow > 0) {
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
                                    text = displayModelLabel(modelLabel),
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

                        if (effectiveContextWindow > 0) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable { showContextDetails = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    progress = { budgetRatio.toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier.size(27.dp),
                                    color = budgetColor,
                                    trackColor = budgetColor.copy(alpha = 0.16f),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = "$budgetPercentage%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = budgetColor,
                                    maxLines = 1,
                                )
                            }
                        }

                        if (effectiveContextWindow > 0) {
                            Text(
                                text = stringResource(
                                    R.string.sysprompt_context_budget,
                                    formatTokenCount(estimatedContextTokens),
                                    formatTokenCount(effectiveContextWindow),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = budgetColor,
                                maxLines = 1,
                            )
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
                    itemsIndexed(attachments, key = { _, it -> it.uri }) { index, attachment ->
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
                                CartoonInkIcon(
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
                        CartoonInkIcon(
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
                        CartoonInkIcon(
                            Icons.Default.Stop,
                            contentDescription = stringResource(R.string.chat_stop),
                            modifier = Modifier.size(14.dp),
                            inkWidth = 0.9.dp,
                            tint = if (isAmoled) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.88f)
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                        )
                    } else {
                        CartoonInkIcon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (isShellMode) {
                                stringResource(R.string.chat_send_shell)
                            } else {
                                stringResource(R.string.chat_send)
                            },
                            modifier = Modifier.size(18.dp),
                            inkWidth = 1.0.dp,
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
            contextWindow = effectiveContextWindow,
            messages = contextMessages,
            estimatedContextTokens = estimatedContextTokens,
            onDismiss = { showContextDetails = false },
        )
    }
}
