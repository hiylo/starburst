/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : TurnPartDeduplicatorTest.kt
 * Date : 2026/09/27 02:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.hiylo.starburst.domain.model.Message
import org.hiylo.starburst.domain.model.Part
import org.hiylo.starburst.domain.model.TimeInfo
import org.hiylo.starburst.domain.model.ToolState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 回归「同一条内容在气泡里显示两遍」。
 *
 * 真实成因：EventReducer 的 `_parts` 以 `messageId` 为外层 key、`part.id` 为内层去重键，
 * 同一 partId / callID 的更新若落在两个不同 `messageId` 下会留下两条独立副本；
 * 而 `ChatMessageBubble.contentGroups` 按消息分组渲染，全链路无跨消息去重。
 */
class TurnPartDeduplicatorTest {

    private fun assistantMsg(id: String, created: Long, parts: List<Part>) = ChatMessage(
        message = Message.Assistant(
            id = id,
            sessionId = "ses_1",
            time = TimeInfo(created = created),
        ),
        parts = parts,
    )

    private fun textPart(id: String, messageId: String, text: String) =
        Part.Text(id, "ses_1", messageId, text = text)

    private fun toolPart(
        id: String,
        messageId: String,
        callId: String,
        tool: String,
        state: ToolState,
    ) = Part.Tool(id = id, sessionId = "ses_1", messageId = messageId, callId = callId, tool = tool, state = state)

    @Test
    fun samePartIdAcrossTwoMessages_keepsLongestTextOnly() {
        val messages = listOf(
            assistantMsg("msg_1", 1L, listOf(textPart("prt_1", "msg_1", "he"))),
            assistantMsg("msg_2", 2L, listOf(textPart("prt_1", "msg_2", "hello world"))),
        )

        val result = dedupeTurnParts(messages)

        val allTexts = result.flatMap { it.parts }.filterIsInstance<Part.Text>()
        assertEquals(1, allTexts.size)
        assertEquals("hello world", allTexts.single().text)
    }

    @Test
    fun sameCallIdAcrossTwoMessages_keepsMostCompleteState() {
        val messages = listOf(
            assistantMsg("msg_1", 1L, listOf(
                toolPart("prt_a", "msg_1", "call_x", "edit", ToolState.Running()),
            )),
            assistantMsg("msg_2", 2L, listOf(
                toolPart("prt_b", "msg_2", "call_x", "edit", ToolState.Completed(result = "done")),
            )),
        )

        val result = dedupeTurnParts(messages)

        val tools = result.flatMap { it.parts }.filterIsInstance<Part.Tool>()
        assertEquals(1, tools.size)
        assertEquals(ToolState.Completed(result = "done"), tools.single().state)
    }

    @Test
    fun sameCallId_errorBeatsCompletedAsMoreComplete() {
        val messages = listOf(
            assistantMsg("msg_1", 1L, listOf(
                toolPart("prt_a", "msg_1", "call_y", "bash", ToolState.Completed(result = "out")),
            )),
            assistantMsg("msg_2", 2L, listOf(
                toolPart("prt_b", "msg_2", "call_y", "bash", ToolState.Error(error = "boom")),
            )),
        )

        val tools = dedupeTurnParts(messages).flatMap { it.parts }.filterIsInstance<Part.Tool>()

        assertEquals(1, tools.size)
        assertEquals("boom", (tools.single().state as ToolState.Error).error)
    }

    @Test
    fun repeatedTodoWrite_keepsOnlyLastPlanCard() {
        val messages = listOf(
            assistantMsg("msg_1", 1L, listOf(
                toolPart("prt_t1", "msg_1", "call_1", "todowrite", ToolState.Completed(result = "plan v1")),
                textPart("prt_x", "msg_1", "开始改代码"),
            )),
            assistantMsg("msg_2", 2L, listOf(
                toolPart("prt_t2", "msg_2", "call_2", "todowrite", ToolState.Completed(result = "plan v2")),
            )),
        )

        val tools = dedupeTurnParts(messages).flatMap { it.parts }.filterIsInstance<Part.Tool>()

        assertEquals(1, tools.size)
        assertEquals("plan v2", (tools.single().state as ToolState.Completed).output)
    }

    @Test
    fun distinctCallIds_allPreserved() {
        val messages = listOf(
            assistantMsg("msg_1", 1L, listOf(
                toolPart("prt_a", "msg_1", "call_1", "read", ToolState.Completed(result = "a")),
                toolPart("prt_b", "msg_1", "call_2", "grep", ToolState.Completed(result = "b")),
            )),
        )

        val tools = dedupeTurnParts(messages).flatMap { it.parts }.filterIsInstance<Part.Tool>()

        assertEquals(2, tools.size)
    }

    @Test
    fun userMessagesUntouched() {
        val userMessage = ChatMessage(
            message = Message.User(id = "msg_u", sessionId = "ses_1", time = TimeInfo(1L)),
            parts = listOf(textPart("prt_1", "msg_u", "hello")),
        )
        assertEquals(1, dedupeTurnParts(listOf(userMessage)).single().parts.size)
    }

    /**
     * 复现真机现象：手机发一条消息，气泡里同一句话显示两遍。
     * 根因是同一个用户 part 落了三个不同 id 的副本（见 dedupeUserMessageParts 的表）。
     */
    @Test
    fun `user text part from three id shapes collapses to one`() {
        val text = "用 edit 工具把 difftest.txt 里的 line2 改成 line2-CHANGED，只改这一行。"
        val parts = listOf(
            textPart("msg_1-prompt", "msg_1", text),
            textPart("msg_1-text", "msg_1", text),
            textPart("msg_1-local-0", "msg_1", text),
        )

        val result = dedupeUserMessageParts(parts)

        assertEquals(1, result.size)
        assertEquals(text, (result[0] as Part.Text).text)
    }

    /** 用户一次输入多段文本是合法的（换行分段发送），内容不同不能被折叠掉。 */
    @Test
    fun `distinct user texts are preserved`() {
        val parts = listOf(
            textPart("msg_2-prompt", "msg_2", "第一段"),
            textPart("msg_2-text", "msg_2", "第二段"),
            textPart("msg_2-local-0", "msg_2", "第一段"),
        )

        val result = dedupeUserMessageParts(parts)

        assertEquals(listOf("第一段", "第二段"), result.map { (it as Part.Text).text })
    }

    /** 助手消息里重复的文本是合法内容，不能被内容去重误伤。 */
    @Test
    fun `assistant repeated text is not collapsed by content`() {
        val messages = listOf(
            assistantMsg("asst_1", 1L, listOf(textPart("p1", "asst_1", "好的"), textPart("p2", "asst_1", "好的"))),
        )

        val result = dedupeTurnParts(messages)

        assertEquals(2, result[0].parts.size)
    }
}