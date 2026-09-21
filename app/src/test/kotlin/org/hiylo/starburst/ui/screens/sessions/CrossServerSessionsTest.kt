/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : CrossServerSessionsTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.sessions

import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.domain.model.Session
import org.hiylo.starburst.domain.model.SessionCategory
import org.hiylo.starburst.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CrossServerSessionsTest {
    private val server = ServerConfig(id = "server", url = "https://example.test")
    private val category = SessionCategory(id = "category", name = "Work", color = "blue", icon = "label")

    @Test
    fun `favorites list excludes regular sessions`() {
        val favorite = item("favorite", isFavorite = true, category = null, favoriteIndex = 0)
        val categorized = item("categorized", isFavorite = false, category = category)

        assertEquals(listOf("favorite"), sortCrossServerFavorites(listOf(favorite, categorized)).map { it.session.id })
    }

    @Test
    fun `favorites respect explicit cross server order`() {
        val second = item("second", isFavorite = true, category = category, favoriteIndex = 1)
        val first = item("first", isFavorite = true, category = null, favoriteIndex = 0)

        assertEquals(
            listOf("first", "second"),
            sortCrossServerFavorites(listOf(second, first)).map { it.session.id },
        )
    }

    @Test
    fun `category filter contains only favorites in that category`() {
        val included = item("included", isFavorite = true, category = category, favoriteIndex = 0)
        val uncategorized = item("uncategorized", isFavorite = true, category = null, favoriteIndex = 1)
        val regular = item("regular", isFavorite = false, category = category)

        assertEquals(
            listOf("included"),
            filterCrossServerFavorites(listOf(regular, uncategorized, included), category.id).map { it.session.id },
        )
    }

    @Test
    fun `moving visible favorite preserves disconnected server positions`() {
        assertEquals(
            listOf("server-a:two", "disconnected:hidden", "server-a:one"),
            moveCrossServerFavoriteOrder(
                currentOrder = listOf("server-a:one", "disconnected:hidden", "server-a:two"),
                visibleOrder = listOf("server-a:one", "server-a:two"),
                itemKey = "server-a:one",
                offset = 1,
            ),
        )
    }

    @Test
    fun `moving favorite across multiple positions shifts intervening favorites`() {
        assertEquals(
            listOf("two", "three", "one", "four"),
            moveCrossServerFavoriteOrder(
                currentOrder = listOf("one", "two", "three", "four"),
                visibleOrder = listOf("one", "two", "three", "four"),
                itemKey = "one",
                offset = 2,
            ),
        )
    }

    @Test
    fun `favorite parent shows busy when its child subagent is running`() {
        // 父会话自身空闲，但子会话（subagent）正在运行 → 列表应显示「处理中」而非空闲。
        val parent = Session(
            id = "parent",
            title = "parent",
            directory = "/project/parent",
            time = Session.Time(created = 1, updated = 2),
        )
        val child = Session(
            id = "child",
            title = "child",
            directory = "/project/parent",
            parentId = "parent",
            time = Session.Time(created = 1, updated = 3),
        )
        val source = SourceState(
            servers = listOf(server),
            sessions = listOf(parent, child),
            serverSessions = mapOf(server.id to setOf("parent", "child")),
            statuses = mapOf("child" to SessionStatus.Busy),
            categories = listOf(category),
        )
        val preferences = mapOf(
            server.id to ServerSessionPreferences(
                favoriteIds = listOf("parent"),
                categoryAssignments = mapOf("parent" to category.id),
            ),
        )
        val state = buildCrossServerSessionsState(source, preferences, setOf(server.id), emptyList(), emptyMap())
        val item = state.items.first()
        assertEquals("parent", item.session.id)
        assertEquals(SessionStatus.Busy, item.status)
    }

    @Test
    fun `favorite parent shows idle when no child subagent runs`() {
        val parent = Session(
            id = "parent",
            title = "parent",
            directory = "/project/parent",
            time = Session.Time(created = 1, updated = 2),
        )
        val source = SourceState(
            servers = listOf(server),
            sessions = listOf(parent),
            serverSessions = mapOf(server.id to setOf("parent")),
            statuses = mapOf("parent" to SessionStatus.Idle),
            categories = listOf(category),
        )
        val preferences = mapOf(
            server.id to ServerSessionPreferences(
                favoriteIds = listOf("parent"),
                categoryAssignments = mapOf("parent" to category.id),
            ),
        )
        val state = buildCrossServerSessionsState(source, preferences, setOf(server.id), emptyList(), emptyMap())
        assertEquals(SessionStatus.Idle, state.items.first().status)
    }

    private fun item(
        id: String,
        isFavorite: Boolean,
        category: SessionCategory?,
        favoriteIndex: Int? = null,
    ) = CrossServerSessionItem(
        server = server,
        session = Session(
            id = id,
            title = id,
            directory = "/project/$id",
            time = Session.Time(created = 1, updated = 2),
        ),
        status = SessionStatus.Idle,
        category = category,
        isFavorite = isFavorite,
        favoriteIndex = favoriteIndex,
        isConnected = true,
    )
}
