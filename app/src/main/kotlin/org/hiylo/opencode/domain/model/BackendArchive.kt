/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : BackendArchive.kt
 * Date : 2026/09/11 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.opencode.domain.model

import kotlinx.serialization.Serializable

/**
 * OpenCode Backend 会话归档（对应后端 `GET/POST /api/archives` 的 Archive 对象）。
 *
 * 归档是后端拉取远端会话消息后落库的快照，支持 `markdown` 与 `json` 两种格式；
 * 列表接口不返回 [content]，仅详情接口返回完整内容。
 */
@Serializable
data class BackendArchive(
    val id: String,
    val sessionId: String? = null,
    val title: String? = null,
    val format: String = "markdown",
    val content: String? = null,
    val size: Int = 0,
    val createdAt: String? = null,
)
