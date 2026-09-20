/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatFileAndImageCards.kt
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import org.hiylo.starburst.domain.model.*
import org.hiylo.starburst.ui.theme.CodeTypography
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.isAmoledTheme

/**
 * Patch, image and file cards plus the full-screen image preview dialog.
 * Extracted verbatim from ChatOverlayCards.kt.
 */

@Composable
internal fun PatchCard(patch: Part.Patch) {
    val isAmoled = isAmoledTheme()
    val autoExpand = LocalCollapseTools.current
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current
    var expanded by remember(autoExpand) { mutableStateOf(autoExpand) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
        tonalElevation = if (isAmoled) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .expandableToolHeader(expanded) {
                        performHaptic(hapticView, hapticOn)
                        expanded = !expanded
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (patch.files.size == 1)
                            stringResource(R.string.chat_files_changed, patch.files.size)
                        else
                            stringResource(R.string.chat_files_changed_plural, patch.files.size),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) stringResource(R.string.chat_collapse) else stringResource(R.string.chat_expand),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }

            // Expanded file list
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (filePath in patch.files) {
                        Text(
                            text = filePath.substringAfterLast('/'),
                            style = CodeTypography.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ImageThumbnailRow(
    imageFiles: List<Part.File>,
) {
    var previewIndex by remember { mutableStateOf(-1) }
    val requestSaveImage = LocalImageSaveRequest.current
    val appCacheDirectory = LocalContext.current.cacheDir

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for ((index, file) in imageFiles.withIndex()) {
            val imageModel = remember(file.url, appCacheDirectory) {
                partFileImageModel(file, appCacheDirectory)
            }

            if (imageModel != null) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = file.filename ?: stringResource(R.string.chat_image),
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { previewIndex = index },
                    contentScale = ContentScale.Crop
                )
            } else {
                // Fallback placeholder for failed decode
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.BrokenImage,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }

    // Fullscreen image preview dialog
    if (previewIndex >= 0 && previewIndex < imageFiles.size) {
        val file = imageFiles[previewIndex]
        val imageBytes = remember(file.url, appCacheDirectory) {
            decodePartFileBytes(file, appCacheDirectory)
        }
        val imageModel = remember(file.url, appCacheDirectory) {
            partFileImageModel(file, appCacheDirectory)
        }

        if (imageModel != null) {
            ImagePreviewDialog(
                imageModel = imageModel,
                contentDescription = file.filename ?: stringResource(R.string.chat_image),
                onDismiss = { previewIndex = -1 },
                onSave = {
                    if (imageBytes != null) {
                        requestSaveImage(imageBytes, file.mime, file.filename)
                    }
                },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ImagePreviewDialog(
    imageModel: Any,
    contentDescription: String?,
    onDismiss: () -> Unit,
    onSave: (() -> Unit)? = null,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(imageModel) {
                        detectTapGestures(
                            onDoubleTap = { tapPosition ->
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2f
                                    offset = Offset(
                                        x = size.width / 2f - tapPosition.x,
                                        y = size.height / 2f - tapPosition.y,
                                    )
                                }
                            },
                        )
                    }
                    .pointerInput(imageModel) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val previousScale = scale
                            val newScale = (previousScale * zoom).coerceIn(1f, 5f)
                            if (newScale == 1f) {
                                scale = 1f
                                offset = Offset.Zero
                                return@detectTransformGestures
                            }

                            val appliedZoom = newScale / previousScale
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val requestedOffset = offset * appliedZoom +
                                (center - centroid) * (appliedZoom - 1f) + pan
                            val maxOffsetX = size.width * (newScale - 1f) / 2f
                            val maxOffsetY = size.height * (newScale - 1f) / 2f
                            offset = Offset(
                                x = requestedOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                                y = requestedOffset.y.coerceIn(-maxOffsetY, maxOffsetY),
                            )
                            scale = newScale
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                    contentScale = ContentScale.Fit,
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val actionContainerColor = Color.Black.copy(alpha = 0.58f)
                val actionBorderColor = Color.White.copy(alpha = 0.32f)
                val actionTintColor = Color.White

                if (onSave != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = actionContainerColor,
                        border = BorderStroke(1.dp, actionBorderColor),
                    ) {
                        IconButton(onClick = onSave, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = stringResource(R.string.chat_save_image),
                                tint = actionTintColor,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = actionContainerColor,
                    border = BorderStroke(1.dp, actionBorderColor),
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = actionTintColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun FileCard(file: Part.File) {
    // Images are handled by ImageThumbnailRow, so FileCard only handles non-image files
    FileCardFallback(file)
}

@Composable
private fun FileCardFallback(file: Part.File) {
    val isAmoled = isAmoledTheme()
    val containerColor = if (isAmoled) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderColor = if (isAmoled) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)
    }
    val contentColor = if (isAmoled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = file.filename
                    ?: file.url?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                    ?: file.mime,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
