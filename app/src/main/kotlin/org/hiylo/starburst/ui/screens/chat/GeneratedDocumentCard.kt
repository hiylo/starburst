/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : GeneratedDocumentCard.kt
 * Date : 2026/09/22 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.GeneratedDocument
import org.hiylo.starburst.ui.components.AppSecondaryButton

/**
 * 本会话生成的文档结果卡片：显示文档名与类型，提供「下载」「预览」「按意见修改」。
 *
 * 「预览」由上层打开 [DocumentPreviewSheet]，「按意见修改」由上层打开
 * [DocumentReviseDialog]，本组件只负责触发回调。
 *
 * @param document 生成的文档信息。
 * @param backendUrl 后端地址（由上层在预览时用来拼接预览/下载 URL）。
 * @param isDownloading 是否正在下载该文档（下载按钮展示 loading）。
 * @param onDownload 下载回调。
 * @param onPreview 预览回调。
 * @param onRevise 按意见修改回调。
 *
 * @author Hsi Chu
 * @since 3.1.0
 */
@Composable
internal fun GeneratedDocumentCard(
    document: GeneratedDocument,
    backendUrl: String,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onPreview: () -> Unit,
    onRevise: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = documentTypeDisplay(document.docType),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onRemove != null) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.document_remove),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSecondaryButton(
                    onClick = onDownload,
                    enabled = !isDownloading,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isDownloading) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.document_download),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                AppSecondaryButton(
                    onClick = onPreview,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.document_preview),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                AppSecondaryButton(
                    onClick = onRevise,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.document_revise_action),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}