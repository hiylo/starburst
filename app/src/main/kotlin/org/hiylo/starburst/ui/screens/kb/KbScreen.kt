/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : KbScreen.kt
 * Date : 2026/09/22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.kb

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.KbCollection
import org.hiylo.starburst.ui.components.AppCardShape
import org.hiylo.starburst.ui.components.AppDialog
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.AppSecondaryButton
import org.hiylo.starburst.ui.components.appAmoledBorder
import org.hiylo.starburst.ui.components.isAmoledTheme
import org.hiylo.starburst.ui.screens.testintel.formatTimestamp

/**
 * 知识库：集合列表页。
 *
 * 列出后端 `/api/kb/collections` 的集合，支持新建集合与进入集合详情。
 *
 * @author Hsi Chu
 * @since 3.1.0
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KbScreen(
    onNavigateBack: () -> Unit,
    onOpenCollection: (Long) -> Unit,
    viewModel: KbCollectionListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isAmoled = isAmoledTheme()
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.kb_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.kb_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.kb_new_collection))
            }
        }
    ) { padding ->
        when {
            uiState.loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = uiState.error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(16.dp))
                    AppPrimaryButton(onClick = viewModel::refresh) {
                        Text(stringResource(R.string.kb_refresh))
                    }
                }
            }
            uiState.collections.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.kb_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.collections, key = { it.id }) { collection ->
                        KbCollectionCard(collection = collection, onClick = { onOpenCollection(collection.id) })
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateCollectionDialog(
            creating = uiState.creating,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, description ->
                viewModel.createCollection(name, description) { ok ->
                    if (ok) showCreateDialog = false
                }
            },
        )
    }
}

@Composable
private fun KbCollectionCard(
    collection: KbCollection,
    onClick: () -> Unit,
) {
    val isAmoled = isAmoledTheme()
    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = appAmoledBorder(0.65f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = collection.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (collection.description.isNotBlank()) {
                Text(
                    text = collection.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.kb_created_at, formatTimestamp(collection.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.kb_document_count, collection.documentCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CreateCollectionDialog(
    creating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    AppDialog(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.kb_new_collection),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp),
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.kb_collection_name)) },
            placeholder = { Text(stringResource(R.string.kb_collection_name_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text(stringResource(R.string.kb_collection_description)) },
            placeholder = { Text(stringResource(R.string.kb_collection_description_hint)) },
            minLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            AppSecondaryButton(
                onClick = onDismiss,
                outlined = true,
            ) {
                Text(stringResource(R.string.cancel))
            }
            AppPrimaryButton(
                onClick = { onConfirm(name, description) },
                enabled = name.isNotBlank() && !creating,
            ) {
                if (creating) {
                    CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    stringResource(if (creating) R.string.kb_creating else R.string.kb_create),
                )
            }
        }
    }
}
