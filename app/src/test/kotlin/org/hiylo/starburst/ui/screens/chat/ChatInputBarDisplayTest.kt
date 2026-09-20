/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatInputBarDisplayTest.kt
 * Date : 2026/09/20 10:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatInputBarDisplayTest {

    // ── displayModelLabel ────────────────────────────────────────────

    @Test
    fun shortModelLabelIsRenderedUnchanged() {
        assertEquals("gpt-5", displayModelLabel("gpt-5"))
        assertEquals("", displayModelLabel(""))
    }

    @Test
    fun modelLabelAtExactLimitIsNotTruncated() {
        val label = "x".repeat(MODEL_LABEL_MAX_CHARS)

        assertEquals(label, displayModelLabel(label))
    }

    @Test
    fun longModelLabelIsTruncatedWithEllipsis() {
        val label = "anthropic/claude-opus-4-5-thinking-2026-09-01"
        val truncated = displayModelLabel(label)

        assertTrue(label.length > MODEL_LABEL_MAX_CHARS)
        assertEquals(MODEL_LABEL_MAX_CHARS + 1, truncated.length)
        assertTrue(truncated.endsWith("…"))
        assertTrue(label.startsWith(truncated.dropLast(1)))
    }

    @Test
    fun truncatedModelLabelNeverExceedsLimitPlusEllipsis() {
        val label = "x".repeat(MODEL_LABEL_MAX_CHARS + 40)

        assertEquals(MODEL_LABEL_MAX_CHARS + 1, displayModelLabel(label).length)
    }

    @Test
    fun trailingWhitespaceIsTrimmedBeforeEllipsis() {
        val label = "x".repeat(MODEL_LABEL_MAX_CHARS) + "   "

        assertEquals("x".repeat(MODEL_LABEL_MAX_CHARS) + "…", displayModelLabel(label))
    }

    @Test
    fun truncationDoesNotSplitSurrogatePair() {
        val label = "x".repeat(MODEL_LABEL_MAX_CHARS - 1) + "😀"

        assertEquals("x".repeat(MODEL_LABEL_MAX_CHARS - 1) + "…", displayModelLabel(label))
    }

    @Test
    fun nonPositiveLimitKeepsFullLabel() {
        assertEquals("claude-opus-4-5", displayModelLabel("claude-opus-4-5", maxChars = 0))
    }

    // ── contextBudgetRatio / contextBudgetPercentage ─────────────────

    @Test
    fun budgetRatioIsZeroWhenWindowIsUnknown() {
        assertEquals(0.0, contextBudgetRatio(1200, 0), 1e-9)
    }

    @Test
    fun budgetRatioDividesEstimatedByEffectiveWindow() {
        assertEquals(0.5, contextBudgetRatio(5000, 10000), 1e-9)
        assertEquals(1.0, contextBudgetRatio(200000, 200000), 1e-9)
    }

    @Test
    fun budgetPercentageRoundsToNearestInteger() {
        assertEquals(50, contextBudgetPercentage(0.5))
        assertEquals(86, contextBudgetPercentage(0.855))
        assertEquals(100, contextBudgetPercentage(1.0))
    }

    // ── contextBudgetLevel ───────────────────────────────────────────

    @Test
    fun budgetLevelStaysNormalBelowWarningThreshold() {
        assertEquals(ContextBudgetLevel.NORMAL, contextBudgetLevel(0.0))
        assertEquals(ContextBudgetLevel.NORMAL, contextBudgetLevel(0.5))
        assertEquals(ContextBudgetLevel.NORMAL, contextBudgetLevel(CONTEXT_BUDGET_WARNING_RATIO))
    }

    @Test
    fun budgetLevelWarnsAboveWarningThreshold() {
        assertEquals(
            ContextBudgetLevel.WARNING,
            contextBudgetLevel(CONTEXT_BUDGET_WARNING_RATIO + 0.01),
        )
        assertEquals(ContextBudgetLevel.WARNING, contextBudgetLevel(0.899))
    }

    @Test
    fun budgetLevelGoesCriticalAtCriticalThreshold() {
        assertEquals(ContextBudgetLevel.CRITICAL, contextBudgetLevel(CONTEXT_BUDGET_CRITICAL_RATIO))
        assertEquals(ContextBudgetLevel.CRITICAL, contextBudgetLevel(1.0))
        assertEquals(ContextBudgetLevel.CRITICAL, contextBudgetLevel(1.5))
    }

    @Test
    fun defaultBudgetColorThresholdsMatchOriginalMagicNumbers() {
        assertEquals(0.8, CONTEXT_BUDGET_WARNING_RATIO, 1e-9)
        assertEquals(0.9, CONTEXT_BUDGET_CRITICAL_RATIO, 1e-9)
    }
}
