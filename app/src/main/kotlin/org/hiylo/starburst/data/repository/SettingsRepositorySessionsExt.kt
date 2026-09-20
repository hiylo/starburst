/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SettingsRepositorySessionsExt.kt
 * Date : 2026/09/20 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.repository

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import org.hiylo.starburst.domain.model.FavoriteSessionSnapshot
import org.hiylo.starburst.domain.model.SessionCategory
import kotlinx.serialization.encodeToString

/**
 * SettingsRepository 的会话设置扩展（分类/收藏/置顶/最近项目/常用路径/跨服务器收藏）。
 * 从 SettingsRepository.kt 拆出，无行为变更。
 */

    internal val SettingsRepository.sessionCategories: Flow<List<SessionCategory>> get() = dataStore.data.mapDecoded { preferences ->
        preferences[SettingsRepository.SESSION_CATEGORIES_KEY]?.let { encoded ->
            runCatching { json.decodeFromString<List<SessionCategory>>(encoded) }.getOrDefault(emptyList())
        }.orEmpty()
    }

    internal val SettingsRepository.crossServerFavoriteOrder: Flow<List<String>> get() = dataStore.data.mapDecoded { preferences ->
        preferences[SettingsRepository.CROSS_SERVER_FAVORITE_ORDER_KEY]
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.toList()
            .orEmpty()
    }

    internal val SettingsRepository.favoriteSessionSnapshots: Flow<Map<String, FavoriteSessionSnapshot>> get() = dataStore.data.mapDecoded { preferences ->
        preferences[SettingsRepository.FAVORITE_SESSION_SNAPSHOTS_KEY]?.let { encoded ->
            runCatching { json.decodeFromString<Map<String, FavoriteSessionSnapshot>>(encoded) }.getOrDefault(emptyMap())
        }.orEmpty()
    }

    internal fun SettingsRepository.sessionCategoryAssignments(serverId: String): Flow<Map<String, String>> = dataStore.data.mapDecoded { preferences ->
        preferences[serverSessionCategoryKey(serverId)]?.let { encoded ->
            runCatching { json.decodeFromString<Map<String, String>>(encoded) }.getOrDefault(emptyMap())
        }.orEmpty()
    }

    internal suspend fun SettingsRepository.saveSessionCategory(category: SessionCategory) {
        dataStore.edit { preferences ->
            val categories = preferences[SettingsRepository.SESSION_CATEGORIES_KEY]?.let { encoded ->
                runCatching { json.decodeFromString<List<SessionCategory>>(encoded) }.getOrDefault(emptyList())
            }.orEmpty().toMutableList()
            val index = categories.indexOfFirst { it.id == category.id }
            if (index >= 0) categories[index] = category else categories += category
            preferences[SettingsRepository.SESSION_CATEGORIES_KEY] = json.encodeToString(categories)
        }
    }

    internal suspend fun SettingsRepository.deleteSessionCategory(categoryId: String) {
        dataStore.edit { preferences ->
            val categories = preferences[SettingsRepository.SESSION_CATEGORIES_KEY]?.let { encoded ->
                runCatching { json.decodeFromString<List<SessionCategory>>(encoded) }.getOrDefault(emptyList())
            }.orEmpty().filterNot { it.id == categoryId }
            preferences[SettingsRepository.SESSION_CATEGORIES_KEY] = json.encodeToString(categories)
        }
    }

    internal suspend fun SettingsRepository.setSessionCategory(serverId: String, sessionId: String, categoryId: String?) {
        dataStore.edit { preferences ->
            val key = serverSessionCategoryKey(serverId)
            val assignments = preferences[key]?.let { encoded ->
                runCatching { json.decodeFromString<Map<String, String>>(encoded) }.getOrDefault(emptyMap())
            }.orEmpty().toMutableMap()
            if (categoryId == null) assignments.remove(sessionId) else assignments[sessionId] = categoryId
            preferences[key] = json.encodeToString(assignments)
        }
    }

    internal fun SettingsRepository.favoriteSessionIds(serverId: String): Flow<List<String>> = dataStore.data.mapDecoded { preferences ->
        (preferences[serverFavoriteSessionsKey(serverId)] ?: preferences[serverPinnedSessionsKey(serverId)])
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.toList()
            .orEmpty()
    }

    internal suspend fun SettingsRepository.setSessionFavorite(
        serverId: String,
        sessionId: String,
        favorite: Boolean,
        snapshot: FavoriteSessionSnapshot? = null,
    ) {
        dataStore.edit { preferences ->
            val key = serverFavoriteSessionsKey(serverId)
            val legacyKey = serverPinnedSessionsKey(serverId)
            val current = (preferences[key] ?: preferences[legacyKey])
                ?.lineSequence()
                ?.filter(String::isNotBlank)
                ?.distinct()
                ?.toList()
                .orEmpty()
            val updated = if (favorite) {
                listOf(sessionId) + current.filterNot { it == sessionId }
            } else {
                current.filterNot { it == sessionId }
            }
            preferences[key] = updated.joinToString("\n")
            preferences.remove(legacyKey)
            val snapshotKey = favoriteSessionSnapshotKey(serverId, sessionId)
            val snapshots = preferences[SettingsRepository.FAVORITE_SESSION_SNAPSHOTS_KEY]?.let { encoded ->
                runCatching {
                    json.decodeFromString<Map<String, FavoriteSessionSnapshot>>(encoded)
                }.getOrDefault(emptyMap())
            }.orEmpty().toMutableMap()
            if (!favorite) snapshots.remove(snapshotKey) else if (snapshot != null) snapshots[snapshotKey] = snapshot
            preferences[SettingsRepository.FAVORITE_SESSION_SNAPSHOTS_KEY] = json.encodeToString(snapshots)
        }
    }

    internal suspend fun SettingsRepository.cacheFavoriteSessionSnapshots(snapshots: Map<String, FavoriteSessionSnapshot>) {
        if (snapshots.isEmpty()) return
        dataStore.edit { preferences ->
            val current = preferences[SettingsRepository.FAVORITE_SESSION_SNAPSHOTS_KEY]?.let { encoded ->
                runCatching {
                    json.decodeFromString<Map<String, FavoriteSessionSnapshot>>(encoded)
                }.getOrDefault(emptyMap())
            }.orEmpty()
            val updated = current + snapshots
            if (updated != current) {
                preferences[SettingsRepository.FAVORITE_SESSION_SNAPSHOTS_KEY] = json.encodeToString(updated)
            }
        }
    }

    internal fun SettingsRepository.favoriteSessionSnapshotKey(serverId: String, sessionId: String): String = "$serverId:$sessionId"

    internal suspend fun SettingsRepository.moveFavoriteSession(serverId: String, sessionId: String, offset: Int) {
        if (offset == 0) return
        dataStore.edit { preferences ->
            val key = serverFavoriteSessionsKey(serverId)
            val legacyKey = serverPinnedSessionsKey(serverId)
            val current = (preferences[key] ?: preferences[legacyKey])
                ?.lineSequence()
                ?.filter(String::isNotBlank)
                ?.distinct()
                ?.toMutableList()
                ?: mutableListOf()
            val from = current.indexOf(sessionId)
            if (from < 0) return@edit
            val to = (from + offset).coerceIn(0, current.lastIndex)
            if (from == to) return@edit
            current[from] = current[to]
            current[to] = sessionId
            preferences[key] = current.joinToString("\n")
            preferences.remove(legacyKey)
        }
    }

    internal fun SettingsRepository.pinnedSessionIds(serverId: String): Flow<List<String>> = dataStore.data.mapDecoded { preferences ->
        preferences[serverPinnedIdsKey(serverId)]
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.toList()
            .orEmpty()
    }

    internal suspend fun SettingsRepository.setSessionPinned(serverId: String, sessionId: String, pinned: Boolean) {
        dataStore.edit { preferences ->
            val key = serverPinnedIdsKey(serverId)
            val current = (preferences[key] ?: "")
                .lineSequence()
                .filter(String::isNotBlank)
                .distinct()
                .toMutableList()
            val updated = if (pinned) {
                listOf(sessionId) + current.filterNot { it == sessionId }
            } else {
                current.filterNot { it == sessionId }
            }
            preferences[key] = updated.joinToString("\n")
        }
    }

    internal suspend fun SettingsRepository.movePinnedSession(serverId: String, sessionId: String, offset: Int) {
        if (offset == 0) return
        dataStore.edit { preferences ->
            val key = serverPinnedIdsKey(serverId)
            val current = (preferences[key] ?: "")
                .lineSequence()
                .filter(String::isNotBlank)
                .distinct()
                .toMutableList()
            val from = current.indexOf(sessionId)
            if (from < 0) return@edit
            val to = (from + offset).coerceIn(0, current.lastIndex)
            if (from == to) return@edit
            val tmp = current[from]
            current[from] = current[to]
            current[to] = tmp
            preferences[key] = current.joinToString("\n")
        }
    }

    /**
     * 批量重排置顶会话顺序（拖拽排序后按新的顺序整体写入）。
     */
    internal suspend fun SettingsRepository.reorderPinnedSessions(serverId: String, orderedIds: List<String>) {
        val clean = orderedIds.filter(String::isNotBlank).distinct()
        if (clean.isEmpty()) return
        dataStore.edit { preferences ->
            val key = serverPinnedIdsKey(serverId)
            val current = (preferences[key] ?: "")
                .lineSequence()
                .filter(String::isNotBlank)
                .distinct()
                .toList()
            if (current.isEmpty()) return@edit
            val currentSet = current.toSet()
            if (clean.any { it !in currentSet }) return@edit
            // Preserve any pinned ids that were not part of the drag as trailing entries.
            val reordered = (clean + current.filter { it !in clean }).distinct()
            preferences[key] = reordered.joinToString("\n")
        }
    }

    internal fun SettingsRepository.recentProjects(serverId: String): Flow<List<String>> = dataStore.data.mapDecoded { preferences ->
        preferences[serverRecentProjectsKey(serverId)]
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.toList()
            .orEmpty()
    }

    internal suspend fun SettingsRepository.recordRecentProject(serverId: String, directory: String) {
        val trimmed = directory.trim().trimEnd('/')
        if (trimmed.isBlank()) return
        dataStore.edit { preferences ->
            val key = serverRecentProjectsKey(serverId)
            val current = (preferences[key] ?: "")
                .lineSequence()
                .filter(String::isNotBlank)
                .distinct()
                .toMutableList()
            val updated = (listOf(trimmed) + current.filterNot { it == trimmed }).take(20)
            preferences[key] = updated.joinToString("\n")
        }
    }

    /** 用户在 Open Project 里手动固定的常用路径（每服务器独立，保序，最近添加在前）。 */
    internal fun SettingsRepository.savedPaths(serverId: String): Flow<List<String>> = dataStore.data.mapDecoded { preferences ->
        preferences[serverSavedPathsKey(serverId)]
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.toList()
            .orEmpty()
    }

    internal suspend fun SettingsRepository.addSavedPath(serverId: String, path: String) {
        val trimmed = path.trim().trimEnd('/')
        if (trimmed.isBlank()) return
        dataStore.edit { preferences ->
            val key = serverSavedPathsKey(serverId)
            val current = (preferences[key] ?: "")
                .lineSequence()
                .filter(String::isNotBlank)
                .distinct()
                .toMutableList()
            val updated = (listOf(trimmed) + current.filterNot { it == trimmed }).take(30)
            preferences[key] = updated.joinToString("\n")
        }
    }

    internal suspend fun SettingsRepository.removeSavedPath(serverId: String, path: String) {
        val trimmed = path.trim().trimEnd('/')
        if (trimmed.isBlank()) return
        dataStore.edit { preferences ->
            val key = serverSavedPathsKey(serverId)
            val current = (preferences[key] ?: "")
                .lineSequence()
                .filter(String::isNotBlank)
                .distinct()
                .toList()
            preferences[key] = current.filterNot { it == trimmed }.joinToString("\n")
        }
    }

    internal suspend fun SettingsRepository.setCrossServerFavoriteOrderItem(itemKey: String, favorite: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[SettingsRepository.CROSS_SERVER_FAVORITE_ORDER_KEY]
                ?.lineSequence()
                ?.filter(String::isNotBlank)
                ?.distinct()
                ?.toList()
                .orEmpty()
            val updated = if (favorite) {
                if (itemKey in current) current else current + itemKey
            } else {
                current.filterNot { it == itemKey }
            }
            preferences[SettingsRepository.CROSS_SERVER_FAVORITE_ORDER_KEY] = updated.joinToString("\n")
        }
    }

    internal suspend fun SettingsRepository.setCrossServerFavoriteOrder(itemKeys: List<String>) {
        dataStore.edit { preferences ->
            preferences[SettingsRepository.CROSS_SERVER_FAVORITE_ORDER_KEY] = itemKeys.distinct().joinToString("\n")
        }
    }

