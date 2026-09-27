/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SseMessageUpdateV2ShapeTest.kt
 * Date : 2026/09/24 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.hiylo.starburst.domain.model.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SseMessageUpdateV2ShapeTest {

    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** 与 NAS starburst-agent `message.updated` 事件 data.info 完全一致的真实载荷。 */
    private val v2AssistantInfo = """
        {
          "id": "msg_0d37ee24a0011n8tFHvqbadC1C",
          "time": {"created": 1790254703178, "completed": 1790254706065},
          "type": "assistant",
          "agent": "build",
          "model": {"id": "sensenova-6.8-flash-lite", "providerID": "litellm"},
          "content": [
            {"type": "reasoning", "id": "prt_0d37ee24d002YOs8igW8md3JSJ", "text": "reasoning text"},
            {"type": "text", "id": "prt_0d37ee24d001ln878Gco9DSGjc", "text": "\n\nPONG"}
          ],
          "finish": "stop",
          "tokens": {"input": 7585, "output": 5, "reasoning": 23, "cache": {"read": 0, "write": 0}}
        }
    """.trimIndent()

    private val infoJson = json.parseToJsonElement(v2AssistantInfo).jsonObject

    @Test
    fun `raw V2 info with missing parentID and sessionID still decodes after enrichment`() {
        // 事件 data 层的 props：{info, sessionID}
        val props = buildJsonObject {
            put("info", infoJson)
            put("sessionID", "ses_f2d6a6150ffehJlBgjL5DHtT00")
        }

        val enriched = enrichMessageInfo(infoJson, props)
        // sessionID 已注入
        assertEquals("ses_f2d6a6150ffehJlBgjL5DHtT00", enriched["sessionID"]?.jsonPrimitive?.contentOrNull)
        // model 已摊平为顶层 modelID/providerID
        assertEquals("sensenova-6.8-flash-lite", enriched["modelID"]?.jsonPrimitive?.contentOrNull)
        assertEquals("litellm", enriched["providerID"]?.jsonPrimitive?.contentOrNull)

        // 完整解码不抛异常（parentID 已有默认值）
        val message = json.decodeFromJsonElement(Message.serializer(), enriched)
        assertEquals("assistant", message.role)
        val assistant = message as Message.Assistant
        assertEquals("msg_0d37ee24a0011n8tFHvqbadC1C", assistant.id)
        assertEquals("ses_f2d6a6150ffehJlBgjL5DHtT00", assistant.sessionId)
        assertEquals("sensenova-6.8-flash-lite", assistant.modelId)
        assertEquals("litellm", assistant.providerId)
        assertEquals(5, assistant.tokens?.output)
        assertEquals("stop", assistant.finish)
        assertEquals("build", assistant.agent)
    }

    @Test
    fun `user message info also enriched`() {
        val userInfo = json.parseToJsonElement(
            """{"id":"msg_user01","time":{"created":1},"type":"user","text":"hello"}"""
        ).jsonObject
        val props = buildJsonObject {
            put("info", userInfo)
            put("sessionID", "ses_abc")
        }
        val enriched = enrichMessageInfo(userInfo, props)
        val message = json.decodeFromJsonElement(Message.serializer(), enriched)
        assertEquals("user", message.role)
        val user = message as Message.User
        assertEquals("ses_abc", user.sessionId)
    }
}
