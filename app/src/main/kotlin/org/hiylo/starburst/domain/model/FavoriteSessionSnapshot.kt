/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : FavoriteSessionSnapshot.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FavoriteSessionSnapshot(
    val id: String,
    val projectId: String,
    val directory: String,
    val title: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toSession() = Session(
        id = id,
        projectId = projectId,
        directory = directory,
        title = title,
        time = Session.Time(created = createdAt, updated = updatedAt),
    )

    companion object {
        fun from(session: Session) = FavoriteSessionSnapshot(
            id = session.id,
            projectId = session.projectId,
            directory = session.directory,
            title = session.title,
            createdAt = session.time.created,
            updatedAt = session.time.updated,
        )
    }
}
