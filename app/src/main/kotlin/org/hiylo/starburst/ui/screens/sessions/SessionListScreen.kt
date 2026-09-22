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
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import org.hiylo.starburst.data.repository.saveSessionCategory
import org.hiylo.starburst.data.repository.deleteSessionCategory
import org.hiylo.starburst.data.repository.setSessionCategory
import org.hiylo.starburst.data.repository.recordRecentProject
import org.hiylo.starburst.data.repository.recentProjects
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
import org.hiylo.starburst.ui.components.CartoonInkIcon
import org.hiylo.starburst.ui.components.CartoonStickerIcon
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
internal fun PulsingDotsIndicator(
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
    val backendReady by viewModel.backendReady.collectAsState()
    val isAmoled = isAmoledTheme()
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) { viewModel.attachLifecycle(lifecycleOwner.lifecycle) }
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
    var showCompactSelectedDialog by remember { mutableStateOf(false) }

    // Project picker dialog state
    var showOpenProject by remember { mutableStateOf(false) }
    var showQuickNewSession by remember { mutableStateOf(false) }
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
                        IconButton(onClick = { showCompactSelectedDialog = true }) {
                            Icon(
                                Icons.Default.Compress,
                                contentDescription = stringResource(R.string.sessions_compact_selected),
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
                            CartoonInkIcon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(if (searchActive) R.string.close else R.string.back),
                            )
                        }
                    },
                    actions = {
                        if (!searchActive) {
                            if (backendReady) {
                                IconButton(onClick = onNavigateToWorkbench) {
                                    CartoonInkIcon(
                                        Icons.Default.Dashboard,
                                        contentDescription = stringResource(R.string.workbench_enter),
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onOpenBookmarks(viewModel.serverId) },
                            ) {
                                CartoonInkIcon(
                                    Icons.Default.BookmarkBorder,
                                    contentDescription = stringResource(R.string.bookmarks_title),
                                )
                            }
                            IconButton(
                                onClick = { onOpenFtsSearch(viewModel.serverId) },
                            ) {
                                CartoonInkIcon(
                                    Icons.Default.ManageSearch,
                                    contentDescription = stringResource(R.string.fts_search_title),
                                )
                            }
                            Box {
                                IconButton(onClick = { showFilterMenu = true }) {
                                    CartoonInkIcon(
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
                    CartoonInkIcon(Icons.Default.Add, contentDescription = stringResource(R.string.sessions_new))
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
                        CartoonStickerIcon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            stickerTint = MaterialTheme.colorScheme.onErrorContainer,
                            padding = 10.dp,
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
                        CartoonStickerIcon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            stickerTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            padding = 12.dp,
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
            onBrowse = {
                showQuickNewSession = false
                showOpenProject = true
            },
            onDismiss = { showQuickNewSession = false }
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

    // Compact selected dialog
    if (showCompactSelectedDialog) {
        AppDialog(onDismissRequest = { showCompactSelectedDialog = false }, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sessions_compact_selected),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(stringResource(R.string.sessions_compact_selected_confirm, uiState.selectedIds.size))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        AppSecondaryButton(onClick = { showCompactSelectedDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        AppPrimaryButton(
                            onClick = {
                                viewModel.compactSelected()
                                showCompactSelectedDialog = false
                            },
                        ) {
                            Text(stringResource(R.string.sessions_compact_selected))
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
