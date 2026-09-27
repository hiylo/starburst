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
}
