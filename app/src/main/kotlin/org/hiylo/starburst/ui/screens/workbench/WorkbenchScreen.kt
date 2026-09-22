/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : WorkbenchScreen.kt
 * Date : 2026/09/14 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.workbench

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.launch
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.QuestionInfo
import org.hiylo.starburst.domain.model.SessionStatus
import org.hiylo.starburst.ui.components.AppCardShape
import org.hiylo.starburst.ui.components.AppDialog
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.AppSecondaryButton
import org.hiylo.starburst.ui.components.LocalAmoledTheme
import org.hiylo.starburst.ui.components.appAmoledBorder
import org.hiylo.starburst.ui.navigation.serverRoute
import org.hiylo.starburst.ui.theme.StatusConnected
import org.hiylo.starburst.ui.theme.StatusProcessing
import org.hiylo.starburst.ui.theme.StatusError
import org.hiylo.starburst.ui.theme.StatusWarning

/** AI 工作台路由定义（导航注册与创建路由都与现有 Screen 模式保持一致）。 */
object WorkbenchScreen {
    const val ROUTE = "workbench"
    const val ROUTE_PATTERN =
        "workbench?serverUrl={serverUrl}&username={username}&password={password}&serverName={serverName}&serverId={serverId}"

    fun createRoute(
        serverUrl: String,
        username: String,
        password: String,
        serverName: String,
        serverId: String,
    ): String = serverRoute(ROUTE, serverUrl, username, password, serverName, serverId)
}

/**
 * 2.0 AI 工作台看板（服务器级）：顶部实时事件流 + 下方全量会话列表。
 * 点击会话在列表内展开「决策面板」：AI 最近回复摘要 + 待决问题选项 + 快捷回复（文本发送）+ 进入完整会话。
 *
 * @param onNavigateBack 返回上一页
 * @param onOpenSession 进入该会话的完整聊天界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkbenchScreen(
    onNavigateBack: () -> Unit,
    onOpenSession: (sessionId: String) -> Unit,
    viewModel: WorkbenchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val panels by viewModel.panels.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectionMode by viewModel.selectionMode.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val sendingSessionIds by viewModel.sendingSessionIds.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val voiceActive by viewModel.voiceActive.collectAsState()
    val recognizedText by viewModel.recognizedText.collectAsState(null)
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) { viewModel.attachLifecycle(lifecycleOwner.lifecycle) }

    val sendQuickReply: (String, String) -> Unit = { sessionId, text ->
        viewModel.sendQuickReply(sessionId, text) { ok ->
            Toast.makeText(
                context,
                context.getString(if (ok) R.string.workbench_quick_reply_sent else R.string.workbench_quick_reply_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val replyToQuestion: (String, String, List<List<String>>) -> Unit = { sessionId, requestId, answers ->
        viewModel.replyToQuestion(sessionId, requestId, answers) { ok ->
            Toast.makeText(
                context,
                context.getString(if (ok) R.string.workbench_question_replied else R.string.workbench_question_reply_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val deleteSession: (String) -> Unit = { sessionId ->
        viewModel.deleteSession(sessionId) { ok ->
            if (!ok) {
                Toast.makeText(
                    context,
                    context.getString(R.string.workbench_delete_session_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // 语音快捷回复：首次使用请求 RECORD_AUDIO 权限，点击开始/结束。
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = SnackbarHostState()
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.toggleVoice()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.chat_voice_input_permission_denied))
            }
        }
    }
    val toggleVoiceInput: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.toggleVoice()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    val recognizedTextForPanel = recognizedText

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.workbench_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (uiState.serverName.isNotBlank()) {
                            Text(
                                text = uiState.serverName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    if (selectionMode) {
                        Text(
                            text = stringResource(R.string.workbench_selected_count, selected.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        TextButton(onClick = viewModel::toggleSelectionMode) {
                            Text(stringResource(R.string.workbench_cancel_select))
                        }
                    } else {
                        if (panels.isNotEmpty()) {
                            TextButton(onClick = viewModel::collapseAllPanels) {
                                Text(stringResource(R.string.workbench_collapse_all))
                            }
                        }
                        if (uiState.sessions.isNotEmpty()) {
                            TextButton(onClick = viewModel::toggleSelectionMode) {
                                Text(stringResource(R.string.workbench_select))
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AllSessionsSection(
                    sessions = uiState.sessions,
                    loading = uiState.loadingSessions,
                    error = uiState.sessionsError,
                    panels = panels,
                    filter = filter,
                    searchQuery = searchQuery,
                    selectionMode = selectionMode,
                    selected = selected,
                    refreshing = refreshing,
                    sendingSessionIds = sendingSessionIds,
                    voiceActive = voiceActive,
                    onToggleVoice = toggleVoiceInput,
                    recognizedText = recognizedTextForPanel,
                    onSearchChange = viewModel::setSearchQuery,
                    onFilterChange = viewModel::setFilter,
                    onTogglePanel = viewModel::togglePanel,
                    onToggleSelected = viewModel::toggleSelected,
                    onSelectAllVisible = viewModel::selectAllVisible,
                    onDeleteSelected = viewModel::deleteSelected,
                    onMarkSelectedRead = viewModel::markSelectedRead,
                    onRenameSession = viewModel::renameSession,
                    onTogglePin = viewModel::togglePin,
                    onEnsurePreview = viewModel::ensurePreview,
                    onRefresh = viewModel::manualRefresh,
                    onSendQuickReply = sendQuickReply,
                    onAnswerQuestion = replyToQuestion,
                    onDeleteSession = deleteSession,
                    onOpenSession = onOpenSession,
                )
            }
        }
    }
}

/** 下方全量会话列表区域：搜索框 + 筛选 chip / 批量操作栏 + 每张卡片内联展开的决策面板（可多会话同时展开）。 */
@Composable
private fun AllSessionsSection(
    sessions: List<WorkbenchSession>,
    loading: Boolean,
    error: String?,
    panels: Map<String, DecisionPanelState>,
    filter: WorkbenchFilter,
    searchQuery: String,
    selectionMode: Boolean,
    selected: Set<String>,
    refreshing: Boolean,
    sendingSessionIds: Set<String>,
    voiceActive: Boolean,
    onToggleVoice: () -> Unit,
    recognizedText: String?,
    onSearchChange: (String) -> Unit,
    onFilterChange: (WorkbenchFilter) -> Unit,
    onTogglePanel: (String) -> Unit,
    onToggleSelected: (String) -> Unit,
    onSelectAllVisible: (Set<String>) -> Unit,
    onDeleteSelected: () -> Unit,
    onMarkSelectedRead: () -> Unit,
    onRenameSession: (String, String) -> Unit,
    onTogglePin: (String) -> Unit,
    onEnsurePreview: (String) -> Unit,
    onRefresh: () -> Unit,
    onSendQuickReply: (String, String) -> Unit,
    onAnswerQuestion: (String, String, List<List<String>>) -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val showList = !loading && error == null && sessions.isNotEmpty()
    Column(modifier = Modifier.fillMaxSize()) {
        if (showList) {
            if (selectionMode) {
                WorkbenchSelectionBar(
                    selectedCount = selected.size,
                    visibleCount = sessions.size,
                    onSelectAll = { onSelectAllVisible(sessions.map { it.session.id }.toSet()) },
                    onMarkRead = onMarkSelectedRead,
                    onDelete = onDeleteSelected,
                )
            } else {
                WorkbenchSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchChange,
                )
                WorkbenchFilterBar(
                    sessions = sessions,
                    filter = filter,
                    onFilterChange = onFilterChange,
                )
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.workbench_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                error != null && sessions.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Default.HelpOutline,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                sessions.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        )
                        Text(
                            text = stringResource(R.string.workbench_sessions_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
                else -> {
                    val filtered = filterAndSearch(sessions, filter, searchQuery)
                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.workbench_filter_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    } else {
                        SwipeRefresh(
                            state = rememberSwipeRefreshState(isRefreshing = refreshing),
                            onRefresh = onRefresh,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                items(filtered, key = { it.session.id }) { item ->
                                    WorkbenchSessionCard(
                                        item = item,
                                        expanded = item.session.id in panels,
                                        panelContent = panels[item.session.id],
                                        sending = item.session.id in sendingSessionIds,
                                        selectionMode = selectionMode,
                                        selected = item.session.id in selected,
                                        voiceActive = voiceActive,
                                        onToggleVoice = onToggleVoice,
                                        recognizedText = recognizedText,
                                        onClick = {
                                            if (selectionMode) onToggleSelected(item.session.id) else onTogglePanel(item.session.id)
                                        },
                                        onToggleSelected = { onToggleSelected(item.session.id) },
                                        onEnsurePreview = { onEnsurePreview(item.session.id) },
                                        onRenameSession = { title -> onRenameSession(item.session.id, title) },
                                        onTogglePin = { onTogglePin(item.session.id) },
                                        onSendQuickReply = { text -> onSendQuickReply(item.session.id, text) },
                                        onAnswerQuestion = { requestId, answers ->
                                            onAnswerQuestion(item.session.id, requestId, answers)
                                        },
                                        onDeleteSession = { onDeleteSession(item.session.id) },
                                        onOpenSession = { onOpenSession(item.session.id) },
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

/** 按状态筛选 + 关键词搜索（标题 / 目录 / 模型）。 */
private fun filterAndSearch(
    sessions: List<WorkbenchSession>,
    filter: WorkbenchFilter,
    searchQuery: String,
): List<WorkbenchSession> {
    val query = searchQuery.trim()
    return sessions.filter { item ->
        val matchStatus = when (filter) {
            WorkbenchFilter.All -> true
            WorkbenchFilter.Question -> item.status is SessionStatus.Question
            WorkbenchFilter.Busy -> item.status is SessionStatus.Busy || item.status is SessionStatus.Retry
            WorkbenchFilter.Idle -> item.status is SessionStatus.Idle
        }
        if (!matchStatus) return@filter false
        if (query.isEmpty()) return@filter true
        item.session.title?.contains(query, ignoreCase = true) == true ||
            item.session.directory.contains(query, ignoreCase = true) ||
            item.session.model?.id?.contains(query, ignoreCase = true) == true
    }
}

/** 搜索框：标题 / 目录 / 模型关键词过滤。 */
@Composable
private fun WorkbenchSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(46.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = { Text(stringResource(R.string.workbench_search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.workbench_search_clear), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        singleLine = true,
        shape = AppCardShape,
    )
}

/** 批量选择操作栏：全选 / 已读 / 删除。 */
@Composable
private fun WorkbenchSelectionBar(
    selectedCount: Int,
    visibleCount: Int,
    onSelectAll: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onSelectAll) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.workbench_select_all))
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onMarkRead, enabled = selectedCount > 0) {
            Text(stringResource(R.string.workbench_mark_read))
        }
        TextButton(onClick = onDelete, enabled = selectedCount > 0) {
            Text(
                text = stringResource(R.string.workbench_batch_delete),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = stringResource(R.string.workbench_selected_count, selectedCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 筛选 chip 栏：全部 / 待回复 / 处理中 / 空闲，chip 上悬浮显示各维度会话数。 */
@Composable
private fun WorkbenchFilterBar(
    sessions: List<WorkbenchSession>,
    filter: WorkbenchFilter,
    onFilterChange: (WorkbenchFilter) -> Unit,
) {
    val questionCount = sessions.count { it.status is SessionStatus.Question }
    val busyCount = sessions.count { it.status is SessionStatus.Busy || it.status is SessionStatus.Retry }
    val idleCount = sessions.count { it.status is SessionStatus.Idle }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = filter == WorkbenchFilter.All,
            onClick = { onFilterChange(WorkbenchFilter.All) },
            label = { Text(stringResource(R.string.workbench_filter_all)) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        )
        WorkbenchFilterChip(
            label = stringResource(R.string.workbench_filter_question),
            count = questionCount,
            selected = filter == WorkbenchFilter.Question,
            highlight = true,
            onClick = { onFilterChange(WorkbenchFilter.Question) },
        )
        WorkbenchFilterChip(
            label = stringResource(R.string.workbench_filter_busy),
            count = busyCount,
            selected = filter == WorkbenchFilter.Busy,
            highlight = true,
            onClick = { onFilterChange(WorkbenchFilter.Busy) },
        )
        WorkbenchFilterChip(
            label = stringResource(R.string.workbench_filter_idle),
            count = idleCount,
            selected = filter == WorkbenchFilter.Idle,
            highlight = false,
            onClick = { onFilterChange(WorkbenchFilter.Idle) },
        )
    }
}

/** 带计数徽标的筛选 chip。 */
@Composable
private fun WorkbenchFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(label)
                if (count > 0) {
                    val badgeColor = if (highlight) {
                        if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else StatusWarning
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            highlightColoredBackground()
                        },
                    ) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    )
}

/** 非选中高亮 chip 徽标的底色（待回复/处理中提示存在感）。 */
@Composable
private fun highlightColoredBackground(): Color =
    if (LocalAmoledTheme.current) Color.Black else MaterialTheme.colorScheme.surfaceVariant

/** 单个会话卡片：顶部为摘要行（含最后消息预览），展开时下方出现决策面板。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkbenchSessionCard(
    item: WorkbenchSession,
    expanded: Boolean,
    sending: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    panelContent: DecisionPanelState?,
    voiceActive: Boolean,
    onToggleVoice: () -> Unit,
    recognizedText: String?,
    onToggleSelected: () -> Unit,
    onEnsurePreview: () -> Unit,
    onRenameSession: (String) -> Unit,
    onTogglePin: () -> Unit,
    onSendQuickReply: (String) -> Unit,
    onAnswerQuestion: (String, List<List<String>>) -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenSession: () -> Unit,
) {
    // 卡片可见即懒加载最后消息预览（未展开也能看到最新进展）。
    LaunchedEffect(item.session.id) { onEnsurePreview() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        // 提问中 / 处理中的活跃会话用彩色描边突出，便于一眼定位需要关注的会话。
        border = when (item.status) {
            is SessionStatus.Question -> BorderStroke(1.5.dp, StatusWarning.copy(alpha = 0.9f))
            is SessionStatus.Busy, is SessionStatus.Retry -> BorderStroke(1.5.dp, StatusProcessing.copy(alpha = 0.9f))
            is SessionStatus.Idle -> appAmoledBorder()
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SessionSummaryRow(
                item = item,
                expanded = expanded,
                selectionMode = selectionMode,
                selected = selected,
                onClick = onClick,
                onToggleSelected = onToggleSelected,
                onRenameSession = onRenameSession,
                onTogglePin = onTogglePin,
                onDeleteSession = { onDeleteSession(item.session.id) },
                onOpenSession = onOpenSession,
            )
            AnimatedVisibility(visible = expanded) {
                if (panelContent != null) {
                    DecisionPanelContent(
                        panel = panelContent,
                        sending = sending,
                        voiceActive = voiceActive,
                        onToggleVoice = onToggleVoice,
                        recognizedText = recognizedText,
                        onSend = onSendQuickReply,
                        onAnswerQuestion = onAnswerQuestion,
                        onOpenSession = onOpenSession,
                    )
                }
            }
        }
    }
}

/** 会话摘要行：状态点 + 标题 + 路径/预览 + 状态标签 + 展开箭头，长按弹出会话菜单（重命名/复制/置顶/删除）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionSummaryRow(
    item: WorkbenchSession,
    expanded: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleSelected: () -> Unit,
    onRenameSession: (String) -> Unit,
    onTogglePin: () -> Unit,
    onDeleteSession: () -> Unit,
    onOpenSession: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember(item.session.id) { mutableStateOf(item.session.title.orEmpty()) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val copyTitle: () -> Unit = {
        showMenu = false
        val title = item.session.title?.takeIf { it.isNotBlank() }
        if (title != null) {
            clipboard.setText(AnnotatedString(title))
            Toast.makeText(context, context.getString(R.string.workbench_title_copied), Toast.LENGTH_SHORT).show()
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = appAmoledBorder(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = { if (!selectionMode) showMenu = true },
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
                } else {
                    StatusDot(status = item.status)
                    if (item.unread) UnreadDot()
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.pinned) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(3.dp))
                        }
                        Text(
                            text = item.session.title?.takeIf { it.isNotBlank() }?.replace('\n', ' ')
                                ?: stringResource(R.string.session_untitled),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val subtitle = buildString {
                        val modelId = item.session.model?.id?.takeIf { it.isNotBlank() }
                        if (modelId != null) {
                            append(modelId)
                        }
                        if (item.session.directory.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(item.session.directory)
                        }
                        if (item.pendingQuestion != null) {
                            if (isNotEmpty()) append(" · ")
                            append(stringResource(R.string.session_status_pending_question))
                            append(" (${item.pendingQuestion.questions.size})")
                        }
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!expanded && item.aiPreview != null) {
                        Text(
                            text = item.aiPreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                if (!selectionMode) {
                    StatusLabel(status = item.status)
                    if (item.pendingQuestion != null) {
                        PendingQuestionBadge(count = item.pendingQuestion.questions.size)
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!selectionMode) {
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.workbench_menu_enter_session)) },
                        onClick = {
                            showMenu = false
                            onOpenSession()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.workbench_menu_rename)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            renameText = item.session.title.orEmpty()
                            showRenameDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.workbench_menu_copy_title)) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = copyTitle,
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(if (item.pinned) R.string.workbench_menu_unpin else R.string.workbench_menu_pin))
                        },
                        leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onTogglePin()
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.workbench_menu_delete_session)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDeleteSession()
                        },
                    )
                }
            }
        }
    }
    if (showRenameDialog) {
        AppDialog(onDismissRequest = { showRenameDialog = false }) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.session_rename),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.session_rename_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    AppSecondaryButton(onClick = { showRenameDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                    AppPrimaryButton(
                        onClick = {
                            showRenameDialog = false
                            onRenameSession(renameText)
                        },
                        enabled = renameText.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.session_rename_button))
                    }
                }
            }
        }
    }
}

/** 状态指示点：提问中(amber) > 处理中(blue) > 重试(red) > 空闲(灰)。 */
@Composable
private fun StatusDot(status: SessionStatus) {
    val color = when (status) {
        is SessionStatus.Question -> StatusWarning
        is SessionStatus.Busy -> StatusProcessing
        is SessionStatus.Retry -> StatusError
        is SessionStatus.Idle -> MaterialTheme.colorScheme.outlineVariant
    }
    Surface(modifier = Modifier.size(10.dp), shape = CircleShape, color = color) {}
}

/** 未读新消息：闪动绿点（后端 session_unread 驱动，Web/App 共享已读状态）。 */
@Composable
private fun UnreadDot() {
    val infinite = rememberInfiniteTransition(label = "unreadPulse")
    val alpha by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
        label = "unreadAlpha",
    )
    Surface(
        modifier = Modifier
            .size(10.dp)
            .graphicsLayer { this.alpha = alpha },
        shape = CircleShape,
        color = StatusConnected,
    ) {}
}

@Composable
private fun StatusLabel(status: SessionStatus) {
    val text: String? = when (status) {
        is SessionStatus.Question -> stringResource(R.string.session_status_pending_question)
        is SessionStatus.Busy -> stringResource(R.string.session_status_busy)
        is SessionStatus.Retry -> stringResource(R.string.sessions_retrying)
        is SessionStatus.Idle -> stringResource(R.string.workbench_status_idle)
    }
    val color = when (status) {
        is SessionStatus.Question -> StatusWarning
        is SessionStatus.Busy -> StatusProcessing
        is SessionStatus.Retry -> StatusError
        is SessionStatus.Idle -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    Text(
        text = text ?: "",
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

/** 待决问题徽标：amber 胶囊 + 问题数，提示该会话等待回答。 */
@Composable
private fun PendingQuestionBadge(count: Int) {
    Surface(
        shape = CircleShape,
        color = StatusWarning.copy(alpha = 0.18f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Surface(modifier = Modifier.size(5.dp), shape = CircleShape, color = StatusWarning) {}
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = StatusWarning,
            )
        }
    }
}

/** 决策面板：AI 最近回复摘要 + 待决问题选项 + 快捷回复输入 + 进入完整会话。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DecisionPanelContent(
    panel: DecisionPanelState,
    sending: Boolean,
    voiceActive: Boolean,
    onToggleVoice: () -> Unit,
    recognizedText: String?,
    onSend: (String) -> Unit,
    onAnswerQuestion: (String, List<List<String>>) -> Unit,
    onOpenSession: () -> Unit,
) {
    var quickReply by rememberSaveable(panel.sessionId) { mutableStateOf("") }
    // 语音识别结果自动填入快捷回复输入框。
    LaunchedEffect(recognizedText) {
        if (!recognizedText.isNullOrBlank()) {
            quickReply = recognizedText
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 快捷回复输入框 + 语音 + 发送，置顶便于快速操作。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val submit: () -> Unit = {
                val trimmed = quickReply.trim()
                if (trimmed.isNotEmpty() && !sending) {
                    // 发送后保留草稿，便于连续补充/改写再发。
                    onSend(trimmed)
                }
            }
            BasicTextField(
                value = quickReply,
                onValueChange = { quickReply = it },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                minLines = 1,
                maxLines = 4,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                decorationBox = { innerTextField ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (quickReply.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.workbench_quick_reply_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                            innerTextField()
                        }
                        if (quickReply.isNotEmpty()) {
                            IconButton(
                                onClick = { quickReply = "" },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.workbench_clear_draft),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                },
            )
            IconButton(
                onClick = onToggleVoice,
                enabled = !sending,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = if (voiceActive) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = stringResource(R.string.chat_voice_input),
                    modifier = Modifier.size(22.dp),
                    tint = if (voiceActive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                    },
                )
            }
            IconButton(
                onClick = submit,
                enabled = quickReply.isNotBlank() && !sending,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.chat_send),
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                )
            }
        }

        // 处理中的实时指示：会话仍在生成/重试时置顶展示，内容随推送去抖刷新。
        if (panel.sessionStatus is SessionStatus.Busy || panel.sessionStatus is SessionStatus.Retry) {
            Row(
                modifier = Modifier.fillMaxWidth().background(
                    StatusProcessing.copy(alpha = 0.10f),
                    RoundedCornerShape(8.dp),
                ).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = StatusProcessing)
                Text(
                    text = stringResource(R.string.workbench_panel_processing),
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusProcessing,
                )
            }
        }

        AppPrimaryButton(
            onClick = onOpenSession,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.OpenInFull, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.workbench_open_full_session))
        }

        // 会话标题/路径/状态在卡片顶部已显示，面板内不再重复。
        Text(
            text = stringResource(R.string.workbench_decision_ai_reply),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            panel.loading -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.workbench_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            panel.recentMessages.isEmpty() -> {
                Text(
                    text = stringResource(R.string.workbench_decision_no_reply),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            else -> {
                panel.recentMessages.forEach { msg ->
                    RecentMessageRow(msg)
                }
            }
        }

        if (panel.questions.isNotEmpty()) {
            Text(
                text = stringResource(R.string.workbench_decision_questions),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val requestId = panel.questionRequestId
            if (requestId != null) {
                val single = panel.questions.size == 1 && panel.questions.first().multiple != true
                if (single) {
                    panel.questions.forEach { question ->
                        QuestionOptions(
                            question = question,
                            onSelect = { label -> onAnswerQuestion(requestId, listOf(listOf(label))) },
                        )
                    }
                } else {
                    val answersPerQuestion = remember(panel.sessionId, requestId) {
                        mutableStateListOf<List<String>>().apply {
                            repeat(panel.questions.size) { add(emptyList()) }
                        }
                    }
                    panel.questions.forEachIndexed { index, question ->
                        QuestionOptions(
                            question = question,
                            onSelect = { label ->
                                answersPerQuestion[index] = listOf(label)
                                if (answersPerQuestion.all { it.isNotEmpty() }) {
                                    onAnswerQuestion(requestId, answersPerQuestion.toList())
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 决策面板里的一条最近对话消息（角色标签 + 文本）。 */
@Composable
private fun RecentMessageRow(msg: PanelMessage) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            },
        ) {
            Text(
                text = if (isUser) stringResource(R.string.workbench_panel_me) else stringResource(R.string.workbench_panel_ai),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(
            text = msg.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (msg.full) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 单个待决问题：题面 + 选项（点击选项即作为快捷回复发送）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionOptions(
    question: QuestionInfo,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val title = question.header.takeIf { it.isNotBlank() } ?: question.question
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (question.question.isNotBlank() && question.question != title) {
            Text(
                text = question.question,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (question.options.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                question.options.forEachIndexed { index, option ->
                    OptionCard(
                        index = index,
                        label = option.label,
                        description = option.description,
                        onSelect = { onSelect(option.label) },
                    )
                }
            }
        }
    }
}

/** 待决问题选项卡片：编号圆点 + 名称/描述 + 箭头，点击即作为快捷回复发送。 */
@Composable
private fun OptionCard(
    index: Int,
    label: String,
    description: String,
    onSelect: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}