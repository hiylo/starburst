/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BackendSystemInfoTest.kt
 * Date : 2026/09/23
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `BackendSystemInfo`（后端 /api/system）的字段解析测试，覆盖 pgvector/vectorCapable。 */
class BackendSystemInfoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses pgvector and vectorCapable flags`() {
        val info = json.decodeFromString<BackendSystemInfo>(
            """{"backend":"starburst-backend","version":"2.1.0","opencodeVersion":"1.18.30","db":"postgres","pgvector":true,"vectorCapable":true}""",
        )
        assertTrue(info.pgvector)
        assertTrue(info.vectorCapable)
        assertEquals("2.1.0", info.version)
        assertEquals("postgres", info.db)
        assertEquals("1.18.30", info.serverVersion)
    }

    @Test
    fun `flags default to false when fields missing`() {
        val info = json.decodeFromString<BackendSystemInfo>(
            """{"backend":"starburst-backend","version":"2.1.0"}""",
        )
        assertEquals(false, info.pgvector)
        assertEquals(false, info.vectorCapable)
        assertEquals("", info.projectUrl)
    }

    @Test
    fun `tolerates unknown fields from newer backend`() {
        val info = json.decodeFromString<BackendSystemInfo>(
            """{"backend":"starburst-backend","version":"2.1.0","pgvector":false,"vectorCapable":false,"someFutureField":"x"}""",
        )
        assertEquals("starburst-backend", info.backend)
    }
}