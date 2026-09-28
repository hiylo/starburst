/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SessionPathFormatterTest.kt
 * Date : 2026/09/27 02:25:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.sessions

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 回归「会话列表路径与会话中路径不一致」。
 *
 * 会话列表把 `$HOME` 前缀折叠为 `~`，聊天顶栏与置顶弹窗曾直接输出绝对路径；
 * 现三处统一走 [SessionPathFormatter]。
 */
class SessionPathFormatterTest {

    @Test
    fun normalize_stripsTrailingSlashAndBlanksToRoot() {
        assertEquals("/a/b", SessionPathFormatter.normalize("/a/b/"))
        assertEquals("/", SessionPathFormatter.normalize(""))
        assertEquals("/", SessionPathFormatter.normalize("/"))
    }





    @Test
    fun listAndTopBarProduceSameStringForSameInput() {
        val path = "/home/bob/work/starburst"
        // 会话列表与聊天顶栏都走 displayForUI，输出必须一致
        assertEquals(
            SessionPathFormatter.displayForUI(path),
            SessionPathFormatter.displayForUI(path),
        )
        assertEquals("/home/bob/work/starburst", SessionPathFormatter.displayForUI(path))
    }

    /** display 不再做 home 折叠，只规范化（缩短交给 displayForUI 的点点点规则）。 */
    @Test
    fun display_onlyNormalizes() {
        assertEquals("/home/bob/proj", SessionPathFormatter.display("/home/bob/proj/"))
        assertEquals("/a/b", SessionPathFormatter.display("/a/b/"))
        assertEquals("/", SessionPathFormatter.display(""))
    }

    /** 不再产出波浪号：home 下的路径也按完整路径显示。 */
    @Test
    fun displayForUI_neverUsesTilde() {
        assertEquals("/home/bob/proj", SessionPathFormatter.displayForUI("/home/bob/proj", 40))
        assertEquals("/home/bob/proj", SessionPathFormatter.displayForUI("/home/bob/proj"))
    }
}