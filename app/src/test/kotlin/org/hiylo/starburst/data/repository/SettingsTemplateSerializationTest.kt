/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SettingsTemplateSerializationTest.kt
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

class SettingsTemplateSerializationTest {
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

    @Test
    fun sessionTemplatesRoundTripThroughJson() {
        val templates = listOf(
            SettingsRepository.SessionTemplate(
                id = "st-1",
                name = "Agent",
                directory = "/project",
                systemPrompt = "You are a helpful assistant",
                modelProviderId = "provider",
                modelId = "model",
                prompt = "Start working",
            ),
        )

        val restored = json.decodeFromString<List<SettingsRepository.SessionTemplate>>(
            json.encodeToString(templates),
        )

        assertEquals(templates, restored)
    }

    @Test
    fun sessionTemplateDefaultsAreEncodedAndRestored() {
        val template = SettingsRepository.SessionTemplate(id = "st-min", name = "Minimal")

        val restored = json.decodeFromString<List<SettingsRepository.SessionTemplate>>(
            json.encodeToString(listOf(template)),
        )

        assertEquals("", restored.single().directory)
        assertEquals("", restored.single().systemPrompt)
        assertEquals("", restored.single().modelProviderId)
        assertEquals("", restored.single().modelId)
        assertEquals("", restored.single().prompt)
    }
}
