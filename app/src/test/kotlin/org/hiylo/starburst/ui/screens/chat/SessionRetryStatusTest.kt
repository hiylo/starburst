/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SessionRetryStatusTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.hiylo.starburst.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRetryStatusTest {
    @Test
    fun `retry remains an active stoppable session state`() {
        val retry = SessionStatus.Retry(1, "rate limited", 10_000)

        assertTrue(isWorkingSessionStatus(SessionStatus.Busy))
        assertTrue(isWorkingSessionStatus(retry))
        assertFalse(isWorkingSessionStatus(SessionStatus.Idle))
        assertEquals(
            ComposerAction.STOP,
            composerAction(
                isBusy = isWorkingSessionStatus(retry),
                isSending = false,
                hasDraft = false,
                isShellMode = false,
            ),
        )
    }

    @Test
    fun `retry countdown rounds partial seconds up and stops at zero`() {
        assertEquals(3, retryDelaySeconds(nextAtMillis = 3_001, nowMillis = 1_000))
        assertEquals(2, retryDelaySeconds(nextAtMillis = 3_000, nowMillis = 1_000))
        assertEquals(0, retryDelaySeconds(nextAtMillis = 999, nowMillis = 1_000))
    }
}
