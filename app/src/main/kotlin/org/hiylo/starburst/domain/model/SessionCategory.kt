/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SessionCategory.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SessionCategory(
    val id: String,
    val name: String,
    val color: String,
    val icon: String,
)
