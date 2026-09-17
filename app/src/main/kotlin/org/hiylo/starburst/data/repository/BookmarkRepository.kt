/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BookmarkRepository.kt
 * Date : 2026/09/11 08:42:13
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hiylo.starburst.domain.model.MessageBookmark
import org.hiylo.starburst.logging.AppLogger as Log
import javax.inject.Inject
import javax.inject.Singleton

private const val BOOKMARKS_TAG = "BookmarkRepository"

/**
 * 消息书签仓库：负责消息书签的加载、缓存与持久化，供后续 UI 层使用。
 *
 * 持久化方式：将书签列表序列化为 JSON 字符串存入 DataStore Preferences 的单个 key；
 * 内存中以 [MutableStateFlow] 缓存，并通过自带 [CoroutineScope]（`Dispatchers.IO + SupervisorJob`）读写 DataStore。
 *
 * @author Hsi Chu
 * @since V1.3.0
 */
@Singleton
class BookmarkRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {
    /** 仓库自带作用域：repository 无 ViewModel，需独立持有协程作用域完成 DataStore 读写。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 串行化初始加载与增删改，避免缓存与持久化出现竞态。 */
    private val mutex = Mutex()

    private val _bookmarks = MutableStateFlow<List<MessageBookmark>>(emptyList())

    /** 全部书签，按添加时间倒序排列。 */
    val bookmarks: StateFlow<List<MessageBookmark>> = _bookmarks.asStateFlow()

    init {
        scope.launch {
            mutex.withLock {
                _bookmarks.value = readFromDataStore()
            }
        }
    }

    /**
     * 查询某个会话下的全部书签（按添加时间倒序）。
     *
     * @param serverId 服务器 ID
     * @param sessionId 会话 ID
     * @return 该会话下书签的 [Flow]
     */
    fun bookmarksForSession(serverId: String, sessionId: String): Flow<List<MessageBookmark>> =
        bookmarks.map { list -> list.filter { it.serverId == serverId && it.sessionId == sessionId } }

    /**
     * 全部书签按会话分组，key 形如 `serverId:sessionId`。
     *
     * @return 会话分组后的书签 [Flow]
     */
    fun bookmarksGroupedBySession(): Flow<Map<String, List<MessageBookmark>>> =
        bookmarks.map { list -> list.groupBy { it.sessionKey() } }

    /**
     * 添加书签（幂等：同一 [MessageBookmark.id] 覆盖旧记录）。
     *
     * @param bookmark 待添加的书签
     */
    suspend fun add(bookmark: MessageBookmark) {
        mutex.withLock {
            val updated = (_bookmarks.value.filterNot { it.id == bookmark.id } + bookmark)
                .sortedByDescending { it.createdAt }
            _bookmarks.value = updated
            dataStore.edit { preferences -> preferences[BOOKMARKS_KEY] = json.encodeToString(updated) }
        }
    }

    /**
     * 按 ID 移除书签。
     *
     * @param id 书签 ID（可通过 [MessageBookmark.buildId] 生成）
     */
    suspend fun remove(id: String) {
        mutex.withLock {
            val current = _bookmarks.value
            val updated = current.filterNot { it.id == id }
            if (updated.size == current.size) return
            _bookmarks.value = updated
            dataStore.edit { preferences -> preferences[BOOKMARKS_KEY] = json.encodeToString(updated) }
        }
    }

    /**
     * 更新指定书签的标签列表（整体替换）。
     *
     * @param id 书签 ID
     * @param tags 新的标签列表，可为空表示清空全部标签
     */
    suspend fun updateTags(id: String, tags: List<String>) {
        mutex.withLock {
            val current = _bookmarks.value
            val updated = current.map { bookmark ->
                if (bookmark.id == id) bookmark.copy(tags = tags.distinct()) else bookmark
            }
            if (updated == current) return
            _bookmarks.value = updated
            dataStore.edit { preferences -> preferences[BOOKMARKS_KEY] = json.encodeToString(updated) }
        }
    }

    /**
     * 判断某条消息是否已被标记为书签。
     *
     * @param serverId 服务器 ID
     * @param sessionId 会话 ID
     * @param messageId 消息 ID
     * @return 已标记返回 `true`，否则返回 `false`
     */
    suspend fun isBookmarked(serverId: String, sessionId: String, messageId: String): Boolean {
        val id = MessageBookmark.buildId(serverId, sessionId, messageId)
        return mutex.withLock { _bookmarks.value.any { it.id == id } }
    }

    /** 从 DataStore 读取并反序列化书签列表，失败时返回空列表。 */
    private suspend fun readFromDataStore(): List<MessageBookmark> {
        val raw = dataStore.data.map { preferences -> preferences[BOOKMARKS_KEY] }.first() ?: return emptyList()
        return runCatching { json.decodeFromString<List<MessageBookmark>>(raw) }.getOrElse { error ->
            Log.e(BOOKMARKS_TAG, "Failed to decode bookmarks", error)
            emptyList()
        }
    }

    private fun MessageBookmark.sessionKey(): String = "$serverId:$sessionId"

    companion object {
        /** 书签列表 JSON 字符串在 DataStore Preferences 中的 key。 */
        private val BOOKMARKS_KEY = stringPreferencesKey("message_bookmarks")
    }
}
