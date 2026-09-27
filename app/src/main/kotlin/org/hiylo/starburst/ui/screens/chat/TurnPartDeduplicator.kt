/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : TurnPartDeduplicator.kt
 * Date : 2026/09/27 02:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.hiylo.starburst.domain.model.Message
import org.hiylo.starburst.domain.model.Part
import org.hiylo.starburst.domain.model.ToolState

/**
 * 回合内 part 去重：修「同一条内容在气泡里显示两遍」（文本 / question / 任务计划）。
 *
 * 背景（数据层与渲染层都缺一道路闸）：
 * - [org.hiylo.starburst.data.sync.EventReducer] 的 `_parts` 以 `messageId` 为外层 key、
 *   `part.id` 为内层去重键（`EventReducerMessageExt.handleMessagePartUpdated`）。同一
 *   partId / callID 的更新若落在两个不同 `messageId` 下（agent 各事件的
 *   `assistantMessageID` 未必一致），会留下两条互相独立的副本。
 * - `ChatMessageBubble.contentGroups` 按「回合内每条 assistant 消息」分组渲染，
 *   全链路没有任何跨消息去重，于是两条副本被各画一次。
 *
 * 作为最后一道闸，按三条规则收敛：
 * 1. **工具 part 按 callID**（空则退化为 part id）：同一调用只保留**状态最完整**的一条。
 *    事件时序是单向的（Pending→Running→Completed/Error），故完整度更高者后到；
 * 2. **非工具 part 按 part id**：同一 id 只保留文本最长的一条；
 * 3. **todowrite 任务计划卡**：一个回合内多次 `todowrite`（计划→更新）只保留最后一张，
 *    避免多张几乎相同的任务卡（与 `suppressRepeatedPatchCards` 同一思路）。
 *
 * 用户消息不参与（其 part 由 pending 乐观渲染与权威渲染二选一，不产生副本）。
 */
internal fun dedupeTurnParts(messages: List<ChatMessage>): List<ChatMessage> {
    // 展平为 (消息序号, 消息内 part 序号, part)，供两趟算法定位。
    val flat = buildList {
        messages.forEachIndexed { messageIndex, chatMessage ->
            chatMessage.parts.forEachIndexed { partIndex, part ->
                add(Triple(messageIndex, partIndex, part))
            }
        }
    }

    // 规则 3：最后一条 todowrite 的全局位置。
    val lastTodoWritePos = flat.lastOrNull { (_, _, part) -> part is Part.Tool && part.tool == "todowrite" }
        ?.let { (messageIndex, partIndex, _) -> messageIndex to partIndex }

    // 规则 1：同 callID 保留状态最完整者；完整度相同时保留后到者（事件单向推进）。
    val bestToolPos = mutableMapOf<String, Triple<Int, Int, Part.Tool>>()
    // 规则 2：同 part id 保留文本最长者；长度相同时保留后到者。
    val bestPartPos = mutableMapOf<String, Triple<Int, Int, Part>>()

    for ((messageIndex, partIndex, part) in flat) {
        val pos = Triple(messageIndex, partIndex, part)
        if (part is Part.Tool) {
            val key = part.callId.ifBlank { part.id }
            if (key.isBlank()) continue
            val current = bestToolPos[key]
            val candidateScore = toolStateCompleteness(part.state)
            val bestScore = current?.third?.let { toolStateCompleteness(it.state) } ?: -1
            if (current == null || candidateScore >= bestScore) {
                bestToolPos[key] = Triple(messageIndex, partIndex, part)
            }
        } else {
            val key = part.id
            if (key.isBlank()) continue
            val current = bestPartPos[key]
            val candidateScore = partTextLength(part)
            val bestScore = current?.third?.let { other -> partTextLength(other) } ?: -1
            if (current == null || candidateScore >= bestScore) bestPartPos[key] = pos
        }
    }

    val keepToolPositions = bestToolPos.values.map { it.first to it.second }.toSet()
    val keepPartPositions = bestPartPos.values.map { it.first to it.second }.toSet()

    return messages.mapIndexed { messageIndex, chatMessage ->
        if (chatMessage.message !is Message.Assistant) {
            chatMessage
        } else {
            val filtered = chatMessage.parts.mapIndexedNotNull { partIndex, part ->
                val pos = messageIndex to partIndex
                when {
                    // 规则 3：非最新一条的 todowrite 丢弃。
                    part is Part.Tool && part.tool == "todowrite" && pos != lastTodoWritePos -> null
                    part is Part.Tool -> part.takeIf { pos in keepToolPositions }
                    else -> part.takeIf { pos in keepPartPositions }
                }
            }
            chatMessage.copy(parts = filtered)
        }
    }
}

/**
 * 工具状态的完成度，用于在同 callID 的多个副本间挑选「最完整」的一条。
 * 数值越大越完整；Error 与 Completed 同为终态，取 Error 更高（携带诊断信息）。
 */
internal fun toolStateCompleteness(state: ToolState): Int = when (state) {
    is ToolState.Pending -> 0
    is ToolState.Running -> 1
    is ToolState.Completed -> 2
    is ToolState.Error -> 3
}

/** part 的可比较文本长度（用于同 id 多副本时保留信息量最大的一条）。 */
private fun partTextLength(part: Part): Int = when (part) {
    is Part.Text -> part.text.length
    is Part.Reasoning -> part.text.length
    else -> 0
}
