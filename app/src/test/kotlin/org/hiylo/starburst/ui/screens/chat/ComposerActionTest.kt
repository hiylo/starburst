/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ComposerActionTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerActionTest {
    @Test
    fun busyWithEmptyDraftShowsStop() {
        assertEquals(
            ComposerAction.STOP,
            composerAction(isBusy = true, isSending = false, hasDraft = false, isShellMode = false),
        )
    }

    @Test
    fun busyWithDraftShowsSend() {
        assertEquals(
            ComposerAction.SEND,
            composerAction(isBusy = true, isSending = false, hasDraft = true, isShellMode = false),
        )
    }

    @Test
    fun shellCommandCannotBeSentWhileBusy() {
        assertEquals(
            ComposerAction.DISABLED,
            composerAction(isBusy = true, isSending = false, hasDraft = true, isShellMode = true),
        )
    }

    @Test
    fun sendingWhileBusyWithNoDraftShowsStop() {
        // STOP 优先：会话进入 Busy 即可中止（无论是否在发送中），避免整个生成期间无法打断。
        assertEquals(
            ComposerAction.STOP,
            composerAction(isBusy = true, isSending = true, hasDraft = false, isShellMode = false),
        )
    }
}
