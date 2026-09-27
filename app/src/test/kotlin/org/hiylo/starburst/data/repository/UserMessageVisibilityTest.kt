/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : UserMessageVisibilityTest.kt
 * Date : 2026/09/27 03:40:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.repository

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.hiylo.starburst.data.search.FtsHit
import org.hiylo.starburst.data.search.MessageFtsIndex
import org.hiylo.starburst.domain.model.Message
import org.hiylo.starburst.domain.model.Part
import org.hiylo.starburst.domain.model.SseEvent
import org.hiylo.starburst.domain.model.TimeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 复现真机现象：「有些会话里我发的消息不显示，只显示 AI 回复」。
 *
 * 线上实测 starburst-agent 对一条用户消息只广播三个事件、**没有任何 part 事件**：
 * `session.next.prompt.admitted` → `session.next.prompted` → `message.updated`。
 * 因此用户消息的 part 只能由 App 自己从 `session.next.prompted` 内嵌的 prompt 合成。
 *
 * 本测试按该真实序列驱动 reducer，断言用户消息最终必须带可渲染的文本 part。
 */
class UserMessageVisibilityTest {

    private object NoopFtsIndex : MessageFtsIndex {
        override suspend fun index(
            serverId: String,
            sessionId: String,
            messageId: String,
            title: String,
            content: String,
        ) = Unit

        override suspend fun search(query: String, limit: Int, serverId: String?): List<FtsHit> =
            emptyList()

        override suspend fun deleteSession(sessionId: String) = Unit

        override suspend fun clear() = Unit
    }

    private val text = "用 edit 工具把 difftest.txt 里的 line2 改成 line2-CHANGED，只改这一行。"

    /** 线上 `session.next.prompted` 的 prompt 载荷形状。 */
    private fun promptedEvent(messageId: String, sessionId: String) = SseEvent.Prompted(
        sessionId = sessionId,
        messageId = messageId,
        delivery = "queue",
        prompt = buildJsonObject {
            put("text", text)
            put("files", kotlinx.serialization.json.JsonArray(emptyList()))
        },
        timestamp = 1_700_000_000_000L,
    )

    /** 线上 `message.updated`（V2 通道）只带 info，parts 恒空。 */
    private fun messageUpdatedEvent(messageId: String, sessionId: String) =
        SseEvent.MessageUpdated(
            Message.User(id = messageId, sessionId = sessionId, time = TimeInfo(1_700_000_000_000L)),
        )

    @Test
    fun `user message keeps its text after the real three-event sequence`() {
        val reducer = EventReducer(NoopFtsIndex)
        val sessionId = "ses_probe"
        val messageId = "msg_probe"

        reducer.processEvent(SseEvent.PromptAdmitted(sessionId, messageId, delivery = "queue"), "server")
        reducer.processEvent(promptedEvent(messageId, sessionId), "server")
        reducer.processEvent(messageUpdatedEvent(messageId, sessionId), "server")

        val messages = reducer.messages.value[sessionId].orEmpty()
        assertEquals("用户消息应已入库", 1, messages.size)

        val parts = reducer.parts.value[messageId].orEmpty()
        val rendered = parts.filterIsInstance<Part.Text>().map { it.text }
        assertTrue(
            "message.updated 之后用户消息文本必须仍可渲染，实际 parts=$parts",
            rendered.any { it == text },
        )
    }

    /** prompt 里没有 text 时（如纯附件），不应凭空造出空文本 part。 */
    @Test
    fun `attachment-only prompt does not fabricate text part`() {
        val reducer = EventReducer(NoopFtsIndex)
        val sessionId = "ses_files"
        val messageId = "msg_files"

        reducer.processEvent(
            SseEvent.Prompted(
                sessionId = sessionId,
                messageId = messageId,
                delivery = "queue",
                prompt = buildJsonObject {
                    put("files", kotlinx.serialization.json.JsonArray(emptyList()))
                },
                timestamp = 1L,
            ),
            "server",
        )

        val parts = reducer.parts.value[messageId].orEmpty()
        assertTrue("无 text 时不应生成文本 part，实际=$parts", parts.none { it is Part.Text })
    }

    /**
     * 复现「有些会话里我发的消息不显示，只显示 AI 回复」：
     * 只收到 `message.updated`（错过 `session.next.prompted`，例如中途重连）时，
     * 用户消息进了 messages 却没有 part，且没有 pending 可兜底 → 气泡空着。
     */
    @Test
    fun `message updated alone still yields a renderable text part`() {
        val reducer = EventReducer(NoopFtsIndex)
        val sessionId = "ses_reconnect"
        val messageId = "msg_reconnect"

        reducer.processEvent(
            SseEvent.MessageUpdated(
                info = Message.User(id = messageId, sessionId = sessionId, time = TimeInfo(1_700_000_000_000L)),
                text = text,
            ),
            "server",
        )

        val parts = reducer.parts.value[messageId].orEmpty()
        assertEquals(
            "message.updated 单独到达也必须合成文本 part",
            listOf(text),
            parts.filterIsInstance<Part.Text>().map { it.text },
        )
    }

    /** 已有文本 part（如 session.next.prompted 已建）时不重复补。 */
    @Test
    fun `existing text part is not duplicated by message updated`() {
        val reducer = EventReducer(NoopFtsIndex)
        val sessionId = "ses_both"
        val messageId = "msg_both"

        reducer.processEvent(promptedEvent(messageId, sessionId), "server")
        reducer.processEvent(
            SseEvent.MessageUpdated(
                info = Message.User(id = messageId, sessionId = sessionId, time = TimeInfo(1_700_000_000_000L)),
                text = text,
            ),
            "server",
        )

        val texts = reducer.parts.value[messageId].orEmpty().filterIsInstance<Part.Text>()
        assertEquals(1, texts.size)
    }
}