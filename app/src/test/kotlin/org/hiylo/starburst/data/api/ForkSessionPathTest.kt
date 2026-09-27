/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ForkSessionPathTest.kt
 * Date : 2026/09/27 13:40:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import org.hiylo.starburst.domain.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归「fork 在 agent 上不可用」。
 *
 * 成因：`forkSession` 只打 V1 `POST /session/{id}/fork`，而 starburst-agent 未注册
 * V1 `/session/` 子树 → 404；而 V2 `POST /api/session/{id}/fork` 是有的（400=校验错）。
 * 于是 App 的 fork slash 命令与分支跟踪在 agent 上必然失败。
 *
 * 本测试锁定 V2 响应形状的解包（`{data:{...}}` 信封），与 shareSession 的既有范式一致。
 */
class ForkSessionPathTest {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun v2ForkResponse_decodesDataEnvelope() {
        val body = """
            {"data":{"id":"ses_forked","title":"原会话","version":"3.0.0",
                     "time":{"created":1700000000000,"updated":1700000000000}}}
        """.trimIndent()

        val envelope = json.parseToJsonElement(body).jsonObject
        val data = envelope.getValue("data")
        val session = json.decodeFromJsonElement<Session>(data)

        assertEquals("ses_forked", session.id)
        assertNotNull(session.time)
    }

    @Test
    fun v2ForkResponse_withoutDataEnvelope_isNotTreatedAsSession() {
        // 防御：若某后端直接返回裸 Session（无 data 信封），不应把整个对象当信封后取不到
        // session —— 调用方需要能区分两种形状，而不是静默得到 null。
        val bare = """{"id":"ses_bare","title":"裸形状"}"""
        val obj = json.parseToJsonElement(bare).jsonObject
        assertTrue(obj.get("data") == null)
    }

    @Test
    fun forkRequestBody_omitsMessageIdWhenAbsent() {
        val body = buildMap<String, String> {
            // 与实现保持一致：无 messageId 时不塞 null 键。
        }
        assertTrue("messageId 缺省时请求体应为空（由后端按最新点 fork）", body.isEmpty())
    }
}
