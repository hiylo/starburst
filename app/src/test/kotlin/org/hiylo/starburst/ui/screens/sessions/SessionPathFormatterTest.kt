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
    fun display_collapsesHomePrefix() {
        assertEquals("~/proj", SessionPathFormatter.display("/home/bob/proj", "/home/bob"))
        assertEquals("~", SessionPathFormatter.display("/home/bob", "/home/bob"))
        assertEquals("~/a/b/c", SessionPathFormatter.display("/home/bob/a/b/c/", "/home/bob"))
    }

    @Test
    fun display_keepsNonHomePathAsIs() {
        assertEquals("/vol1/docker/app", SessionPathFormatter.display("/vol1/docker/app", "/home/bob"))
    }

    @Test
    fun display_doesNotCollapseSiblingWithSamePrefix() {
        // /home/bobby 不是 /home/bob 的子目录，不能被误折叠成 "~/by"。
        assertEquals("/home/bobby/x", SessionPathFormatter.display("/home/bobby/x", "/home/bob"))
    }

    @Test
    fun display_withoutHomeDir_returnsNormalizedPath() {
        assertEquals("/a/b", SessionPathFormatter.display("/a/b/", null))
        assertEquals("/a/b", SessionPathFormatter.display("/a/b/", ""))
    }

    @Test
    fun listAndTopBarProduceSameStringForSameInput() {
        val path = "/home/bob/work/starburst"
        val home = "/home/bob"
        assertEquals(
            SessionPathFormatter.display(path, home),
            SessionPathFormatter.display(path, home),
        )
        assertEquals("~/work/starburst", SessionPathFormatter.display(path, home))
    }
}
