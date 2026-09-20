/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : AppSurfaces.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.hiylo.starburst.ui.theme.AdaptiveRoundedShape
import org.hiylo.starburst.ui.theme.cartoonInkOutline
import org.hiylo.starburst.ui.theme.cartoonOffsetShadow
import org.hiylo.starburst.ui.theme.isCartoonStyle

val AppDialogShape = AdaptiveRoundedShape(20.dp, 32.dp)
val AppPickerItemShape = AdaptiveRoundedShape(12.dp, 24.dp)
val AppCardShape = AdaptiveRoundedShape(12.dp, 26.dp)
val AppSearchShape = AdaptiveRoundedShape(14.dp, 30.dp)
val LocalAmoledTheme = staticCompositionLocalOf { false }

/**
 * 卡通容器外观：硬偏移投影 + 墨色描边一次套用。
 *
 * 投影画在内容之下并向右下偏移，描边画在内容之上（不会被容器自身背景盖住），
 * 所以任意 Card / Surface / Box 直接套即可，不用再单独传 border 或 elevation 参数。
 * 关闭卡通风格时原样返回，不产生任何绘制节点。
 *
 * @param shape 容器形状，需与容器自身的 shape 一致
 */
@Composable
fun Modifier.cartoonChrome(shape: Shape = AppCardShape): Modifier =
    cartoonOffsetShadow(shape).cartoonInkOutline(shape)

@Composable
fun isAmoledTheme(): Boolean {
    return LocalAmoledTheme.current
}

@Composable
fun appDialogContainerColor(): Color {
    return if (isAmoledTheme()) Color.Black else AlertDialogDefaults.containerColor
}

@Composable
fun appPopupContainerColor(): Color {
    return if (isAmoledTheme()) Color.Black else MaterialTheme.colorScheme.surface
}

@Composable
fun appAmoledBorder(alpha: Float = 0.55f): BorderStroke? {
    return if (isAmoledTheme()) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha))
    } else {
        null
    }
}

@Composable
fun appDialogElevation(): Dp = if (isAmoledTheme()) 0.dp else 6.dp

@Composable
fun appSelectedItemColor(): Color {
    return if (isAmoledTheme()) Color.Black else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
}

@Composable
fun Modifier.appPopupBorder(shape: Shape = RoundedCornerShape(8.dp)): Modifier {
    val border = appAmoledBorder() ?: return this
    return border(border, shape)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = AppDialogShape,
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        // edge-to-edge 对话框：让 WindowInsets.ime 上报给内容，imePadding 才能把内容/按钮顶到键盘上方。
        properties = DialogProperties(decorFitsSystemWindows = false),
    ) {
        // 卡通风格：对话框从 0.8 弹性放大到 1，配合低阻尼弹簧得到"弹出"手感。
        val cartoon = isCartoonStyle()
        val pop = remember { Animatable(if (cartoon) 0.8f else 1f) }
        LaunchedEffect(Unit) {
            if (cartoon) {
                pop.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
                )
            }
        }
        Surface(
            modifier = modifier
                .imePadding()
                .scale(scaleX = pop.value, scaleY = pop.value)
                .cartoonChrome(shape),
            shape = shape,
            color = appDialogContainerColor(),
            border = if (isCartoonStyle()) null else appAmoledBorder(),
            tonalElevation = appDialogElevation(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun AppDialogActions(
    dismissText: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    destructiveConfirm: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        AppSecondaryButton(onClick = onDismiss) {
            Text(dismissText)
        }
        AppPrimaryButton(
            onClick = onConfirm,
            enabled = confirmEnabled,
            destructive = destructiveConfirm,
        ) {
            Text(confirmText)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun cartoonButtonStyle(
    modifier: Modifier,
    shape: Shape,
    enabled: Boolean,
): Pair<Modifier, MutableInteractionSource> {
    val interactionSource = remember { MutableInteractionSource() }
    if (!isCartoonStyle()) return modifier to interactionSource
    // 按下缩到 0.9，松开用低阻尼弹簧回弹——卡通按钮的"按下去弹回来"手感。
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "cartoon_button_scale",
    )
    return modifier.scale(scaleX = scale, scaleY = scale).cartoonChrome(shape) to interactionSource
}

@Composable
fun AppPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val shape = AdaptiveRoundedShape(20.dp, 28.dp)
    val (buttonModifier, interactionSource) = cartoonButtonStyle(modifier, shape, enabled)
    if (isAmoledTheme()) {
        OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            interactionSource = interactionSource,
            border = if (isCartoonStyle()) null else BorderStroke(
                1.dp,
                if (enabled) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Black,
                contentColor = accent,
            ),
            content = content,
        )
    } else {
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            interactionSource = interactionSource,
            colors = if (destructive) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
            content = content,
        )
    }
}

@Composable
fun AppSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    outlined: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val shape = AdaptiveRoundedShape(20.dp, 22.dp)
    val (buttonModifier, interactionSource) = cartoonButtonStyle(modifier, shape, enabled)
    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            interactionSource = interactionSource,
            border = if (isCartoonStyle()) null else BorderStroke(
                1.dp,
                if (enabled) accent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (isAmoledTheme()) Color.Black else Color.Transparent,
                contentColor = accent,
            ),
            content = content,
        )
    } else {
        TextButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            shape = shape,
            interactionSource = interactionSource,
            colors = ButtonDefaults.textButtonColors(contentColor = accent),
            content = content,
        )
    }
}
