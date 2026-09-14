/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : HealthStatusTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthStatusTest {
    @Test
    fun `health status preserves authentication and HTTP errors`() {
        assertNull(healthStatusException(200))
        assertTrue(healthStatusException(401) is ServerAuthenticationException)
        assertTrue(healthStatusException(403) is ServerAuthenticationException)
        assertEquals(503, (healthStatusException(503) as ServerHealthHttpException).statusCode)
    }
}
