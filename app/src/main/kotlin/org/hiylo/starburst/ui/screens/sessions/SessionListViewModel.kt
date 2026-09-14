/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SessionListViewModel.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.sessions

import org.hiylo.starburst.logging.AppLogger as Log
import androidx.lifecycle.SavedStateHandle
import org.hiylo.starburst.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import org.hiylo.starburst.data.api.FileNode
import org.hiylo.starburst.data.api.OpenCodeApi
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.api.createSession
import org.hiylo.starburst.data.api.deleteSession
import org.hiylo.starburst.data.api.executeCommand
import org.hiylo.starburst.data.api.findFiles
import org.hiylo.starburst.data.api.listDirectory
import org.hiylo.starburst.data.api.listPendingQuestions
import org.hiylo.starburst.data.api.listProjects
import org.hiylo.starburst.data.api.listSessions
import org.hiylo.starburst.data.api.listSessionStatuses
import org.hiylo.starburst.data.api.runShellCommand
import org.hiylo.starburst.data.api.updateSession
import org.hiylo.starburst.data.repository.BackendRepository
import org.hiylo.starburst.data.repository.EventReducer
import org.hiylo.starburst.data.repository.DirectoryScope
import org.hiylo.starburst.data.repository.SettingsRepository
import org.hiylo.starburst.data.repository.ServerConnectionStateRepository
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.domain.model.Project
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.domain.model.Session
import org.hiylo.starburst.domain.model.SessionStatus
import org.hiylo.starburst.domain.model.SessionCategory
import org.hiylo.starburst.domain.model.FavoriteSessionSnapshot
import org.hiylo.starburst.domain.model.PendingInteraction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SessionListViewModel"
/** Status poll interval while the SSE stream is connected (fallback for states missed while disconnected). */
private const val STATUS_POLL_INTERVAL_CONNECTED_MS = 10_000L
/** Status poll interval while disconnected (must actively rediscover status). */
private const val STATUS_POLL_INTERVAL_DISCONNECTED_MS = 5_000L

data class SessionListUiState(
    val sessionGroups: List<ProjectSessionGroup> = emptyList(),
    val projects: List<Project> = emptyList(),
    val serverName: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val categories: List<SessionCategory> = emptyList(),
    /** All configured servers (for the one-tap server switcher). */
    val servers: List<ServerConfig> = emptyList(),
    /** Ids of servers that are currently connected. */
    val connectedServerIds: Set<String> = emptySet(),
)

/** A group of sessions belonging to a project. */
data class ProjectSessionGroup(
    val projectId: String,
    val projectName: String,
    val directory: String,
    val sessions: List<SessionItem>,
    val branch: String? = null,
    /** Per-session tilde-path labels (sessionId -> tildePath) for flat display. */
    val sessionDirLabels: Map<String, String> = emptyMap()
)

internal fun buildProjectSessionGroups(
    sessions: List<SessionItem>,
    projects: List<Project>,
    homeDir: String?,
    branches: Map<DirectoryScope, String?>,
    serverId: String,
): List<ProjectSessionGroup> {
    fun normalized(path: String) = path.trimEnd('/').ifEmpty { "/" }
    fun displayPath(path: String): String {
        val dir = normalized(path)
        return if (!homeDir.isNullOrBlank() && (dir == homeDir || dir.startsWith("$homeDir/"))) {
            "~" + dir.removePrefix(homeDir)
        } else {
            dir
        }
    }
    fun projectFor(session: Session): Project? {
        projects.firstOrNull { it.id.isNotBlank() && it.id == session.projectId }?.let { return it }
        val directory = normalized(session.directory)
        return projects
            .filter { project ->
                val root = normalized(project.worktree.ifBlank { project.path })
                directory == root || directory.startsWith("$root/")
            }
            .maxByOrNull { normalized(it.worktree.ifBlank { it.path }).length }
    }

    return sessions
        .groupBy { item ->
            val project = projectFor(item.session)
            project?.id?.takeIf { it.isNotBlank() }
                ?: "directory:${normalized(item.session.directory)}"
        }
        .map { (key, items) ->
            val sorted = sortSessionItems(items)
            val project = projectFor(sorted.first().session)
            val directory = normalized(
                project?.worktree?.takeIf { it.isNotBlank() }
                    ?: project?.path?.takeIf { it.isNotBlank() }
                    ?: sorted.first().session.directory,
            )
            val branch = branches.entries.firstOrNull { (scope, _) ->
                scope.serverId == serverId && normalized(scope.directory) == directory
            }?.value
            ProjectSessionGroup(
                projectId = project?.id ?: key,
                projectName = project?.displayName
                    ?: directory.substringAfterLast('/').ifEmpty { "/" },
                directory = directory,
                sessions = sorted,
                branch = branch,
                sessionDirLabels = sorted.associate { it.session.id to displayPath(it.session.directory) },
            )
        }
        .sortedWith(compareByDescending<ProjectSessionGroup> { group ->
            group.sessions.any { it.isPinned }
        }.thenBy { group ->
            group.sessions.mapNotNull { it.pinnedIndex }.minOrNull() ?: Int.MAX_VALUE
        }.thenByDescending { group ->
            group.sessions.any { it.isUnconfirmedCompleted }
        }.thenByDescending { group ->
            group.sessions.any { it.isFavorite }
        }.thenBy { group ->
            group.sessions.mapNotNull { it.favoriteIndex }.minOrNull() ?: Int.MAX_VALUE
        }        .thenByDescending { group ->
            group.sessions.maxOfOrNull { it.lastUserMessageAt } ?: 0
        }.thenBy { it.projectName.lowercase() })
}

data class SessionItem(
    val session: Session,
    val status: SessionStatus = SessionStatus.Idle,
    val favoriteIndex: Int? = null,
    val pinnedIndex: Int? = null,
    val category: SessionCategory? = null,
    val isUnconfirmedCompleted: Boolean = false,
    val unconfirmedCompletedAt: Long = 0L,
    /** Last time the user sent a message in this session (stable; does not change during streaming). */
    val lastUserMessageAt: Long = 0L,
) {
    val isFavorite: Boolean get() = favoriteIndex != null
    val isPinned: Boolean get() = pinnedIndex != null
}

internal fun sortSessionItems(items: List<SessionItem>): List<SessionItem> = items.sortedWith(
    compareByDescending<SessionItem> { it.isPinned }
        .thenBy { it.pinnedIndex ?: Int.MAX_VALUE }
        .thenByDescending { it.isUnconfirmedCompleted }
        .thenByDescending { it.isFavorite }
        .thenBy { it.favoriteIndex ?: Int.MAX_VALUE }
        .thenByDescending { it.lastUserMessageAt }
        .thenByDescending { it.session.id }
)

internal data class DirectoryPathQuery(val parent: String, val segment: String)

internal fun parseDirectoryPathQuery(query: String, homeDirectory: String): DirectoryPathQuery? {
    val trimmed = query.trim()
    val expanded = when {
        trimmed == "~" -> homeDirectory
        trimmed.startsWith("~/") -> homeDirectory.trimEnd('/') + trimmed.removePrefix("~")
        trimmed.startsWith('/') -> trimmed
        else -> return null
    }
    if (expanded == "/") return DirectoryPathQuery("/", "")
    if (expanded.endsWith('/')) return DirectoryPathQuery(expanded.trimEnd('/').ifEmpty { "/" }, "")
    val parent = expanded.substringBeforeLast('/', missingDelimiterValue = "")
        .ifEmpty { "/" }
    return DirectoryPathQuery(parent, expanded.substringAfterLast('/'))
}

@HiltViewModel
class SessionListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val eventReducer: EventReducer,
    private val api: OpenCodeApi,
    private val settingsRepository: SettingsRepository,
    private val connectionStateRepository: ServerConnectionStateRepository,
    private val serverRepository: ServerRepository,
    private val backendRepository: BackendRepository,
) : ViewModel() {

    val serverUrl: String = savedStateHandle.get<String>("serverUrl").orEmpty()
    private val username: String = savedStateHandle.get<String>("username").orEmpty()
    private val password: String = savedStateHandle.get<String>("password").orEmpty()
    val serverName: String = savedStateHandle.get<String>("serverName").orEmpty()
    val serverId: String = savedStateHandle.get<String>("serverId").orEmpty()
    /** 进入即自动新建会话（来自 Widget / 快捷方式「新建会话」入口）。 */
    private val autoNewSession: Boolean = savedStateHandle.get<Boolean>("autoNewSession") ?: false

    private val conn = ServerConnection.from(serverUrl, username, password.ifEmpty { null })

    val groupSessionsByProject: StateFlow<Boolean> = settingsRepository.groupSessionsByProject.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )

    val recentDirectoryCount: StateFlow<Int> = settingsRepository.recentDirectoryCount.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        20,
    )

    /** 会话列表是否使用紧凑布局（复用聊天的「紧凑模式」开关）。 */
    val compactSessions: StateFlow<Boolean> = settingsRepository.compactMessages.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )

    private val favoriteSessionIds: StateFlow<List<String>> = settingsRepository.favoriteSessionIds(serverId).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private val pinnedSessionIds: StateFlow<List<String>> = settingsRepository.pinnedSessionIds(serverId).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val recentProjects: StateFlow<List<String>> = settingsRepository.recentProjects(serverId).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private val sessionCategories: StateFlow<List<SessionCategory>> = settingsRepository.sessionCategories.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private val categoryAssignments: StateFlow<Map<String, String>> =
        settingsRepository.sessionCategoryAssignments(serverId).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyMap(),
        )

    /** 周期性从服务器 REST 拉取的待决问题快照（sessionId -> 有待决问题），按 sessionId 分组。 */
    private val _pendingQuestionSessionIds = MutableStateFlow<Set<String>>(emptySet())

    /**
     * 当前服务器有待决问题（待用户回答）的会话 id 集合。
     * REST 快照为权威来源，另以 SSE 实时 [PendingInteraction.Question] 兜底，保证回答后徽标即时消失。
     */
    val pendingQuestionSessionIds: StateFlow<Set<String>> = combine(
        eventReducer.pendingInteractions,
        _pendingQuestionSessionIds,
    ) { interactions, restSnapshot ->
        val realtime = interactions
            .filterIsInstance<PendingInteraction.Question>()
            .mapTo(mutableSetOf<String>()) { it.sessionId }
        realtime += restSnapshot
        realtime
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptySet(),
    )

    private val _error = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(true)
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    private val _homeDir = MutableStateFlow<String?>(null)
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val _navigateToSession = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToSession: SharedFlow<String> = _navigateToSession.asSharedFlow()

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<SessionListUiState> = combine(
        listOf(
            eventReducer.sessions,
            eventReducer.sessionStatuses,
            eventReducer.serverSessions,
            _isLoading,
            _error,
            _projects,
            _homeDir,
            _selectedIds,
            eventReducer.vcsBranches,
            favoriteSessionIds,
            pinnedSessionIds,
            sessionCategories,
            categoryAssignments,
            eventReducer.unconfirmedCompletedSessions,
            eventReducer.lastUserMessageAt,
            serverRepository.servers,
            connectionStateRepository.connectedServerIds,
            pendingQuestionSessionIds,
        )
    ) { values ->
        val allSessions = values[0] as List<Session>
        val statuses = values[1] as Map<String, SessionStatus>
        val serverSessions = values[2] as Map<String, Set<String>>
        val loading = values[3] as Boolean
        val error = values[4] as String?
        val projects = values[5] as List<Project>
        val homeDir = values[6] as String?
        val selectedIds = values[7] as Set<String>
        val branches = values[8] as Map<DirectoryScope, String?>
        val favoriteIds = values[9] as List<String>
        val pinnedIds = values[10] as List<String>
        val categories = values[11] as List<SessionCategory>
        val assignments = values[12] as Map<String, String>
        val unconfirmedCompleted = values[13] as Map<String, Long>
        val lastUserMessageAt = values[14] as Map<String, Long>
        val servers = values[15] as List<ServerConfig>
        val connectedServerIds = values[16] as Set<String>
        val pendingQuestionIds = values[17] as Set<String>
        val favoriteOrder = favoriteIds.withIndex().associate { (index, id) -> id to index }
        val pinnedOrder = pinnedIds.withIndex().associate { (index, id) -> id to index }
        val categoriesById = categories.associateBy { it.id }

        // Filter sessions belonging to this server
        val serverSessionIds = serverSessions[serverId] ?: emptySet()

        // A parent session should look "busy" if any of its sub-agent (child) sessions are busy/retrying,
        // even though the children themselves are filtered out of the list.
        val childBusyByParent = mutableMapOf<String, SessionStatus>()
        // Sub-agent 有待决问题时，父会话同样标记为 Question（子会话被过滤不出现在列表里）。
        val parentWithPendingQuestion = mutableSetOf<String>()
        for (child in allSessions) {
            val parentId = child.parentId ?: continue
            if (child.id !in serverSessionIds) continue
            if (child.id in pendingQuestionIds) parentWithPendingQuestion += parentId
            val childStatus = statuses[child.id] ?: continue
            if (childStatus is SessionStatus.Busy || childStatus is SessionStatus.Retry) {
                childBusyByParent[parentId] = childStatus
            }
        }

        val sessions = allSessions
            .filter { it.id in serverSessionIds && !it.isArchived && it.parentId == null }
            .sortedByDescending { it.time.updated }
            .map { session ->
                SessionItem(
                    session = session,
                    status = if (session.id in pendingQuestionIds || session.id in parentWithPendingQuestion) {
                        // 有待决问题优先：等待用户回答，优先级高于处理中/重试。
                        SessionStatus.Question
                    } else {
                        when (val self = statuses[session.id]) {
                            // 父会话自身正在忙/重试 → 直接用它。
                            is SessionStatus.Busy, is SessionStatus.Retry -> self
                            // 否则子会话忙 → 父会话视为忙（子会话被过滤不出现在列表里）。
                            else -> childBusyByParent[session.id] ?: self ?: SessionStatus.Idle
                        }
                    },
                    favoriteIndex = favoriteOrder[session.id],
                    pinnedIndex = pinnedOrder[session.id],
                    category = assignments[session.id]?.let(categoriesById::get),
                    isUnconfirmedCompleted = session.id in unconfirmedCompleted,
                    unconfirmedCompletedAt = unconfirmedCompleted[session.id] ?: 0L,
                    lastUserMessageAt = lastUserMessageAt[session.id] ?: session.time.created,
                )
            }

        val groups = buildProjectSessionGroups(sessions, projects, homeDir, branches, serverId)

        val visibleSessionIds = sessions.map { it.session.id }.toSet()
        val validSelectedIds = selectedIds.intersect(visibleSessionIds)
        if (validSelectedIds != selectedIds) {
            _selectedIds.value = validSelectedIds
        }

        SessionListUiState(
            sessionGroups = groups,
            projects = projects,
            serverName = serverName,
            isLoading = loading,
            error = error,
            selectedIds = validSelectedIds,
            isSelectionMode = validSelectedIds.isNotEmpty(),
            categories = categories,
            servers = servers,
            connectedServerIds = connectedServerIds,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SessionListUiState(serverName = serverName)
    )

    init {
        loadHomeDir()
        loadSessions()
        if (autoNewSession) {
            createNewSession()
        }
        viewModelScope.launch {
            // Poll statuses periodically. When the SSE stream is connected the server pushes
            // status changes in real-time, so polling is only a fallback and runs infrequently.
            while (true) {
                val connected = connectionStateRepository.connectedServerIds.value.contains(serverId)
                delay(if (connected) STATUS_POLL_INTERVAL_CONNECTED_MS else STATUS_POLL_INTERVAL_DISCONNECTED_MS)
                refreshSessionStatuses()
                refreshPendingQuestions()
            }
        }
    }

    fun setGroupSessionsByProject(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setGroupSessionsByProject(enabled) }
    }

    fun toggleFavorite(sessionId: String) {
        val favorites = favoriteSessionIds.value
        val session = uiState.value.sessionGroups
            .asSequence()
            .flatMap { it.sessions.asSequence() }
            .firstOrNull { it.session.id == sessionId }
            ?.session
        viewModelScope.launch {
            settingsRepository.setSessionFavorite(
                serverId = serverId,
                sessionId = sessionId,
                favorite = sessionId !in favorites,
                snapshot = session?.let(FavoriteSessionSnapshot::from),
            )
        }
    }

    fun moveFavorite(sessionId: String, offset: Int) {
        viewModelScope.launch {
            settingsRepository.moveFavoriteSession(serverId, sessionId, offset)
        }
    }

    fun togglePin(sessionId: String) {
        val pinned = pinnedSessionIds.value
        viewModelScope.launch {
            settingsRepository.setSessionPinned(
                serverId = serverId,
                sessionId = sessionId,
                pinned = sessionId !in pinned,
            )
        }
    }

    fun movePinned(sessionId: String, offset: Int) {
        viewModelScope.launch {
            settingsRepository.movePinnedSession(serverId, sessionId, offset)
        }
    }

    /**
     * 拖拽排序后整体写入置顶会话的新顺序。
     */
    fun reorderPinned(orderedIds: List<String>) {
        viewModelScope.launch {
            settingsRepository.reorderPinnedSessions(serverId, orderedIds)
        }
    }

    fun setSessionCategory(sessionId: String, categoryId: String?) {
        viewModelScope.launch {
            settingsRepository.setSessionCategory(serverId, sessionId, categoryId)
        }
    }

    fun saveSessionCategory(id: String?, name: String, color: String, icon: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            settingsRepository.saveSessionCategory(
                SessionCategory(
                    id = id ?: java.util.UUID.randomUUID().toString(),
                    name = trimmed,
                    color = color,
                    icon = icon,
                ),
            )
        }
    }

    fun deleteSessionCategory(categoryId: String) {
        viewModelScope.launch { settingsRepository.deleteSessionCategory(categoryId) }
    }

    fun loadSessions() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // Load all projects first (for grouping/status refresh)
                val projects = api.listProjects(conn)
                _projects.value = projects
                if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${projects.size} projects")

                // The server's /experimental/session endpoint lists root sessions across ALL projects.
                val sessions = api.listSessions(conn)
                eventReducer.setSessions(serverId, sessions)
                if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${sessions.size} sessions for server $serverId")

                refreshSessionStatuses(projects)
                refreshPendingQuestions()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to load sessions", e)
                _error.value = e.message ?: "Failed to load sessions"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun refreshSessionStatuses(projects: List<Project> = _projects.value) {
        val serverSessionIds = eventReducer.serverSessions.value[serverId].orEmpty()
        try {
            val sessionIds = eventReducer.sessions.value.asSequence()
                .filter { it.id in serverSessionIds }
                .map { it.id }
                .toSet()
            val statuses = api.listSessionStatuses(conn)
            val connected = connectionStateRepository.connectedServerIds.value.contains(serverId)
            eventReducer.replaceSessionStatuses(serverId, sessionIds, statuses, connected)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (BuildConfig.DEBUG) Log.d(TAG, "Failed to refresh session statuses: ${e::class.java.simpleName}")
        }
    }

    /** 拉取当前服务器全部待决问题，按 sessionId 分组后刷新「待回答」会话集合。 */
    private suspend fun refreshPendingQuestions() {
        try {
            // 服务器 /question 按 query 参数 directory 过滤，按会话目录分组聚合查询。
            val serverSessionIds = eventReducer.serverSessions.value[serverId].orEmpty()
            val directories = eventReducer.sessions.value.asSequence()
                .filter { it.id in serverSessionIds }
                .map { it.directory }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()
            val requests = directories.flatMap { dir ->
                runCatching { api.listPendingQuestions(conn, directory = dir) }
                    .getOrElse { e ->
                        if (e is CancellationException) throw e
                        if (BuildConfig.DEBUG) Log.d(TAG, "Failed to load pending questions for $dir: ${e.message}")
                        emptyList()
                    }
            }
            _pendingQuestionSessionIds.value = requests.mapTo(mutableSetOf<String>()) { it.sessionId }
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "Pending questions: ${requests.size} requests across ${_pendingQuestionSessionIds.value.size} sessions",
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (BuildConfig.DEBUG) Log.d(TAG, "Failed to refresh pending questions: ${e::class.java.simpleName}")
        }
    }

    private fun loadProjects() {
        viewModelScope.launch {
            try {
                val projects = api.listProjects(conn)
                _projects.value = projects
                if (BuildConfig.DEBUG) Log.d(TAG, "Loaded ${projects.size} projects")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to load projects", e)
            }
        }
    }

    private fun loadHomeDir() {
        viewModelScope.launch {
            getHomeDirectory()
        }
    }

    fun createNewSession(directory: String? = null) {
        viewModelScope.launch {
            try {
                val session = api.createSession(conn, directory = directory)
                // The SSE stream should pick up the new session, but also add directly.
                // Use upsert (merge) so a single new session doesn't wipe the rest of the list.
                eventReducer.upsertSession(serverId, session)
                if (BuildConfig.DEBUG) Log.d(TAG, "Created new session: ${session.id}")
                directory?.let { settingsRepository.recordRecentProject(serverId, it) }
                _navigateToSession.tryEmit(session.id)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to create session", e)
                _error.value = e.message ?: "Failed to create session"
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                val success = api.deleteSession(conn, sessionId)
                if (success) {
                    settingsRepository.setSessionFavorite(serverId, sessionId, false)
                    settingsRepository.setSessionPinned(serverId, sessionId, false)
                    settingsRepository.setSessionCategory(serverId, sessionId, null)
                    // Remove the session from in-memory state immediately so it disappears
                    // from the list even if the SSE SessionDeleted event is delayed or missing.
                    eventReducer.removeSession(sessionId)
                    if (BuildConfig.DEBUG) Log.d(TAG, "Deleted session $sessionId")
                    loadSessions()
                } else {
                    _error.value = "Failed to delete session"
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to delete session", e)
                _error.value = e.message ?: "Failed to delete session"
            }
        }
    }

    fun toggleSelection(sessionId: String) {
        _selectedIds.update { selected ->
            if (sessionId in selected) selected - sessionId else selected + sessionId
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun selectAll() {
        val allIds = uiState.value.sessionGroups
            .flatMap { group -> group.sessions.map { it.session.id } }
            .toSet()
        _selectedIds.value = allIds
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val ids = _selectedIds.value
            if (ids.isEmpty()) return@launch
            try {
                val results = coroutineScope {
                    ids.map { id ->
                        async {
                            id to api.deleteSession(conn, id)
                        }
                    }.awaitAll()
                }
                val failed = results.filterNot { it.second }
                results.filter { it.second }.forEach { (id, _) ->
                    settingsRepository.setSessionFavorite(serverId, id, false)
                    settingsRepository.setSessionPinned(serverId, id, false)
                    settingsRepository.setSessionCategory(serverId, id, null)
                }
                if (failed.isNotEmpty()) {
                    _error.value = "Failed to delete ${failed.size} session(s)"
                }
                clearSelection()
                loadSessions()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to delete selected sessions", e)
                _error.value = e.message ?: "Failed to delete selected sessions"
            }
        }
    }

    fun archiveSession(sessionId: String) {
        viewModelScope.launch {
            try {
                api.updateSession(conn, sessionId, archive = true)
                if (BuildConfig.DEBUG) Log.d(TAG, "Archived session $sessionId")
                loadSessions()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to archive session", e)
                _error.value = e.message ?: "Failed to archive session"
            }
        }
    }

    fun archiveSelected() {
        viewModelScope.launch {
            val ids = _selectedIds.value
            if (ids.isEmpty()) return@launch
            try {
                val results = coroutineScope {
                    ids.map { id ->
                        async {
                            id to runCatching { api.updateSession(conn, id, archive = true) }
                        }
                    }.awaitAll()
                }
                val failed = results.filter { it.second.isFailure }
                if (failed.isNotEmpty()) {
                    _error.value = "Failed to archive ${failed.size} session(s)"
                }
                clearSelection()
                loadSessions()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to archive selected sessions", e)
                _error.value = e.message ?: "Failed to archive selected sessions"
            }
        }
    }

    fun recordRecentProject(directory: String) {
        if (directory.isBlank()) return
        viewModelScope.launch {
            settingsRepository.recordRecentProject(serverId, directory)
        }
    }

    /**
     * 打开「最近项目」里的某个目录：优先跳转到该目录下最近活跃的会话，
     * 仅当该目录下没有任何现存会话时才新建一个（区别于 [createNewSession] 的无条件新建）。
     */
    fun openRecentProject(directory: String) {
        if (directory.isBlank()) return
        val normalized = directory.trimEnd('/')
        val target = uiState.value.sessionGroups
            .firstOrNull { it.directory.trimEnd('/') == normalized }
            ?.sessions
            ?.maxByOrNull { it.lastUserMessageAt }
        if (target != null) {
            recordRecentProject(directory)
            _navigateToSession.tryEmit(target.session.id)
        } else {
            createNewSession(directory)
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        viewModelScope.launch {
            try {
                api.updateSession(conn, sessionId, newTitle)
                if (BuildConfig.DEBUG) Log.d(TAG, "Renamed session $sessionId to '$newTitle'")
                loadSessions()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to rename session", e)
                _error.value = e.message ?: "Failed to rename session"
            }
        }
    }

    // ============ Directory browsing for Open Project ============

    /** Get the server's home directory (cached). */
    suspend fun getHomeDirectory(): String {
        _homeDir.value?.let { return it }
        return try {
            val paths = api.getServerPaths(conn)
            val home = paths.home
            _homeDir.value = home
            if (BuildConfig.DEBUG) Log.d(TAG, "Server home directory resolved")
            home
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to get server paths", e)
            "/"
        }
    }

    /** List directories in a given path on the server. */
    suspend fun listDirectories(directory: String): List<FileNode> {
        return try {
            val nodes = api.listDirectory(conn, path = "", directory = directory)
            nodes.filter { it.type == "directory" }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to list directory", e)
            emptyList()
        }
    }

    /** Search names fuzzily, but resolve typed absolute and home-relative paths like the WebUI. */
    suspend fun searchDirectories(query: String, directory: String): List<String> {
        val pathQuery = parseDirectoryPathQuery(query, directory)
        if (pathQuery != null) {
            val parentNodes = listDirectories(pathQuery.parent)
            if (pathQuery.segment.isEmpty()) {
                return (listOf(pathQuery.parent) + parentNodes.map { node ->
                    node.absolute ?: joinDirectory(pathQuery.parent, node.name)
                }).distinct()
            }

            val matches = parentNodes.filter { node ->
                node.name.contains(pathQuery.segment, ignoreCase = true)
            }
            val exact = matches.firstOrNull { it.name.equals(pathQuery.segment, ignoreCase = true) }
            if (exact != null) {
                val exactPath = exact.absolute ?: joinDirectory(pathQuery.parent, exact.name)
                val children = listDirectories(exactPath).map { node ->
                    node.absolute ?: joinDirectory(exactPath, node.name)
                }
                return (listOf(exactPath) + children).distinct()
            }
            return matches.map { it.absolute ?: joinDirectory(pathQuery.parent, it.name) }.distinct()
        }

        return try {
            api.findFiles(conn, query = query, type = "directory", directory = directory, limit = 50)
                .map { result ->
                    if (result.startsWith('/')) result.trimEnd('/')
                    else joinDirectory(directory, result)
                }
                .distinct()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to search directories", e)
            emptyList()
        }
    }

    private fun joinDirectory(parent: String, child: String): String =
        if (parent == "/") "/${child.trim('/')}" else "${parent.trimEnd('/')}/${child.trim('/')}"

    /** Create a directory inside the currently browsed path. */
    suspend fun createDirectory(parentDirectory: String, folderName: String): Result<String> {
        val sanitized = folderName.trim().trim('/').replace(Regex("/+"), "/")
        if (sanitized.isBlank() || sanitized == "." || sanitized == "..") {
            return Result.failure(IllegalArgumentException("Invalid folder name"))
        }

        return runCatching {
            val targetDirectory = if (parentDirectory == "/") {
                "/$sanitized"
            } else {
                "${parentDirectory.trimEnd('/')}/$sanitized"
            }

            val tempSession = api.createSession(
                conn = conn,
                title = "mkdir",
                directory = parentDirectory,
            )

            try {
                val escaped = sanitized.replace("'", "'\"'\"'")
                val command = "mkdir -p -- '$escaped'"

                val runShellOk = runCatching {
                    api.runShellCommand(
                        conn = conn,
                        sessionId = tempSession.id,
                        command = command,
                        agent = "build",
                        directory = parentDirectory,
                    )
                }.getOrElse { false }

                if (!runShellOk) {
                    val executeOk = api.executeCommand(
                        conn = conn,
                        sessionId = tempSession.id,
                        command = "bash",
                        arguments = "-lc \"$command\"",
                        directory = parentDirectory,
                    )
                    if (!executeOk) {
                        throw IllegalStateException("Failed to create directory")
                    }
                }
            } finally {
                runCatching { api.deleteSession(conn, tempSession.id) }
            }

            repeat(6) {
                if (directoryExists(targetDirectory)) {
                    return@runCatching targetDirectory
                }
                delay(200)
            }

            throw IllegalStateException("Directory was not created")
        }
    }

    private suspend fun directoryExists(directory: String): Boolean {
        return try {
            api.listDirectory(conn, path = "", directory = directory)
            true
        } catch (_: Exception) {
            false
        }
    }
}
