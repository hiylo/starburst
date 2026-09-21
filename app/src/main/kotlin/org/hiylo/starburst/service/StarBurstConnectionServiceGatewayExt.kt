/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : StarBurstConnectionServiceGatewayExt.kt
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
import android.os.Looper
import org.hiylo.starburst.data.api.BackendStatus
import org.hiylo.starburst.data.api.OpenCodeGateway
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.data.backend.PushSessionEvent
import org.hiylo.starburst.data.api.listSessionStatusesForDirectories
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.domain.model.SessionStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update

/**
 * Dual-channel gateway wiring and the starburst-backend push channel:
 * mirror connection build, /api/ws session-event handling, the fallback to a
 * direct opencode connection and the debounced session-status refresh.
 * Extracted verbatim from StarBurstConnectionService as extension functions;
 * no behaviour change.
 */

internal fun StarBurstConnectionService.getServerConnection(server: ServerConfig): ServerConnection? {
    return connections[server.id]?.conn
}

/**
 * 双通道网关：服务器配置了 starburst-backend 且后端存活时，把连接切到
 * `${backendUrl}/api/opencode` 镜像（Bearer 后端 token）；否则原样直连 opencode。
 * 探测有 2.5s 总超时；主线程或后端不可达时安全回退到直连。
 */
internal fun StarBurstConnectionService.buildGatewayConn(server: ServerConfig, baseConn: ServerConnection, backendLocalPort: Int?): ServerConnection {
    val backendUrl = resolveBackendUrl(server, backendLocalPort).trim().trimEnd('/').takeIf { it.isNotBlank() }
    if (backendUrl.isNullOrBlank()) {
        Log.w(TAG, "[${server.displayName}] buildGatewayConn: no backend URL (backendLocalPort=$backendLocalPort, backendUrl=${server.backendUrl}), staying direct")
        return baseConn
    }
    val backendToken = server.backendResolvedToken.trim().takeIf { it.isNotBlank() }
    if (backendToken.isNullOrBlank()) {
        Log.w(TAG, "[${server.displayName}] buildGatewayConn: backend token empty, staying direct")
        return baseConn
    }
    if (Looper.myLooper() == Looper.getMainLooper()) {
        Log.w(TAG, "[${server.displayName}] buildGatewayConn: on main looper, staying direct")
        return baseConn
    }
    val start = SystemClock.elapsedRealtime()
    val result = runCatching {
        runBlocking {
            withTimeoutOrNull(2_500L) {
                if (!backendApi.isHealthy(backendUrl)) {
                    Log.w(TAG, "[${server.displayName}] buildGatewayConn: /api/health NOT healthy at $backendUrl")
                    return@withTimeoutOrNull baseConn
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "[${server.displayName}] buildGatewayConn: /api/health ok at $backendUrl")
                // token 无效时 /api/system 鉴权失败返回 {"error":"invalid token"}，会反序列化为
                // 字段为空的 BackendSystemInfo（非 null），因此须校验关键字段而非仅判空：
                // 不切换镜像，保持直连，避免用一个注定鉴权失败的镜像地址建立 SSE。
                val info = backendApi.getSystemInfo(backendUrl, backendToken)
                if (info == null || info.backend.isBlank()) {
                    Log.w(TAG, "[${server.displayName}] buildGatewayConn: /api/system invalid or backend blank at $backendUrl")
                    return@withTimeoutOrNull baseConn
                }
                OpenCodeGateway.resolve(
                    baseConn,
                    BackendStatus(
                        backendUrl = backendUrl,
                        backendToken = backendToken,
                        backendAvailable = true,
                        backendVersion = info.version,
                    ),
                )
            } ?: run {
                Log.w(TAG, "[${server.displayName}] buildGatewayConn: probe timed out >2.5s at $backendUrl")
                baseConn
            }
        }
    }.getOrElse { e ->
        Log.w(TAG, "[${server.displayName}] buildGatewayConn: unexpected error at $backendUrl", e)
        baseConn
    }
    Log.i(TAG, "[${server.displayName}] buildGatewayConn: -> ${if (result === baseConn) "DIRECT" else "MIRROR"} ($backendUrl, ${SystemClock.elapsedRealtime() - start}ms)")
    return result
}

/**
 * 需求 2 后端主动推送：当连接已切到后端镜像（后端可用）时，订阅后端 `/api/ws` 的
 * `session.event`，把会话完成 / 提问 / 出错 / 授权事件转成带声音震动的通知。
 * 断线指数退避重连；服务端断开该 server 后自动退出。
 */
internal fun StarBurstConnectionService.startBackendPushJob(server: ServerConfig, conn: ServerConnection, backendLocalPort: Int?): Job? {
    val backendUrl = resolveBackendUrl(server, backendLocalPort).trim().trimEnd('/').takeIf { it.isNotBlank() } ?: return null
    if (!conn.baseUrl.startsWith("$backendUrl${OpenCodeGateway.BACKEND_API_PREFIX}")) return null
    val token = server.backendResolvedToken.trim().takeIf { it.isNotBlank() } ?: return null
    if (BuildConfig.DEBUG) {
        Log.d(TAG, "[${server.displayName}] Backend gateway active, listening /api/ws pushes")
    }
    return serviceScope.launch {
        var backoffMs = 2_000L
        var consecutiveFailures = 0
        while (isActive) {
            if (!connections.containsKey(server.id)) return@launch
            try {
                backendPushListener.eventFlow(backendUrl, token).collect { ev ->
                    handleBackendPushEvent(server, ev)
                }
                // 正常断开（收集器结束/WS 被网关按 idle 掐断）不算故障：
                // 重置失败计数，避免健康后端仅因空闲断流被误判回退直连；
                // 同时重置退避间隔，避免多次空闲断开后 WS 重连永久卡在高倍退避。
                consecutiveFailures = 0
                backoffMs = 2_000L
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                consecutiveFailures++
                Log.w(TAG, "[${server.displayName}] Backend push stream stopped: ${e.message}")
            }
            // 后端连续不可用：回退到直连，保证聊天与会话列表仍然可用。
            if (consecutiveFailures >= BACKEND_FALLBACK_THRESHOLD) {
                Log.w(TAG, "[${server.displayName}] Backend push failing repeatedly, falling back to direct opencode")
                fallbackToDirectConn(server)
                return@launch
            }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
        }
    }
}

/** 后端不可用时把该 server 的连接切回直连 opencode（重建 SSE；push 镜像关闭）。 */
internal fun StarBurstConnectionService.fallbackToDirectConn(server: ServerConfig) {
    // 并发去重：SSE 失联循环与后端推送 job 都会触发 fallback，且 fallback 内部会取消旧 sseJob
    // 并启动新 job；若同时执行会互相取消、反复重建隧道，导致两侧都连不上。
    // 用原子 add 占位：已在回退中则直接返回；回退完成后在 finally 释放，允许后续再次回退。
    if (!fallbackInFlight.add(server.id)) return
    try {
        val state = connections[server.id] ?: return
        val directConn = state.directConn ?: return
        if (state.conn === directConn) return
        val job = startSseConnection(state.config, directConn, preload = false)
        val replacement = state.copy(conn = directConn, sseJob = job, isConnected = false, pushJob = null)
        if (!connections.replace(server.id, state, replacement)) {
            job.cancel()
            return
        }
        // 回退到直连后不再走后端镜像：回收旧推送订阅，避免后端 WS 继续残留。
        state.sseJob.cancel()
        state.pushJob?.cancel()
        reconciliationJobs.remove(server.id)?.cancel()
        _connectedServerIds.update { it - server.id }
        _connectingServerIds.update { it + server.id }
        connectStartedAt[server.id] = SystemClock.elapsedRealtime()
        _serverMetrics.update { it - server.id }
        job.start()
        if (BuildConfig.DEBUG) Log.d(TAG, "[${server.displayName}] Fell back to direct opencode")
    } finally {
        fallbackInFlight.remove(server.id)
    }
}

internal fun StarBurstConnectionService.handleBackendPushEvent(server: ServerConfig, ev: PushSessionEvent) {
    when (ev.eventType) {
        "session.idle" -> {
            // 状态准确：推送驱动，把该会话立即置为空闲（不依赖 SSE/轮询）。
            // 子会话（subagent）的状态同样要写入：会话列表靠 childBusyByParent 把
            // 正在跑子会话的父会话标为「处理中」，子会话 idle 后父会话才能解除该状态。
            // 仅抑制子会话的「回复就绪」通知（与官方 WebUI/TUI 行为一致），不抑制状态写入。
            val child = isChildSession(ev.sessionId)
            eventReducer.updateSessionStatus(ev.sessionId, SessionStatus.Idle)
            if (child) return
            serviceScope.launch {
                delay(250)
                notifySessionComplete(server, ev.sessionId)
            }
        }
        "session.status", "session.updated" -> {
            // 状态准确：以推送事件的 status 为准（/session/status 快照可能不全），
            // 立即写入 eventReducer，聊天与会话列表实时反映。
            // 子会话的 busy/retry 同样写入——这是「运行子会话时父会话显示处理中」
            // 的实时数据源（SSE 偶发丢帧或重连间隙时，推送是兜底通道）。
            // 加单调守卫：聚合快照可能滞后，旧 idle 不得抢跑真实 busy/retry/question，
            // 避免运行中会话被误标完成并触发 markUnconfirmedCompleted/完成通知。
            val child = ev.sessionId.isNotBlank() && isChildSession(ev.sessionId)
            val status = ev.status()
            if (status != null && shouldApplyPushStatus(eventReducer.sessionStatuses.value[ev.sessionId], status)) {
                eventReducer.updateSessionStatus(ev.sessionId, status)
            }
            refreshSessionStatusesSoon(server)
            if (child) return
        }
        "question.asked", "question.updated" -> {
            if (isChildSession(ev.sessionId)) return
            val questionText = ev.questionText()
                ?: getString(R.string.notification_has_question, getString(R.string.notification_new_session))
            showQuestionNotification(server, ev.sessionId, questionText)
        }
        "question.replied", "question.rejected" -> {
            // web 端用 opencode 通道选中/拒绝问题后，App 走推送通道也要同步清除 pending，
            // 否则会话列表/工作台一直挂着「待回答问题」无法取消。
            if (ev.sessionId.isNotBlank() && isChildSession(ev.sessionId)) return
            val requestId = ev.questionId().orEmpty()
            if (requestId.isBlank()) {
                // payload 缺失请求 id 时按会话兜底清空，避免题永久滞留。
                eventReducer.clearPendingForSession(ev.sessionId)
            } else {
                eventReducer.removeQuestion(ev.sessionId, requestId)
            }
        }
        "permission.asked", "permission.updated" -> {
            if (isChildSession(ev.sessionId)) return
            val permission = ev.permission() ?: return
            showPermissionNotification(server, ev.sessionId, permission)
        }
        "permission.replied", "permission.denied", "permission.granted" -> {
            // web 端已授权/拒绝后，App 走推送通道同步清除 pending 授权，避免一直挂着无法取消。
            if (ev.sessionId.isNotBlank() && isChildSession(ev.sessionId)) return
            val requestId = ev.permissionId().orEmpty()
            if (requestId.isBlank()) {
                eventReducer.clearPendingForSession(ev.sessionId)
            } else {
                eventReducer.removePermission(ev.sessionId, requestId)
            }
        }
        "session.error", "session.failed" -> {
            if (ev.sessionId.isNotBlank() && isChildSession(ev.sessionId)) return
            showErrorNotification(server, ev.sessionId.ifBlank { null }, ev.errorMessage()
                ?: getString(R.string.error_unknown))
        }
        else -> {}
    }
}

/**
 * 推送状态单调守卫：Busy / Retry / Question 均视为比 Idle 活跃（更新）。
 * 仅当推送状态不比当前状态「更旧」时才放行写入——聚合快照滞后的旧 Idle
 * 不得覆盖真实 Busy/Retry，避免运行中会话被误标完成并触发完成通知。
 * SSE 直连路径不经过此守卫（SSE 是权威实时源，直接驱动各分支）。
 */
private fun shouldApplyPushStatus(current: SessionStatus?, incoming: SessionStatus): Boolean {
    return when (current) {
        is SessionStatus.Busy, is SessionStatus.Question, is SessionStatus.Retry -> incoming !is SessionStatus.Idle
        else -> true
    }
}

internal fun StarBurstConnectionService.refreshSessionStatusesSoon(server: ServerConfig) {
    val now = System.currentTimeMillis()
    val last = lastStatusRefreshAtByServer[server.id] ?: 0L
    if (now - last < 2_000L) return
    lastStatusRefreshAtByServer[server.id] = now
    serviceScope.launch { refreshSessionStatuses(server) }
}

/** 从权威接口拉一次全量状态并写入 eventReducer（busy/retry 立即反映；其余由 SSE/轮询兜底）。 */
internal suspend fun StarBurstConnectionService.refreshSessionStatuses(server: ServerConfig) {
    val state = connections[server.id] ?: return
    try {
        val serverSessionIds = eventReducer.serverSessions.value[server.id].orEmpty()
        val directories = eventReducer.sessions.value.asSequence()
            .filter { it.id in serverSessionIds }
            .map { it.directory }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
        val statuses = api.listSessionStatusesForDirectories(state.conn, directories)
        statuses.forEach { (sessionId, status) ->
            eventReducer.updateSessionStatus(sessionId, status)
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "[${server.displayName}] Push-triggered status refresh: ${statuses.size} active sessions")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "[${server.displayName}] Status refresh failed: ${e.message}")
    }
}

