/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : MessageFtsIndex.kt
 * Date : 2026/09/11 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.starburst.data.search

/**
 * 消息全文搜索的索引与查询能力抽象。
 *
 * 通过接口解耦，使 [org.hiylo.starburst.data.repository.EventReducer] 等依赖方可在单元测试中
 * 注入 no-op 实现，而不需要 Android 运行环境（SQLite/Context）。
 *
 * 生产实现为 [SqliteMessageFtsIndex]（基于 Android SQLite 的普通表 + LIKE 匹配）。
 *
 * @author Hsi Chu
 * @since V1.3.0
 */
interface MessageFtsIndex {

    /** 索引（写入或更新）一条消息，以 [messageId] 为主键 upsert。 */
    suspend fun index(
        serverId: String,
        sessionId: String,
        messageId: String,
        title: String,
        content: String,
    )

    /** 按关键词搜索，返回命中列表（按更新时间倒序）；查询词为空返回空列表。 */
    suspend fun search(query: String, limit: Int = 50, serverId: String? = null): List<FtsHit>

    /** 删除某个会话的全部索引记录。 */
    suspend fun deleteSession(sessionId: String)

    /** 清空全部索引。 */
    suspend fun clear()
}
