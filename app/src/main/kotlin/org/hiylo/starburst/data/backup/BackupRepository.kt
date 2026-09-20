/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BackupRepository.kt
 * Date : 2026/09/17 10:05:36
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hiylo.starburst.data.repository.ServerRepository
import org.hiylo.starburst.data.repository.SettingsRepository
import org.hiylo.starburst.data.repository.isPortableSyncServerUrl
import org.hiylo.starburst.data.sync.LocalSyncSecretStore
import org.hiylo.starburst.data.sync.PasswordCrypto
import java.io.IOException
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

/** 备份失败原因，供 UI 层映射为本地化文案。 */
enum class BackupFailure { INVALID_FILE, UNSUPPORTED_VERSION, WRONG_PASSPHRASE, READ_ERROR, WRITE_ERROR }

/** 加密备份/还原过程中的可预期失败。 */
class BackupException(val failure: BackupFailure, cause: Throwable? = null) : Exception(failure.name, cause)

/**
 * 负责导出/还原应用本地数据的加密备份。
 *
 * 备份文件为 JSON 信封，其载荷（含服务器密码、SSH 凭据等）使用用户口令派生的
 * AES-256-GCM 密钥（PBKDF2WithHmacSHA256）加密。因 Android Keystore 密钥与设备绑定、
 * 无法跨设备迁移，故备份采用口令加密路径以保证可移植；设备本地的服务器列表仍由
 * [org.hiylo.starburst.data.sync.LocalSyncSecretStore]（Keystore AES-GCM）负责静态加密。
 */
@Singleton
class BackupRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val settingsRepository: SettingsRepository,
    private val serverRepository: ServerRepository,
    private val json: Json,
    @ApplicationContext private val context: Context,
    private val localSyncSecretStore: LocalSyncSecretStore,
    private val sftpBackupTransport: SftpBackupTransport,
) {

    /** 将当前本地数据快照加密后写入 [uri] 指定的文件。 */
    suspend fun exportBackup(uri: Uri, passphrase: CharArray) = withContext(Dispatchers.IO) {
        val file = buildBackupFile(passphrase)
        writeDocument(uri, json.encodeToString(file))
    }

    /** 读取并解密 [uri] 指定的备份文件，校验版本后写回各仓库。 */
    suspend fun restoreBackup(uri: Uri, passphrase: CharArray) = withContext(Dispatchers.IO) {
        val content = readDocument(uri)
        restoreContent(content, passphrase)
    }

    /** 将当前本地数据快照加密后通过 SFTP 上传到 [config] 指定的远程目录。 */
    suspend fun exportBackupToSftp(config: SftpBackupConfig, passphrase: CharArray) = withContext(Dispatchers.IO) {
        val file = buildBackupFile(passphrase)
        val content = json.encodeToString(file).toByteArray(Charsets.UTF_8)
        try {
            sftpBackupTransport.upload(content, config, BACKUP_FILENAME)
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException(BackupFailure.WRITE_ERROR, e)
        }
    }

    /** 通过 SFTP 从 [config] 下载加密备份并还原。 */
    suspend fun restoreBackupFromSftp(config: SftpBackupConfig, passphrase: CharArray) = withContext(Dispatchers.IO) {
        val content = try {
            sftpBackupTransport.download(config, BACKUP_FILENAME).decodeToString()
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException(BackupFailure.READ_ERROR, e)
        }
        restoreContent(content, passphrase)
    }

    /** 读取已保存的 SFTP 目标配置（不含口令）。 */
    suspend fun savedSftpSettings(): SftpBackupSettings = settingsRepository.sftpBackupSettings.first()

    /** 保存 SFTP 目标配置（host/port/username/remoteDir）。 */
    suspend fun saveSftpSettings(settings: SftpBackupSettings) = settingsRepository.setSftpBackupSettings(settings)

    /** 保存 SFTP 口令（Keystore 加密）。 */
    fun saveSftpPassword(password: String) {
        localSyncSecretStore.put(LocalSyncSecretStore.SecretKey.SFTP_PASSWORD, password)
    }

    /** 读取已保存的 SFTP 口令（Keystore 解密，可能为 null）。 */
    fun savedSftpPassword(): String? = localSyncSecretStore.get(LocalSyncSecretStore.SecretKey.SFTP_PASSWORD)

    private suspend fun buildBackupFile(passphrase: CharArray): BackupFile {
        val payload = snapshot()
        val plaintext = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        val encrypted = try {
            PasswordCrypto.encrypt(plaintext, passphrase)
        } finally {
            plaintext.fill(0)
        }
        return BackupFile(createdAt = System.currentTimeMillis(), encrypted = encrypted)
    }

    private suspend fun restoreContent(content: String, passphrase: CharArray) {
        val file = runCatching { json.decodeFromString<BackupFile>(content) }
            .getOrElse { throw BackupException(BackupFailure.INVALID_FILE, it) }
        if (file.version != BackupFile.VERSION) throw BackupException(BackupFailure.UNSUPPORTED_VERSION)
        val plaintext = try {
            PasswordCrypto.decrypt(file.encrypted, passphrase)
        } catch (e: AEADBadTagException) {
            throw BackupException(BackupFailure.WRONG_PASSPHRASE, e)
        } catch (e: IllegalArgumentException) {
            throw BackupException(BackupFailure.WRONG_PASSPHRASE, e)
        }
        val payload = try {
            json.decodeFromString<BackupPayload>(plaintext.decodeToString())
        } finally {
            plaintext.fill(0)
        }
        if (payload.version != BackupPayload.VERSION) throw BackupException(BackupFailure.UNSUPPORTED_VERSION)
        apply(payload)
    }

    private suspend fun snapshot(): BackupPayload {
        val preferences = dataStore.data.first()
        val servers = serverRepository.serverConfigsFrom(preferences)
            .filter { isPortableSyncServerUrl(it.url) }
        val serverIds = servers.map { it.id }
        val llmProvider = settingsRepository.syncLlmProviderFrom(preferences)
        return BackupPayload(
            settings = settingsRepository.syncSettingsSnapshotFrom(preferences),
            chatLineHeight = settingsRepository.syncChatLineHeightFrom(preferences),
            sessionCategories = settingsRepository.syncSessionCategoriesFrom(preferences),
            promptTemplates = settingsRepository.syncPromptTemplatesFrom(preferences),
            customCommands = settingsRepository.syncCustomCommandsFrom(preferences),
            llmProviderBaseUrl = llmProvider.first,
            llmProviderModel = llmProvider.second,
            servers = servers,
            serverSavedPaths = settingsRepository.syncSavedPathsFrom(preferences, serverIds),
            serverRecentProjects = settingsRepository.syncRecentProjectsFrom(preferences, serverIds),
            sessionCategoryAssignments =
                settingsRepository.syncSessionCategoryAssignmentsSnapshotFrom(preferences, serverIds),
            favoriteSessionIds = settingsRepository.syncFavoriteSessionIdsFrom(preferences, serverIds),
            crossServerFavoriteOrder = settingsRepository.syncCrossServerFavoriteOrderFrom(preferences, serverIds),
            favoriteSessionSnapshots = settingsRepository.syncFavoriteSessionSnapshotsFrom(preferences, serverIds),
            hiddenModels = settingsRepository.syncHiddenModelsFrom(preferences, serverIds),
        )
    }

    private suspend fun apply(payload: BackupPayload) {
        dataStore.edit { preferences ->
            val serverIdMapping = serverRepository.importServerConfigsTo(preferences, payload.servers)
            settingsRepository.applySyncSettingsTo(preferences, payload.settings, payload.sessionCategories)
            settingsRepository.applyChatLineHeightTo(preferences, payload.chatLineHeight)
            settingsRepository.applyPromptTemplatesTo(preferences, payload.promptTemplates)
            settingsRepository.applyCustomCommandsTo(preferences, payload.customCommands)
            settingsRepository.applyLlmProviderTo(preferences, payload.llmProviderBaseUrl, payload.llmProviderModel)
            settingsRepository.applySyncSessionCategoryAssignmentsTo(
                preferences,
                payload.sessionCategoryAssignments,
                serverIdMapping,
            )
            settingsRepository.applySyncSessionCollectionsTo(
                preferences = preferences,
                favoriteSessionIds = payload.favoriteSessionIds,
                crossServerFavoriteOrder = payload.crossServerFavoriteOrder,
                favoriteSessionSnapshots = payload.favoriteSessionSnapshots,
                hiddenModels = payload.hiddenModels,
                serverIdMapping = serverIdMapping,
            )
            settingsRepository.applySyncSavedPathsTo(preferences, payload.serverSavedPaths, serverIdMapping)
            settingsRepository.applySyncRecentProjectsTo(preferences, payload.serverRecentProjects, serverIdMapping)
        }
        settingsRepository.updateSynchronousLocale(payload.settings.appLanguage)
    }

    private fun writeDocument(uri: Uri, content: String) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")
                ?.bufferedWriter(Charsets.UTF_8)
                ?.use { it.write(content) }
                ?: throw IOException("Unable to open output stream")
        } catch (e: Exception) {
            throw BackupException(BackupFailure.WRITE_ERROR, e)
        }
    }

    private fun readDocument(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: throw IOException("Unable to open input stream")
        } catch (e: Exception) {
            throw BackupException(BackupFailure.READ_ERROR, e)
        }
    }

    companion object {
        /** 备份文件的固定文件名（本地 SAF 与 SFTP 远程目录均使用）。 */
        const val BACKUP_FILENAME = "starburst-backup.json"
    }
}
