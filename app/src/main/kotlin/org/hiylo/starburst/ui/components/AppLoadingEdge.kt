/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : AppLoadingEdge.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun AppLoadingEdge(
    active: Boolean,
    progress: Float = if (active) 1f else 0f,
    modifier: Modifier = Modifier,
) {
    val scale: Float
    val alpha: Float
    if (active) {
        // Only run the infinite animation while active; when inactive we use the static
        // progress so an idle list does not pay a per-frame invalidate cost.
        val transition = rememberInfiniteTransition(label = "app_loading_edge")
        val pulse by transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "app_loading_edge_pulse",
        )
        scale = pulse
        alpha = 0.9f
    } else {
        scale = progress.coerceIn(0f, 1f)
        alpha = (progress * 1.8f).coerceIn(0f, 0.9f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                transformOrigin = TransformOrigin.Center
            }
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.35f to MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    0.5f to MaterialTheme.colorScheme.primary,
                    0.65f to MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    1f to Color.Transparent,
                )
            )
    )
}
