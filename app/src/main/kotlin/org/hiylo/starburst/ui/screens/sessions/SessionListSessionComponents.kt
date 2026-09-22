/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SessionListSessionComponents.kt
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
import org.hiylo.starburst.ui.components.cartoonChrome
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

/** Anchored drag settle positions for the session-row swipe-to-reveal delete action. */
internal enum class SessionReveal { Settled, Revealed }

/**
 * Quick-start dialog for creating a new session.
 * Groups sessions by their directory to show unique project folders,
 * sorted by most recently used. One tap creates a session in that folder.
 * A "Browse..." row at the bottom opens the full directory picker.
 */
@Composable
internal fun NewSessionQuickDialog(
    sessions: List<SessionItem>,
    limit: Int,
    onSelectDirectory: (String) -> Unit,
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun PinnedSortDialog(
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
internal fun SessionRow(
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
                .cartoonChrome(AppCardShape)
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
                    sessionError = item.sessionError?.message,
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
