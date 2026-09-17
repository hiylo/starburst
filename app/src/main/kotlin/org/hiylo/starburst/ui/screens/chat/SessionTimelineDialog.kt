/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SessionTimelineDialog.kt
 * Date : 2026/09/17 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.hiylo.starburst.R
import org.hiylo.starburst.domain.model.Part
import org.hiylo.starburst.domain.model.PendingInteraction
import org.hiylo.starburst.domain.model.Session
import org.hiylo.starburst.domain.model.SseEvent
import org.hiylo.starburst.domain.model.ToolState
import org.hiylo.starburst.ui.components.AppCardShape
import org.hiylo.starburst.ui.components.AppSecondaryButton
import org.hiylo.starburst.ui.components.appAmoledBorder
import org.hiylo.starburst.ui.components.isAmoledTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 无时间戳条目（当前待决项 / 最新快照）使用的哨兵值，用于排序时把它们放在时间线末尾。 */
private const val UNKNOWN_TIME = Long.MAX_VALUE

/**
 * 时间线条目的统一状态枚举。
 * 不同来源（工具、授权、子代理、todo）都映射到这几个语义状态，便于统一渲染颜色与文案。
 */
internal enum class TimelineStatus {
    /** 运行中。 */
    RUNNING,

    /** 已成功完成。 */
    COMPLETED,

    /** 执行失败。 */
    FAILED,

    /** 尚未开始 / 等待处理。 */
    PENDING,

    /** 仅为已记录事件，结果未知（如历史授权、子代理快照）。 */
    INFO,
}

/**
 * 会话时间线中的单条事件（按时间排序）。
 * 由内存中的现有会话状态（消息/parts、pending 交互、子会话、todo 快照）重建，不发起任何网络请求。
 */
internal sealed interface SessionTimelineEntry {
    /** 稳定唯一 key，供 LazyColumn 复用。 */
    val key: String

    /** 事件时间戳（epoch 毫秒），无时间戳时使用 [UNKNOWN_TIME]。 */
    val timestamp: Long

    /**
     * 工具调用：工具名 + 入参摘要 + 结果状态。
     */
    data class ToolCall(
        val callId: String,
        val tool: String,
        val summary: String,
        val status: TimelineStatus,
        override val timestamp: Long,
    ) : SessionTimelineEntry {
        override val key: String get() = "tool_$callId"
    }

    /**
     * 授权请求：权限名 + 匹配范围 + 当前状态（待决 / 已记录）。
     */
    data class Permission(
        val id: String,
        val permission: String,
        val patterns: List<String>,
        val status: TimelineStatus,
        override val timestamp: Long,
    ) : SessionTimelineEntry {
        override val key: String get() = "perm_$id"
    }

    /**
     * 提问（决策过程中向用户澄清的问题）。
     */
    data class Question(
        val id: String,
        val header: String,
        val status: TimelineStatus,
        override val timestamp: Long,
    ) : SessionTimelineEntry {
        override val key: String get() = "question_$id"
    }

    /**
     * 子代理生成：子会话 / task 工具 / subtask、agent part。
     */
    data class SubAgent(
        val id: String,
        val title: String,
        val agentType: String?,
        val status: TimelineStatus,
        override val timestamp: Long,
    ) : SessionTimelineEntry {
        override val key: String get() = "subagent_$id"
    }

    /**
     * Todo 进度快照：完成数 / 总数 + 明细。
     */
    data class Todo(
        val completed: Int,
        val total: Int,
        val items: List<SseEvent.TodoUpdated.Todo>,
        override val timestamp: Long,
    ) : SessionTimelineEntry {
        override val key: String get() = "todo_$timestamp"
    }
}

/** 从 [Part.Tool] 提取入参 map（各状态共用）。 */
private fun toolInput(part: Part.Tool): Map<String, JsonElement> = when (val state = part.state) {
    is ToolState.Pending -> state.input
    is ToolState.Running -> state.input
    is ToolState.Completed -> state.input
    is ToolState.Error -> state.input
}

/** 把工具入参压缩成一行摘要（取前 3 个键，值截断到 48 字符）。 */
private fun summarizeToolInput(input: Map<String, JsonElement>): String {
    if (input.isEmpty()) return ""
    return input.entries.take(3).joinToString(" · ") { (key, value) ->
        val raw = runCatching { value.jsonPrimitive.content }.getOrNull()
            ?: value.toString()
        val text = raw.replace(Regex("\\s+"), " ").trim()
        "$key: ${if (text.length > 48) text.take(45) + "…" else text}"
    }
}

/** 从 tool 状态解析时间戳，缺省时回退到所属消息创建时间。 */
private fun toolTimestamp(part: Part.Tool, fallback: Long): Long = when (val state = part.state) {
    is ToolState.Running -> state.time?.start ?: fallback
    is ToolState.Completed -> state.time?.start ?: fallback
    is ToolState.Error -> state.time?.start ?: fallback
    is ToolState.Pending -> fallback
}

/** 从 tool 状态解析语义状态。 */
private fun toolStatus(part: Part.Tool): TimelineStatus = when (part.state) {
    is ToolState.Running -> TimelineStatus.RUNNING
    is ToolState.Completed -> TimelineStatus.COMPLETED
    is ToolState.Error -> TimelineStatus.FAILED
    is ToolState.Pending -> TimelineStatus.PENDING
}

/**
 * 由内存中的会话状态重建时间线条目（不发起任何网络请求）。
 *
 * 数据来源（全部来自 [org.hiylo.starburst.data.repository.EventReducer] 维护的内存状态）：
 * - 工具调用：消息 parts 中的 [Part.Tool]（工具名 + 入参 + 结果状态）。
 * - 授权 / 提问：`_pendingInteractions`（仅保留「待答复」项）+ 消息历史中的 [Part.Permission] / [Part.Question]。
 * - 子代理生成：直接子会话（`parentId` 指向当前会话）+ `task` 工具 + [Part.Subtask] / [Part.Agent]。
 * - todo 进度：`_todos` 维护的最新快照（completed / total）。
 *
 * 由于数据模型只保留最新状态（授权答复后即被移除、todo 只留最终快照），
 * 本方法为「尽力重建」，无法完整还原历史；UI 层会明确标注「由当前状态重建」。
 *
 * @param messages 当前会话消息（含 parts）。
 * @param pendingInteractions 待答复的授权 / 提问。
 * @param childSessions 当前会话的直接子会话。
 * @param todos 最新 todo 快照。
 * @return 按时间升序排列的时间线条目。
 */
internal fun buildSessionTimeline(
    messages: List<ChatMessage>,
    pendingInteractions: List<PendingInteraction>,
    childSessions: List<Session>,
    todos: List<SseEvent.TodoUpdated.Todo>,
): List<SessionTimelineEntry> {
    val entries = mutableListOf<SessionTimelineEntry>()

    for (chat in messages.sortedBy { it.message.time.created }) {
        val messageTime = chat.message.time.created
        for (part in chat.parts) {
            when (part) {
                is Part.Tool -> {
                    val status = toolStatus(part)
                    val timestamp = toolTimestamp(part, messageTime)
                    entries += SessionTimelineEntry.ToolCall(
                        callId = part.callId,
                        tool = part.tool,
                        summary = summarizeToolInput(toolInput(part)),
                        status = status,
                        timestamp = timestamp,
                    )
                    if (part.tool == "task") {
                        val input = toolInput(part)
                        val description = input["description"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
                        val subagentType = input["subagent_type"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
                        entries += SessionTimelineEntry.SubAgent(
                            id = part.callId,
                            title = description ?: subagentType ?: part.callId,
                            agentType = subagentType,
                            status = status,
                            timestamp = timestamp,
                        )
                    }
                }
                is Part.Permission -> entries += SessionTimelineEntry.Permission(
                    id = part.id,
                    permission = part.message,
                    patterns = emptyList(),
                    status = TimelineStatus.INFO,
                    timestamp = messageTime,
                )
                is Part.Question -> entries += SessionTimelineEntry.Question(
                    id = part.id,
                    header = part.question,
                    status = TimelineStatus.INFO,
                    timestamp = messageTime,
                )
                is Part.Subtask -> entries += SessionTimelineEntry.SubAgent(
                    id = part.id,
                    title = part.description ?: part.prompt,
                    agentType = part.agent,
                    status = TimelineStatus.INFO,
                    timestamp = messageTime,
                )
                is Part.Agent -> entries += SessionTimelineEntry.SubAgent(
                    id = part.id,
                    title = part.name,
                    agentType = null,
                    status = TimelineStatus.INFO,
                    timestamp = messageTime,
                )
                else -> Unit
            }
        }
    }

    for (pending in pendingInteractions) {
        when (pending) {
            is PendingInteraction.Permission -> entries += SessionTimelineEntry.Permission(
                id = pending.request.id,
                permission = pending.request.permission,
                patterns = pending.request.patterns,
                status = TimelineStatus.PENDING,
                timestamp = UNKNOWN_TIME,
            )
            is PendingInteraction.Question -> {
                val first = pending.request.questions.firstOrNull()
                entries += SessionTimelineEntry.Question(
                    id = pending.request.id,
                    header = first?.header?.takeIf { it.isNotBlank() } ?: first?.question.orEmpty(),
                    status = TimelineStatus.PENDING,
                    timestamp = UNKNOWN_TIME,
                )
            }
        }
    }

    for (child in childSessions) {
        entries += SessionTimelineEntry.SubAgent(
            id = child.id,
            title = child.title ?: child.id,
            agentType = null,
            status = TimelineStatus.COMPLETED,
            timestamp = child.time.created,
        )
    }

    if (todos.isNotEmpty()) {
        entries += SessionTimelineEntry.Todo(
            completed = todos.count { it.status == "completed" },
            total = todos.size,
            items = todos,
            timestamp = UNKNOWN_TIME,
        )
    }

    return entries.sortedBy { it.timestamp }
}

/**
 * 会话时间线对话框：按时间顺序展示 agent 的决策过程
 * （工具调用、授权请求、子代理生成、todo 进度）。
 */
@Composable
internal fun SessionTimelineDialog(
    entries: List<SessionTimelineEntry>,
    onDismiss: () -> Unit,
) {
    ChatDialog(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.timeline_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.timeline_reconstructed_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.timeline_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(entries, key = { it.key }) { entry ->
                        TimelineEntryCard(entry)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AppSecondaryButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@Composable
private fun TimelineEntryCard(entry: SessionTimelineEntry) {
    when (entry) {
        is SessionTimelineEntry.ToolCall -> ToolCallRow(entry)
        is SessionTimelineEntry.Permission -> PermissionRow(entry)
        is SessionTimelineEntry.Question -> QuestionRow(entry)
        is SessionTimelineEntry.SubAgent -> SubAgentRow(entry)
        is SessionTimelineEntry.Todo -> TodoRow(entry)
    }
}

@Composable
private fun statusColor(status: TimelineStatus): Color = when (status) {
    TimelineStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
    TimelineStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    TimelineStatus.FAILED -> MaterialTheme.colorScheme.error
    TimelineStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    TimelineStatus.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun statusLabel(status: TimelineStatus): String = when (status) {
    TimelineStatus.RUNNING -> stringResource(R.string.timeline_status_running)
    TimelineStatus.COMPLETED -> stringResource(R.string.timeline_status_completed)
    TimelineStatus.FAILED -> stringResource(R.string.timeline_status_failed)
    TimelineStatus.PENDING -> stringResource(R.string.timeline_status_pending)
    TimelineStatus.INFO -> stringResource(R.string.timeline_status_recorded)
}

@Composable
private fun TimelineCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String?,
    status: TimelineStatus?,
    timestamp: Long,
    content: (@Composable () -> Unit)? = null,
) {
    val isAmoled = isAmoledTheme()
    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = appAmoledBorder(0.65f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = iconTint,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (status != null) {
                    StatusChip(status)
                }
            }
            if (timestamp != UNKNOWN_TIME && timestamp > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatTimelineTime(timestamp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            if (content != null) {
                Spacer(Modifier.height(6.dp))
                content()
            }
        }
    }
}

@Composable
private fun StatusChip(status: TimelineStatus) {
    val color = statusColor(status)
    Text(
        text = statusLabel(status),
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

@Composable
private fun ToolCallRow(entry: SessionTimelineEntry.ToolCall) {
    TimelineCard(
        icon = Icons.Default.Build,
        iconTint = statusColor(entry.status),
        title = entry.tool,
        subtitle = entry.summary,
        status = entry.status,
        timestamp = entry.timestamp,
    )
}

@Composable
private fun PermissionRow(entry: SessionTimelineEntry.Permission) {
    TimelineCard(
        icon = Icons.Default.Security,
        iconTint = when (entry.status) {
            TimelineStatus.PENDING -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.primary
        },
        title = entry.permission,
        subtitle = entry.patterns.takeIf { it.isNotEmpty() }?.joinToString(", "),
        status = entry.status,
        timestamp = entry.timestamp,
    )
}

@Composable
private fun QuestionRow(entry: SessionTimelineEntry.Question) {
    TimelineCard(
        icon = Icons.Default.HelpOutline,
        iconTint = MaterialTheme.colorScheme.primary,
        title = entry.header,
        subtitle = null,
        status = entry.status,
        timestamp = entry.timestamp,
    )
}

@Composable
private fun SubAgentRow(entry: SessionTimelineEntry.SubAgent) {
    TimelineCard(
        icon = Icons.Default.AccountTree,
        iconTint = statusColor(entry.status),
        title = entry.title,
        subtitle = entry.agentType?.takeIf { it.isNotBlank() },
        status = entry.status,
        timestamp = entry.timestamp,
    )
}

@Composable
private fun TodoRow(entry: SessionTimelineEntry.Todo) {
    var expanded by remember { mutableStateOf(true) }
    val completedColor = MaterialTheme.colorScheme.primary
    val iconTint = if (entry.completed == entry.total) completedColor else MaterialTheme.colorScheme.tertiary
    TimelineCard(
        icon = Icons.Default.Checklist,
        iconTint = iconTint,
        title = stringResource(R.string.timeline_todo_title),
        subtitle = stringResource(R.string.timeline_todo_progress, entry.completed, entry.total),
        status = null,
        timestamp = entry.timestamp,
    ) {
        LinearProgressIndicator(
            progress = { if (entry.total == 0) 0f else entry.completed.toFloat() / entry.total },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = if (entry.completed == entry.total) completedColor else MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expanded) stringResource(R.string.chat_collapse) else stringResource(R.string.chat_expand),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (item in entry.items) {
                    TimelineTodoItem(item)
                }
            }
        }
    }
}

@Composable
private fun TimelineTodoItem(item: SseEvent.TodoUpdated.Todo) {
    val isCompleted = item.status == "completed"
    val isInProgress = item.status == "in_progress"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
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
                },
            ),
        )
        Text(
            text = item.content,
            style = MaterialTheme.typography.bodySmall.copy(
                color = if (isCompleted) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

private fun formatTimelineTime(millis: Long): String {
    val safe = if (millis < 10_000_000_000L) millis * 1000 else millis
    return SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date(safe))
}
