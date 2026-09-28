/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : McpStatusParsingTest.kt
 * Date : 2026/09/28 03:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归：`GET /mcp` 的响应形状在两边不一致，直接按 `Map<String, McpStatus>` 反序列化
 * 遇到 starburst-agent 的 `{"servers":[...]}` 会抛类型不匹配（数组塞进对象字段），
 * MCP 屏整页报错或空白。
 */
class McpStatusParsingTest {

    /** 线上实测形状：starburst-agent。 */
    @Test
    fun `parses agent servers array shape`() {
        val raw = """{"servers":[{"name":"workspace","status":"connected"},{"name":"projects","status":"error","error":"boom"}]}"""
        val m = parseMcpStatusMap(raw)
        assertEquals(2, m.size)
        assertEquals("connected", m["workspace"]?.status)
        assertEquals("error", m["projects"]?.status)
        assertEquals("boom", m["projects"]?.error)
    }

    /** 另一种在用形状：键即服务器名的扁平 map。 */
    @Test
    fun `parses flat keyed map shape`() {
        val raw = """{"workspace":{"status":"connected"},"projects":{"status":"disabled"}}"""
        val m = parseMcpStatusMap(raw)
        assertEquals(2, m.size)
        assertEquals("connected", m["workspace"]?.status)
        assertEquals("disabled", m["projects"]?.status)
    }

    /** 容忍与本仓其他端点一致的 {location, data:[...]} 信封。 */
    @Test
    fun `parses data envelope shape`() {
        val raw = """{"location":{"directory":"/x"},"data":[{"name":"workspace","status":"connected"}]}"""
        val m = parseMcpStatusMap(raw)
        assertEquals(1, m.size)
        assertEquals("connected", m["workspace"]?.status)
    }

    @Test
    fun `empty and malformed inputs yield empty map without throwing`() {
        assertTrue(parseMcpStatusMap("""{"servers":[]}""").isEmpty())
        assertTrue(parseMcpStatusMap("{}").isEmpty())
        assertTrue(parseMcpStatusMap("not json").isEmpty())
        assertTrue(parseMcpStatusMap("[]").isEmpty())
    }

    /** 单个畸形条目不应打挂整页：好的条目仍要解析出来。 */
    @Test
    fun `malformed entry does not break the page`() {
        val raw = """{"servers":[{"name":"good","status":"connected"},{"status":"orphan"},{"name":"bad","status":5}]}"""
        val m = parseMcpStatusMap(raw)
        assertEquals("connected", m["good"]?.status)
        // status 非字符串的条目被丢弃，但不影响其它条目
        assertTrue(m["bad"] == null)
    }
}
