/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : LogTailDialog.kt
 * Date : 2026/09/17 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.theme.CodeTypography

/**
 * 服务日志实时跟踪（tail）对话框。
 *
 * 提供可编辑的日志路径与关键字过滤输入框，并以等宽字体逐行展示日志正文；滚动区靠近底部时
 * 自动跟随最新内容（auto-scroll）。对话框关闭时停止跟踪，避免 PTY 命令持续运行。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogTailDialog(
    onDismiss: () -> Unit,
    viewModel: LogTailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var pathText by rememberSaveable { mutableStateOf("") }
    var keyword by rememberSaveable { mutableStateOf("") }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) { viewModel.attachLifecycle(lifecycleOwner.lifecycle) }

    LaunchedEffect(uiState.logPath) { pathText = uiState.logPath }
    DisposableEffect(Unit) {
        onDispose { viewModel.stop() }
    }

    val lines = remember(uiState.content, keyword) {
        val k = keyword.trim()
        val all = uiState.content.split('\n')
        if (k.isEmpty()) all else all.filter { it.contains(k, ignoreCase = true) }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isEmpty()) return@LaunchedEffect
        // 仅当用户位于（或接近）底部时自动跟随最新内容。
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
        val atBottom = lastVisible == null || lastVisible.index >= lines.size - 3
        if (atBottom) listState.scrollToItem(lines.size - 1)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.log_tail_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    },
                    actions = {
                        if (uiState.isStreaming) {
                            IconButton(onClick = viewModel::stop) {
                                Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.log_tail_stop))
                            }
                        } else {
                            IconButton(onClick = {
                                viewModel.setLogPath(pathText)
                                viewModel.start()
                            }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.log_tail_start))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )

                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = pathText,
                        onValueChange = { pathText = it },
                        label = { Text(stringResource(R.string.log_tail_path_label)) },
                        placeholder = { Text(stringResource(R.string.log_tail_path_hint)) },
                        singleLine = true,
                        enabled = !uiState.isStreaming,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text(stringResource(R.string.log_tail_keyword_label)) },
                        placeholder = { Text(stringResource(R.string.log_tail_keyword_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    uiState.error?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize().padding(bottom = 8.dp)) {
                        when {
                            uiState.isLoading && lines.isEmpty() -> {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }
                            lines.isEmpty() -> {
                                Text(
                                    text = stringResource(R.string.log_tail_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                            else -> {
                                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                    items(lines) { line ->
                                        Text(
                                            text = line,
                                            style = CodeTypography.copy(
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            ),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
