/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SessionPatchBodyTest.kt
 * Date : 2026/09/25 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPatchBodyTest {

    private val json = Json { encodeDefaults = true; explicitNulls = false }

    @Test
    fun `title-only body serializes with title present`() {
        val body = SessionPatchBody(title = "讲一下递归的缺点")
        val encoded = json.encodeToString(SessionPatchBody.serializer(), body)
        assertTrue("title 必须在 body 中", encoded.contains("\"title\":\"讲一下递归的缺点\""))
        assertFalse("未归档时不应带 time", encoded.contains("\"time\""))
    }

    @Test
    fun `archive body carries millis timestamp`() {
        val body = SessionPatchBody(title = null, time = buildJsonObject {
            put("archived", JsonPrimitive(123456789L))
        })
        val encoded = json.encodeToString(SessionPatchBody.serializer(), body)
        assertTrue(encoded.contains("\"archived\":123456789"))
    }

    @Test
    fun `unarchive body carries explicit null`() {
        val body = SessionPatchBody(title = null, time = buildJsonObject {
            put("archived", JsonNull)
        })
        val encoded = json.encodeToString(SessionPatchBody.serializer(), body)
        // explicitNulls=false 下 JsonNull 是值、不会被省略。
        assertTrue("取消归档必须显式带 null", encoded.contains("\"archived\":null"))
    }

    @Test
    fun `blank title stays present in data class (filtering happens in updateSession)`() {
        val body = SessionPatchBody(title = "   ")
        val encoded = json.encodeToString(SessionPatchBody.serializer(), body)
        assertTrue("数据类原样序列化，blank 过滤在 updateSession 的 takeIf", encoded.contains("\"title\":\"   \""))
    }
}
