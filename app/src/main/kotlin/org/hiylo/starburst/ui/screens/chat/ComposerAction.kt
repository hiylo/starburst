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
    // STOP 优先：会话一旦进入 Busy 即可中止，避免 isSending 覆盖整个 prompt 流程期间无法打断。
    if (isBusy && !hasDraft) return ComposerAction.STOP
    if (isSending) return ComposerAction.DISABLED
    if (hasDraft && (!isShellMode || !isBusy)) return ComposerAction.SEND
    return ComposerAction.DISABLED
}
