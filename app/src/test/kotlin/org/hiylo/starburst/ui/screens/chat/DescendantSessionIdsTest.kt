/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : DescendantSessionIdsTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.hiylo.starburst.domain.model.Session
import org.junit.Assert.assertEquals
import org.junit.Test

class DescendantSessionIdsTest {

    @Test
    fun includesRootAndRecursiveDescendantsButNotUnrelatedSessions() {
        val sessions = listOf(
            session("root"),
            session("child", "root"),
            session("grandchild", "child"),
            session("unrelated"),
        )

        assertEquals(
            setOf("root", "child", "grandchild"),
            descendantSessionIds(sessions, "root"),
        )
    }

    private fun session(id: String, parentId: String? = null) = Session(
        id = id,
        parentId = parentId,
        title = id,
        time = Session.Time(created = 0, updated = 0),
    )
}
