/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SseEventParserTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.hiylo.starburst.domain.model.SseEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseEventParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesStructuredSessionError() {
        val event = parseSessionError(
            buildJsonObject {
                put("sessionID", "session")
                put("error", buildJsonObject {
                    put("name", "ProviderError")
                    put("data", buildJsonObject { put("message", "quota exceeded") })
                })
            },
            json,
        )

        assertEquals("session", event.sessionId)
        assertEquals("ProviderError", event.error.name)
        assertEquals("quota exceeded", event.error.message)
    }

    @Test
    fun keepsCompatibilityWithStringServerErrors() {
        val event = parseSessionError(buildJsonObject { put("error", "legacy error") }, json)

        assertNull(event.sessionId)
        assertEquals("legacy error", event.error.message)
    }

    @Test
    fun readsV2EventDataAndKeepsLegacyPropertiesCompatibility() {
        val v2 = buildJsonObject {
            put("data", buildJsonObject { put("sessionID", "v2") })
        }
        val legacy = buildJsonObject {
            put("properties", buildJsonObject { put("sessionID", "legacy") })
        }

        assertEquals("v2", sseEventData(v2)["sessionID"]?.toString()?.trim('"'))
        assertEquals("legacy", sseEventData(legacy)["sessionID"]?.toString()?.trim('"'))
    }

    @Test
    fun suppressesOnlyHighFrequencyEventsFromDebugLog() {
        assertEquals(true, isHighFrequencySseEvent(SseEvent.MessagePartDelta("session", "message", "part", "text", "x")))
        assertEquals(true, isHighFrequencySseEvent(SseEvent.ServerHeartbeat))
        assertEquals(false, isHighFrequencySseEvent(SseEvent.SessionCompacted("session")))
        assertEquals(false, isHighFrequencySseEvent(SseEvent.SessionIdle("session")))
    }

    @Test
    fun mapsV2PermissionAskedEventFields() {
        val event = parsePermissionV2Asked(
            buildJsonObject {
                put("id", "per_1")
                put("sessionID", "ses_1")
                put("action", "bash")
                put("resources", buildJsonArray { add("echo hi"); add("ls") })
                put("save", buildJsonArray { add("bash:echo*") })
                put("source", buildJsonObject {
                    put("type", "tool")
                    put("messageID", "msg_1")
                    put("callID", "call_1")
                })
            },
        )

        assertEquals("per_1", event.id)
        assertEquals("ses_1", event.sessionId)
        assertEquals("bash", event.permission)
        assertEquals(listOf("echo hi", "ls"), event.patterns)
        assertEquals(listOf("bash:echo*"), event.always)
        assertEquals("msg_1", event.tool?.messageId)
        assertEquals("call_1", event.tool?.callId)
    }

    @Test
    fun decodesV2PermissionRequestRestShape() {
        val decoded = json.decodeFromString(
            PermissionRequest.serializer(),
            """{"id":"per_1","sessionID":"ses_1","action":"bash","resources":["echo hi"],""" +
                """"save":["bash:*"],"source":{"type":"tool","messageID":"msg_1","callID":"call_1"}}""",
        )

        assertEquals("bash", decoded.permission)
        assertEquals(listOf("echo hi"), decoded.patterns)
        assertEquals(listOf("bash:*"), decoded.always)
        assertEquals("msg_1", decoded.tool?.messageId)
        assertEquals("call_1", decoded.tool?.callId)
    }
}
