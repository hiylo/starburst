/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatTimeline.kt
 * Date : 2026/09/14 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.domain.model.*
import org.hiylo.starburst.R


internal sealed interface ChatTimelineEntry {
    val key: String

    data class DateDivider(val dayStartMillis: Long) : ChatTimelineEntry {
        override val key: String get() = "day_$dayStartMillis"
    }

    data class Turn(val turn: ChatTurn) : ChatTimelineEntry {
        override val key: String get() = turn.key
    }
}

private fun dayStartEpochMillis(created: Long): Long {
    val millis = if (created < 10_000_000_000L) created * 1000 else created
    val zone = java.time.ZoneId.systemDefault()
    val day = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    return day.atStartOfDay(zone).toInstant().toEpochMilli()
}

internal fun buildChatTimeline(messages: List<ChatMessage>): List<ChatTimelineEntry> {
    val turns = groupChatTurns(messages)
    val entries = mutableListOf<ChatTimelineEntry>()
    var lastDay: Long? = null
    turns.forEach { turn ->
        val created = turn.messages.first().message.time.created
        val dayStart = dayStartEpochMillis(created)
        if (lastDay == null || dayStart != lastDay) {
            entries += ChatTimelineEntry.DateDivider(dayStart)
            lastDay = dayStart
        }
        entries += ChatTimelineEntry.Turn(turn)
    }
    return entries
}

@Composable
internal fun DateDividerRow(dayStartMillis: Long) {
    val context = LocalContext.current
    val zone = java.time.ZoneId.systemDefault()
    val day = java.time.Instant.ofEpochMilli(dayStartMillis).atZone(zone).toLocalDate()
    val today = java.time.LocalDate.now(zone)
    val label = when (day) {
        today -> context.getString(R.string.chat_today)
        today.minusDays(1) -> context.getString(R.string.chat_yesterday)
        else -> context.getString(R.string.chat_earlier)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 32.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        )
    }
}

internal fun isBubbleRenderablePart(part: Part): Boolean {
    return when (part) {
        is Part.Text,
        is Part.Reasoning,
        is Part.Patch,
        is Part.File,
        is Part.Permission,
        is Part.Question,
        is Part.Abort,
        is Part.Retry,
        is Part.Tool -> true
        else -> false
    }
}
