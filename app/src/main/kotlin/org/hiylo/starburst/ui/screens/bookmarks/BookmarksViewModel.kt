/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BookmarksViewModel.kt
 * Date : 2026/09/11 09:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.bookmarks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.hiylo.starburst.data.repository.BookmarkRepository
import org.hiylo.starburst.domain.model.MessageBookmark
import javax.inject.Inject

/**
 * 消息书签列表页的视图模型：向 UI 层暴露书签数据，并代理移除与加载操作。
 *
 * 书签数据由 [BookmarkRepository] 在构造时自动从 DataStore 加载并缓存为 [StateFlow]，
 * 因此本类直接透传其 [StateFlow]，不自行维护副本。
 *
 * @author Hsi Chu
 * @since V1.3.0
 */
@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** 当前书签所属服务器 ID；为空表示不过滤（展示全部服务器书签）。 */
    private val serverId: String? = savedStateHandle["serverId"]

    /** 书签列表，按添加时间倒序排列；绑定服务器时仅展示该服务器的书签。 */
    val bookmarks: StateFlow<List<MessageBookmark>> =
        bookmarkRepository.bookmarks.map { all ->
            if (serverId.isNullOrBlank()) all else all.filter { it.serverId == serverId }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /** 全部书签出现过的去重标签（按字典序排列），用于筛选栏。 */
    val allTags: StateFlow<List<String>> =
        bookmarks.map { list -> list.flatMap { it.tags }.distinct().sorted() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前选中的标签；为空表示展示全部书签。 */
    private val _selectedTag = MutableStateFlow<String?>(null)

    /** 当前选中的标签，供 UI 高亮筛选芯片。 */
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    /** 按选中标签过滤后的书签列表。 */
    val filteredBookmarks: StateFlow<List<MessageBookmark>> =
        combine(bookmarks, _selectedTag) { list, tag ->
            if (tag == null) list else list.filter { tag in it.tags }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 选中或取消某个标签作为筛选条件。
     *
     * @param tag 要筛选的标签，传 `null` 表示清除筛选
     */
    fun selectTag(tag: String?) {
        _selectedTag.value = tag
    }

    /**
     * 加载书签列表。
     *
     * 数据在仓库构造时即已异步加载，此处作为显式刷新入口预留，
     * 后续接入增量刷新时可在此触发。
     */
    fun load() {
        // 数据由 BookmarkRepository 构造时异步加载，此处暂无需额外动作。
    }

    /**
     * 移除指定书签。
     *
     * @param id 书签 ID
     */
    fun remove(id: String) {
        viewModelScope.launch { bookmarkRepository.remove(id) }
    }

    /**
     * 更新指定书签的标签列表。
     *
     * @param id 书签 ID
     * @param tags 新的标签列表
     */
    fun updateTags(id: String, tags: List<String>) {
        viewModelScope.launch { bookmarkRepository.updateTags(id, tags) }
    }
}
