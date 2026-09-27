/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : StarBurstGatewayTest.kt
 * Date : 2026/09/24 09:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * StarBurstGateway.resolve 解析规则回归：
 *  - 同主机后端（starburst-agent：server 与 backend 同一 URL）→ 直连带 Bearer，不受版本门槛约束；
 *  - 独立后端镜像 → 版本 ≥ BACKEND_MIN_VERSION 才加 /api/opencode 前缀 + Bearer；
 *  - 否则保持直连。
 */
class StarBurstGatewayTest {

    private fun direct(url: String = "http://192.0.2.150:4096") =
        ServerConnection(baseUrl = url, authHeader = null)

    private fun status(url: String, version: String, available: Boolean = true) = BackendStatus(
        backendUrl = url,
        backendToken = "ocb_placeholder_test",
        backendAvailable = available,
        backendVersion = version,
    )

    @Test
    fun sameHostBackendUsesDirectBearerRegardlessOfVersion() {
        val conn = direct("http://192.0.2.150:18880")
        val resolved = StarBurstGateway.resolve(conn, status("http://192.0.2.150:18880", "0.1.1"))

        assertEquals("http://192.0.2.150:18880", resolved.baseUrl)
        assertEquals("Bearer ocb_placeholder_test", resolved.authHeader)
    }

    @Test
    fun sameHostBackendMatchesAfterTrailingSlashTrim() {
        val conn = direct("http://192.0.2.150:18880/")
        val resolved = StarBurstGateway.resolve(conn, status("http://192.0.2.150:18880", "0.1.1"))

        assertEquals("Bearer ocb_placeholder_test", resolved.authHeader)
    }

    @Test
    fun lowVersionIndependentBackendStaysDirect() {
        val conn = direct("http://192.0.2.150:4096")
        val resolved = StarBurstGateway.resolve(conn, status("http://192.0.2.150:18880", "0.1.1"))

        assertEquals("http://192.0.2.150:4096", resolved.baseUrl)
        assertNull(resolved.authHeader)
    }

    @Test
    fun highVersionIndependentBackendUsesMirror() {
        val conn = direct("http://192.0.2.150:4096")
        val resolved = StarBurstGateway.resolve(conn, status("http://192.0.2.150:18880", "1.5.0"))

        assertEquals("http://192.0.2.150:18880/api/opencode", resolved.baseUrl)
        assertEquals("Bearer ocb_placeholder_test", resolved.authHeader)
    }

    @Test
    fun unavailableBackendStaysDirect() {
        val conn = direct("http://192.0.2.150:18880")
        val resolved = StarBurstGateway.resolve(conn, status("http://192.0.2.150:18880", "1.5.0", available = false))

        assertEquals("http://192.0.2.150:18880", resolved.baseUrl)
        assertNull(resolved.authHeader)
    }

    @Test
    fun applySelfBackendBearerAddsBearerOnSameHost() {
        val conn = direct("http://192.0.2.150:18880")
        val resolved = StarBurstGateway.applySelfBackendBearer(conn, "http://192.0.2.150:18880", "ocb_placeholder_x")

        assertEquals("Bearer ocb_placeholder_x", resolved.authHeader)
        assertEquals("http://192.0.2.150:18880", resolved.baseUrl)
    }

    @Test
    fun applySelfBackendBearerLeavesDifferentHostUntouched() {
        val conn = direct("http://192.0.2.150:4096")
        val resolved = StarBurstGateway.applySelfBackendBearer(conn, "http://192.0.2.150:18880", "ocb_placeholder_x")

        assertNull(resolved.authHeader)
    }

    @Test
    fun applySelfBackendBearerNoopWithoutToken() {
        val conn = direct("http://192.0.2.150:18880")
        val resolved = StarBurstGateway.applySelfBackendBearer(conn, "http://192.0.2.150:18880", "  ")

        assertNull(resolved.authHeader)
    }
}
