/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : MessageIdGeneratorTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageIdGeneratorTest {

    @Test
    fun createsServerCompatibleMonotonicMessageIds() {
        val first = MessageIdGenerator.next(1_700_000_000_000)
        val second = MessageIdGenerator.next(1_700_000_000_000)

        assertEquals(30, first.length)
        assertTrue(first.startsWith("msg_"))
        assertTrue(first < second)
    }
}
