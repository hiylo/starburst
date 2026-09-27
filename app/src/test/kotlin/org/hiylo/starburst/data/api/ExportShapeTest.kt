/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ExportShapeTest.kt
 * Date : 2026/09/25 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportShapeTest {

    @Test
    fun `session envelope unwraps data object`() {
        val body = """{"data":{"id":"ses_1","title":"t"}}"""
        val data = v2EnvelopeDataOrNull(body)
        assertTrue(data is JsonObject)
        assertEquals("ses_1", (data as JsonObject)["id"]?.toString()?.removeSurrounding("\""))
    }

    @Test
    fun `messages envelope unwraps data array`() {
        val body = """{"cursor":{"next":"abc"},"data":[{"id":"m1"},{"id":"m2"}]}"""
        val data = v2EnvelopeDataOrNull(body)
        assertTrue(data is JsonArray)
        assertEquals(2, (data as JsonArray).size)
    }

    @Test
    fun `plain array without envelope returns null data`() {
        val body = """[{"id":"m1"}]"""
        assertNull(v2EnvelopeDataOrNull(body))
    }

    @Test
    fun `malformed body returns null data`() {
        assertNull(v2EnvelopeDataOrNull("not-json"))
        assertNull(v2EnvelopeDataOrNull(""))
    }

    @Test
    fun `root object parse extracts cursor and data`() {
        val body = """{"cursor":{"next":"c2"},"data":[{"id":"m1"}]}"""
        val root = parseRootObjectOrNull(body)
        val next = root?.get("cursor")?.let { it as JsonObject }?.get("next")?.toString()
        assertEquals("\"c2\"", next)
        assertTrue(root?.get("data") is JsonArray)
    }
}
