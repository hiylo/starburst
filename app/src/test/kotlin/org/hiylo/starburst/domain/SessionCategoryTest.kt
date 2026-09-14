/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SessionCategoryTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.domain

import org.hiylo.starburst.domain.model.SessionCategory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionCategoryTest {

    @Test
    fun categoryRoundTripsThroughPersistedJson() {
        val category = SessionCategory(
            id = "category",
            name = "Important work",
            color = "violet",
            icon = "work",
        )

        val restored = Json.decodeFromString<SessionCategory>(Json.encodeToString(category))

        assertEquals(category, restored)
    }
}
