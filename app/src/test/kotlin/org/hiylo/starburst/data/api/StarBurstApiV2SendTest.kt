/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : StarBurstApiV2SendTest.kt
 * Date : 2026/09/23 15:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarBurstApiV2SendTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `buildV2PromptRequest maps text parts and file parts`() {
        val req = buildV2PromptRequest(
            messageId = "msg_x",
            parts = listOf(
                PromptPart(type = "text", text = "第一段"),
                PromptPart(type = "file", path = "/tmp/a.pdf", mime = "application/pdf", filename = "a.pdf"),
                PromptPart(type = "text", text = "第二段"),
            ),
        )
        assertEquals("msg_x", req.id)
        assertEquals("第一段\n第二段", req.prompt.text)
        assertEquals(1, req.prompt.files.size)
        val file = req.prompt.files[0]
        assertEquals("/tmp/a.pdf", file.uri)
        assertEquals("a.pdf", file.name)
        assertEquals("application/pdf", file.description)
        assertEquals("queue", req.delivery)
        assertTrue(req.resume)
        assertTrue(req.prompt.agents.isEmpty())
    }

    @Test
    fun `buildV2PromptRequest falls back to url and skips blank file uris`() {
        val req = buildV2PromptRequest(
            messageId = "msg_y",
            parts = listOf(
                PromptPart(type = "text", text = "hi"),
                PromptPart(type = "file", url = "https://x/y.png", filename = "y.png"),
                PromptPart(type = "file", path = "   "),
                PromptPart(type = "file"),
            ),
        )
        assertEquals("hi", req.prompt.text)
        assertEquals(1, req.prompt.files.size)
        assertEquals("https://x/y.png", req.prompt.files[0].uri)
    }

    @Test
    fun `files only prompt keeps empty text and attaches files`() {
        val req = buildV2PromptRequest(
            messageId = "msg_f",
            parts = listOf(PromptPart(type = "file", path = "/tmp/z.csv", filename = "z.csv")),
        )
        assertEquals("", req.prompt.text)
        assertEquals(1, req.prompt.files.size)
    }

    @Test
    fun `v2 prompt serializes delivery and resume and never modelID`() {
        val encoded = json.encodeToString(
            buildV2PromptRequest("msg_z", listOf(PromptPart(type = "text", text = "hi"))),
        )
        assertTrue(encoded.contains("\"id\":\"msg_z\""))
        assertTrue(encoded.contains("\"text\":\"hi\""))
        assertTrue(encoded.contains("\"delivery\":\"queue\""))
        assertTrue(encoded.contains("\"resume\":true"))
        assertFalse(encoded.contains("modelID"))
    }

    @Test
    fun `model switch body wraps model with id field`() {
        val encoded = json.encodeToString(
            V2ModelSwitchBody(V2ModelRef(providerId = "anthropic", modelId = "claude-x", variant = "high")),
        )
        assertTrue(encoded.contains("\"model\":"))
        assertTrue(encoded.contains("\"id\":\"claude-x\""))
        assertTrue(encoded.contains("\"providerID\":\"anthropic\""))
        assertTrue(encoded.contains("\"variant\":\"high\""))
        assertFalse(encoded.contains("\"modelID\""))
    }

    @Test
    fun `v2 message adapter maps real assistant message shape`() {
        val raw = """
            {"id":"msg_0cd475c47001wK1L1D7HTFo303","time":{"created":1790150401095,"completed":1790150401491},
             "type":"assistant","agent":"build","model":{"id":"sensenova-deepseek-flash","providerID":"litellm","variant":"default"},
             "content":[
               {"type":"reasoning","id":"reasoning-0","text":"The bash tool is being interrupted.","time":{"created":1}},
               {"type":"tool","id":"call_82f8ede825684a9099f45477","name":"read","state":{"status":"pending","input":""},"time":{"created":2}},
               {"type":"text","id":"txt-0","text":"Done."}
             ],
             "snapshot":{"start":"abc"},"finish":"error","error":{"type":"unknown","message":"boom"}}
        """.trimIndent()
        val msg = v2Json.decodeFromString<V2SessionMessage>(raw)
        val mwp = msg.toMessageWithParts("ses_test")
        val assistant = mwp.info as org.hiylo.starburst.domain.model.Message.Assistant
        assertEquals("msg_0cd475c47001wK1L1D7HTFo303", assistant.id)
        assertEquals("sensenova-deepseek-flash", assistant.modelId)
        assertEquals("error", assistant.finish)
        assertEquals("boom", assistant.error?.message)
        assertEquals(3, mwp.parts.size)
        assertTrue(mwp.parts[0] is org.hiylo.starburst.domain.model.Part.Reasoning)
        val tool = mwp.parts[1] as org.hiylo.starburst.domain.model.Part.Tool
        assertEquals("read", tool.tool)
        assertEquals("call_82f8ede825684a9099f45477", tool.callId)
        assertTrue(tool.state is org.hiylo.starburst.domain.model.ToolState.Pending)
        val text = mwp.parts[2] as org.hiylo.starburst.domain.model.Part.Text
        assertEquals("Done.", text.text)
    }
}