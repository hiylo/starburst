/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BookmarksScreen.kt
 * Date : 2026/09/11 09:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.bookmarks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.hiylo.starburst.R
import org.hiylo.starburst.domain.model.MessageBookmark
import org.hiylo.starburst.ui.components.AppCardShape
import org.hiylo.starburst.ui.components.CartoonStickerIcon
import org.hiylo.starburst.ui.components.appAmoledBorder
import org.hiylo.starburst.ui.components.isAmoledTheme

/**
 * 消息书签列表页：展示全部书签，支持按标签筛选、管理标签，点击跳转会话、长按或删除按钮移除书签。
 *
 * @param onNavigateBack 返回上一页回调
 * @param onOpenSession 点击书签时回调，参数为服务器 ID 与会话 ID，跳转接线由外部完成
 * @param viewModel 书签视图模型，默认通过 Hilt 注入
 * @author Hsi Chu
 * @since V1.3.0
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun BookmarksScreen(
    serverName: String = "",
    onNavigateBack: () -> Unit,
    onOpenSession: (serverId: String, sessionId: String) -> Unit,
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    val bookmarks by viewModel.bookmarks.collectAsState()
    val filteredBookmarks by viewModel.filteredBookmarks.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val isAmoled = isAmoledTheme()
    var manageTarget by remember { mutableStateOf<MessageBookmark?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (serverName.isNotBlank()) {
                            stringResource(R.string.bookmarks_title_for, serverName)
                        } else {
                            stringResource(R.string.bookmarks_title)
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
                .background(MaterialTheme.colorScheme.surface)
                .padding(padding),
        ) {
            if (bookmarks.isEmpty()) {
                EmptyBookmarks()
            } else {
                TagFilterBar(
                    tags = allTags,
                    selectedTag = selectedTag,
                    onSelectTag = viewModel::selectTag,
                )
                if (filteredBookmarks.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CartoonStickerIcon(
                            Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp),
                            stickerTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            padding = 10.dp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.bookmarktags_empty_filter),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filteredBookmarks, key = { it.id }) { bookmark ->
                            BookmarkCard(
                                bookmark = bookmark,
                                isAmoled = isAmoled,
                                onOpen = { onOpenSession(bookmark.serverId, bookmark.sessionId) },
                                onRemove = { viewModel.remove(bookmark.id) },
                                onManageTags = { manageTarget = bookmark },
                            )
                        }
                    }
                }
            }
        }
    }

    manageTarget?.let { target ->
        ManageTagsDialog(
            bookmark = target,
            onDismiss = { manageTarget = null },
            onSave = { tags ->
                viewModel.updateTags(target.id, tags)
                manageTarget = null
            },
        )
    }
}

/**
 * 无书签时的空态提示。
 */
@Composable
private fun EmptyBookmarks() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CartoonStickerIcon(
            Icons.Default.BookmarkBorder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(48.dp),
            stickerTint = MaterialTheme.colorScheme.onPrimaryContainer,
            padding = 10.dp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.bookmarks_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.bookmarks_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        )
    }
}

/**
 * 标签筛选栏：展示「全部」与去重后的标签芯片，点击切换筛选条件。
 *
 * @param tags 全部可选标签
 * @param selectedTag 当前选中的标签，为空表示「全部」
 * @param onSelectTag 选中标签回调，传 `null` 表示清除筛选
 */
@Composable
private fun TagFilterBar(
    tags: List<String>,
    selectedTag: String?,
    onSelectTag: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selectedTag == null,
            onClick = { onSelectTag(null) },
            label = { Text(stringResource(R.string.bookmarktags_all)) },
        )
        tags.forEach { tag ->
            FilterChip(
                selected = selectedTag == tag,
                onClick = { onSelectTag(tag) },
                label = { Text(tag) },
            )
        }
    }
}

/**
 * 单条书签卡片：展示消息摘要、添加时间与标签，支持点击打开会话、长按或删除按钮移除、打开标签管理。
 *
 * @param bookmark 书签数据
 * @param isAmoled 是否为纯黑（AMOLED）主题
 * @param onOpen 点击打开会话回调
 * @param onRemove 移除书签回调
 * @param onManageTags 打开标签管理对话框回调
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun BookmarkCard(
    bookmark: MessageBookmark,
    isAmoled: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onManageTags: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = appAmoledBorder(0.65f),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onRemove),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.messageText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = dateFormat.format(Date(bookmark.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (bookmark.tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        bookmark.tags.forEach { tag ->
                            SuggestionChip(
                                onClick = onManageTags,
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onManageTags) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.bookmarktags_manage_tags),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.bookmarks_delete),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * 标签管理对话框：展示、删除已有标签，并可输入新标签添加，保存后整体替换书签标签。
 *
 * @param bookmark 当前编辑的书签
 * @param onDismiss 关闭对话框回调
 * @param onSave 保存回调，参数为新的标签列表
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManageTagsDialog(
    bookmark: MessageBookmark,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var tags by remember(bookmark.id) { mutableStateOf(bookmark.tags) }
    var newTag by remember(bookmark.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bookmarktags_manage_tags)) },
        text = {
            Column {
                if (tags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.bookmarktags_no_tags),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.bookmarktags_remove_tag),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { tags = tags - tag },
                                    )
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    label = { Text(stringResource(R.string.bookmarktags_tag_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        val trimmed = newTag.trim()
                        if (trimmed.isNotEmpty() && trimmed !in tags) {
                            tags = tags + trimmed
                        }
                        newTag = ""
                    },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.bookmarktags_add_tag))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(tags) }) {
                Text(stringResource(R.string.bookmarktags_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.bookmarktags_cancel))
            }
        },
    )
}
