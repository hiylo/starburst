/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ShareUrlNormalizeTest.kt
 * Date : 2026/09/25 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareUrlNormalizeTest {

    @Test
    fun `relative share path becomes public link`() {
        assertEquals(
            "https://opncd.ai/s/ses_f29fca90cffe58DA6QUj4gGmle",
            normalizeShareUrl("/share/ses_f29fca90cffe58DA6QUj4gGmle"),
        )
    }

    @Test
    fun `full url passes through unchanged`() {
        assertEquals("https://opncd.ai/s/ses_abc", normalizeShareUrl("https://opncd.ai/s/ses_abc"))
        assertEquals("http://example.com/s/xyz", normalizeShareUrl("http://example.com/s/xyz"))
    }

    @Test
    fun `path with leading slash and trailing slash is trimmed`() {
        assertEquals(
            "https://opncd.ai/s/ses_xyz",
            normalizeShareUrl("/share/ses_xyz/"),
        )
    }

    @Test
    fun `bare id without share prefix resolves too`() {
        assertEquals("https://opncd.ai/s/ses_abc", normalizeShareUrl("ses_abc"))
    }

    @Test
    fun `unparseable relative url falls back to raw`() {
        assertEquals("/", normalizeShareUrl("/"))
        assertEquals("", normalizeShareUrl(""))
    }
}
