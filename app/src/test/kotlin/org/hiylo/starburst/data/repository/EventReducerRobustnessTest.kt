/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : EventReducerRobustnessTest.kt
 * Date : 2026/09/25 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.repository

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.hiylo.starburst.data.search.FtsHit
import org.hiylo.starburst.data.search.MessageFtsIndex
import org.hiylo.starburst.domain.model.Message
import org.hiylo.starburst.domain.model.Part
import org.hiylo.starburst.domain.model.SseEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventReducerRobustnessTest {

    /** no-op 全文索引实现：纯 JVM 测试无需 Android SQLite/Context。 */
    private object NoopFtsIndex : MessageFtsIndex {
        override suspend fun index(serverId: String, sessionId: String, messageId: String, title: String, content: String) = Unit
        override suspend fun search(query: String, limit: Int, serverId: String?): List<FtsHit> = emptyList()
        override suspend fun deleteSession(sessionId: String) = Unit
        override suspend fun clear() = Unit
    }

    private fun reducer() = EventReducer(NoopFtsIndex)

    @Test
    fun `toolContentText tolerates non-array content`() {
        val r = reducer()
        assertEquals("", r.toolContentText(JsonObject(emptyMap())))
        assertEquals("", r.toolContentText(JsonPrimitive("oops")))
        assertEquals("", r.toolContentText(JsonArray(emptyList())))
    }

    @Test
    fun `toolContentText extracts text parts from array`() {
        val r = reducer()
        val arr = JsonArray(
            listOf(
                buildJsonObject { put("type", "text"); put("text", "out1") },
                buildJsonObject { put("type", "tool"); put("text", "skip") },
                buildJsonObject { put("type", "text"); put("text", "out2") },
            ),
        )
        assertEquals("out1\nout2", r.toolContentText(arr))
    }

    @Test
    fun `toolMetadata tolerates non-object structured`() {
        val r = reducer()
        val meta = r.toolMetadata(JsonPrimitive("x"), "output")
        assertEquals("output", meta["output"]?.jsonPrimitive?.content)
    }

    @Test
    fun `toolMetadata merges structured with output`() {
        val r = reducer()
        val structured = buildJsonObject { put("command", "ls") }
        val meta = r.toolMetadata(structured, "files.txt")
        assertEquals("ls", meta["command"]?.jsonPrimitive?.content)
        assertEquals("files.txt", meta["output"]?.jsonPrimitive?.content)
    }

    @Test
    fun `stepStarted with non-object model does not crash`() {
        val r = reducer()
        // model 为字符串（畸形）：原实现 event.model.jsonObject 会抛 MissingFieldException
        // 导致整条 step.started 被外层 catch 丢弃；修复后走空对象、消息正常建立。
        r.processEvent(
            SseEvent.NextStepStarted("ses", "msg_a1", "build", JsonPrimitive("not-an-object"), 100L),
            "srv",
        )
        val assistant = r.messages.value["ses"]?.singleOrNull()
        assertTrue(assistant is Message.Assistant)
        assertEquals(null, (assistant as Message.Assistant).modelId)
    }

    @Test
    fun `toolCalled with non-object input does not crash`() {
        val r = reducer()
        r.processEvent(
            SseEvent.NextToolCalled(
                sessionId = "ses", messageId = "msg_t1", callId = "call1",
                tool = "bash", input = JsonArray(emptyList()), timestamp = 200L,
            ),
            "srv",
        )
        val tool = r.parts.value["msg_t1"]?.filterIsInstance<Part.Tool>()?.singleOrNull()
        assertTrue(tool != null)
    }
}
