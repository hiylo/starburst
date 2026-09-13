/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : FtsSearchScreen.kt
 * Date : 2026/09/11 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.opencode.ui.screens.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.res.stringResource
import org.hiylo.opencode.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.hiylo.opencode.data.search.FtsHit
import org.hiylo.opencode.ui.components.AppCardShape
import org.hiylo.opencode.ui.components.AppSearchShape
import org.hiylo.opencode.ui.components.appAmoledBorder
import org.hiylo.opencode.ui.components.isAmoledTheme

/**
 * 全文消息搜索页。
 *
 * 顶部提供搜索输入框（自动聚焦），下方以列表展示命中结果，
 * 结果片段中的 `<b>`/`</b>` 命中标记被转换为高亮样式。
 *
 * @param onNavigateBack 返回上一页回调
 * @param onOpenResult 点击命中结果后的跳转回调，携带 serverId、sessionId、messageId
 * @param viewModel 搜索页 ViewModel
 * @author Hsi Chu
 * @since V1.3.0
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FtsSearchScreen(
    serverName: String = "",
    onNavigateBack: () -> Unit,
    onOpenResult: (serverId: String, sessionId: String, messageId: String) -> Unit,
    viewModel: FtsSearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val isAmoled = isAmoledTheme()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (serverName.isNotBlank()) {
                            stringResource(R.string.fts_search_title_for, serverName)
                        } else {
                            stringResource(R.string.fts_search_title)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
                shape = AppSearchShape,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.fts_search_clear))
                        }
                    }
                } else {
                    null
                },
                placeholder = { Text(stringResource(R.string.fts_search_hint)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                ),
            )

            when {
                query.trim().isEmpty() -> {
                    EmptyStateHint(
                        icon = Icons.Default.Search,
                        text = stringResource(R.string.fts_search_prompt),
                    )
                }

                searching -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                results.isEmpty() -> {
                    EmptyStateHint(
                        icon = Icons.Default.Search,
                        text = stringResource(R.string.fts_search_no_results),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(
                            results,
                            key = { it.messageId },
                        ) { hit ->
                            FtsHitCard(
                                hit = hit,
                                onClick = {
                                    onOpenResult(hit.serverId, hit.sessionId, hit.messageId)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateHint(
    icon: ImageVector,
    text: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp))
        Text(
            text = text,
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FtsHitCard(
    hit: FtsHit,
    onClick: () -> Unit,
) {
    val isAmoled = isAmoledTheme()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = if (isAmoled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        } else {
            null
        },
        shape = AppCardShape,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = hit.title.ifBlank { stringResource(R.string.fts_search_empty_title) },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildHighlightedSnippet(
                    snippet = hit.snippet,
                    baseColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    highlightColor = MaterialTheme.colorScheme.primary,
                ),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 将片段中的 `<b>`/`</b>` 命中标记转换为高亮样式。
 *
 * 未命中的普通文本使用 [baseColor]，命中词使用 [highlightColor] 并加粗。
 *
 * @param snippet 含 `<b>`/`</b>` 标记的命中片段
 * @param baseColor 普通文本颜色
 * @param highlightColor 命中词高亮颜色
 * @return 处理后的富文本
 */
private fun buildHighlightedSnippet(
    snippet: String,
    baseColor: Color,
    highlightColor: Color,
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    while (cursor < snippet.length) {
        val open = snippet.indexOf(OPEN_TAG, cursor)
        if (open < 0) {
            withStyle(SpanStyle(color = baseColor)) { append(snippet.substring(cursor)) }
            break
        }
        if (open > cursor) {
            withStyle(SpanStyle(color = baseColor)) { append(snippet.substring(cursor, open)) }
        }
        val contentStart = open + OPEN_TAG.length
        val close = snippet.indexOf(CLOSE_TAG, contentStart)
        if (close < 0) {
            withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                append(snippet.substring(contentStart))
            }
            break
        }
        withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
            append(snippet.substring(contentStart, close))
        }
        cursor = close + CLOSE_TAG.length
    }
}

/** snippet 命中词左标记。 */
private const val OPEN_TAG = "<b>"

/** snippet 命中词右标记。 */
private const val CLOSE_TAG = "</b>"
