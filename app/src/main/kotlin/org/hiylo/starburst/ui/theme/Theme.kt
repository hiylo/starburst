/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : Theme.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.hiylo.starburst.ui.components.LocalAmoledTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6366F1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF8B5CF6),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF4C1D95),
    onSecondaryContainer = Color(0xFFF3E8FF),
    tertiary = Color(0xFF7DD0E1),
    onTertiary = Color(0xFF003640),
    surface = Color(0xFF121218),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF2B2B35),
    onSurfaceVariant = Color(0xFFC8C5D0),
    surfaceContainer = Color(0xFF1E1E25),
    surfaceContainerHigh = Color(0xFF262630),
    surfaceContainerHighest = Color(0xFF31313B),
    outline = Color(0xFF918F9A),
    outlineVariant = Color(0xFF47464F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6366F1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF8B5CF6),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = Color(0xFF3B0764),
    tertiary = Color(0xFF006879),
    onTertiary = Color(0xFFFFFFFF),
    surface = Color(0xFFFCF8FF),
    onSurface = Color(0xFF1C1B22),
    surfaceVariant = Color(0xFFE5E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    surfaceContainer = Color(0xFFF3EFF7),
    surfaceContainerHigh = Color(0xFFECE8F1),
    surfaceContainerHighest = Color(0xFFE6E2EB),
    outline = Color(0xFF787680),
    outlineVariant = Color(0xFFC9C5D0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

/**
 * AMOLED dark color scheme — pure black surfaces for OLED battery savings.
 * Uses true black (#000000) for the main surface and very dark tones for containers,
 * ensuring cards/sheets are still visually distinguishable from the background.
 */
/** Overrides the surface family with pure black tones for OLED battery savings. */
private fun ColorScheme.withAmoledSurfaces(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF1A1A22),
    surfaceContainer = Color(0xFF0D0D12),
    surfaceContainerLow = Color(0xFF080810),
    surfaceContainerLowest = Color.Black,
    surfaceContainerHigh = Color(0xFF141419),
    surfaceContainerHighest = Color(0xFF1C1C24)
)

/** 柔和（Dim）主题的中性灰表面系：介于浅色与深色之间，避免纯黑刺眼也避免纯白过亮。 */
private fun ColorScheme.withDimSurfaces(): ColorScheme = copy(
    background = Color(0xFF2A2A30),
    surface = Color(0xFF2A2A30),
    surfaceVariant = Color(0xFF3A3A43),
    surfaceContainer = Color(0xFF303038),
    surfaceContainerLow = Color(0xFF2C2C33),
    surfaceContainerLowest = Color(0xFF26262C),
    surfaceContainerHigh = Color(0xFF383841),
    surfaceContainerHighest = Color(0xFF42424B),
    onSurface = Color(0xFFE6E2EA),
    onSurfaceVariant = Color(0xFFC9C5CF),
    outline = Color(0xFF929099),
    outlineVariant = Color(0xFF4A4951)
)

/** Primary color roles for one accent choice, split by light/dark mode. */
internal data class AccentRoles(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
)

internal data class AccentPalette(
    val light: AccentRoles,
    val dark: AccentRoles,
)

/** Selectable accent colors. "indigo" is the brand default and matches the base schemes. */
internal val StarBurstAccents: Map<String, AccentPalette> = mapOf(
    "indigo" to AccentPalette(
        light = AccentRoles(Color(0xFF6366F1), Color(0xFFFFFFFF), Color(0xFFE0E7FF), Color(0xFF1E1B4B)),
        dark = AccentRoles(Color(0xFF6366F1), Color(0xFFFFFFFF), Color(0xFF312E81), Color(0xFFE0E7FF)),
    ),
    "violet" to AccentPalette(
        light = AccentRoles(Color(0xFF7C3AED), Color(0xFFFFFFFF), Color(0xFFEDE9FE), Color(0xFF4C1D95)),
        dark = AccentRoles(Color(0xFFC4B5FD), Color(0xFF2E1065), Color(0xFF6D28D9), Color(0xFFEDE9FE)),
    ),
    "cyan" to AccentPalette(
        light = AccentRoles(Color(0xFF0891B2), Color(0xFFFFFFFF), Color(0xFFCFFAFE), Color(0xFF164E63)),
        dark = AccentRoles(Color(0xFF67E8F9), Color(0xFF083344), Color(0xFF155E75), Color(0xFFCFFAFE)),
    ),
    "green" to AccentPalette(
        light = AccentRoles(Color(0xFF059669), Color(0xFFFFFFFF), Color(0xFFD1FAE5), Color(0xFF064E3B)),
        dark = AccentRoles(Color(0xFF6EE7B7), Color(0xFF064E3B), Color(0xFF065F46), Color(0xFFD1FAE5)),
    ),
    "amber" to AccentPalette(
        light = AccentRoles(Color(0xFFD97706), Color(0xFFFFFFFF), Color(0xFFFEF3C7), Color(0xFF78350F)),
        dark = AccentRoles(Color(0xFFFBBF24), Color(0xFF451A03), Color(0xFF92400E), Color(0xFFFEF3C7)),
    ),
    "red" to AccentPalette(
        light = AccentRoles(Color(0xFFDC2626), Color(0xFFFFFFFF), Color(0xFFFEE2E2), Color(0xFF7F1D1D)),
        dark = AccentRoles(Color(0xFFFCA5A5), Color(0xFF7F1D1D), Color(0xFFB91C1C), Color(0xFFFEE2E2)),
    ),
    // ---- 卡通亮色（Cartoon）----
    "pink" to AccentPalette(
        light = AccentRoles(Color(0xFFEC4899), Color(0xFFFFFFFF), Color(0xFFFCE7F3), Color(0xFF831843)),
        dark = AccentRoles(Color(0xFFF9A8D4), Color(0xFF831843), Color(0xFFBE185D), Color(0xFFFCE7F3)),
    ),
    "orange" to AccentPalette(
        light = AccentRoles(Color(0xFFF97316), Color(0xFFFFFFFF), Color(0xFFFFEDD5), Color(0xFF7C2D12)),
        dark = AccentRoles(Color(0xFFFDBA74), Color(0xFF7C2D12), Color(0xFFC2410C), Color(0xFFFFEDD5)),
    ),
    "lime" to AccentPalette(
        light = AccentRoles(Color(0xFF84CC16), Color(0xFF1A2E05), Color(0xFFECFCCB), Color(0xFF365314)),
        dark = AccentRoles(Color(0xFFBEF264), Color(0xFF1A2E05), Color(0xFF3F6212), Color(0xFFECFCCB)),
    ),
    "sky" to AccentPalette(
        light = AccentRoles(Color(0xFF0EA5E9), Color(0xFFFFFFFF), Color(0xFFE0F2FE), Color(0xFF0C4A6E)),
        dark = AccentRoles(Color(0xFF7DD3FC), Color(0xFF0C4A6E), Color(0xFF0284C7), Color(0xFFE0F2FE)),
    ),
    "mint" to AccentPalette(
        light = AccentRoles(Color(0xFF14B8A6), Color(0xFFFFFFFF), Color(0xFFCCFBF1), Color(0xFF134E4A)),
        dark = AccentRoles(Color(0xFF5EEAD4), Color(0xFF134E4A), Color(0xFF0F766E), Color(0xFFCCFBF1)),
    ),
)

private fun darkSchemeFor(accent: AccentPalette): ColorScheme {
    val roles = accent.dark
    return DarkColorScheme.copy(
        primary = roles.primary,
        onPrimary = roles.onPrimary,
        primaryContainer = roles.primaryContainer,
        onPrimaryContainer = roles.onPrimaryContainer,
    )
}

private fun lightSchemeFor(accent: AccentPalette): ColorScheme {
    val roles = accent.light
    return LightColorScheme.copy(
        primary = roles.primary,
        onPrimary = roles.onPrimary,
        primaryContainer = roles.primaryContainer,
        onPrimaryContainer = roles.onPrimaryContainer,
    )
}

/**
 * 完整主题方案：一套开箱即用的整机配色（含 surface/surfaceVariant/次级色等），
 * 与强调色不同——它整体改变 App 观感，而不仅是 primary 色系。
 * "default" 走强调色体系（见 [StarBurstAccents]），其余为卡通风格完整方案。
 */
internal data class ThemeScheme(
    val id: String,
    val light: ColorScheme,
    val dark: ColorScheme,
)

/** 主题方案注册表。id 与 DataStore 中 theme_scheme 的取值一一对应。 */
internal val StarBurstSchemes: Map<String, ThemeScheme> = mapOf(
    "candy" to ThemeScheme(
        id = "candy",
        light = lightColorScheme(
            primary = Color(0xFFFF5C8A),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFD9E4),
            onPrimaryContainer = Color(0xFF590024),
            secondary = Color(0xFF00A89A),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFA7F2E7),
            onSecondaryContainer = Color(0xFF00382D),
            tertiary = Color(0xFFFFB547),
            onTertiary = Color(0xFF3E2800),
            tertiaryContainer = Color(0xFFFFDE9E),
            onTertiaryContainer = Color(0xFF2C1B00),
            background = Color(0xFFFFF8F9),
            onBackground = Color(0xFF2D2225),
            surface = Color(0xFFFFF8F9),
            onSurface = Color(0xFF2D2225),
            surfaceVariant = Color(0xFFF4E8EB),
            onSurfaceVariant = Color(0xFF5A454B),
            surfaceContainer = Color(0xFFFFF0F3),
            surfaceContainerLow = Color(0xFFFFF3F5),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerHigh = Color(0xFFF9E8ED),
            surfaceContainerHighest = Color(0xFFF3E2E7),
            outline = Color(0xFF8F747C),
            outlineVariant = Color(0xFFD6C0C7),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
        ),
        dark = darkColorScheme(
            primary = Color(0xFFFF9DC4),
            onPrimary = Color(0xFF590024),
            primaryContainer = Color(0xFF8A1B4B),
            onPrimaryContainer = Color(0xFFFFD9E4),
            secondary = Color(0xFF6ED9CB),
            onSecondary = Color(0xFF00382D),
            secondaryContainer = Color(0xFF005049),
            onSecondaryContainer = Color(0xFFA7F2E7),
            tertiary = Color(0xFFF0BD6D),
            onTertiary = Color(0xFF3E2800),
            tertiaryContainer = Color(0xFF5A3C00),
            onTertiaryContainer = Color(0xFFFFDE9E),
            background = Color(0xFF1E1418),
            onBackground = Color(0xFFEBDFE3),
            surface = Color(0xFF1E1418),
            onSurface = Color(0xFFEBDFE3),
            surfaceVariant = Color(0xFF4D3940),
            onSurfaceVariant = Color(0xFFD1BAC1),
            surfaceContainer = Color(0xFF2A1E23),
            surfaceContainerLow = Color(0xFF161014),
            surfaceContainerLowest = Color(0xFF120B0F),
            surfaceContainerHigh = Color(0xFF35282D),
            surfaceContainerHighest = Color(0xFF403338),
            outline = Color(0xFF9B858C),
            outlineVariant = Color(0xFF4D3940),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
        ),
    ),
    "ocean" to ThemeScheme(
        id = "ocean",
        light = lightColorScheme(
            primary = Color(0xFF00AEEF),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFBDF0FF),
            onPrimaryContainer = Color(0xFF00313F),
            secondary = Color(0xFF00B5A4),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFF98EDE1),
            onSecondaryContainer = Color(0xFF003A34),
            tertiary = Color(0xFF7E6BFF),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFE4DEFF),
            onTertiaryContainer = Color(0xFF1A0065),
            background = Color(0xFFF3FAFE),
            onBackground = Color(0xFF1B2026),
            surface = Color(0xFFF3FAFE),
            onSurface = Color(0xFF1B2026),
            surfaceVariant = Color(0xFFDFE9EE),
            onSurfaceVariant = Color(0xFF45535B),
            surfaceContainer = Color(0xFFE8F3F8),
            surfaceContainerLow = Color(0xFFECF6FB),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerHigh = Color(0xFFE2EEF3),
            surfaceContainerHighest = Color(0xFFDCE8ED),
            outline = Color(0xFF7C8A93),
            outlineVariant = Color(0xFFBFCBD2),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
        ),
        dark = darkColorScheme(
            primary = Color(0xFF5ED4FF),
            onPrimary = Color(0xFF003C4E),
            primaryContainer = Color(0xFF00586F),
            onPrimaryContainer = Color(0xFFBDF0FF),
            secondary = Color(0xFF69DFCE),
            onSecondary = Color(0xFF003C35),
            secondaryContainer = Color(0xFF00544A),
            onSecondaryContainer = Color(0xFF98EDE1),
            tertiary = Color(0xFFC2B5FF),
            onTertiary = Color(0xFF2B00A0),
            tertiaryContainer = Color(0xFF4D32CF),
            onTertiaryContainer = Color(0xFFE4DEFF),
            background = Color(0xFF111418),
            onBackground = Color(0xFFE1E6EB),
            surface = Color(0xFF111418),
            onSurface = Color(0xFFE1E6EB),
            surfaceVariant = Color(0xFF3A454B),
            onSurfaceVariant = Color(0xFFBCC9D1),
            surfaceContainer = Color(0xFF1B2026),
            surfaceContainerLow = Color(0xFF0B0E12),
            surfaceContainerLowest = Color(0xFF06090D),
            surfaceContainerHigh = Color(0xFF252A30),
            surfaceContainerHighest = Color(0xFF30353B),
            outline = Color(0xFF85939C),
            outlineVariant = Color(0xFF3A454B),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
        ),
    ),
    "sunset" to ThemeScheme(
        id = "sunset",
        light = lightColorScheme(
            primary = Color(0xFFFF6D4A),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDBCE),
            onPrimaryContainer = Color(0xFF4B1900),
            secondary = Color(0xFFE94D8E),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFFD8E3),
            onSecondaryContainer = Color(0xFF58002E),
            tertiary = Color(0xFFB050FF),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFF1DCFF),
            onTertiaryContainer = Color(0xFF36005E),
            background = Color(0xFFFFF8F5),
            onBackground = Color(0xFF241A12),
            surface = Color(0xFFFFF8F5),
            onSurface = Color(0xFF241A12),
            surfaceVariant = Color(0xFFF7E7DE),
            onSurfaceVariant = Color(0xFF58463C),
            surfaceContainer = Color(0xFFFFF0E7),
            surfaceContainerLow = Color(0xFFFFF4EC),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerHigh = Color(0xFFFAE5D9),
            surfaceContainerHighest = Color(0xFFF4DFD3),
            outline = Color(0xFF8F7A6F),
            outlineVariant = Color(0xFFD7C2B5),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
        ),
        dark = darkColorScheme(
            primary = Color(0xFFFFB59F),
            onPrimary = Color(0xFF561A00),
            primaryContainer = Color(0xFF7F2F12),
            onPrimaryContainer = Color(0xFFFFDBCE),
            secondary = Color(0xFFFFB1CC),
            onSecondary = Color(0xFF670036),
            secondaryContainer = Color(0xFF8F1B50),
            onSecondaryContainer = Color(0xFFFFD8E3),
            tertiary = Color(0xFFD9BBFF),
            onTertiary = Color(0xFF46006B),
            tertiaryContainer = Color(0xFF6A2AA0),
            onTertiaryContainer = Color(0xFFF1DCFF),
            background = Color(0xFF1F1712),
            onBackground = Color(0xFFF3E3D8),
            surface = Color(0xFF1F1712),
            onSurface = Color(0xFFF3E3D8),
            surfaceVariant = Color(0xFF53433A),
            onSurfaceVariant = Color(0xFFD8C2B5),
            surfaceContainer = Color(0xFF2B2119),
            surfaceContainerLow = Color(0xFF17110D),
            surfaceContainerLowest = Color(0xFF120D09),
            surfaceContainerHigh = Color(0xFF362B23),
            surfaceContainerHighest = Color(0xFF41352D),
            outline = Color(0xFFA99588),
            outlineVariant = Color(0xFF53433A),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
        ),
    ),
    "flame" to ThemeScheme(
        id = "flame",
        light = lightColorScheme(
            primary = Color(0xFFE53935),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDAD6),
            onPrimaryContainer = Color(0xFF410001),
            secondary = Color(0xFFF4511E),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFFDBCE),
            onSecondaryContainer = Color(0xFF5D1400),
            tertiary = Color(0xFFB71C1C),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFDAD8),
            onTertiaryContainer = Color(0xFF410505),
            background = Color(0xFFFFF8F7),
            onBackground = Color(0xFF241918),
            surface = Color(0xFFFFF8F7),
            onSurface = Color(0xFF241918),
            surfaceVariant = Color(0xFFF5DDDA),
            onSurfaceVariant = Color(0xFF534340),
            surfaceContainer = Color(0xFFFFECE9),
            surfaceContainerLow = Color(0xFFFFF2F0),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerHigh = Color(0xFFFDE4E0),
            surfaceContainerHighest = Color(0xFFF6DCD8),
            outline = Color(0xFF8C7571),
            outlineVariant = Color(0xFFD6BEB9),
            error = Color(0xFFC62828),
            onError = Color(0xFFFFFFFF),
        ),
        dark = darkColorScheme(
            primary = Color(0xFFFFB4AB),
            onPrimary = Color(0xFF690005),
            primaryContainer = Color(0xFF93000A),
            onPrimaryContainer = Color(0xFFFFDAD6),
            secondary = Color(0xFFFFB59E),
            onSecondary = Color(0xFF5D1400),
            secondaryContainer = Color(0xFF8A2A00),
            onSecondaryContainer = Color(0xFFFFDBCE),
            tertiary = Color(0xFFFFB4B3),
            onTertiary = Color(0xFF690005),
            tertiaryContainer = Color(0xFF930011),
            onTertiaryContainer = Color(0xFFFFDAD8),
            background = Color(0xFF1B1211),
            onBackground = Color(0xFFF6E1DE),
            surface = Color(0xFF1B1211),
            onSurface = Color(0xFFF6E1DE),
            surfaceVariant = Color(0xFF534340),
            onSurfaceVariant = Color(0xFFD8BDB9),
            surfaceContainer = Color(0xFF271C1B),
            surfaceContainerLow = Color(0xFF130B0B),
            surfaceContainerLowest = Color(0xFF0F0909),
            surfaceContainerHigh = Color(0xFF322624),
            surfaceContainerHighest = Color(0xFF3D302E),
            outline = Color(0xFFA1847F),
            outlineVariant = Color(0xFF534340),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
        ),
    ),
    "bubble" to ThemeScheme(
        id = "bubble",
        light = lightColorScheme(
            primary = Color(0xFFFF2D8E),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFD6E8),
            onPrimaryContainer = Color(0xFF3F001F),
            secondary = Color(0xFF00B8E8),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFC9F2FF),
            onSecondaryContainer = Color(0xFF003543),
            tertiary = Color(0xFFFFC300),
            onTertiary = Color(0xFF3E2A00),
            tertiaryContainer = Color(0xFFFFF0C2),
            onTertiaryContainer = Color(0xFF2B1D00),
            background = Color(0xFFFFFBFE),
            onBackground = Color(0xFF23191E),
            surface = Color(0xFFFFFBFE),
            onSurface = Color(0xFF23191E),
            surfaceVariant = Color(0xFFF4E6EE),
            onSurfaceVariant = Color(0xFF524249),
            surfaceContainer = Color(0xFFFFF0F6),
            surfaceContainerLow = Color(0xFFFFF5F9),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerHigh = Color(0xFFF9E7F0),
            surfaceContainerHighest = Color(0xFFF3E1EB),
            outline = Color(0xFF857177),
            outlineVariant = Color(0xFFD6C2CB),
            error = Color(0xFFBA1A1A),
            onError = Color(0xFFFFFFFF),
        ),
        dark = darkColorScheme(
            primary = Color(0xFFFF9DC2),
            onPrimary = Color(0xFF5A0030),
            primaryContainer = Color(0xFF86125E),
            onPrimaryContainer = Color(0xFFFFD6E8),
            secondary = Color(0xFF66E0F5),
            onSecondary = Color(0xFF003A43),
            secondaryContainer = Color(0xFF005364),
            onSecondaryContainer = Color(0xFFC9F2FF),
            tertiary = Color(0xFFF0C14A),
            onTertiary = Color(0xFF3E2A00),
            tertiaryContainer = Color(0xFF5C4100),
            onTertiaryContainer = Color(0xFFFFF0C2),
            background = Color(0xFF1F1217),
            onBackground = Color(0xFFECDFE3),
            surface = Color(0xFF1F1217),
            onSurface = Color(0xFFECDFE3),
            surfaceVariant = Color(0xFF514047),
            onSurfaceVariant = Color(0xFFD6BEC6),
            surfaceContainer = Color(0xFF2B1D22),
            surfaceContainerLow = Color(0xFF170C10),
            surfaceContainerLowest = Color(0xFF12070B),
            surfaceContainerHigh = Color(0xFF36272C),
            surfaceContainerHighest = Color(0xFF413237),
            outline = Color(0xFFA08990),
            outlineVariant = Color(0xFF514047),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
        ),
    ),
)

/**
 * StarBurst Material 3 Theme
 * 
 * Supports:
 * - Light/Dark theme based on system settings
 * - Dynamic color on Android 12+ (Material You)
 * - AMOLED dark mode with pure black surfaces
 * - Selectable accent colors (ignored when dynamic color is enabled)
 * - Cartoon style: larger radii, thick ink outlines, solid shadows
 * - Edge-to-edge display
 */
@Composable
fun StarBurstTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    amoledDark: Boolean = false,
    dimTheme: Boolean = false,
    accentColor: String = "indigo",
    themeScheme: String = "default",
    cartoonStyle: Boolean = false,
    content: @Composable () -> Unit
) {
    val accent = StarBurstAccents[accentColor] ?: StarBurstAccents.getValue("indigo")
    val scheme = StarBurstSchemes[themeScheme]
    val colorScheme = when {
        dimTheme -> {
            // 柔和主题：以对应方案/强调色的深色版为基底，应用中性灰表面系。
            val base = if (scheme != null) scheme.dark else darkSchemeFor(accent)
            base.withDimSurfaces()
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val base = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (darkTheme && amoledDark) base.withAmoledSurfaces() else base
        }
        scheme != null -> {
            // 完整主题方案：整套配色，忽略强调色（强调色仅用于 default 方案）。
            val base = if (darkTheme) scheme.dark else scheme.light
            if (darkTheme && amoledDark) base.withAmoledSurfaces() else base
        }
        darkTheme && amoledDark -> darkSchemeFor(accent).withAmoledSurfaces()
        darkTheme -> darkSchemeFor(accent)
        else -> lightSchemeFor(accent)
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Use surface color for status bar (less jarring than primary)
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // 自适应 Shape 在绘制期读取该开关，故这里同步写入全局状态。
    SideEffect {
        CartoonStyleState.enabled = cartoonStyle
    }

    CompositionLocalProvider(
        LocalAmoledTheme provides (darkTheme && amoledDark),
        LocalCartoonStyle provides cartoonStyle,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
