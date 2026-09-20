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
import androidx.compose.material3.Shapes
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
 * 卡通 M3 形状方案。
 *
 * M3 组件的默认形状是 `tokens.ContainerShape.value`，而它最终解析到
 * `MaterialTheme.shapes.fromToken(...)`——所以只换主题 shapes，就能一次性覆盖所有
 * 没有显式传 shape 的组件：Card(medium)、FAB(large)、Snackbar / 输入框(extraSmall)、
 * Chip(small)。Button / Switch / Badge 走 CornerFull，本来就是全圆，不受影响。
 *
 * 档位取值与 [AdaptiveRoundedShape] 的卡通端对齐（卡片 26dp、对话框 32dp），避免两套体系给出
 * 两种明显不同的圆角。
 */
fun cartoonShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(26.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(38.dp),
)

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
 * 贴纸色片：在内容**外围**扩出一块带墨线和硬投影的圆角色片。
 *
 * 刻意用"绘制外扩"而不是 padding + 背景：调用点的 modifier 原样保留即可，布局尺寸、
 * align/padding 语义、以及关闭卡通风格时的外观都不受影响。代价是色片画在自身边界之外，
 * 祖先若开了 `clipToBounds` 会被裁掉——所以只用于空状态、英雄位这类不在裁剪容器里的位置。
 *
 * @param padding 色片相对内容向外扩出的量
 */
@Composable
fun Modifier.cartoonStickerChip(
    shape: Shape,
    containerColor: Color,
    padding: Dp,
): Modifier {
    if (!isCartoonStyle()) return this
    val ink = cartoonInkColor()
    val shadow = cartoonShadowColor()
    val stroke = cartoonStrokeWidth()
    val shift = cartoonShadowOffset()
    return drawWithContent {
        val pad = padding.toPx()
        val dx = shift.toPx()
        val chip = Size(size.width + pad * 2f, size.height + pad * 2f)
        translate(-pad + dx, -pad + dx) { drawShapePath(shape, shadow, Fill, chip) }
        translate(-pad, -pad) { drawShapePath(shape, containerColor, Fill, chip) }
        drawContent()
        translate(-pad, -pad) {
            drawShapePath(shape, ink, Stroke(stroke.toPx(), cap = StrokeCap.Butt), chip)
        }
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
 * 半径为零时返回 [Outline.Rectangle]；均匀圆角（[RoundedCornerShape] 的常规情况）返回
 * [Outline.Rounded]（自带 [RoundRect] 与 [CornerRadius]，直接绘制）；不对称圆角返回
 * [Outline.Generic]，复用其路径即可。
 */
private fun DrawScope.drawShapePath(
    shape: Shape,
    color: Color,
    style: DrawStyle,
    outlineSize: Size = this.size,
) {
    val size = outlineSize
    when (val outline = shape.createOutline(size, layoutDirection, this)) {
        is Outline.Rectangle -> drawRoundRect(
            color = color,
            topLeft = outline.rect.topLeft,
            size = outline.rect.size,
            cornerRadius = CornerRadius(cornerRadiusPx(shape)),
            style = style,
        )

        is Outline.Rounded -> drawRoundRect(
            color = color,
            topLeft = Offset(outline.roundRect.left, outline.roundRect.top),
            size = Size(outline.roundRect.width, outline.roundRect.height),
            cornerRadius = outline.roundRect.topLeftCornerRadius,
            style = style,
        )

        is Outline.Generic -> drawPath(outline.path, color, style = style)
        else -> throw IllegalStateException("不支持的 outline 类型：$outline")
    }
}
