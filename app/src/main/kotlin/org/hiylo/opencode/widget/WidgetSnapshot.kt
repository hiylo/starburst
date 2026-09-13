/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : WidgetSnapshot.kt
 * Date : 2026/09/12 20:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.3.0
 */
package org.hiylo.opencode.widget

import kotlinx.serialization.Serializable

/**
 * 桌面 Widget 展示所需的轻量数据快照。
 *
 * 由应用进程（[WidgetSnapshotWriter]）持续写入 SharedPreferences，Widget 进程在
 * `onUpdate` 时同步读取并渲染。快照只包含渲染所需的摘要字段，避免 Widget 直接
 * 依赖网络或内存态。
 *
 * @author Hsi Chu
 * @since V1.3.0
 */
@Serializable
data class WidgetSnapshot(
    val servers: List<WidgetServerInfo> = emptyList(),
    val activeSessions: List<WidgetSessionInfo> = emptyList(),
    val taskCounts: Map<String, WidgetTaskCounts> = emptyMap(),
)

/** Widget 上展示的单台服务器信息。 */
@Serializable
data class WidgetServerInfo(
    val id: String,
    val name: String,
    val url: String,
    val connected: Boolean = false,
)

/** Widget 上展示的「有新消息 / 活动」的会话。 */
@Serializable
data class WidgetSessionInfo(
    val serverId: String,
    val serverName: String,
    val sessionId: String,
    val title: String,
    val directory: String,
    val updatedAt: Long = 0L,
)

/** Widget 上展示的任务统计（按 serverId）。 */
@Serializable
data class WidgetTaskCounts(
    val running: Int = 0,
    val queued: Int = 0,
    val scheduled: Int = 0,
    val failed: Int = 0,
) {
    val active: Int get() = running + queued
}
