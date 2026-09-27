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
 * 用户消息不走本函数（见 [dedupeUserMessageParts]）。
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

/**
 * 折叠单条**用户**消息里内容重复的 part：修「自己发的话在气泡里显示两遍」。
 *
 * 同一个用户 part 会经三条路径进入本地状态，而三者 id 互不相同 ——
 * `handleMessagePartUpdated` 只按 part id 归并，跨 id 的副本一律放过：
 *
 * | 来源 | part id |
 * |---|---|
 * | SSE `message.part.updated`（`handleNextPrompted`） | `msg_x-prompt` |
 * | V2 `message.updated` 解析（content 空、用顶层 text 合成） | `msg_x-text` |
 * | 发送时的乐观本地 part（`PendingPromptRecord.toLocalParts`） | `msg_x-local-0` |
 *
 * 后端只存一份，App 却按三个 id 各存一份，于是同一条消息的文本被渲染 2~3 遍
 * （`groupChatTurns` 每条用户消息独立成 turn，turn 内不做去重）。
 *
 * 只对用户消息按「内容相同即重复」折叠：用户消息的每个 part 都是一次独立的用户输入，
 * 不存在「模型故意把同一句话输出两遍」的情形，故按文本内容去重是安全的。
 * 助手消息**不能**这么判——重复文本在助手输出里是合法内容，故助手消息仍走
 * [dedupeTurnParts] 的 callID / part id 归并。
 */
internal fun dedupeUserMessageParts(parts: List<Part>): List<Part> {
    if (parts.size < 2) return parts
    val seenIds = HashSet<String>(parts.size)
    val seenTexts = HashSet<String>()
    val result = ArrayList<Part>(parts.size)
    for (part in parts) {
        if (!seenIds.add(part.id)) continue
        if (part is Part.Text && !seenTexts.add(part.text)) continue
        result += part
    }
    return result
}
