/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : MessageBookmarkTest.kt
 * Date : 2026/09/17 10:05:36
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.domain.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageBookmarkTest {
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun decodesLegacyRecordWithoutTagsAsEmptyList() {
        val legacy = """
            [{
              "id": "server|session|message",
              "serverId": "server",
              "sessionId": "session",
              "messageId": "message",
              "messageText": "hello",
              "createdAt": 1700000000000
            }]
        """.trimIndent()

        val bookmarks = json.decodeFromString<List<MessageBookmark>>(legacy)

        assertEquals(1, bookmarks.size)
        assertEquals(emptyList<String>(), bookmarks.single().tags)
    }

    @Test
    fun bookmarkWithTagsRoundTripsThroughJson() {
        val bookmark = MessageBookmark(
            id = MessageBookmark.buildId("server", "session", "message"),
            serverId = "server",
            sessionId = "session",
            messageId = "message",
            messageText = "hello",
            createdAt = 1_700_000_000_000L,
            tags = listOf("important", "review"),
        )

        val restored = json.decodeFromString<List<MessageBookmark>>(json.encodeToString(listOf(bookmark)))

        assertEquals(listOf("important", "review"), restored.single().tags)
        assertEquals("server|session|message", restored.single().id)
    }

    @Test
    fun buildIdIsStableAndIdempotent() {
        assertEquals("server|session|message", MessageBookmark.buildId("server", "session", "message"))
        assertEquals(
            MessageBookmark.buildId("a", "b", "c"),
            MessageBookmark.buildId("a", "b", "c"),
        )
    }
}
