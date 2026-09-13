/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : FtsSearchViewModel.kt
 * Date : 2026/09/11 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.opencode.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.hiylo.opencode.data.search.FtsHit
import org.hiylo.opencode.data.search.MessageFtsIndex
import javax.inject.Inject

/**
 * 全文消息搜索页的 ViewModel。
 *
 * 负责维护搜索输入、命中结果与加载状态，并对查询输入做 300ms 防抖，
 * 防抖后通过 [MessageFtsIndex] 执行全文检索。
 *
 * @author Hsi Chu
 * @since V1.3.0
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class FtsSearchViewModel @Inject constructor(
    private val messageFtsIndex: MessageFtsIndex,
) : ViewModel() {

    private val _query = MutableStateFlow("")

    /** 用户当前输入的关键词。 */
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<FtsHit>>(emptyList())

    /** 最近一次搜索命中的结果列表。 */
    val results: StateFlow<List<FtsHit>> = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)

    /** 是否正在执行搜索。 */
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    init {
        viewModelScope.launch {
            _query
                .debounce(DEBOUNCE_MILLIS)
                .collectLatest { text ->
                    val keyword = text.trim()
                    if (keyword.isEmpty()) {
                        _results.value = emptyList()
                        _searching.value = false
                        return@collectLatest
                    }
                    _searching.value = true
                    _results.value = messageFtsIndex.search(keyword)
                    _searching.value = false
                }
        }
    }

    /**
     * 更新搜索输入。
     *
     * @param text 输入框最新文本
     */
    fun onQueryChange(text: String) {
        _query.value = text
    }

    companion object {
        /** 输入防抖间隔（毫秒）。 */
        private const val DEBOUNCE_MILLIS = 300L
    }
}
