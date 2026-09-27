/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BackendTask.kt
 * Date : 2026/09/10 19:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.starburst.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Backend 异步任务状态。
 *
 * 对应后端 `/api/tasks` 的状态机：`queued → running → succeeded/failed`，
 * `queued/running → canceled`，失败后进入 `retrying`（带退避）；
 * 依赖任务用 `pending`/`blocked` 表达「等待前置 / 被阻塞」。
 */
@Serializable
enum class BackendTaskStatus {
    @SerialName("queued") Queued,
    @SerialName("running") Running,
    @SerialName("succeeded") Succeeded,
    @SerialName("failed") Failed,
    @SerialName("canceled") Canceled,
    @SerialName("retrying") Retrying,
    @SerialName("pending") Pending,
    @SerialName("blocked") Blocked,
    @SerialName("scheduled") Scheduled,
}

/**
 * Backend 后台任务（对应后端 `GET/POST /api/tasks` 的 Task 对象）。
 */
@Serializable
data class BackendTask(
    val id: String,
    val sessionId: String? = null,
    val directory: String? = null,
    val name: String? = null,
    val prompt: String = "",
    val dependsOn: String? = null,
    val status: BackendTaskStatus = BackendTaskStatus.Queued,
    val error: String? = null,
    val result: String? = null,
    val progress: String? = null,
    val aiSummary: String? = null,
    val attempts: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val scheduledAt: String? = null,
    val cron: String? = null,
    val lastFiredAt: String? = null,
    /**
     * 后端「任务类型」（`tasks.kind`）：`""` = agent 编排任务（走 OpenCode prompt 路径）；
     * 非空 = 内置追踪任务（`test-run` / `doc-generate` / `ai-suggest`），由后端 runner 处理。
     *
     * **只读，App 不得回传**：后端 `tasks/executor.go` 以 `t.Kind != ""` 作为「不走 prompt
     * 路径」的开关，且仅 `doc-generate` 有专用 runner——回传自定义 kind 会让任务无法执行。
     */
    val kind: String = "",
    /**
     * 多步编排分组 id（非空 = 该任务属于一个「多步骤计划」）。
     * 这是任务模式（单个 / 多步骤计划）的真实落库依据——不需要新增字段即可判定。
     */
    val workflowId: String? = null,
    val priority: Int? = null,
    val timeoutSeconds: Int? = null,
)
