/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : SharedSession.kt
 * Date : 2026/09/11 09:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.opencode.domain.model

import kotlinx.serialization.Serializable

/**
 * 只读会话：通过分享 ID 加载的会话快照，仅供只读查看，不参与本地会话状态。
 *
 * 字段贴合分享后端返回的会话数据，文本内容由各消息的 text part 展平合并而来。
 *
 * @author Hsi Chu
 * @since V1.3.0
 */
@Serializable
data class SharedSession(
    /** 会话 ID。 */
    val id: String,
    /** 会话标题，可能为空。 */
    val title: String? = null,
    /** 会话创建时间（毫秒时间戳），可能为空。 */
    val createdAt: Long? = null,
    /** 消息列表，按创建时间升序排列。 */
    val messages: List<SharedMessage> = emptyList(),
)

/**
 * 只读会话中的单条消息。
 *
 * @author Hsi Chu
 * @since V1.3.0
 */
@Serializable
data class SharedMessage(
    /** 消息 ID。 */
    val id: String,
    /** 消息角色：`user` 或 `assistant`。 */
    val role: String,
    /** 消息文本内容（text part 展平后的结果）。 */
    val text: String = "",
    /** 消息创建时间（毫秒时间戳），可能为空。 */
    val createdAt: Long? = null,
    /** 消息完成时间（毫秒时间戳，仅 assistant 消息存在），可能为空。 */
    val completedAt: Long? = null,
)
