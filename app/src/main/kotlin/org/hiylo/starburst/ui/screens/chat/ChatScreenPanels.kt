/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatScreenPanels.kt
 * Date : 2026/09/18 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.domain.model.*
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.appAmoledBorder
import org.hiylo.starburst.ui.components.isAmoledTheme



@Composable
internal fun DisconnectedServerBanner() {
    val isAmoled = isAmoledTheme()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = stringResource(R.string.chat_server_disconnected),
                style = MaterialTheme.typography.bodySmall,
                color = if (isAmoled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            )
        }
    }
}






/**
 * Banner shown when messages have been reverted.
 * Tapping restores (redo) the reverted messages.
 */
@Composable
internal fun RevertBanner(onRedo: () -> Unit) {
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current
    val isAmoled = isAmoledTheme()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
        border = if (isAmoled) appAmoledBorder() else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { role = Role.Button }
            .clickable { performHaptic(hapticView, hapticOn); onRedo() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Undo,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isAmoled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onTertiaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.chat_messages_reverted),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isAmoled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = stringResource(R.string.chat_tap_restore),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isAmoled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                Icons.Default.Restore,
                contentDescription = stringResource(R.string.chat_restore),
                modifier = Modifier.size(20.dp),
                tint = if (isAmoled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}



/**
 * 会话异常结束横幅：upstream 对会话发 session.error（限流/超时/取消）后，
 * 会话由处理中直接变空闲。此横幅把错误原因展示出来，避免「无感知变空闲」。
 *
 * @param message 会话级错误文案（已取 message/name，可能为空）。
 * @param retry true 时仍处于加载/处理中，仅做弱提示；false 时以错误样式展示。
 */
@Composable
internal fun ChatSessionErrorBanner(message: String?, retry: Boolean) {
    val isAmoled = isAmoledTheme()
    val label = message?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.session_error_banner)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        border = if (isAmoled) appAmoledBorder() else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isAmoled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onErrorContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.session_error_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isAmoled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isAmoled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

internal fun isWorkingSessionStatus(status: SessionStatus): Boolean =
    status is SessionStatus.Busy || status is SessionStatus.Retry

internal fun retryDelaySeconds(nextAtMillis: Long, nowMillis: Long): Long =
    ((nextAtMillis - nowMillis).coerceAtLeast(0) + 999) / 1_000

@Composable
internal fun SuggestionRow(
    suggestions: List<String>,
    suggestionsSource: SuggestionSource? = null,
    isGenerating: Boolean,
    error: String?,
    streamText: String = "",
    modelNeedsDownload: Boolean = false,
    modelDownloading: Boolean = false,
    modelDownloadProgress: Int = 0,
    onDownloadModel: () -> Unit = {},
    onSuggestionClick: (String) -> Unit,
    onGenerate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isAmoled = isAmoledTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            isGenerating -> {
                // Show the model's streamed text live if we have any, otherwise a spinner.
                if (streamText.isNotBlank()) {
                    Text(
                        text = streamText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    PulsingDotsIndicator(dotSize = 5.dp, dotSpacing = 3.dp, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.chat_suggestions_generating),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
            error != null -> {
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (modelDownloading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.chat_suggestions_model_downloading, modelDownloadProgress),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        LinearProgressIndicator(
                            progress = { modelDownloadProgress / 100f },
                            modifier = Modifier.width(90.dp).height(4.dp),
                        )
                    }
                } else if (modelNeedsDownload) {
                    TextButton(onClick = onDownloadModel) {
                        Text(stringResource(R.string.chat_suggestions_download_model))
                    }
                } else {
                    TextButton(onClick = onGenerate) {
                        Text(stringResource(R.string.retry))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
            suggestions.isNotEmpty() -> {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        suggestionsSource?.let { source ->
                            Text(
                                text = stringResource(
                                    when (source) {
                                        SuggestionSource.BACKEND -> R.string.chat_suggestions_source_server
                                        SuggestionSource.CLOUD -> R.string.chat_suggestions_source_cloud
                                        SuggestionSource.ON_DEVICE -> R.string.chat_suggestions_source_on_device
                                        SuggestionSource.FALLBACK -> R.string.chat_suggestions_source_fallback
                                    }
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        suggestions.take(3).forEach { suggestion ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isAmoled) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSuggestionClick(suggestion) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Send,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    )
                                    Text(
                                        text = suggestion,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }
            else -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isAmoled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    },
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.chat_suggestions_generate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onGenerate)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun RetryStatusBanner(retry: SessionStatus.Retry) {
    var nowMillis by remember(retry.next) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(retry.next) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            val delayMillis = retry.next - nowMillis
            if (delayMillis <= 0) break
            kotlinx.coroutines.delay(minOf(1_000L, delayMillis))
        }
    }
    val remainingSeconds = retryDelaySeconds(retry.next, nowMillis)
    val isAmoled = isAmoledTheme()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.sessions_retrying),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = retry.message.ifBlank { stringResource(R.string.error_unknown) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (remainingSeconds > 0) {
                        stringResource(R.string.chat_retry_waiting, remainingSeconds, retry.attempt)
                    } else {
                        stringResource(R.string.chat_retry_now, retry.attempt)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
internal fun VoiceListeningBanner(voiceLevel: Float) {
    val isAmoled = isAmoledTheme()
    val level = (voiceLevel / 10f).coerceIn(0f, 1f)
    val bars = 16
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            // Volume waveform
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                repeat(bars) { index ->
                    val barLevel = if (level <= 0f) {
                        0.15f
                    } else {
                        // Animate bars relative to the voice level with a slight falloff.
                        val peak = 1f - (kotlin.math.abs(index - bars / 2).toFloat() / (bars / 2f)) * 0.6f
                        (level * peak).coerceIn(0.12f, 1f)
                    }
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height((6f + 22f * barLevel).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                    )
                }
            }
            Text(
                text = stringResource(R.string.chat_voice_input_listening),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}




