/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SessionPathFormatter.kt
 * Date : 2026/09/27 02:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.sessions

/**
 * 会话目录路径的统一展示格式化。
 *
 * 此前会话列表（[buildProjectSessionGroups] 内的局部 `displayPath`）会把 `$HOME` 前缀
 * 折叠成 `~`，而聊天顶栏（`ChatScreenTopBar`）与置顶排序弹窗
 * （`SessionListSessionComponents`）直接输出绝对路径——同一目录在两处显示不同，
 * 长绝对路径还会被 `maxLines=1 + Ellipsis` 裁掉尾部目录（恰是最有辨识度的部分）。
 *
 * 抽出为共享函数，让三处走同一规则。
 */
object SessionPathFormatter {

    /** 去掉尾部斜杠；空串归一为根目录 `/`。 */
    fun normalize(path: String): String = path.trimEnd('/').ifEmpty { "/" }

    /**
     * 展示用路径：仅规范化（去尾部斜杠、空串归一为根目录 `/`），**不做任何缩短**。
     *
     * 此前这里会把 `$HOME/x` 折成 `~/x`。已去掉：`~` 在 shell 里是 home 的简写，
     * 但这里显示的是**项目/会话目录**，绝大多数并不在 home 下（实测 home=/home/hiylo，
     * 会话目录全在 /vol1/...），于是一个 `~` 会被读成「这是 home 下的路径」——
     * 与事实不符，正是要避免的误解。缩短统一交给 [truncateHead] 用点点点表示。
     */
    fun display(path: String): String = normalize(path)

    /**
     * 缩短标记前缀：三个点 + 斜杠。以它开头即表示「前面已省略」。
     * 刻意不用 `~`（那是 home 的约定，这里省略的可能是任意前缀）或 `…`（单字符，
     * 部分字体渲染宽度不一致），`.../` 在任何字体下都稳定且不会被误认成真实路径。
     */
    const val TRUNCATED_PREFIX: String = ".../"

    /**
     * 超长路径的展示：**从头部截断、保留尾部**，并加 [TRUNCATED_PREFIX] 前缀。
     *
     * 为什么不用 Compose 的 `maxLines=1 + TextOverflow.Ellipsis`：那是从**尾部**截，
     * 砍掉的恰是末级目录（`.../starburst-agent/workspace` 会变成 `.../docker/starbu`），
     * 而末级目录才是区分不同会话的关键信息；并且同行的 token/花费会被整段挤掉。
     *
     * 为什么加 `…/` 前缀：截断后的字符串若不加标记，`docker/starburst-agent/workspace`
     * 看起来就像一个真实路径，会造成误解（用户会以为项目根目录就在 `docker`）。
     * 以 `…/` 开头明确表示「这是省略后的尾部」，不会被误认为完整路径。
     *
     * 尽量在目录分隔符处断开，避免把一个目录名从中间劈开（如 `works…/x`）。
     *
     * @param maxChars 允许的最大字符数（含前缀与分隔符）
     */
    fun truncateHead(text: String, maxChars: Int): String {
        if (maxChars <= 0) return text
        if (text.length <= maxChars) return text
        val budget = maxChars - TRUNCATED_PREFIX.length
        if (budget <= 0) return TRUNCATED_PREFIX
        val keep = text.takeLast(budget)
        // 尽量退到上一个目录分隔符之后，避免劈开目录名
        val cut = keep.indexOf('/')
        val tail = if (cut >= 0 && cut < keep.lastIndex) keep.substring(cut + 1) else keep
        return if (tail.isEmpty()) TRUNCATED_PREFIX else TRUNCATED_PREFIX + tail
    }

    /**
     * 一步得到可展示的路径：规范化（[display]）后按长度缩短（[truncateHead]）。
     * 只有超出 [maxChars] 才缩短，短路径一律原样显示 —— 不做无必要的缩写。
     */
    fun displayForUI(path: String, maxChars: Int = DEFAULT_MAX_CHARS): String =
        truncateHead(display(path), maxChars)

    /** 顶栏等窄控件的默认预算：够放下多数项目路径，又为同行的 token/花费留出余量。 */
    const val DEFAULT_MAX_CHARS: Int = 40
}
