/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : WidgetSnapshotWriter.kt
 * Date : 2026/09/12 20:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.3.0
 */
package org.hiylo.starburst.widget

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.hiylo.starburst.data.repository.BackendRepository
import org.hiylo.starburst.data.repository.EventReducer
import org.hiylo.starburst.data.repository.ServerConnectionStateRepository
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.domain.model.BackendTask
import org.hiylo.starburst.domain.model.BackendTaskStatus
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.domain.model.Session
import org.hiylo.starburst.domain.model.SessionStatus
import javax.inject.Inject
import javax.inject.Singleton

/** Widget 上最多展示的「有新消息」会话条数。 */
private const val MAX_ACTIVE_SESSIONS = 5

/** Widget 刷新去抖间隔。 */
private const val REFRESH_DEBOUNCE_MS = 300L

/**
 * 把应用内的服务器/会话/任务状态持续同步到 [WidgetSnapshotStore]，供桌面 Widget 渲染。
 *
 * 观察：
 *  - [ServerRepository.servers] + [ServerConnectionStateRepository.connectedServerIds] → 服务器状态
 *  - [EventReducer.sessions] + [EventReducer.serverSessions] → 各服务器最近会话
 *  - [BackendRepository.tasks] → 各服务器任务统计
 *
 * 任一变化即重算快照并请求 Widget 刷新（带节流，避免高频事件打爆渲染）。
 *
 * @author Hsi Chu
 * @since V1.3.0
 */
@Singleton
class WidgetSnapshotWriter @Inject constructor(
    private val store: WidgetSnapshotStore,
    private val serverRepository: ServerRepository,
    private val connectionStateRepository: ServerConnectionStateRepository,
    private val eventReducer: EventReducer,
    private val backendRepository: BackendRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshJob: Job? = null
    private var pending = false

    private data class RebuildData(
        val servers: List<ServerConfig>,
        val connectedIds: Set<String>,
        val sessions: List<Session>,
        val serverSessions: Map<String, Set<String>>,
        val statuses: Map<String, SessionStatus>,
        val unconfirmed: Map<String, Long>,
        val tasksByServer: Map<String, List<BackendTask>>,
    )

    /** 启动观察（幂等）。通常在 [org.hiylo.starburst.MainActivity] 启动后调用。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            serverRepository.servers
                .flatMapLatest { servers ->
                    val taskFlows = servers.map { server ->
                        backendRepository.tasks(server.id)
                            .map { tasks -> server.id to tasks }
                    }
                    @Suppress("UNCHECKED_CAST")
                    combine(
                        listOf(
                            connectionStateRepository.connectedServerIds,
                            eventReducer.sessions,
                            eventReducer.serverSessions,
                            eventReducer.sessionStatuses,
                            eventReducer.unconfirmedCompletedSessions,
                            combine(taskFlows) { pairs -> pairs.toMap() },
                        ),
                    ) { values ->
                        RebuildData(
                            servers = servers,
                            connectedIds = values[0] as Set<String>,
                            sessions = values[1] as List<Session>,
                            serverSessions = values[2] as Map<String, Set<String>>,
                            statuses = values[3] as Map<String, SessionStatus>,
                            unconfirmed = values[4] as Map<String, Long>,
                            tasksByServer = values[5] as Map<String, List<BackendTask>>,
                        )
                    }
                }
                .distinctUntilChanged()
                .collect { data ->
                    writeSnapshot(data)
                    scheduleRefresh()
                }
        }
    }

    private fun writeSnapshot(data: RebuildData) {
        val (servers, connectedIds, sessions, serverSessions, statuses, unconfirmed, tasksByServer) = data

        val serverInfos = servers.map { server ->
            WidgetServerInfo(
                id = server.id,
                name = server.displayName,
                url = server.url,
                connected = server.id in connectedIds,
            )
        }

        // 「有新消息/活动」的会话 = ① Busy/Retry（正在生成回复）② 未读完成（Busy→Idle 且用户未打开）。
        // 排序：未读完成 + 活动会话优先，其次最近更新；仅展示有 server 归属的根会话。
        val activeSessionIds = sessions.asSequence()
            .filter { it.parentId == null && !it.isArchived }
            .filter { session -> serverSessions.values.any { ids -> ids.contains(session.id) } }
            .filter { session ->
                val status = statuses[session.id]
                status is SessionStatus.Busy || status is SessionStatus.Retry ||
                    session.id in unconfirmed
            }
            .sortedWith(
                compareByDescending<Session> { it.id in unconfirmed }
                    .thenByDescending { it.time.updated },
            )
            .map { it.id }
            .toList()

        val active = activeSessionIds
            .take(MAX_ACTIVE_SESSIONS)
            .mapNotNull { id -> sessions.firstOrNull { it.id == id } }
            .map { session ->
                val server = serverInfos.firstOrNull { s ->
                    serverSessions[s.id]?.contains(session.id) == true
                }
                WidgetSessionInfo(
                    serverId = server?.id ?: "",
                    serverName = server?.name ?: "",
                    sessionId = session.id,
                    title = session.title.orEmpty().ifBlank { session.id },
                    directory = session.directory,
                    updatedAt = session.time.updated,
                )
            }
            .toList()

        val taskCounts = servers.associate { server ->
            val tasks = tasksByServer[server.id].orEmpty()
            server.id to WidgetTaskCounts(
                running = tasks.count { it.status == BackendTaskStatus.Running },
                queued = tasks.count { it.status == BackendTaskStatus.Queued },
                scheduled = tasks.count { it.status == BackendTaskStatus.Scheduled },
                failed = tasks.count { it.status == BackendTaskStatus.Failed },
            )
        }

        store.write(
            WidgetSnapshot(
                servers = serverInfos,
                activeSessions = active,
                taskCounts = taskCounts,
            ),
        )
    }

    /** 节流请求 Widget 刷新（合并高频事件）。 */
    private fun scheduleRefresh() {
        synchronized(this) {
            if (pending) return
            pending = true
        }
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            try {
                kotlinx.coroutines.delay(REFRESH_DEBOUNCE_MS)
                store.requestUpdate()
            } finally {
                synchronized(this@WidgetSnapshotWriter) { pending = false }
            }
        }
    }

    @Volatile
    private var started = false
}