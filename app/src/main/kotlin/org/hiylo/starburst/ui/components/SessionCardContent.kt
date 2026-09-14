/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SessionCardContent.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.components

import androidx.compose.foundation.background
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.hiylo.starburst.R
import org.hiylo.starburst.domain.model.Session
import org.hiylo.starburst.domain.model.SessionCategory
import org.hiylo.starburst.domain.model.SessionStatus
import org.hiylo.starburst.ui.theme.StatusConnected
import org.hiylo.starburst.ui.theme.StatusError
import org.hiylo.starburst.ui.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionCardContent(
    session: Session,
    status: SessionStatus,
    isFavorite: Boolean,
    category: SessionCategory?,
    contextLabel: String,
    contextDetail: String? = null,
    isPinned: Boolean = false,
    isOffline: Boolean = false,
    compact: Boolean = false,
    leadingContent: @Composable () -> Unit = {},
    trailingContent: @Composable () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val isBusy = status == SessionStatus.Busy
    val pulse = if (isBusy) {
        val transition = rememberInfiniteTransition(label = "session_accent_pulse")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "session_accent_alpha",
        )
        value
    } else {
        1f
    }
    val accent = category?.let { sessionCategoryColor(it.color) }
        ?: MaterialTheme.colorScheme.primary
    val statusBadge: Triple<String, Color, ImageVector?>? = when (status) {
        SessionStatus.Busy -> Triple(stringResource(R.string.session_status_busy), StatusConnected, null)
        is SessionStatus.Retry -> Triple(stringResource(R.string.sessions_retrying), StatusError, null)
        SessionStatus.Question -> Triple(
            stringResource(R.string.session_status_pending_question),
            StatusWarning,
            Icons.Default.HelpOutline,
        )
        SessionStatus.Idle -> null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        if (isBusy) {
            val topGlowAlpha = 0.08f + 0.2f * pulse
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .requiredHeight(3.dp)
                        .graphicsLayer {
                            scaleY = 0.33f + 0.67f * pulse
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                        .background(
                            Brush.horizontalGradient(
                                0f to Color.Transparent,
                                0.18f to accent.copy(alpha = topGlowAlpha * 0.45f),
                                0.5f to accent.copy(alpha = topGlowAlpha),
                                0.82f to accent.copy(alpha = topGlowAlpha * 0.45f),
                                1f to Color.Transparent,
                            ),
                        ),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    top = if (compact) 6.dp else 10.dp,
                    bottom = if (compact) 6.dp else 10.dp,
                    end = 4.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingContent()
            Column(modifier = Modifier.weight(1f)) {
            val detail = contextDetail?.takeIf(String::isNotBlank)
            if (contextLabel.isNotBlank() || detail != null || isOffline) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (contextLabel.isNotBlank()) {
                        Text(
                            text = contextLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    detail?.let {
                        Text(
                            text = it,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (detail == null && isOffline) Spacer(Modifier.weight(1f))
                    if (isOffline) {
                        Text(
                            text = stringResource(R.string.cross_sessions_offline),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.height(if (compact) 1.dp else 2.dp))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isPinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = stringResource(R.string.session_pinned),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                    )
                }
                if (isFavorite) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = stringResource(R.string.session_favorite),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    )
                }
                category?.let {
                    Icon(
                        sessionCategoryIcon(it.icon),
                        contentDescription = it.name,
                        tint = sessionCategoryColor(it.color),
                        modifier = Modifier.size(15.dp),
                    )
                }
                Text(
                    text = session.title?.takeIf(String::isNotBlank)?.replace('\n', ' ') ?: stringResource(R.string.session_untitled),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (statusBadge != null) {
                    SessionStatusBadge(
                        label = statusBadge.first,
                        color = statusBadge.second,
                        icon = statusBadge.third,
                    )
                }
            }
            Spacer(Modifier.height(if (compact) 1.dp else 2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                category?.let {
                    Text(
                        text = it.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = sessionCategoryColor(it.color),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (session.time.updated > 0) {
                    Text(
                        text = dateFormat.format(Date(session.time.updated)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
                session.summary?.let { summary ->
                    if (summary.additions > 0) {
                        Text(
                            text = stringResource(R.string.session_changes_additions, summary.additions),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = StatusConnected,
                            ),
                        )
                    }
                    if (summary.deletions > 0) {
                        Text(
                            text = stringResource(R.string.session_changes_deletions, summary.deletions),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = StatusError,
                            ),
                        )
                    }
                }
            }
            }
            trailingContent()
        }
        if (category != null || isBusy) {
            val accentAlpha = if (category != null) {
                if (isBusy) 0.4f + 0.6f * pulse else 1f
            } else {
                0.08f + 0.2f * pulse
            }
            val accentScaleY = if (isBusy) 0.5f + 0.5f * pulse else 0.5f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .requiredHeight(4.dp)
                        .graphicsLayer {
                            scaleY = accentScaleY
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        }
                        .background(
                            Brush.horizontalGradient(
                                0f to Color.Transparent,
                                0.18f to accent.copy(alpha = accentAlpha * 0.45f),
                                0.5f to accent.copy(alpha = accentAlpha),
                                0.82f to accent.copy(alpha = accentAlpha * 0.45f),
                                1f to Color.Transparent,
                            ),
                        ),
                )
            }
        }
    }
}

/**
 * 会话状态彩色徽章：小圆角背景 + 状态色圆点（可选状态色图标）+ 状态色文字。
 *
 * @param label 状态文案。
 * @param color 状态语义色。
 * @param icon 可选的徽章图标（如「待选择/提问中」的问号），与圆点二选一展示。
 */
@Composable
private fun SessionStatusBadge(
    label: String,
    color: Color,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = color,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = color,
        )
    }
}
