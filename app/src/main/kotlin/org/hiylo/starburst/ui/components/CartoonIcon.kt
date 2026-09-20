/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : CartoonIcon.kt
 * Date : 2026-09-20 23:40:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.hiylo.starburst.ui.theme.cartoonInkColor
import org.hiylo.starburst.ui.theme.cartoonStickerChip
import org.hiylo.starburst.ui.theme.isCartoonStyle

/**
 * 卡通图标外观：墨线勾边与贴纸底座。
 *
 * 两者都不动图标字形本身，只在绘制层加工——所以不需要为某个具体图标单独适配，也不需要引入
 * 第二套图标资产。关闭卡通风格时都退化成一次普通 [Icon] 绘制，路径与开销完全一致。
 *
 * @author Hsi Chu
 * @since 1.0
 */

/** 轮廓环采样点数：墨色沿图标外轮廓铺满一圈所需的方位数。 */
private const val InkRingSamples = 12

/**
 * 墨线勾边图标。
 *
 * 把图标先沿圆周偏移重绘 [InkRingSamples] 次墨色、再盖上本色，得到字形**外轮廓**的一圈描边。
 * 刻意不给每个子路径单独描边：镂空（齿轮孔、字母 O 的眼）和密集笔画会被内部描线糊掉，
 * 而外轮廓描边无论图标多复杂都只是加一圈边。
 *
 * @param size 图标边长；调用方 modifier 里已有的 `size` 优先级更高
 * @param inkWidth 墨线粗细（实际向外扩展量为其一半）；传 0 则退化为普通 [Icon]
 */
@Composable
fun CartoonInkIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
    inkWidth: Dp = 1.2.dp,
    inkColor: Color = cartoonInkColor(),
) {
    if (!isCartoonStyle() || inkWidth <= 0.dp) {
        Icon(imageVector, contentDescription, modifier.size(size), tint)
        return
    }
    val painter = rememberVectorPainter(imageVector)
    val inkFilter = remember(inkColor) { ColorFilter.tint(inkColor) }
    val tintFilter = remember(tint) { if (tint == Color.Unspecified) null else ColorFilter.tint(tint) }
    Box(
        modifier
            .size(size)
            .drawBehind {
                // 显式 this.size：外层函数有同名 size 参数（Dp），会被局部变量优先解析。
                val drawSize = this.size
                val reach = inkWidth.toPx() / 2f
                for (i in 0 until InkRingSamples) {
                    val angle = (2f * PI * i / InkRingSamples).toFloat()
                    translate(cos(angle) * reach, sin(angle) * reach) {
                        with(painter) { draw(size = drawSize, colorFilter = inkFilter) }
                    }
                }
                with(painter) { draw(size = drawSize, colorFilter = tintFilter) }
            }
            .then(cartoonIconSemantics(contentDescription)),
    )
}

/**
 * 贴纸底座图标：图标坐在一枚带墨线、硬投影、轻微倾斜的色片上。
 *
 * 倾斜是这套处理的关键——贴纸不会摆得笔直，配上右下偏移的硬投影才有"从纸上揭起来"的实体感。
 * 用于空状态、英雄位这类大块图标；小尺寸处用 [CartoonInkIcon] 就够了。
 *
 * [modifier] 与 [tint] 原样交给内部的 [Icon]，所以关闭卡通风格时与直接写 [Icon] 完全等价；
 * 色片靠绘制外扩得到（见 `cartoonStickerChip`），不改变布局尺寸。
 *
 * @param padding 色片相对图标向外扩出的量
 * @param tilt 倾斜角度（度），负值逆时针
 * @param stickerTint 仅卡通模式生效的字形染色。色片是有色底，原来的浅色淡染放上去会糊成一片，
 *   但直接改 [tint] 又会动到关闭卡通风格时的外观，所以单独给一个。
 */
@Composable
fun CartoonStickerIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    padding: Dp = 14.dp,
    tilt: Float = -6f,
    shape: Shape = CircleShape,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    stickerTint: Color = Color.Unspecified,
) {
    if (!isCartoonStyle()) {
        Icon(imageVector, contentDescription, modifier, tint)
        return
    }
    Box(
        modifier = modifier
            .rotate(tilt)
            .cartoonStickerChip(shape, containerColor, padding),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector,
            contentDescription,
            Modifier.matchParentSize(),
            if (stickerTint != Color.Unspecified) stickerTint else tint,
        )
    }
}

/**
 * 与 Material3 [Icon] 一致的可访问性修饰符。
 *
 * 自绘图标绕过了 [Icon]，得自己补回 contentDescription 与 role，否则 TalkBack 读不到。
 */
@Composable
private fun cartoonIconSemantics(contentDescription: String?): Modifier =
    if (contentDescription == null) {
        Modifier
    } else {
        Modifier.semantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    }
