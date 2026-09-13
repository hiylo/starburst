/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : BackendTask.kt
 * Date : 2026/09/10 19:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.opencode.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenCode Backend 异步任务状态。
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
 * OpenCode Backend 后台任务（对应后端 `GET/POST /api/tasks` 的 Task 对象）。
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
)
