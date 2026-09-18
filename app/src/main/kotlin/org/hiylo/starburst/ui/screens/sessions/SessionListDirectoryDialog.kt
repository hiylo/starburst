/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SessionListDirectoryDialog.kt
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

/**
 * Directory browser dialog for opening a project.
 * Shows: known projects at top, then browsable server filesystem.
 * Supports search and tap-to-navigate into subdirectories.
 */
@Composable
internal fun OpenProjectDialog(
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
                                    items(searchResults, key = { it }) { path ->
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
