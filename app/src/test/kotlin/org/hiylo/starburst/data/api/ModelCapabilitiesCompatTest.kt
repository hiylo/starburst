/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ModelCapabilitiesCompatTest.kt
 * Date : 2026/09/27 10:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归「模型能力徽章在 V2 后端上一个都不显示」。
 *
 * 真实载荷对比（V1 = opencode `/config/providers`、`/provider`；V2 = opencode 与 agent 的
 * `/api/model`、`/api/provider`）：
 *  - 工具调用：V1 `toolcall: bool`；V2 **没有该键**，改用 `tools: bool`
 *  - 附件：V1 `attachment: bool`；V2 **没有该键**，只能从 `input` 模态推导
 *  - `input`/`output`：V1 是对象（取 key）；V2 是数组
 *
 * 此前 `ModelCapabilities` 只声明 V1 的四个布尔键 → V2 模型（agent 全部模型）被一律
 * 判为「不支持工具调用/推理/附件」，模型选择器与模型筛选页不显示任何能力徽章。
 * 本文件的载荷形状取自真实抓包（已剔除密钥等无关字段）。
 */
class ModelCapabilitiesCompatTest {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun v1Shape_toolcallAndAttachmentReadDirectly() {
        val body = """
            {"temperature":true,"reasoning":true,"attachment":true,"toolcall":true,
             "interleaved":true,
             "input":{"text":true,"image":true,"audio":false,"pdf":true,"video":false},
             "output":{"text":true,"image":false}}
        """.trimIndent()

        val caps = json.decodeFromJsonElement<ModelCapabilities>(json.parseToJsonElement(body))

        assertTrue(caps.toolcall)
        assertTrue(caps.attachment)
        assertTrue(caps.reasoning)
        assertTrue(caps.temperature)
        // V1 的 input 是对象 → 模态名取 key
        assertEquals(listOf("text", "image", "audio", "pdf", "video"), modalities(caps.input))
    }

    @Test
    fun v2Shape_toolsMarksToolcall_andImageInputMarksAttachment() {
        // V2 真实形状：没有 toolcall/attachment 键，input/output 是数组
        val body = """{"tools":true,"input":["text","image"],"output":["text"]}"""

        val caps = json.decodeFromJsonElement<ModelCapabilities>(json.parseToJsonElement(body))

        assertTrue("V2 的 tools 应等价于 toolcall", caps.toolcall)
        assertTrue("V2 的 input 含 image 应推出 attachment", caps.attachment)
        assertEquals(listOf("text", "image"), modalities(caps.input))
    }

    @Test
    fun v2Shape_textOnlyModel_isNotAttachmentCapable() {
        val body = """{"tools":true,"input":["text"],"output":["text"]}"""

        val caps = json.decodeFromJsonElement<ModelCapabilities>(json.parseToJsonElement(body))

        assertTrue(caps.toolcall)
        assertFalse("纯文本模型不应被判定为支持附件", caps.attachment)
    }

    @Test
    fun v2Shape_withoutTools_isNotToolcallCapable() {
        val caps = json.decodeFromJsonElement<ModelCapabilities>(json.parseToJsonElement("""{"input":["text"]}"""))
        assertFalse(caps.toolcall)
        assertFalse(caps.attachment)
    }

    @Test
    fun emptyCapabilities_defaultsAllFalse_withoutThrowing() {
        val caps = json.decodeFromJsonElement<ModelCapabilities>(json.parseToJsonElement("""{}"""))
        assertFalse(caps.toolcall)
        assertFalse(caps.attachment)
        assertFalse(caps.reasoning)
    }

    @Test
    fun v1AttachmentFalseButImageInputPresent_stillAttachmentCapable() {
        // 防御：attachment 键缺失/为 false，但 input 含 image → 仍应支持附件。
        val body = """{"toolcall":true,"input":{"text":true,"image":true}}"""
        val caps = json.decodeFromJsonElement<ModelCapabilities>(json.parseToJsonElement(body))
        assertTrue(caps.attachment)
    }
}
