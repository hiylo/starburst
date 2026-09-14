/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : TerminalPanelHintCoordinatorTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalPanelHintCoordinatorTest {
    @Test
    fun `hint is consumed once per process state`() {
        val hint = OncePerProcessHint()

        assertTrue(hint.tryShow())
        assertFalse(hint.tryShow())
    }
}
