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
    fun `home folding wins over truncation`() {
        // home 下的长路径仍以 ~/ 开头，用户一眼认出是 home 内路径
        val out = SessionPathFormatter.displayForUI("/home/hiylo/a/very/deep/nested/project", "/home/hiylo", 30)
        assertTrue("home 下路径折叠优先：$out", out.startsWith("~/"))
    }

    @Test
    fun `path equal to home is shown as tilde not truncated`() {
        assertEquals("~", SessionPathFormatter.displayForUI("/home/hiylo", "/home/hiylo", 40))
    }

    @Test
    fun `tiny budget degrades to marker only`() {
        assertEquals(SessionPathFormatter.TRUNCATED_PREFIX, SessionPathFormatter.truncateHead("/a/b/c/d", 2))
        assertEquals("/a/b/c/d", SessionPathFormatter.truncateHead("/a/b/c/d", 0))
    }
}