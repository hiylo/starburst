/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatSessionDiffDialog.kt
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.domain.model.*
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.AppSecondaryButton
import org.hiylo.starburst.ui.components.AppCardShape
import org.hiylo.starburst.ui.components.appAmoledBorder
import org.hiylo.starburst.ui.components.cartoonChrome
import org.hiylo.starburst.ui.components.isAmoledTheme
import org.hiylo.starburst.ui.theme.StatusConnected
import org.hiylo.starburst.ui.theme.StatusError

/**
 * Session diff dialog: the changed-file list and the per-file before/after view.
 * Extracted verbatim from ChatDialogs.kt.
 */

@Composable
internal fun SessionDiffDialog(
    diffs: List<FileDiff>,
    onDismiss: () -> Unit,
) {
    var expanded by remember { mutableStateOf<String?>(null) }
    ChatDialog(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.session_changes_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            if (diffs.isEmpty()) {
                Text(
                    text = stringResource(R.string.session_changes_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(diffs, key = { it.file }) { diff ->
                        FileDiffCard(
                            diff = diff,
                            expanded = expanded == diff.file,
                            onToggle = {
                                expanded = if (expanded == diff.file) null else diff.file
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AppSecondaryButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@Composable
private fun FileDiffCard(
    diff: FileDiff,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val isAmoled = isAmoledTheme()
    val statusColor = when (diff.status) {
        "added" -> StatusConnected
        "deleted" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = appAmoledBorder(0.65f),
        modifier = Modifier.cartoonChrome(AppCardShape).fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = diff.file,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "+${diff.additions}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusConnected,
                        )
                        Text(
                            text = "-${diff.deletions}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        diff.status?.let { status ->
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                SessionFileDiffContent(before = diff.before, after = diff.after, patch = diff.patch)
            }
        }
    }
}

@Composable
private fun SessionFileDiffContent(before: String, after: String, patch: String = "") {
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (before.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StatusError.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                    .padding(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.session_changes_before),
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusError,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = before,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (after.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StatusConnected.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                    .padding(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.session_changes_after),
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusConnected,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = after,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        // V2 REST diff 只有原始 unified patch：before/after 均缺失时直接渲染 patch。
        if (before.isBlank() && after.isBlank() && patch.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(lineColor, RoundedCornerShape(6.dp))
                    .padding(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.session_changes_patch),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = patch,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
