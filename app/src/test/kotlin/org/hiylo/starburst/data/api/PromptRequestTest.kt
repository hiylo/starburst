/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : PromptRequestTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptRequestTest {

    @Test
    fun serializesCorrelatedMessageId() {
        val encoded = Json.encodeToString(
            PromptRequest(
                messageId = "msg_000000000001abcdefghijklmn",
                parts = listOf(PromptPart(type = "text", text = "hello")),
            ),
        )

        assertTrue(encoded.contains("\"messageID\":\"msg_000000000001abcdefghijklmn\""))
    }
}
