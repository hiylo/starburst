/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SettingsDisplayNames.kt
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.AppCardShape
import org.hiylo.starburst.ui.components.cartoonChrome
import org.hiylo.starburst.ui.components.isAmoledTheme
import org.hiylo.starburst.ui.theme.StarBurstAccents
import org.hiylo.starburst.ui.theme.StarBurstSchemes
import java.util.Locale

/**
 * Card/section primitives and the display-name + value-formatting helpers the
 * settings screens share. Extracted verbatim from SettingsScreen.kt.
 */

@Composable
internal fun SectionHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
        )
    }
}

@Composable
internal fun SettingsCardSpacer() {
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
internal fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    val isAmoled = isAmoledTheme()
    Surface(
        shape = AppCardShape,
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
        border = if (isAmoled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .cartoonChrome(AppCardShape),
    ) {
        Column(content = content)
    }
}

/** 主题方案显示名对应的字符串资源 id。 */
internal fun themeSchemeNameRes(scheme: String): Int = when (scheme) {
    "candy" -> R.string.settings_theme_scheme_candy
    "ocean" -> R.string.settings_theme_scheme_ocean
    "sunset" -> R.string.settings_theme_scheme_sunset
    "bubble" -> R.string.settings_theme_scheme_bubble
    else -> R.string.settings_theme_scheme_default
}

/** 主题方案在设置行/对话框里的预览主色（取该方案 light 模式的 primary）。 */
internal fun themeSchemePreviewColor(scheme: String): Color {
    return StarBurstSchemes[scheme]?.light?.primary
        ?: Color(0xFF6366F1) // default 方案主色（indigo）
}

internal fun accentSwatchColor(accent: String): Color {
    return StarBurstAccents[accent]?.light?.primary
        ?: StarBurstAccents.getValue("indigo").light.primary
}

internal fun accentOnSwatchColor(accent: String): Color {
    return StarBurstAccents[accent]?.light?.onPrimary ?: Color.White
}

@Composable
internal fun getThemeDisplayName(theme: String): String {
    return when (theme) {
        "system" -> stringResource(R.string.settings_theme_system)
        "light" -> stringResource(R.string.settings_theme_light)
        "dim" -> stringResource(R.string.settings_theme_dim)
        "dark" -> stringResource(R.string.settings_theme_dark)
        "amoled" -> stringResource(R.string.settings_theme_amoled)
        else -> theme
    }
}

@Composable
internal fun getFontSizeDisplayName(size: String): String {
    return when (size) {
        "small" -> stringResource(R.string.settings_font_size_small)
        "medium" -> stringResource(R.string.settings_font_size_medium)
        "large" -> stringResource(R.string.settings_font_size_large)
        else -> size
    }
}

@Composable
internal fun getLineHeightDisplayName(multiplier: Float): String {
    return stringResource(R.string.settings_line_height_value, formatMultiplier(multiplier))
}

internal fun formatMultiplier(multiplier: Float): String {
    return String.format(Locale.US, "%.1f", multiplier)
}

@Composable
internal fun getLanguageDisplayName(code: String): String {
    val systemDefault = stringResource(R.string.settings_language_system)
    
    if (code.isEmpty()) return systemDefault
    
    // Parse the language tag and get native display name
    val locale = if (code.contains("-")) {
        val parts = code.split("-")
        if (parts.size >= 2) {
            Locale(parts[0], parts[1].uppercase())
        } else {
            Locale(parts[0])
        }
    } else {
        Locale(code)
    }
    
    return locale.getDisplayName(locale).replaceFirstChar { 
        if (it.isLowerCase()) it.titlecase(locale) else it.toString() 
    }
}

@Composable
internal fun getReconnectModeDisplayName(mode: String): String {
    return when (mode) {
        "aggressive" -> stringResource(R.string.settings_reconnect_aggressive)
        "normal" -> stringResource(R.string.settings_reconnect_normal)
        "conservative" -> stringResource(R.string.settings_reconnect_conservative)
        else -> mode
    }
}

@Composable
internal fun getImageMaxSideDisplayName(px: Int): String {
    if (px <= 0) {
        return stringResource(R.string.settings_compress_images_max_side_keep_original)
    }
    return stringResource(R.string.settings_compress_images_max_side_value, px)
}

internal fun parseHhMm(value: String): Pair<Int, Int> {
    val parts = value.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    return hour to minute
}
