/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ContextTokenEstimationTest.kt
 * Date : 2026/09/17 10:05:36
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.hiylo.starburst.domain.model.Message
import org.hiylo.starburst.domain.model.Part
import org.hiylo.starburst.domain.model.TimeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextTokenEstimationTest {

    private fun user(id: String, vararg parts: Part) = ChatMessage(
        message = Message.User(id, "session", time = TimeInfo(1)),
        parts = parts.toList(),
    )

    private fun textPart(id: String, messageId: String, text: String) =
        Part.Text(id = id, sessionId = "session", messageId = messageId, text = text)

    @Test
    fun emptyMessagesEstimateToZeroTokens() {
        assertEquals(0, estimateContextTokens(emptyList()))
        assertEquals(0, estimateContextTokens(listOf(user("u-1"))))
    }

    @Test
    fun cjkAndAsciiAreCountedPerCharacter() {
        val ascii = estimateContextTokens(listOf(user("u-1", textPart("p-1", "u-1", "abcd"))))
        val cjk = estimateContextTokens(listOf(user("u-1", textPart("p-1", "u-1", "你好世界"))))

        assertEquals(1, ascii)
        assertEquals(1, cjk)
    }

    @Test
    fun tokenEstimateRoundsUpWithCharsPerToken() {
        assertEquals(1, estimateContextTokens(listOf(user("u-1", textPart("p-1", "u-1", "a")))))
        assertEquals(1, estimateContextTokens(listOf(user("u-1", textPart("p-1", "u-1", "abcd")))))
        assertEquals(2, estimateContextTokens(listOf(user("u-1", textPart("p-1", "u-1", "abcde")))))
    }

    @Test
    fun tokenEstimateIsMonotonicWithLength() {
        val shorter = estimateContextTokens(listOf(user("u-1", textPart("p-1", "u-1", "ab"))))
        val longer = estimateContextTokens(listOf(user("u-1", textPart("p-1", "u-1", "abcdefgh"))))

        assertTrue(longer >= shorter)
        assertTrue(longer > shorter)
    }

    @Test
    fun reasoningAndSnapshotPartsAreCounted() {
        val message = user(
            "u-1",
            textPart("p-1", "u-1", "abcd"),
            Part.Reasoning(id = "p-2", sessionId = "session", messageId = "u-1", text = "efgh"),
            Part.Snapshot(id = "p-3", sessionId = "session", messageId = "u-1", snapshot = "ijkl"),
        )

        assertEquals(3, estimateContextTokens(listOf(message)))
    }
}
