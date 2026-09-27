/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : V2ErrorEnvelopeTest.kt
 * Date : 2026/09/25 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class V2ErrorEnvelopeTest {

    /** 真实 NAS starburst-agent 错误信封：{_tag, message, kind?, field?}。 */
    @Test
    fun `parses message from tagged error envelope`() {
        val text = """{"_tag":"InvalidRequestError","kind":"Query","field":"limit","message":"Expected an integer, got NaN"}"""
        assertEquals("Expected an integer, got NaN", parseV2ErrorMessage(text))
    }

    @Test
    fun `parses plain writeErr envelope`() {
        assertEquals(
            "session not found",
            parseV2ErrorMessage("""{"_tag":"NotFoundError","message":"session not found"}"""),
        )
    }

    @Test
    fun `returns empty for non-json or missing message`() {
        assertEquals("", parseV2ErrorMessage("not json"))
        assertEquals("", parseV2ErrorMessage("""{"_tag":"Error"}"""))
        assertEquals("", parseV2ErrorMessage("""{"message": ""}"""))
        assertEquals("", parseV2ErrorMessage(""))
    }

    @Test
    fun `returns empty for unparseable body`() {
        assertEquals("", parseV2ErrorMessage("<html>502 Bad Gateway</html>"))
    }
}
