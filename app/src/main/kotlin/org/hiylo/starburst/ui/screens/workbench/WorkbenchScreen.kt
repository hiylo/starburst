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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.QuestionInfo
import org.hiylo.starburst.data.api.SessionEventRecord
import org.hiylo.starburst.domain.model.SessionStatus
import org.hiylo.starburst.ui.components.AppCardShape
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.appAmoledBorder
import org.hiylo.starburst.ui.navigation.serverRoute
import org.hiylo.starburst.ui.theme.StatusConnected
import org.hiylo.starburst.ui.theme.StatusProcessing
import org.hiylo.starburst.ui.theme.StatusError
import org.hiylo.starburst.ui.theme.StatusWarning
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    val panel by viewModel.panel.collectAsState()
    val sendingSessionId by viewModel.sendingSessionId.collectAsState()
    val voiceActive by viewModel.voiceActive.collectAsState()
    val recognizedText by viewModel.recognizedText.collectAsState(null)
    val context = LocalContext.current

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
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            LiveEventsSection(
                events = uiState.events,
                sessions = uiState.sessions,
                error = uiState.eventsError,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            Box(modifier = Modifier.weight(1f)) {
                AllSessionsSection(
                    sessions = uiState.sessions,
                    loading = uiState.loadingSessions,
                    error = uiState.sessionsError,
                    panel = panel,
                    sendingSessionId = sendingSessionId,
                    voiceActive = voiceActive,
                    onToggleVoice = toggleVoiceInput,
                    recognizedText = recognizedTextForPanel,
                    onTogglePanel = viewModel::togglePanel,
                    onSendQuickReply = sendQuickReply,
                    onAnswerQuestion = replyToQuestion,
                    onDeleteSession = deleteSession,
                    onOpenSession = onOpenSession,
                )
            }
        }
    }
}

/** 顶部实时事件流区域：总体进度汇总 + 按会话聚合的最新动态。 */
@Composable
private fun LiveEventsSection(
    events: List<WorkbenchEventItem>,
    sessions: List<WorkbenchSession>,
    error: String?,
) {
    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 230.dp)) {
        SectionHeader(
            title = stringResource(R.string.workbench_live_events),
            count = events.size,
        )
        ProgressHeader(sessions)
        when {
            events.isEmpty() && error != null -> {
                EmptyHint(text = stringResource(R.string.workbench_events_failed))
            }
            events.isEmpty() -> {
                EmptyHint(text = stringResource(R.string.workbench_live_events_empty))
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                ) {
                    items(events, key = { it.sessionId }) { event ->
                        val session = sessions.firstOrNull { it.session.id == event.sessionId }?.session
                        LiveEventRow(
                            event = event,
                            title = event.title.ifBlank { session?.title.orEmpty() },
                            directory = event.directory.ifBlank { session?.directory.orEmpty() },
                        )
                    }
                }
            }
        }
    }
}

/** 总体进度：处理中 / 待决 / 空闲 计数。 */
@Composable
private fun ProgressHeader(sessions: List<WorkbenchSession>) {
    val busy = sessions.count { it.status is SessionStatus.Busy || it.status is SessionStatus.Retry }
    val question = sessions.count { it.status is SessionStatus.Question }
    val idle = sessions.count { it.status is SessionStatus.Idle }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressChip(color = StatusWarning, count = question, label = stringResource(R.string.workbench_progress_question))
        ProgressChip(color = StatusProcessing, count = busy, label = stringResource(R.string.workbench_progress_busy))
        ProgressChip(color = MaterialTheme.colorScheme.outline, count = idle, label = stringResource(R.string.workbench_progress_idle))
    }
}

@Composable
private fun ProgressChip(color: Color, count: Int, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = color) {}
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

/** 单条动态行：状态点 + 会话名 + 「动作 · 路径」摘要 + 时间。 */
@Composable
private fun LiveEventRow(
    event: WorkbenchEventItem,
    title: String = "",
    directory: String = "",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = CircleShape,
            color = eventDotColor(event.eventType),
        ) {}
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ellipsizeMiddle(title.ifBlank { event.title.ifBlank { event.sessionId } }, 42),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = listOfNotNull(
                event.summary.takeIf { it.isNotBlank() },
                directory.ifBlank { event.directory }.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = formatEventTime(event.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun eventDotColor(eventType: String): Color =
    when {
        eventType.contains("failed") || eventType.contains("error") -> StatusError
        eventType.contains("idle") || eventType.contains("finished") -> StatusConnected
        else -> MaterialTheme.colorScheme.primary
    }

private fun formatEventTime(createdAt: String): String {
    if (createdAt.isBlank()) return ""
    return runCatching {
        OffsetDateTime.parse(createdAt)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    }.getOrDefault("")
}

/**
 * 超长文本中间省略：保留开头（目录/项目）与结尾（文件名），中间以 … 代替。
 * 行级 Ellipsis 只会吞掉尾部，路径场景下会丢掉有价值的文件名。
 */
private fun ellipsizeMiddle(text: String, maxChars: Int): String {
    if (text.length <= maxChars) return text
    val left = text.length * 3 / 5
    val right = text.length - left
    return text.take(left) + "…" + text.takeLast(right)
}

/** 下方全量会话列表区域。 */
@Composable
private fun AllSessionsSection(
    sessions: List<WorkbenchSession>,
    loading: Boolean,
    error: String?,
    panel: DecisionPanelState?,
    sendingSessionId: String?,
    voiceActive: Boolean,
    onToggleVoice: () -> Unit,
    recognizedText: String?,
    onTogglePanel: (String) -> Unit,
    onSendQuickReply: (String, String) -> Unit,
    onAnswerQuestion: (String, String, List<List<String>>) -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenSession: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SectionHeader(
            title = stringResource(R.string.workbench_all_sessions),
            count = sessions.size,
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading -> {
                    Text(
                        text = stringResource(R.string.workbench_loading),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    // 展开中的会话：标题行固定置顶，决策面板随列表滚动可查看全部内容。
                    val expandedItem = sessions.firstOrNull { it.session.id == panel?.sessionId }
                    val restSessions = if (expandedItem != null) {
                        sessions.filterNot { it.session.id == expandedItem.session.id }
                    } else {
                        sessions
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (expandedItem != null) {
                            SessionSummaryRow(
                                item = expandedItem,
                                expanded = true,
                                onClick = { onTogglePanel(expandedItem.session.id) },
                                onDeleteSession = { onDeleteSession(expandedItem.session.id) },
                                onOpenSession = { onOpenSession(expandedItem.session.id) },
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (expandedItem != null && panel != null) {
                                item(key = "panel_${expandedItem.session.id}") {
                                    DecisionPanelContent(
                                        panel = panel,
                                        sending = sendingSessionId == expandedItem.session.id,
                                        voiceActive = voiceActive,
                                        onToggleVoice = onToggleVoice,
                                        recognizedText = recognizedText,
                                        onSend = { onSendQuickReply(expandedItem.session.id, it) },
                                        onAnswerQuestion = { requestId, answers ->
                                            onAnswerQuestion(expandedItem.session.id, requestId, answers)
                                        },
                                        onOpenSession = { onOpenSession(expandedItem.session.id) },
                                    )
                                }
                            }
                            items(restSessions, key = { it.session.id }) { item ->
                                WorkbenchSessionCard(
                                    item = item,
                                    expanded = false,
                                    sending = sendingSessionId == item.session.id,
                                    onClick = { onTogglePanel(item.session.id) },
                                    panelContent = null,
                                    voiceActive = voiceActive,
                                    onToggleVoice = onToggleVoice,
                                    recognizedText = recognizedText,
                                    onSendQuickReply = { text -> onSendQuickReply(item.session.id, text) },
                                    onAnswerQuestion = { requestId, answers -> onAnswerQuestion(item.session.id, requestId, answers) },
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

/** 单个会话卡片：顶部为摘要行，展开时下方出现决策面板。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkbenchSessionCard(
    item: WorkbenchSession,
    expanded: Boolean,
    sending: Boolean,
    onClick: () -> Unit,
    panelContent: DecisionPanelState?,
    voiceActive: Boolean,
    onToggleVoice: () -> Unit,
    recognizedText: String?,
    onSendQuickReply: (String) -> Unit,
    onAnswerQuestion: (String, List<List<String>>) -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenSession: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = appAmoledBorder(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SessionSummaryRow(
                item = item,
                expanded = expanded,
                onClick = onClick,
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

/** 会话摘要行：状态点 + 标题 + 路径 + 状态标签 + 展开箭头，长按弹出会话菜单。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionSummaryRow(
    item: WorkbenchSession,
    expanded: Boolean,
    onClick: () -> Unit,
    onDeleteSession: () -> Unit,
    onOpenSession: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
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
                        onLongClick = { showMenu = true },
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusDot(status = item.status)
                if (item.unread) UnreadDot()
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.session.title?.takeIf { it.isNotBlank() }?.replace('\n', ' ')
                            ?: stringResource(R.string.session_untitled),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = buildString {
                        if (item.session.directory.isNotBlank()) {
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
                }
                StatusLabel(status = item.status)
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = quickReply,
                onValueChange = { quickReply = it },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxHeight(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (quickReply.isEmpty()) {
                            Text(
                                text = stringResource(R.string.workbench_quick_reply_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
            IconButton(
                onClick = onToggleVoice,
                enabled = !sending,
            ) {
                Icon(
                    imageVector = if (voiceActive) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = stringResource(R.string.chat_voice_input),
                    tint = if (voiceActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = {
                    val trimmed = quickReply.trim()
                    if (trimmed.isEmpty()) return@IconButton
                    quickReply = ""
                    onSend(trimmed)
                },
                enabled = quickReply.isNotBlank() && !sending,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send))
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
                Text(
                    text = stringResource(R.string.workbench_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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