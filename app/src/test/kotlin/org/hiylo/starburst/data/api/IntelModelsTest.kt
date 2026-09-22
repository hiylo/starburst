/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : IntelModelsTest.kt
 * Date : 2026/09/22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class IntelModelsTest {

    @Test
    fun `parseStringListJson returns empty for null blank or invalid`() {
        assertEquals(emptyList<String>(), parseStringListJson(null))
        assertEquals(emptyList<String>(), parseStringListJson(""))
        assertEquals(emptyList<String>(), parseStringListJson("not json"))
    }

    @Test
    fun `parseStringListJson parses json array strings`() {
        assertEquals(listOf("GET /api/users", "POST /api/users"), parseStringListJson("[\"GET /api/users\",\"POST /api/users\"]"))
        assertEquals(listOf("GET /api/health"), parseStringListJson("[\"GET /api/health\"]"))
    }

    @Test
    fun `IntelFeature ends delegates to parsed endsJson`() {
        val feature = IntelFeature(
            id = 1L,
            projectId = 2L,
            name = "auth",
            endsJson = "[\"POST /auth/login\",\"GET /auth/me\"]",
        )
        assertEquals(listOf("POST /auth/login", "GET /auth/me"), feature.ends())
        assertEquals(emptyList<String>(), IntelFeature(id = 2L, projectId = 1L).ends())
    }

    @Test
    fun `parseDiffJson returns empty for null blank or invalid`() {
        assertEquals(emptyList<IntelFixSuggestion>(), parseDiffJson(null))
        assertEquals(emptyList<IntelFixSuggestion>(), parseDiffJson(""))
        assertEquals(emptyList<IntelFixSuggestion>(), parseDiffJson("garbage"))
    }

    @Test
    fun `parseDiffJson parses suggestion array`() {
        val raw = """[{"file":"src/Main.kt","oldText":"val a = 1","newText":"val a = 2","line":3,"confidence":"high"}]"""
        val parsed = parseDiffJson(raw)
        assertEquals(1, parsed.size)
        val suggestion = parsed.first()
        assertEquals("src/Main.kt", suggestion.file)
        assertEquals("val a = 1", suggestion.oldText)
        assertEquals("val a = 2", suggestion.newText)
        assertEquals(3, suggestion.line)
        assertEquals("high", suggestion.confidence)
    }
}