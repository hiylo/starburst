/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : SharedSessionRepository.kt
 * Date : 2026/09/11 09:20:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.opencode.data.repository

import org.hiylo.opencode.data.api.OpenCodeApi
import org.hiylo.opencode.data.api.ServerConnection
import org.hiylo.opencode.domain.model.SharedSession
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话分享只读仓库：通过分享 ID 加载只读会话，供后续只读查看器 UI 使用。
 *
 * 目前仅对 [OpenCodeApi] 做薄封装，方便 UI 层统一注入数据来源。
 *
 * @author Hsi Chu
 * @since V1.3.0
 */
@Singleton
class SharedSessionRepository @Inject constructor(
    private val api: OpenCodeApi,
) {
    /**
     * 通过分享 ID 加载只读会话（标题 + 消息列表）。
     *
     * @param conn 服务器连接（分享读取走公开分享后端，此处仅为签名一致性保留）
     * @param shareId 分享 ID
     * @return 重组后的只读会话
     */
    suspend fun loadSharedSession(conn: ServerConnection, shareId: String): SharedSession {
        return api.getSharedSession(conn, shareId)
    }

    // TODO(UI 接入点): 在「会话列表 / 会话详情」的分享入口处，取出 `Session.share.url`（形如
    //  `https://opncd.ai/s/{shareId}`），解析出末段 shareId 后调用 `loadSharedSession(conn, shareId)`，
    //  再以只读方式渲染 `SharedSession`（标题 + 消息列表）。分享链接查看器本身不在本次改动范围内。
}
