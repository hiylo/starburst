/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatContextUsageDialog.kt
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.domain.model.*
import java.util.Locale
import java.text.SimpleDateFormat
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.AppDialog
import org.hiylo.starburst.ui.components.AppSecondaryButton
import org.hiylo.starburst.ui.components.isAmoledTheme

/**
 * Context-usage dialog: the token accounting summary, the per-category breakdown
 * bar and the raw per-message token rows. Extracted verbatim from ChatDialogs.kt.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContextUsageDialog(
    usage: ContextUsageDetails,
    contextWindow: Int,
    messages: List<ChatMessage> = emptyList(),
    estimatedContextTokens: Int = 0,
    onDismiss: () -> Unit,
) {
    val isAmoled = isAmoledTheme()
    // 顶部「已用 X / 窗口」与顶栏/圆环/输入框预算环统一为当前上下文估算
    // （estimatedContextTokens）。usage.currentTotal 是最近一轮模型 tokens 的
    // 口径，留在明细网格里展示即可，避免与估算口径并存造成三个数不一致。
    val used = if (estimatedContextTokens > 0) estimatedContextTokens else usage.currentTotal
    val percentage = if (contextWindow > 0) (used.toDouble() / contextWindow * 100).roundToInt() else 0
    val remaining = (contextWindow - used).coerceAtLeast(0)
    val progressColor = when {
        percentage >= 90 -> MaterialTheme.colorScheme.error
        percentage >= 70 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    var showSystemPrompt by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    AppDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // --- Header ---
            Text(stringResource(R.string.chat_context_details), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = progressColor,
                )
                Text(
                    text = stringResource(
                        R.string.chat_context_used,
                        formatTokenCount(used),
                        formatTokenCount(contextWindow),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { if (contextWindow > 0) (used.toFloat() / contextWindow).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = progressColor,
                trackColor = progressColor.copy(alpha = 0.16f),
            )
            Text(
                text = stringResource(R.string.chat_context_remaining, formatTokenCount(remaining)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // --- Stats Grid ---
            HorizontalDivider()
            ContextStatGrid(
                stats = buildContextStats(usage, contextWindow, percentage, timeFormat),
            )

            // --- Token Breakdown Bar ---
            if (usage.breakdown.isNotEmpty()) {
                HorizontalDivider()
                ContextBreakdownBar(usage.breakdown)
            }

            // --- System Prompt ---
            if (!usage.systemPrompt.isNullOrBlank()) {
                HorizontalDivider()
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSystemPrompt = !showSystemPrompt }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.chat_context_system_prompt_title),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = if (showSystemPrompt) stringResource(R.string.chat_collapse) else stringResource(R.string.chat_expand),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showSystemPrompt) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .padding(vertical = 8.dp),
                            color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                            border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                        ) {
                            Text(
                                text = usage.systemPrompt.orEmpty(),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }

            // --- Raw Messages ---
            if (messages.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.chat_context_raw_messages_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(messages) { msg ->
                        ContextRawMessageRow(msg, timeFormat)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AppSecondaryButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        }
    }
}

@Composable
internal fun buildContextStats(
    usage: ContextUsageDetails,
    contextWindow: Int,
    percentage: Int,
    timeFormat: SimpleDateFormat,
): List<Pair<String, String>> {
    val fmt = { v: Int -> if (v == 0) "—" else formatTokenCount(v) }
    val fmtPct = { "$percentage%" }
    val fmtCost = if (usage.totalCost > 0) String.format(Locale.US, "$%.4f", usage.totalCost) else "—"
    val fmtTime = { t: Long? -> t?.let { timeFormat.format(java.util.Date(it)) } ?: "—" }
    val cacheLabel = if (usage.cacheRead > 0 || usage.cacheWrite > 0) {
        "${fmt(usage.cacheRead)} / ${fmt(usage.cacheWrite)}"
    } else "—"

    return listOf(
        stringResource(R.string.chat_context_stats_session) to (usage.sessionTitle ?: "—"),
        stringResource(R.string.chat_context_stats_messages) to "${usage.userMessages + usage.assistantMessages}",
        stringResource(R.string.chat_context_stats_provider) to (usage.providerLabel ?: "—"),
        stringResource(R.string.chat_context_stats_model) to (usage.modelLabel ?: "—"),
        stringResource(R.string.chat_context_stats_limit) to (if (contextWindow > 0) formatTokenCount(contextWindow) else "—"),
        stringResource(R.string.chat_context_stats_total_tokens) to fmt(usage.currentTotal),
        stringResource(R.string.chat_context_stats_usage) to fmtPct(),
        stringResource(R.string.chat_context_stats_input_tokens) to fmt(usage.input),
        stringResource(R.string.chat_context_stats_output_tokens) to fmt(usage.output),
        stringResource(R.string.chat_context_stats_reasoning_tokens) to fmt(usage.reasoning),
        stringResource(R.string.chat_context_stats_cache_tokens) to cacheLabel,
        stringResource(R.string.chat_context_stats_user_messages) to usage.userMessages.toString(),
        stringResource(R.string.chat_context_stats_assistant_messages) to usage.assistantMessages.toString(),
        stringResource(R.string.chat_context_stats_total_cost) to fmtCost,
        stringResource(R.string.chat_context_stats_created) to fmtTime(usage.sessionCreatedAt),
        stringResource(R.string.chat_context_stats_last_activity) to fmtTime(usage.lastActivityAt),
    )
}

@Composable
private fun ContextStatGrid(stats: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val rowSize = 2
        stats.chunked(rowSize).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.forEach { (label, value) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextBreakdownBar(segments: List<ContextBreakdownSegment>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.chat_context_breakdown_title),
            style = MaterialTheme.typography.labelLarge,
        )
        // Colored bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .height(8.dp),
        ) {
            segments.forEach { segment ->
                val color = breakdownSegmentColor(segment.key)
                Box(
                    modifier = Modifier
                        .weight((segment.percentage / 100.0).toFloat())
                        .fillMaxHeight()
                        .background(color),
                )
            }
        }
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            segments.forEach { segment ->
                val label = when (segment.key) {
                    ContextBreakdownKey.SYSTEM -> stringResource(R.string.chat_context_breakdown_system)
                    ContextBreakdownKey.USER -> stringResource(R.string.chat_context_breakdown_user)
                    ContextBreakdownKey.ASSISTANT -> stringResource(R.string.chat_context_breakdown_assistant)
                    ContextBreakdownKey.TOOL -> stringResource(R.string.chat_context_breakdown_tool)
                    ContextBreakdownKey.OTHER -> stringResource(R.string.chat_context_breakdown_other)
                }
                val color = breakdownSegmentColor(segment.key)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color),
                    )
                    Text(
                        text = "$label ${String.format(Locale.US, "%.1f", segment.percentage)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.chat_context_breakdown_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

/** 分布条分段颜色。 */
private fun breakdownSegmentColor(key: ContextBreakdownKey): Color = when (key) {
    ContextBreakdownKey.SYSTEM -> Color(0xFF569CD6) // blue
    ContextBreakdownKey.USER -> Color(0xFF4EC9B0)   // green
    ContextBreakdownKey.ASSISTANT -> Color(0xFFC586C0) // purple
    ContextBreakdownKey.TOOL -> Color(0xFFCE9178)  // orange
    ContextBreakdownKey.OTHER -> Color(0xFF808080)  // gray
}

@Composable
private fun ContextRawMessageRow(message: ChatMessage, timeFormat: SimpleDateFormat) {
    var expanded by remember { mutableStateOf(false) }
    val msg = message.message
    val role = msg.role
    val id = msg.id
    val time = timeFormat.format(java.util.Date(msg.time.created))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$role • $id",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "${message.parts.size}p",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (expanded) {
        Column(modifier = Modifier.padding(start = 12.dp)) {
            message.parts.forEach { part ->
                when (part) {
                    is Part.Text -> {
                        if (part.text.isNotBlank()) {
                            Text(
                                text = part.text.take(200) + if (part.text.length > 200) "…" else "",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is Part.Reasoning -> {
                        if (part.text.isNotBlank()) {
                            Text(
                                text = "[reasoning] ${part.text.take(100)}${if (part.text.length > 100) "…" else ""}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is Part.Tool -> {
                        Text(
                            text = "[tool] ${part.tool}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    is Part.File -> {
                        Text(
                            text = "[file] ${part.filename ?: part.url ?: part.mime}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun ContextTokenRow(label: String, tokens: Int, raw: Boolean = false, value: String? = null) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: if (raw) tokens.toString() else formatTokenCount(tokens), style = MaterialTheme.typography.bodySmall)
    }
}
