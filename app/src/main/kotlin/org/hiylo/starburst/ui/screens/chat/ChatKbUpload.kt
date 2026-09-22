/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatKbUpload.kt
 * Date : 2026/09/23 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.KbCollection
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.isAmoledTheme

/**
 * 聊天附件弹层进入的「上传文档到知识库」底部弹窗：勾选当前项目文档，
 * 选择目标知识库集合后逐个上传（供 RAG 检索）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UploadToKbSheet(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isAmoled = isAmoledTheme()

    var scanning by remember { mutableStateOf(true) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var scan by remember { mutableStateOf(KbUploadScan()) }
    var selectedPaths by remember { mutableStateOf(setOf<String>()) }
    var collections by remember { mutableStateOf<List<KbCollection>>(emptyList()) }
    var collectionsLoading by remember { mutableStateOf(true) }
    var selectedCollectionId by remember { mutableStateOf<Long?>(null) }
    var collectionDropdownOpen by remember { mutableStateOf(false) }
    var creatingCollection by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var uploadDone by remember { mutableStateOf(0) }
    var uploadTotal by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        scan = try {
            viewModel.enumerateKbUploadFiles()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            scanError = e.message ?: context.getString(R.string.chat_kb_upload_load_failed)
            KbUploadScan()
        }
        scanning = false
    }
    LaunchedEffect(Unit) {
        viewModel.loadKbCollectionsForUpload { result ->
            collections = result
            collectionsLoading = false
            if (selectedCollectionId == null) selectedCollectionId = result.firstOrNull()?.id
        }
    }

    val selectedFiles = scan.files.filter { it.path in selectedPaths }
    val selectedCollectionName = collections.firstOrNull { it.id == selectedCollectionId }?.name

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                BottomSheetDefaults.DragHandle()
            }
            Text(
                text = stringResource(R.string.chat_kb_upload_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.chat_kb_upload_documents, scan.files.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                TextButton(
                    onClick = { selectedPaths = scan.files.mapTo(mutableSetOf()) { it.path } },
                    enabled = scan.files.isNotEmpty() && selectedPaths.size != scan.files.size,
                ) {
                    Text(stringResource(R.string.chat_kb_upload_select_all))
                }
                TextButton(
                    onClick = { selectedPaths = emptySet() },
                    enabled = selectedPaths.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.chat_kb_upload_clear_all))
                }
            }

            when {
                scanning -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                scanError != null -> Text(
                    text = scanError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
                scan.files.isEmpty() -> Text(
                    text = stringResource(R.string.chat_kb_upload_no_files),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                )
                else -> LazyColumn(modifier = Modifier.weight(1f, fill = false).fillMaxWidth()) {
                    items(scan.files, key = { it.path }) { file ->
                        UploadKbFileRow(
                            name = file.name,
                            size = file.size,
                            checked = file.path in selectedPaths,
                            onCheckedChange = { checked ->
                                selectedPaths = if (checked) {
                                    selectedPaths + file.path
                                } else {
                                    selectedPaths - file.path
                                }
                            },
                        )
                    }
                }
            }

            if (scan.skippedDirs > 0) {
                Text(
                    text = stringResource(R.string.chat_kb_upload_skipped_dirs, scan.skippedDirs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

            Text(
                text = stringResource(R.string.chat_kb_upload_collection),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
            )
            when {
                collectionsLoading -> Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.chat_kb_upload_loading_collections),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                collections.isEmpty() -> Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = stringResource(R.string.chat_kb_upload_no_collection),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            creatingCollection = true
                            viewModel.createKbUploadCollection { created ->
                                creatingCollection = false
                                if (created != null) {
                                    collections = collections + created
                                    selectedCollectionId = created.id
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.chat_kb_upload_create_failed),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        },
                        enabled = !creatingCollection,
                    ) {
                        if (creatingCollection) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.chat_kb_upload_create_default))
                    }
                }
                else -> Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Surface(
                        onClick = { collectionDropdownOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                        border = if (isAmoled) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        } else {
                            null
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = selectedCollectionName
                                    ?: stringResource(R.string.chat_kb_upload_select_collection),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = collectionDropdownOpen,
                        onDismissRequest = { collectionDropdownOpen = false },
                    ) {
                        collections.forEach { collection ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = collection.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                leadingIcon = if (collection.id == selectedCollectionId) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                                onClick = {
                                    selectedCollectionId = collection.id
                                    collectionDropdownOpen = false
                                },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (uploading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.chat_kb_upload_progress, uploadDone, uploadTotal),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                } else {
                    AppPrimaryButton(
                        onClick = {
                            val collectionId = selectedCollectionId ?: return@AppPrimaryButton
                            uploading = true
                            uploadDone = 0
                            uploadTotal = selectedFiles.size
                            viewModel.uploadDocumentsToKb(
                                collectionId = collectionId,
                                files = selectedFiles,
                                onProgress = { done, total ->
                                    uploadDone = done
                                    uploadTotal = total
                                },
                                onResult = { allOk, uploaded ->
                                    uploading = false
                                    val message = if (allOk) {
                                        context.getString(R.string.chat_kb_upload_done, uploaded)
                                    } else {
                                        context.getString(
                                            R.string.chat_kb_upload_failed,
                                            selectedFiles.size - uploaded,
                                            selectedFiles.size,
                                        )
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                },
                            )
                        },
                        enabled = selectedCollectionId != null && selectedFiles.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.chat_kb_upload_upload))
                    }
                }
            }
        }
    }
}

/** 单个文档候选行：勾选框 + 文件名 + 大小。 */
@Composable
private fun UploadKbFileRow(
    name: String,
    size: Long?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(start = 8.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange(it) },
        )
        Icon(
            imageVector = if (isKbMarkdownName(name)) {
                Icons.Default.Description
            } else {
                Icons.Default.Code
            },
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = size?.let { formatFileSize(it.toInt()) } ?: "-",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun isKbMarkdownName(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in setOf("md", "markdown", "txt")
