/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : DocumentIntentTest.kt
 * Date : 2026/09/22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 对话式文档生成意图识别的纯函数单测。 */
class DocumentIntentTest {

    @Test
    fun `Ppt keyword maps to pptx`() {
        val intent = detectDocumentIntent("帮我生成一份演讲PPT，主题是季度总结")!!
        assertEquals("pptx", intent.type)
        assertEquals("帮我生成一份演讲PPT，主题是季度总结", intent.prompt)
    }

    @Test
    fun `Slides synonym maps to pptx`() {
        val intent = detectDocumentIntent("做个演示文稿介绍新产品")!!
        assertEquals("pptx", intent.type)
    }

    @Test
    fun `Excel keyword maps to xlsx`() {
        val intent = detectDocumentIntent("生成一个Excel表格，统计本月销售")!!
        assertEquals("xlsx", intent.type)
    }

    @Test
    fun `Table synonym maps to xlsx`() {
        val intent = detectDocumentIntent("帮我做个表格记录采购清单")!!
        assertEquals("xlsx", intent.type)
    }

    @Test
    fun `Word keyword maps to docx`() {
        val intent = detectDocumentIntent("写一份Word文档，描述系统设计")!!
        assertEquals("docx", intent.type)
    }

    @Test
    fun `Report keyword maps to docx`() {
        val intent = detectDocumentIntent("生成一份周报")!!
        assertEquals("docx", intent.type)
    }

    @Test
    fun `English keywords map to docx`() {
        val intent = detectDocumentIntent("create a document about API design")!!
        assertEquals("docx", intent.type)
    }

    @Test
    fun `Action verb missing returns null`() {
        assertNull(detectDocumentIntent("今天天气怎么样"))
    }

    @Test
    fun `Document type missing returns null`() {
        assertNull(detectDocumentIntent("帮我生成一段代码"))
    }

    @Test
    fun `Blank input returns null`() {
        assertNull(detectDocumentIntent("   "))
        assertNull(detectDocumentIntent(""))
    }

    @Test
    fun `Ppt wins over document when both present`() {
        val intent = detectDocumentIntent("生成一份PPT文档！")!!
        assertEquals("pptx", intent.type)
    }
}