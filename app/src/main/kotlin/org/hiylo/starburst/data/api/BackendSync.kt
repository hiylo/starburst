/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BackendSync.kt
 * Date : 2026/09/22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import kotlinx.serialization.Serializable

/** 后端键值同步包（payload 为任意序列化内容，revision 单调递增）。 */
@Serializable
data class BackendSyncBundle(
    val key: String = "",
    val payload: String = "",
    val revision: Long = 0,
    val updatedAt: String = "",
)

/** `PUT /api/sync` 的请求体。 */
@Serializable
internal data class BackendSyncPutRequest(
    val key: String,
    val payload: String,
)

/** `PUT /api/sync` 的响应体。 */
@Serializable
internal data class BackendSyncPutResponse(
    val key: String = "",
    val revision: Long = 0,
)
