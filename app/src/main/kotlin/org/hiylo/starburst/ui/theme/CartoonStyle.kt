/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : CartoonStyle.kt
 * Date : 2026/09/19 22:49:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 卡通风格（Cartoon Style）。
 *
 * 卡通感不来自换色，而来自三件事：更大的圆角、粗描边（ink outline）、实体投影。
 * 配色仍沿用主题方案体系（见 [ThemeScheme]），本文件只负责形状 / 描边 / 投影风格。
 *
 * @author Hsi Chu
 * @since 1.0
 */

/** 卡通风格开关的 CompositionLocal，供 composable 内读取。 */
val LocalCartoonStyle = staticCompositionLocalOf { false }

/** 卡通风格是否开启。 */
@Composable
fun isCartoonStyle(): Boolean = LocalCartoonStyle.current

/**
 * 卡通风格全局开关。
 *
 * [Shape] 是普通对象、拿不到 CompositionLocal，因此由 StarBurstTheme 通过 SideEffect
 * 同步写入，供 [AdaptiveRoundedShape] 在绘制期读取。@Volatile 保证跨线程可见性。
 */
internal object CartoonStyleState {
    @Volatile
    var enabled: Boolean = false
}

/**
 * 自适应圆角形状：卡通风格开启时返回卡通圆角，否则返回常规圆角。
 *
 * 在 [createOutline]（绘制期）读取全局开关，因此既有 `shape = AppCardShape` 等
 * 调用点无需任何修改即可整体升级。
 *
 * @param normalRadius 常规圆角
 * @param cartoonRadius 卡通圆角
 */
class AdaptiveRoundedShape(
    private val normalRadius: Dp,
    private val cartoonRadius: Dp,
) : Shape {

    /** 当前生效的圆角半径，供描边/投影按形状取路径时使用。 */
    val radius: Dp
        get() = if (CartoonStyleState.enabled) cartoonRadius else normalRadius

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radius = if (CartoonStyleState.enabled) cartoonRadius else normalRadius
        return RoundedCornerShape(radius).createOutline(size, layoutDirection, density)
    }
}

/** 卡通描边宽度：2.5dp，常规模式 1dp。 */
@Composable
fun cartoonStrokeWidth(): Dp = if (isCartoonStyle()) 2.5.dp else 1.dp

/**
 * 卡通描边色（ink）。
 *
 * 浅色表面用近黑墨色，深色表面用提亮的 onSurface，保证两档下描边都可见。
 */
@Composable
fun cartoonInkColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.surface.luminance() > 0.5f) {
        Color(0xFF241F33).copy(alpha = 0.85f)
    } else {
        scheme.onSurface.copy(alpha = 0.45f)
    }
}

/**
 * 卡通实体投影色。
 *
 * 浅色表面用带靛调的深灰（漫画纸感），深色表面用纯黑压暗。
 */
@Composable
fun cartoonShadowColor(): Color {
    return if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFF3A2E5C).copy(alpha = 0.30f)
    } else {
        Color.Black.copy(alpha = 0.65f)
    }
}

/** 硬偏移投影的位移量：投影整体向右下挪，得到漫画式的实体色块投影。 */
@Composable
fun cartoonShadowOffset(): Dp = if (isCartoonStyle()) 3.dp else 0.dp

/** 纸纹点阵的点间距。 */
@Composable
fun cartoonBackdropSpacing(): Dp = if (isCartoonStyle()) 18.dp else 0.dp

/** 纸纹点阵的单点半径。 */
@Composable
fun cartoonBackdropDot(): Dp = if (isCartoonStyle()) 1.3.dp else 0.dp

/**
 * 纸纹点阵的点色：浅色表面极淡墨点，深色表面极淡亮点。
 *
 * 透明度刻意压得很低——它只提供"贴纸纸"底纹，不能干扰信息层级。
 */
@Composable
fun cartoonDotColor(): Color {
    return if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFF241F33).copy(alpha = 0.07f)
    } else {
        Color.White.copy(alpha = 0.05f)
    }
}

/**
 * 纸纹点阵背景：卡通模式下给整屏铺一层极淡的点阵底纹。
 *
 * 关闭时直接返回原 Modifier，不产生任何绘制节点。
 */
@Composable
fun Modifier.cartoonBackdrop(): Modifier {
    if (!isCartoonStyle()) return this
    val color = cartoonDotColor()
    val spacing = cartoonBackdropSpacing()
    val dot = cartoonBackdropDot()
    return drawBehind {
        val step = spacing.toPx()
        val radius = dot.toPx()
        var x = step / 2f
        while (x < size.width) {
            var y = step / 2f
            while (y < size.height) {
                drawCircle(color, radius = radius, center = Offset(x, y))
                y += step
            }
            x += step
        }
    }
}

/**
 * 硬偏移投影：在内容下方绘制一个向右下偏移的同形状色块。
 *
 * 与模糊投影不同——它不用模糊半径，边缘是硬的，更接近漫画书的实体投影。
 * 必须先于容器的背景使用（作为外层 Modifier）。
 */
@Composable
fun Modifier.cartoonOffsetShadow(shape: Shape): Modifier {
    if (!isCartoonStyle()) return this
    val color = cartoonShadowColor()
    val offset = cartoonShadowOffset()
    return drawWithContent {
        val dx = offset.toPx()
        translate(dx, dx) {
            drawShapePath(shape, color, Fill)
        }
        drawContent()
    }
}

/**
 * 墨色描边：画在内容之上，因此不会被容器自身的背景色盖住。
 *
 * Surface 的 border 参数由容器自己绘制、会被背景覆盖内半边，画在最外层才能
 * 拿到完整的 2.5dp 墨线。
 */
@Composable
fun Modifier.cartoonInkOutline(shape: Shape): Modifier {
    if (!isCartoonStyle()) return this
    val color = cartoonInkColor()
    val stroke = cartoonStrokeWidth()
    return drawWithContent {
        drawContent()
        drawShapePath(shape, color, Stroke(stroke.toPx(), cap = StrokeCap.Butt))
    }
}

/**
 * 取 [Shape] 当前生效的均匀圆角半径（px）。
 *
 * 只有 [AdaptiveRoundedShape] 暴露半径；不对称圆角的 outline 走 [Outline.Generic] 路径分支，
 * 不需要半径，所以其余形状按 0 处理。
 */
private fun DrawScope.cornerRadiusPx(shape: Shape): Float =
    if (shape is AdaptiveRoundedShape) with(this) { shape.radius.toPx() } else 0f

/**
 * 把 [Shape] 的 outline 转成绘制调用。
 *
 * 均匀圆角会返回 [Outline.Rectangle]（不暴露圆角半径），需要按形状自己取半径绘制；
 * 不对称圆角返回 [Outline.Generic]，直接复用其路径即可。
 */
private fun DrawScope.drawShapePath(shape: Shape, color: Color, style: DrawStyle) {
    val size = Size(size.width, size.height)
    when (val outline = shape.createOutline(size, layoutDirection, this)) {
        is Outline.Rectangle -> drawRoundRect(
            color = color,
            topLeft = outline.rect.topLeft,
            size = outline.rect.size,
            cornerRadius = CornerRadius(cornerRadiusPx(shape)),
            style = style,
        )

        is Outline.Generic -> drawPath(outline.path, color, style = style)
        else -> throw IllegalStateException("不支持的 outline 类型：$outline")
    }
}
