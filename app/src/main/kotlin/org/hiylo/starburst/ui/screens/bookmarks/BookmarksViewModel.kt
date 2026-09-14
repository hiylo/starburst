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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
}
