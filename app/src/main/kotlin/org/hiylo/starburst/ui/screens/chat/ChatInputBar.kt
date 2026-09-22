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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.CartoonInkIcon
import org.hiylo.starburst.ui.components.ProviderIcon
import org.hiylo.starburst.ui.components.appPopupBorder
import org.hiylo.starburst.ui.components.appPopupContainerColor
import org.hiylo.starburst.ui.components.isAmoledTheme
import org.hiylo.starburst.ui.theme.StatusError
import org.hiylo.starburst.ui.theme.StatusWarning



private val placeholderHintResIds = listOf(
    R.string.chat_hint_ask,
    R.string.chat_hint_fix,
    R.string.chat_hint_refactor,
    R.string.chat_hint_tests,
    R.string.chat_hint_explain,
    R.string.chat_hint_help,
)

/** 对话式文档生成意图：[type] 为后端 docType（pptx/docx/xlsx），[prompt] 为原始描述文本。 */
internal data class DocumentIntent(val type: String, val prompt: String)

/**
 * 从输入文本识别「生成文档」意图：需**同时**命中「生成动作词」与「文档类型词」才触发，
 * 避免误伤普通消息（如「生成一段代码」不含类型词 → 不触发）。命中返回 [DocumentIntent]，
 * 否则返回 null，由调用方按普通消息发送。
 */
internal fun detectDocumentIntent(text: String): DocumentIntent? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val actionRegex = Regex(
        "生成|制作|创建|写一份|写个|做个|做一个|来个|出个|帮我做|generate|create|make",
        RegexOption.IGNORE_CASE,
    )
    if (!actionRegex.containsMatchIn(trimmed)) return null
    val type = when {
        Regex("ppt|幻灯片|演示文稿|powerpoint", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) -> "pptx"
        Regex("excel|电子表格|表格|xlsx", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) -> "xlsx"
        Regex("word|文档|docx|document|报告|周报|月报", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) -> "docx"
        else -> return null
    }
    return DocumentIntent(type, trimmed)
}

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
    onDocumentGenerateClick: () -> Unit = {},
    onDocumentIntentDetected: (type: String, prompt: String) -> Unit = { _, _ -> },
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
    // 语音模式：输入框区域整体替换为「按住说话」条。点击麦克风按钮进入，键盘按钮退出。
    var voiceMode by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    // 进入语音模式收起软键盘；ASR 模型不可用（voiceEnabled=false）时自动退回文本输入。
    LaunchedEffect(voiceMode) {
        if (voiceMode) keyboardController?.hide()
    }
    LaunchedEffect(voiceEnabled) {
        if (!voiceEnabled) voiceMode = false
    }

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
            ContextBudgetLevel.CRITICAL -> StatusError
            ContextBudgetLevel.WARNING -> StatusWarning
            else -> MaterialTheme.colorScheme.onSurfaceVariant
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
                // Document generation entry — temporarily hidden (2026-09-22).
                // if (!isShellMode) { IconButton(onClick = onDocumentGenerateClick, modifier = Modifier.size(44.dp)) { Icon(...) } }
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

                if (voiceMode) {
                    // 语音模式：输入框区域整体替换为更高的「按住说话」大胶囊（下游 VoiceHoldToTalkCapsule）。
                    VoiceHoldToTalkCapsule(
                        listening = isListening,
                        voiceLevel = voiceLevel,
                        onPress = onMicPress,
                        onRelease = {
                            onMicRelease()
                            voiceMode = false // 松手文字上屏后自动回到文本输入
                        },
                        onCancel = onMicCancel,
                        onSwitchToText = { voiceMode = false },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    // 文本模式：输入框容器内部最右端放一个小麦克风图标，点它进入语音模式。
                    val showInlineMic = !isShellMode && voiceEnabled
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
                                    end = when {
                                        showInlineAttach && showInlineMic -> 96.dp
                                        showInlineAttach || showInlineMic -> 48.dp
                                        else -> 16.dp
                                    },
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
                        if (showInlineAttach || showInlineMic) {
                            Box(
                                modifier = Modifier.matchParentSize(),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    if (showInlineAttach) {
                                        IconButton(
                                            onClick = onAttach,
                                            modifier = Modifier.size(44.dp),
                                        ) {
                                            CartoonInkIcon(
                                                Icons.Default.AttachFile,
                                                contentDescription = stringResource(R.string.chat_attach),
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                            )
                                        }
                                    }
                                    if (showInlineMic) {
                                        // 点麦克风进入语音模式；与行内附件图标并列，麦克风最靠右。
                                        IconButton(
                                            onClick = { voiceMode = true },
                                            modifier = Modifier.size(40.dp),
                                        ) {
                                            CartoonInkIcon(
                                                Icons.Default.Mic,
                                                contentDescription = stringResource(R.string.chat_voice_input),
                                                modifier = Modifier.size(22.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
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
                                    ComposerAction.SEND -> {
                                        val intent = detectDocumentIntent(textFieldValue.text)
                                        if (intent != null) {
                                            onDocumentIntentDetected(intent.type, intent.prompt)
                                        } else {
                                            onSend()
                                        }
                                    }
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
                    if (action == ComposerAction.STOP) {
                        CartoonInkIcon(
                            Icons.Default.Stop,
                            contentDescription = stringResource(R.string.chat_stop),
                            modifier = Modifier.size(14.dp),
                            inkWidth = 0.9.dp,
                            tint = if (isAmoled) {
                                StatusError
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                        )
                    } else if (isSending) {
                        BreathingCircleIndicator(
                            size = 14.dp,
                            color = MaterialTheme.colorScheme.primary
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

/**
 * 语音模式「按住说话」大胶囊：替换输入框占据整条宽度，更高更圆润更抢眼。
 *
 * 空闲时居中显示提示文字，右侧小键盘按钮退出语音模式；按下时轻微缩小（按压反馈）；
 * 录音中（[listening]）主色系高亮 + 音浪动效，并显示「松开 发送」与「上滑 取消」提示。
 * 手势委托给 [Modifier.holdToTalk]：按下 [onPress]、松手 [onRelease]、上滑 [onCancel]。
 *
 * @param listening 是否正在录音
 * @param voiceLevel 录音音量（0..10，与 VoiceListeningBanner 同口径，内部归一化到 0..1）
 */
@Composable
private fun VoiceHoldToTalkCapsule(
    listening: Boolean,
    voiceLevel: Float,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
    onSwitchToText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isAmoled = isAmoledTheme()
    // 按压反馈：按下微缩，松手回弹。
    var held by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (held) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "voiceCapsulePressScale",
    )
    val capsuleShape = RoundedCornerShape(28.dp)
    Row(
        modifier = modifier
            .height(54.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clip(capsuleShape)
            .background(
                when {
                    listening -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    isAmoled -> Color.Black
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
            .then(
                when {
                    listening -> Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        shape = capsuleShape,
                    )
                    isAmoled -> Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                        shape = capsuleShape,
                    )
                    else -> Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        shape = capsuleShape,
                    )
                }
            )
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .holdToTalk(
                    enabled = true,
                    onPress = {
                        held = true
                        onPress()
                    },
                    onRelease = {
                        held = false
                        onRelease()
                    },
                    onCancel = {
                        held = false
                        onCancel()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (listening) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.chat_voice_slide_up_cancel),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        VoiceWaveform(voiceLevel = voiceLevel)
                        Text(
                            text = stringResource(R.string.chat_voice_release_to_send),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.chat_voice_hold_to_talk),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isAmoled) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    },
                )
            }
        }
        // 胶囊内返回键盘小按钮：不受 holdToTalk 手势影响，点它退回文本输入。
        IconButton(
            onClick = onSwitchToText,
            modifier = Modifier.size(40.dp),
        ) {
            CartoonInkIcon(
                Icons.Default.Keyboard,
                contentDescription = stringResource(R.string.chat_voice_switch_to_text),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
            )
        }
    }
}

/**
 * 录音音浪动效：5 根动态高度的竖条，振幅随 [voiceLevel]（0..10）缩放，
 * 并在相位循环动画下保持跳动（voiceLevel 恒为 0 时仍有基础律动）。
 */
@Composable
private fun VoiceWaveform(voiceLevel: Float) {
    val level = (voiceLevel / 10f).coerceIn(0f, 1f).coerceAtLeast(0.2f)
    val barCount = 5
    val minHeight = 10.dp
    val maxHeight = 32.dp
    val wave = rememberInfiniteTransition(label = "voiceWaveform")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(barCount) { index ->
            val phase by wave.animateFloat(
                initialValue = -index.toFloat(),
                targetValue = 5f + (barCount - index).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 700, delayMillis = index * 90),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "voiceWaveformBar$index",
            )
            val barLevel = (phase + 1f).coerceIn(0f, 1f) * level
            val barHeight = minHeight + (maxHeight - minHeight) * barLevel
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
            )
        }
    }
}

/**
 * 「按住说话」手势：按下立即开始录音（[onPress]），松手提交（[onRelease]），
 * 手指上滑取消（[onCancel]）。用于语音模式的按住说话条，按下即讲、无需等待长按时长。
 */
private fun Modifier.holdToTalk(
    enabled: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
): Modifier {
    if (!enabled) return this
    return this.pointerInput(enabled) {
        val cancelPx = with(density) { 44.dp.toPx() }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            onPress()
            var cancelled = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    if (cancelled) onCancel() else onRelease()
                    change?.consume()
                    return@awaitEachGesture
                }
                // 上滑取消：手指移动明显高于语音条。
                if (change.position.y < -cancelPx) {
                    cancelled = true
                } else {
                    change.consume()
                }
            }
        }
    }
}
