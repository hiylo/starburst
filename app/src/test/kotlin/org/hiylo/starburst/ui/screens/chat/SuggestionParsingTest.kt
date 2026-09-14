/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SuggestionParsingTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionParsingTest {
    @Test
    fun parsesPlainJsonArray() {
        assertEquals(
            listOf("a", "b", "c"),
            parseSuggestionList("""["a", "b", "c"]"""),
        )
    }

    @Test
    fun parsesJsonInsideFence() {
        assertEquals(
            listOf("a", "b", "c"),
            parseSuggestionList("```json\n[\"a\", \"b\", \"c\"]\n```"),
        )
    }

    @Test
    fun parsesWithSurroundingProse() {
        assertEquals(
            listOf("a", "b", "c"),
            parseSuggestionList("Here are suggestions:\n[\"a\", \"b\", \"c\"]\nHope that helps!"),
        )
    }

    @Test
    fun trimsItemsAndDropsBlank() {
        assertEquals(
            listOf("a", "c"),
            parseSuggestionList("""["  a  ", "", "c"]"""),
        )
    }

    @Test
    fun capsAtThree() {
        val result = parseSuggestionList("""["a", "b", "c", "d"]""")
        assertEquals(3, result.size)
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun emptyForBlankInput() {
        assertTrue(parseSuggestionList(null).isEmpty())
        assertTrue(parseSuggestionList("").isEmpty())
        assertTrue(parseSuggestionList("no array here").isEmpty())
    }
}
