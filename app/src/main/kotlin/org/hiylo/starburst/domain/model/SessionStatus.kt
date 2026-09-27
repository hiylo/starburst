/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SessionStatus.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.domain.model

import kotlinx.serialization.Serializable

/**
 * Session Status - indicates if session is processing or idle
 */
@Serializable
sealed class SessionStatus {
    @Serializable
    data object Idle : SessionStatus()
    
    @Serializable
    data object Busy : SessionStatus()

    /**
     * 会话有待用户回答的问题（等待用户在提问卡片中选择/确认），优先级高于 Busy。
     */
    @Serializable
    data object Question : SessionStatus()

    /**
     * 会话有待用户授权/拒绝的权限请求（工具调用被 permission 拦截，等待用户在授权卡片中决定），
     * 优先级高于 Busy——服务端在等待授权期间仍上报 busy，列表据此区别于「处理中」。
     */
    @Serializable
    data object Permission : SessionStatus()

    @Serializable
    data class Retry(
        val attempt: Int,
        val message: String,
        val next: Long // Timestamp of next retry
    ) : SessionStatus()
}
