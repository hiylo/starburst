/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : KbCollectionDetailScreen.kt
 * Date : 2026/09/22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.kb

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.KbDocument
import org.hiylo.starburst.data.api.KbSearchResult
import org.hiylo.starburst.ui.components.AppCardShape
import org.hiylo.starburst.ui.components.AppDialog
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.AppSecondaryButton
import org.hiylo.starburst.ui.components.appAmoledBorder
import org.hiylo.starburst.ui.components.isAmoledTheme
import org.hiylo.starburst.ui.screens.testintel.StatusBadge
import org.hiylo.starburst.ui.screens.testintel.formatTimestamp
import org.hiylo.starburst.ui.theme.StatusConnected
import org.hiylo.starburst.ui.theme.StatusError
import org.hiylo.starburst.ui.theme.StatusProcessing

/**
 * 知识库：集合详情页。
 *
 * 顶部提供「摄入文档」与「搜索」入口；正文展示集合内文档列表。
 * 摄入支持剪贴板文本/多行文本与本地文本类文件；搜索为集合内的向量检索。
 *
 * @author Hsi Chu
 * @since 3.1.0
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KbCollectionDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: KbCollectionDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val isAmoled = isAmoledTheme()
    var showIngestDialog by rememberSaveable { mutableStateOf(false) }
    var showSearchDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.collection?.name?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.kb_collection_title),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::loadDocuments) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.kb_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppPrimaryButton(
                    onClick = { showIngestDialog = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.kb_ingest))
                }
                AppSecondaryButton(
                    onClick = { showSearchDialog = true },
                    modifier = Modifier.weight(1f),
                    outlined = true,
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.kb_search_button))
                }
            }

            uiState.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            DocumentsSection(
                state = uiState,
                isAmoled = isAmoled,
            )
        }
    }

    if (showIngestDialog) {
        IngestDialog(
            ingesting = uiState.ingesting,
            error = uiState.ingestError,
            onDismiss = { showIngestDialog = false },
            onIngestText = { name, content ->
                viewModel.ingestText(name, content) { ok ->
                    if (ok) showIngestDialog = false
                }
            },
            onIngestFile = { name, uri, mime ->
                viewModel.ingestFile(name, uri, mime) { ok ->
                    if (ok) showIngestDialog = false
                }
            },
        )
    }

    if (showSearchDialog) {
        SearchDialog(
            query = uiState.query,
            searching = uiState.searching,
            error = uiState.searchError,
            results = uiState.results,
            onQueryChange = viewModel::setQuery,
            onSearch = viewModel::search,
            onDismiss = {
                showSearchDialog = false
                viewModel.clearSearch()
            },
        )
    }
}

@Composable
private fun ColumnScope.DocumentsSection(
    state: KbCollectionDetailUiState,
    isAmoled: Boolean,
) {
    Text(
        text = stringResource(R.string.kb_documents),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    when {
        state.loading && state.documents.isEmpty() -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.documents.isEmpty() -> {
            Text(
                text = stringResource(R.string.kb_documents_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.documents, key = { it.id }) { document ->
                    KbDocumentCard(document = document, isAmoled = isAmoled)
                }
            }
        }
    }
}

@Composable
private fun KbDocumentCard(
    document: KbDocument,
    isAmoled: Boolean,
) {
    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = appAmoledBorder(0.65f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(text = kbStatusLabel(document.status), color = kbStatusColor(document.status))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (document.mime.isNotBlank()) {
                    Text(
                        text = document.mime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.kb_doc_chunks, document.chunkCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.kb_doc_created, formatTimestamp(document.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!document.error.isNullOrBlank()) {
                Text(
                    text = document.error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun IngestDialog(
    ingesting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onIngestText: (name: String, content: String) -> Unit,
    onIngestFile: (name: String, uri: Uri, mime: String) -> Unit,
) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf("text") }
    var name by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var pickedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pickedName by rememberSaveable { mutableStateOf("") }
    var pickedMime by rememberSaveable { mutableStateOf<String?>(null) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            pickedUri = uri
            pickedName = displayNameOf(context.contentResolver, uri)
            pickedMime = context.contentResolver.getType(uri)
            if (name.isBlank()) name = pickedName
            validationError = null
        }
    }

    val canSubmit = name.isNotBlank() &&
        (mode != "text" || content.isNotBlank()) &&
        (mode != "file" || pickedUri != null)

    AppDialog(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.kb_ingest),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp),
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.clickable { mode = "text" }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = mode == "text", onClick = { mode = "text" })
                Text(stringResource(R.string.kb_ingest_text))
            }
            Spacer(Modifier.width(16.dp))
            Row(
                modifier = Modifier.clickable { mode = "file" }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = mode == "file", onClick = { mode = "file" })
                Text(stringResource(R.string.kb_ingest_file))
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.kb_doc_name)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (mode == "text") {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.kb_doc_content)) },
                placeholder = { Text(stringResource(R.string.kb_doc_content_hint)) },
                minLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )
        } else {
            AppSecondaryButton(
                onClick = { fileLauncher.launch(arrayOf("text/*")) },
                outlined = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (pickedName.isNotBlank()) pickedName
                    else stringResource(R.string.kb_choose_file),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (pickedUri == null) {
                Text(
                    text = stringResource(R.string.kb_no_file),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        validationError?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
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
                onClick = {
                    if (!canSubmit) {
                        validationError = context.getString(R.string.kb_ingest_no_content)
                    } else if (mode == "text") {
                        onIngestText(name, content)
                    } else {
                        onIngestFile(name, pickedUri!!, pickedMime ?: "text/plain")
                    }
                },
                enabled = !ingesting,
            ) {
                if (ingesting) {
                    CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(stringResource(R.string.kb_ingest))
            }
        }
    }
}

@Composable
private fun SearchDialog(
    query: String,
    searching: Boolean,
    error: String?,
    results: List<KbSearchResult>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.kb_search_button),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.kb_search_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f),
            )
            AppPrimaryButton(
                onClick = onSearch,
                enabled = query.isNotBlank() && !searching,
            ) {
                if (searching) {
                    CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.kb_search_button))
                }
            }
        }
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (searching && results.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (results.isEmpty()) {
                Text(
                    text = stringResource(R.string.kb_search_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            } else {
                results.forEach { result -> SearchResultItem(result) }
            }
        }
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
                Text(stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun SearchResultItem(result: KbSearchResult) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = result.source.ifBlank { stringResource(R.string.kb_search_source_unknown) },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.kb_score, result.score),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (result.section.isNotBlank()) {
            Text(
                text = result.section,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = result.content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun kbStatusLabel(status: String): String = when (status.lowercase()) {
    "indexed", "done", "ok" -> stringResource(R.string.kb_status_indexed)
    "failed", "error" -> stringResource(R.string.kb_status_failed)
    else -> stringResource(R.string.kb_status_pending)
}

private fun kbStatusColor(status: String): Color = when (status.lowercase()) {
    "indexed", "done", "ok" -> StatusConnected
    "failed", "error" -> StatusError
    else -> StatusProcessing
}

/** 解析 content:// Uri 的文档显示名；失败回退为 Uri 末段。 */
private fun displayNameOf(contentResolver: android.content.ContentResolver, uri: Uri): String {
    val queried = contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        } else {
            null
        }
    }
    return queried ?: uri.lastPathSegment?.substringAfterLast('/') ?: ""
}