/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : V2MessageMappingTest.kt
 * Date : 2026/09/24 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import org.hiylo.starburst.domain.model.Message
import org.hiylo.starburst.domain.model.Part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V2MessageMappingTest {

    /** 真实 NAS 载荷：用户消息文本在顶层，无 content。 */
    private val userMessageJson = """
        {"id": "msg_0d2ecd32600186RMIauwn7sShn", "time": {"created": 1790245103404}, "type": "user", "text": "你好"}
    """.trimIndent()

    /** 真实 NAS 载荷：assistant 消息，model 嵌套，无 parentID。 */
    private val assistantMessageJson = """
        {
          "id": "msg_0d37ee24a0011n8tFHvqbadC1C",
          "time": {"created": 1790254703178, "completed": 1790254706065},
          "type": "assistant",
          "agent": "build",
          "model": {"id": "sensenova-6.8-flash-lite", "providerID": "litellm"},
          "content": [
            {"type": "text", "id": "prt_0d37ee24d001ln878Gco9DSGjc", "text": "\n\nPONG"}
          ],
          "finish": "stop",
          "tokens": {"input": 7585, "output": 5, "reasoning": 23, "cache": {"read": 0, "write": 0}}
        }
    """.trimIndent()

    @Test
    fun `user message text maps to a text part`() {
        val msg = v2Json.decodeFromString<V2SessionMessage>(userMessageJson)
        val result = msg.toMessageWithParts("ses_test")
        assertEquals("user", result.info.role)
        assertTrue(result.info is Message.User)
        assertEquals(1, result.parts.size)
        val textPart = result.parts[0] as Part.Text
        assertEquals("你好", textPart.text)
        assertEquals("msg_0d2ecd32600186RMIauwn7sShn", textPart.messageId)
        assertEquals("ses_test", textPart.sessionId)
    }

    @Test
    fun `assistant message maps model and parts`() {
        val msg = v2Json.decodeFromString<V2SessionMessage>(assistantMessageJson)
        val result = msg.toMessageWithParts("ses_test")
        val assistant = result.info as Message.Assistant
        assertEquals("sensenova-6.8-flash-lite", assistant.modelId)
        assertEquals("litellm", assistant.providerId)
        assertEquals("", assistant.parentId)
        assertEquals("stop", assistant.finish)
        assertEquals(5, assistant.tokens?.output)
        assertEquals(1, result.parts.size)
        assertTrue(result.parts[0] is Part.Text)
    }

    @Test
    fun `user message with file attachment maps to file part`() {
        val json = """
            {"id": "msg_1", "time": {"created": 1}, "type": "user",
             "text": "look at this",
             "files": [{"uri": "file:///tmp/a.png", "mime": "image/png", "name": "a.png"}]}
        """.trimIndent()
        val msg = v2Json.decodeFromString<V2SessionMessage>(json)
        val result = msg.toMessageWithParts("ses_test")
        assertEquals(2, result.parts.size)
        assertTrue(result.parts[0] is Part.Text)
        val filePart = result.parts[1] as Part.File
        assertEquals("image/png", filePart.mime)
        assertEquals("a.png", filePart.filename)
        assertEquals("file:///tmp/a.png", filePart.url)
    }

    @Test
    fun `compaction message maps to divider`() {
        val json = """
            {"id": "msg_compact1", "time": {"created": 100}, "type": "compaction", "reason": "manual", "summary": "sum", "recent": ""}
        """.trimIndent()
        val msg = v2Json.decodeFromString<V2SessionMessage>(json)
        val result = msg.toMessageWithParts("ses_test")
        assertTrue(result.info is Message.User)
        assertEquals(1, result.parts.size)
        assertTrue(result.parts[0] is Part.Compaction)
        val divider = result.parts[0] as Part.Compaction
        assertEquals("msg_compact1", divider.messageId)
    }
}
