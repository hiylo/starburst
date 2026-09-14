/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : TerminalPanelHintCoordinator.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import java.util.concurrent.atomic.AtomicBoolean

internal class OncePerProcessHint {
    private val shown = AtomicBoolean(false)

    fun tryShow(): Boolean = shown.compareAndSet(false, true)
}

internal object TerminalPanelHintCoordinator {
    private val hint = OncePerProcessHint()

    fun tryShow(): Boolean = hint.tryShow()
}
