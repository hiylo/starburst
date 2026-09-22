/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : StarBurstConnectionServiceSseExt.kt
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.service

import android.app.*
import android.os.SystemClock
import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.BuildConfig
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.api.listSessions
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.domain.model.SseEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update

/**
 * SSE connection lifecycle for a server: the auto-reconnect loop, the
 * per-server connected/connecting flags and the latency/heartbeat metrics.
 * Extracted verbatim from StarBurstConnectionService as extension functions
 * (same pattern as StarBurstConnectionServiceExt.kt); no behaviour change.
 */

internal fun StarBurstConnectionService.startSseConnection(
    server: ServerConfig,
    conn: ServerConnection,
    preload: Boolean = true,
): Job {
    return serviceScope.launch(start = CoroutineStart.LAZY) {
        val currentJob = coroutineContext[Job]!!
        var attempt = 0
        var failureStartedAt: Long? = SystemClock.elapsedRealtime()
        var preloaded = !preload
        var currentConn = conn

        while (isActive) {
            failureStartedAt?.let { failedSince ->
                if (hasFailedConnectionTimedOut(failedSince, SystemClock.elapsedRealtime())) {
                    Log.w(TAG, "[${server.displayName}] Stopping reconnect after 15 minutes without a connection")
                    _connectionErrors.update { it - server.id }
                    cleanupTerminatedConnection(server.id, currentJob)
                    return@launch
                }
            }
            attempt++
            if (attempt <= 3 || attempt % 10 == 0) {
                Log.i(TAG, "[${server.displayName}] SSE connection attempt #$attempt")
            } else if (BuildConfig.DEBUG) {
                Log.d(TAG, "[${server.displayName}] SSE connection attempt #$attempt")
            }

            // SSH 隧道断开后需重建：首次连接由 connectInternal 建好隧道；之后每次重连前
            // 重建一次，避免隧道已断（SSH 会话超时/网络波动）却仍用失效的
            // 127.0.0.1:localPort 反复 ECONNREFUSED 而永远连不上。
            if (server.useSsh && attempt > 1) {
                try {
                    val resolved = resolveConnection(server)
                    val baseConn = ServerConnection.from(resolved.baseUrl, server.username, server.password)
                    val newConn = buildGatewayConn(server, baseConn, resolved.backendLocalPort)
                    // 直连通道必须指向重建后的新隧道：directConn 陈旧（指向已被 replaceSshSession
                    // 关闭的旧 127.0.0.1:localPort）会让后端镜像失败后的回退直连永远连不上。
                    replaceSshSession(server.id, newConn, resolved.sshSession, resolved.backendLocalPort, baseConn)
                    currentConn = newConn
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "[${server.displayName}] Failed to re-establish SSH tunnel: ${e.message}")
                }
            }

            if (!preloaded) {
                preloaded = true
                // Pre-load once. Reconciliation refreshes state after a successful reconnect.
                try {
                    val sessions = api.listSessions(currentConn)
                    eventReducer.setSessions(server.id, sessions)
                    Log.i(TAG, "[${server.displayName}] Pre-loaded ${sessions.size} sessions")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "[${server.displayName}] Failed to pre-load sessions: ${e.message}")
                }
            }

            try {
                sseClient.connectToGlobalEvents(
                    conn = currentConn,
                    onOpen = {
                        // SSE 流已打开（HTTP 200）即是连接成功的权威信号，无条件标记 connected，
                        // 避免 job 引用因 forceReconnect/恢复流程被替换后 onOpen 被丢弃而卡在 connecting。
                        updateServerConnected(server.id, true, currentJob)
                        // attempt/failureStartedAt 的重置仅对「当前 job」有意义，仍用引用判断。
                        if (connections[server.id]?.sseJob === currentJob) {
                            attempt = 0
                            failureStartedAt = null
                        }
                    },
                )
                    .catch { error ->
                        Log.e(TAG, "[${server.displayName}] SSE stream error", error)
                        updateServerConnected(server.id, false, currentJob)
                        throw error
                    }
                    .collect { scoped ->
                        if (connections[server.id]?.sseJob !== currentJob) return@collect
                        val event = scoped.event
                        if (connections[server.id]?.isConnected != true) {
                            updateServerConnected(server.id, true, currentJob)
                            attempt = 0
                            failureStartedAt = null
                        }
                        processEvent(server, event, scoped.directory, scoped.workspaceId)
                        if (event is SseEvent.ServerConnected) {
                            // 用循环内最新 currentConn（SSH 重连后已指向新隧道端口），
                            // 避免对账打到已被 replaceSshSession 关闭的旧 127.0.0.1:localPort。
                            startReconciliation(server, currentConn)
                        }
                    }

                // Flow completed normally (server closed connection)
                Log.w(TAG, "[${server.displayName}] SSE stream completed")
                updateServerConnected(server.id, false, currentJob)
            } catch (e: CancellationException) {
                if (BuildConfig.DEBUG) Log.d(TAG, "[${server.displayName}] SSE job cancelled, not reconnecting")
                throw e
            } catch (e: org.hiylo.starburst.data.api.SseAuthException) {
                Log.e(TAG, "[${server.displayName}] Authentication failed; automatic reconnect stopped", e)
                _connectionErrors.update { it + (server.id to getString(R.string.home_server_auth_failed)) }
                updateServerConnected(server.id, false, currentJob)
                cleanupTerminatedConnection(server.id, currentJob)
                break
            } catch (e: org.hiylo.starburst.data.api.SseConnectionException) {
                Log.e(TAG, "[${server.displayName}] SSE connection failed: ${e.message}")
                updateServerConnected(server.id, false, currentJob)
                if (!e.retryable) {
                    _connectionErrors.update { it + (server.id to getString(R.string.home_server_not_responding)) }
                    cleanupTerminatedConnection(server.id, currentJob)
                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "[${server.displayName}] SSE connection failed: ${e.message}")
                updateServerConnected(server.id, false, currentJob)
            }

            // If this server was removed from connections, stop the loop
            if (!connections.containsKey(server.id)) break

            // 后端镜像 SSE 连续失败：回退直连 opencode。镜像 SSE 端点异常（网关反复
            // 打开后立即断开）时 REST 消息仍走直连、能正常收发，但 SSE 状态会一直卡在
            // connecting；这里在连续失败达到阈值后主动回退直连，打破无限重连。
            if (attempt >= BACKEND_FALLBACK_THRESHOLD) {
                val st = connections[server.id]
                if (st != null && st.directConn != null && st.directConn !== currentConn) {
                    Log.w(
                        TAG,
                        "[${server.displayName}] Backend-mirrored SSE failing repeatedly (attempt=$attempt), falling back to direct opencode",
                    )
                    fallbackToDirectConn(server)
                    return@launch
                }
            }

            val now = SystemClock.elapsedRealtime()
            val failedSince = failureStartedAt ?: now.also { failureStartedAt = it }
            val delayMs = calculateBackoff(attempt)
            if (attempt <= 3 || attempt % 10 == 0) {
                Log.i(TAG, "[${server.displayName}] Reconnecting in ${delayMs}ms (attempt #$attempt)")
            } else if (BuildConfig.DEBUG) {
                Log.d(TAG, "[${server.displayName}] Reconnecting in ${delayMs}ms (attempt #$attempt)")
            }
            val remainingMs = FAILED_CONNECTION_TIMEOUT_MS - (now - failedSince)
            delay(minOf(delayMs, remainingMs.coerceAtLeast(1L)))
        }
    }
}

internal fun StarBurstConnectionService.cleanupTerminatedConnection(serverId: String, job: Job) {
    synchronized(this) {
        val state = connections[serverId] ?: return
        if (state.sseJob !== job || !connections.remove(serverId, state)) return
        state.pushJob?.cancel()
        closeSshSession(state.sshSession)
        reconciliationJobs.remove(serverId)?.cancel()

        _connectedServerIds.update { it - serverId }
        _connectingServerIds.update { it - serverId }
        eventReducer.clearTransientForServer(serverId)
        clearServerMetrics(serverId)

        publishResolvedConnections()

        if (connections.isEmpty()) {
            stopServiceIfIdle()
        } else {
            updatePersistentNotification()
        }
    }
}

internal fun StarBurstConnectionService.updateServerConnected(serverId: String, connected: Boolean, expectedJob: Job) {
    var transitioned = false
    // flags 与 isConnected 必须在同一原子操作内更新：并发 true/false 交错时，
    // 否则会出现「isConnected=true 但 connectedServerIds 未含该 server」的失步
    // （表现为会话页显示断开、列表页一直 connecting，却仍能正常收发）。
    connections.computeIfPresent(serverId) { _, state ->
        if (state.sseJob !== expectedJob) {
            // 过期 job 的信号：不覆盖当前 job 的 isConnected，但「SSE 已打开」（connected=true）
            // 是连接成功的权威信号，仍应对齐 flags，避免 UI 永久卡在 connecting。
            if (connected) reconcileConnectionFlags(serverId, true)
            return@computeIfPresent state
        }
        if (state.isConnected == connected) {
            // 状态未变，仍对齐 flags，自愈任何历史失步。
            reconcileConnectionFlags(serverId, connected)
            return@computeIfPresent state
        }
        transitioned = true
        reconcileConnectionFlags(serverId, connected)
        state.copy(isConnected = connected)
    }
    if (!transitioned) return
    if (connected) {
        val latencyMs = connectStartedAt.remove(serverId)?.let { SystemClock.elapsedRealtime() - it }
        recordServerHeartbeat(serverId)
        if (latencyMs != null) recordServerLatency(serverId, latencyMs)
    }
    updatePersistentNotification()
}

/** 让 connected/connecting 两个 StateFlow 与 [ServerConnectionState.isConnected] 保持一致。 */
internal fun StarBurstConnectionService.reconcileConnectionFlags(serverId: String, connected: Boolean) {
    if (connected) {
        _connectingServerIds.update { it - serverId }
        _connectedServerIds.update { it + serverId }
    } else {
        _connectedServerIds.update { it - serverId }
        _connectingServerIds.update { it + serverId }
    }
}

internal fun StarBurstConnectionService.recordServerHeartbeat(serverId: String) {
    _serverMetrics.update { current ->
        val existing = current[serverId] ?: ServerConnectionMetrics()
        current + (serverId to existing.copy(lastHeartbeatAt = System.currentTimeMillis()))
    }
}

internal fun StarBurstConnectionService.recordServerLatency(serverId: String, latencyMs: Long) {
    _serverMetrics.update { current ->
        val existing = current[serverId] ?: ServerConnectionMetrics()
        current + (serverId to existing.copy(latencyMs = latencyMs))
    }
}

internal fun StarBurstConnectionService.clearServerMetrics(serverId: String) {
    _serverMetrics.update { it - serverId }
    connectStartedAt.remove(serverId)
}

