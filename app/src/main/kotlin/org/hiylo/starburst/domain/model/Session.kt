/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : Session.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Session - represents a server conversation session.
 * Field names match the server API convention (uppercase ID suffixes).
 */
@Serializable
data class Session(
    val id: String,
    val slug: String = "",
    @SerialName("projectID") val projectId: String = "",
    val directory: String = "",
    @SerialName("workspaceID") val workspaceId: String? = null,
    @SerialName("parentID") val parentId: String? = null,
    val title: String? = null,
    val version: String = "",
    val time: Time,
    val summary: Summary? = null,
    val share: Share? = null,
    val permission: List<PermissionRule>? = null,
    val revert: Revert? = null,
    val model: SessionModel? = null,
    // 后端会话对象带 agent（首个回合起才有值）；此前未解析，气泡下方的
    // 「Agent 类型」在消息缺值时无处回落。
    val agent: String? = null
) {
    @Serializable
    data class Time(
        val created: Long,
        val updated: Long,
        val compacting: Long? = null,
        val archived: Long? = null
    )

    @Serializable
    data class Summary(
        val additions: Int = 0,
        val deletions: Int = 0,
        val files: Int = 0,
        val diffs: List<FileDiff>? = null
    )

    @Serializable
    data class Share(val url: String)

    @Serializable
    data class Revert(
        @SerialName("messageID") val messageId: String,
        @SerialName("partID") val partId: String? = null,
        val snapshot: String? = null,
        val diff: String? = null
    )

    @Serializable
    data class PermissionRule(
        val permission: String,
        val pattern: String = "*",
        val action: String = "ask"
    )

    @Serializable
    data class SessionModel(
        val id: String = "",
        @SerialName("providerID") val providerId: String = "",
        val variant: String? = null
    )

    val createdAt: Long
        get() = time.created

    val isArchived: Boolean
        get() = time.archived != null
}

/**
 * Session with its current status and last message.
 */
data class SessionWithStatus(
    val session: Session,
    val status: SessionStatus,
    val lastMessageData: MessageWithParts? = null
) {
    val lastMessage: MessageWithParts?
        get() = lastMessageData
}
