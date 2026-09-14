/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : CancellationHandlingTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import java.util.concurrent.CancellationException
import org.junit.Assert.assertThrows
import org.junit.Test

class CancellationHandlingTest {
    @Test
    fun `routine cancellation is rethrown instead of logged as failure`() {
        assertThrows(CancellationException::class.java) {
            CancellationException("cancelled").rethrowCancellation()
        }
    }
}
