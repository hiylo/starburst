/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatScreenLinkTest.kt
 * Date : 2026/09/07 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScreenLinkTest {

    @Test
    fun `same server url matches server root`() {
        assertTrue(isSameServerUrl("http://192.168.1.100:4096", "http://192.168.1.100:4096"))
        assertTrue(isSameServerUrl("http://192.168.1.100:4096/", "http://192.168.1.100:4096"))
        assertTrue(isSameServerUrl("https://example.com", "https://example.com/"))
    }

    @Test
    fun `same server url matches subpaths`() {
        assertTrue(isSameServerUrl("http://192.168.1.100:4096/docs/guide", "http://192.168.1.100:4096"))
        assertTrue(isSameServerUrl("http://192.168.1.100:4096/api/health?x=1#top", "http://192.168.1.100:4096"))
    }

    @Test
    fun `cross origin urls are not same server`() {
        assertFalse(isSameServerUrl("https://github.com/hiylo/starburst", "http://192.168.1.100:4096"))
        assertFalse(isSameServerUrl("http://192.168.1.101:4096", "http://192.168.1.100:4096"))
        assertFalse(isSameServerUrl("http://192.168.1.100:4097", "http://192.168.1.100:4096"))
        assertFalse(isSameServerUrl("http://evil.example.com/192.168.1.100:4096", "http://192.168.1.100:4096"))
    }

    @Test
    fun `blank inputs never match`() {
        assertFalse(isSameServerUrl("", "http://192.168.1.100:4096"))
        assertFalse(isSameServerUrl("http://192.168.1.100:4096", ""))
        assertFalse(isSameServerUrl("", ""))
    }
}
