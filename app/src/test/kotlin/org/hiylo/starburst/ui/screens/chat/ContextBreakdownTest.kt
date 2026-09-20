/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ContextBreakdownTest.kt
 * Date : 2026/09/20 10:30:00
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
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlinx.serialization.json.JsonPrimitive

class ContextBreakdownTest {

    private fun user(id: String, text: String) = ChatMessage(
        message = Message.User(id, "session", time = TimeInfo(1)),
        parts = listOf(
            Part.Text(id = "p-${id}", sessionId = "session", messageId = id, text = text),
        ),
    )

    private fun assistant(id: String, text: String) = ChatMessage(
        message = Message.Assistant(id, "session", time = TimeInfo(2), parentId = "root"),
        parts = listOf(
            Part.Text(id = "p-${id}", sessionId = "session", messageId = id, text = text),
        ),
    )

    private fun assistantWithTool(output: String, inputEntries: Int) = ChatMessage(
        message = Message.Assistant("a-tool", "session", time = TimeInfo(2), parentId = "root"),
        parts = listOf(
            Part.Tool(
                id = "p-tool",
                sessionId = "session",
                messageId = "a-tool",
                callId = "call",
                tool = "bash",
                state = ToolState.Completed(
                    input = (0 until inputEntries).associate { i -> "key$i" to JsonPrimitive("value$i") },
                    output = output,
                ),
            ),
        ),
    )

    @Test
    fun breakdownIsEmptyWithoutReportedInputTokens() {
        assertTrue(computeContextBreakdown(listOf(user("u-1", "abcd")), 0, "sys").isEmpty())
    }

    @Test
    fun breakdownIsEmptyWhenNothingIsReported() {
        assertTrue(computeContextBreakdown(emptyList(), 0, null).isEmpty())
        assertTrue(computeContextBreakdown(listOf(user("u-1", "")), 0, "").isEmpty())
    }

    @Test
    fun reportedInputWithoutMessageTextFallsIntoOther() {
        assertEquals(
            listOf(ContextBreakdownKey.OTHER to 100),
            computeContextBreakdown(emptyList(), 100, null).map { it.key to it.tokens },
        )
    }

    @Test
    fun breakdownScalesEstimatesDownToReportedInputTokens() {
        val segments = computeContextBreakdown(
            messages = listOf(user("u-1", "a".repeat(16))),
            input = 6,
            systemPrompt = "a".repeat(32),
        )

        assertEquals(
            listOf(ContextBreakdownKey.SYSTEM to 4, ContextBreakdownKey.USER to 2),
            segments.map { it.key to it.tokens },
        )
        assertEquals(100.0, segments.sumOf { it.percentage }, 1e-6)
        assertEquals(66.66666666666667, segments[0].percentage, 1e-9)
    }

    @Test
    fun breakdownNeverScalesEstimatesUpBeyondInputTokens() {
        val segments = computeContextBreakdown(
            messages = listOf(user("u-1", "abcdefgh")),
            input = 45,
            systemPrompt = null,
        )

        assertEquals(
            listOf(ContextBreakdownKey.USER to 2, ContextBreakdownKey.OTHER to 43),
            segments.map { it.key to it.tokens },
        )
    }

    @Test
    fun flooringRemainderIsAbsorbedByOther() {
        val segments = computeContextBreakdown(
            messages = listOf(user("u-1", "a".repeat(16))),
            input = 7,
            systemPrompt = "a".repeat(32),
        )

        assertEquals(
            listOf(
                ContextBreakdownKey.SYSTEM to 4,
                ContextBreakdownKey.USER to 2,
                ContextBreakdownKey.OTHER to 1,
            ),
            segments.map { it.key to it.tokens },
        )
        assertEquals(7, segments.sumOf { it.tokens })
    }

    @Test
    fun flooringCanCollapseEstimatesIntoOther() {
        val segments = computeContextBreakdown(
            messages = listOf(user("u-1", "abcd")),
            input = 1,
            systemPrompt = "sys",
        )

        assertEquals(listOf(ContextBreakdownKey.OTHER to 1), segments.map { it.key to it.tokens })
    }

    @Test
    fun toolInputAndOutputAreCountedAsToolSegment() {
        val segments = computeContextBreakdown(
            messages = listOf(assistantWithTool(output = "x".repeat(60), inputEntries = 4)),
            input = 31,
            systemPrompt = null,
        )

        assertEquals(listOf(ContextBreakdownKey.TOOL), segments.map { it.key })
        assertEquals(31, segments.single().tokens)
        assertEquals(100.0, segments.single().percentage, 1e-9)
    }

    @Test
    fun assistantTextAndReasoningShareAssistantSegment() {
        val message = ChatMessage(
            message = Message.Assistant("a-1", "session", time = TimeInfo(2), parentId = "root"),
            parts = listOf(
                Part.Text(id = "p-1", sessionId = "session", messageId = "a-1", text = "abcd"),
                Part.Reasoning(id = "p-2", sessionId = "session", messageId = "a-1", text = "efgh"),
            ),
        )

        val segments = computeContextBreakdown(listOf(message), 4, systemPrompt = null)

        assertEquals(
            listOf(ContextBreakdownKey.ASSISTANT to 2, ContextBreakdownKey.OTHER to 2),
            segments.map { it.key to it.tokens },
        )
    }

    @Test
    fun segmentTokensNeverExceedReportedInput() {
        val segments = computeContextBreakdown(
            messages = listOf(user("u-1", "a".repeat(4000)), assistant("a-1", "b".repeat(4000))),
            input = 100,
            systemPrompt = "a".repeat(2000),
        )

        assertTrue(segments.sumOf { it.tokens } <= 100)
        segments.forEach { assertTrue(it.tokens >= 0) }
        segments.forEach { assertTrue(abs(it.percentage - it.tokens / 100.0 * 100) < 1e-9) }
    }
}
