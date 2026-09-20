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
import org.hiylo.starburst.data.backup.SftpBackupSettings
import org.hiylo.starburst.data.sync.SyncSettings
import org.hiylo.starburst.domain.model.FavoriteSessionSnapshot
import org.hiylo.starburst.domain.model.SessionCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore 偏好读取统一收口：`data` 的 map 变换（含 JSON 反序列化）移到 IO 线程执行，
 * 避免每次偏好变更都在主线程解码大集合（会话分类/收藏快照等随会话数线性增长）。
 */
internal fun <T> Flow<Preferences>.mapDecoded(
    transform: (Preferences) -> T,
): Flow<T> = map(transform).flowOn(Dispatchers.IO)

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
    internal val dataStore: DataStore<Preferences>,
    internal val json: Json,
    @dagger.hilt.android.qualifiers.ApplicationContext internal val context: Context
) {
    companion object {
        const val DEFAULT_DYNAMIC_COLOR = false
        const val DEFAULT_ACCENT_COLOR = "indigo"
        const val DEFAULT_THEME_SCHEME = "default"
        const val DEFAULT_CARTOON_STYLE = false

        internal const val DEFAULT_DND_START = "22:00"
        internal const val DEFAULT_DND_END = "07:00"

        internal val LANGUAGE_KEY = stringPreferencesKey("app_language")
        internal val THEME_KEY = stringPreferencesKey("app_theme")
        internal val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
        internal val FONT_SIZE_KEY = stringPreferencesKey("chat_font_size")
        internal val LINE_HEIGHT_KEY = floatPreferencesKey("chat_line_height")
        internal val NOTIFICATIONS_KEY = booleanPreferencesKey("notifications_enabled")

        internal val INITIAL_MESSAGE_COUNT_KEY = intPreferencesKey("initial_message_count")
        internal val MESSAGE_HISTORY_RESPONSE_LIMIT_MB_KEY = intPreferencesKey("message_history_response_limit_mb")
        internal val RECENT_DIRECTORY_COUNT_KEY = intPreferencesKey("recent_directory_count")
        internal val CODE_WORD_WRAP_KEY = booleanPreferencesKey("code_word_wrap")
        internal val CONFIRM_BEFORE_SEND_KEY = booleanPreferencesKey("confirm_before_send")
        internal val AMOLED_DARK_KEY = booleanPreferencesKey("amoled_dark")
        internal val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
        internal val THEME_SCHEME_KEY = stringPreferencesKey("theme_scheme")
        internal val CARTOON_STYLE_KEY = booleanPreferencesKey("cartoon_style")
        internal val COMPACT_MESSAGES_KEY = booleanPreferencesKey("compact_messages")
        internal val COLLAPSE_TOOLS_KEY = booleanPreferencesKey("collapse_tools")
        internal val EXPAND_REASONING_KEY = booleanPreferencesKey("expand_reasoning")
        internal val SHOW_TURN_DIVIDERS_KEY = booleanPreferencesKey("show_turn_dividers")
        internal val GROUP_SESSIONS_BY_PROJECT_KEY = booleanPreferencesKey("group_sessions_by_project")
        internal val HAPTIC_FEEDBACK_KEY = booleanPreferencesKey("haptic_feedback")
        internal val HAPTIC_STRENGTH_KEY = stringPreferencesKey("haptic_strength")
        internal val HAPTIC_DURATION_KEY = intPreferencesKey("haptic_duration_ms")
        internal val HAPTIC_AMPLITUDE_KEY = intPreferencesKey("haptic_amplitude")
        internal val RECONNECT_MODE_KEY = stringPreferencesKey("reconnect_mode")
        internal val BACKGROUND_WAKE_LOCK_KEY = booleanPreferencesKey("background_wake_lock")
        internal val KEEP_SCREEN_ON_KEY = booleanPreferencesKey("keep_screen_on")
        internal val SILENT_NOTIFICATIONS_KEY = booleanPreferencesKey("silent_notifications")
        internal val GROUP_NOTIFICATIONS_KEY = booleanPreferencesKey("group_notifications")
        internal val DND_ENABLED_KEY = booleanPreferencesKey("dnd_enabled")
        internal val DND_START_KEY = stringPreferencesKey("dnd_start") // "HH:mm"
        internal val DND_END_KEY = stringPreferencesKey("dnd_end") // "HH:mm"
        internal val COMPRESS_IMAGE_ATTACHMENTS_KEY = booleanPreferencesKey("compress_image_attachments")
        internal val IMAGE_ATTACHMENT_MAX_LONG_SIDE_KEY = intPreferencesKey("image_attachment_max_long_side")
        internal val IMAGE_ATTACHMENT_WEBP_QUALITY_KEY = intPreferencesKey("image_attachment_webp_quality")
        internal val TERMINAL_FONT_SIZE_KEY = floatPreferencesKey("terminal_font_size")
        internal val SHOW_TERMINAL_PANEL_HINT_KEY = booleanPreferencesKey("show_terminal_panel_hint")
internal val SESSION_CATEGORIES_KEY = stringPreferencesKey("session_categories")
         internal val CROSS_SERVER_FAVORITE_ORDER_KEY = stringPreferencesKey("cross_server_favorite_order")
         internal val FAVORITE_SESSION_SNAPSHOTS_KEY = stringPreferencesKey("favorite_session_snapshots")
         internal val LLM_PROVIDER_BASE_URL_KEY = stringPreferencesKey("llm_provider_base_url")
         internal val LLM_PROVIDER_MODEL_KEY = stringPreferencesKey("llm_provider_model")
         internal val CUSTOM_COMMANDS_KEY = stringPreferencesKey("custom_commands")
         internal val PROMPT_TEMPLATES_KEY = stringPreferencesKey("prompt_templates")

         internal val SFTP_BACKUP_HOST_KEY = stringPreferencesKey("sftp_backup_host")
         internal val SFTP_BACKUP_PORT_KEY = intPreferencesKey("sftp_backup_port")
         internal val SFTP_BACKUP_USERNAME_KEY = stringPreferencesKey("sftp_backup_username")
         internal val SFTP_BACKUP_DIR_KEY = stringPreferencesKey("sftp_backup_dir")

        /** SharedPreferences name used for synchronous locale reads in attachBaseContext. */
        internal const val LOCALE_PREFS = "locale_prefs"
        internal const val LOCALE_PREFS_KEY = "app_language"

        internal const val SERVER_MODEL_HIDDEN_PREFIX = "server_model_hidden_"
        internal const val SERVER_PINNED_SESSIONS_PREFIX = "server_pinned_sessions_"
        internal const val SERVER_FAVORITE_SESSIONS_PREFIX = "server_favorite_sessions_"
        internal const val SERVER_SESSION_CATEGORY_PREFIX = "server_session_category_"
        internal const val SERVER_PINNED_IDS_PREFIX = "server_pinned_ids_"
         internal const val SERVER_RECENT_PROJECTS_PREFIX = "server_recent_projects_"
         internal const val SERVER_SAVED_PATHS_PREFIX = "server_saved_paths_"
         internal const val SERVER_SYSTEM_PROMPT_PREFIX = "server_system_prompt_"
         internal const val SERVER_CONTEXT_LIMIT_PREFIX = "server_context_limit_"

        internal fun dynamicColorEnabled(preferences: Preferences): Boolean =
            preferences[DYNAMIC_COLOR_KEY] ?: DEFAULT_DYNAMIC_COLOR

        internal fun hapticPatternForStrength(strength: String): Pair<Int, Int> = when (strength) {
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

    internal fun serverModelHiddenKey(serverId: String) =
        stringSetPreferencesKey(SERVER_MODEL_HIDDEN_PREFIX + serverId)

    internal fun serverPinnedSessionsKey(serverId: String) =
        stringPreferencesKey(SERVER_PINNED_SESSIONS_PREFIX + serverId)

    internal fun serverFavoriteSessionsKey(serverId: String) =
        stringPreferencesKey(SERVER_FAVORITE_SESSIONS_PREFIX + serverId)

    internal fun serverSessionCategoryKey(serverId: String) =
        stringPreferencesKey(SERVER_SESSION_CATEGORY_PREFIX + serverId)

    internal fun serverPinnedIdsKey(serverId: String) =
        stringPreferencesKey(SERVER_PINNED_IDS_PREFIX + serverId)

    internal fun serverRecentProjectsKey(serverId: String) =
        stringPreferencesKey(SERVER_RECENT_PROJECTS_PREFIX + serverId)

    internal fun serverSavedPathsKey(serverId: String) =
        stringPreferencesKey(SERVER_SAVED_PATHS_PREFIX + serverId)

    private fun serverSystemPromptKey(serverId: String) =
        stringPreferencesKey(SERVER_SYSTEM_PROMPT_PREFIX + serverId)

    private fun serverContextLimitKey(serverId: String) =
        intPreferencesKey(SERVER_CONTEXT_LIMIT_PREFIX + serverId)

    /**
     * Selected language code (e.g. "en", "ru", "de") or empty string for system default.
     */
    val appLanguage: Flow<String> = dataStore.data.mapDecoded { preferences ->
        preferences[LANGUAGE_KEY] ?: ""
    }

    /**
     * Selected theme: "system", "light", or "dark".
     */
    val appTheme: Flow<String> = dataStore.data.mapDecoded { preferences ->
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
    val dynamicColor: Flow<Boolean> = dataStore.data.mapDecoded { preferences ->
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
    val chatFontSize: Flow<String> = dataStore.data.mapDecoded { preferences ->
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
    val chatLineHeight: Flow<Float> = dataStore.data.mapDecoded { preferences ->
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

    val customCommands: Flow<List<CustomCommand>> = dataStore.data.mapDecoded { preferences ->
        val raw = preferences[CUSTOM_COMMANDS_KEY] ?: return@mapDecoded emptyList()
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

    internal fun decodeCustomCommands(preferences: Preferences): List<CustomCommand> {
        val raw = preferences[CUSTOM_COMMANDS_KEY] ?: return emptyList()
        return runCatching { Json.decodeFromString<List<CustomCommand>>(raw) }.getOrDefault(emptyList())
    }

    /** 用户自定义的命名 Prompt 模板（全局共享，跨服务器复用）。 */
    @kotlinx.serialization.Serializable
    data class PromptTemplate(
        val id: String,
        val name: String,
        val prompt: String,
    )

    val promptTemplates: Flow<List<PromptTemplate>> = dataStore.data.mapDecoded { preferences ->
        decodePromptTemplates(preferences)
    }

    /** 新增或更新一个 Prompt 模板，id 为空时生成新 id；返回保存后的模板。 */
    suspend fun savePromptTemplate(id: String?, name: String, prompt: String): PromptTemplate {
        val trimmedName = name.trim()
        val target = PromptTemplate(
            id = id?.takeIf(String::isNotBlank) ?: java.util.UUID.randomUUID().toString(),
            name = trimmedName,
            prompt = prompt,
        )
        dataStore.edit { preferences ->
            val current = decodePromptTemplates(preferences).toMutableList()
            val index = current.indexOfFirst { it.id == target.id }
            if (index >= 0) current[index] = target else current.add(target)
            preferences[PROMPT_TEMPLATES_KEY] = json.encodeToString(current)
        }
        return target
    }

    suspend fun deletePromptTemplate(id: String) {
        dataStore.edit { preferences ->
            val current = decodePromptTemplates(preferences).filterNot { it.id == id }
            preferences[PROMPT_TEMPLATES_KEY] = json.encodeToString(current)
        }
    }

    /** 上移/下移一个 Prompt 模板（在用户模板列表内交换相邻项）。 */
    suspend fun movePromptTemplate(id: String, offset: Int) {
        if (offset == 0) return
        dataStore.edit { preferences ->
            val current = decodePromptTemplates(preferences).toMutableList()
            val from = current.indexOfFirst { it.id == id }
            if (from < 0) return@edit
            val to = (from + offset).coerceIn(0, current.lastIndex)
            if (from == to) return@edit
            val tmp = current[from]
            current[from] = current[to]
            current[to] = tmp
            preferences[PROMPT_TEMPLATES_KEY] = json.encodeToString(current)
        }
    }

    internal fun decodePromptTemplates(preferences: Preferences): List<PromptTemplate> {
        val raw = preferences[PROMPT_TEMPLATES_KEY] ?: return emptyList()
        return runCatching { json.decodeFromString<List<PromptTemplate>>(raw) }.getOrDefault(emptyList())
    }

    /**
     * Whether task completion notifications are enabled. Default: true.
     */
    val notificationsEnabled: Flow<Boolean> = dataStore.data.mapDecoded { preferences ->
        preferences[NOTIFICATIONS_KEY] ?: true
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_KEY] = enabled
        }
    }

    /** Base URL of the externally configured LLM provider used for suggestions (e.g. https://api.openai.com/v1). */
    val llmProviderBaseUrl: Flow<String> = dataStore.data.mapDecoded { preferences ->
        preferences[LLM_PROVIDER_BASE_URL_KEY] ?: ""
    }

    suspend fun setLlmProviderBaseUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[LLM_PROVIDER_BASE_URL_KEY] = url.trim()
        }
    }

    /** Model name used with the external LLM provider for suggestions (e.g. gpt-4o-mini, deepseek-chat). */
    val llmProviderModel: Flow<String> = dataStore.data.mapDecoded { preferences ->
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
    val initialMessageCount: Flow<Int> = dataStore.data.mapDecoded { preferences ->
        preferences[INITIAL_MESSAGE_COUNT_KEY] ?: 50
    }

    suspend fun setInitialMessageCount(count: Int) {
        dataStore.edit { preferences ->
            preferences[INITIAL_MESSAGE_COUNT_KEY] = count
        }
    }

    /** Maximum uncompressed message-history response size before adaptive fallback. Default: 24 MB. */
    val messageHistoryResponseLimitMb: Flow<Int> = dataStore.data.mapDecoded { preferences ->
        (preferences[MESSAGE_HISTORY_RESPONSE_LIMIT_MB_KEY] ?: 24).coerceIn(8, 128)
    }

    suspend fun setMessageHistoryResponseLimitMb(limitMb: Int) {
        dataStore.edit { preferences ->
            preferences[MESSAGE_HISTORY_RESPONSE_LIMIT_MB_KEY] = limitMb.coerceIn(8, 128)
        }
    }

    /** Number of directories shown in the quick new-session dialog. Default: 20. */
    val recentDirectoryCount: Flow<Int> = dataStore.data.mapDecoded { preferences ->
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
    val codeWordWrap: Flow<Boolean> = dataStore.data.mapDecoded { preferences ->
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
    val confirmBeforeSend: Flow<Boolean> = dataStore.data.mapDecoded { preferences ->
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
    val amoledDark: Flow<Boolean> = dataStore.data.mapDecoded { preferences ->
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
    val accentColor: Flow<String> = dataStore.data.mapDecoded { preferences ->
        preferences[ACCENT_COLOR_KEY] ?: DEFAULT_ACCENT_COLOR
    }

    suspend fun setAccentColor(accent: String) {
        dataStore.edit { preferences ->
            preferences[ACCENT_COLOR_KEY] = accent
        }
    }

    /**
     * Selected full theme scheme: "default" (accent-based), "candy", "ocean", "sunset", "bubble".
     * Default: "default".
     */
    val themeScheme: Flow<String> = dataStore.data.mapDecoded { preferences ->
        preferences[THEME_SCHEME_KEY] ?: DEFAULT_THEME_SCHEME
    }

    suspend fun setThemeScheme(scheme: String) {
        dataStore.edit { preferences ->
            preferences[THEME_SCHEME_KEY] = scheme
        }
    }

    /**
     * 卡通风格开关：放大圆角、加粗描边、实体投影。与配色方案正交，可叠加使用。
     * 默认关闭。
     */
    val cartoonStyle: Flow<Boolean> = dataStore.data.mapDecoded { preferences ->
        preferences[CARTOON_STYLE_KEY] ?: DEFAULT_CARTOON_STYLE
    }

    suspend fun setCartoonStyle(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[CARTOON_STYLE_KEY] = enabled
        }
    }

    /**
     * SFTP 备份目标的可持久化配置（不含口令，口令由 Keystore 加密存储）。
     * 供「备份到 NAS」功能使用；默认端口 22。
     */
    val sftpBackupSettings: Flow<SftpBackupSettings> = dataStore.data.mapDecoded { preferences ->
        SftpBackupSettings(
            host = preferences[SFTP_BACKUP_HOST_KEY].orEmpty(),
            port = preferences[SFTP_BACKUP_PORT_KEY] ?: 22,
            username = preferences[SFTP_BACKUP_USERNAME_KEY].orEmpty(),
            remoteDir = preferences[SFTP_BACKUP_DIR_KEY].orEmpty(),
        )
    }

    /** 保存 SFTP 备份目标配置（host/port/username/remoteDir）。 */
    suspend fun setSftpBackupSettings(settings: SftpBackupSettings) {
        dataStore.edit { preferences ->
            preferences[SFTP_BACKUP_HOST_KEY] = settings.host
            preferences[SFTP_BACKUP_PORT_KEY] = settings.port
            preferences[SFTP_BACKUP_USERNAME_KEY] = settings.username
            preferences[SFTP_BACKUP_DIR_KEY] = settings.remoteDir
        }
    }

    /**
     * Whether compact message spacing is enabled. Default: false.
     */
    val compactMessages: Flow<Boolean> = dataStore.data.mapDecoded { preferences ->
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
    val collapseTools: Flow<Boolean> = dataStore.data.mapDecoded { preferences ->
        preferences[COLLAPSE_TOOLS_KEY] ?: false
    }

    suspend fun setCollapseTools(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[COLLAPSE_TOOLS_KEY] = enabled
        }
    }

    val expandReasoning: Flow<Boolean> = dataStore.data.mapDecoded { preferences ->
        preferences[EXPAND_REASONING_KEY] ?: false
    }

    suspend fun setExpandReasoning(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[EXPAND_REASONING_KEY] = enabled }
    }

    val showTurnDividers: Flow<Boolean> = dataStore.data.mapDecoded { preferences ->
        preferences[SHOW_TURN_DIVIDERS_KEY] ?: true
    }

    suspend fun setShowTurnDividers(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[SHOW_TURN_DIVIDERS_KEY] = enabled }
    }

    val groupSessionsByProject: Flow<Boolean> = dataStore.data.mapDecoded { preferences ->
        preferences[GROUP_SESSIONS_BY_PROJECT_KEY] ?: false
    }

    suspend fun setGroupSessionsByProject(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[GROUP_SESSIONS_BY_PROJECT_KEY] = enabled }
    }

    /**
     * Whether haptic feedback is enabled. Default: true.
     */
    val hapticFeedback: Flow<Boolean> = dataStore.data.mapDecoded { preferences ->
        preferences[HAPTIC_FEEDBACK_KEY] ?: true
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[HAPTIC_FEEDBACK_KEY] = enabled
        }
    }

    val hapticStrength: Flow<String> = dataStore.data.mapDecoded { preferences ->
        preferences[HAPTIC_STRENGTH_KEY]?.takeIf { it in setOf("light", "medium", "strong") } ?: "medium"
    }

    suspend fun setHapticStrength(strength: String) {
        require(strength in setOf("light", "medium", "strong"))
        dataStore.edit { preferences -> preferences[HAPTIC_STRENGTH_KEY] = strength }
    }

    val hapticDurationMillis: Flow<Int> = dataStore.data.mapDecoded { preferences ->
        val fallback = hapticPatternForStrength(preferences[HAPTIC_STRENGTH_KEY] ?: "medium")
        (preferences[HAPTIC_DURATION_KEY] ?: fallback.first).coerceIn(5, 100)
    }

    val hapticAmplitude: Flow<Int> = dataStore.data.mapDecoded { preferences ->
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
    val reconnectMode: Flow<String> = dataStore.data.mapDecoded { preferences ->
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
    val backgroundWakeLock: Flow<Boolean> = dataStore.data.mapDecoded { preferences ->
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
    val keepScreenOn: Flow<Boolean> = dataStore.data.mapDecoded { preferences ->
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
    val silentNotifications: Flow<Boolean> = dataStore.data.mapDecoded { preferences ->
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
    val groupNotifications: Flow<Boolean> = dataStore.data.mapDecoded { preferences ->
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
    val dndEnabled: Flow<Boolean> = dataStore.data.mapDecoded { preferences ->
        preferences[DND_ENABLED_KEY] ?: false
    }

    suspend fun setDndEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DND_ENABLED_KEY] = enabled
        }
    }

    /** Do-not-disturb start time "HH:mm". Default: "22:00". */
    val dndStart: Flow<String> = dataStore.data.mapDecoded { preferences ->
        preferences[DND_START_KEY] ?: DEFAULT_DND_START
    }

    suspend fun setDndStart(value: String) {
        dataStore.edit { preferences ->
            preferences[DND_START_KEY] = value
        }
    }

    /** Do-not-disturb end time "HH:mm". Default: "07:00". */
    val dndEnd: Flow<String> = dataStore.data.mapDecoded { preferences ->
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
    val compressImageAttachments: Flow<Boolean> = dataStore.data.mapDecoded { preferences ->
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
    val imageAttachmentMaxLongSide: Flow<Int> = dataStore.data.mapDecoded { preferences ->
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
    val imageAttachmentWebpQuality: Flow<Int> = dataStore.data.mapDecoded { preferences ->
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
    val terminalFontSize: Flow<Float> = dataStore.data.mapDecoded { preferences ->
        (preferences[TERMINAL_FONT_SIZE_KEY] ?: 13f).coerceIn(6f, 20f)
    }

    suspend fun setTerminalFontSize(size: Float) {
        dataStore.edit { preferences ->
            preferences[TERMINAL_FONT_SIZE_KEY] = size.coerceIn(6f, 20f)
        }
    }

    val showTerminalPanelHint: Flow<Boolean> = dataStore.data.mapDecoded { preferences ->
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
    fun hiddenModels(serverId: String): Flow<Set<String>> = dataStore.data.mapDecoded { preferences ->
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

    /**
     * 用户为某服务器配置的自定义系统提示词（空串表示未设置）。
     *
     * @param serverId 服务器 ID
     * @return 该服务器的系统提示词（未设置时为空串）
     */
    fun systemPrompt(serverId: String): Flow<String> = dataStore.data.mapDecoded { preferences ->
        preferences[serverSystemPromptKey(serverId)].orEmpty()
    }

    /**
     * 保存（或清除）某服务器的自定义系统提示词。
     *
     * @param serverId 服务器 ID
     * @param prompt 新的系统提示词；空白时清除
     */
    suspend fun setSystemPrompt(serverId: String, prompt: String) {
        val trimmed = prompt.trim()
        dataStore.edit { preferences ->
            if (trimmed.isEmpty()) {
                preferences.remove(serverSystemPromptKey(serverId))
            } else {
                preferences[serverSystemPromptKey(serverId)] = trimmed
            }
        }
    }

    /**
     * 用户为某服务器覆盖的上下文窗口大小（token 数，0 表示未覆盖）。
     *
     * @param serverId 服务器 ID
     * @return 覆盖的上下文窗口 token 数（未设置时为 0）
     */
    fun contextLimit(serverId: String): Flow<Int> = dataStore.data.mapDecoded { preferences ->
        preferences[serverContextLimitKey(serverId)] ?: 0
    }

    /**
     * 保存（或清除）某服务器的上下文窗口覆盖值。
     *
     * @param serverId 服务器 ID
     * @param limit 覆盖的上下文窗口 token 数；小于等于 0 时清除
     */
    suspend fun setContextLimit(serverId: String, limit: Int) {
        dataStore.edit { preferences ->
            if (limit <= 0) {
                preferences.remove(serverContextLimitKey(serverId))
            } else {
                preferences[serverContextLimitKey(serverId)] = limit
            }
        }
    }

}
