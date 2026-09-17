/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SessionListScreen.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.sessions

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.FileNode
import org.hiylo.starburst.data.repository.SettingsRepository
import org.hiylo.starburst.domain.model.Project
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.domain.model.SessionStatus
import org.hiylo.starburst.domain.model.SessionCategory
import org.hiylo.starburst.ui.theme.StatusConnected
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import org.hiylo.starburst.ui.components.AppDialog
import org.hiylo.starburst.ui.components.AppDialogShape
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.AppSearchShape
import org.hiylo.starburst.ui.components.AppSecondaryButton
import org.hiylo.starburst.ui.components.SessionCardContent
import org.hiylo.starburst.ui.components.AppCardShape
import org.hiylo.starburst.ui.components.appAmoledBorder
import org.hiylo.starburst.ui.components.appDialogContainerColor
import org.hiylo.starburst.ui.components.appDialogElevation
import org.hiylo.starburst.ui.components.appPopupBorder
import org.hiylo.starburst.ui.components.appPopupContainerColor
import org.hiylo.starburst.ui.components.isAmoledTheme
import org.hiylo.starburst.ui.components.AppLoadingEdge
import org.hiylo.starburst.ui.components.sessionCategoryColor
import org.hiylo.starburst.ui.components.sessionCategoryIcon
import org.hiylo.starburst.ui.screens.settings.SessionCategoriesDialog
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshState
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

internal fun shouldRevealPromotedSession(
    previousTopSessionId: String?,
    currentTopSessionId: String?,
    firstVisibleItemIndex: Int,
    searchActive: Boolean,
): Boolean = previousTopSessionId != null &&
    currentTopSessionId != null &&
    previousTopSessionId != currentTopSessionId &&
    firstVisibleItemIndex <= 2 &&
    !searchActive

internal data class RecentSessionDirectory(
    val directory: String,
    val name: String,
    val count: Int,
    val lastUsed: Long,
)

internal fun recentSessionDirectories(
    sessions: List<SessionItem>,
    limit: Int = 20,
): List<RecentSessionDirectory> = sessions
    .groupBy { it.session.directory.trimEnd('/') }
    .map { (directory, items) ->
        RecentSessionDirectory(
            directory = items.first().session.directory,
            name = directory.substringAfterLast('/').ifEmpty { directory },
            count = items.size,
            lastUsed = items.maxOf { it.session.time.updated },
        )
    }
    .sortedByDescending(RecentSessionDirectory::lastUsed)
    .take(limit)

/** Sort order selectable from the session list filter menu. */
private enum class SessionSortMode { Newest, Oldest, Title }

/** Anchored drag settle positions for the session-row swipe-to-reveal delete action. */
private enum class SessionReveal { Settled, Revealed }

/** Time range filter selectable from the session list filter menu. */
internal enum class SessionTimeFilter {
    All,
    Today,
    LastWeek,
    LastMonth,
}

internal fun sessionTimeCutoff(filter: SessionTimeFilter, now: Long = System.currentTimeMillis()): Long = when (filter) {
    SessionTimeFilter.All -> Long.MIN_VALUE
    SessionTimeFilter.Today -> {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    SessionTimeFilter.LastWeek -> now - 7L * 24 * 60 * 60 * 1000
    SessionTimeFilter.LastMonth -> now - 30L * 24 * 60 * 60 * 1000
}

private fun sessionTimeComparator(sortMode: SessionSortMode): Comparator<SessionItem> = when (sortMode) {
    // 用 lastUserMessageAt（会话状态不变时保持稳定），避免流式更新 time.updated 导致列表跳动。
    SessionSortMode.Newest -> compareByDescending<SessionItem> { it.lastUserMessageAt }
    SessionSortMode.Oldest -> compareBy<SessionItem> { it.lastUserMessageAt }
    SessionSortMode.Title -> compareBy<SessionItem> { it.session.title?.lowercase(Locale.getDefault()).orEmpty() }
}

private fun displaySessionComparator(sortMode: SessionSortMode): Comparator<SessionItem> =
    compareByDescending<SessionItem> { it.isPinned }
        .thenBy { it.pinnedIndex ?: Int.MAX_VALUE }
        .thenByDescending { it.isFavorite }
        .thenBy { it.favoriteIndex ?: Int.MAX_VALUE }
        .then(sessionTimeComparator(sortMode))

private fun sortDisplayGroups(
    groups: List<ProjectSessionGroup>,
    sortMode: SessionSortMode,
): List<ProjectSessionGroup> = groups.sortedWith(
    compareByDescending<ProjectSessionGroup> { group -> group.sessions.any { it.isPinned } }
        .thenBy { group -> group.sessions.mapNotNull { it.pinnedIndex }.minOrNull() ?: Int.MAX_VALUE }
        .then(
            when (sortMode) {
                SessionSortMode.Newest -> compareByDescending<ProjectSessionGroup> {
                    it.sessions.maxOfOrNull { s -> s.lastUserMessageAt } ?: 0L
                }
                SessionSortMode.Oldest -> compareBy<ProjectSessionGroup> {
                    it.sessions.maxOfOrNull { s -> s.lastUserMessageAt } ?: 0L
                }
                SessionSortMode.Title -> compareBy<ProjectSessionGroup> {
                    it.projectName.lowercase(Locale.getDefault())
                }
            },
        ),
)

/** Pulsing dots loading indicator — 3 dots that scale up/down in sequence. */
@Composable
private fun PulsingDotsIndicator(
    modifier: Modifier = Modifier,
    dotSize: androidx.compose.ui.unit.Dp = 10.dp,
    dotSpacing: androidx.compose.ui.unit.Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "pulsing_dots")
    val scales2 = (0..2).map { index ->
        transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1200
                    val offset = index * 150
                    0.4f at 0 + offset
                    1.0f at 300 + offset
                    0.4f at 600 + offset
                    0.4f at 1200
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "dot_scale_$index"
        )
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        scales2.forEach { scale ->
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                        alpha = 0.3f + 0.7f * ((scale.value - 0.4f) / 0.6f)
                    }
                .background(color, CircleShape)
            )
        }
    }
}

@Composable
private fun ServerRefreshEdge(
    state: SwipeRefreshState,
    refreshTriggerDistance: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val triggerPx = with(density) { refreshTriggerDistance.toPx() }.coerceAtLeast(1f)
    val progress = (state.indicatorOffset / triggerPx).coerceIn(0f, 1f)
    AppLoadingEdge(active = state.isRefreshing, progress = progress, modifier = modifier)
}

/**
 * Session List Screen - shows all sessions for a connected server,
 * grouped by project. Tapping a session navigates to the chat screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    onNavigateToChat: (sessionId: String, openTerminal: Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onSwitchServer: (serverId: String) -> Unit = {},
    onOpenBookmarks: (serverId: String) -> Unit = {},
    onOpenFtsSearch: (serverId: String) -> Unit = {},
    onNavigateToWorkbench: () -> Unit = {},
    viewModel: SessionListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val groupByProject by viewModel.groupSessionsByProject.collectAsState()
    val recentDirectoryCount by viewModel.recentDirectoryCount.collectAsState()
    val compactSessions by viewModel.compactSessions.collectAsState()
    val isAmoled = isAmoledTheme()
    val context = LocalContext.current
    // Navigate to newly created session
    LaunchedEffect(viewModel) {
        viewModel.navigateToSession
            .onEach { sessionId ->
                onNavigateToChat(sessionId, false)
            }
            .launchIn(this)
    }

    // Rename dialog state
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameSessionId by remember { mutableStateOf("") }
    var renameText by remember { mutableStateOf("") }

    // Delete confirmation dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteSessionId by remember { mutableStateOf("") }
    var deleteSessionTitle by remember { mutableStateOf("") }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }

    // Archive confirmation dialog state
    var showArchiveDialog by remember { mutableStateOf(false) }
    var archiveSessionId by remember { mutableStateOf("") }
    var archiveSessionTitle by remember { mutableStateOf("") }
    var showPinnedSortDialog by remember { mutableStateOf(false) }
    var showArchiveSelectedDialog by remember { mutableStateOf(false) }

    // Project picker dialog state
    var showOpenProject by remember { mutableStateOf(false) }
    var showQuickNewSession by remember { mutableStateOf(false) }
    var showSessionTemplates by remember { mutableStateOf(false) }
    val sessionTemplates by viewModel.sessionTemplates.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var collapsedProjects by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var sortMode by remember { mutableStateOf(SessionSortMode.Newest) }
    var directoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var timeFilter by rememberSaveable { mutableStateOf(SessionTimeFilter.All) }
    var showFilterMenu by remember { mutableStateOf(false) }
    val recentProjects by viewModel.recentProjects.collectAsState()
    val sessionListState = rememberLazyListState()
    val topSessionId = uiState.sessionGroups.firstOrNull()?.sessions?.firstOrNull()?.session?.id
    var previousTopSessionId by remember { mutableStateOf<String?>(null) }
    val visibleGroups = remember(uiState.sessionGroups, searchQuery, sortMode, directoryFilter, timeFilter) {
        val query = searchQuery.trim()
        val dirFilter = directoryFilter?.trimEnd('/')
        val timeCutoff = sessionTimeCutoff(timeFilter)
        val filtered = uiState.sessionGroups.mapNotNull { group ->
            val afterDirectory = if (dirFilter == null) {
                group.sessions
            } else {
                group.sessions.filter { it.session.directory.trimEnd('/') == dirFilter }
            }
            val afterTime = afterDirectory.filter { it.session.time.updated >= timeCutoff }
            if (query.isEmpty()) {
                group.copy(sessions = afterTime.sortedWith(displaySessionComparator(sortMode)))
                    .takeIf { afterTime.isNotEmpty() }
            } else {
                val projectMatches = group.projectName.contains(query, ignoreCase = true) ||
                    group.directory.contains(query, ignoreCase = true) ||
                    group.branch?.contains(query, ignoreCase = true) == true
                val sessions = if (projectMatches) afterTime else afterTime.filter { item ->
                    item.session.title?.contains(query, ignoreCase = true) == true ||
                        item.session.id.contains(query, ignoreCase = true) ||
                        item.session.directory.contains(query, ignoreCase = true)
                }
                group.copy(sessions = sessions.sortedWith(displaySessionComparator(sortMode)))
                    .takeIf { sessions.isNotEmpty() }
            }
        }
        sortDisplayGroups(filtered, sortMode)
    }

    val recentSessions = remember(visibleGroups, sortMode) {
        visibleGroups
            .flatMap { group -> group.sessions.map { group to it } }
            .sortedWith(
                compareBy(displaySessionComparator(sortMode)) {
                    it: Pair<ProjectSessionGroup, SessionItem> -> it.second
                },
            )
    }

    LaunchedEffect(topSessionId, searchQuery.isNotBlank()) {
        if (shouldRevealPromotedSession(
                previousTopSessionId = previousTopSessionId,
                currentTopSessionId = topSessionId,
                firstVisibleItemIndex = sessionListState.firstVisibleItemIndex,
                searchActive = searchQuery.isNotBlank(),
            )
        ) {
            sessionListState.animateScrollToItem(0)
        }
        previousTopSessionId = topSessionId
    }

    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.clearSelection()
    }

    val allSessions = uiState.sessionGroups.flatMap { it.sessions }

    val directoryOptions = remember(uiState.sessionGroups) {
        allSessions
            .map { it.session.directory.trimEnd('/') }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedBy { it.lowercase(Locale.getDefault()) }
    }

    val recentProjectsForDisplay = remember(recentProjects, uiState.sessionGroups) {
        val recorded = recentProjects.map { it.trimEnd('/') }
        val derived = recentSessionDirectories(uiState.sessionGroups.flatMap { it.sessions })
            .map { it.directory.trimEnd('/') }
        (recorded + derived).distinct().filter { it.isNotBlank() }.take(8)
    }

    // Precompute once per composition instead of per-row (avoids O(n²) inside LazyColumn items).
    val allFavoritesCount = allSessions.count { it.isFavorite }
    val allPinnedCount = allSessions.count { it.isPinned }
    val refreshTriggerDistance = 80.dp
    val swipeRefreshState = rememberSwipeRefreshState(uiState.isLoading && allSessions.isNotEmpty())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Box {
                if (uiState.isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.sessions_selected_count, uiState.selectedIds.size),
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                        }
                    },
                    actions = {
                        TextButton(onClick = { viewModel.selectAll() }) {
                            Text(stringResource(R.string.sessions_select_all))
                        }
                        IconButton(onClick = { showArchiveSelectedDialog = true }) {
                            Icon(
                                Icons.Default.Archive,
                                contentDescription = stringResource(R.string.sessions_archive_selected),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showDeleteSelectedDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.sessions_delete_selected),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                } else {
                TopAppBar(
                    title = {
                        if (searchActive) {
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .clip(AppSearchShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Box(modifier = Modifier.weight(1f)) {
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = stringResource(R.string.search_sessions),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                )
                                            }
                                            innerTextField()
                                        }
                                        if (searchQuery.isNotEmpty()) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(R.string.close),
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { searchQuery = "" },
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                },
                            )
                        } else {
                            Column {
                                Text(
                                    text = uiState.serverName.ifEmpty { stringResource(R.string.sessions_title) },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (searchActive) {
                                    searchActive = false
                                    searchQuery = ""
                                } else {
                                    onNavigateBack()
                                }
                            },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(if (searchActive) R.string.close else R.string.back),
                            )
                        }
                    },
                    actions = {
                        if (!searchActive) {
                            IconButton(onClick = onNavigateToWorkbench) {
                                Icon(
                                    Icons.Default.Dashboard,
                                    contentDescription = stringResource(R.string.workbench_enter),
                                )
                            }
                            IconButton(
                                onClick = { onOpenBookmarks(viewModel.serverId) },
                            ) {
                                Icon(
                                    Icons.Default.BookmarkBorder,
                                    contentDescription = stringResource(R.string.bookmarks_title),
                                )
                            }
                            IconButton(
                                onClick = { onOpenFtsSearch(viewModel.serverId) },
                            ) {
                                Icon(
                                    Icons.Default.ManageSearch,
                                    contentDescription = stringResource(R.string.fts_search_title),
                                )
                            }
                            Box {
                                IconButton(onClick = { showFilterMenu = true }) {
                                    Icon(
                                        Icons.Default.FilterList,
                                        contentDescription = stringResource(R.string.sessions_filter),
                                    )
                                }
                                DropdownMenu(
                                    expanded = showFilterMenu,
                                    onDismissRequest = { showFilterMenu = false },
                                    modifier = Modifier.appPopupBorder(),
                                    containerColor = appPopupContainerColor(),
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sessions_sort_newest)) },
                                        leadingIcon = {
                                            if (sortMode == SessionSortMode.Newest) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            } else {
                                                Icon(Icons.Default.Sort, contentDescription = null)
                                            }
                                        },
                                        onClick = {
                                            sortMode = SessionSortMode.Newest
                                            showFilterMenu = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sessions_sort_oldest)) },
                                        leadingIcon = {
                                            if (sortMode == SessionSortMode.Oldest) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            } else {
                                                Icon(Icons.Default.Sort, contentDescription = null)
                                            }
                                        },
                                        onClick = {
                                            sortMode = SessionSortMode.Oldest
                                            showFilterMenu = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sessions_sort_title)) },
                                        leadingIcon = {
                                            if (sortMode == SessionSortMode.Title) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            } else {
                                                Icon(Icons.Default.SortByAlpha, contentDescription = null)
                                            }
                                        },
                                        onClick = {
                                            sortMode = SessionSortMode.Title
                                            showFilterMenu = false
                                        },
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                                    )
                                    Text(
                                        text = stringResource(R.string.sessions_filter_directory),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sessions_filter_all_directories)) },
                                        leadingIcon = {
                                            if (directoryFilter == null) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            } else {
                                                Icon(Icons.Default.Folder, contentDescription = null)
                                            }
                                        },
                                        onClick = {
                                            directoryFilter = null
                                            showFilterMenu = false
                                        },
                                    )
                                    directoryOptions.forEach { directory ->
                                        val selected = directoryFilter == directory
                                        DropdownMenuItem(
                                            text = { Text(directory.substringAfterLast('/').ifEmpty { directory }) },
                                            leadingIcon = {
                                                if (selected) {
                                                    Icon(Icons.Default.Check, contentDescription = null)
                                                } else {
                                                    Icon(Icons.Default.Folder, contentDescription = null)
                                                }
                                            },
                                            onClick = {
                                                directoryFilter = directory
                                                showFilterMenu = false
                                            },
                                        )
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                                    )
                                    Text(
                                        text = stringResource(R.string.sessions_filter_time),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    )
                                    SessionTimeFilter.entries.forEach { filter ->
                                        val selected = timeFilter == filter
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(
                                                        when (filter) {
                                                            SessionTimeFilter.All -> R.string.sessions_time_all
                                                            SessionTimeFilter.Today -> R.string.sessions_time_today
                                                            SessionTimeFilter.LastWeek -> R.string.sessions_time_week
                                                            SessionTimeFilter.LastMonth -> R.string.sessions_time_month
                                                        },
                                                    ),
                                                )
                                            },
                                            leadingIcon = {
                                                if (selected) {
                                                    Icon(Icons.Default.Check, contentDescription = null)
                                                } else {
                                                    Icon(Icons.Default.Schedule, contentDescription = null)
                                                }
                                            },
                                            onClick = {
                                                timeFilter = filter
                                                showFilterMenu = false
                                            },
                                        )
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    if (groupByProject) R.string.sessions_view_recent
                                                    else R.string.sessions_view_projects,
                                                ),
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (groupByProject) {
                                                    Icons.AutoMirrored.Filled.ViewList
                                                } else {
                                                    Icons.Default.Folder
                                                },
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            viewModel.setGroupSessionsByProject(!groupByProject)
                                            showFilterMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                    )
                }
                ServerRefreshEdge(
                    state = swipeRefreshState,
                    refreshTriggerDistance = refreshTriggerDistance,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        },
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                SmallFloatingActionButton(
                    onClick = {
                        // If there are known projects, show the quick dialog first;
                        // otherwise go straight to the full directory browser.
                        if (uiState.sessionGroups.isNotEmpty()) {
                            showQuickNewSession = true
                        } else {
                            showOpenProject = true
                        }
                    },
                    containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.primary,
                    contentColor = if (isAmoled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
                    elevation = if (isAmoled) {
                        FloatingActionButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp
                        )
                    } else {
                        FloatingActionButtonDefaults.elevation()
                    },
                    modifier = if (isAmoled) {
                        Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            shape = FloatingActionButtonDefaults.shape
                        )
                    } else {
                        Modifier
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.sessions_new))
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            // Multi-server one-tap switcher: only shown when more than one server is configured.
            if (uiState.servers.size > 1 && !uiState.isSelectionMode && !searchActive) {
                ServerSwitcherRow(
                    servers = uiState.servers,
                    currentServerId = viewModel.serverId,
                    connectedServerIds = uiState.connectedServerIds,
                    onSwitchServer = onSwitchServer,
                )
            }
            SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = viewModel::loadSessions,
            swipeEnabled = !uiState.isSelectionMode && !uiState.isLoading,
            refreshTriggerDistance = refreshTriggerDistance,
            modifier = Modifier.fillMaxSize().weight(1f),
            indicator = { _, _ -> },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .graphicsLayer { translationY = swipeRefreshState.indicatorOffset * 0.45f },
            ) {
                when {
                uiState.isLoading && allSessions.isEmpty() -> {
                    PulsingDotsIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        dotSize = 12.dp,
                        dotSpacing = 8.dp
                    )
                }
                uiState.error != null && allSessions.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = uiState.error ?: stringResource(R.string.session_unknown_error),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        AppPrimaryButton(onClick = { viewModel.loadSessions() }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
                allSessions.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Text(
                            text = stringResource(R.string.sessions_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = stringResource(R.string.sessions_tap_plus),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = sessionListState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(if (compactSessions) 6.dp else 10.dp)
                    ) {
                        if (recentProjectsForDisplay.isNotEmpty() && searchQuery.isBlank() && !uiState.isSelectionMode) {
                            item(key = "recent-projects") {
                                RecentProjectsRow(
                                    directories = recentProjectsForDisplay,
                                    onSelect = { directory ->
                                        viewModel.openRecentProject(directory)
                                    },
                                )
                            }
                        }
                        if (visibleGroups.isEmpty()) {
                            item(key = "no-search-results") {
                                Text(
                                    text = stringResource(
                                        if (searchActive) R.string.sessions_no_search_results else R.string.sessions_empty,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (!groupByProject) {
                            items(
                                recentSessions,
                                key = { (_, item) -> item.session.id },
                                contentType = { it::class },
                            ) { (group, item) ->
                                val untitledLabel = stringResource(R.string.session_untitled)
                                SessionRow(
                                    item = item,
                                    projectName = group.sessionDirLabels[item.session.id] ?: group.directory,
                                    compact = compactSessions,
                                    isSelectionMode = uiState.isSelectionMode,
                                    isSelected = item.session.id in uiState.selectedIds,
                                    favoriteCount = allFavoritesCount,
                                    pinnedCount = allPinnedCount,
                                    categories = uiState.categories,
                                    onClick = {
                                        if (uiState.isSelectionMode) {
                                            viewModel.toggleSelection(item.session.id)
                                        } else {
                                            viewModel.recordRecentProject(item.session.directory)
                                            onNavigateToChat(item.session.id, false)
                                        }
                                    },
                                    onLongClick = { viewModel.toggleSelection(item.session.id) },
                                    onToggleFavorite = { viewModel.toggleFavorite(item.session.id) },
                                    onMoveFavorite = { offset -> viewModel.moveFavorite(item.session.id, offset) },
                                    onTogglePin = { viewModel.togglePin(item.session.id) },
                                    onMovePinned = { offset -> viewModel.movePinned(item.session.id, offset) },
                                    onSortPinned = { showPinnedSortDialog = true },
                                    onSetCategory = { categoryId ->
                                        viewModel.setSessionCategory(item.session.id, categoryId)
                                    },
                                    onSaveCategory = viewModel::saveSessionCategory,
                                    onDeleteCategory = viewModel::deleteSessionCategory,
                                    onRename = {
                                        renameSessionId = item.session.id
                                        renameText = item.session.title ?: ""
                                        showRenameDialog = true
                                    },
                                    onDelete = {
                                        deleteSessionId = item.session.id
                                        deleteSessionTitle = item.session.title ?: untitledLabel
                                        showDeleteDialog = true
                                    },
                                    onArchive = {
                                        archiveSessionId = item.session.id
                                        archiveSessionTitle = item.session.title ?: untitledLabel
                                        showArchiveDialog = true
                                    },
                                )
                            }
                        } else {
                            for (group in visibleGroups) {
                                val expanded = searchQuery.isNotBlank() || group.projectId !in collapsedProjects
                                item(key = "project-${group.projectId}") {
                                ProjectHeader(
                                    name = group.projectName,
                                    directory = group.directory,
                                    branch = group.branch,
                                    sessionCount = group.sessions.size,
                                    expanded = expanded,
                                    onToggle = {
                                        collapsedProjects = if (group.projectId in collapsedProjects) {
                                            collapsedProjects - group.projectId
                                        } else {
                                            collapsedProjects + group.projectId
                                        }
                                    },
                                    onNewSession = { viewModel.createNewSession(group.directory) },
                                )
                            }
                                if (expanded) items(group.sessions, key = { it.session.id }) { item ->
                                val untitledLabel = stringResource(R.string.session_untitled)
                                val dirLabel = group.sessionDirLabels[item.session.id]
                                    ?.takeIf { item.session.directory.trimEnd('/') != group.directory.trimEnd('/') }
                                SessionRow(
                                    item = item,
                                    projectName = dirLabel,
                                    compact = compactSessions,
                                    isSelectionMode = uiState.isSelectionMode,
                                    isSelected = item.session.id in uiState.selectedIds,
                                    favoriteCount = allFavoritesCount,
                                    pinnedCount = allPinnedCount,
                                    categories = uiState.categories,
                                    onClick = {
                                        if (uiState.isSelectionMode) {
                                            viewModel.toggleSelection(item.session.id)
                                        } else {
                                            viewModel.recordRecentProject(item.session.directory)
                                            onNavigateToChat(item.session.id, false)
                                        }
                                    },
                                    onLongClick = { viewModel.toggleSelection(item.session.id) },
                                    onToggleFavorite = { viewModel.toggleFavorite(item.session.id) },
                                    onMoveFavorite = { offset -> viewModel.moveFavorite(item.session.id, offset) },
                                    onTogglePin = { viewModel.togglePin(item.session.id) },
                                    onMovePinned = { offset -> viewModel.movePinned(item.session.id, offset) },
                                    onSortPinned = { showPinnedSortDialog = true },
                                    onSetCategory = { categoryId ->
                                        viewModel.setSessionCategory(item.session.id, categoryId)
                                    },
                                    onSaveCategory = viewModel::saveSessionCategory,
                                    onDeleteCategory = viewModel::deleteSessionCategory,
                                    onRename = {
                                        renameSessionId = item.session.id
                                        renameText = item.session.title ?: ""
                                        showRenameDialog = true
                                    },
                                    onDelete = {
                                        deleteSessionId = item.session.id
                                        deleteSessionTitle = item.session.title ?: untitledLabel
                                        showDeleteDialog = true
                                    },
                                    onArchive = {
                                        archiveSessionId = item.session.id
                                        archiveSessionTitle = item.session.title ?: untitledLabel
                                        showArchiveDialog = true
                                    },
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

    // Quick new session dialog (recent projects)
    if (showQuickNewSession) {
        val allSessions = uiState.sessionGroups.flatMap { it.sessions }
        NewSessionQuickDialog(
            sessions = allSessions,
            limit = recentDirectoryCount,
            onSelectDirectory = { directory ->
                showQuickNewSession = false
                viewModel.createNewSession(directory = directory)
            },
            onOpenTemplates = { showSessionTemplates = true },
            onBrowse = {
                showQuickNewSession = false
                showOpenProject = true
            },
            onDismiss = { showQuickNewSession = false }
        )
    }

    // Session templates management dialog
    if (showSessionTemplates) {
        SessionTemplatesDialog(
            templates = sessionTemplates,
            onCreate = { template ->
                showSessionTemplates = false
                showQuickNewSession = false
                viewModel.createSessionFromTemplate(template)
            },
            onSave = viewModel::saveSessionTemplate,
            onDelete = viewModel::deleteSessionTemplate,
            onMove = viewModel::moveSessionTemplate,
            onDismiss = { showSessionTemplates = false },
        )
    }

    // Open Project directory browser dialog
    if (showOpenProject) {
        OpenProjectDialog(
            viewModel = viewModel,
            projects = uiState.projects,
            onSelect = { directory ->
                showOpenProject = false
                viewModel.createNewSession(directory = directory)
            },
            onDismiss = { showOpenProject = false }
        )
    }

    if (showPinnedSortDialog) {
        PinnedSortDialog(
            pinnedSessions = allSessions.filter { it.isPinned }.sortedBy { it.pinnedIndex },
            onReorder = { orderedIds -> viewModel.reorderPinned(orderedIds) },
            onDismiss = { showPinnedSortDialog = false },
        )
    }

    if (showDeleteSelectedDialog) {
        AppDialog(onDismissRequest = { showDeleteSelectedDialog = false }, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sessions_delete_selected),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(stringResource(R.string.sessions_delete_selected_confirm, uiState.selectedIds.size))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        AppSecondaryButton(onClick = { showDeleteSelectedDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        AppPrimaryButton(
                            onClick = {
                                viewModel.deleteSelected()
                                showDeleteSelectedDialog = false
                            },
                            destructive = true,
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                }
        }
    }

    // Rename dialog
    if (showRenameDialog) {
        AppDialog(onDismissRequest = { showRenameDialog = false }, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.session_rename),
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text(stringResource(R.string.session_rename_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        AppSecondaryButton(onClick = { showRenameDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        AppPrimaryButton(
                            onClick = {
                                viewModel.renameSession(renameSessionId, renameText)
                                showRenameDialog = false
                            },
                            enabled = renameText.isNotBlank()
                        ) {
                            Text(stringResource(R.string.session_rename_button))
                        }
                    }
                }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AppDialog(onDismissRequest = { showDeleteDialog = false }, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.session_delete),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(stringResource(R.string.session_delete_confirm, deleteSessionTitle))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        AppSecondaryButton(onClick = { showDeleteDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        AppPrimaryButton(
                            onClick = {
                                viewModel.deleteSession(deleteSessionId)
                                showDeleteDialog = false
                            },
                            destructive = true,
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                }
        }
    }

    // Archive selected dialog
    if (showArchiveSelectedDialog) {
        AppDialog(onDismissRequest = { showArchiveSelectedDialog = false }, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sessions_archive_selected),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(stringResource(R.string.sessions_archive_selected_confirm, uiState.selectedIds.size))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        AppSecondaryButton(onClick = { showArchiveSelectedDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        AppPrimaryButton(
                            onClick = {
                                viewModel.archiveSelected()
                                showArchiveSelectedDialog = false
                            },
                        ) {
                            Text(stringResource(R.string.session_archive))
                        }
                    }
                }
        }
    }

    // Archive confirmation dialog
    if (showArchiveDialog) {
        AppDialog(onDismissRequest = { showArchiveDialog = false }, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.session_archive),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(stringResource(R.string.session_archive_confirm, archiveSessionTitle))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        AppSecondaryButton(onClick = { showArchiveDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        AppPrimaryButton(
                            onClick = {
                                viewModel.archiveSession(archiveSessionId)
                                showArchiveDialog = false
                            },
                        ) {
                            Text(stringResource(R.string.session_archive))
                        }
                    }
                }
        }
    }
}

@Composable
private fun ServerSwitcherRow(
    servers: List<ServerConfig>,
    currentServerId: String,
    connectedServerIds: Set<String>,
    onSwitchServer: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Dns,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        servers.forEach { server ->
            val isCurrent = server.id == currentServerId
            val isConnected = server.id in connectedServerIds
            SuggestionChip(
                onClick = { if (!isCurrent) onSwitchServer(server.id) },
                enabled = !isCurrent && isConnected,
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (isConnected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isCurrent) MaterialTheme.colorScheme.primary else StatusConnected,
                            )
                        } else {
                            Icon(
                                Icons.Default.Circle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        Text(
                            server.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun RecentProjectsRow(
    directories: List<String>,
    onSelect: (String) -> Unit,
) {
    if (directories.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(
            text = stringResource(R.string.sessions_recent_projects),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(directories, key = { it }) { directory ->
                val name = directory.substringAfterLast('/').ifEmpty { directory }
                SuggestionChip(
                    onClick = { onSelect(directory) },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProjectHeader(
    name: String,
    directory: String,
    branch: String?,
    sessionCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onNewSession: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(top = 12.dp, bottom = 8.dp, start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name.replace('\n', ' '),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!branch.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = branch,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.sessions_project_summary, directory, sessionCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onNewSession) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.sessions_new_in_project))
        }
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Directory browser dialog for opening a project.
 * Shows: known projects at top, then browsable server filesystem.
 * Supports search and tap-to-navigate into subdirectories.
 */
@Composable
private fun OpenProjectDialog(
    viewModel: SessionListViewModel,
    projects: List<Project>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isAmoled = isAmoledTheme()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val savedPaths by viewModel.savedPaths.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var currentDir by remember { mutableStateOf<String?>(null) }
    var homeDir by remember { mutableStateOf<String?>(null) }
    var directories by remember { mutableStateOf<List<FileNode>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var isCreatingFolder by remember { mutableStateOf(false) }
    var createFolderError by remember { mutableStateOf<String?>(null) }
    var showAddSavedPathDialog by remember { mutableStateOf(false) }
    var newPathInput by remember { mutableStateOf("") }
    var addSavedPathError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    val isSearching = searchQuery.isNotBlank()

    // Load home directory and initial listing
    LaunchedEffect(Unit) {
        val home = viewModel.getHomeDirectory()
        homeDir = home
        currentDir = home
        isLoading = true
        directories = viewModel.listDirectories(home)
        isLoading = false
    }

    // Re-list when currentDir changes
    LaunchedEffect(currentDir) {
        val dir = currentDir ?: return@LaunchedEffect
        if (searchQuery.isBlank()) {
            isLoading = true
            directories = viewModel.listDirectories(dir)
            isLoading = false
        }
    }

    // Search debounce
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            // Re-list current dir
            currentDir?.let {
                isLoading = true
                directories = viewModel.listDirectories(it)
                isLoading = false
            }
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(300)
        isLoading = true
        val baseDir = homeDir ?: "/"
        searchResults = viewModel.searchDirectories(searchQuery, baseDir)
        isLoading = false
    }

    // Focus the search field
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    /** Shorten an absolute path by replacing home prefix with ~ */
    fun tildeReplace(path: String): String {
        val home = homeDir ?: return path
        return if (path.startsWith(home)) "~" + path.removePrefix(home) else path
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.75f),
            shape = AppDialogShape,
            color = appDialogContainerColor(),
            border = appAmoledBorder(),
            tonalElevation = appDialogElevation(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.sessions_open_project),
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                // Search field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(AppSearchShape)
                        .background(
                            if (isAmoled) {
                                Color.Black
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                        .then(
                            if (isAmoled) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                                    shape = AppSearchShape
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.sessions_search_folders),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.chat_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }

                // Breadcrumb / current path (when not searching)
                if (!isSearching && currentDir != null) {
                    val canGoUp = currentDir != "/" && currentDir != homeDir
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 20.dp, vertical = 4.dp)
                            .then(
                                if (canGoUp) Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        // Navigate up
                                        val parent = currentDir!!.trimEnd('/').substringBeforeLast('/')
                                        currentDir = parent.ifEmpty { "/" }
                                    } else Modifier
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (canGoUp) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            text = tildeReplace(currentDir ?: "/"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    // Content
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                PulsingDotsIndicator(dotSize = 10.dp, dotSpacing = 6.dp)
                            }
                        }
                        isSearching -> {
                            // Search results
                            if (searchResults.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.sessions_no_folders),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    items(searchResults) { path ->
                                        val absolutePath = path.trimEnd('/').ifEmpty { "/" }
                                        DirectoryRow(
                                            displayPath = tildeReplace(absolutePath) + "/",
                                            onClick = { onSelect(absolutePath) },
                                            onNavigate = {
                                                // Navigate into this directory for further browsing
                                                searchQuery = ""
                                                currentDir = absolutePath
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            // Directory listing
                            val showKnownProjects = currentDir == homeDir && projects.isNotEmpty()

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                // Common paths pinned by the user
                                item(key = "saved-paths-header") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 20.dp, end = 8.dp, top = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.sessions_saved_paths),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(
                                            onClick = {
                                                newPathInput = ""
                                                addSavedPathError = null
                                                showAddSavedPathDialog = true
                                            },
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = stringResource(R.string.sessions_add_saved_path),
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                                items(savedPaths, key = { it }) { path ->
                                    SavedPathRow(
                                        displayPath = tildeReplace(path) + "/",
                                        onClick = {
                                            // Navigate into the path so the projects below it are listed
                                            searchQuery = ""
                                            currentDir = path
                                        },
                                        onRemove = { viewModel.removeSavedPath(path) },
                                    )
                                }
                                item(key = "saved-paths-divider") {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    )
                                }
                                if (directories.isEmpty() && !showKnownProjects) {
                                    item(key = "empty-directory") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 40.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = stringResource(R.string.sessions_empty_directory),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            )
                                        }
                                    }
                                }
                                items(directories, key = { it.name }) { node ->
                                    val absPath = node.absolute ?: "${currentDir?.trimEnd('/')}/${node.name}"
                                    DirectoryRow(
                                        displayPath = tildeReplace(absPath) + "/",
                                        onNavigate = {
                                            // Navigate into this directory
                                            currentDir = absPath
                                        },
                                        onClick = { onSelect(absPath) }
                                    )
                                }
                            }
                        }
                    }

                    FloatingActionButton(
                        onClick = {
                            showCreateFolderDialog = true
                            createFolderError = null
                            if (newFolderName.isBlank()) newFolderName = ""
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(16.dp)
                            .then(if (isAmoled) Modifier.appPopupBorder(FloatingActionButtonDefaults.shape) else Modifier),
                        containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isAmoled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer,
                        elevation = if (isAmoled) {
                            FloatingActionButtonDefaults.elevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp,
                                focusedElevation = 0.dp,
                                hoveredElevation = 0.dp,
                            )
                        } else {
                            FloatingActionButtonDefaults.elevation()
                        },
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.sessions_create_folder))
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        AppDialog(
            onDismissRequest = {
                if (!isCreatingFolder) showCreateFolderDialog = false
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.sessions_create_folder_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = {
                            newFolderName = it
                            createFolderError = null
                        },
                        singleLine = true,
                        enabled = !isCreatingFolder,
                        label = { Text(stringResource(R.string.sessions_create_folder_name_label)) },
                        placeholder = { Text(stringResource(R.string.sessions_create_folder_name_placeholder)) },
                        isError = createFolderError != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (createFolderError != null) {
                        Text(
                            text = createFolderError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    AppSecondaryButton(
                        onClick = { showCreateFolderDialog = false },
                        enabled = !isCreatingFolder,
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    AppPrimaryButton(
                        onClick = {
                            val parent = (searchQuery.trim().takeIf { it.isNotBlank() }
                                ?: (currentDir ?: homeDir ?: "/"))
                                .trimEnd('/').ifEmpty { "/" }
                            val name = newFolderName.trim()
                            if (name.isBlank()) {
                                createFolderError = context.getString(R.string.sessions_create_folder_invalid_name)
                                return@AppPrimaryButton
                            }

                            isCreatingFolder = true
                            scope.launch {
                                val result = viewModel.createDirectory(parent, name)
                                isCreatingFolder = false
                                result.onSuccess { createdPath ->
                                    showCreateFolderDialog = false
                                    newFolderName = ""
                                    createFolderError = null
                                    searchQuery = ""
                                    currentDir = parent
                                    directories = viewModel.listDirectories(parent)
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.sessions_create_folder_success, tildeReplace(createdPath)),
                                            Toast.LENGTH_SHORT,
                                        )
                                        .show()
                                }.onFailure { error ->
                                    createFolderError = error.message ?: context.getString(R.string.sessions_create_folder_failed)
                                }
                            }
                        },
                        enabled = !isCreatingFolder,
                    ) {
                        if (isCreatingFolder) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.sessions_create_folder_create))
                        }
                    }
                }
            }
        }
    }

    if (showAddSavedPathDialog) {
        AppDialog(
            onDismissRequest = { showAddSavedPathDialog = false },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.sessions_add_saved_path_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newPathInput,
                        onValueChange = {
                            newPathInput = it
                            addSavedPathError = null
                        },
                        singleLine = true,
                        label = { Text(stringResource(R.string.sessions_add_saved_path_hint)) },
                        placeholder = { Text(stringResource(R.string.sessions_add_saved_path_placeholder)) },
                        isError = addSavedPathError != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (addSavedPathError != null) {
                        Text(
                            text = addSavedPathError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    AppSecondaryButton(onClick = { showAddSavedPathDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                    AppPrimaryButton(
                        onClick = {
                            val trimmed = newPathInput.trim().trimEnd('/')
                            if (trimmed.isBlank()) {
                                addSavedPathError = context.getString(R.string.sessions_add_saved_path_invalid)
                                return@AppPrimaryButton
                            }
                            viewModel.addSavedPath(trimmed)
                            showAddSavedPathDialog = false
                            newPathInput = ""
                            addSavedPathError = null
                        },
                    ) {
                        Text(stringResource(R.string.sessions_add_saved_path))
                    }
                }
            }
        }
    }
}

/**
 * A single directory row in the browser.
 * Tap to select. Has a chevron to navigate into the directory.
 */
@Composable
private fun DirectoryRow(
    displayPath: String,
    onClick: () -> Unit,
    onNavigate: (() -> Unit)? = null
) {
    // Split into parent + leaf for styling
    val trimmed = displayPath.trimEnd('/')
    val lastSlash = trimmed.lastIndexOf('/')
    val parent = if (lastSlash > 0) trimmed.substring(0, lastSlash + 1) else ""
    val leaf = if (lastSlash >= 0) trimmed.substring(lastSlash + 1) else trimmed
    val trailing = if (displayPath.endsWith("/")) "/" else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Text(
            text = buildAnnotatedString {
                if (parent.isNotEmpty()) {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))) {
                        append(parent)
                    }
                }
                withStyle(SpanStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )) {
                    append(leaf)
                }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))) {
                    append(trailing)
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
            if (onNavigate != null) {
                IconButton(
                    onClick = onNavigate,
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = stringResource(R.string.open),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
    }
}

/**
 * A user-pinned common path in the Open Project dialog.
 * Tapping the row navigates into the path so its subdirectories are listed below.
 */
@Composable
private fun SavedPathRow(
    displayPath: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Default.Star,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Text(
            text = displayPath.trimEnd('/'),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.sessions_remove_saved_path),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * Quick-start dialog for creating a new session.
 * Groups sessions by their directory to show unique project folders,
 * sorted by most recently used. One tap creates a session in that folder.
 * A "Browse..." row at the bottom opens the full directory picker.
 */
@Composable
private fun NewSessionQuickDialog(
    sessions: List<SessionItem>,
    limit: Int,
    onSelectDirectory: (String) -> Unit,
    onOpenTemplates: () -> Unit,
    onBrowse: () -> Unit,
    onDismiss: () -> Unit
) {
    val dirEntries = remember(sessions, limit) {
        recentSessionDirectories(sessions, limit)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight(),
            shape = AppDialogShape,
            color = appDialogContainerColor(),
            border = appAmoledBorder(),
            tonalElevation = appDialogElevation(),
        ) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                // Header
                Text(
                    text = stringResource(R.string.sessions_new_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    items(dirEntries, key = { it.directory }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectDirectory(entry.directory) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = entry.directory.trimEnd('/'),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${entry.count}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // Divider
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                // "Open other project..." row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBrowse() }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = stringResource(R.string.sessions_open_other_project),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // "Session templates..." row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTemplates() }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = stringResource(R.string.sessions_session_templates),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 会话模板管理弹窗：一键新建 + 新增/编辑/删除/排序。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionTemplatesDialog(
    templates: List<SettingsRepository.SessionTemplate>,
    onCreate: (SettingsRepository.SessionTemplate) -> Unit,
    onSave: (SettingsRepository.SessionTemplate) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var directory by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var modelProviderId by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var showBlankError by remember { mutableStateOf(false) }

    fun clearForm() {
        editingId = null
        name = ""
        directory = ""
        systemPrompt = ""
        modelProviderId = ""
        modelId = ""
        prompt = ""
        showBlankError = false
    }

    AppDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.session_template_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (templates.isEmpty()) {
                Text(
                    text = stringResource(R.string.session_template_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    templates.forEachIndexed { index, template ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onCreate(template) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = template.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val summary = buildString {
                                    if (template.directory.isNotBlank()) append(template.directory)
                                    val model = listOf(template.modelProviderId, template.modelId)
                                        .filter(String::isNotBlank)
                                        .joinToString(":")
                                    if (model.isNotBlank()) {
                                        if (isNotEmpty()) append("  ·  ")
                                        append(model)
                                    }
                                }
                                if (summary.isNotBlank()) {
                                    Text(
                                        text = summary,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (index > 0) {
                                IconButton(onClick = { onMove(template.id, -1) }, modifier = Modifier.size(26.dp)) {
                                    Icon(
                                        Icons.Default.ArrowUpward,
                                        contentDescription = stringResource(R.string.template_move_up),
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (index < templates.lastIndex) {
                                IconButton(onClick = { onMove(template.id, 1) }, modifier = Modifier.size(26.dp)) {
                                    Icon(
                                        Icons.Default.ArrowDownward,
                                        contentDescription = stringResource(R.string.template_move_down),
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    editingId = template.id
                                    name = template.name
                                    directory = template.directory
                                    systemPrompt = template.systemPrompt
                                    modelProviderId = template.modelProviderId
                                    modelId = template.modelId
                                    prompt = template.prompt
                                    showBlankError = false
                                },
                                modifier = Modifier.size(26.dp),
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.session_template_edit),
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onDelete(template.id) }, modifier = Modifier.size(26.dp)) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.session_template_delete),
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; showBlankError = false },
                label = { Text(stringResource(R.string.session_template_name_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
            OutlinedTextField(
                value = directory,
                onValueChange = { directory = it; showBlankError = false },
                label = { Text(stringResource(R.string.session_template_directory_label)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text(stringResource(R.string.session_template_system_prompt_label)) },
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = modelProviderId,
                    onValueChange = { modelProviderId = it },
                    label = { Text(stringResource(R.string.session_template_model_provider_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text(stringResource(R.string.session_template_model_id_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text(stringResource(R.string.session_template_prompt_label)) },
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
            if (showBlankError) {
                Text(
                    text = stringResource(R.string.session_template_save_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppSecondaryButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                Spacer(Modifier.width(8.dp))
                if (editingId != null) {
                    AppSecondaryButton(onClick = ::clearForm) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                }
                AppPrimaryButton(
                    onClick = {
                        val trimmedName = name.trim()
                        val trimmedDir = directory.trim()
                        if (trimmedName.isEmpty() || trimmedDir.isEmpty()) {
                            showBlankError = true
                        } else {
                            onSave(
                                SettingsRepository.SessionTemplate(
                                    id = editingId.orEmpty(),
                                    name = trimmedName,
                                    directory = trimmedDir,
                                    systemPrompt = systemPrompt,
                                    modelProviderId = modelProviderId.trim(),
                                    modelId = modelId.trim(),
                                    prompt = prompt,
                                ),
                            )
                            clearForm()
                        }
                    },
                    enabled = name.isNotBlank() && directory.isNotBlank(),
                ) {
                    Text(stringResource(if (editingId != null) R.string.session_template_save else R.string.session_template_add))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PinnedSortDialog(
    pinnedSessions: List<SessionItem>,
    onReorder: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val isAmoled = isAmoledTheme()
    val haptic = LocalHapticFeedback.current
    var order by remember(pinnedSessions) {
        mutableStateOf(pinnedSessions.map { it.session.id })
    }
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        scrollThreshold = 72.dp,
    ) { from, to ->
        if (from.index !in order.indices || to.index !in order.indices) {
            return@rememberReorderableLazyListState
        }
        order = order.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.session_pin_sort_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                text = stringResource(R.string.session_pin_sort_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(order, key = { _, id -> id }) { index, id ->
                    val item = pinnedSessions.firstOrNull { it.session.id == id } ?: return@itemsIndexed
                    ReorderableItem(reorderableState, key = id) { isDragged ->
                        val interactionSource = remember(id) { MutableInteractionSource() }
                        val containerColor = if (isAmoled) {
                            Color.Black
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                        Surface(
                            shape = AppCardShape,
                            color = containerColor,
                            border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .longPressDraggableHandle(
                                    interactionSource = interactionSource,
                                    onDragStarted = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                )
                                .graphicsLayer {
                                    if (isDragged) {
                                        scaleX = 1.02f
                                        scaleY = 1.02f
                                        shadowElevation = 8.dp.toPx()
                                    }
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.session.title?.takeIf { it.isNotBlank() }
                                            ?: stringResource(R.string.session_untitled),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (item.session.directory.isNotBlank()) {
                                        Text(
                                            text = item.session.directory,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Default.Sort,
                                    contentDescription = stringResource(R.string.session_pin_sort),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppSecondaryButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(12.dp))
                AppPrimaryButton(onClick = {
                    onReorder(order)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.session_pin_sort_apply))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    item: SessionItem,
    projectName: String? = null,
    compact: Boolean = false,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    favoriteCount: Int,
    pinnedCount: Int,
    categories: List<SessionCategory>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoveFavorite: (Int) -> Unit,
    onTogglePin: () -> Unit,
    onMovePinned: (Int) -> Unit,
    onSortPinned: () -> Unit = {},
    onSetCategory: (String?) -> Unit,
    onSaveCategory: (String?, String, String, String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
) {
    val isAmoled = isAmoledTheme()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var showActions by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    val cardContent: @Composable () -> Unit = {
        val containerColor = if (isSelected) {
            if (isAmoled) {
                Color.Black
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            }
        } else {
            if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
        }

        val cardColors = CardDefaults.cardColors(containerColor = containerColor)

        val cardBorder = when {
            isSelected -> BorderStroke(
                1.5.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = if (isAmoled) 0.75f else 0.5f)
            )
            isAmoled -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            else -> null
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            colors = cardColors,
            border = cardBorder,
            shape = AppCardShape,
        ) {
            Column {
                SessionCardContent(
                    session = item.session,
                    status = item.status,
                    isFavorite = item.isFavorite,
                    category = item.category,
                    contextLabel = projectName.orEmpty(),
                    isPinned = item.isPinned,
                    compact = compact,
                    leadingContent = {
                        AnimatedVisibility(
                            visible = isSelectionMode,
                            enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                            exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onClick() },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                    },
                    trailingContent = {
                        if (!isSelectionMode) {
                            Box {
                        IconButton(onClick = { showActions = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(
                            expanded = showActions,
                            onDismissRequest = { showActions = false },
                            modifier = Modifier.appPopupBorder(),
                            containerColor = appPopupContainerColor(),
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(if (item.isFavorite) R.string.session_favorite_remove else R.string.session_favorite_add))
                                },
                                leadingIcon = {
                                    Icon(
                                        if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    showActions = false
                                    onToggleFavorite()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.session_category)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = item.category?.let { sessionCategoryIcon(it.icon) }
                                            ?: Icons.Default.Label,
                                        contentDescription = null,
                                        tint = item.category?.let { sessionCategoryColor(it.color) }
                                            ?: LocalContentColor.current,
                                    )
                                },
                                onClick = {
                                    showActions = false
                                    showCategoryPicker = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(if (item.isPinned) R.string.session_unpin else R.string.session_pin)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.PushPin,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    showActions = false
                                    onTogglePin()
                                },
                            )
                            if (item.isFavorite) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.session_favorite_move_up)) },
                                    leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                                    enabled = (item.favoriteIndex ?: 0) > 0,
                                    onClick = {
                                        showActions = false
                                        onMoveFavorite(-1)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.session_favorite_move_down)) },
                                    leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                                    enabled = (item.favoriteIndex ?: Int.MAX_VALUE) < favoriteCount - 1,
                                    onClick = {
                                        showActions = false
                                        onMoveFavorite(1)
                                    },
                                )
                            }
                            if (item.isPinned) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.session_pin_move_up)) },
                                    leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                                    enabled = (item.pinnedIndex ?: 0) > 0,
                                    onClick = {
                                        showActions = false
                                        onMovePinned(-1)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.session_pin_move_down)) },
                                    leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                                    enabled = (item.pinnedIndex ?: Int.MAX_VALUE) < pinnedCount - 1,
                                    onClick = {
                                        showActions = false
                                        onMovePinned(1)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.session_pin_sort)) },
                                    leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                                    enabled = pinnedCount > 1,
                                    onClick = {
                                        showActions = false
                                        onSortPinned()
                                    },
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.session_copy_id)) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    showActions = false
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(item.session.id))
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.chat_copied_clipboard),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.session_rename)) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showActions = false
                                    onRename()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.session_archive)) },
                                leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                                onClick = {
                                    showActions = false
                                    onArchive()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    showActions = false
                                    onDelete()
                                },
                            )
                        }
                            }
                        }
                    },
                )
            }
        }
    }

    // Swipe left to reveal a delete button; tapping it opens the confirm dialog.
    // 使用 anchoredDraggable 限定滑动距离（仅露出固定宽度删除按钮），而不是整卡完全滑出。
    // 允许滑回复位，点击展开区域也可复位，避免误触展开后无法清理。
    val dismissGestureEnabled = !isSelectionMode
    val deleteRevealWidth = 72.dp
    val revealState = remember(dismissGestureEnabled) {
        val widthPx = with(density) { deleteRevealWidth.toPx() }
        AnchoredDraggableState<SessionReveal>(
            initialValue = SessionReveal.Settled,
            anchors = DraggableAnchors {
                SessionReveal.Settled at 0f
                SessionReveal.Revealed at -widthPx
            },
            positionalThreshold = { totalDistance -> totalDistance * 0.35f },
            velocityThreshold = { with(density) { 125.dp.toPx() } },
            snapAnimationSpec = tween(durationMillis = 220),
            decayAnimationSpec = exponentialDecay(),
            confirmValueChange = { value ->
                if (dismissGestureEnabled) {
                    value == SessionReveal.Revealed || value == SessionReveal.Settled
                } else {
                    value == SessionReveal.Settled
                }
            },
        )
    }
    LaunchedEffect(dismissGestureEnabled) {
        if (!dismissGestureEnabled) revealState.snapTo(SessionReveal.Settled)
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        if (dismissGestureEnabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(AppCardShape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable { scope.launch { revealState.snapTo(SessionReveal.Settled) } },
                contentAlignment = Alignment.CenterEnd,
            ) {
                IconButton(
                    onClick = {
                        scope.launch { revealState.snapTo(SessionReveal.Settled) }
                        onDelete()
                    },
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .width(deleteRevealWidth),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(x = revealState.requireOffset().roundToInt(), y = 0)
                }
                .anchoredDraggable(revealState, Orientation.Horizontal),
        ) {
            cardContent()
        }
    }

    if (showCategoryPicker) {
        SessionCategoryPickerDialog(
            categories = categories,
            selectedCategoryId = item.category?.id,
            onSelect = { categoryId ->
                onSetCategory(categoryId)
                showCategoryPicker = false
            },
            onSaveCategory = onSaveCategory,
            onDeleteCategory = onDeleteCategory,
            onDismiss = { showCategoryPicker = false },
        )
    }
}

@Composable
internal fun SessionCategoryPickerDialog(
    categories: List<SessionCategory>,
    selectedCategoryId: String?,
    onSelect: (String?) -> Unit,
    onSaveCategory: (String?, String, String, String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showManager by remember { mutableStateOf(false) }
    AppDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.session_category),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(null) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.LabelOff, contentDescription = null)
                    Text(
                        text = stringResource(R.string.session_category_none),
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    if (selectedCategoryId == null) Icon(Icons.Default.Check, contentDescription = null)
                }
                categories.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(category.id) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = sessionCategoryIcon(category.icon),
                            contentDescription = null,
                            tint = sessionCategoryColor(category.color),
                        )
                        Text(
                            text = category.name,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        )
                        if (selectedCategoryId == category.id) Icon(Icons.Default.Check, contentDescription = null)
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showManager = true }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.settings_session_categories),
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            AppSecondaryButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
    if (showManager) {
        SessionCategoriesDialog(
            categories = categories,
            onSave = onSaveCategory,
            onDelete = onDeleteCategory,
            onDismiss = { showManager = false },
        )
    }
}
