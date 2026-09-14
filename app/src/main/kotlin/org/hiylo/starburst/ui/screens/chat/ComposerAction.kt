/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ComposerAction.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

internal enum class ComposerAction {
    SEND,
    STOP,
    DISABLED,
}

internal fun composerAction(
    isBusy: Boolean,
    isSending: Boolean,
    hasDraft: Boolean,
    isShellMode: Boolean,
): ComposerAction {
    if (isSending) return ComposerAction.DISABLED
    if (isBusy && !hasDraft) return ComposerAction.STOP
    if (hasDraft && (!isShellMode || !isBusy)) return ComposerAction.SEND
    return ComposerAction.DISABLED
}
