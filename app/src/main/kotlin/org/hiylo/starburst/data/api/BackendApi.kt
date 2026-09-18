/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BackendApi.kt
 * Date : 2026/09/11 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.starburst.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.hiylo.starburst.domain.model.BackendArchive
import org.hiylo.starburst.domain.model.BackendTask
import org.hiylo.starburst.domain.model.BackendTaskStatus
import javax.inject.Inject
import javax.inject.Singleton

/** `GET /api/tasks` 的响应包装。 */
@Serializable
internal data class BackendTasksResponse(val tasks: List<BackendTask> = emptyList())

/** `GET /api/stats` 的任务计数统计。 */
@Serializable
data class BackendTaskStats(
    val queued: Int = 0,
    val running: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val canceled: Int = 0,
    val pending: Int = 0,
    val blocked: Int = 0,
    val retried: Int = 0,
    val total: Int = 0,
)

/** `GET /api/stats` 的 token 调用量条目。 */
@Serializable
data class BackendTokenUsage(
    val tokenId: String = "",
    val tokenName: String = "",
    val calls: Int = 0,
)

/** `GET /api/stats` 的响应包装。 */
@Serializable
data class BackendStats(
    val tasks: BackendTaskStats = BackendTaskStats(),
    val tokenUsage: List<BackendTokenUsage> = emptyList(),
    val archives: Int = 0,
    val maxConcurrency: Int = 0,
)

/** `POST /api/tasks` 的请求体。 */
@Serializable
internal data class BackendCreateTaskRequest(
    val prompt: String,
    val name: String? = null,
    val sessionId: String? = null,
    val directory: String? = null,
    val dependsOn: String? = null,
    val scheduledAt: String? = null,
    val cron: String? = null,
)

/** `POST /api/batch` 的响应。 */
@Serializable
internal data class BackendBatchResponse(val created: List<String> = emptyList(), val count: Int = 0)

/** `POST /api/batch` 的请求体。 */
@Serializable
internal data class BackendBatchRequest(
    val prompt: String,
    val targets: List<BackendTaskTarget>,
)

/** 批量任务的单个目标（directory 或 sessionId 二选一）。 */
@Serializable
data class BackendTaskTarget(
    val directory: String? = null,
    val sessionId: String? = null,
)

/** `GET /api/archives` 的响应包装。 */
@Serializable
internal data class BackendArchivesResponse(val archives: List<BackendArchive> = emptyList())

/** `POST /api/archives` 的请求体。 */
@Serializable
internal data class BackendArchiveRequest(val sessionId: String, val format: String = "markdown")

/** `POST /api/archives` 的响应。 */
@Serializable
data class BackendArchiveCreatedResponse(val id: String, val size: Int = 0, val format: String = "markdown")

/** `POST /api/tasks/generate` 的计划步骤草稿。 */
@Serializable
data class BackendPlanStepDraft(
    val name: String = "",
    val prompt: String = "",
    val directory: String = "",
)

/** LLM 识别出的调度意图（type：immediate / delay / at / cron）。 */
@Serializable
data class BackendScheduleDraft(
    val type: String = "immediate",
    val minutes: Int = 0,
    val cron: String = "",
    val at: String = "",
)

/** `POST /api/tasks/generate` 的计划草稿。 */
@Serializable
data class BackendPlanDraft(
    val name: String = "",
    val directory: String = "",
    val steps: List<BackendPlanStepDraft> = emptyList(),
    val schedule: BackendScheduleDraft? = null,
)

/** `POST /api/tasks/generate` 的响应包装。 */
@Serializable
internal data class BackendPlanDraftResponse(val draft: BackendPlanDraft = BackendPlanDraft())

/** `POST /api/tasks/generate` 的请求体（fresh 用 description，refine 用 draft+instruction）。 */
@Serializable
internal data class BackendGenerateRequest(
    val description: String = "",
    val draft: BackendPlanDraft? = null,
    val instruction: String = "",
)

/** `POST /api/tasks/generate?stream=1` 的单个 SSE 事件。 */
@Serializable
internal data class BackendGenerateSseEvent(
    val type: String = "",
    val text: String = "",
    val draft: BackendPlanDraft? = null,
    val message: String = "",
)

/** `POST /api/llm/generate` 的请求体（复用 OpenAI 兼容的 system/user 对）。 */
@Serializable
internal data class BackendLlmGenerateRequest(
    val system: String = "",
    val user: String = "",
)

/** `POST /api/llm/generate` 的响应包装。 */
@Serializable
internal data class BackendLlmGenerateResponse(
    val suggestions: List<String> = emptyList(),
)

/**
 * OpenCode Backend 的 HTTP 客户端，对接后台任务 / 批量接口。
 *
 * 认证使用 `Authorization: Bearer <ocb_...>`；所有端点均以「后端地址 + token」为参数，
 * 不依赖 ServerConnection（后端是独立的服务，与直连 OpenCode 的连接无关）。
 *
 * @author Hsi Chu
 * @since V1.3.0
 */
@Singleton
class BackendApi @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
) {
    /**
     * 探测 starburst-backend 是否已部署并存活（`GET /api/health`）。
     * 网络不可达或非 2xx 均视为不可用。
     */
    suspend fun isHealthy(backendUrl: String): Boolean {
        val url = "${backendUrl.trimEnd('/')}/api/health"
        return try {
            httpClient.get(url) {
                timeout { requestTimeoutMillis = 8_000L }
            }.status.value in 200..299
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 读取后端系统信息（`GET /api/system`）：后端自身版本 + 上游 opencode 版本。
     * 仅 2xx 视为成功；401（token 无效）或其它非 2xx 一律返回 null，避免把错误响应
     * 误反序列化成「版本为空」的可用结果（否则 token 失效时后端功能仍会被误判为可用）。
     */
    suspend fun getSystemInfo(backendUrl: String, token: String): BackendSystemInfo? =
        try {
            val resp = httpClient.get("${backendUrl.trimEnd('/')}/api/system") {
                header("Authorization", "Bearer $token")
                timeout { requestTimeoutMillis = 8_000L }
            }
            if (resp.status.value !in 200..299) null else resp.body()
        } catch (e: Exception) {
            null
        }

    /** `GET /api/unread` 的响应包装（session_id → 是否未读）。 */
    @Serializable
    internal data class BackendUnreadResponse(val unread: Map<String, Boolean> = emptyMap())

    /**
     * 读取后端记录的「有新消息未读」会话集合（`GET /api/unread`）。
     * 该状态由后端统一维护，Web 与 App 共享；任一端点开会话后经 [markSessionRead] 清除。
     */
    suspend fun listUnread(backendUrl: String, token: String): Set<String> {
        val resp: BackendUnreadResponse = httpClient.get("${backendUrl.trimEnd('/')}/api/unread") {
            header("Authorization", "Bearer $token")
            // 非关键状态，短超时：后端不可达时快速失败，不影响会话列表刷新。
            timeout { requestTimeoutMillis = 3_000L }
        }.body()
        return resp.unread.filterValues { it }.keys
    }

    /** 标记某会话已读（`POST /api/unread/{sessionId}`），任一端口开后全端清除未读。 */
    suspend fun markSessionRead(backendUrl: String, token: String, sessionId: String): Boolean {
        val resp: HttpResponse = httpClient.post("${backendUrl.trimEnd('/')}/api/unread/$sessionId") {
            header("Authorization", "Bearer $token")
            timeout { requestTimeoutMillis = 3_000L }
        }
        return resp.status.value in 200..299
    }

    /** 读取用量统计（`GET /api/stats`）：任务计数 + token 调用量 + 归档数。 */
    suspend fun getStats(backendUrl: String, token: String): BackendStats {
        return httpClient.get("${backendUrl.trimEnd('/')}/api/stats") {
            header("Authorization", "Bearer $token")
        }.body()
    }

    /** 列任务（最新在前，最多 50 条）。[status] 可选过滤。 */
    suspend fun listTasks(backendUrl: String, token: String, status: BackendTaskStatus? = null): List<BackendTask> {
        val resp: BackendTasksResponse = httpClient.get("${backendUrl.trimEnd('/')}/api/tasks") {
            header("Authorization", "Bearer $token")
            status?.let { parameter("status", it.name.lowercase()) }
        }.body()
        return resp.tasks
    }

    /** 创建后台任务，返回新任务对象。 */
    suspend fun createTask(
        backendUrl: String,
        token: String,
        prompt: String,
        name: String? = null,
        sessionId: String? = null,
        directory: String? = null,
        dependsOn: String? = null,
        scheduledAt: String? = null,
        cron: String? = null,
    ): BackendTask = httpClient.post("${backendUrl.trimEnd('/')}/api/tasks") {
        header("Authorization", "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(BackendCreateTaskRequest(prompt, name, sessionId, directory, dependsOn, scheduledAt, cron))
    }.body()

    /** 查询单个任务。 */
    suspend fun getTask(backendUrl: String, token: String, id: String): BackendTask =
        httpClient.get("${backendUrl.trimEnd('/')}/api/tasks/$id") {
            header("Authorization", "Bearer $token")
        }.body()

    /** 取消任务（queued/running/pending）。 */
    suspend fun cancelTask(backendUrl: String, token: String, id: String): Boolean {
        val resp: HttpResponse = httpClient.delete("${backendUrl.trimEnd('/')}/api/tasks/$id") {
            header("Authorization", "Bearer $token")
        }
        return resp.status.value in 200..299
    }

    /** `DELETE /api/tasks` 的响应（清除已完成任务）。 */
    @Serializable
    internal data class BackendPurgeResponse(val deleted: Int = 0, val kept: Int = 0)

    /** 清除已完成任务（succeeded/failed/canceled），返回删除数量。 */
    suspend fun purgeFinishedTasks(backendUrl: String, token: String): Int {
        val resp: BackendPurgeResponse = httpClient.delete("${backendUrl.trimEnd('/')}/api/tasks") {
            header("Authorization", "Bearer $token")
        }.body()
        return resp.deleted
    }

    /** 手动解阻（blocked → queued）。 */
    suspend fun unblockTask(backendUrl: String, token: String, id: String): Boolean {
        val resp: HttpResponse = httpClient.post("${backendUrl.trimEnd('/')}/api/tasks/$id") {
            header("Authorization", "Bearer $token")
        }
        return resp.status.value in 200..299
    }

    /** 批量：一条指令对多个 target 建任务。返回新建任务 id 列表。 */
    suspend fun batch(
        backendUrl: String,
        token: String,
        prompt: String,
        targets: List<BackendTaskTarget>,
    ): List<String> = httpClient.post("${backendUrl.trimEnd('/')}/api/batch") {
        header("Authorization", "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(BackendBatchRequest(prompt, targets))
    }.body<BackendBatchResponse>().created

    /** 列出归档元数据（不含内容，最新在前）。 */
    suspend fun listArchives(backendUrl: String, token: String, limit: Int = 50): List<BackendArchive> {
        val resp: BackendArchivesResponse = httpClient.get("${backendUrl.trimEnd('/')}/api/archives") {
            header("Authorization", "Bearer $token")
            parameter("limit", limit)
        }.body()
        return resp.archives
    }

    /** 把远端会话归档到后端存储。返回新建归档的元信息（id/size/format）。 */
    suspend fun archiveSession(
        backendUrl: String,
        token: String,
        sessionId: String,
        format: String = "markdown",
    ): BackendArchiveCreatedResponse = httpClient.post("${backendUrl.trimEnd('/')}/api/archives") {
        header("Authorization", "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(BackendArchiveRequest(sessionId, format))
    }.body()

    /** 查询单个归档（含 content）。 */
    suspend fun getArchive(backendUrl: String, token: String, id: String): BackendArchive =
        httpClient.get("${backendUrl.trimEnd('/')}/api/archives/$id") {
            header("Authorization", "Bearer $token")
        }.body()

    /** 删除归档。 */
    suspend fun deleteArchive(backendUrl: String, token: String, id: String): Boolean {
        val resp: HttpResponse = httpClient.delete("${backendUrl.trimEnd('/')}/api/archives/$id") {
            header("Authorization", "Bearer $token")
        }
        return resp.status.value in 200..299
    }

    /**
     * 用自然语言描述生成结构化任务计划草稿（需后端已配置编排 LLM）。
     * [description] 为首次生成；传 [draft]+[instruction] 则基于既有草案做多轮修正。
     */
    suspend fun generatePlan(
        backendUrl: String,
        token: String,
        description: String = "",
        draft: BackendPlanDraft? = null,
        instruction: String = "",
    ): BackendPlanDraft {
        val resp: BackendPlanDraftResponse = httpClient.post("${backendUrl.trimEnd('/')}/api/tasks/generate") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(BackendGenerateRequest(description, draft, instruction))
        }.body()
        return resp.draft
    }

    /**
     * 流式生成任务计划草稿：边生成边回调 [onDelta] 返回增量文本，
     * 结束时返回解析好的草稿（Result 携带失败信息）。
     */
    suspend fun generatePlanStream(
        backendUrl: String,
        token: String,
        description: String = "",
        draft: BackendPlanDraft? = null,
        instruction: String = "",
        onDelta: (String) -> Unit = {},
    ): Result<BackendPlanDraft> {
        return httpClient.preparePost("${backendUrl.trimEnd('/')}/api/tasks/generate?stream=1") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(BackendGenerateRequest(description, draft, instruction))
        }.execute { response ->
            if (response.status.value >= 300) {
                return@execute Result.failure(Exception("generate failed (HTTP ${response.status.value})"))
            }
            val channel = response.bodyAsChannel()
            var result: Result<BackendPlanDraft> = Result.failure(Exception("no draft received"))
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty()) continue
                val ev = runCatching { json.decodeFromString<BackendGenerateSseEvent>(data) }.getOrNull() ?: continue
                when (ev.type) {
                    "delta" -> onDelta(ev.text)
                    "draft" -> ev.draft?.let { result = Result.success(it) }
                    "error" -> result = Result.failure(Exception(ev.message.ifBlank { "generate failed" }))
                }
            }
            result
        }
    }

    /**
     * 用后端已配置的编排 LLM 生成下一步建议（优先于 App 内配置的外部 provider 与端侧 MNN）。
     * 后端未配置 LLM（503）或返回非成功状态时抛异常，由调用方回退到下一级。
     */
    suspend fun generateSuggestions(
        backendUrl: String,
        token: String,
        system: String,
        user: String,
    ): List<String> {
        val resp: BackendLlmGenerateResponse = httpClient.post("${backendUrl.trimEnd('/')}/api/llm/generate") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(BackendLlmGenerateRequest(system, user))
            // 后端编排 LLM 走独立短超时，避免后端挂起时长时间阻塞，再回退到云端 / 端侧。
            timeout { requestTimeoutMillis = 30_000L }
        }.body()
        return resp.suggestions
    }

    /** `GET /api/rules` 的响应包装。 */
    @Serializable
    internal data class BackendRulesResponse(val rules: List<BackendRule> = emptyList())

    /** `POST /api/rules` 的请求体。 */
    @Serializable
    data class BackendCreateRuleRequest(
        val name: String = "",
        val kind: String = "",
        val schedule: String = "",
        val directory: String = "",
        val prompt: String = "",
        val enabled: Boolean = true,
    )

    /** 列出自动化规则（最新启用优先）。 */
    suspend fun listRules(backendUrl: String, token: String): List<BackendRule> {
        val resp: BackendRulesResponse = httpClient.get("${backendUrl.trimEnd('/')}/api/rules") {
            header("Authorization", "Bearer $token")
        }.body()
        return resp.rules
    }

    /** 新建自动化规则，返回持久化后的规则对象。 */
    suspend fun createRule(backendUrl: String, token: String, rule: BackendCreateRuleRequest): BackendRule =
        httpClient.post("${backendUrl.trimEnd('/')}/api/rules") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(rule)
        }.body()

    /** 删除自动化规则。 */
    suspend fun deleteRule(backendUrl: String, token: String, id: String): Boolean {
        val resp: HttpResponse = httpClient.delete("${backendUrl.trimEnd('/')}/api/rules/$id") {
            header("Authorization", "Bearer $token")
        }
        return resp.status.value in 200..299
    }

    /** `GET /api/rules/{id}/executions` 的响应包装。 */
    @Serializable
    data class BackendRuleExecutionsResponse(
        val executions: List<BackendRuleExecution> = emptyList(),
        val total: Int = 0,
    )

    /** 列出某规则的执行记录（最多 50 条）与总数。 */
    suspend fun listRuleExecutions(
        backendUrl: String,
        token: String,
        id: String,
    ): BackendRuleExecutionsResponse =
        httpClient.get("${backendUrl.trimEnd('/')}/api/rules/$id/executions") {
            header("Authorization", "Bearer $token")
        }.body()

    /** `POST /api/rules/generate` 的请求体与响应包装。 */
    @Serializable
    internal data class BackendRuleGenerateRequest(val description: String = "")

    @Serializable
    internal data class BackendRuleGenerateResponse(val draft: BackendRuleDraft = BackendRuleDraft())

    /** 用自然语言描述生成规则草稿（需后端已配置编排 LLM）。 */
    suspend fun generateRule(backendUrl: String, token: String, description: String): BackendRuleDraft {
        val resp: BackendRuleGenerateResponse = httpClient.post("${backendUrl.trimEnd('/')}/api/rules/generate") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(BackendRuleGenerateRequest(description))
            timeout { requestTimeoutMillis = 120_000L }
        }.body()
        return resp.draft
    }

    /** 列出所有 APP token（token 明文不序列化，仅元信息）。 */
    suspend fun listTokens(backendUrl: String, token: String): List<BackendToken> =
        httpClient.get("${backendUrl.trimEnd('/')}/api/tokens") {
            header("Authorization", "Bearer $token")
        }.body()

    /** `POST /api/tokens` 的请求体与响应包装。 */
    @Serializable
    internal data class BackendCreateTokenRequest(val name: String = "")

    @Serializable
    internal data class BackendCreateTokenResponse(val token: String = "")

    /** 新建 APP token，返回明文 token（仅此一次可见）。 */
    suspend fun createToken(backendUrl: String, token: String, name: String): String {
        val resp: BackendCreateTokenResponse = httpClient.post("${backendUrl.trimEnd('/')}/api/tokens") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(BackendCreateTokenRequest(name))
        }.body()
        return resp.token
    }

    /** 吊销 APP token。 */
    suspend fun revokeToken(backendUrl: String, token: String, id: String): Boolean {
        val resp: HttpResponse = httpClient.delete("${backendUrl.trimEnd('/')}/api/tokens/$id") {
            header("Authorization", "Bearer $token")
        }
        return resp.status.value in 200..299
    }

    /** `GET /api/audit` 的响应包装。 */
    @Serializable
    internal data class BackendAuditResponse(val audit: List<BackendAuditEntry> = emptyList())

    /** 列出审计日志（最新在前）。 */
    suspend fun listAudit(backendUrl: String, token: String, limit: Int = 100): List<BackendAuditEntry> {
        val resp: BackendAuditResponse = httpClient.get("${backendUrl.trimEnd('/')}/api/audit") {
            header("Authorization", "Bearer $token")
            parameter("limit", limit)
        }.body()
        return resp.audit
    }

    /** `POST /api/llm/complete` 的请求体与响应包装。 */
    @Serializable
    internal data class BackendLlmCompleteRequest(
        val system: String = "",
        val user: String = "",
    )

    @Serializable
    internal data class BackendLlmCompleteResponse(val text: String = "")

    /**
     * 用后端编排 LLM 生成自由文本（如 AGENTS.md 草稿）。
     * [system] 为生成指令，[user] 为项目上下文；返回模型原文。
     */
    suspend fun completeText(backendUrl: String, token: String, system: String, user: String): String {
        val resp: BackendLlmCompleteResponse = httpClient.post("${backendUrl.trimEnd('/')}/api/llm/complete") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(BackendLlmCompleteRequest(system, user))
            timeout { requestTimeoutMillis = 120_000L }
        }.body()
        return resp.text
    }
}

/** 自动化规则（后端 /api/rules）。kind：cron | git | http。 */
@Serializable
data class BackendRule(
    val id: String = "",
    val name: String = "",
    val kind: String = "",
    val schedule: String = "",
    val directory: String = "",
    val prompt: String = "",
    val enabled: Boolean = true,
    val lastFiredAt: String? = null,
    val createdAt: String = "",
)

/** 规则执行记录（后端 /api/rules/{id}/executions）。 */
@Serializable
data class BackendRuleExecution(
    val id: Long = 0,
    val ruleId: String = "",
    val taskId: String = "",
    val triggeredAt: String = "",
)

/** AI 生成的规则草稿（后端 /api/rules/generate）。 */
@Serializable
data class BackendRuleDraft(
    val name: String = "",
    val kind: String = "",
    val schedule: String = "",
    val directory: String = "",
    val prompt: String = "",
    val enabled: Boolean = true,
)

/** APP token 元信息（后端 /api/tokens，明文不序列化）。 */
@Serializable
data class BackendToken(
    val id: String = "",
    val name: String = "",
    val createdAt: String = "",
    val revokedAt: String? = null,
    val lastUsed: String? = null,
)

/** 审计日志条目（后端 /api/audit）。 */
@Serializable
data class BackendAuditEntry(
    val id: Long = 0,
    val tokenId: String = "",
    val tokenName: String = "",
    val method: String = "",
    val path: String = "",
    val status: Int = 0,
    val createdAt: String = "",
)

/** 后端系统信息（后端 /api/system）。version 为后端自身版本。 */
@Serializable
data class BackendSystemInfo(
    val backend: String = "",
    val version: String = "",
    val opencodeUrl: String = "",
    val opencodeVersion: String = "",
    val db: String = "",
    val pgvector: Boolean = false,
    val vectorCapable: Boolean = false,
)
