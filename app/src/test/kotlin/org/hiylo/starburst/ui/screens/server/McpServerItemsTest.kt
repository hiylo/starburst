/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : McpServerItemsTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.server

import org.hiylo.starburst.data.api.McpStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class McpServerItemsTest {
    @Test
    fun `mcp servers are mapped and sorted by name`() {
        val items = mcpItems(
            mapOf(
                "Zeta" to McpStatus(status = "failed", error = "offline"),
                "alpha" to McpStatus(status = "connected"),
            )
        )

        assertEquals(listOf("alpha", "Zeta"), items.map(McpServerItem::name))
        assertEquals("offline", items.last().error)
    }
}
