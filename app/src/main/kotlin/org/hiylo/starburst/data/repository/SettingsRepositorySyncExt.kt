/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SettingsRepositorySyncExt.kt
 * Date : 2026/09/20 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.repository

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import org.hiylo.starburst.data.sync.SyncSettings
import org.hiylo.starburst.domain.model.FavoriteSessionSnapshot
import org.hiylo.starburst.domain.model.SessionCategory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SettingsRepository 的同步辅助扩展（备份/同步的 snapshot/apply）。
 * 从 SettingsRepository.kt 拆出，无行为变更。
 */

    internal suspend fun SettingsRepository.syncSettingsSnapshot(): SyncSettings = syncSettingsSnapshotFrom(dataStore.data.first())

    internal fun SettingsRepository.syncSettingsSnapshotFrom(preferences: Preferences): SyncSettings {
        val fallbackHaptic = SettingsRepository.hapticPatternForStrength(preferences[SettingsRepository.HAPTIC_STRENGTH_KEY] ?: "medium")
        val hapticDuration = (preferences[SettingsRepository.HAPTIC_DURATION_KEY] ?: fallbackHaptic.first).coerceIn(5, 100)
        val hapticAmplitude = (preferences[SettingsRepository.HAPTIC_AMPLITUDE_KEY] ?: fallbackHaptic.second).coerceIn(1, 255)
        return SyncSettings(
            appLanguage = preferences[SettingsRepository.LANGUAGE_KEY] ?: "",
            appTheme = preferences[SettingsRepository.THEME_KEY] ?: "system",
            dynamicColor = SettingsRepository.dynamicColorEnabled(preferences),
            accentColor = preferences[SettingsRepository.ACCENT_COLOR_KEY] ?: SettingsRepository.DEFAULT_ACCENT_COLOR,
            themeScheme = preferences[SettingsRepository.THEME_SCHEME_KEY] ?: SettingsRepository.DEFAULT_THEME_SCHEME,
            cartoonStyle = preferences[SettingsRepository.CARTOON_STYLE_KEY] ?: SettingsRepository.DEFAULT_CARTOON_STYLE,
            chatFontSize = preferences[SettingsRepository.FONT_SIZE_KEY] ?: "medium",
            notificationsEnabled = preferences[SettingsRepository.NOTIFICATIONS_KEY] ?: true,
            initialMessageCount = preferences[SettingsRepository.INITIAL_MESSAGE_COUNT_KEY] ?: 50,
            messageHistoryResponseLimitMb =
                (preferences[SettingsRepository.MESSAGE_HISTORY_RESPONSE_LIMIT_MB_KEY] ?: 24).coerceIn(8, 128),
            recentDirectoryCount = (preferences[SettingsRepository.RECENT_DIRECTORY_COUNT_KEY] ?: 20).coerceIn(5, 50),
            codeWordWrap = preferences[SettingsRepository.CODE_WORD_WRAP_KEY] ?: false,
            confirmBeforeSend = preferences[SettingsRepository.CONFIRM_BEFORE_SEND_KEY] ?: false,
            amoledDark = preferences[SettingsRepository.AMOLED_DARK_KEY] ?: false,
            compactMessages = preferences[SettingsRepository.COMPACT_MESSAGES_KEY] ?: false,
            collapseTools = preferences[SettingsRepository.COLLAPSE_TOOLS_KEY] ?: false,
            expandReasoning = preferences[SettingsRepository.EXPAND_REASONING_KEY] ?: false,
            showTurnDividers = preferences[SettingsRepository.SHOW_TURN_DIVIDERS_KEY] ?: true,
            groupSessionsByProject = preferences[SettingsRepository.GROUP_SESSIONS_BY_PROJECT_KEY] ?: false,
            hapticFeedback = preferences[SettingsRepository.HAPTIC_FEEDBACK_KEY] ?: true,
            hapticStrength = when {
                hapticAmplitude < 96 -> "light"
                hapticAmplitude < 208 -> "medium"
                else -> "strong"
            },
            hapticDurationMillis = hapticDuration,
            hapticAmplitude = hapticAmplitude,
            reconnectMode = preferences[SettingsRepository.RECONNECT_MODE_KEY] ?: "normal",
            backgroundWakeLock = preferences[SettingsRepository.BACKGROUND_WAKE_LOCK_KEY] ?: true,
            keepScreenOn = preferences[SettingsRepository.KEEP_SCREEN_ON_KEY] ?: false,
            silentNotifications = preferences[SettingsRepository.SILENT_NOTIFICATIONS_KEY] ?: false,
            groupNotifications = preferences[SettingsRepository.GROUP_NOTIFICATIONS_KEY] ?: false,
            dndEnabled = preferences[SettingsRepository.DND_ENABLED_KEY] ?: false,
            dndStart = preferences[SettingsRepository.DND_START_KEY] ?: SettingsRepository.DEFAULT_DND_START,
            dndEnd = preferences[SettingsRepository.DND_END_KEY] ?: SettingsRepository.DEFAULT_DND_END,
            compressImageAttachments = preferences[SettingsRepository.COMPRESS_IMAGE_ATTACHMENTS_KEY] ?: true,
            imageAttachmentMaxLongSide = preferences[SettingsRepository.IMAGE_ATTACHMENT_MAX_LONG_SIDE_KEY] ?: 1440,
            imageAttachmentWebpQuality = preferences[SettingsRepository.IMAGE_ATTACHMENT_WEBP_QUALITY_KEY] ?: 60,
            terminalFontSize = preferences[SettingsRepository.TERMINAL_FONT_SIZE_KEY] ?: 13f,
            showTerminalPanelHint = preferences[SettingsRepository.SHOW_TERMINAL_PANEL_HINT_KEY] ?: true,
        )
    }

    internal suspend fun SettingsRepository.applySyncSettings(settings: SyncSettings, categories: List<SessionCategory>) {
        dataStore.edit { preferences ->
            applySyncSettingsTo(preferences, settings, categories)
        }
        updateSynchronousLocale(settings.appLanguage)
    }

    internal suspend fun SettingsRepository.syncSessionCategoryAssignmentsSnapshot(
        serverIds: Collection<String>,
    ): Map<String, Map<String, String>> {
        return syncSessionCategoryAssignmentsSnapshotFrom(dataStore.data.first(), serverIds)
    }

    internal fun SettingsRepository.syncSessionCategoriesFrom(preferences: Preferences): List<SessionCategory> {
        return preferences[SettingsRepository.SESSION_CATEGORIES_KEY]?.let { encoded ->
            runCatching { json.decodeFromString<List<SessionCategory>>(encoded) }.getOrDefault(emptyList())
        }.orEmpty()
    }

    internal fun SettingsRepository.syncSessionCategoryAssignmentsSnapshotFrom(
        preferences: Preferences,
        serverIds: Collection<String>,
    ): Map<String, Map<String, String>> {
        return serverIds.associateWith { serverId ->
            preferences[serverSessionCategoryKey(serverId)]?.let { encoded ->
                runCatching { json.decodeFromString<Map<String, String>>(encoded) }.getOrDefault(emptyMap())
            }.orEmpty()
        }
    }

    internal fun SettingsRepository.syncFavoriteSessionIdsFrom(
        preferences: Preferences,
        serverIds: Collection<String>,
    ): Map<String, List<String>> = serverIds.associateWith { serverId ->
        (preferences[serverFavoriteSessionsKey(serverId)] ?: preferences[serverPinnedSessionsKey(serverId)])
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.toList()
            .orEmpty()
    }

    internal fun SettingsRepository.syncCrossServerFavoriteOrderFrom(
        preferences: Preferences,
        serverIds: Collection<String>,
    ): List<String> {
        val includedServerIds = serverIds.toSet()
        return preferences[SettingsRepository.CROSS_SERVER_FAVORITE_ORDER_KEY]
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.filter { it.substringBefore(':') in includedServerIds }
            ?.distinct()
            ?.toList()
            .orEmpty()
    }

    internal fun SettingsRepository.syncFavoriteSessionSnapshotsFrom(
        preferences: Preferences,
        serverIds: Collection<String>,
    ): Map<String, FavoriteSessionSnapshot> {
        val includedServerIds = serverIds.toSet()
        return preferences[SettingsRepository.FAVORITE_SESSION_SNAPSHOTS_KEY]?.let { encoded ->
            runCatching { json.decodeFromString<Map<String, FavoriteSessionSnapshot>>(encoded) }
                .getOrDefault(emptyMap())
        }.orEmpty().filterKeys { it.substringBefore(':') in includedServerIds }
    }

    internal fun SettingsRepository.syncHiddenModelsFrom(
        preferences: Preferences,
        serverIds: Collection<String>,
    ): Map<String, Set<String>> = serverIds.associateWith { serverId ->
        preferences[serverModelHiddenKey(serverId)] ?: emptySet()
    }

    internal fun SettingsRepository.syncChatLineHeightFrom(preferences: Preferences): Float =
        (preferences[SettingsRepository.LINE_HEIGHT_KEY] ?: 1f).coerceIn(1f, 2f)

    internal fun SettingsRepository.syncPromptTemplatesFrom(preferences: Preferences): List<SettingsRepository.PromptTemplate> =
        decodePromptTemplates(preferences)

    internal fun SettingsRepository.syncCustomCommandsFrom(preferences: Preferences): List<SettingsRepository.CustomCommand> =
        decodeCustomCommands(preferences)

    internal fun SettingsRepository.syncLlmProviderFrom(preferences: Preferences): Pair<String, String> =
        (preferences[SettingsRepository.LLM_PROVIDER_BASE_URL_KEY] ?: "") to (preferences[SettingsRepository.LLM_PROVIDER_MODEL_KEY] ?: "")

    internal fun SettingsRepository.syncSavedPathsFrom(
        preferences: Preferences,
        serverIds: Collection<String>,
    ): Map<String, List<String>> = serverIds.associateWith { serverId ->
        preferences[serverSavedPathsKey(serverId)]
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.toList()
            .orEmpty()
    }

    internal fun SettingsRepository.syncRecentProjectsFrom(
        preferences: Preferences,
        serverIds: Collection<String>,
    ): Map<String, List<String>> = serverIds.associateWith { serverId ->
        preferences[serverRecentProjectsKey(serverId)]
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.toList()
            .orEmpty()
    }

    internal suspend fun SettingsRepository.applySyncSessionCategoryAssignments(
        assignments: Map<String, Map<String, String>>,
        serverIdMapping: Map<String, String>,
    ) {
        dataStore.edit { preferences ->
            applySyncSessionCategoryAssignmentsTo(preferences, assignments, serverIdMapping)
        }
    }

    internal fun SettingsRepository.applySyncSettingsTo(
        preferences: MutablePreferences,
        settings: SyncSettings,
        categories: List<SessionCategory>,
    ) {
        preferences[SettingsRepository.LANGUAGE_KEY] = settings.appLanguage
        preferences[SettingsRepository.THEME_KEY] = settings.appTheme
        preferences[SettingsRepository.DYNAMIC_COLOR_KEY] = settings.dynamicColor
        preferences[SettingsRepository.ACCENT_COLOR_KEY] = settings.accentColor
        preferences[SettingsRepository.THEME_SCHEME_KEY] = settings.themeScheme
        preferences[SettingsRepository.CARTOON_STYLE_KEY] = settings.cartoonStyle
        preferences[SettingsRepository.FONT_SIZE_KEY] = settings.chatFontSize
        preferences[SettingsRepository.NOTIFICATIONS_KEY] = settings.notificationsEnabled
        preferences[SettingsRepository.INITIAL_MESSAGE_COUNT_KEY] = settings.initialMessageCount
        preferences[SettingsRepository.MESSAGE_HISTORY_RESPONSE_LIMIT_MB_KEY] = settings.messageHistoryResponseLimitMb.coerceIn(8, 128)
        preferences[SettingsRepository.RECENT_DIRECTORY_COUNT_KEY] = settings.recentDirectoryCount.coerceIn(5, 50)
        preferences[SettingsRepository.CODE_WORD_WRAP_KEY] = settings.codeWordWrap
        preferences[SettingsRepository.CONFIRM_BEFORE_SEND_KEY] = settings.confirmBeforeSend
        preferences[SettingsRepository.AMOLED_DARK_KEY] = settings.amoledDark
        preferences[SettingsRepository.COMPACT_MESSAGES_KEY] = settings.compactMessages
        preferences[SettingsRepository.COLLAPSE_TOOLS_KEY] = settings.collapseTools
        preferences[SettingsRepository.EXPAND_REASONING_KEY] = settings.expandReasoning
        preferences[SettingsRepository.SHOW_TURN_DIVIDERS_KEY] = settings.showTurnDividers
        preferences[SettingsRepository.GROUP_SESSIONS_BY_PROJECT_KEY] = settings.groupSessionsByProject
        preferences[SettingsRepository.HAPTIC_FEEDBACK_KEY] = settings.hapticFeedback
        preferences[SettingsRepository.HAPTIC_STRENGTH_KEY] = settings.hapticStrength
        val fallbackHaptic = SettingsRepository.hapticPatternForStrength(settings.hapticStrength)
        preferences[SettingsRepository.HAPTIC_DURATION_KEY] =
            (settings.hapticDurationMillis ?: fallbackHaptic.first).coerceIn(5, 100)
        preferences[SettingsRepository.HAPTIC_AMPLITUDE_KEY] =
            (settings.hapticAmplitude ?: fallbackHaptic.second).coerceIn(1, 255)
        preferences[SettingsRepository.RECONNECT_MODE_KEY] = settings.reconnectMode
        preferences[SettingsRepository.BACKGROUND_WAKE_LOCK_KEY] = settings.backgroundWakeLock
        preferences[SettingsRepository.KEEP_SCREEN_ON_KEY] = settings.keepScreenOn
        preferences[SettingsRepository.SILENT_NOTIFICATIONS_KEY] = settings.silentNotifications
        preferences[SettingsRepository.GROUP_NOTIFICATIONS_KEY] = settings.groupNotifications
        preferences[SettingsRepository.DND_ENABLED_KEY] = settings.dndEnabled
        preferences[SettingsRepository.DND_START_KEY] = settings.dndStart
        preferences[SettingsRepository.DND_END_KEY] = settings.dndEnd
        preferences[SettingsRepository.COMPRESS_IMAGE_ATTACHMENTS_KEY] = settings.compressImageAttachments
        preferences[SettingsRepository.IMAGE_ATTACHMENT_MAX_LONG_SIDE_KEY] = settings.imageAttachmentMaxLongSide.coerceIn(0, 4096)
        preferences[SettingsRepository.IMAGE_ATTACHMENT_WEBP_QUALITY_KEY] = settings.imageAttachmentWebpQuality.coerceIn(1, 100)
        preferences[SettingsRepository.TERMINAL_FONT_SIZE_KEY] = settings.terminalFontSize.coerceIn(6f, 20f)
        settings.showTerminalPanelHint?.let { preferences[SettingsRepository.SHOW_TERMINAL_PANEL_HINT_KEY] = it }
        preferences[SettingsRepository.SESSION_CATEGORIES_KEY] = json.encodeToString(categories)
    }

    internal fun SettingsRepository.applySyncSessionCategoryAssignmentsTo(
        preferences: MutablePreferences,
        assignments: Map<String, Map<String, String>>,
        serverIdMapping: Map<String, String>,
    ) {
        assignments.forEach { (remoteServerId, values) ->
            val localServerId = serverIdMapping[remoteServerId] ?: return@forEach
            preferences[serverSessionCategoryKey(localServerId)] = json.encodeToString(values)
        }
    }

    internal fun SettingsRepository.applySyncSessionCollectionsTo(
        preferences: MutablePreferences,
        favoriteSessionIds: Map<String, List<String>>?,
        crossServerFavoriteOrder: List<String>?,
        favoriteSessionSnapshots: Map<String, FavoriteSessionSnapshot>?,
        hiddenModels: Map<String, Set<String>>?,
        serverIdMapping: Map<String, String>,
    ) {
        favoriteSessionIds?.let { favorites ->
            serverIdMapping.forEach { (remoteServerId, localServerId) ->
                preferences[serverFavoriteSessionsKey(localServerId)] = favorites[remoteServerId]
                    .orEmpty()
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString("\n")
                preferences.remove(serverPinnedSessionsKey(localServerId))
            }
        }
        hiddenModels?.let { models ->
            serverIdMapping.forEach { (remoteServerId, localServerId) ->
                preferences[serverModelHiddenKey(localServerId)] = models[remoteServerId].orEmpty()
            }
        }
        crossServerFavoriteOrder?.let { order ->
            val mappedLocalIds = serverIdMapping.values.toSet()
            val preservedLocalOnly = preferences[SettingsRepository.CROSS_SERVER_FAVORITE_ORDER_KEY]
                ?.lineSequence()
                ?.filter(String::isNotBlank)
                ?.filterNot { it.substringBefore(':') in mappedLocalIds }
                .orEmpty()
            val mapped = order.mapNotNull { remapServerScopedKey(it, serverIdMapping) }
            preferences[SettingsRepository.CROSS_SERVER_FAVORITE_ORDER_KEY] = (mapped + preservedLocalOnly).distinct().joinToString("\n")
        }
        favoriteSessionSnapshots?.let { snapshots ->
            val mappedLocalIds = serverIdMapping.values.toSet()
            val current = preferences[SettingsRepository.FAVORITE_SESSION_SNAPSHOTS_KEY]?.let { encoded ->
                runCatching { json.decodeFromString<Map<String, FavoriteSessionSnapshot>>(encoded) }
                    .getOrDefault(emptyMap())
            }.orEmpty()
            val preservedLocalOnly = current.filterKeys { it.substringBefore(':') !in mappedLocalIds }
            val mapped = snapshots.mapNotNull { (key, snapshot) ->
                remapServerScopedKey(key, serverIdMapping)?.let { it to snapshot }
            }.toMap()
            preferences[SettingsRepository.FAVORITE_SESSION_SNAPSHOTS_KEY] = json.encodeToString(preservedLocalOnly + mapped)
        }
    }

    internal fun SettingsRepository.applyChatLineHeightTo(preferences: MutablePreferences, value: Float) {
        preferences[SettingsRepository.LINE_HEIGHT_KEY] = value.coerceIn(1f, 2f)
    }

    internal fun SettingsRepository.applyPromptTemplatesTo(preferences: MutablePreferences, templates: List<SettingsRepository.PromptTemplate>) {
        preferences[SettingsRepository.PROMPT_TEMPLATES_KEY] = json.encodeToString(templates)
    }

    internal fun SettingsRepository.applyCustomCommandsTo(preferences: MutablePreferences, commands: List<SettingsRepository.CustomCommand>) {
        preferences[SettingsRepository.CUSTOM_COMMANDS_KEY] = Json.encodeToString(commands)
    }

    internal fun SettingsRepository.applyLlmProviderTo(preferences: MutablePreferences, baseUrl: String, model: String) {
        preferences[SettingsRepository.LLM_PROVIDER_BASE_URL_KEY] = baseUrl.trim()
        preferences[SettingsRepository.LLM_PROVIDER_MODEL_KEY] = model.trim()
    }

    internal fun SettingsRepository.applySyncSavedPathsTo(
        preferences: MutablePreferences,
        savedPaths: Map<String, List<String>>,
        serverIdMapping: Map<String, String>,
    ) {
        savedPaths.forEach { (remoteServerId, paths) ->
            val localServerId = serverIdMapping[remoteServerId] ?: return@forEach
            preferences[serverSavedPathsKey(localServerId)] = paths
                .filter(String::isNotBlank)
                .distinct()
                .joinToString("\n")
        }
    }

    internal fun SettingsRepository.applySyncRecentProjectsTo(
        preferences: MutablePreferences,
        projects: Map<String, List<String>>,
        serverIdMapping: Map<String, String>,
    ) {
        projects.forEach { (remoteServerId, paths) ->
            val localServerId = serverIdMapping[remoteServerId] ?: return@forEach
            preferences[serverRecentProjectsKey(localServerId)] = paths
                .filter(String::isNotBlank)
                .distinct()
                .joinToString("\n")
        }
    }

    internal fun SettingsRepository.updateSynchronousLocale(language: String) {
        context.getSharedPreferences(SettingsRepository.LOCALE_PREFS, Context.MODE_PRIVATE).edit()
            .putString(SettingsRepository.LOCALE_PREFS_KEY, language)
            .apply()
    }
