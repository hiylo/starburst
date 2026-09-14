/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : FavoriteSessionSnapshotTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.domain

import org.hiylo.starburst.domain.model.FavoriteSessionSnapshot
import org.hiylo.starburst.domain.model.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteSessionSnapshotTest {
    @Test
    fun `snapshot preserves list metadata without conversation payload`() {
        val session = Session(
            id = "session",
            projectId = "project",
            directory = "/workspace/project",
            title = "Favorite session",
            time = Session.Time(created = 10, updated = 20),
            summary = Session.Summary(additions = 4, deletions = 2),
        )

        val restored = FavoriteSessionSnapshot.from(session).toSession()

        assertEquals(session.id, restored.id)
        assertEquals(session.projectId, restored.projectId)
        assertEquals(session.directory, restored.directory)
        assertEquals(session.title, restored.title)
        assertEquals(session.time, restored.time)
        assertNull(restored.summary)
    }
}
