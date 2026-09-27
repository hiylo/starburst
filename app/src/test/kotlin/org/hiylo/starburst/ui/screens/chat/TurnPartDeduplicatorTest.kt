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
}
