/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatInputBarModelLabelTest.kt
 * Date : 2026/09/20 11:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Rule
import org.junit.Test

class ChatInputBarModelLabelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun launchInputBar(modelLabel: String) {
        composeRule.setContent {
            MaterialTheme {
                ChatInputBar(
                    textFieldValue = TextFieldValue(""),
                    onTextFieldValueChange = {},
                    onSend = {},
                    onStop = {},
                    isSending = false,
                    modelLabel = modelLabel,
                    effectiveContextWindow = 1_000_000,
                    estimatedContextTokens = 45_900,
                )
            }
        }
    }

    @Test
    fun longModelLabelIsTruncatedWithEllipsis() {
        val full = "x".repeat(30)
        launchInputBar(full)

        composeRule.onNodeWithText("x".repeat(MODEL_LABEL_MAX_CHARS) + "…", substring = false)
            .assertExists()
        composeRule.onNodeWithText(full, substring = false)
            .assertDoesNotExist()
    }

    @Test
    fun shortModelLabelIsRenderedUnchanged() {
        launchInputBar("gpt-5")

        composeRule.onNodeWithText("gpt-5", substring = false).assertExists()
    }
}
