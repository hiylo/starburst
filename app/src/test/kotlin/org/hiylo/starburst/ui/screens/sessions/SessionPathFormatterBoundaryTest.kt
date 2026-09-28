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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归：路径折叠曾把聊天顶栏的路径缩成一个 `~`。
 *
 * 根因不在折叠逻辑，而在喂给它的 home —— getServerPaths 从 V2 /api/location 拿不到
 * home 时，用 directory 顶替，于是「目录 == home」成立，整条路径被折成 `~`。
 * starburst-agent 的 /api/location 确实只有 directory/project，没有 home。
 */
class SessionPathFormatterBoundaryTest {




    /** 关键回归：工作区目录不在 home 下时不能被折成 ~（此前因 home 被赋成 directory）。 */

    /** 前缀边界：/home/hiylo-backup 不是 /home/hiylo 的子目录，不能折。 */


    @Test
    fun `normalize strips trailing slashes and maps empty to root`() {
        assertEquals("/", SessionPathFormatter.normalize(""))
        assertEquals("/", SessionPathFormatter.normalize("/"))
        assertEquals("/a/b", SessionPathFormatter.normalize("/a/b/"))
    }

    // ---- 截断规则：保留尾部 + `…/` 明确标记，不造成误解 ----

    @Test
    fun `short path is not truncated`() {
        val p = "/vol1/projects/app"
        assertEquals(p, SessionPathFormatter.truncateHead(p, 40))
    }

    @Test
    fun `long path keeps the distinguishing tail and is marked as truncated`() {
        val p = "/vol1/docker/starburst-agent/workspace"
        val out = SessionPathFormatter.truncateHead(p, 30)
        assertTrue("超长路径应以 …/ 开头表示已省略：$out", out.startsWith(SessionPathFormatter.TRUNCATED_PREFIX))
        assertTrue("应保留末级目录 workspace：$out", out.endsWith("workspace"))
        assertTrue("总长应不超过预算：$out", out.length <= 30)
    }

    @Test
    fun `truncation breaks at a directory boundary not mid name`() {
        val p = "/vol1/docker/starburst-agent/workspace"
        val out = SessionPathFormatter.truncateHead(p, 26)
        // 目录名不能被从中间劈开（如 works…/x）
        assertTrue("不应出现被截断的目录名：$out", out == SessionPathFormatter.TRUNCATED_PREFIX || out.removePrefix(SessionPathFormatter.TRUNCATED_PREFIX).none { it == '.' })
    }

    /** 关键：截断结果不能长得像真实路径，否则用户会以为根目录就在省略处。 */
    @Test
    fun `truncated path is never mistakable for a real path`() {
        val out = SessionPathFormatter.truncateHead("/vol1/docker/starburst-agent/workspace", 30)
        assertFalse("不应以 / 开头（那会像绝对路径）：$out", out.startsWith("/"))
        assertFalse("不应是 ~ 简写：$out", out == "~" || out.startsWith("~/"))
    }



    @Test
    fun `tiny budget degrades to marker only`() {
        assertEquals(SessionPathFormatter.TRUNCATED_PREFIX, SessionPathFormatter.truncateHead("/a/b/c/d", 2))
        assertEquals("/a/b/c/d", SessionPathFormatter.truncateHead("/a/b/c/d", 0))
    }

    // ---- 不再使用 ~ 折叠（用户明确要求），只用点点点表示缩短 ----

    /** 核心诉求：任何情况下都不出现波浪号。 */
    @Test
    fun `no path is ever displayed with a tilde`() {
        val paths = listOf(
            "/home/hiylo",
            "/home/hiylo/projects/app",
            "/vol1/docker/starburst-agent/workspace",
            "/vol1/1000/WorkSpaces",
        )
        for (p in paths) {
            for (max in listOf(8, 20, 40, 200)) {
                val out = SessionPathFormatter.displayForUI(p, max)
                assertFalse("不应出现波浪号（$p, max=$max）-> $out", out.contains("~"))
            }
        }
    }

    /** home 下的路径也不再折成 ~/... —— 明确按完整路径显示，除非过长。 */
    @Test
    fun `path under home is shown in full when short enough`() {
        assertEquals(
            "/home/hiylo/projects/app",
            SessionPathFormatter.displayForUI("/home/hiylo/projects/app", 40),
        )
    }

    /** 过长时用点点点缩短，且保留末级目录。 */
    @Test
    fun `long home path is shortened with dots not tilde`() {
        val out = SessionPathFormatter.displayForUI("/home/hiylo/a/very/deep/nested/project", 24)
        assertTrue("应以 .../ 开头：$out", out.startsWith(".../"))
        assertTrue("应保留末级目录 project：$out", out.endsWith("project"))
        assertFalse("不应含波浪号：$out", out.contains("~"))
        assertTrue("总长应不超过预算：$out", out.length <= 24)
    }

    @Test
    fun `short path is displayed verbatim`() {
        val p = "/vol1/projects/app"
        assertEquals(p, SessionPathFormatter.displayForUI(p))
        assertEquals(p, SessionPathFormatter.displayForUI(p, 40))
    }
}