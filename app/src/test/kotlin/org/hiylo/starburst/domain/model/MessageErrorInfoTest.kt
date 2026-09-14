/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : MessageErrorInfoTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.domain.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageErrorInfoTest {
    @Test
    fun readsMessageFromObjectData() {
        val error = Message.Assistant.ErrorInfo(
            name = "ProviderError",
            data = JsonObject(mapOf("message" to JsonPrimitive("Request failed"))),
        )

        assertEquals("Request failed", error.message)
    }

    @Test
    fun readsMessageFromPrimitiveData() {
        val error = Message.Assistant.ErrorInfo(
            name = "ProviderError",
            data = JsonPrimitive("Plain server error"),
        )

        assertEquals("Plain server error", error.message)
    }

    @Test
    fun fallsBackToNameForUnsupportedDataShape() {
        val error = Message.Assistant.ErrorInfo(
            name = "ProviderError",
            data = JsonArray(listOf(JsonPrimitive("unexpected"))),
        )

        assertEquals("ProviderError", error.message)
    }
}
