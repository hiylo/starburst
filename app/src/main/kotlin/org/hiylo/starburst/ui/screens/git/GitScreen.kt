/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : GitScreen.kt
 * Date : 2026/09/07 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.git

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.CartoonStickerIcon
import org.hiylo.starburst.ui.theme.StatusConnected
import org.hiylo.starburst.ui.theme.StatusError
import org.hiylo.starburst.ui.theme.StatusWarning

/**
 * Git 页面：查看仓库状态、变更、差异与提交历史，并执行提交/推送/拉取/分支操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(
    onNavigateBack: () -> Unit,
    viewModel: GitViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val generatingMessage by viewModel.generatingMessage.collectAsState()
    val generatedMessage by viewModel.generatedMessage.collectAsState()
    val generateError by viewModel.generateError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val successMessage = stringResource(R.string.git_operation_success)
    val failedMessage = stringResource(R.string.git_operation_failed)
    var showCommitDialog by remember { mutableStateOf(false) }
    var showNewBranchDialog by remember { mutableStateOf(false) }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var showPushDialog by remember { mutableStateOf(false) }
    var showPullDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }

    BackHandler(onBack = onNavigateBack)

    LaunchedEffect(state.operationMessage) {
        val message = state.operationMessage ?: return@LaunchedEffect
        val text = if (message == "success") successMessage else (state.operationError ?: failedMessage)
        snackbarHostState.showSnackbar(text)
        viewModel.clearOperationMessage()
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.git_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.git_loading))
                    }
                    Box {
                        IconButton(onClick = { moreExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.git_commit)) },
                                onClick = { moreExpanded = false; showCommitDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.git_push)) },
                                onClick = { moreExpanded = false; showPushDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.git_pull)) },
                                onClick = { moreExpanded = false; showPullDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.git_checkout)) },
                                onClick = { moreExpanded = false; showCheckoutDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.git_new_branch)) },
                                onClick = { moreExpanded = false; showNewBranchDialog = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.git_fetch)) },
                                onClick = { moreExpanded = false; viewModel.fetch() },
                            )
                            if (!state.isClean) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.git_stash)) },
                                    onClick = { moreExpanded = false; viewModel.stash() },
                                )
                            }
                            if (state.hasStash) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.git_stash_pop)) },
                                    onClick = { moreExpanded = false; viewModel.stashPop() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.git_tags)) },
                                onClick = { moreExpanded = false; showTagDialog = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.notRepository -> NotRepositoryView()
                state.error != null && !state.isLoading -> ErrorView(state.error!!, viewModel::refresh)
                else -> GitContent(
                    state = state,
                    selectedPaths = selectedPaths,
                    onSelectRepo = viewModel::selectRepo,
                    onLoadDiff = viewModel::loadDiff,
                    onToggleSelect = { path ->
                        selectedPaths = if (path in selectedPaths) selectedPaths - path else selectedPaths + path
                    },
                    onLoadCommitDetail = viewModel::loadCommitDetail,
                    onLoadCommitFileDiff = viewModel::loadCommitFileDiff,
                    onLoadMoreCommits = viewModel::loadMoreCommits,
                )
            }
        }
    }

    if (showCommitDialog) {
        CommitDialog(
            onDismiss = { showCommitDialog = false },
            onConfirm = { message ->
                showCommitDialog = false
                val paths = selectedPaths.toList()
                selectedPaths = emptySet()
                viewModel.commit(message, paths)
            },
            selectedCount = selectedPaths.size,
            generating = generatingMessage,
            generatedMessage = generatedMessage,
            generateError = generateError,
            onGenerate = viewModel::generateCommitMessage,
            onClearGenerated = viewModel::clearGeneratedMessage,
        )
    }
    if (showNewBranchDialog) {
        NewBranchDialog(
            onDismiss = { showNewBranchDialog = false },
            onConfirm = { name ->
                showNewBranchDialog = false
                viewModel.createBranch(name)
            },
        )
    }
    if (showCheckoutDialog) {
        CheckoutDialog(
            branches = state.branches,
            currentBranch = state.branch,
            onDismiss = { showCheckoutDialog = false },
            onSelect = { branch ->
                showCheckoutDialog = false
                viewModel.checkout(branch)
            },
        )
    }
    if (showPushDialog) {
        RemoteDialog(
            remotes = state.remotes,
            currentBranch = state.branch,
            actionLabel = stringResource(R.string.git_push),
            onDismiss = { showPushDialog = false },
            onSelect = { remote ->
                showPushDialog = false
                viewModel.push(remote)
            },
        )
    }
    if (showPullDialog) {
        RemoteDialog(
            remotes = state.remotes,
            currentBranch = state.branch,
            actionLabel = stringResource(R.string.git_pull),
            onDismiss = { showPullDialog = false },
            onSelect = { remote ->
                showPullDialog = false
                viewModel.pull(remote)
            },
        )
    }
    if (showTagDialog) {
        TagDialog(
            tags = state.tags,
            onDismiss = { showTagDialog = false },
            onCreate = { name ->
                showTagDialog = false
                viewModel.createTag(name)
            },
        )
    }
}

@Composable
private fun NotRepositoryView() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CartoonStickerIcon(
            Icons.Default.AccountTree,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            stickerTint = MaterialTheme.colorScheme.onSecondaryContainer,
            padding = 11.dp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.git_not_repository),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.git_not_repository_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorView(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun GitContent(
    state: GitUiState,
    selectedPaths: Set<String>,
    onSelectRepo: (String) -> Unit,
    onLoadDiff: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onLoadCommitDetail: (GitCommit) -> Unit,
    onLoadCommitFileDiff: (String, String) -> Unit,
    onLoadMoreCommits: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RepoSelector(state.repos, state.selectedRepo, onSelectRepo)

        BranchStatusRow(state)

        ChangesSection(state.changes, state.selectedDiff, selectedPaths, onLoadDiff, onToggleSelect)

        CommitsSection(
            commits = state.commits,
            selectedCommit = state.selectedCommit,
            commitChanges = state.commitChanges,
            commitDiff = state.commitDiff,
            isLoadingCommit = state.isLoadingCommit,
            commitFileDiff = state.commitFileDiff,
            hasMoreCommits = state.hasMoreCommits,
            isLoadingMoreCommits = state.isLoadingMoreCommits,
            onLoadCommitDetail = onLoadCommitDetail,
            onLoadCommitFileDiff = onLoadCommitFileDiff,
            onLoadMoreCommits = onLoadMoreCommits,
        )
    }
}

@Composable
private fun RepoSelector(
    repos: List<GitRepo>,
    selectedRepo: String,
    onSelectRepo: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = repos.firstOrNull { it.path == selectedRepo }
    Column {
        Text(
            stringResource(R.string.git_repository),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.AccountTree,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = selected?.label ?: selectedRepo,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                repos.forEach { repo ->
                    DropdownMenuItem(
                        text = { Text(repo.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            expanded = false
                            onSelectRepo(repo.path)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BranchStatusRow(state: GitUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        state.branch?.let { branch ->
            Text(
                text = stringResource(R.string.git_branch, branch),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (state.aheadCount > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.git_ahead_count, state.aheadCount),
                style = MaterialTheme.typography.bodyMedium,
                color = StatusWarning,
            )
        }
        Spacer(Modifier.weight(1f))
        if (state.isClean) {
            Text(
                text = stringResource(R.string.git_clean),
                style = MaterialTheme.typography.bodyMedium,
                color = StatusConnected,
            )
        } else {
            Text(
                text = stringResource(R.string.git_changes_count, state.changes.size),
                style = MaterialTheme.typography.bodyMedium,
                color = StatusWarning,
            )
        }
    }
}

@Composable
private fun ChangesSection(
    changes: List<GitChange>,
    selectedDiff: GitFileDiff?,
    selectedPaths: Set<String>,
    onLoadDiff: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.git_changes),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        )
        if (changes.isEmpty()) {
            Text(
                stringResource(R.string.git_no_changes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            changes.forEach { change ->
                ChangeRow(
                    change = change,
                    selected = selectedDiff?.path == change.path,
                    checked = change.path in selectedPaths,
                    onClick = { onLoadDiff(change.path) },
                    onCheckedChange = { onToggleSelect(change.path) },
                )
                if (selectedDiff?.path == change.path) {
                    DiffView(selectedDiff)
                }
            }
        }
    }
}

@Composable
private fun ChangeRow(
    change: GitChange,
    selected: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
) {
    val (color, label) = when (change.status) {
        "added", "untracked" -> StatusConnected to stringResource(R.string.git_status_added)
        "deleted" -> StatusError to stringResource(R.string.git_status_deleted)
        else -> StatusWarning to stringResource(R.string.git_status_modified)
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = change.path,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (change.additions > 0) {
                Text(
                    text = "+${change.additions}",
                    color = StatusConnected,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(Modifier.width(6.dp))
            }
            if (change.deletions > 0) {
                Text(
                    text = "-${change.deletions}",
                    color = StatusError,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun CommitsSection(
    commits: List<GitCommit>,
    selectedCommit: GitCommit?,
    commitChanges: List<GitChange>,
    commitDiff: String,
    isLoadingCommit: Boolean,
    commitFileDiff: GitFileDiff?,
    hasMoreCommits: Boolean,
    isLoadingMoreCommits: Boolean,
    onLoadCommitDetail: (GitCommit) -> Unit,
    onLoadCommitFileDiff: (String, String) -> Unit,
    onLoadMoreCommits: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.git_commits),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
        }
        if (commits.isEmpty()) {
            Text(
                stringResource(R.string.git_no_commits),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            commits.forEach { commit ->
                val expanded = selectedCommit?.hash == commit.hash
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onLoadCommitDetail(commit) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (expanded) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = commit.message,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${commit.hash.take(8)} · ${commit.author} · ${commit.date}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (expanded) {
                            Spacer(Modifier.height(8.dp))
                            CommitDetail(
                                changes = commitChanges,
                                diff = commitDiff,
                                loading = isLoadingCommit,
                                commitHash = commit.hash,
                                fileDiff = commitFileDiff,
                                onLoadFileDiff = onLoadCommitFileDiff,
                            )
                        }
                    }
                }
            }
            if (hasMoreCommits) {
                TextButton(
                    onClick = onLoadMoreCommits,
                    enabled = !isLoadingMoreCommits,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    if (isLoadingMoreCommits) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(stringResource(R.string.git_load_more))
                }
            }
        }
    }
}
