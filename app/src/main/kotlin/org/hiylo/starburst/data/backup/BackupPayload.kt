/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BackupPayload.kt
 * Date : 2026/09/17 10:05:36
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.backup

import kotlinx.serialization.Serializable
import org.hiylo.starburst.data.repository.SettingsRepository
import org.hiylo.starburst.data.sync.EncryptedSecrets
import org.hiylo.starburst.data.sync.SyncSettings
import org.hiylo.starburst.domain.model.FavoriteSessionSnapshot
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.domain.model.SessionCategory

/**
 * 本地备份快照：包含需要跨设备迁移的全部应用设置与每服务器数据。
 * 密码、SSH 凭据、Backend token 等敏感字段随 [servers] 一并快照，整体由备份文件层加密保护。
 */
@Serializable
data class BackupPayload(
    val version: Int = VERSION,
    val createdAt: Long = 0,
    val settings: SyncSettings = SyncSettings(),
    val chatLineHeight: Float = 1f,
    val sessionCategories: List<SessionCategory> = emptyList(),
    val promptTemplates: List<SettingsRepository.PromptTemplate> = emptyList(),
    val customCommands: List<SettingsRepository.CustomCommand> = emptyList(),
    val llmProviderBaseUrl: String = "",
    val llmProviderModel: String = "",
    val servers: List<ServerConfig> = emptyList(),
    val serverSavedPaths: Map<String, List<String>> = emptyMap(),
    val serverSessionTemplates: Map<String, List<SettingsRepository.SessionTemplate>> = emptyMap(),
    val serverRecentProjects: Map<String, List<String>> = emptyMap(),
    val sessionCategoryAssignments: Map<String, Map<String, String>> = emptyMap(),
    val favoriteSessionIds: Map<String, List<String>> = emptyMap(),
    val crossServerFavoriteOrder: List<String> = emptyList(),
    val favoriteSessionSnapshots: Map<String, FavoriteSessionSnapshot> = emptyMap(),
    val hiddenModels: Map<String, Set<String>> = emptyMap(),
) {
    companion object {
        const val VERSION = 1
    }
}

/**
 * 磁盘上的加密备份文件：passphrase 派生 AES-256-GCM（PBKDF2WithHmacSHA256）密文信封。
 * 密钥不依赖设备 Keystore，因此备份文件可跨设备还原。
 */
@Serializable
data class BackupFile(
    val version: Int = VERSION,
    val createdAt: Long = 0,
    val encrypted: EncryptedSecrets,
) {
    companion object {
        const val VERSION = 1
    }
}
