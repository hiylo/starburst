/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ProviderOauthRequestTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderOauthRequestTest {
    private val json = Json { explicitNulls = false }

    @Test
    fun serializesCodeCallbackWithTypedFields() {
        assertEquals(
            "{\"method\":1,\"code\":\"authorization-code\"}",
            json.encodeToString(ProviderOauthCallbackRequest(method = 1, code = "authorization-code")),
        )
    }

    @Test
    fun omitsCodeForAutomaticCallback() {
        assertEquals(
            "{\"method\":0}",
            json.encodeToString(ProviderOauthCallbackRequest(method = 0)),
        )
    }
}
