/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SessionPromptabilityTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.hiylo.starburst.domain.model.Session
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPromptabilityTest {
    @Test
    fun rootSessionAcceptsPrompts() {
        assertTrue(sessionAcceptsPrompts(session(parentId = null)))
    }

    @Test
    fun childSessionRejectsPrompts() {
        assertFalse(sessionAcceptsPrompts(session(parentId = "parent")))
    }

    @Test
    fun unknownSessionRejectsPrompts() {
        assertFalse(sessionAcceptsPrompts(null))
    }

    private fun session(parentId: String?) = Session(
        id = "session",
        parentId = parentId,
        time = Session.Time(created = 1, updated = 1),
    )
}
