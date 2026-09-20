/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SettingsScreen.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.AppHaptics
import org.hiylo.starburst.ui.components.CartoonInkIcon
import org.hiylo.starburst.ui.components.AppHapticConfig
import org.hiylo.starburst.ui.components.isAmoledTheme
import kotlin.math.roundToInt

/**
 * Settings Screen - global app preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDiagnostics: () -> Unit = {},
    onNavigateToSync: () -> Unit = {},
    onNavigateToLlmProvider: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val currentLanguage by viewModel.appLanguage.collectAsState()
    val currentTheme by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val chatFontSize by viewModel.chatFontSize.collectAsState()
    val chatLineHeight by viewModel.chatLineHeight.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val groupNotifications by viewModel.groupNotifications.collectAsState()
    val dndEnabled by viewModel.dndEnabled.collectAsState()
    val dndStart by viewModel.dndStart.collectAsState()
    val dndEnd by viewModel.dndEnd.collectAsState()

    val initialMessageCount by viewModel.initialMessageCount.collectAsState()
    val messageHistoryResponseLimitMb by viewModel.messageHistoryResponseLimitMb.collectAsState()
    val recentDirectoryCount by viewModel.recentDirectoryCount.collectAsState()
    val codeWordWrap by viewModel.codeWordWrap.collectAsState()
    val confirmBeforeSend by viewModel.confirmBeforeSend.collectAsState()
    val amoledDark by viewModel.amoledDark.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val themeScheme by viewModel.themeScheme.collectAsState()
    val cartoonStyle by viewModel.cartoonStyle.collectAsState()
    val compactMessages by viewModel.compactMessages.collectAsState()
    val collapseTools by viewModel.collapseTools.collectAsState()
    val expandReasoning by viewModel.expandReasoning.collectAsState()
    val showTurnDividers by viewModel.showTurnDividers.collectAsState()
    val hapticFeedback by viewModel.hapticFeedback.collectAsState()
    val hapticDurationMillis by viewModel.hapticDurationMillis.collectAsState()
    val hapticAmplitude by viewModel.hapticAmplitude.collectAsState()
    val reconnectMode by viewModel.reconnectMode.collectAsState()
    val backgroundWakeLock by viewModel.backgroundWakeLock.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val silentNotifications by viewModel.silentNotifications.collectAsState()
    val compressImageAttachments by viewModel.compressImageAttachments.collectAsState()
    val imageAttachmentMaxLongSide by viewModel.imageAttachmentMaxLongSide.collectAsState()
    val imageAttachmentWebpQuality by viewModel.imageAttachmentWebpQuality.collectAsState()
    val terminalFontSize by viewModel.terminalFontSize.collectAsState()
    val showTerminalPanelHint by viewModel.showTerminalPanelHint.collectAsState()
    val modelDownloading by viewModel.modelDownloading.collectAsState()
    val modelExtracting by viewModel.modelExtracting.collectAsState()
    val modelDownloadProgress by viewModel.modelDownloadProgress.collectAsState()
    val modelReady by viewModel.modelReady.collectAsState()
    val modelDownloadFailed by viewModel.modelDownloadFailed.collectAsState()
    val asrModelDownloading by viewModel.asrModelDownloading.collectAsState()
    val asrModelDownloadProgress by viewModel.asrModelDownloadProgress.collectAsState()
    val asrModelReady by viewModel.asrModelReady.collectAsState()
    val asrModelDownloadFailed by viewModel.asrModelDownloadFailed.collectAsState()
    val asrSupported = viewModel.asrSupported

    val backupViewModel: BackupViewModel = hiltViewModel()

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showDndStartDialog by remember { mutableStateOf(false) }
    var showDndEndDialog by remember { mutableStateOf(false) }
    var showSchemeDialog by remember { mutableStateOf(false) }
    var showAccentDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showLineHeightDialog by remember { mutableStateOf(false) }
    var showMessageCountDialog by remember { mutableStateOf(false) }
    var showMessageHistoryResponseLimitDialog by remember { mutableStateOf(false) }
    var showRecentDirectoryCountDialog by remember { mutableStateOf(false) }
    var showReconnectModeDialog by remember { mutableStateOf(false) }
    var showHapticPatternDialog by remember { mutableStateOf(false) }
    var showTerminalFontSizeDialog by remember { mutableStateOf(false) }
    var showImageMaxSideDialog by remember { mutableStateOf(false) }
    var showImageQualityDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }

    val isAmoled = isAmoledTheme()
    val settingsView = LocalView.current
    val switchColors = if (isAmoled) {
        SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.primary,
            checkedTrackColor = Color.Black,
            checkedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = Color.Black,
            uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
        )
    } else {
        SwitchDefaults.colors()
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        CartoonInkIcon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ======== General ========
            SectionHeader(stringResource(R.string.settings_section_general))

            SettingsCard {
            // Language
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_language)) },
                supportingContent = { Text(getLanguageDisplayName(currentLanguage)) },
                leadingContent = {
                    Icon(Icons.Default.Language, contentDescription = null)
                },
                modifier = Modifier.clickable { showLanguageDialog = true }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.sync_title)) },
                supportingContent = { Text(stringResource(R.string.sync_settings_desc_v2)) },
                leadingContent = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                modifier = Modifier.clickable { onNavigateToSync() },
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.llm_provider_settings_title)) },
                supportingContent = { Text(stringResource(R.string.llm_provider_settings_desc)) },
                leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                modifier = Modifier.clickable { onNavigateToLlmProvider() },
            )

            // On-device model download (used for offline next-step suggestions)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_on_device_model_title)) },
                supportingContent = {
                    when {
                        modelDownloading && modelExtracting -> {
                            Text(stringResource(R.string.settings_on_device_model_preparing, modelDownloadProgress))
                        }
                        modelDownloading -> {
                            Text(stringResource(R.string.chat_suggestions_model_downloading, modelDownloadProgress))
                        }
                        modelReady -> Text(stringResource(R.string.settings_on_device_model_ready))
                        modelDownloadFailed -> Text(stringResource(R.string.settings_on_device_model_download_failed))
                        else -> Text(stringResource(R.string.settings_on_device_model_desc))
                    }
                },
                leadingContent = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                trailingContent = {
                    when {
                        modelDownloading -> {
                            LinearProgressIndicator(
                                progress = { modelDownloadProgress / 100f },
                                modifier = Modifier.width(90.dp).height(4.dp),
                            )
                        }
                        modelReady -> {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        else -> {
                            TextButton(onClick = viewModel::downloadModel) {
                                Text(stringResource(R.string.chat_suggestions_download_model))
                            }
                        }
                    }
                },
                modifier = Modifier.clickable(enabled = !modelDownloading && !modelReady) {
                    viewModel.downloadModel()
                },
            )

            // On-device voice recognition (ASR) model download — enables the mic button in chat
            if (asrSupported) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_asr_model_title)) },
                    supportingContent = {
                        when {
                            asrModelDownloading -> {
                                Text(stringResource(R.string.chat_suggestions_model_downloading, asrModelDownloadProgress))
                            }
                            asrModelReady -> Text(stringResource(R.string.settings_asr_model_ready))
                            asrModelDownloadFailed -> Text(stringResource(R.string.settings_on_device_model_download_failed))
                            else -> Text(stringResource(R.string.settings_asr_model_desc))
                        }
                    },
                    leadingContent = { Icon(Icons.Default.Mic, contentDescription = null) },
                    trailingContent = {
                        when {
                            asrModelDownloading -> {
                                LinearProgressIndicator(
                                    progress = { asrModelDownloadProgress / 100f },
                                    modifier = Modifier.width(90.dp).height(4.dp),
                                )
                            }
                            asrModelReady -> {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            else -> {
                                TextButton(onClick = viewModel::downloadAsrModel) {
                                    Text(stringResource(R.string.chat_suggestions_download_model))
                                }
                            }
                        }
                    },
                    modifier = Modifier.clickable(enabled = !asrModelDownloading && !asrModelReady) {
                        viewModel.downloadAsrModel()
                    },
                )
            }

            // Reconnect mode
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_reconnect_mode)) },
                supportingContent = { Text(getReconnectModeDisplayName(reconnectMode)) },
                leadingContent = {
                    Icon(Icons.Default.Sync, contentDescription = null)
                },
                modifier = Modifier.clickable { showReconnectModeDialog = true }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_recent_directories)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_recent_directories_desc, recentDirectoryCount))
                },
                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                modifier = Modifier.clickable { showRecentDirectoryCountDialog = true },
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_background_wake_lock)) },
                supportingContent = { Text(stringResource(R.string.settings_background_wake_lock_desc)) },
                leadingContent = {
                    Icon(Icons.Default.BatteryChargingFull, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = backgroundWakeLock,
                        onCheckedChange = { viewModel.setBackgroundWakeLock(it) },
                        colors = switchColors,
                    )
                },
                modifier = Modifier.clickable {
                    viewModel.setBackgroundWakeLock(!backgroundWakeLock)
                },
            )
            }

            SettingsCardSpacer()

            // ======== Notifications ========
            SectionHeader(stringResource(R.string.settings_section_notifications))

            SettingsCard {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_notifications)) },
                supportingContent = { Text(stringResource(R.string.settings_notifications_desc)) },
                leadingContent = {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { viewModel.setNotificationsEnabled(it) },
                        colors = switchColors
                    )
                },
                modifier = Modifier.clickable { viewModel.setNotificationsEnabled(!notificationsEnabled) }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_silent_notifications)) },
                supportingContent = { Text(stringResource(R.string.settings_silent_notifications_desc)) },
                leadingContent = {
                    Icon(Icons.Default.NotificationsOff, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = silentNotifications,
                        onCheckedChange = { viewModel.setSilentNotifications(it) },
                        enabled = notificationsEnabled,
                        colors = switchColors
                    )
                },
                modifier = Modifier.clickable(enabled = notificationsEnabled) {
                    viewModel.setSilentNotifications(!silentNotifications)
                }
            )

            // 按会话分组折叠通知
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_group_notifications)) },
                supportingContent = { Text(stringResource(R.string.settings_group_notifications_desc)) },
                leadingContent = {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = groupNotifications,
                        onCheckedChange = { viewModel.setGroupNotifications(it) },
                        enabled = notificationsEnabled,
                        colors = switchColors
                    )
                },
                modifier = Modifier.clickable(enabled = notificationsEnabled) {
                    viewModel.setGroupNotifications(!groupNotifications)
                }
            )

            // 免打扰时段
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dnd_schedule)) },
                supportingContent = {
                    Text(
                        text = if (dndEnabled) {
                            stringResource(R.string.settings_dnd_schedule_range, dndStart, dndEnd)
                        } else {
                            stringResource(R.string.settings_dnd_schedule_off)
                        },
                    )
                },
                leadingContent = {
                    Icon(Icons.Default.Bedtime, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = dndEnabled,
                        onCheckedChange = { viewModel.setDndEnabled(it) },
                        enabled = notificationsEnabled,
                        colors = switchColors
                    )
                },
                modifier = Modifier.clickable(enabled = notificationsEnabled) {
                    viewModel.setDndEnabled(!dndEnabled)
                }
            )

            // 免打扰时段起止时间编辑
            if (dndEnabled && notificationsEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_dnd_schedule_start),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showDndStartDialog = true }) {
                        Text(dndStart)
                    }
                    Text(
                        text = stringResource(R.string.settings_dnd_schedule_to),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { showDndEndDialog = true }) {
                        Text(dndEnd)
                    }
                }
            }
            }

            SettingsCardSpacer()

            // ======== Appearance ========
            SectionHeader(stringResource(R.string.settings_section_appearance))

            SettingsCard {
            // Theme
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_theme)) },
                supportingContent = { Text(getThemeDisplayName(currentTheme)) },
                leadingContent = {
                    Icon(Icons.Default.Palette, contentDescription = null)
                },
                modifier = Modifier.clickable { showThemeDialog = true }
            )

            // Theme scheme (full color schemes, e.g. cartoon)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_theme_scheme)) },
                supportingContent = { Text(stringResource(themeSchemeNameRes(themeScheme))) },
                leadingContent = {
                    Icon(Icons.Default.ColorLens, contentDescription = null)
                },
                trailingContent = {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickable { showSchemeDialog = true }
            )

            // Cartoon style: larger radii, ink outlines, solid shadows
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_cartoon_style)) },
                supportingContent = { Text(stringResource(R.string.settings_cartoon_style_desc)) },
                leadingContent = {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = cartoonStyle,
                        onCheckedChange = { viewModel.setCartoonStyle(it) },
                        colors = switchColors
                    )
                },
                modifier = Modifier.clickable { viewModel.setCartoonStyle(!cartoonStyle) }
            )

            // Accent color
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_accent_color)) },
                supportingContent = { Text(stringResource(R.string.settings_accent_color_desc)) },
                leadingContent = {
                    Icon(Icons.Default.Colorize, contentDescription = null)
                },
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(accentSwatchColor(accentColor)),
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                modifier = Modifier.clickable { showAccentDialog = true }
            )

            // Dynamic colors (only on Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                    supportingContent = { Text(stringResource(R.string.settings_dynamic_color_desc)) },
                    leadingContent = {
                        Icon(Icons.Default.ColorLens, contentDescription = null)
                    },
                    trailingContent = {
                        Switch(
                            checked = dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) },
                            colors = switchColors
                        )
                    },
                    modifier = Modifier.clickable { viewModel.setDynamicColor(!dynamicColor) }
                )
            }

            // AMOLED dark mode
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_amoled_dark)) },
                supportingContent = { Text(stringResource(R.string.settings_amoled_dark_desc)) },
                leadingContent = {
                    Icon(Icons.Default.DarkMode, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = amoledDark,
                        onCheckedChange = { viewModel.setAmoledDark(it) },
                        colors = switchColors
                    )
                },
                modifier = Modifier.clickable { viewModel.setAmoledDark(!amoledDark) }
            )
            }

            SettingsCardSpacer()

            // ======== Chat Display ========
            SectionHeader(stringResource(R.string.settings_section_chat_display))

            SettingsCard {
            // Font size
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_font_size)) },
                supportingContent = { Text(getFontSizeDisplayName(chatFontSize)) },
                leadingContent = {
                    Icon(Icons.Default.FormatSize, contentDescription = null)
                },
                modifier = Modifier.clickable { showFontSizeDialog = true }
            )

            // Line spacing
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_line_height)) },
                supportingContent = { Text(getLineHeightDisplayName(chatLineHeight)) },
                leadingContent = {
                    Icon(Icons.Default.UnfoldMore, contentDescription = null)
                },
                modifier = Modifier.clickable { showLineHeightDialog = true }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_terminal_font_size)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_terminal_font_size_value, terminalFontSize.roundToInt()))
                },
                leadingContent = {
                    Icon(Icons.Default.Terminal, contentDescription = null)
                },
                modifier = Modifier.clickable { showTerminalFontSizeDialog = true }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_terminal_panel_hint)) },
                supportingContent = { Text(stringResource(R.string.settings_terminal_panel_hint_desc)) },
                leadingContent = { Icon(Icons.Default.Terminal, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = showTerminalPanelHint,
                        onCheckedChange = viewModel::setShowTerminalPanelHint,
                        colors = switchColors,
                    )
                },
                modifier = Modifier.clickable {
                    viewModel.setShowTerminalPanelHint(!showTerminalPanelHint)
                },
            )

            // Compact messages
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_compact_messages)) },
                supportingContent = { Text(stringResource(R.string.settings_compact_messages_desc)) },
                leadingContent = {
                    Icon(Icons.Default.ViewCompact, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = compactMessages,
                        onCheckedChange = { viewModel.setCompactMessages(it) },
                        colors = switchColors
                    )
                },
                modifier = Modifier.clickable { viewModel.setCompactMessages(!compactMessages) }
            )

            // Code word wrap
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_code_word_wrap)) },
                supportingContent = { Text(stringResource(R.string.settings_code_word_wrap_desc)) },
                leadingContent = {
                    Icon(Icons.Default.WrapText, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = codeWordWrap,
                        onCheckedChange = { viewModel.setCodeWordWrap(it) },
                        colors = switchColors
                    )
                },
                modifier = Modifier.clickable { viewModel.setCodeWordWrap(!codeWordWrap) }
            )

            // Auto-expand tool results
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_auto_expand_tools)) },
                supportingContent = { Text(stringResource(R.string.settings_auto_expand_tools_desc)) },
                leadingContent = {
                    Icon(Icons.Default.UnfoldMore, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = collapseTools,
                        onCheckedChange = { viewModel.setCollapseTools(it) },
                        colors = switchColors
                    )
                },
                modifier = Modifier.clickable { viewModel.setCollapseTools(!collapseTools) }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_expand_reasoning)) },
                supportingContent = { Text(stringResource(R.string.settings_expand_reasoning_desc)) },
                leadingContent = { Icon(Icons.Default.UnfoldMore, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = expandReasoning,
                        onCheckedChange = { viewModel.setExpandReasoning(it) },
                        colors = switchColors,
                    )
                },
                modifier = Modifier.clickable { viewModel.setExpandReasoning(!expandReasoning) },
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_turn_dividers)) },
                supportingContent = { Text(stringResource(R.string.settings_turn_dividers_desc)) },
                leadingContent = { Icon(Icons.Default.HorizontalRule, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = showTurnDividers,
                        onCheckedChange = { viewModel.setShowTurnDividers(it) },
                        colors = switchColors,
                    )
                },
                modifier = Modifier.clickable { viewModel.setShowTurnDividers(!showTurnDividers) },
            )
            }

            SettingsCardSpacer()

            // ======== Chat Behavior ========
            SectionHeader(stringResource(R.string.settings_section_chat_behavior))

            SettingsCard {
            // Initial message count
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_initial_messages)) },
                supportingContent = { Text("$initialMessageCount") },
                leadingContent = {
                    Icon(Icons.Default.History, contentDescription = null)
                },
                modifier = Modifier.clickable { showMessageCountDialog = true }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_history_response_limit)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_history_response_limit_value, messageHistoryResponseLimitMb))
                },
                leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
                modifier = Modifier.clickable { showMessageHistoryResponseLimitDialog = true },
            )

            // Confirm before send
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_confirm_send)) },
                supportingContent = { Text(stringResource(R.string.settings_confirm_send_desc)) },
                leadingContent = {
                    Icon(Icons.Default.Send, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = confirmBeforeSend,
                        onCheckedChange = { viewModel.setConfirmBeforeSend(it) },
                        colors = switchColors
                    )
                },
                modifier = Modifier.clickable { viewModel.setConfirmBeforeSend(!confirmBeforeSend) }
            )

            // Haptic feedback
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_haptic_feedback)) },
                supportingContent = { Text(stringResource(R.string.settings_haptic_feedback_desc)) },
                leadingContent = {
                    Icon(Icons.Default.Vibration, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = hapticFeedback,
                        onCheckedChange = {
                            viewModel.setHapticFeedback(it)
                            AppHaptics.perform(
                                settingsView,
                                AppHapticConfig(it, hapticDurationMillis, hapticAmplitude),
                            )
                        },
                        colors = switchColors
                    )
                },
                modifier = Modifier.clickable {
                    val enabled = !hapticFeedback
                    viewModel.setHapticFeedback(enabled)
                    AppHaptics.perform(
                        settingsView,
                        AppHapticConfig(enabled, hapticDurationMillis, hapticAmplitude),
                    )
                }
            )

            if (hapticFeedback) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_haptic_pattern)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_haptic_pattern_value, hapticDurationMillis, hapticAmplitude))
                    },
                    leadingContent = { Icon(Icons.Default.Vibration, contentDescription = null) },
                    modifier = Modifier.clickable { showHapticPatternDialog = true },
                )
            }

            // Keep screen on
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_keep_screen_on)) },
                supportingContent = { Text(stringResource(R.string.settings_keep_screen_on_desc)) },
                leadingContent = {
                    Icon(Icons.Default.ScreenLockPortrait, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = { viewModel.setKeepScreenOn(it) },
                        colors = switchColors
                    )
                },
                modifier = Modifier.clickable { viewModel.setKeepScreenOn(!keepScreenOn) }
            )

            // Optimize image attachments
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_compress_images)) },
                supportingContent = { Text(stringResource(R.string.settings_compress_images_desc)) },
                leadingContent = {
                    Icon(Icons.Default.PhotoSizeSelectLarge, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = compressImageAttachments,
                        onCheckedChange = { viewModel.setCompressImageAttachments(it) },
                        colors = switchColors
                    )
                },
                modifier = Modifier.clickable {
                    viewModel.setCompressImageAttachments(!compressImageAttachments)
                }
            )

            if (compressImageAttachments) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_compress_images_max_side)) },
                    supportingContent = { Text(getImageMaxSideDisplayName(imageAttachmentMaxLongSide)) },
                    leadingContent = {
                        Spacer(modifier = Modifier.width(24.dp))
                    },
                    modifier = Modifier.clickable { showImageMaxSideDialog = true }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_compress_images_quality)) },
                    supportingContent = {
                        Text(stringResource(R.string.settings_compress_images_quality_value, imageAttachmentWebpQuality))
                    },
                    leadingContent = {
                        Spacer(modifier = Modifier.width(24.dp))
                    },
                    modifier = Modifier.clickable { showImageQualityDialog = true }
                )
            }
            }

            SettingsCardSpacer()

            // ======== Data ========
            SectionHeader(stringResource(R.string.settings_section_data))

            SettingsCard {
            // Backup & restore (encrypted export/import of local settings and servers)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_backup_restore)) },
                supportingContent = { Text(stringResource(R.string.settings_backup_restore_desc)) },
                leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                modifier = Modifier.clickable { showBackupDialog = true },
            )

            // Export current session (entry point placeholder — session data lives in ChatScreen)
            val exportHint = stringResource(R.string.settings_export_hint)
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_export_session)) },
                supportingContent = { Text(stringResource(R.string.settings_export_session_desc)) },
                leadingContent = { Icon(Icons.Default.Send, contentDescription = null) },
                modifier = Modifier.clickable {
                    Toast.makeText(
                        settingsView.context,
                        exportHint,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            )
            }

            SettingsCardSpacer()

            // ======== Advanced ========
            SectionHeader(stringResource(R.string.settings_section_advanced))

            SettingsCard {
            ListItem(
                headlineContent = { Text(stringResource(R.string.diagnostics_title)) },
                supportingContent = { Text(stringResource(R.string.diagnostics_settings_desc)) },
                leadingContent = { Icon(Icons.Default.BugReport, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onNavigateToDiagnostics),
            )
            }

        }

        if (showThemeDialog) {
            ThemePickerDialog(
                currentTheme = currentTheme,
                onThemeSelected = { theme ->
                    viewModel.setThemeMode(theme)
                    showThemeDialog = false
                },
                onDismiss = { showThemeDialog = false }
            )
        }

        DndTimeDialog(
            title = stringResource(R.string.settings_dnd_schedule_start),
            initial = dndStart,
            show = showDndStartDialog,
            onConfirm = { time ->
                viewModel.setDndTime(time, dndEnd)
                showDndStartDialog = false
            },
            onDismiss = { showDndStartDialog = false }
        )

        DndTimeDialog(
            title = stringResource(R.string.settings_dnd_schedule_end),
            initial = dndEnd,
            show = showDndEndDialog,
            onConfirm = { time ->
                viewModel.setDndTime(dndStart, time)
                showDndEndDialog = false
            },
            onDismiss = { showDndEndDialog = false }
        )

        if (showSchemeDialog) {
            ThemeSchemeDialog(
                currentScheme = themeScheme,
                onSchemeSelected = { scheme ->
                    viewModel.setThemeScheme(scheme)
                    showSchemeDialog = false
                },
                onDismiss = { showSchemeDialog = false }
            )
        }

        if (showAccentDialog) {
            AccentColorDialog(
                currentAccent = accentColor,
                onAccentSelected = { accent ->
                    viewModel.setAccentColor(accent)
                    showAccentDialog = false
                },
                onDismiss = { showAccentDialog = false }
            )
        }

        if (showLanguageDialog) {
            LanguagePickerDialog(
                currentLanguage = currentLanguage,
                onLanguageSelected = { languageCode ->
                    viewModel.setLanguage(languageCode)
                    showLanguageDialog = false
                },
                onDismiss = { showLanguageDialog = false }
            )
        }

        if (showFontSizeDialog) {
            FontSizePickerDialog(
                currentSize = chatFontSize,
                onSizeSelected = { size ->
                    viewModel.setChatFontSize(size)
                    showFontSizeDialog = false
                },
                onDismiss = { showFontSizeDialog = false }
            )
        }

        if (showLineHeightDialog) {
            LineHeightDialog(
                currentMultiplier = chatLineHeight,
                onMultiplierSelected = { multiplier ->
                    viewModel.setChatLineHeight(multiplier)
                    showLineHeightDialog = false
                },
                onDismiss = { showLineHeightDialog = false }
            )
        }

        if (showMessageCountDialog) {
            MessageCountPickerDialog(
                currentCount = initialMessageCount,
                onCountSelected = { count ->
                    viewModel.setInitialMessageCount(count)
                    showMessageCountDialog = false
                },
                onDismiss = { showMessageCountDialog = false }
            )
        }

        if (showMessageHistoryResponseLimitDialog) {
            MessageHistoryResponseLimitPickerDialog(
                currentLimitMb = messageHistoryResponseLimitMb,
                onLimitSelected = { limitMb ->
                    viewModel.setMessageHistoryResponseLimitMb(limitMb)
                    showMessageHistoryResponseLimitDialog = false
                },
                onDismiss = { showMessageHistoryResponseLimitDialog = false },
            )
        }

        if (showRecentDirectoryCountDialog) {
            RecentDirectoryCountPickerDialog(
                currentCount = recentDirectoryCount,
                onCountSelected = { count ->
                    viewModel.setRecentDirectoryCount(count)
                    showRecentDirectoryCountDialog = false
                },
                onDismiss = { showRecentDirectoryCountDialog = false },
            )
        }

        if (showReconnectModeDialog) {
            ReconnectModePickerDialog(
                currentMode = reconnectMode,
                onModeSelected = { mode ->
                    viewModel.setReconnectMode(mode)
                    showReconnectModeDialog = false
                },
                onDismiss = { showReconnectModeDialog = false }
            )
        }

        if (showHapticPatternDialog) {
            HapticPatternDialog(
                currentDurationMillis = hapticDurationMillis,
                currentAmplitude = hapticAmplitude,
                onSave = { durationMillis, amplitude ->
                    viewModel.setHapticPattern(durationMillis, amplitude)
                    showHapticPatternDialog = false
                },
                onDismiss = { showHapticPatternDialog = false },
            )
        }

        if (showTerminalFontSizeDialog) {
            TerminalFontSizeDialog(
                currentSize = terminalFontSize,
                onSizeSelected = { size ->
                    viewModel.setTerminalFontSize(size)
                    showTerminalFontSizeDialog = false
                },
                onDismiss = { showTerminalFontSizeDialog = false }
            )
        }

        if (showImageMaxSideDialog) {
            ImageCompressionMaxSideDialog(
                currentMaxSide = imageAttachmentMaxLongSide,
                onSelected = { px ->
                    viewModel.setImageAttachmentMaxLongSide(px)
                    showImageMaxSideDialog = false
                },
                onDismiss = { showImageMaxSideDialog = false }
            )
        }

        if (showImageQualityDialog) {
            ImageCompressionQualityDialog(
                currentQuality = imageAttachmentWebpQuality,
                onSelected = { quality ->
                    viewModel.setImageAttachmentWebpQuality(quality)
                    showImageQualityDialog = false
                },
                onDismiss = { showImageQualityDialog = false }
            )
        }

        if (showBackupDialog) {
            BackupDialog(
                viewModel = backupViewModel,
                onDismiss = { showBackupDialog = false },
            )
        }

    }
}

/** Vertical gap between two grouped setting cards. */

/**
 * Rounded card container that groups a section's settings items, matching the
 * grouped-card visual language of the design system (12dp radius, surfaceContainer,
 * 16dp horizontal margins, 1dp outline in AMOLED).
 */
