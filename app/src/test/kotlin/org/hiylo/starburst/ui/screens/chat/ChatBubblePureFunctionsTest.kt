/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatBubblePureFunctionsTest.kt
 * Date : 2026/09/18 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.hiylo.starburst.domain.model.Part
import org.hiylo.starburst.domain.model.ToolState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatBubblePureFunctionsTest {

    private fun tool(state: ToolState) = Part.Tool(
        id = "id",
        sessionId = "session",
        messageId = "message",
        callId = "call",
        tool = "bash",
        state = state,
    )

    // ── extractToolInput ──────────────────────────────────────────────

    @Test
    fun extractToolInput_returnsInputFromEveryState() {
        val input = mapOf("command" to JsonPrimitive("ls"))
        assertEquals(input, extractToolInput(tool(ToolState.Pending(input = input))))
        assertEquals(input, extractToolInput(tool(ToolState.Running(input = input))))
        assertEquals(input, extractToolInput(tool(ToolState.Completed(input = input))))
        assertEquals(input, extractToolInput(tool(ToolState.Error(input = input))))
    }

    @Test
    fun extractToolInput_fallsBackToEmptyMap() {
        assertEquals(
            emptyMap<String, kotlinx.serialization.json.JsonElement>(),
            extractToolInput(tool(ToolState.Pending())),
        )
    }

    // ── extractToolOutput ─────────────────────────────────────────────

    @Test
    fun extractToolOutput_readsCompletedOutput() {
        assertEquals("done", extractToolOutput(tool(ToolState.Completed(output = "done"))))
    }

    @Test
    fun extractToolOutput_readsError() {
        assertEquals("boom", extractToolOutput(tool(ToolState.Error(error = "boom"))))
    }

    @Test
    fun extractToolOutput_blankForRunningAndPending() {
        assertEquals("", extractToolOutput(tool(ToolState.Running())))
        assertEquals("", extractToolOutput(tool(ToolState.Pending())))
    }

    // ── computeSimpleDiff ─────────────────────────────────────────────

    @Test
    fun computeSimpleDiff_allAdded() {
        val lines = computeSimpleDiff(emptyList(), listOf("a", "b"))
        assertEquals(
            listOf(
                DiffLine(DiffLineType.ADDED, "a"),
                DiffLine(DiffLineType.ADDED, "b"),
            ),
            lines,
        )
    }

    @Test
    fun computeSimpleDiff_allRemoved() {
        val lines = computeSimpleDiff(listOf("a", "b"), emptyList())
        assertEquals(
            listOf(
                DiffLine(DiffLineType.REMOVED, "a"),
                DiffLine(DiffLineType.REMOVED, "b"),
            ),
            lines,
        )
    }

    @Test
    fun computeSimpleDiff_commonPrefixAndSuffixKeptAsUnchanged() {
        val lines = computeSimpleDiff(listOf("a", "b", "c"), listOf("a", "X", "c"))
        assertEquals(
            listOf(
                DiffLine(DiffLineType.UNCHANGED, "a"),
                DiffLine(DiffLineType.REMOVED, "b"),
                DiffLine(DiffLineType.ADDED, "X"),
                DiffLine(DiffLineType.UNCHANGED, "c"),
            ),
            lines,
        )
    }

    @Test
    fun computeSimpleDiff_bothEmpty() {
        assertEquals(emptyList<DiffLine>(), computeSimpleDiff(emptyList<String>(), emptyList<String>()))
    }

    // ── cleanSessionTitle ─────────────────────────────────────────────

    @Test
    fun cleanSessionTitle_stripsPathToLastSegment() {
        assertEquals("opencode-backend", cleanSessionTitle("/workspaces/opencode-backend"))
        assertEquals("backend", cleanSessionTitle("backend"))
    }

    @Test
    fun cleanSessionTitle_stripsAgentPrefixAndHashSuffix() {
        assertEquals("fix-timeout", cleanSessionTitle("agent-fix-timeout-abc12345"))
        assertEquals("review", cleanSessionTitle("subagent_review_9f8e7d"))
    }

    @Test
    fun cleanSessionTitle_blankInputYieldsNull() {
        assertNull(cleanSessionTitle(null))
        assertNull(cleanSessionTitle(""))
        assertNull(cleanSessionTitle("   "))
    }

    // ── formatDurationText ────────────────────────────────────────────

    @Test
    fun formatDurationText_usesMillisecondsUnderSecond() {
        assertEquals("850ms", formatDurationText(850))
    }

    @Test
    fun formatDurationText_usesSecondsOverSecond() {
        assertEquals("1.2s", formatDurationText(1200))
    }
}
