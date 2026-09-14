/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ToolStateAttachmentTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolStateAttachmentTest {

    @Test
    fun parsesOfficialFilePartAttachmentShape() {
        val state = Json { ignoreUnknownKeys = true }.decodeFromString<ToolState>(
            """{
                "status":"completed",
                "input":{},
                "output":"done",
                "attachments":[{
                    "id":"prt_1",
                    "sessionID":"ses_1",
                    "messageID":"msg_1",
                    "type":"file",
                    "mime":"image/png",
                    "filename":"result.png",
                    "url":"data:image/png;base64,abc"
                }]
            }""".trimIndent(),
        ) as ToolState.Completed

        assertEquals("prt_1", state.attachments?.single()?.id)
        assertEquals("image/png", state.attachments?.single()?.mime)
        assertEquals("data:image/png;base64,abc", state.attachments?.single()?.url)
    }
}
