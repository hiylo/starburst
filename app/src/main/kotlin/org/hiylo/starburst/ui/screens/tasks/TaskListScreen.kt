/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : TaskListScreen.kt
 * Date : 2026/09/11 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.starburst.ui.screens.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.widget.Toast
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.BackendStats
import org.hiylo.starburst.data.api.BackendTaskStats
import org.hiylo.starburst.data.api.BackendTaskTarget
import org.hiylo.starburst.data.api.BackendTokenUsage
import org.hiylo.starburst.domain.model.BackendArchive
import org.hiylo.starburst.domain.model.BackendTask
import org.hiylo.starburst.domain.model.BackendTaskStatus
import org.hiylo.starburst.ui.components.AppCardShape
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.appAmoledBorder
import org.hiylo.starburst.ui.components.isAmoledTheme
import org.hiylo.starburst.ui.theme.StatusConnected
import org.hiylo.starburst.ui.theme.StatusError
import org.hiylo.starburst.ui.theme.StatusProcessing
import org.hiylo.starburst.ui.theme.StatusWarning

private data class TaskStatusStyle(
    val label: String,
    val color: Color,
    val icon: ImageVector,
)

@Composable
private fun taskStatusStyle(status: BackendTaskStatus): TaskStatusStyle {
    return when (status) {
        BackendTaskStatus.Queued -> TaskStatusStyle(
            stringResource(R.string.task_status_queued), MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.Schedule,
        )
        BackendTaskStatus.Running -> TaskStatusStyle(
            stringResource(R.string.task_status_running), MaterialTheme.colorScheme.primary, Icons.Default.PlayArrow,
        )
        BackendTaskStatus.Succeeded -> TaskStatusStyle(
            stringResource(R.string.task_status_succeeded),
            StatusConnected, Icons.Default.CheckCircle,
        )
        BackendTaskStatus.Failed -> TaskStatusStyle(
            stringResource(R.string.task_status_failed), MaterialTheme.colorScheme.error, Icons.Default.Error,
        )
        BackendTaskStatus.Canceled -> TaskStatusStyle(
            stringResource(R.string.task_status_canceled), MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.Cancel,
        )
        BackendTaskStatus.Retrying -> TaskStatusStyle(
            stringResource(R.string.task_status_retrying), StatusWarning, Icons.Default.Refresh,
        )
        BackendTaskStatus.Pending -> TaskStatusStyle(
            stringResource(R.string.task_status_pending), MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.HourglassEmpty,
        )
        BackendTaskStatus.Blocked -> TaskStatusStyle(
            stringResource(R.string.task_status_blocked), StatusError, Icons.Default.Lock,
        )
        BackendTaskStatus.Scheduled -> TaskStatusStyle(
            stringResource(R.string.task_status_scheduled), StatusProcessing, Icons.Default.EventNote,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    onNavigateBack: () -> Unit,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    val isAmoled = isAmoledTheme()
    val tasks by viewModel.tasks.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val archives by viewModel.archives.collectAsState()
    val archiveLoading by viewModel.archiveLoading.collectAsState()
    val archiveError by viewModel.archiveError.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val statsLoading by viewModel.statsLoading.collectAsState()
    val statsError by viewModel.statsError.collectAsState()

    var filter by remember { mutableStateOf<BackendTaskStatus?>(null) }
    var showBatchDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showPurgeDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    val visibleTasks = tasks.filter { filter == null || it.status == filter }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tasks_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.task_create_title))
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.task_purge_finished)) },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showPurgeDialog = true
                                },
                            )
                        }
                    }
                    IconButton(onClick = {
                        when (selectedTab) {
                            0 -> viewModel.refresh()
                            1 -> viewModel.loadArchives()
                            else -> viewModel.loadStats()
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.skills_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.tasks_tab_tasks)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1; viewModel.loadArchives() },
                    text = { Text(stringResource(R.string.tasks_tab_archives)) },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2; viewModel.loadStats() },
                    text = { Text(stringResource(R.string.tasks_tab_stats)) },
                )
            }

            when (selectedTab) {
                0 -> TasksContent(
                    tasks = tasks,
                    visibleTasks = visibleTasks,
                    loading = loading,
                    error = error,
                    filter = filter,
                    onFilterChange = { filter = it },
                    onRefresh = viewModel::refresh,
                    onNewTask = { showCreateDialog = true },
                    onOpenBatch = { showBatchDialog = true },
                    onCancelTask = viewModel::cancelTask,
                    onUnblockTask = viewModel::unblockTask,
                )
                1 -> ArchivesContent(
                    archives = archives,
                    loading = archiveLoading,
                    error = archiveError,
                    onRefresh = viewModel::loadArchives,
                    onDelete = viewModel::deleteArchive,
                )
                else -> StatsContent(
                    stats = stats,
                    loading = statsLoading,
                    error = statsError,
                    onRefresh = viewModel::loadStats,
                )
            }
        }
    }

    if (showBatchDialog) {
        BatchDialog(
            onDismiss = { showBatchDialog = false },
            onConfirm = { batchPrompt, directories ->
                showBatchDialog = false
                viewModel.batch(batchPrompt, directories.map { BackendTaskTarget(directory = it) })
            },
        )
    }

    if (showCreateDialog) {
        TaskCreateDialog(
            onDismiss = { showCreateDialog = false },
            onCreateTask = { name, prompt, directory, scheduledAt, cron ->
                viewModel.createTask(prompt, name = name, directory = directory, scheduledAt = scheduledAt, cron = cron) { ok ->
                    if (ok) showCreateDialog = false
                }
            },
            onCreatePlan = { name, steps, scheduledAt, cron ->
                viewModel.createPlan(name, steps, scheduledAt, cron) { ok ->
                    if (ok) showCreateDialog = false
                }
            },
            onGeneratePlan = viewModel::generatePlan,
        )
    }

    if (showPurgeDialog) {
        AlertDialog(
            onDismissRequest = { showPurgeDialog = false },
            title = { Text(stringResource(R.string.task_purge_finished)) },
            text = { Text(stringResource(R.string.task_purge_finished_desc)) },
            confirmButton = {
                val context = LocalContext.current
                TextButton(
                    onClick = {
                        showPurgeDialog = false
                        viewModel.purgeFinishedTasks { deleted ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.task_purge_done, deleted),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                ) { Text(stringResource(R.string.task_purge_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeDialog = false }) { Text(stringResource(R.string.server_cancel)) }
            },
        )
    }
}

@Composable
private fun TasksContent(
    tasks: List<BackendTask>,
    visibleTasks: List<BackendTask>,
    loading: Boolean,
    error: String?,
    filter: BackendTaskStatus?,
    onFilterChange: (BackendTaskStatus?) -> Unit,
    onRefresh: () -> Unit,
    onNewTask: () -> Unit,
    onOpenBatch: () -> Unit,
    onCancelTask: (String) -> Unit,
    onUnblockTask: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 统计概览
        StatsBar(tasks = tasks)

        // 新建任务入口
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppPrimaryButton(
                onClick = onNewTask,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.task_create_title))
            }
            TextButton(onClick = onOpenBatch) {
                Text(stringResource(R.string.tasks_batch))
            }
        }

        // 筛选 chips
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = filter == null, onClick = { onFilterChange(null) }, label = { Text(stringResource(R.string.tasks_filter_all)) })
            listOf(
                BackendTaskStatus.Queued, BackendTaskStatus.Running, BackendTaskStatus.Succeeded,
                BackendTaskStatus.Failed, BackendTaskStatus.Canceled, BackendTaskStatus.Pending, BackendTaskStatus.Blocked,
                BackendTaskStatus.Scheduled,
            ).forEach { status ->
                FilterChip(
                    selected = filter == status,
                    onClick = { onFilterChange(status) },
                    label = { Text(taskStatusStyle(status).label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            loading && tasks.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            error != null && tasks.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(text = error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onRefresh) { Text(stringResource(R.string.skills_retry)) }
                }
            }
            visibleTasks.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.tasks_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visibleTasks, key = { it.id }) { task ->
                        TaskCard(task = task, onCancel = { onCancelTask(task.id) }, onUnblock = { onUnblockTask(task.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivesContent(
    archives: List<BackendArchive>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val isAmoled = isAmoledTheme()
    Column(modifier = Modifier.fillMaxSize()) {
        when {
            loading && archives.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            error != null && archives.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(text = error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onRefresh) { Text(stringResource(R.string.skills_retry)) }
                }
            }
            archives.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.tasks_archives_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(archives, key = { it.id }) { archive ->
                        ArchiveCard(archive = archive, onDelete = { onDelete(archive.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsContent(
    stats: BackendStats?,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        when {
            loading && stats == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            error != null && stats == null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(text = error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onRefresh) { Text(stringResource(R.string.skills_retry)) }
                }
            }
            stats == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.tasks_stats_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { TaskStatsCard(stats!!.tasks) }
                    item {
                        TokenUsageCard(
                            usages = stats!!.tokenUsage,
                            archiveCount = stats!!.archives,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskStatsCard(tasks: BackendTaskStats) {
    val isAmoled = isAmoledTheme()
    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = appAmoledBorder(0.65f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.tasks_stats_task_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.tasks_stats_task_total, tasks.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatCell(stringResource(R.string.tasks_stat_succeeded), tasks.succeeded, StatusConnected)
                StatCell(stringResource(R.string.tasks_stat_running), tasks.running, StatusProcessing)
                StatCell(stringResource(R.string.tasks_stat_failed), tasks.failed, MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatCell(stringResource(R.string.tasks_stat_queued), tasks.queued, MaterialTheme.colorScheme.onSurfaceVariant)
                StatCell(stringResource(R.string.tasks_stat_pending), tasks.pending, StatusWarning)
                StatCell(stringResource(R.string.tasks_stat_blocked), tasks.blocked, StatusError)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatCell(stringResource(R.string.tasks_stat_canceled), tasks.canceled, MaterialTheme.colorScheme.onSurfaceVariant)
                StatCell(stringResource(R.string.tasks_stat_retried), tasks.retried, MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TokenUsageCard(usages: List<BackendTokenUsage>, archiveCount: Int) {
    val isAmoled = isAmoledTheme()
    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = appAmoledBorder(0.65f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.tasks_stats_token_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.tasks_stats_archive_count, archiveCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (usages.isEmpty()) {
                Text(
                    text = stringResource(R.string.tasks_stats_token_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                usages.forEach { usage ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = usage.tokenName.ifBlank { usage.tokenId },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (usage.tokenName.isNotBlank()) {
                                Text(
                                    text = usage.tokenId,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.tasks_stats_token_calls, usage.calls),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchiveCard(archive: BackendArchive, onDelete: () -> Unit) {
    val isAmoled = isAmoledTheme()
    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer),
        border = appAmoledBorder(0.65f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = archive.title ?: archive.sessionId.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = archive.format.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatBytes(archive.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = archive.createdAt.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.tasks_archive_delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatBytes(size: Int): String {
    if (size < 1024) return "$size B"
    if (size < 1024 * 1024) return "${size / 1024} KB"
    return "${size / (1024 * 1024)} MB"
}

@Composable
private fun StatsBar(tasks: List<BackendTask>) {
    val isAmoled = isAmoledTheme()
    val count = tasks.groupingBy { it.status }.eachCount()
    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer),
        border = appAmoledBorder(0.65f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                BackendTaskStatus.Running, BackendTaskStatus.Queued, BackendTaskStatus.Succeeded,
                BackendTaskStatus.Failed, BackendTaskStatus.Blocked,
            ).forEach { status ->
                val style = taskStatusStyle(status)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = (count[status] ?: 0).toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = style.color,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(text = style.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: BackendTask,
    onCancel: () -> Unit,
    onUnblock: () -> Unit,
) {
    val isAmoled = isAmoledTheme()
    val style = taskStatusStyle(task.status)
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer),
        border = appAmoledBorder(0.65f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().let { it }) {
                Icon(style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                val recurring = task.status == BackendTaskStatus.Scheduled && !task.cron.isNullOrBlank()
                Text(
                    text = if (recurring) stringResource(R.string.task_status_recurring) else style.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (recurring) MaterialTheme.colorScheme.tertiary else style.color,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        recurring -> task.cron.orEmpty()
                        task.status == BackendTaskStatus.Scheduled -> task.scheduledAt.orEmpty()
                        else -> task.directory.orEmpty()
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = task.createdAt.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            task.name?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = task.prompt,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            )

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                task.dependsOn?.takeIf { it.isNotBlank() }?.let {
                    DetailRow(stringResource(R.string.task_detail_depends_on), it)
                }
                when (task.status) {
                    BackendTaskStatus.Running -> task.progress?.takeIf { it.isNotBlank() }?.let {
                        DetailRow(stringResource(R.string.task_detail_progress), it)
                    }
                    BackendTaskStatus.Succeeded -> {
                        task.aiSummary?.takeIf { it.isNotBlank() }?.let { DetailRow(stringResource(R.string.task_detail_summary), it) }
                        task.result?.takeIf { it.isNotBlank() }?.let { DetailRow(stringResource(R.string.task_detail_result), it) }
                    }
                    BackendTaskStatus.Failed -> task.error?.takeIf { it.isNotBlank() }?.let {
                        DetailRow(stringResource(R.string.task_detail_error), it, error = true)
                    }
                    BackendTaskStatus.Blocked -> task.error?.takeIf { it.isNotBlank() }?.let {
                        DetailRow(stringResource(R.string.task_detail_error), it, error = true)
                    }
                    BackendTaskStatus.Scheduled -> {
                        task.scheduledAt?.takeIf { it.isNotBlank() }?.let {
                            DetailRow(stringResource(R.string.task_detail_scheduled_at), it)
                        }
                        task.cron?.takeIf { it.isNotBlank() }?.let {
                            DetailRow(stringResource(R.string.task_detail_cron), it)
                        }
                    }
                    else -> {}
                }
                DetailRow(stringResource(R.string.task_detail_attempts), task.attempts.toString())
            }

            // 操作按钮
            when (task.status) {
                BackendTaskStatus.Queued, BackendTaskStatus.Running, BackendTaskStatus.Pending, BackendTaskStatus.Scheduled -> {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onCancel) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.task_action_cancel))
                    }
                }
                BackendTaskStatus.Blocked -> {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onUnblock) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.task_action_unblock))
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, error: Boolean = false) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun BatchDialog(
    onDismiss: () -> Unit,
    onConfirm: (prompt: String, directories: List<String>) -> Unit,
) {
    var prompt by remember { mutableStateOf("") }
    var dirsText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tasks_batch_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.tasks_prompt_hint)) },
                    minLines = 2,
                    maxLines = 4,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dirsText,
                    onValueChange = { dirsText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.tasks_batch_dirs_hint)) },
                    minLines = 3,
                    maxLines = 6,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val dirs = dirsText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
                    if (prompt.isNotBlank() && dirs.isNotEmpty()) onConfirm(prompt, dirs)
                },
                enabled = prompt.isNotBlank(),
            ) { Text(stringResource(R.string.tasks_batch_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.server_cancel)) } },
    )
}
