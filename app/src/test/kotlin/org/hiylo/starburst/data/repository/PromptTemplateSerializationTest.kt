/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : PromptTemplateSerializationTest.kt
 * Date : 2026/09/17 10:05:36
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.repository

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PromptTemplateSerializationTest {
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun promptTemplatesRoundTripThroughJson() {
        val templates = listOf(
            SettingsRepository.PromptTemplate(id = "tpl-1", name = "Review", prompt = "Review this diff"),
            SettingsRepository.PromptTemplate(id = "tpl-2", name = "Summarize", prompt = "Summarize the changes"),
        )

        val restored = json.decodeFromString<List<SettingsRepository.PromptTemplate>>(
            json.encodeToString(templates),
        )

        assertEquals(templates, restored)
    }
}
