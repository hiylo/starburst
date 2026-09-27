/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : WorkbenchPanel.kt
 * Date : 2026/09/23 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.workbench

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.PermissionRequest
import org.hiylo.starburst.data.api.QuestionInfo
import org.hiylo.starburst.domain.model.SessionStatus
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.AppSecondaryButton
import org.hiylo.starburst.ui.screens.chat.MarkdownContent
import org.hiylo.starburst.ui.theme.StatusError
import org.hiylo.starburst.ui.theme.StatusProcessing
import org.hiylo.starburst.ui.theme.StatusWarning

/**
 * 决定面板里的一条最近对话消息（角色标签 + 内容；最近一条 AI 回复用完整 Markdown 渲染，
 * 面板随推送去抖刷新时流式重渲染）。
 */
@Composable
internal fun RecentMessageRow(msg: PanelMessage) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            },
        ) {
            Text(
                text = if (isUser) stringResource(R.string.workbench_panel_me) else stringResource(R.string.workbench_panel_ai),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        if (msg.full && !isUser) {
            Column(modifier = Modifier.weight(1f)) {
                MarkdownContent(
                    markdown = msg.text,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    isUser = false,
                )
                PanelAttachments(msg.attachments, full = true)
            }
        } else {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (msg.text.isNotBlank()) {
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (msg.full) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PanelAttachments(msg.attachments, full = msg.full)
            }
        }
    }
}

/** 一条消息里的非文本部件（图片缩略图 / 工具 / 文件胶囊）。 */
@Composable
internal fun PanelAttachments(attachments: List<PanelAttachment>, full: Boolean) {
    val shown = if (full) attachments else attachments.take(3)
    if (shown.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.forEach { a ->
            when (a.kind) {
                "image" -> if (a.url != null) {
                    AsyncImage(
                        model = a.url,
                        contentDescription = a.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 150.dp, height = 110.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
                else -> {
                    val isTool = a.kind == "tool"
                    // 工具按状态着色（出错红 / 运行蓝），文件中性。
                    val accent = when {
                        isTool && a.detail == "error" -> StatusError
                        isTool && a.detail == "running" -> StatusProcessing
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val stateTxt = when {
                        isTool && a.detail == "error" -> stringResource(R.string.workbench_panel_tool_error)
                        isTool && a.detail == "running" -> stringResource(R.string.workbench_panel_tool_running)
                        else -> ""
                    }
                    val prefix = if (isTool) stringResource(R.string.workbench_panel_tool) else stringResource(R.string.workbench_panel_file)
                    val showDetail = a.detail.isNotBlank() && a.detail !in setOf("pending", "running", "completed", "error") && !isTool
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accent.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                    ) {
                        Text(
                            text = buildString {
                                append(prefix)
                                append(" · ")
                                append(a.label)
                                if (stateTxt.isNotBlank()) { append(" · "); append(stateTxt) }
                                if (showDetail) { append(" · "); append(a.detail) }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 面板里的上下文占用行：已用 token / 窗口上限 + 进度条。 */
@Composable
internal fun ContextUsageLine(tokens: Int, window: Int) {
    if (tokens <= 0) return
    val pct = if (window > 0) ((tokens.toDouble() / window) * 100).roundToInt() else null
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = buildString {
                append(stringResource(R.string.workbench_context_label))
                append(" ")
                append(formatTokens(tokens))
                if (window > 0) {
                    append(" / ")
                    append(formatTokens(window))
                }
                if (pct != null) append(" · $pct%")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (window > 0) {
            LinearProgressIndicator(
                progress = { (tokens.toFloat() / window).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

private fun formatTokens(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1000.0)
    else -> n.toString()
}

/** 待决问题徽标：amber 胶囊 + 问题数，提示该会话等待回答。 */
@Composable
internal fun PendingQuestionBadge(count: Int) {
    Surface(
        shape = CircleShape,
        color = StatusWarning.copy(alpha = 0.18f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Surface(modifier = Modifier.size(5.dp), shape = CircleShape, color = StatusWarning) {}
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = StatusWarning,
            )
        }
    }
}

/** 通用信息横幅：彩色图标 + 文本 + 尾部操作（用于「处理中」/「失败」提示条）。 */
@Composable
internal fun PanelBanner(
    color: Color,
    text: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading?.invoke()
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}

/** 快捷回复模板行：点击填入草稿、长按删除；末尾「＋」把当前草稿存为模板。 */
@Composable
internal fun WorkbenchTemplateRow(
    templates: List<String>,
    enabled: Boolean,
    onInsert: (String) -> Unit,
    onRemove: (String) -> Unit,
    onSave: () -> Unit,
    canSave: Boolean,
) {
    if (templates.isEmpty() && !canSave) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        templates.forEach { t ->
            WorkbenchTemplateChip(
                text = t,
                enabled = enabled,
                onClick = { onInsert(t) },
                onLongClick = { onRemove(t) },
            )
        }
        WorkbenchTemplateChip(
            text = stringResource(R.string.workbench_save_as_template),
            enabled = enabled && canSave,
            leading = {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
            },
            onClick = onSave,
            onLongClick = {},
        )
    }
}

/** 模板小胶囊：点击插入，长按删除；可选前置图标。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WorkbenchTemplateChip(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (leading != null) leading()
            Text(text = text, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/** 会话分组标题（置顶 / 会话）。 */
@Composable
internal fun SectionHeader(text: String, count: Int) {
    Text(
        text = if (count > 0) "$text ($count)" else text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
    )
}
/** 决策面板：AI 最近回复摘要 + 待决问题选项 + 快捷回复输入 + 进入完整会话。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DecisionPanelContent(
    panel: DecisionPanelState,
    sending: Boolean,
    templates: List<String>,
    errorMessage: String?,
    voiceActive: Boolean,
    onToggleVoice: () -> Unit,
    recognizedText: String?,
    onSend: (String) -> Unit,
    onAnswerQuestion: (String, List<List<String>>) -> Unit,
    onReplyPermission: (String, String) -> Unit,
    onToggleAllMessages: () -> Unit,
    onRetry: () -> Unit,
    onAddTemplate: (String) -> Unit,
    onRemoveTemplate: (String) -> Unit,
    onOpenSession: () -> Unit,
) {
    var quickReply by rememberSaveable(panel.sessionId) { mutableStateOf("") }
    val context = LocalContext.current
    // 语音识别结果自动填入快捷回复输入框。
    LaunchedEffect(recognizedText) {
        if (!recognizedText.isNullOrBlank()) {
            quickReply = recognizedText
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 快捷回复模板：点击填入输入框，长按删除；末尾「＋」把当前草稿存为模板。
        WorkbenchTemplateRow(
            templates = templates,
            enabled = !sending,
            onInsert = { quickReply = it },
            onRemove = { text ->
                onRemoveTemplate(text)
                Toast.makeText(context, context.getString(R.string.workbench_saved_template_removed), Toast.LENGTH_SHORT).show()
            },
            onSave = {
                if (quickReply.isNotBlank()) {
                    onAddTemplate(quickReply)
                    Toast.makeText(context, context.getString(R.string.workbench_saved_template), Toast.LENGTH_SHORT).show()
                }
            },
            canSave = quickReply.isNotBlank(),
        )
        // 快捷回复输入框 + 语音 + 发送，置顶便于快速操作。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val submit: () -> Unit = {
                val trimmed = quickReply.trim()
                if (trimmed.isNotEmpty() && !sending) {
                    // 发送后保留草稿，便于连续补充/改写再发。
                    onSend(trimmed)
                }
            }
            BasicTextField(
                value = quickReply,
                onValueChange = { quickReply = it },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                minLines = 1,
                maxLines = 4,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                decorationBox = { innerTextField ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (quickReply.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.workbench_quick_reply_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                        if (quickReply.isNotEmpty()) {
                            IconButton(
                                onClick = { quickReply = "" },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.workbench_clear_draft),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
            )
            IconButton(
                onClick = onToggleVoice,
                enabled = !sending,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = if (voiceActive) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = stringResource(R.string.chat_voice_input),
                    modifier = Modifier.size(22.dp),
                    tint = if (voiceActive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                    },
                )
            }
            IconButton(
                onClick = submit,
                enabled = quickReply.isNotBlank() && !sending,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.chat_send),
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                )
            }
        }

        // 处理中的实时指示：会话仍在生成/重试时置顶展示，内容随推送去抖刷新。
        if (panel.sessionStatus is SessionStatus.Busy || panel.sessionStatus is SessionStatus.Retry) {
            PanelBanner(
                color = StatusProcessing,
                text = stringResource(R.string.workbench_panel_processing),
                leading = {
                    CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = StatusProcessing)
                },
            )
        }

        // 失败摘要：会话出错时展示错误信息 + 一键重试（重发最后一条用户消息）。
        if (errorMessage != null) {
            PanelBanner(
                color = StatusError,
                text = errorMessage,
                leading = {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusError)
                },
                trailing = {
                    TextButton(onClick = onRetry, enabled = !sending) {
                        Text(stringResource(R.string.workbench_retry), color = StatusError)
                    }
                },
            )
        }

        AppPrimaryButton(
            onClick = onOpenSession,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.OpenInFull, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.workbench_open_full_session))
        }

        // 会话标题/路径/状态在卡片顶部已显示，面板内不再重复。
        ContextUsageLine(tokens = panel.contextTokens, window = panel.contextWindow)
        Text(
            text = stringResource(R.string.workbench_decision_ai_reply),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            panel.loading -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.workbench_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            panel.recentMessages.isEmpty() -> {
                Text(
                    text = stringResource(R.string.workbench_decision_no_reply),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                if (panel.showAll) {
                    val all = panel.allMessages
                    if (all == null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text(
                                text = stringResource(R.string.workbench_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        // 完整消息列表：独立内部滚动区（高度受限于外层 LazyColumn 卡片，避免无限高度冲突）。
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            all.forEach { RecentMessageRow(it) }
                        }
                    }
                } else {
                    panel.recentMessages.forEach { msg ->
                        RecentMessageRow(msg)
                    }
                }
                TextButton(onClick = onToggleAllMessages) {
                    Text(
                        text = stringResource(
                            if (panel.showAll) R.string.workbench_view_all_collapse else R.string.workbench_view_all
                        ),
                    )
                }
            }
        }

        if (panel.permissions.isNotEmpty()) {
            Text(
                text = stringResource(R.string.workbench_decision_permissions),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            panel.permissions.forEach { permission ->
                PanelPermissionCard(
                    permission = permission,
                    onReply = { reply -> onReplyPermission(permission.id, reply) },
                )
            }
        }

        if (panel.questions.isNotEmpty()) {
            Text(
                text = stringResource(R.string.workbench_decision_questions),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val requestId = panel.questionRequestId
            if (requestId != null) {
                val single = panel.questions.size == 1 && panel.questions.first().multiple != true
                if (single) {
                    panel.questions.forEach { question ->
                        QuestionOptions(
                            question = question,
                            onSelect = { label -> onAnswerQuestion(requestId, listOf(listOf(label))) },
                        )
                    }
                } else {
                    val answersPerQuestion = remember(panel.sessionId, requestId) {
                        mutableStateListOf<List<String>>().apply {
                            repeat(panel.questions.size) { add(emptyList()) }
                        }
                    }
                    panel.questions.forEachIndexed { index, question ->
                        QuestionOptions(
                            question = question,
                            onSelect = { label ->
                                answersPerQuestion[index] = listOf(label)
                                if (answersPerQuestion.all { it.isNotEmpty() }) {
                                    onAnswerQuestion(requestId, answersPerQuestion.toList())
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 单个待决授权：权限名 + 匹配范围 + 一键拒绝/仅一次/始终允许（面板内直接处理，无需进会话）。 */
@Composable
internal fun PanelPermissionCard(
    permission: PermissionRequest,
    onReply: (String) -> Unit,
) {
    var submitting by rememberSaveable(permission.id) { mutableStateOf(false) }
    val reply: (String) -> Unit = { value ->
        if (!submitting) {
            submitting = true
            onReply(value)
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = StatusWarning.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, StatusWarning.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = StatusWarning,
                )
                Text(
                    text = stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = permission.permission,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (permission.patterns.isNotEmpty()) {
                Text(
                    text = permission.patterns.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppSecondaryButton(
                    onClick = { reply("reject") },
                    enabled = !submitting,
                    modifier = Modifier.weight(1f),
                    destructive = true,
                ) {
                    Text(stringResource(R.string.permission_deny), maxLines = 1)
                }
                AppSecondaryButton(
                    onClick = { reply("once") },
                    enabled = !submitting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.permission_allow_once), maxLines = 1)
                }
                AppPrimaryButton(
                    onClick = { reply("always") },
                    enabled = !submitting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.permission_allow_always), maxLines = 1)
                }
            }
        }
    }
}

/** 单个待决问题：题面 + 选项（点击选项即作为快捷回复发送）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuestionOptions(
    question: QuestionInfo,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val title = question.header.takeIf { it.isNotBlank() } ?: question.question
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (question.question.isNotBlank() && question.question != title) {
            Text(
                text = question.question,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (question.options.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                question.options.forEachIndexed { index, option ->
                    OptionCard(
                        index = index,
                        label = option.label,
                        description = option.description,
                        onSelect = { onSelect(option.label) },
                    )
                }
            }
        }
    }
}

/** 待决问题选项卡片：编号圆点 + 名称/描述 + 箭头，点击即作为快捷回复发送。 */
@Composable
internal fun OptionCard(
    index: Int,
    label: String,
    description: String,
    onSelect: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}