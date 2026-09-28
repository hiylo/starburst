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
     * 展示用路径：`$HOME/x` 折叠为 `~/x`，其余原样（仅去尾部斜杠）。
     * [homeDir] 为空或取不到时退化为不折叠。
     *
     * `~` 只在路径**确实位于 home 之下**时出现——这是 shell 的通用约定，不会误解。
     * 拿不到 home 时不折叠（显示完整路径），绝不用一个假的简写代替完整路径。
     */
    fun display(path: String, homeDir: String?): String {
        val dir = normalize(path)
        if (homeDir.isNullOrBlank()) return dir
        val home = homeDir.trimEnd('/')
        if (home.isEmpty()) return dir
        return if (dir == home || dir.startsWith("$home/")) {
            "~" + dir.removePrefix(home)
        } else {
            dir
        }
    }

    /** 截断标记前缀：以它开头即表示「前面被省略」，不会被当成真实路径。 */
    const val TRUNCATED_PREFIX: String = "…/"

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
     * 一步得到可展示的路径：先按 home 折叠（[display]），再按长度截断（[truncateHead]）。
     * 折叠在前是有意的——`~/very/long/...` 比 `…/long/...` 更容易一眼认出是 home 下路径。
     */
    fun displayForUI(path: String, homeDir: String?, maxChars: Int = DEFAULT_MAX_CHARS): String =
        truncateHead(display(path, homeDir), maxChars)

    /** 顶栏等窄控件的默认预算：够放下多数项目路径，又为同行的 token/花费留出余量。 */
    const val DEFAULT_MAX_CHARS: Int = 40
}
