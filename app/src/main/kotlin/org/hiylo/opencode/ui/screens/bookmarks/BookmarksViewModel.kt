/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : BookmarksViewModel.kt
 * Date : 2026/09/11 09:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.opencode.ui.screens.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.hiylo.opencode.data.repository.BookmarkRepository
import org.hiylo.opencode.domain.model.MessageBookmark
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
) : ViewModel() {

    /** 全部书签，按添加时间倒序排列。 */
    val bookmarks: StateFlow<List<MessageBookmark>> = bookmarkRepository.bookmarks

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
