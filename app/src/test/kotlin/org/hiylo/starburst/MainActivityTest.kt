/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : MainActivityTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst

import org.hiylo.starburst.domain.model.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainActivityTest {
    private val servers = listOf(
        ServerConfig(
            id = "server-1",
            url = "http://198.51.100.6:4096/",
            username = "opencode",
        ),
        ServerConfig(
            id = "server-2",
            url = "https://example.test",
            username = "opencode",
        ),
    )

    @Test
    fun `deep link resolves server by id first`() {
        val server = findDeepLinkServer(servers, "server-2", "http://198.51.100.6:4096")

        assertEquals("server-2", server?.id)
    }

    @Test
    fun `legacy deep link resolves server by normalized url`() {
        val server = findDeepLinkServer(servers, "", "http://198.51.100.6:4096")

        assertEquals("server-1", server?.id)
    }

    @Test
    fun `unknown deep link server is not resolved`() {
        assertNull(findDeepLinkServer(servers, "", "https://unknown.test"))
    }
}
