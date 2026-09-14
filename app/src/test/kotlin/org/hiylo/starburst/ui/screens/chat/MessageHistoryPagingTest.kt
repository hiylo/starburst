/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : MessageHistoryPagingTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageHistoryPagingTest {
    @Test
    fun initialPageShowsAtMostTenMessages() {
        assertEquals(5, fastInitialMessageLimit(5))
        assertEquals(10, fastInitialMessageLimit(25))
        assertEquals(10, fastInitialMessageLimit(200))
    }

    @Test
    fun backgroundPagesFillConfiguredLimitInBoundedChunks() {
        assertEquals(25, backgroundMessageLimit(10, 50))
        assertEquals(15, backgroundMessageLimit(35, 50))
        assertEquals(1, backgroundMessageLimit(49, 50))
    }
}
