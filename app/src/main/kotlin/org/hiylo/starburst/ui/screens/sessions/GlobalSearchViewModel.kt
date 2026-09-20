/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : GlobalSearchViewModel.kt
 * Date : 2026/09/09 17:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import org.hiylo.starburst.data.repository.DirectoryScope
import org.hiylo.starburst.data.repository.EventReducer
import org.hiylo.starburst.data.repository.ServerConnectionStateRepository
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.data.repository.SettingsRepository
import org.hiylo.starburst.data.repository.sessionCategories
import org.hiylo.starburst.data.repository.sessionCategoryAssignments
import org.hiylo.starburst.domain.model.Project
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.domain.model.Session
import org.hiylo.starburst.domain.model.SessionCategory
import org.hiylo.starburst.domain.model.SessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

/**
 * 全局搜索结果项：跨服务器的单个根会话，附带归属服务器、项目与连接信息。
 */
data class GlobalSearchItem(
    val server: ServerConfig,
    val session: Session,
    val status: SessionStatus,
    val category: SessionCategory?,
    val projectName: String,
    val branch: String?,
    val isConnected: Boolean,
)

/** 全局搜索时间过滤范围。 */
enum class GlobalSearchTimeFilter {
    All,
    Today,
    LastWeek,
    LastMonth,
}

data class GlobalSearchUiState(
    val items: List<GlobalSearchItem> = emptyList(),
    val serverCount: Int = 0,
    val connectedServerCount: Int = 0,
)

private data class GlobalSearchPreferences(
    val categoryAssignments: Map<String, String>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
    private val eventReducer: EventReducer,
    private val connectionStateRepository: ServerConnectionStateRepository,
) : ViewModel() {

    private val preferencesByServer: Flow<Map<String, GlobalSearchPreferences>> =
        serverRepository.servers.flatMapLatest { servers ->
            if (servers.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    servers.map { server ->
                        settingsRepository.sessionCategoryAssignments(server.id).map { assignments ->
                            server.id to GlobalSearchPreferences(assignments)
                        }
                    },
                ) { values -> values.toMap() }
            }
        }

    val uiState = combine(
        listOf(
            serverRepository.servers,
            eventReducer.sessions,
            eventReducer.serverSessions,
            eventReducer.sessionStatuses,
            eventReducer.projectInfo,
            eventReducer.vcsBranches,
            settingsRepository.sessionCategories,
            preferencesByServer,
            connectionStateRepository.connectedServerIds,
        )
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        buildGlobalSearchState(
            servers = values[0] as List<ServerConfig>,
            sessions = values[1] as List<Session>,
            serverSessions = values[2] as Map<String, Set<String>>,
            statuses = values[3] as Map<String, SessionStatus>,
            projectInfo = values[4] as Map<DirectoryScope, Project>,
            branches = values[5] as Map<DirectoryScope, String?>,
            categories = values[6] as List<SessionCategory>,
            preferences = values[7] as Map<String, GlobalSearchPreferences>,
            connectedIds = values[8] as Set<String>,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        GlobalSearchUiState(),
    )
}

private fun buildGlobalSearchState(
    servers: List<ServerConfig>,
    sessions: List<Session>,
    serverSessions: Map<String, Set<String>>,
    statuses: Map<String, SessionStatus>,
    projectInfo: Map<DirectoryScope, Project>,
    branches: Map<DirectoryScope, String?>,
    categories: List<SessionCategory>,
    preferences: Map<String, GlobalSearchPreferences>,
    connectedIds: Set<String>,
): GlobalSearchUiState {
    val sessionsById = sessions.associateBy(Session::id)
    val categoriesById = categories.associateBy(SessionCategory::id)
    val items = servers.asSequence()
        .flatMap { server ->
            val serverPreferences = preferences[server.id] ?: GlobalSearchPreferences(emptyMap())
            val isConnected = server.id in connectedIds
            serverSessions[server.id].orEmpty()
                .asSequence()
                .mapNotNull { sessionId ->
                    val session = sessionsById[sessionId] ?: return@mapNotNull null
                    session.takeUnless { it.isArchived || it.parentId != null } ?: return@mapNotNull null
                    val scope = DirectoryScope(server.id, session.directory)
                    val projectName = projectInfo[scope]?.displayName
                        ?: session.directory.trimEnd('/').substringAfterLast('/').ifEmpty { "/" }
                    GlobalSearchItem(
                        server = server,
                        session = session,
                        status = statuses[sessionId] ?: SessionStatus.Idle,
                        category = serverPreferences.categoryAssignments[sessionId]?.let(categoriesById::get),
                        projectName = projectName,
                        branch = branches[scope],
                        isConnected = isConnected,
                    )
                }
        }
        .sortedByDescending { it.session.time.updated }
        .toList()
    return GlobalSearchUiState(
        items = items,
        serverCount = servers.size,
        connectedServerCount = connectedIds.size,
    )
}

/**
 * 按关键词与时间范围过滤全局搜索结果。
 * 关键词匹配标题、目录、项目名、分支与 session id；时间过滤基于更新时间。
 */
internal fun filterGlobalSearchItems(
    items: List<GlobalSearchItem>,
    query: String,
    timeFilter: GlobalSearchTimeFilter,
): List<GlobalSearchItem> {
    val q = query.trim()
    val cutoff = when (timeFilter) {
        GlobalSearchTimeFilter.All -> Long.MIN_VALUE
        GlobalSearchTimeFilter.Today -> {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        GlobalSearchTimeFilter.LastWeek -> System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        GlobalSearchTimeFilter.LastMonth -> System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
    }
    return items.filter { item ->
        item.session.time.updated >= cutoff && (q.isEmpty() || item.matchesQuery(q))
    }
}

private fun GlobalSearchItem.matchesQuery(q: String): Boolean =
    session.title?.contains(q, ignoreCase = true) == true ||
        session.id.contains(q, ignoreCase = true) ||
        session.directory.contains(q, ignoreCase = true) ||
        projectName.contains(q, ignoreCase = true) ||
        branch?.contains(q, ignoreCase = true) == true ||
        server.displayName.contains(q, ignoreCase = true) ||
        server.url.contains(q, ignoreCase = true)
