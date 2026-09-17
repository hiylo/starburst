/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : MessageBookmark.kt
 * Date : 2026/09/11 08:42:13
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.domain.model

import kotlinx.serialization.Serializable

/**
 * 消息书签：标记「某服务器某会话里的某条消息」，用于跨会话、跨服务器收藏与快速定位。
 *
 * @author Hsi Chu
 * @since V1.3.0
 */
@Serializable
data class MessageBookmark(
    /** 稳定唯一标识，由 [buildId] 生成，保证同一消息重复添加幂等。 */
    val id: String,
    /** 所属服务器 ID。 */
    val serverId: String,
    /** 所属会话 ID。 */
    val sessionId: String,
    /** 被标记的消息 ID。 */
    val messageId: String,
    /** 消息内容摘要，用于列表展示。 */
    val messageText: String,
    /** 添加时间（epoch 毫秒）。 */
    val createdAt: Long,
    /** 书签标签列表（可为空），用于分类与筛选。 */
    val tags: List<String> = emptyList(),
) {
    companion object {
        /** 分隔符，用于拼接服务器、会话、消息三个 ID 生成稳定书签 ID。 */
        private const val ID_SEPARATOR = "|"

        /**
         * 根据服务器、会话、消息 ID 生成稳定的书签 ID（幂等）。
         *
         * @param serverId 服务器 ID
         * @param sessionId 会话 ID
         * @param messageId 消息 ID
         * @return 形如 `serverId|sessionId|messageId` 的稳定 ID
         */
        fun buildId(serverId: String, sessionId: String, messageId: String): String =
            listOf(serverId, sessionId, messageId).joinToString(ID_SEPARATOR)
    }
}
