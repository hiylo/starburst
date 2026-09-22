/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : TestIntelUiTest.kt
 * Date : 2026/09/22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.testintel

import androidx.compose.ui.graphics.Color
import org.hiylo.starburst.ui.theme.StatusConnected
import org.hiylo.starburst.ui.theme.StatusError
import org.hiylo.starburst.ui.theme.StatusProcessing
import org.hiylo.starburst.ui.theme.StatusWarning
import org.junit.Assert.assertEquals
import org.junit.Test

class TestIntelUiTest {

    @Test
    fun `analysis status color maps known states`() {
        assertEquals(StatusConnected, analysisStatusColor("ok"))
        assertEquals(StatusConnected, analysisStatusColor("OK"))
        assertEquals(StatusError, analysisStatusColor("failed"))
        assertEquals(StatusProcessing, analysisStatusColor("running"))
        assertEquals(StatusProcessing, analysisStatusColor("analyzing"))
        assertEquals(Color.Gray, analysisStatusColor(""))
        assertEquals(Color.Gray, analysisStatusColor("weird"))
    }

    @Test
    fun `severity color maps high medium low`() {
        assertEquals(StatusError, severityColor("high"))
        assertEquals(Color(0xFFF97316), severityColor("medium"))
        assertEquals(StatusWarning, severityColor("low"))
        assertEquals(Color.Gray, severityColor("unknown"))
    }

    @Test
    fun `run status color maps queued running passed failed`() {
        assertEquals(Color.Gray, runStatusColor("queued"))
        assertEquals(StatusProcessing, runStatusColor("running"))
        assertEquals(StatusConnected, runStatusColor("passed"))
        assertEquals(StatusError, runStatusColor("failed"))
    }

    @Test
    fun `fix status color maps proposed applied rejected rolled_back`() {
        assertEquals(StatusProcessing, fixStatusColor("proposed"))
        assertEquals(StatusConnected, fixStatusColor("applied"))
        assertEquals(StatusError, fixStatusColor("rejected"))
        assertEquals(StatusWarning, fixStatusColor("rolled_back"))
        assertEquals(StatusWarning, fixStatusColor("rollback"))
    }

    @Test
    fun `format timestamp parses iso offset and local forms`() {
        assertEquals("2026-09-22 10:30", formatTimestamp("2026-09-22T10:30:00+08:00"))
        assertEquals("2026-09-22 10:30", formatTimestamp("2026-09-22T10:30:00"))
    }

    @Test
    fun `format timestamp returns blank for null and raw for garbage`() {
        assertEquals("", formatTimestamp(null))
        assertEquals("", formatTimestamp(""))
        assertEquals("not-a-date", formatTimestamp("not-a-date"))
    }
}