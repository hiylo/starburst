/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SseFrameDecoderTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SseFrameDecoderTest {
    @Test
    fun joinsMultipleDataLinesWithNewlines() {
        val decoder = SseFrameDecoder()

        assertNull(decoder.accept("data: first"))
        assertNull(decoder.accept("data:second"))
        assertEquals("first\nsecond", decoder.accept(""))
    }

    @Test
    fun ignoresCommentsAndNonDataFields() {
        val decoder = SseFrameDecoder()

        decoder.accept(": heartbeat")
        decoder.accept("event: update")
        decoder.accept("id: 42")
        decoder.accept("data: payload")

        assertEquals("payload", decoder.accept(""))
    }

    @Test
    fun dispatchesBufferedDataAtEndOfStream() {
        val decoder = SseFrameDecoder()
        decoder.accept("data: trailing")

        assertEquals("trailing", decoder.finish())
        assertNull(decoder.finish())
    }

    @Test
    fun rejectsOversizedFramesAndResetsState() {
        val decoder = SseFrameDecoder(maxFrameSize = 5)

        assertThrows(SseFrameTooLargeException::class.java) {
            decoder.accept("data: 123456")
        }
        decoder.accept("data: ok")
        assertEquals("ok", decoder.accept(""))
    }
}
