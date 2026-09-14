/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SessionProjectGroupingTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.sessions

import org.hiylo.starburst.data.repository.DirectoryScope
import org.hiylo.starburst.domain.model.Project
import org.hiylo.starburst.domain.model.Session
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionProjectGroupingTest {
    @Test
    fun groupsByProjectIdBeforeDirectoryFallback() {
        val projects = listOf(
            Project(id = "project", worktree = "/repo", name = "Repository"),
        )
        val item = item("session", "/elsewhere", projectId = "project")

        val group = buildProjectSessionGroups(listOf(item), projects, "/home/user", emptyMap(), "server").single()

        assertEquals("project", group.projectId)
        assertEquals("Repository", group.projectName)
        assertEquals("/repo", group.directory)
    }

    @Test
    fun choosesLongestMatchingWorktreeAndKeepsBranch() {
        val projects = listOf(
            Project(id = "root", worktree = "/repo", name = "Root"),
            Project(id = "nested", worktree = "/repo/apps/mobile", name = "Mobile"),
        )
        val branches = mapOf(DirectoryScope("server", "/repo/apps/mobile") to "feature/context")

        val group = buildProjectSessionGroups(
            listOf(item("session", "/repo/apps/mobile/src")),
            projects,
            null,
            branches,
            "server",
        ).single()

        assertEquals("nested", group.projectId)
        assertEquals("feature/context", group.branch)
    }

    @Test
    fun unknownDirectoriesBecomeIndependentGroupsOrderedByActivity() {
        val groups = buildProjectSessionGroups(
            listOf(
                item("older", "/one", lastUserMessageAt = 1),
                item("newer", "/two", lastUserMessageAt = 2),
            ),
            emptyList(),
            null,
            emptyMap(),
            "server",
        )

        assertEquals(listOf("/two", "/one"), groups.map { it.directory })
    }

    @Test
    fun favoriteSessionsLeadAndRespectExplicitOrder() {
        val sorted = sortSessionItems(
            listOf(
                item("newest", "/repo", lastUserMessageAt = 5),
                item("second-favorite", "/repo", lastUserMessageAt = 4, favoriteIndex = 1),
                item("first-favorite", "/repo", lastUserMessageAt = 1, favoriteIndex = 0),
            )
        )

        assertEquals(listOf("first-favorite", "second-favorite", "newest"), sorted.map { it.session.id })
    }

    @Test
    fun unconfirmedCompletedSessionsArePinnedAboveIdleAndFavorites() {
        val sorted = sortSessionItems(
            listOf(
                item("idle-recent", "/repo", lastUserMessageAt = 10),
                item("favorite", "/repo", lastUserMessageAt = 1, favoriteIndex = 0),
                item("unconfirmed-oldest", "/repo", lastUserMessageAt = 2, isUnconfirmedCompleted = true, unconfirmedCompletedAt = 100),
                item("unconfirmed-newer", "/repo", lastUserMessageAt = 3, isUnconfirmedCompleted = true, unconfirmedCompletedAt = 200),
            )
        )

        assertEquals(
            listOf("unconfirmed-newer", "unconfirmed-oldest", "favorite", "idle-recent"),
            sorted.map { it.session.id },
        )
    }

    @Test
    fun unconfirmedCompletedSessionsOrderAmongThemselvesByFavoriteThenUserMessage() {
        val sorted = sortSessionItems(
            listOf(
                item("unconfirmed-early", "/repo", lastUserMessageAt = 9, isUnconfirmedCompleted = true),
                item("unconfirmed-favorite", "/repo", lastUserMessageAt = 1, favoriteIndex = 0, isUnconfirmedCompleted = true),
                item("unconfirmed-late", "/repo", lastUserMessageAt = 2, isUnconfirmedCompleted = true),
            )
        )

        assertEquals(
            listOf("unconfirmed-favorite", "unconfirmed-early", "unconfirmed-late"),
            sorted.map { it.session.id },
        )
    }

    @Test
    fun sessionOrderByLastUserMessageNotResponseTime() {
        // A session whose AI just responded (updated bumped) but with an old user message
        // must NOT jump above a session with a newer user message.
        val sorted = sortSessionItems(
            listOf(
                item("old-user-new-response", "/repo", lastUserMessageAt = 5, sessionUpdated = 999),
                item("new-user", "/repo", lastUserMessageAt = 6, sessionUpdated = 6),
            )
        )

        assertEquals(listOf("new-user", "old-user-new-response"), sorted.map { it.session.id })
    }

    @Test
    fun projectContainingUnconfirmedSessionLeadsOtherProjects() {
        val groups = buildProjectSessionGroups(
            listOf(
                item("idle-recent", "/recent", lastUserMessageAt = 10),
                item("unconfirmed", "/active", lastUserMessageAt = 1, isUnconfirmedCompleted = true),
            ),
            emptyList(),
            null,
            emptyMap(),
            "server",
        )

        assertEquals(listOf("/active", "/recent"), groups.map { it.directory })
    }

    @Test
    fun projectContainingTopFavoriteLeadsNewerProjects() {
        val groups = buildProjectSessionGroups(
            listOf(
                item("recent", "/recent", updated = 10),
                item("favorite", "/older", updated = 1, favoriteIndex = 0),
            ),
            emptyList(),
            null,
            emptyMap(),
            "server",
        )

        assertEquals(listOf("/older", "/recent"), groups.map { it.directory })
    }

    @Test
    fun recentDirectoriesKeepTwentyNewestUniqueLocations() {
        val sessions = (1L..21L).map { updated ->
            item("session-$updated", "/repo-$updated", updated = updated)
        }

        val directories = recentSessionDirectories(sessions)

        assertEquals(20, directories.size)
        assertEquals("/repo-21", directories.first().directory)
        assertEquals("/repo-2", directories.last().directory)
    }

    @Test
    fun recentDirectoriesRespectConfiguredLimit() {
        val sessions = (1L..10L).map { updated ->
            item("session-$updated", "/repo-$updated", updated = updated)
        }

        val directories = recentSessionDirectories(sessions, limit = 5)

        assertEquals(listOf(10L, 9L, 8L, 7L, 6L), directories.map { it.lastUsed })
    }

    @Test
    fun recentDirectoriesGroupTrailingSlashesAndUseLatestActivity() {
        val directories = recentSessionDirectories(
            listOf(
                item("first", "/repo", updated = 1),
                item("second", "/repo/", updated = 3),
                item("other", "/other", updated = 2),
            )
        )

        assertEquals(listOf("/repo", "/other"), directories.map { it.directory.trimEnd('/') })
        assertEquals(2, directories.first().count)
        assertEquals(3, directories.first().lastUsed)
    }

    private fun item(
        id: String,
        directory: String,
        projectId: String = "",
        updated: Long = 1,
        sessionUpdated: Long = updated,
        favoriteIndex: Int? = null,
        isUnconfirmedCompleted: Boolean = false,
        unconfirmedCompletedAt: Long = 0L,
        lastUserMessageAt: Long = 0L,
    ) = SessionItem(
        Session(
            id = id,
            projectId = projectId,
            directory = directory,
            time = Session.Time(created = 1, updated = sessionUpdated),
        ),
        favoriteIndex = favoriteIndex,
        isUnconfirmedCompleted = isUnconfirmedCompleted,
        unconfirmedCompletedAt = unconfirmedCompletedAt,
        lastUserMessageAt = lastUserMessageAt,
    )
}
