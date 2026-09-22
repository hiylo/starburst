/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : RagContextChip.kt
 * Date : 2026/09/22 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.isAmoledTheme

/** 后端 RAG-in-Prompt 注入块的起始标记。 */
internal const val RAG_CONTEXT_START_MARKER = "[RAG_CONTEXT_START]"

/** 后端 RAG-in-Prompt 注入块的结束标记。 */
internal const val RAG_CONTEXT_END_MARKER = "[RAG_CONTEXT_END]"

/** RAG 引用片段前缀（如 [来源1]）。 */
private const val RAG_CONTEXT_SOURCE_PREFIX = "[来源"

/** 判断一段文本是否为后端注入的 RAG 上下文块（只影响渲染，不动数据）。 */
internal fun isRagContextBlock(text: String): Boolean =
    text.startsWith(RAG_CONTEXT_START_MARKER) && text.contains(RAG_CONTEXT_END_MARKER)

/** 统计 RAG 上下文块中 [来源N] 引用片段的数量。 */
internal fun countRagContextSources(text: String): Int {
    var count = 0
    var index = 0
    while (true) {
        index = text.indexOf(RAG_CONTEXT_SOURCE_PREFIX, index)
        if (index < 0) break
        count++
        index += RAG_CONTEXT_SOURCE_PREFIX.length
    }
    return count
}

/** 去掉 RAG 上下文块的包装标记，仅保留中间的参考资料正文（用于展开预览）。 */
internal fun stripRagContextMarkers(text: String): String {
    val bodyStart = if (text.startsWith(RAG_CONTEXT_START_MARKER)) RAG_CONTEXT_START_MARKER.length else 0
    val endIndex = text.indexOf(RAG_CONTEXT_END_MARKER, bodyStart)
    val bodyEnd = if (endIndex >= 0) endIndex else text.length
    return text.substring(bodyStart, bodyEnd).trim()
}

/**
 * 用户消息中被注入的 RAG 上下文（[RAG_CONTEXT_START]…[RAG_CONTEXT_END]）的紧凑展示：
 * 折叠时仅显示一行小纸条（如「已带入知识库资料（N 条）」），点击可展开查看完整原文。
 * 只影响渲染，原始文本仍完整保存在 Part.Text 中。
 */
@Composable
internal fun RagContextChip(
    text: String,
    textColor: Color,
    isUser: Boolean = false,
) {
    val isAmoled = isAmoledTheme()
    var expanded by remember(text) { mutableStateOf(false) }
    val sourceCount = remember(text) { countRagContextSources(text) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            shape = RoundedCornerShape(50),
            color = if (isAmoled) {
                Color.Black
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            border = BorderStroke(
                1.dp,
                if (isAmoled) {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
                } else {
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                },
            ),
            tonalElevation = if (isAmoled) 0.dp else 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .expandableToolHeader(expanded, onClick = { expanded = !expanded })
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = stringResource(R.string.chat_rag_context_attached, sourceCount),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.chat_collapse else R.string.chat_expand),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            MarkdownContent(
                markdown = stripRagContextMarkers(text),
                textColor = textColor,
                isUser = isUser,
            )
        }
    }
}
