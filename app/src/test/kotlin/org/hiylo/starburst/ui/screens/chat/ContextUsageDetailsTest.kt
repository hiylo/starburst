/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ContextUsageDetailsTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ContextUsageDetailsTest {
    @Test
    fun separatesCurrentContextFromCumulativeSessionTotals() {
        val usage = ContextUsageDetails(
            input = 100,
            output = 20,
            reasoning = 10,
            cacheRead = 30,
            cacheWrite = 5,
            sessionInput = 500,
            sessionOutput = 100,
            sessionReasoning = 50,
            sessionCacheRead = 200,
            sessionCacheWrite = 10,
        )

        assertEquals(165, usage.currentTotal)
        assertEquals(860, usage.sessionTotal)
    }
}
