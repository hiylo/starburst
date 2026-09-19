/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BackendRepository.kt
 * Date : 2026/09/11 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.starburst.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hiylo.starburst.data.api.BackendApi
import org.hiylo.starburst.data.api.BackendPlanDraft
import org.hiylo.starburst.data.api.BackendPlanStepDraft
import org.hiylo.starburst.data.api.BackendStats
import org.hiylo.starburst.data.api.BackendTaskTarget
import org.hiylo.starburst.domain.model.BackendArchive
import org.hiylo.starburst.domain.model.BackendTask
import org.hiylo.starburst.domain.model.BackendTaskStatus
import org.hiylo.starburst.logging.AppLogger as Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BackendRepository"

/** WebSocket 断线后重连的间隔。 */
private const val RECONNECT_BASE_DELAY_MS = 2_000L
private const val RECONNECT_MAX_DELAY_MS = 30_000L

/** 新任务事件触发列表刷新的去抖间隔。 */
private const val REFRESH_DEBOUNCE_MS = 500L

/**
 * OpenCode Backend 的任务状态仓库。
 *
 * 状态按 [serverId] 隔离：每个 server 拥有独立的任务列表、归档列表、连接态与
 * WebSocket 订阅，避免多服务器场景下串数据、互踩连接。
 *
 * @author Hsi Chu
 * @since V1.3.0
 */
@Singleton
class BackendRepository @Inject constructor(
    private val api: BackendApi,
    private val httpClient: HttpClient,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 每个 server 一份独立状态。 */
    private class ServerState {
        val tasks = MutableStateFlow<List<BackendTask>>(emptyList())
        val archives = MutableStateFlow<List<BackendArchive>>(emptyList())
        val connected = MutableStateFlow(false)
        var wsJob: Job? = null
        var refreshJob: Job? = null
        var url: String = ""
        var token: String = ""
    }

    private val states = ConcurrentHashMap<String, ServerState>()

    private fun state(serverId: String): ServerState = states.getOrPut(serverId) { ServerState() }

    fun tasks(serverId: String): StateFlow<List<BackendTask>> = state(serverId).tasks

    fun archives(serverId: String): StateFlow<List<BackendArchive>> = state(serverId).archives

    fun connected(serverId: String): StateFlow<Boolean> = state(serverId).connected

    /** 从后端拉取任务列表。 */
    suspend fun loadTasks(serverId: String, backendUrl: String, token: String) {
        val loaded = api.listTasks(backendUrl, token)
        state(serverId).tasks.value = loaded
    }

    /** 创建任务并刷新列表。 */
    suspend fun createTask(
        serverId: String,
        backendUrl: String,
        token: String,
        prompt: String,
        name: String? = null,
        sessionId: String? = null,
        directory: String? = null,
        scheduledAt: String? = null,
        cron: String? = null,
    ): BackendTask {
        val task = api.createTask(backendUrl, token, prompt, name, sessionId, directory, scheduledAt = scheduledAt, cron = cron)
        loadTasks(serverId, backendUrl, token)
        return task
    }

    /**
     * 创建多步骤计划：按顺序创建任务并用 dependsOn 串成依赖链。
     * 只有第一步带调度（scheduledAt/cron），后续步骤等待前置成功。
     */
    suspend fun createPlan(
        serverId: String,
        backendUrl: String,
        token: String,
        name: String,
        steps: List<BackendPlanStepDraft>,
        scheduledAt: String? = null,
        cron: String? = null,
    ): List<BackendTask> {
        val created = mutableListOf<BackendTask>()
        var previousId: String? = null
        steps.forEachIndexed { index, step ->
            val stepName = if (steps.size > 1) "$name · ${index + 1}/${steps.size} ${step.name}".trim() else name.ifBlank { step.name }
            val task = api.createTask(
                backendUrl,
                token,
                step.prompt,
                name = stepName.ifBlank { null },
                directory = step.directory.ifBlank { null },
                dependsOn = previousId,
                scheduledAt = if (index == 0) scheduledAt else null,
                cron = if (index == 0) cron else null,
            )
            created.add(task)
            previousId = task.id
        }
        loadTasks(serverId, backendUrl, token)
        return created
    }

    /** 用自然语言描述生成结构化任务计划草稿（支持基于既有草案的多轮修正）。 */
    suspend fun generatePlan(
        backendUrl: String,
        token: String,
        description: String = "",
        draft: BackendPlanDraft? = null,
        instruction: String = "",
    ): BackendPlanDraft = api.generatePlan(backendUrl, token, description, draft, instruction)

    /** 流式生成任务计划草稿。 */
    suspend fun generatePlanStream(
        backendUrl: String,
        token: String,
        description: String = "",
        draft: BackendPlanDraft? = null,
        instruction: String = "",
        onDelta: (String) -> Unit = {},
    ): Result<BackendPlanDraft> = api.generatePlanStream(backendUrl, token, description, draft, instruction, onDelta)

    /** 用后端已配置的编排 LLM 生成下一步建议（优先于 App 内配置的外部 provider 与端侧 MNN）。 */
    suspend fun generateSuggestions(
        backendUrl: String,
        token: String,
        system: String,
        user: String,
    ): List<String> = api.generateSuggestions(backendUrl, token, system, user)

    /** 批量创建任务并刷新列表。 */
    suspend fun batch(serverId: String, backendUrl: String, token: String, prompt: String, targets: List<BackendTaskTarget>) {
        api.batch(backendUrl, token, prompt, targets)
        loadTasks(serverId, backendUrl, token)
    }

    /** 取消任务。 */
    suspend fun cancelTask(serverId: String, backendUrl: String, token: String, id: String) {
        api.cancelTask(backendUrl, token, id)
        loadTasks(serverId, backendUrl, token)
    }

    /** 手动解阻（blocked → queued）。 */
    suspend fun unblockTask(serverId: String, backendUrl: String, token: String, id: String) {
        api.unblockTask(backendUrl, token, id)
        loadTasks(serverId, backendUrl, token)
    }

    /** 清除已完成任务（succeeded/failed/canceled）并刷新列表。返回删除数量。 */
    suspend fun purgeFinishedTasks(serverId: String, backendUrl: String, token: String): Int {
        val deleted = api.purgeFinishedTasks(backendUrl, token)
        loadTasks(serverId, backendUrl, token)
        return deleted
    }

    /** 拉取归档元数据列表。 */
    suspend fun loadArchives(serverId: String, backendUrl: String, token: String) {
        state(serverId).archives.value = api.listArchives(backendUrl, token)
    }

    /** 归档指定会话并刷新归档列表。 */
    suspend fun archiveSession(serverId: String, backendUrl: String, token: String, sessionId: String, format: String = "markdown") {
        api.archiveSession(backendUrl, token, sessionId, format)
        loadArchives(serverId, backendUrl, token)
    }

    /** 查询单个归档的完整内容。 */
    suspend fun getArchive(backendUrl: String, token: String, id: String): BackendArchive =
        api.getArchive(backendUrl, token, id)

    /** 读取后端用量统计（任务计数 + token 调用量 + 归档数）。 */
    suspend fun getStats(backendUrl: String, token: String): BackendStats =
        api.getStats(backendUrl, token)

    /** 删除归档并刷新归档列表。 */
    suspend fun deleteArchive(serverId: String, backendUrl: String, token: String, id: String) {
        api.deleteArchive(backendUrl, token, id)
        loadArchives(serverId, backendUrl, token)
    }

    /** 连接 WebSocket 订阅任务事件。幂等：重复调用先断开旧连接。断线后自动重连。 */
    fun connectWs(serverId: String, backendUrl: String, token: String) {
        val st = state(serverId)
        disconnectWs(serverId)
        st.url = backendUrl
        st.token = token
        // token 走 Authorization 头而非 URL 查询串：避免明文凭据落代理/访问日志；
        // https 后端对应 wss（不能简单 replaceFirst("http","ws")，否则得到非法的 wsps://）。
        val wsUrl = backendUrl.trimEnd('/')
            .let { if (it.startsWith("https://")) it.replaceFirst("https://", "wss://") else it.replaceFirst("http://", "ws://") } +
            "/api/ws"
        st.wsJob = scope.launch {
            var attempt = 0
            while (isActive) {
                try {
                    httpClient.webSocket(wsUrl, request = {
                        header(io.ktor.http.HttpHeaders.Authorization, "Bearer $token")
                    }) {
                        st.connected.value = true
                        attempt = 0 // 连上后重置退避
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                handleWsMessage(serverId, frame.readText())
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Backend WebSocket error: ${e.message}")
                } finally {
                    st.connected.value = false
                }
                if (isActive) {
                    // 指数退避 + 抖动：VPN 抖动时避免每 3s 固定敲门（重连风暴）。
                    val backoff = (RECONNECT_BASE_DELAY_MS * (1L shl attempt.coerceAtMost(4)))
                        .coerceAtMost(RECONNECT_MAX_DELAY_MS)
                    attempt++
                    delay((backoff * (0.75 + Random.nextDouble() * 0.5)).toLong().coerceAtLeast(200L))
                }
            }
        }
    }

    /** 断开指定 server 的 WebSocket。 */
    fun disconnectWs(serverId: String) {
        val st = state(serverId)
        st.wsJob?.cancel()
        st.wsJob = null
        st.refreshJob?.cancel()
        st.refreshJob = null
        st.connected.value = false
    }

    private fun handleWsMessage(serverId: String, text: String) {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val type = obj["type"]?.jsonPrimitive?.content ?: return
        if (type != "task.event") return
        val payload = obj["payload"]?.jsonObject ?: return
        val id = payload["id"]?.jsonPrimitive?.content ?: return
        val status = payload["status"]?.jsonPrimitive?.content ?: return
        val statusEnum = parseStatus(status) ?: return
        val st = state(serverId)
        var unknown = false
        st.tasks.update { current ->
            val idx = current.indexOfFirst { it.id == id }
            if (idx < 0) {
                unknown = true
                current
            } else {
                current.toMutableList().also { it[idx] = it[idx].copy(status = statusEnum) }
            }
        }
        // 新任务（如周期任务到点克隆、其他客户端创建）不在当前列表里，
        // 去抖刷新一次以把它纳入列表。
        if (unknown) {
            scheduleRefresh(serverId)
        }
    }

    private fun scheduleRefresh(serverId: String) {
        val st = state(serverId)
        if (st.url.isEmpty()) return
        val url = st.url
        val token = st.token
        st.refreshJob?.cancel()
        st.refreshJob = scope.launch {
            delay(REFRESH_DEBOUNCE_MS)
            runCatching { api.listTasks(url, token) }.onSuccess { st.tasks.value = it }
        }
    }

    private fun parseStatus(raw: String): BackendTaskStatus? = when (raw.lowercase()) {
        "queued" -> BackendTaskStatus.Queued
        "running" -> BackendTaskStatus.Running
        "succeeded" -> BackendTaskStatus.Succeeded
        "failed" -> BackendTaskStatus.Failed
        "canceled" -> BackendTaskStatus.Canceled
        "retrying" -> BackendTaskStatus.Retrying
        "pending" -> BackendTaskStatus.Pending
        "blocked" -> BackendTaskStatus.Blocked
        "scheduled" -> BackendTaskStatus.Scheduled
        else -> null
    }
}
