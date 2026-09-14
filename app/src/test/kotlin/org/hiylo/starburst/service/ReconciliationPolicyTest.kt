/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ReconciliationPolicyTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.service

import org.hiylo.starburst.domain.model.Session
import org.junit.Assert.assertEquals
import org.junit.Test

class ReconciliationPolicyTest {
    @Test
    fun `message reconciliation keeps only newest changed sessions`() {
        val remote = (1L..25L).map { updated -> session("session-$updated", updated) }

        val selected = sessionsNeedingMessageReconciliation(emptyMap(), remote)

        assertEquals(20, selected.size)
        assertEquals((25L downTo 6L).toList(), selected.map { it.time.updated })
    }

    @Test
    fun `message reconciliation skips unchanged sessions`() {
        val local = session("same", updated = 10)
        val changed = session("changed", updated = 11)

        val selected = sessionsNeedingMessageReconciliation(
            localSessions = mapOf(local.id to local, changed.id to changed.copy(time = Session.Time(1, 10))),
            remoteSessions = listOf(local, changed),
        )

        assertEquals(listOf("changed"), selected.map(Session::id))
    }

    private fun session(id: String, updated: Long) = Session(
        id = id,
        time = Session.Time(created = 1, updated = updated),
    )
}
