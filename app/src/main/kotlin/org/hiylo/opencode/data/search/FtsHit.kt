/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : FtsHit.kt
 * Date : 2026/09/11 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.opencode.data.search

/**
 * 全文搜索命中结果。
 *
 * 用于跨会话/跨服务器的消息全文检索，携带命中消息的定位信息与高亮片段。
 *
 * @property serverId 命中消息所属服务器 ID
 * @property sessionId 命中消息所属会话 ID
 * @property messageId 命中消息 ID
 * @property title 命中消息所属会话标题
 * @property snippet 命中片段（以 `<b>`/`</b>` 包裹命中词，供 UI 高亮）
 * @author Hsi Chu
 * @since V1.3.0
 */
data class FtsHit(
    val serverId: String,
    val sessionId: String,
    val messageId: String,
    val title: String,
    val snippet: String,
)
