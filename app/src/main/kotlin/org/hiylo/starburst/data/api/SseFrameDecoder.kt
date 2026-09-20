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
// 注：上限按「字符数」计（String.length），RAM 里每字符占 2 字节，理论峰值内存约 2×，
// 且逐行存 dataLines + joinToString 会再产生一份拷贝；这量级只有极端附件帧才会触达，
// 正常文本事件远小于此，故保留现有 dataLines 结构、不改成字节流累加器。
internal const val DEFAULT_MAX_SSE_FRAME_SIZE = 16 * 1024 * 1024

internal class SseFrameDecoder(
    private val maxFrameSize: Int = DEFAULT_MAX_SSE_FRAME_SIZE,
) {
    private val dataLines = mutableListOf<String>()
    private var size = 0

    fun accept(line: String): String? {
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(':')) return null

        val separator = line.indexOf(':')
        val field = if (separator >= 0) line.substring(0, separator) else line
        var value = if (separator >= 0) line.substring(separator + 1) else ""
        if (value.startsWith(' ')) value = value.substring(1)

        if (field == "data") {
            val addedSize = value.length + if (dataLines.isEmpty()) 0 else 1
            if (size + addedSize > maxFrameSize) {
                clear()
                throw SseFrameTooLargeException(maxFrameSize)
            }
            dataLines += value
            size += addedSize
        }
        return null
    }

    fun finish(): String? = dispatch()

    private fun dispatch(): String? {
        if (dataLines.isEmpty()) return null
        val data = dataLines.joinToString("\n")
        clear()
        return data
    }

    private fun clear() {
        dataLines.clear()
        size = 0
    }
}

class SseFrameTooLargeException(maxFrameSize: Int) :
    Exception("SSE frame exceeds $maxFrameSize characters")
