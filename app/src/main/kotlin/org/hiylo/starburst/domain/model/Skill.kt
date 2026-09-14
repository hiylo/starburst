/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : Skill.kt
 * Date : 2026/09/10 19:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.starburst.domain.model

import kotlinx.serialization.Serializable

/**
 * OpenCode 服务端的 skill（对应 `GET /skill` 返回的条目）。
 */
@Serializable
data class Skill(
    val name: String,
    val description: String? = null,
    val location: String? = null,
    val content: String? = null,
)
