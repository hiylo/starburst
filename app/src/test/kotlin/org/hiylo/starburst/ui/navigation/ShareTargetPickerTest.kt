/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ShareTargetPickerTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.navigation

import org.hiylo.starburst.domain.model.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareTargetPickerTest {
    @Test
    fun exposesOnlyServersWithLiveConnections() {
        val connected = ServerConfig(id = "connected", url = "https://connected.example")
        val disconnectedWithCache = ServerConfig(
            id = "disconnected",
            url = "https://disconnected.example",
            lastConnected = 123,
            isHealthy = true,
        )

        val result = connectedShareServers(
            servers = listOf(connected, disconnectedWithCache),
            connectedServerIds = setOf(connected.id),
        )

        assertEquals(listOf(connected), result)
    }
}
