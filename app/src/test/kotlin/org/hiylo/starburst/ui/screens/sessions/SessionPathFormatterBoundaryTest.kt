/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SessionPathFormatterBoundaryTest.kt
 * Date : 2026/09/27 05:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.sessions

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 回归：路径折叠曾把聊天顶栏的路径缩成一个 `~`。
 *
 * 根因不在折叠逻辑，而在喂给它的 home —— getServerPaths 从 V2 /api/location 拿不到
 * home 时，用 directory 顶替，于是「目录 == home」成立，整条路径被折成 `~`。
 * starburst-agent 的 /api/location 确实只有 directory/project，没有 home。
 */
class SessionPathFormatterBoundaryTest {

    @Test
    fun `home blank means no folding - full path is shown`() {
        val dir = "/vol1/docker/starburst-agent/workspace"
        // home 取不到 → 不折叠（信息多于错误的简写）
        assertEquals(dir, SessionPathFormatter.display(dir, null))
        assertEquals(dir, SessionPathFormatter.display(dir, ""))
    }

    @Test
    fun `directory under real home folds to tilde`() {
        assertEquals(
            "~/projects/app",
            SessionPathFormatter.display("/home/hiylo/projects/app", "/home/hiylo"),
        )
    }

    @Test
    fun `directory equal to home keeps the tilde`() {
        assertEquals("~", SessionPathFormatter.display("/home/hiylo", "/home/hiylo"))
    }

    /** 关键回归：工作区目录不在 home 下时不能被折成 ~（此前因 home 被赋成 directory）。 */
    @Test
    fun `workspace outside home is not folded`() {
        val dir = "/vol1/docker/starburst-agent/workspace"
        assertEquals(dir, SessionPathFormatter.display(dir, "/home/hiylo"))
    }

    /** 前缀边界：/home/hiylo-backup 不是 /home/hiylo 的子目录，不能折。 */
    @Test
    fun `sibling directory sharing name prefix is not folded`() {
        val dir = "/home/hiylo-backup/secret"
        assertEquals(dir, SessionPathFormatter.display(dir, "/home/hiylo"))
    }

    @Test
    fun `trailing slash on home does not break folding`() {
        assertEquals(
            "~/projects",
            SessionPathFormatter.display("/home/hiylo/projects", "/home/hiylo/"),
        )
    }

    @Test
    fun `normalize strips trailing slashes and maps empty to root`() {
        assertEquals("/", SessionPathFormatter.normalize(""))
        assertEquals("/", SessionPathFormatter.normalize("/"))
        assertEquals("/a/b", SessionPathFormatter.normalize("/a/b/"))
    }
}
