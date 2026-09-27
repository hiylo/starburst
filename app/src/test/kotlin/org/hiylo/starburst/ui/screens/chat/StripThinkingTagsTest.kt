/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : StripThinkingTagsTest.kt
 * Date : 2026/09/25 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class StripThinkingTagsTest {

    @Test
    fun `removes qwen think wrapper`() {
        val input = "<think>The user says reply OK</think>OK"
        assertEquals("OK", stripThinkingTags(input))
    }

    @Test
    fun `keeps text before and after wrapper`() {
        val input = "Before <think>hidden</think> After"
        assertEquals("Before  After", stripThinkingTags(input))
    }

    @Test
    fun `removes fim thinking wrapper`() {
        val input = "<|im_start|>thinking reasoning here<|im_end|>answer"
        assertEquals("answer", stripThinkingTags(input))
    }

    @Test
    fun `removes bang thinking wrapper`() {
        val input = "<!thinking>reasoning<!/thinking>answer"
        assertEquals("answer", stripThinkingTags(input))
    }

    @Test
    fun `leaves plain english untouched`() {
        val input = "I am thinking about this response carefully"
        assertEquals(input, stripThinkingTags(input))
    }

    @Test
    fun `blank input stays blank`() {
        assertEquals("", stripThinkingTags(""))
        assertEquals("", stripThinkingTags("   "))
    }
}
