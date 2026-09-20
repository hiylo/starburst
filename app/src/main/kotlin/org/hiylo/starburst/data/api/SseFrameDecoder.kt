/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SseFrameDecoder.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

// 与后端 StreamEvents 的 maxSSEEventSize（16MiB）对齐：附件 data URL 单帧可能达到
// 10~13MB，默认 1MiB 会把大 patch/附件整帧丢弃，App 只能靠轮询补齐——弱网正反馈。
// 单帧改用单个 StringBuilder 累积（不再逐行存 dataLines），消除 joinToString 的整帧拷贝；
// 上限按 UTF-8 字节计（utf8Size），与后端 maxSSEEventSize 口径一致。RAM 内 String 仍是
// UTF-16（每字符 2 字节），极端附件帧峰值约 2×，正常文本事件远小于此，可接受。
internal const val DEFAULT_MAX_SSE_FRAME_SIZE = 16 * 1024 * 1024

internal class SseFrameDecoder(
    private val maxFrameSize: Int = DEFAULT_MAX_SSE_FRAME_SIZE,
) {
    private val data = StringBuilder()
    private var size = 0
    private var hasDataLine = false

    fun accept(line: String): String? {
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(':')) return null

        val separator = line.indexOf(':')
        val field = if (separator >= 0) line.substring(0, separator) else line
        var value = if (separator >= 0) line.substring(separator + 1) else ""
        if (value.startsWith(' ')) value = value.substring(1)

        if (field == "data") {
            val addedSize = value.utf8Size() + if (hasDataLine) 1 else 0
            if (size + addedSize > maxFrameSize) {
                clear()
                throw SseFrameTooLargeException(maxFrameSize)
            }
            if (hasDataLine) data.append('\n')
            data.append(value)
            size += addedSize
            hasDataLine = true
        }
        return null
    }

    fun finish(): String? = dispatch()

    private fun dispatch(): String? {
        if (!hasDataLine) return null
        val text = data.toString()
        clear()
        return text
    }

    private fun clear() {
        data.setLength(0)
        size = 0
        hasDataLine = false
    }
}

class SseFrameTooLargeException(maxFrameSize: Int) :
    Exception("SSE frame exceeds $maxFrameSize bytes")
