/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SettingsDialogs.kt
 * Date : 2026/09/18 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.ui.screens.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.hiylo.starburst.R
import org.hiylo.starburst.domain.model.SessionCategory
import org.hiylo.starburst.ui.components.AppDialog
import org.hiylo.starburst.ui.components.AppHaptics
import org.hiylo.starburst.ui.components.AppHapticConfig
import org.hiylo.starburst.ui.components.AppDialogActions
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.AppSecondaryButton
import org.hiylo.starburst.ui.components.isAmoledTheme
import org.hiylo.starburst.ui.components.AppPickerItemShape
import org.hiylo.starburst.ui.components.appSelectedItemColor
import org.hiylo.starburst.ui.components.SessionCategoryColorKeys
import org.hiylo.starburst.ui.components.SessionCategoryIconKeys
import org.hiylo.starburst.ui.components.sessionCategoryColor
import org.hiylo.starburst.ui.components.sessionCategoryIcon
import org.hiylo.starburst.ui.theme.StarBurstAccents
import org.hiylo.starburst.ui.theme.StarBurstSchemes
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun SessionCategoriesDialog(
    categories: List<SessionCategory>,
    onSave: (String?, String, String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(SessionCategoryColorKeys.first()) }
    var icon by remember { mutableStateOf(SessionCategoryIconKeys.first()) }

    fun edit(category: SessionCategory?) {
        editingId = category?.id
        name = category?.name.orEmpty()
        color = category?.color ?: SessionCategoryColorKeys.first()
        icon = category?.icon ?: SessionCategoryIconKeys.first()
        editing = true
    }

    AppDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(
                    if (editing) R.string.settings_category_edit else R.string.settings_session_categories
                ),
                style = MaterialTheme.typography.titleMedium,
            )

            if (editing) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_category_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(stringResource(R.string.settings_category_color), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SessionCategoryColorKeys.forEach { key ->
                        val selected = key == color
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(sessionCategoryColor(key))
                                .then(
                                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { color = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                Text(stringResource(R.string.settings_category_icon), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SessionCategoryIconKeys.forEach { key ->
                        val selected = key == icon
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                selected && !isAmoledTheme() -> sessionCategoryColor(color).copy(alpha = 0.18f)
                                else -> Color.Transparent
                            },
                            border = if (selected) BorderStroke(1.dp, sessionCategoryColor(color)) else null,
                            modifier = Modifier.clickable { icon = key },
                        ) {
                            Icon(
                                imageVector = sessionCategoryIcon(key),
                                contentDescription = null,
                                tint = if (selected) sessionCategoryColor(color) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp).size(22.dp),
                            )
                        }
                    }
                }

                AppDialogActions(
                    dismissText = stringResource(R.string.cancel),
                    confirmText = stringResource(R.string.settings_category_save),
                    onDismiss = { editing = false },
                    onConfirm = {
                        onSave(editingId, name, color, icon)
                        editing = false
                    },
                    confirmEnabled = name.isNotBlank(),
                )
            } else {
                if (categories.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_categories_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        categories.forEach { category ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { edit(category) }
                                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = sessionCategoryIcon(category.icon),
                                    contentDescription = null,
                                    tint = sessionCategoryColor(category.color),
                                )
                                Text(
                                    text = category.name,
                                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                IconButton(onClick = { onDelete(category.id) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.settings_category_delete),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }

                AppDialogActions(
                    dismissText = stringResource(R.string.close),
                    confirmText = stringResource(R.string.settings_category_add),
                    onDismiss = onDismiss,
                    onConfirm = { edit(null) },
                )
            }
        }
    }
}

/**
 * Reusable single-selection picker dialog styled to match
 * the ModelPickerDialog visual language: selected item gets a
 * rounded background highlight and a check icon.
 *
 * @param title       Dialog title string.
 * @param options     List of key-label pairs to display.
 * @param selectedKey The currently selected key.
 * @param onSelect    Called with the key when an option is tapped.
 * @param onDismiss   Called when the dialog should close.
 * @param maxHeight   Maximum dialog body height (useful for long lists).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <K> SettingsPickerDialog(
    title: String,
    options: List<Pair<K, String>>,
    selectedKey: K,
    onSelect: (K) -> Unit,
    onDismiss: () -> Unit,
    maxHeight: Int = 480
) {
    val isAmoled = isAmoledTheme()

    val listState = rememberLazyListState()

    // Scroll to selected item on first composition
    val selectedIndex = remember(options, selectedKey) {
        options.indexOfFirst { it.first == selectedKey }.coerceAtLeast(0)
    }
    LaunchedEffect(selectedIndex) {
        listState.scrollToItem(selectedIndex)
    }

    AppDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight.dp),
    ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Title
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(
                        start = 24.dp,
                        end = 24.dp,
                        top = 20.dp,
                        bottom = 8.dp
                    )
                )

                // Items
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(
                        options,
                        key = { it.first.toString() }
                    ) { (key, label) ->
                        val isSelected = key == selectedKey
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(AppPickerItemShape)
                                .background(
                                    when {
                                        isSelected -> appSelectedItemColor()
                                        else -> Color.Transparent
                                    }
                                )
                                .then(
                                    if (isSelected && isAmoled) {
                                        Modifier.border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                                            shape = AppPickerItemShape,
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { onSelect(key) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Cancel button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    AppSecondaryButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
    }
}

@Composable
internal fun ThemePickerDialog(
    currentTheme: String,
    onThemeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    SettingsPickerDialog(
        title = stringResource(R.string.dialog_select_theme),
        options = listOf(
            "system" to stringResource(R.string.settings_theme_system),
            "light" to stringResource(R.string.settings_theme_light),
            "dim" to stringResource(R.string.settings_theme_dim),
            "dark" to stringResource(R.string.settings_theme_dark),
            "amoled" to stringResource(R.string.settings_theme_amoled)
        ),
        selectedKey = currentTheme,
        onSelect = onThemeSelected,
        onDismiss = onDismiss
    )
}

/** 主题方案选择对话框：展示各方案的表面色 + 强调色 + 次级色三色条预览。 */
@Composable
internal fun ThemeSchemeDialog(
    currentScheme: String,
    onSchemeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val schemeIds = listOf("default", "candy", "ocean", "sunset", "bubble")
    AppDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_theme_scheme),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.settings_theme_scheme_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                schemeIds.forEach { id ->
                    val selected = id == currentScheme
                    val primary = themeSchemePreviewColor(id)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSchemeSelected(id) },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // 迷你 UI 预览：surface 底 + primary 标题条 + secondary 副标题 + 文本占位。
                            Column(
                                modifier = Modifier
                                    .width(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        StarBurstSchemes[id]?.light?.surface ?: Color(0xFFFCF8FF)
                                    )
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(18.dp)
                                        .background(primary),
                                )
                                Column(Modifier.padding(6.dp)) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(0.7f)
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(
                                                StarBurstSchemes[id]?.light?.onSurface
                                                    ?: Color(0xFF1C1B1F),
                                            ),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        Modifier
                                            .fillMaxWidth(0.5f)
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(
                                                StarBurstSchemes[id]?.light?.secondary
                                                    ?: Color(0xFF8B5CF6),
                                            ),
                                    )
                                }
                            }
                            Text(
                                text = stringResource(themeSchemeNameRes(id)),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccentColorDialog(
    currentAccent: String,
    onAccentSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_accent_color),
                style = MaterialTheme.typography.titleMedium
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StarBurstAccents.keys.forEach { accent ->
                    val selected = accent == currentAccent
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(accentSwatchColor(accent))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            )
                            .clickable { onAccentSelected(accent) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = accentOnSwatchColor(accent),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LanguagePickerDialog(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val systemDefault = stringResource(R.string.settings_language_system)

    SettingsPickerDialog(
        title = stringResource(R.string.dialog_select_language),
        options = listOf(
            "" to systemDefault,
            "en" to "English",
            "ar" to "العربية",
            "de" to "Deutsch",
            "es" to "Español",
            "fr" to "Français",
            "id" to "Bahasa Indonesia",
            "it" to "Italiano",
            "ja" to "日本語",
            "ko" to "한국어",
            "pl" to "Polski",
            "pt-BR" to "Português (Brasil)",
            "ru" to "Русский",
            "tr" to "Türkçe",
            "uk" to "Українська",
            "zh-CN" to "简体中文"
        ),
        selectedKey = currentLanguage,
        onSelect = onLanguageSelected,
        onDismiss = onDismiss,
        maxHeight = 520
    )
}

@Composable
internal fun FontSizePickerDialog(
    currentSize: String,
    onSizeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    SettingsPickerDialog(
        title = stringResource(R.string.settings_font_size),
        options = listOf(
            "small" to stringResource(R.string.settings_font_size_small),
            "medium" to stringResource(R.string.settings_font_size_medium),
            "large" to stringResource(R.string.settings_font_size_large)
        ),
        selectedKey = currentSize,
        onSelect = onSizeSelected,
        onDismiss = onDismiss
    )
}

@Composable
internal fun LineHeightDialog(
    currentMultiplier: Float,
    onMultiplierSelected: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var multiplier by remember(currentMultiplier) {
        mutableFloatStateOf(currentMultiplier.coerceIn(1f, 2f))
    }

    AppDialog(onDismissRequest = onDismiss, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_line_height), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_line_height_value, formatMultiplier(multiplier)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = multiplier,
                onValueChange = { multiplier = it },
                valueRange = 1f..2f,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AppSecondaryButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(modifier = Modifier.width(8.dp))
                AppPrimaryButton(onClick = { onMultiplierSelected(multiplier) }) {
                    Text(stringResource(R.string.server_save))
                }
            }
        }
    }
}

@Composable
internal fun MessageCountPickerDialog(
    currentCount: Int,
    onCountSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    SettingsPickerDialog(
        title = stringResource(R.string.settings_initial_messages),
        options = listOf(25, 50, 100, 200).map { it to "$it" },
        selectedKey = currentCount,
        onSelect = onCountSelected,
        onDismiss = onDismiss
    )
}

@Composable
internal fun MessageHistoryResponseLimitPickerDialog(
    currentLimitMb: Int,
    onLimitSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsPickerDialog(
        title = stringResource(R.string.settings_history_response_limit),
        options = listOf(8, 16, 24, 32, 48, 64, 96, 128).map {
            it to stringResource(R.string.settings_history_response_limit_value, it)
        },
        selectedKey = currentLimitMb,
        onSelect = onLimitSelected,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun RecentDirectoryCountPickerDialog(
    currentCount: Int,
    onCountSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsPickerDialog(
        title = stringResource(R.string.settings_recent_directories),
        options = listOf(5, 10, 15, 20, 30, 50).map { it to "$it" },
        selectedKey = currentCount,
        onSelect = onCountSelected,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun ReconnectModePickerDialog(
    currentMode: String,
    onModeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    SettingsPickerDialog(
        title = stringResource(R.string.dialog_select_reconnect_mode),
        options = listOf(
            "aggressive" to stringResource(R.string.settings_reconnect_aggressive),
            "normal" to stringResource(R.string.settings_reconnect_normal),
            "conservative" to stringResource(R.string.settings_reconnect_conservative)
        ),
        selectedKey = currentMode,
        onSelect = onModeSelected,
        onDismiss = onDismiss
    )
}

@Composable
internal fun HapticPatternDialog(
    currentDurationMillis: Int,
    currentAmplitude: Int,
    onSave: (durationMillis: Int, amplitude: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var durationMillis by remember(currentDurationMillis) {
        mutableFloatStateOf(currentDurationMillis.coerceIn(5, 100).toFloat())
    }
    var amplitude by remember(currentAmplitude) {
        mutableFloatStateOf(currentAmplitude.coerceIn(1, 255).toFloat())
    }
    val view = LocalView.current

    AppDialog(onDismissRequest = onDismiss, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_haptic_pattern), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_haptic_duration_value, durationMillis.roundToInt()),
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = durationMillis,
                onValueChange = { durationMillis = it },
                valueRange = 5f..100f,
            )
            Text(
                stringResource(R.string.settings_haptic_amplitude_value, amplitude.roundToInt()),
                style = MaterialTheme.typography.bodyLarge,
            )
            Slider(
                value = amplitude,
                onValueChange = { amplitude = it },
                valueRange = 1f..255f,
            )
            AppSecondaryButton(
                onClick = {
                    AppHaptics.perform(
                        view,
                        AppHapticConfig(
                            enabled = true,
                            durationMillis = durationMillis.roundToInt(),
                            amplitude = amplitude.roundToInt(),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                outlined = true,
            ) {
                Text(stringResource(R.string.settings_haptic_test))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AppSecondaryButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                AppPrimaryButton(
                    onClick = { onSave(durationMillis.roundToInt(), amplitude.roundToInt()) },
                ) {
                    Text(stringResource(R.string.server_save))
                }
            }
        }
    }
}

@Composable
internal fun TerminalFontSizeDialog(
    currentSize: Float,
    onSizeSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember(currentSize) { mutableFloatStateOf(currentSize.coerceIn(6f, 20f)) }

    AppDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_terminal_font_size),
                style = MaterialTheme.typography.titleMedium,
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.settings_terminal_font_size_value, selected.roundToInt()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Slider(
                    value = selected,
                    onValueChange = { selected = it },
                    valueRange = 6f..20f,
                    steps = 13
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                AppSecondaryButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                AppPrimaryButton(onClick = { onSizeSelected(selected.roundToInt().toFloat()) }) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    }
}

@Composable
internal fun ImageCompressionMaxSideDialog(
    currentMaxSide: Int,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(0, 720, 960, 1080, 1440, 1920, 2560)
    SettingsPickerDialog(
        title = stringResource(R.string.settings_compress_images_max_side),
        options = options.map { it to getImageMaxSideDisplayName(it) },
        selectedKey = currentMaxSide,
        onSelect = onSelected,
        onDismiss = onDismiss
    )
}

@Composable
internal fun ImageCompressionQualityDialog(
    currentQuality: Int,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(40, 50, 60, 70, 80)
    SettingsPickerDialog(
        title = stringResource(R.string.settings_compress_images_quality),
        options = options.map {
            it to stringResource(R.string.settings_compress_images_quality_value, it)
        },
        selectedKey = currentQuality,
        onSelect = onSelected,
        onDismiss = onDismiss
    )
}

/**
 * Time-picker dialog for the do-not-disturb window start/end.
 * "HH:mm" strings are parsed to 24h hour/minute; invalid input falls back to midnight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DndTimeDialog(
    title: String,
    initial: String,
    show: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return
    val (initialHour, initialMinute) = parseHhMm(initial)
    val timeState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TimePicker(
                state = timeState,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val formatted = String.format(
                        Locale.ROOT,
                        "%02d:%02d",
                        timeState.hour,
                        timeState.minute,
                    )
                    onConfirm(formatted)
                },
            ) {
                Text(stringResource(R.string.settings_dnd_schedule_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_dnd_schedule_cancel))
            }
        },
    )
}
