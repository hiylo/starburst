/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : GitDiffViews.kt
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.ui.screens.git

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.theme.StatusConnected
import org.hiylo.starburst.ui.theme.StatusError
import org.hiylo.starburst.ui.theme.StatusWarning

/**
 * Side-by-side diff, per-file diff and commit detail views of the git screen.
 * Extracted verbatim from GitScreen.kt.
 */

@Composable
internal fun DiffView(diff: GitFileDiff) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.git_diff),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (diff.content.isBlank()) {
                Text(
                    stringResource(R.string.git_no_changes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                diff.content.lines().forEach { line ->
                    val color = when {
                        line.startsWith("+") -> StatusConnected
                        line.startsWith("-") -> StatusError
                        line.startsWith("@@") -> MaterialTheme.colorScheme.primary
                        else -> Color.Unspecified
                    }
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun CommitDetail(
    changes: List<GitChange>,
    diff: String,
    loading: Boolean,
    commitHash: String,
    fileDiff: GitFileDiff?,
    onLoadFileDiff: (String, String) -> Unit,
) {
    if (loading) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.git_loading), style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    if (changes.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            changes.forEach { c ->
                val (color, label) = when (c.status) {
                    "added" -> StatusConnected to stringResource(R.string.git_status_added)
                    "deleted" -> StatusError to stringResource(R.string.git_status_deleted)
                    else -> StatusWarning to stringResource(R.string.git_status_modified)
                }
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLoadFileDiff(commitHash, c.path) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            c.path,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (c.additions > 0) {
                            Text("+${c.additions}", color = StatusConnected, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.width(6.dp))
                        }
                        if (c.deletions > 0) {
                            Text("-${c.deletions}", color = StatusError, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (fileDiff?.path == c.path) {
                        Spacer(Modifier.height(4.dp))
                        FileDiffView(fileDiff)
                    }
                }
            }
        }
    }
    if (diff.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                diff.lines().take(200).forEach { line ->
                    val color = when {
                        line.startsWith("+") -> StatusConnected
                        line.startsWith("-") -> StatusError
                        line.startsWith("@@") || line.startsWith("diff") || line.startsWith("index") -> MaterialTheme.colorScheme.primary
                        else -> Color.Unspecified
                    }
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
                    )
                }
            }
        }
    }
}

@Composable
internal fun FileDiffView(fileDiff: GitFileDiff) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            if (fileDiff.content.isBlank()) {
                Text(
                    stringResource(R.string.git_no_changes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                fileDiff.content.lines().take(200).forEach { line ->
                    val color = when {
                        line.startsWith("+") -> StatusConnected
                        line.startsWith("-") -> StatusError
                        line.startsWith("@@") || line.startsWith("diff") || line.startsWith("index") -> MaterialTheme.colorScheme.primary
                        else -> Color.Unspecified
                    }
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else color,
                    )
                }
            }
        }
    }
}
