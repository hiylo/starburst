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
                        Icon(
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

@Composable
private fun SectionHeader(title: String) {
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

/** Vertical gap between two grouped setting cards. */
@Composable
private fun SettingsCardSpacer() {
    Spacer(modifier = Modifier.height(12.dp))
}

/**
 * Rounded card container that groups a section's settings items, matching the
 * grouped-card visual language of the design system (12dp radius, surfaceContainer,
 * 16dp horizontal margins, 1dp outline in AMOLED).
 */
@Composable
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    val isAmoled = isAmoledTheme()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
        border = if (isAmoled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(content = content)
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
private fun ThemePickerDialog(
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

/** 主题方案显示名对应的字符串资源 id。 */
private fun themeSchemeNameRes(scheme: String): Int = when (scheme) {
    "candy" -> R.string.settings_theme_scheme_candy
    "ocean" -> R.string.settings_theme_scheme_ocean
    "sunset" -> R.string.settings_theme_scheme_sunset
    else -> R.string.settings_theme_scheme_default
}

/** 主题方案在设置行/对话框里的预览主色（取该方案 light 模式的 primary）。 */
private fun themeSchemePreviewColor(scheme: String): Color {
    return StarBurstSchemes[scheme]?.light?.primary
        ?: Color(0xFF6366F1) // default 方案主色（indigo）
}

/** 主题方案选择对话框：展示各方案的表面色 + 强调色 + 次级色三色条预览。 */
@Composable
private fun ThemeSchemeDialog(
    currentScheme: String,
    onSchemeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val schemeIds = listOf("default", "candy", "ocean", "sunset")
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
private fun AccentColorDialog(
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

private fun accentSwatchColor(accent: String): Color {
    return StarBurstAccents[accent]?.light?.primary
        ?: StarBurstAccents.getValue("indigo").light.primary
}

private fun accentOnSwatchColor(accent: String): Color {
    return StarBurstAccents[accent]?.light?.onPrimary ?: Color.White
}

@Composable
private fun LanguagePickerDialog(
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
private fun FontSizePickerDialog(
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
private fun LineHeightDialog(
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
private fun MessageCountPickerDialog(
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
private fun MessageHistoryResponseLimitPickerDialog(
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
private fun RecentDirectoryCountPickerDialog(
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
private fun ReconnectModePickerDialog(
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
private fun HapticPatternDialog(
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
private fun TerminalFontSizeDialog(
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
private fun ImageCompressionMaxSideDialog(
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
private fun ImageCompressionQualityDialog(
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

@Composable
private fun getThemeDisplayName(theme: String): String {
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
private fun getFontSizeDisplayName(size: String): String {
    return when (size) {
        "small" -> stringResource(R.string.settings_font_size_small)
        "medium" -> stringResource(R.string.settings_font_size_medium)
        "large" -> stringResource(R.string.settings_font_size_large)
        else -> size
    }
}

@Composable
private fun getLineHeightDisplayName(multiplier: Float): String {
    return stringResource(R.string.settings_line_height_value, formatMultiplier(multiplier))
}

private fun formatMultiplier(multiplier: Float): String {
    return String.format(Locale.US, "%.1f", multiplier)
}

@Composable
private fun getLanguageDisplayName(code: String): String {
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
private fun getReconnectModeDisplayName(mode: String): String {
    return when (mode) {
        "aggressive" -> stringResource(R.string.settings_reconnect_aggressive)
        "normal" -> stringResource(R.string.settings_reconnect_normal)
        "conservative" -> stringResource(R.string.settings_reconnect_conservative)
        else -> mode
    }
}

@Composable
private fun getImageMaxSideDisplayName(px: Int): String {
    if (px <= 0) {
        return stringResource(R.string.settings_compress_images_max_side_keep_original)
    }
    return stringResource(R.string.settings_compress_images_max_side_value, px)
}

/**
 * Time-picker dialog for the do-not-disturb window start/end.
 * "HH:mm" strings are parsed to 24h hour/minute; invalid input falls back to midnight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DndTimeDialog(
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

private fun parseHhMm(value: String): Pair<Int, Int> {
    val parts = value.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    return hour to minute
}
