/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SettingsRepository.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.hiylo.starburst.data.sync.SyncSettings
import org.hiylo.starburst.domain.model.FavoriteSessionSnapshot
import org.hiylo.starburst.domain.model.SessionCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

internal fun remapServerScopedKey(key: String, serverIdMapping: Map<String, String>): String? {
    val separator = key.indexOf(':')
    if (separator <= 0 || separator == key.lastIndex) return null
    val localServerId = serverIdMapping[key.substring(0, separator)] ?: return null
    return "$localServerId:${key.substring(separator + 1)}"
}

/**
 * App-wide settings stored in DataStore.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    companion object {
        const val DEFAULT_DYNAMIC_COLOR = false
        const val DEFAULT_ACCENT_COLOR = "indigo"
        const val DEFAULT_THEME_SCHEME = "default"

        private const val DEFAULT_DND_START = "22:00"
        private const val DEFAULT_DND_END = "07:00"

        private val LANGUAGE_KEY = stringPreferencesKey("app_language")
        private val THEME_KEY = stringPreferencesKey("app_theme")
        private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
        private val FONT_SIZE_KEY = stringPreferencesKey("chat_font_size")
        private val LINE_HEIGHT_KEY = floatPreferencesKey("chat_line_height")
        private val NOTIFICATIONS_KEY = booleanPreferencesKey("notifications_enabled")

        private val INITIAL_MESSAGE_COUNT_KEY = intPreferencesKey("initial_message_count")
        private val MESSAGE_HISTORY_RESPONSE_LIMIT_MB_KEY = intPreferencesKey("message_history_response_limit_mb")
        private val RECENT_DIRECTORY_COUNT_KEY = intPreferencesKey("recent_directory_count")
        private val CODE_WORD_WRAP_KEY = booleanPreferencesKey("code_word_wrap")
        private val CONFIRM_BEFORE_SEND_KEY = booleanPreferencesKey("confirm_before_send")
        private val AMOLED_DARK_KEY = booleanPreferencesKey("amoled_dark")
        private val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
        private val THEME_SCHEME_KEY = stringPreferencesKey("theme_scheme")
        private val COMPACT_MESSAGES_KEY = booleanPreferencesKey("compact_messages")
        private val COLLAPSE_TOOLS_KEY = booleanPreferencesKey("collapse_tools")
        private val EXPAND_REASONING_KEY = booleanPreferencesKey("expand_reasoning")
        private val SHOW_TURN_DIVIDERS_KEY = booleanPreferencesKey("show_turn_dividers")
        private val GROUP_SESSIONS_BY_PROJECT_KEY = booleanPreferencesKey("group_sessions_by_project")
        private val HAPTIC_FEEDBACK_KEY = booleanPreferencesKey("haptic_feedback")
        private val HAPTIC_STRENGTH_KEY = stringPreferencesKey("haptic_strength")
        private val HAPTIC_DURATION_KEY = intPreferencesKey("haptic_duration_ms")
        private val HAPTIC_AMPLITUDE_KEY = intPreferencesKey("haptic_amplitude")
        private val RECONNECT_MODE_KEY = stringPreferencesKey("reconnect_mode")
        private val BACKGROUND_WAKE_LOCK_KEY = booleanPreferencesKey("background_wake_lock")
        private val KEEP_SCREEN_ON_KEY = booleanPreferencesKey("keep_screen_on")
        private val SILENT_NOTIFICATIONS_KEY = booleanPreferencesKey("silent_notifications")
        private val GROUP_NOTIFICATIONS_KEY = booleanPreferencesKey("group_notifications")
        private val DND_ENABLED_KEY = booleanPreferencesKey("dnd_enabled")
        private val DND_START_KEY = stringPreferencesKey("dnd_start") // "HH:mm"
        private val DND_END_KEY = stringPreferencesKey("dnd_end") // "HH:mm"
        private val COMPRESS_IMAGE_ATTACHMENTS_KEY = booleanPreferencesKey("compress_image_attachments")
        private val IMAGE_ATTACHMENT_MAX_LONG_SIDE_KEY = intPreferencesKey("image_attachment_max_long_side")
        private val IMAGE_ATTACHMENT_WEBP_QUALITY_KEY = intPreferencesKey("image_attachment_webp_quality")
        private val TERMINAL_FONT_SIZE_KEY = floatPreferencesKey("terminal_font_size")
        private val SHOW_TERMINAL_PANEL_HINT_KEY = booleanPreferencesKey("show_terminal_panel_hint")
private val SESSION_CATEGORIES_KEY = stringPreferencesKey("session_categories")
         private val CROSS_SERVER_FAVORITE_ORDER_KEY = stringPreferencesKey("cross_server_favorite_order")
         private val FAVORITE_SESSION_SNAPSHOTS_KEY = stringPreferencesKey("favorite_session_snapshots")
         private val LLM_PROVIDER_BASE_URL_KEY = stringPreferencesKey("llm_provider_base_url")
         private val LLM_PROVIDER_MODEL_KEY = stringPreferencesKey("llm_provider_model")
         private val CUSTOM_COMMANDS_KEY = stringPreferencesKey("custom_commands")

        /** SharedPreferences name used for synchronous locale reads in attachBaseContext. */
        private const val LOCALE_PREFS = "locale_prefs"
        private const val LOCALE_PREFS_KEY = "app_language"

        private const val SERVER_MODEL_HIDDEN_PREFIX = "server_model_hidden_"
        private const val SERVER_PINNED_SESSIONS_PREFIX = "server_pinned_sessions_"
        private const val SERVER_FAVORITE_SESSIONS_PREFIX = "server_favorite_sessions_"
        private const val SERVER_SESSION_CATEGORY_PREFIX = "server_session_category_"
        private const val SERVER_PINNED_IDS_PREFIX = "server_pinned_ids_"
        private const val SERVER_RECENT_PROJECTS_PREFIX = "server_recent_projects_"
        private const val SERVER_SAVED_PATHS_PREFIX = "server_saved_paths_"

        internal fun dynamicColorEnabled(preferences: Preferences): Boolean =
            preferences[DYNAMIC_COLOR_KEY] ?: DEFAULT_DYNAMIC_COLOR

        private fun hapticPatternForStrength(strength: String): Pair<Int, Int> = when (strength) {
            "light" -> 18 to 80
            "strong" -> 48 to 255
            else -> 30 to 160
        }

        /** Read stored language synchronously — safe to call before Hilt init. */
        fun getStoredLanguage(context: Context): String {
            return context.getSharedPreferences(LOCALE_PREFS, Context.MODE_PRIVATE)
                .getString(LOCALE_PREFS_KEY, "") ?: ""
        }
    }

    private fun serverModelHiddenKey(serverId: String) =
        stringSetPreferencesKey(SERVER_MODEL_HIDDEN_PREFIX + serverId)

    private fun serverPinnedSessionsKey(serverId: String) =
        stringPreferencesKey(SERVER_PINNED_SESSIONS_PREFIX + serverId)

    private fun serverFavoriteSessionsKey(serverId: String) =
        stringPreferencesKey(SERVER_FAVORITE_SESSIONS_PREFIX + serverId)

    private fun serverSessionCategoryKey(serverId: String) =
        stringPreferencesKey(SERVER_SESSION_CATEGORY_PREFIX + serverId)

    private fun serverPinnedIdsKey(serverId: String) =
        stringPreferencesKey(SERVER_PINNED_IDS_PREFIX + serverId)

    private fun serverRecentProjectsKey(serverId: String) =
        stringPreferencesKey(SERVER_RECENT_PROJECTS_PREFIX + serverId)

    private fun serverSavedPathsKey(serverId: String) =
        stringPreferencesKey(SERVER_SAVED_PATHS_PREFIX + serverId)

    val sessionCategories: Flow<List<SessionCategory>> = dataStore.data.map { preferences ->
        preferences[SESSION_CATEGORIES_KEY]?.let { encoded ->
            runCatching { json.decodeFromString<List<SessionCategory>>(encoded) }.getOrDefault(emptyList())
        }.orEmpty()
    }

    val crossServerFavoriteOrder: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[CROSS_SERVER_FAVORITE_ORDER_KEY]
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.toList()
            .orEmpty()
    }

    val favoriteSessionSnapshots: Flow<Map<String, FavoriteSessionSnapshot>> = dataStore.data.map { preferences ->
        preferences[FAVORITE_SESSION_SNAPSHOTS_KEY]?.let { encoded ->
            runCatching { json.decodeFromString<Map<String, FavoriteSessionSnapshot>>(encoded) }.getOrDefault(emptyMap())
        }.orEmpty()
    }

    fun sessionCategoryAssignments(serverId: String): Flow<Map<String, String>> = dataStore.data.map { preferences ->
        preferences[serverSessionCategoryKey(serverId)]?.let { encoded ->
            runCatching { json.decodeFromString<Map<String, String>>(encoded) }.getOrDefault(emptyMap())
        }.orEmpty()
    }

    suspend fun saveSessionCategory(category: SessionCategory) {
        dataStore.edit { preferences ->
            val categories = preferences[SESSION_CATEGORIES_KEY]?.let { encoded ->
                runCatching { json.decodeFromString<List<SessionCategory>>(encoded) }.getOrDefault(emptyList())
            }.orEmpty().toMutableList()
            val index = categories.indexOfFirst { it.id == category.id }
            if (index >= 0) categories[index] = category else categories += category
            preferences[SESSION_CATEGORIES_KEY] = json.encodeToString(categories)
        }
    }

    suspend fun deleteSessionCategory(categoryId: String) {
        dataStore.edit { preferences ->
            val categories = preferences[SESSION_CATEGORIES_KEY]?.let { encoded ->
                runCatching { json.decodeFromString<List<SessionCategory>>(encoded) }.getOrDefault(emptyList())
            }.orEmpty().filterNot { it.id == categoryId }
            preferences[SESSION_CATEGORIES_KEY] = json.encodeToString(categories)
        }
    }

    suspend fun setSessionCategory(serverId: String, sessionId: String, categoryId: String?) {
        dataStore.edit { preferences ->
            val key = serverSessionCategoryKey(serverId)
            val assignments = preferences[key]?.let { encoded ->
                runCatching { json.decodeFromString<Map<String, String>>(encoded) }.getOrDefault(emptyMap())
            }.orEmpty().toMutableMap()
            if (categoryId == null) assignments.remove(sessionId) else assignments[sessionId] = categoryId
            preferences[key] = json.encodeToString(assignments)
        }
    }

    fun favoriteSessionIds(serverId: String): Flow<List<String>> = dataStore.data.map { preferences ->
        (preferences[serverFavoriteSessionsKey(serverId)] ?: preferences[serverPinnedSessionsKey(serverId)])
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.toList()
            .orEmpty()
    }

    suspend fun setSessionFavorite(
        serverId: String,
        sessionId: String,
        favorite: Boolean,
        snapshot: FavoriteSessionSnapshot? = null,
    ) {
        dataStore.edit { preferences ->
            val key = serverFavoriteSessionsKey(serverId)
            val legacyKey = serverPinnedSessionsKey(serverId)
            val current = (preferences[key] ?: preferences[legacyKey])
                ?.lineSequence()
                ?.filter(String::isNotBlank)
                ?.distinct()
                ?.toList()
                .orEmpty()
            val updated = if (favorite) {
                listOf(sessionId) + current.filterNot { it == sessionId }
            } else {
                current.filterNot { it == sessionId }
            }
            preferences[key] = updated.joinToString("\n")
            preferences.remove(legacyKey)
            val snapshotKey = favoriteSessionSnapshotKey(serverId, sessionId)
            val snapshots = preferences[FAVORITE_SESSION_SNAPSHOTS_KEY]?.let { encoded ->
                runCatching {
                    json.decodeFromString<Map<String, FavoriteSessionSnapshot>>(encoded)
                }.getOrDefault(emptyMap())
            }.orEmpty().toMutableMap()
            if (!favorite) snapshots.remove(snapshotKey) else if (snapshot != null) snapshots[snapshotKey] = snapshot
            preferences[FAVORITE_SESSION_SNAPSHOTS_KEY] = json.encodeToString(snapshots)
        }
    }

    suspend fun cacheFavoriteSessionSnapshots(snapshots: Map<String, FavoriteSessionSnapshot>) {
        if (snapshots.isEmpty()) return
        dataStore.edit { preferences ->
            val current = preferences[FAVORITE_SESSION_SNAPSHOTS_KEY]?.let { encoded ->
                runCatching {
                    json.decodeFromString<Map<String, FavoriteSessionSnapshot>>(encoded)
                }.getOrDefault(emptyMap())
            }.orEmpty()
            val updated = current + snapshots
            if (updated != current) {
                preferences[FAVORITE_SESSION_SNAPSHOTS_KEY] = json.encodeToString(updated)
            }
        }
    }

    fun favoriteSessionSnapshotKey(serverId: String, sessionId: String): String = "$serverId:$sessionId"

    suspend fun moveFavoriteSession(serverId: String, sessionId: String, offset: Int) {
        if (offset == 0) return
        dataStore.edit { preferences ->
            val key = serverFavoriteSessionsKey(serverId)
            val legacyKey = serverPinnedSessionsKey(serverId)
            val current = (preferences[key] ?: preferences[legacyKey])
                ?.lineSequence()
                ?.filter(String::isNotBlank)
                ?.distinct()
                ?.toMutableList()
                ?: mutableListOf()
            val from = current.indexOf(sessionId)
            if (from < 0) return@edit
            val to = (from + offset).coerceIn(0, current.lastIndex)
            if (from == to) return@edit
            current[from] = current[to]
            current[to] = sessionId
            preferences[key] = current.joinToString("\n")
            preferences.remove(legacyKey)
        }
    }

    fun pinnedSessionIds(serverId: String): Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[serverPinnedIdsKey(serverId)]
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.toList()
            .orEmpty()
    }

    suspend fun setSessionPinned(serverId: String, sessionId: String, pinned: Boolean) {
        dataStore.edit { preferences ->
            val key = serverPinnedIdsKey(serverId)
            val current = (preferences[key] ?: "")
                .lineSequence()
                .filter(String::isNotBlank)
                .distinct()
                .toMutableList()
            val updated = if (pinned) {
                listOf(sessionId) + current.filterNot { it == sessionId }
            } else {
                current.filterNot { it == sessionId }
            }
            preferences[key] = updated.joinToString("\n")
        }
    }

    suspend fun movePinnedSession(serverId: String, sessionId: String, offset: Int) {
        if (offset == 0) return
        dataStore.edit { preferences ->
            val key = serverPinnedIdsKey(serverId)
            val current = (preferences[key] ?: "")
                .lineSequence()
                .filter(String::isNotBlank)
                .distinct()
                .toMutableList()
            val from = current.indexOf(sessionId)
            if (from < 0) return@edit
            val to = (from + offset).coerceIn(0, current.lastIndex)
            if (from == to) return@edit
            val tmp = current[from]
            current[from] = current[to]
            current[to] = tmp
            preferences[key] = current.joinToString("\n")
        }
    }

    /**
     * 批量重排置顶会话顺序（拖拽排序后按新的顺序整体写入）。
     */
    suspend fun reorderPinnedSessions(serverId: String, orderedIds: List<String>) {
        val clean = orderedIds.filter(String::isNotBlank).distinct()
        if (clean.isEmpty()) return
        dataStore.edit { preferences ->
            val key = serverPinnedIdsKey(serverId)
            val current = (preferences[key] ?: "")
                .lineSequence()
                .filter(String::isNotBlank)
                .distinct()
                .toList()
            if (current.isEmpty()) return@edit
            val currentSet = current.toSet()
            if (clean.any { it !in currentSet }) return@edit
            // Preserve any pinned ids that were not part of the drag as trailing entries.
            val reordered = (clean + current.filter { it !in clean }).distinct()
            preferences[key] = reordered.joinToString("\n")
        }
    }

    fun recentProjects(serverId: String): Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[serverRecentProjectsKey(serverId)]
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.toList()
            .orEmpty()
    }

    suspend fun recordRecentProject(serverId: String, directory: String) {
        val trimmed = directory.trim().trimEnd('/')
        if (trimmed.isBlank()) return
        dataStore.edit { preferences ->
            val key = serverRecentProjectsKey(serverId)
            val current = (preferences[key] ?: "")
                .lineSequence()
                .filter(String::isNotBlank)
                .distinct()
                .toMutableList()
            val updated = (listOf(trimmed) + current.filterNot { it == trimmed }).take(20)
            preferences[key] = updated.joinToString("\n")
        }
    }

    /** 用户在 Open Project 里手动固定的常用路径（每服务器独立，保序，最近添加在前）。 */
    fun savedPaths(serverId: String): Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[serverSavedPathsKey(serverId)]
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.toList()
            .orEmpty()
    }

    suspend fun addSavedPath(serverId: String, path: String) {
        val trimmed = path.trim().trimEnd('/')
        if (trimmed.isBlank()) return
        dataStore.edit { preferences ->
            val key = serverSavedPathsKey(serverId)
            val current = (preferences[key] ?: "")
                .lineSequence()
                .filter(String::isNotBlank)
                .distinct()
                .toMutableList()
            val updated = (listOf(trimmed) + current.filterNot { it == trimmed }).take(30)
            preferences[key] = updated.joinToString("\n")
        }
    }

    suspend fun removeSavedPath(serverId: String, path: String) {
        val trimmed = path.trim().trimEnd('/')
        if (trimmed.isBlank()) return
        dataStore.edit { preferences ->
            val key = serverSavedPathsKey(serverId)
            val current = (preferences[key] ?: "")
                .lineSequence()
                .filter(String::isNotBlank)
                .distinct()
                .toList()
            preferences[key] = current.filterNot { it == trimmed }.joinToString("\n")
        }
    }

    suspend fun setCrossServerFavoriteOrderItem(itemKey: String, favorite: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[CROSS_SERVER_FAVORITE_ORDER_KEY]
                ?.lineSequence()
                ?.filter(String::isNotBlank)
                ?.distinct()
                ?.toList()
                .orEmpty()
            val updated = if (favorite) {
                if (itemKey in current) current else current + itemKey
            } else {
                current.filterNot { it == itemKey }
            }
            preferences[CROSS_SERVER_FAVORITE_ORDER_KEY] = updated.joinToString("\n")
        }
    }

    suspend fun setCrossServerFavoriteOrder(itemKeys: List<String>) {
        dataStore.edit { preferences ->
            preferences[CROSS_SERVER_FAVORITE_ORDER_KEY] = itemKeys.distinct().joinToString("\n")
        }
    }

    /**
     * Selected language code (e.g. "en", "ru", "de") or empty string for system default.
     */
    val appLanguage: Flow<String> = dataStore.data.map { preferences ->
        preferences[LANGUAGE_KEY] ?: ""
    }

    /**
     * Selected theme: "system", "light", or "dark".
     */
    val appTheme: Flow<String> = dataStore.data.map { preferences ->
        preferences[THEME_KEY] ?: "system"
    }

    /**
     * Set the app language. Pass empty string to use system default.
     * Also writes to SharedPreferences for synchronous read in attachBaseContext.
     */
    suspend fun setAppLanguage(languageCode: String) {
        context.getSharedPreferences(LOCALE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(LOCALE_PREFS_KEY, languageCode)
            .apply()
        dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = languageCode
        }
    }

    /**
     * Set the app theme. Valid values: "system", "light", "dark".
     */
    suspend fun setAppTheme(theme: String) {
        dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme
        }
    }

    /**
     * Whether dynamic colors (Material You) are enabled. Default: false.
     */
    val dynamicColor: Flow<Boolean> = dataStore.data.map { preferences ->
        dynamicColorEnabled(preferences)
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_KEY] = enabled
        }
    }

    /**
     * Chat font size: "small", "medium", "large". Default: "medium".
     */
    val chatFontSize: Flow<String> = dataStore.data.map { preferences ->
        preferences[FONT_SIZE_KEY] ?: "medium"
    }

    suspend fun setChatFontSize(size: String) {
        dataStore.edit { preferences ->
            preferences[FONT_SIZE_KEY] = size
        }
    }

    /**
     * Chat line spacing multiplier (1.0 = default, up to 2.0). Default: 1.0.
     */
    val chatLineHeight: Flow<Float> = dataStore.data.map { preferences ->
        (preferences[LINE_HEIGHT_KEY] ?: 1f).coerceIn(1f, 2f)
    }

    suspend fun setChatLineHeight(multiplier: Float) {
        dataStore.edit { preferences ->
            preferences[LINE_HEIGHT_KEY] = multiplier.coerceIn(1f, 2f)
        }
    }

    /** 自定义 Slash 命令（用户自定义 /name → prompt）。 */
    @kotlinx.serialization.Serializable
    data class CustomCommand(val name: String, val prompt: String)

    val customCommands: Flow<List<CustomCommand>> = dataStore.data.map { preferences ->
        val raw = preferences[CUSTOM_COMMANDS_KEY] ?: return@map emptyList()
        runCatching { Json.decodeFromString<List<CustomCommand>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun addCustomCommand(name: String, prompt: String): Boolean {
        var added = false
        dataStore.edit { preferences ->
            val current = decodeCustomCommands(preferences).toMutableList()
            if (current.any { it.name == name }) return@edit
            current.add(CustomCommand(name, prompt))
            preferences[CUSTOM_COMMANDS_KEY] = Json.encodeToString(current)
            added = true
        }
        return added
    }

    suspend fun removeCustomCommand(name: String) {
        dataStore.edit { preferences ->
            val current = decodeCustomCommands(preferences).filterNot { it.name == name }
            preferences[CUSTOM_COMMANDS_KEY] = Json.encodeToString(current)
        }
    }

    private fun decodeCustomCommands(preferences: Preferences): List<CustomCommand> {
        val raw = preferences[CUSTOM_COMMANDS_KEY] ?: return emptyList()
        return runCatching { Json.decodeFromString<List<CustomCommand>>(raw) }.getOrDefault(emptyList())
    }

    /**
     * Whether task completion notifications are enabled. Default: true.
     */
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[NOTIFICATIONS_KEY] ?: true
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_KEY] = enabled
        }
    }

    /** Base URL of the externally configured LLM provider used for suggestions (e.g. https://api.openai.com/v1). */
    val llmProviderBaseUrl: Flow<String> = dataStore.data.map { preferences ->
        preferences[LLM_PROVIDER_BASE_URL_KEY] ?: ""
    }

    suspend fun setLlmProviderBaseUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[LLM_PROVIDER_BASE_URL_KEY] = url.trim()
        }
    }

    /** Model name used with the external LLM provider for suggestions (e.g. gpt-4o-mini, deepseek-chat). */
    val llmProviderModel: Flow<String> = dataStore.data.map { preferences ->
        preferences[LLM_PROVIDER_MODEL_KEY] ?: ""
    }

    suspend fun setLlmProviderModel(model: String) {
        dataStore.edit { preferences ->
            preferences[LLM_PROVIDER_MODEL_KEY] = model.trim()
        }
    }

    /**
     * Initial number of messages to load per session. Default: 50.
     */
    val initialMessageCount: Flow<Int> = dataStore.data.map { preferences ->
        preferences[INITIAL_MESSAGE_COUNT_KEY] ?: 50
    }

    suspend fun setInitialMessageCount(count: Int) {
        dataStore.edit { preferences ->
            preferences[INITIAL_MESSAGE_COUNT_KEY] = count
        }
    }

    /** Maximum uncompressed message-history response size before adaptive fallback. Default: 24 MB. */
    val messageHistoryResponseLimitMb: Flow<Int> = dataStore.data.map { preferences ->
        (preferences[MESSAGE_HISTORY_RESPONSE_LIMIT_MB_KEY] ?: 24).coerceIn(8, 128)
    }

    suspend fun setMessageHistoryResponseLimitMb(limitMb: Int) {
        dataStore.edit { preferences ->
            preferences[MESSAGE_HISTORY_RESPONSE_LIMIT_MB_KEY] = limitMb.coerceIn(8, 128)
        }
    }

    /** Number of directories shown in the quick new-session dialog. Default: 20. */
    val recentDirectoryCount: Flow<Int> = dataStore.data.map { preferences ->
        (preferences[RECENT_DIRECTORY_COUNT_KEY] ?: 20).coerceIn(5, 50)
    }

    suspend fun setRecentDirectoryCount(count: Int) {
        dataStore.edit { preferences ->
            preferences[RECENT_DIRECTORY_COUNT_KEY] = count.coerceIn(5, 50)
        }
    }

    /**
     * Whether code blocks use word wrap (true) or horizontal scroll (false). Default: false.
     */
    val codeWordWrap: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[CODE_WORD_WRAP_KEY] ?: false
    }

    suspend fun setCodeWordWrap(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[CODE_WORD_WRAP_KEY] = enabled
        }
    }

    /**
     * Whether to show confirmation dialog before sending a message. Default: false.
     */
    val confirmBeforeSend: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[CONFIRM_BEFORE_SEND_KEY] ?: false
    }

    suspend fun setConfirmBeforeSend(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[CONFIRM_BEFORE_SEND_KEY] = enabled
        }
    }

    /**
     * Whether AMOLED pure black dark theme is enabled. Default: false.
     */
    val amoledDark: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AMOLED_DARK_KEY] ?: false
    }

    suspend fun setAmoledDark(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AMOLED_DARK_KEY] = enabled
        }
    }

    /**
     * Selected accent color: "indigo", "violet", "cyan", "green", "amber", "red". Default: "indigo".
     */
    val accentColor: Flow<String> = dataStore.data.map { preferences ->
        preferences[ACCENT_COLOR_KEY] ?: DEFAULT_ACCENT_COLOR
    }

    suspend fun setAccentColor(accent: String) {
        dataStore.edit { preferences ->
            preferences[ACCENT_COLOR_KEY] = accent
        }
    }

    /**
     * Selected full theme scheme: "default" (accent-based), "candy", "ocean", "sunset".
     * Default: "default".
     */
    val themeScheme: Flow<String> = dataStore.data.map { preferences ->
        preferences[THEME_SCHEME_KEY] ?: DEFAULT_THEME_SCHEME
    }

    suspend fun setThemeScheme(scheme: String) {
        dataStore.edit { preferences ->
            preferences[THEME_SCHEME_KEY] = scheme
        }
    }

    /**
     * Whether compact message spacing is enabled. Default: false.
     */
    val compactMessages: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[COMPACT_MESSAGES_KEY] ?: false
    }

    suspend fun setCompactMessages(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[COMPACT_MESSAGES_KEY] = enabled
        }
    }

    /**
     * Whether tool cards are collapsed by default. Default: false.
     */
    val collapseTools: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[COLLAPSE_TOOLS_KEY] ?: false
    }

    suspend fun setCollapseTools(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[COLLAPSE_TOOLS_KEY] = enabled
        }
    }

    val expandReasoning: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[EXPAND_REASONING_KEY] ?: false
    }

    suspend fun setExpandReasoning(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[EXPAND_REASONING_KEY] = enabled }
    }

    val showTurnDividers: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_TURN_DIVIDERS_KEY] ?: true
    }

    suspend fun setShowTurnDividers(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[SHOW_TURN_DIVIDERS_KEY] = enabled }
    }

    val groupSessionsByProject: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[GROUP_SESSIONS_BY_PROJECT_KEY] ?: false
    }

    suspend fun setGroupSessionsByProject(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[GROUP_SESSIONS_BY_PROJECT_KEY] = enabled }
    }

    /**
     * Whether haptic feedback is enabled. Default: true.
     */
    val hapticFeedback: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[HAPTIC_FEEDBACK_KEY] ?: true
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[HAPTIC_FEEDBACK_KEY] = enabled
        }
    }

    val hapticStrength: Flow<String> = dataStore.data.map { preferences ->
        preferences[HAPTIC_STRENGTH_KEY]?.takeIf { it in setOf("light", "medium", "strong") } ?: "medium"
    }

    suspend fun setHapticStrength(strength: String) {
        require(strength in setOf("light", "medium", "strong"))
        dataStore.edit { preferences -> preferences[HAPTIC_STRENGTH_KEY] = strength }
    }

    val hapticDurationMillis: Flow<Int> = dataStore.data.map { preferences ->
        val fallback = hapticPatternForStrength(preferences[HAPTIC_STRENGTH_KEY] ?: "medium")
        (preferences[HAPTIC_DURATION_KEY] ?: fallback.first).coerceIn(5, 100)
    }

    val hapticAmplitude: Flow<Int> = dataStore.data.map { preferences ->
        val fallback = hapticPatternForStrength(preferences[HAPTIC_STRENGTH_KEY] ?: "medium")
        (preferences[HAPTIC_AMPLITUDE_KEY] ?: fallback.second).coerceIn(1, 255)
    }

    suspend fun setHapticPattern(durationMillis: Int, amplitude: Int) {
        dataStore.edit { preferences ->
            preferences[HAPTIC_DURATION_KEY] = durationMillis.coerceIn(5, 100)
            preferences[HAPTIC_AMPLITUDE_KEY] = amplitude.coerceIn(1, 255)
        }
    }

    /**
     * Reconnect mode: "aggressive" (1-5s), "normal" (1-30s), "conservative" (1-60s).
     * Default: "normal".
     */
    val reconnectMode: Flow<String> = dataStore.data.map { preferences ->
        preferences[RECONNECT_MODE_KEY] ?: "normal"
    }

    suspend fun setReconnectMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[RECONNECT_MODE_KEY] = mode
        }
    }

    /**
     * Whether background SSE connections keep the CPU awake. Default: false.
     */
    val backgroundWakeLock: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[BACKGROUND_WAKE_LOCK_KEY] ?: false
    }

    suspend fun setBackgroundWakeLock(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BACKGROUND_WAKE_LOCK_KEY] = enabled
        }
    }

    /**
     * Whether to keep screen on during streaming. Default: false.
     */
    val keepScreenOn: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEEP_SCREEN_ON_KEY] ?: false
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEEP_SCREEN_ON_KEY] = enabled
        }
    }

    /**
     * Whether notifications are silent (no sound/vibration). Default: false.
     */
    val silentNotifications: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SILENT_NOTIFICATIONS_KEY] ?: false
    }

    suspend fun setSilentNotifications(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SILENT_NOTIFICATIONS_KEY] = enabled
        }
    }

    /**
     * Whether notifications are grouped/collapsed by project by default. Default: false.
     */
    val groupNotifications: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[GROUP_NOTIFICATIONS_KEY] ?: false
    }

    suspend fun setGroupNotifications(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[GROUP_NOTIFICATIONS_KEY] = enabled
        }
    }

    /**
     * Whether do-not-disturb time window is enabled. Default: false.
     */
    val dndEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[DND_ENABLED_KEY] ?: false
    }

    suspend fun setDndEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DND_ENABLED_KEY] = enabled
        }
    }

    /** Do-not-disturb start time "HH:mm". Default: "22:00". */
    val dndStart: Flow<String> = dataStore.data.map { preferences ->
        preferences[DND_START_KEY] ?: DEFAULT_DND_START
    }

    suspend fun setDndStart(value: String) {
        dataStore.edit { preferences ->
            preferences[DND_START_KEY] = value
        }
    }

    /** Do-not-disturb end time "HH:mm". Default: "07:00". */
    val dndEnd: Flow<String> = dataStore.data.map { preferences ->
        preferences[DND_END_KEY] ?: DEFAULT_DND_END
    }

    suspend fun setDndEnd(value: String) {
        dataStore.edit { preferences ->
            preferences[DND_END_KEY] = value
        }
    }

    /**
     * Whether image attachments are optimized (resize + WebP) before sending. Default: true.
     */
    val compressImageAttachments: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[COMPRESS_IMAGE_ATTACHMENTS_KEY] ?: true
    }

    suspend fun setCompressImageAttachments(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[COMPRESS_IMAGE_ATTACHMENTS_KEY] = enabled
        }
    }

    /**
     * Max long side (in px) used when resizing image attachments before sending.
     * Use 0 to keep original resolution. Default: 1440.
     */
    val imageAttachmentMaxLongSide: Flow<Int> = dataStore.data.map { preferences ->
        val value = preferences[IMAGE_ATTACHMENT_MAX_LONG_SIDE_KEY] ?: 1440
        if (value <= 0) 0 else value.coerceIn(720, 4096)
    }

    suspend fun setImageAttachmentMaxLongSide(px: Int) {
        dataStore.edit { preferences ->
            preferences[IMAGE_ATTACHMENT_MAX_LONG_SIDE_KEY] = if (px <= 0) 0 else px.coerceIn(720, 4096)
        }
    }

    /**
     * WebP quality used for image attachment optimization. Default: 60.
     */
    val imageAttachmentWebpQuality: Flow<Int> = dataStore.data.map { preferences ->
        (preferences[IMAGE_ATTACHMENT_WEBP_QUALITY_KEY] ?: 60).coerceIn(1, 100)
    }

    suspend fun setImageAttachmentWebpQuality(quality: Int) {
        dataStore.edit { preferences ->
            preferences[IMAGE_ATTACHMENT_WEBP_QUALITY_KEY] = quality.coerceIn(1, 100)
        }
    }

    /**
     * Default terminal font size in sp. Default: 13.
     */
    val terminalFontSize: Flow<Float> = dataStore.data.map { preferences ->
        (preferences[TERMINAL_FONT_SIZE_KEY] ?: 13f).coerceIn(6f, 20f)
    }

    suspend fun setTerminalFontSize(size: Float) {
        dataStore.edit { preferences ->
            preferences[TERMINAL_FONT_SIZE_KEY] = size.coerceIn(6f, 20f)
        }
    }

    val showTerminalPanelHint: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_TERMINAL_PANEL_HINT_KEY] ?: true
    }

    suspend fun setShowTerminalPanelHint(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_TERMINAL_PANEL_HINT_KEY] = enabled
        }
    }

    /**
     * Hidden model keys for a server. Key format: "providerId:modelId".
     */
    fun hiddenModels(serverId: String): Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[serverModelHiddenKey(serverId)] ?: emptySet()
    }

    /**
     * Set model visibility for a server.
     * visible=true removes it from hidden set, visible=false adds it.
     */
    suspend fun setModelVisibility(serverId: String, providerId: String, modelId: String, visible: Boolean) {
        val key = "$providerId:$modelId"
        val prefsKey = serverModelHiddenKey(serverId)
        dataStore.edit { preferences ->
            val current = preferences[prefsKey] ?: emptySet()
            preferences[prefsKey] = if (visible) {
                current - key
            } else {
                current + key
            }
        }
    }

    suspend fun syncSettingsSnapshot(): SyncSettings = syncSettingsSnapshotFrom(dataStore.data.first())

    internal fun syncSettingsSnapshotFrom(preferences: Preferences): SyncSettings {
        val fallbackHaptic = hapticPatternForStrength(preferences[HAPTIC_STRENGTH_KEY] ?: "medium")
        val hapticDuration = (preferences[HAPTIC_DURATION_KEY] ?: fallbackHaptic.first).coerceIn(5, 100)
        val hapticAmplitude = (preferences[HAPTIC_AMPLITUDE_KEY] ?: fallbackHaptic.second).coerceIn(1, 255)
        return SyncSettings(
            appLanguage = preferences[LANGUAGE_KEY] ?: "",
            appTheme = preferences[THEME_KEY] ?: "system",
            dynamicColor = dynamicColorEnabled(preferences),
            accentColor = preferences[ACCENT_COLOR_KEY] ?: DEFAULT_ACCENT_COLOR,
            themeScheme = preferences[THEME_SCHEME_KEY] ?: DEFAULT_THEME_SCHEME,
            chatFontSize = preferences[FONT_SIZE_KEY] ?: "medium",
            notificationsEnabled = preferences[NOTIFICATIONS_KEY] ?: true,
            initialMessageCount = preferences[INITIAL_MESSAGE_COUNT_KEY] ?: 50,
            messageHistoryResponseLimitMb =
                (preferences[MESSAGE_HISTORY_RESPONSE_LIMIT_MB_KEY] ?: 24).coerceIn(8, 128),
            recentDirectoryCount = (preferences[RECENT_DIRECTORY_COUNT_KEY] ?: 20).coerceIn(5, 50),
            codeWordWrap = preferences[CODE_WORD_WRAP_KEY] ?: false,
            confirmBeforeSend = preferences[CONFIRM_BEFORE_SEND_KEY] ?: false,
            amoledDark = preferences[AMOLED_DARK_KEY] ?: false,
            compactMessages = preferences[COMPACT_MESSAGES_KEY] ?: false,
            collapseTools = preferences[COLLAPSE_TOOLS_KEY] ?: false,
            expandReasoning = preferences[EXPAND_REASONING_KEY] ?: false,
            showTurnDividers = preferences[SHOW_TURN_DIVIDERS_KEY] ?: true,
            groupSessionsByProject = preferences[GROUP_SESSIONS_BY_PROJECT_KEY] ?: false,
            hapticFeedback = preferences[HAPTIC_FEEDBACK_KEY] ?: true,
            hapticStrength = when {
                hapticAmplitude < 96 -> "light"
                hapticAmplitude < 208 -> "medium"
                else -> "strong"
            },
            hapticDurationMillis = hapticDuration,
            hapticAmplitude = hapticAmplitude,
            reconnectMode = preferences[RECONNECT_MODE_KEY] ?: "normal",
            backgroundWakeLock = preferences[BACKGROUND_WAKE_LOCK_KEY] ?: true,
            keepScreenOn = preferences[KEEP_SCREEN_ON_KEY] ?: false,
            silentNotifications = preferences[SILENT_NOTIFICATIONS_KEY] ?: false,
            groupNotifications = preferences[GROUP_NOTIFICATIONS_KEY] ?: false,
            dndEnabled = preferences[DND_ENABLED_KEY] ?: false,
            dndStart = preferences[DND_START_KEY] ?: DEFAULT_DND_START,
            dndEnd = preferences[DND_END_KEY] ?: DEFAULT_DND_END,
            compressImageAttachments = preferences[COMPRESS_IMAGE_ATTACHMENTS_KEY] ?: true,
            imageAttachmentMaxLongSide = preferences[IMAGE_ATTACHMENT_MAX_LONG_SIDE_KEY] ?: 1440,
            imageAttachmentWebpQuality = preferences[IMAGE_ATTACHMENT_WEBP_QUALITY_KEY] ?: 60,
            terminalFontSize = preferences[TERMINAL_FONT_SIZE_KEY] ?: 13f,
            showTerminalPanelHint = preferences[SHOW_TERMINAL_PANEL_HINT_KEY] ?: true,
        )
    }

    suspend fun applySyncSettings(settings: SyncSettings, categories: List<SessionCategory>) {
        dataStore.edit { preferences ->
            applySyncSettingsTo(preferences, settings, categories)
        }
        updateSynchronousLocale(settings.appLanguage)
    }

    suspend fun syncSessionCategoryAssignmentsSnapshot(
        serverIds: Collection<String>,
    ): Map<String, Map<String, String>> {
        return syncSessionCategoryAssignmentsSnapshotFrom(dataStore.data.first(), serverIds)
    }

    internal fun syncSessionCategoriesFrom(preferences: Preferences): List<SessionCategory> {
        return preferences[SESSION_CATEGORIES_KEY]?.let { encoded ->
            runCatching { json.decodeFromString<List<SessionCategory>>(encoded) }.getOrDefault(emptyList())
        }.orEmpty()
    }

    internal fun syncSessionCategoryAssignmentsSnapshotFrom(
        preferences: Preferences,
        serverIds: Collection<String>,
    ): Map<String, Map<String, String>> {
        return serverIds.associateWith { serverId ->
            preferences[serverSessionCategoryKey(serverId)]?.let { encoded ->
                runCatching { json.decodeFromString<Map<String, String>>(encoded) }.getOrDefault(emptyMap())
            }.orEmpty()
        }
    }

    internal fun syncFavoriteSessionIdsFrom(
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

    internal fun syncCrossServerFavoriteOrderFrom(
        preferences: Preferences,
        serverIds: Collection<String>,
    ): List<String> {
        val includedServerIds = serverIds.toSet()
        return preferences[CROSS_SERVER_FAVORITE_ORDER_KEY]
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.filter { it.substringBefore(':') in includedServerIds }
            ?.distinct()
            ?.toList()
            .orEmpty()
    }

    internal fun syncFavoriteSessionSnapshotsFrom(
        preferences: Preferences,
        serverIds: Collection<String>,
    ): Map<String, FavoriteSessionSnapshot> {
        val includedServerIds = serverIds.toSet()
        return preferences[FAVORITE_SESSION_SNAPSHOTS_KEY]?.let { encoded ->
            runCatching { json.decodeFromString<Map<String, FavoriteSessionSnapshot>>(encoded) }
                .getOrDefault(emptyMap())
        }.orEmpty().filterKeys { it.substringBefore(':') in includedServerIds }
    }

    internal fun syncHiddenModelsFrom(
        preferences: Preferences,
        serverIds: Collection<String>,
    ): Map<String, Set<String>> = serverIds.associateWith { serverId ->
        preferences[serverModelHiddenKey(serverId)] ?: emptySet()
    }

    suspend fun applySyncSessionCategoryAssignments(
        assignments: Map<String, Map<String, String>>,
        serverIdMapping: Map<String, String>,
    ) {
        dataStore.edit { preferences ->
            applySyncSessionCategoryAssignmentsTo(preferences, assignments, serverIdMapping)
        }
    }

    internal fun applySyncSettingsTo(
        preferences: MutablePreferences,
        settings: SyncSettings,
        categories: List<SessionCategory>,
    ) {
        preferences[LANGUAGE_KEY] = settings.appLanguage
        preferences[THEME_KEY] = settings.appTheme
        preferences[DYNAMIC_COLOR_KEY] = settings.dynamicColor
        preferences[ACCENT_COLOR_KEY] = settings.accentColor
        preferences[THEME_SCHEME_KEY] = settings.themeScheme
        preferences[FONT_SIZE_KEY] = settings.chatFontSize
        preferences[NOTIFICATIONS_KEY] = settings.notificationsEnabled
        preferences[INITIAL_MESSAGE_COUNT_KEY] = settings.initialMessageCount
        preferences[MESSAGE_HISTORY_RESPONSE_LIMIT_MB_KEY] = settings.messageHistoryResponseLimitMb.coerceIn(8, 128)
        preferences[RECENT_DIRECTORY_COUNT_KEY] = settings.recentDirectoryCount.coerceIn(5, 50)
        preferences[CODE_WORD_WRAP_KEY] = settings.codeWordWrap
        preferences[CONFIRM_BEFORE_SEND_KEY] = settings.confirmBeforeSend
        preferences[AMOLED_DARK_KEY] = settings.amoledDark
        preferences[COMPACT_MESSAGES_KEY] = settings.compactMessages
        preferences[COLLAPSE_TOOLS_KEY] = settings.collapseTools
        preferences[EXPAND_REASONING_KEY] = settings.expandReasoning
        preferences[SHOW_TURN_DIVIDERS_KEY] = settings.showTurnDividers
        preferences[GROUP_SESSIONS_BY_PROJECT_KEY] = settings.groupSessionsByProject
        preferences[HAPTIC_FEEDBACK_KEY] = settings.hapticFeedback
        preferences[HAPTIC_STRENGTH_KEY] = settings.hapticStrength
        val fallbackHaptic = hapticPatternForStrength(settings.hapticStrength)
        preferences[HAPTIC_DURATION_KEY] =
            (settings.hapticDurationMillis ?: fallbackHaptic.first).coerceIn(5, 100)
        preferences[HAPTIC_AMPLITUDE_KEY] =
            (settings.hapticAmplitude ?: fallbackHaptic.second).coerceIn(1, 255)
        preferences[RECONNECT_MODE_KEY] = settings.reconnectMode
        preferences[BACKGROUND_WAKE_LOCK_KEY] = settings.backgroundWakeLock
        preferences[KEEP_SCREEN_ON_KEY] = settings.keepScreenOn
        preferences[SILENT_NOTIFICATIONS_KEY] = settings.silentNotifications
        preferences[GROUP_NOTIFICATIONS_KEY] = settings.groupNotifications
        preferences[DND_ENABLED_KEY] = settings.dndEnabled
        preferences[DND_START_KEY] = settings.dndStart
        preferences[DND_END_KEY] = settings.dndEnd
        preferences[COMPRESS_IMAGE_ATTACHMENTS_KEY] = settings.compressImageAttachments
        preferences[IMAGE_ATTACHMENT_MAX_LONG_SIDE_KEY] = settings.imageAttachmentMaxLongSide.coerceIn(0, 4096)
        preferences[IMAGE_ATTACHMENT_WEBP_QUALITY_KEY] = settings.imageAttachmentWebpQuality.coerceIn(1, 100)
        preferences[TERMINAL_FONT_SIZE_KEY] = settings.terminalFontSize.coerceIn(6f, 20f)
        settings.showTerminalPanelHint?.let { preferences[SHOW_TERMINAL_PANEL_HINT_KEY] = it }
        preferences[SESSION_CATEGORIES_KEY] = json.encodeToString(categories)
    }

    internal fun applySyncSessionCategoryAssignmentsTo(
        preferences: MutablePreferences,
        assignments: Map<String, Map<String, String>>,
        serverIdMapping: Map<String, String>,
    ) {
        assignments.forEach { (remoteServerId, values) ->
            val localServerId = serverIdMapping[remoteServerId] ?: return@forEach
            preferences[serverSessionCategoryKey(localServerId)] = json.encodeToString(values)
        }
    }

    internal fun applySyncSessionCollectionsTo(
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
            val preservedLocalOnly = preferences[CROSS_SERVER_FAVORITE_ORDER_KEY]
                ?.lineSequence()
                ?.filter(String::isNotBlank)
                ?.filterNot { it.substringBefore(':') in mappedLocalIds }
                .orEmpty()
            val mapped = order.mapNotNull { remapServerScopedKey(it, serverIdMapping) }
            preferences[CROSS_SERVER_FAVORITE_ORDER_KEY] = (mapped + preservedLocalOnly).distinct().joinToString("\n")
        }
        favoriteSessionSnapshots?.let { snapshots ->
            val mappedLocalIds = serverIdMapping.values.toSet()
            val current = preferences[FAVORITE_SESSION_SNAPSHOTS_KEY]?.let { encoded ->
                runCatching { json.decodeFromString<Map<String, FavoriteSessionSnapshot>>(encoded) }
                    .getOrDefault(emptyMap())
            }.orEmpty()
            val preservedLocalOnly = current.filterKeys { it.substringBefore(':') !in mappedLocalIds }
            val mapped = snapshots.mapNotNull { (key, snapshot) ->
                remapServerScopedKey(key, serverIdMapping)?.let { it to snapshot }
            }.toMap()
            preferences[FAVORITE_SESSION_SNAPSHOTS_KEY] = json.encodeToString(preservedLocalOnly + mapped)
        }
    }

    internal fun updateSynchronousLocale(language: String) {
        context.getSharedPreferences(LOCALE_PREFS, Context.MODE_PRIVATE).edit()
            .putString(LOCALE_PREFS_KEY, language)
            .apply()
    }
}
