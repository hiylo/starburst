/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : PendingInteraction.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.domain.model

sealed interface PendingInteraction {
    val id: String
    val sessionId: String

    data class Permission(val request: SseEvent.PermissionAsked) : PendingInteraction {
        override val id: String = request.id
        override val sessionId: String = request.sessionId
    }

    data class Question(val request: SseEvent.QuestionAsked) : PendingInteraction {
        override val id: String = request.id
        override val sessionId: String = request.sessionId
    }
}
