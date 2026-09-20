/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BackupViewModel.kt
 * Date : 2026/09/17 10:05:36
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.hiylo.starburst.R
import org.hiylo.starburst.data.backup.BackupException
import org.hiylo.starburst.data.backup.BackupFailure
import org.hiylo.starburst.data.backup.BackupRepository
import org.hiylo.starburst.data.backup.SftpBackupConfig
import org.hiylo.starburst.data.backup.SftpBackupSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 备份/还原操作的完成结果。 */
enum class BackupOutcome { EXPORTED, IMPORTED }

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repository: BackupRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _outcome = MutableStateFlow<BackupOutcome?>(null)
    val outcome = _outcome.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    /** 已保存的 SFTP 目标配置（不含口令），用于预填对话框字段。 */
    private val _sftpSettings = MutableStateFlow(SftpBackupSettings())
    val sftpSettings = _sftpSettings.asStateFlow()

    /** 清空上次操作结果，供对话框打开时调用。 */
    fun reset() {
        _outcome.value = null
        _errorMessage.value = null
    }

    /** 载入已保存的 SFTP 目标配置（host/port/username/remoteDir）。 */
    fun loadSftpSettings() {
        viewModelScope.launch { _sftpSettings.value = repository.savedSftpSettings() }
    }

    /** 保存 SFTP 目标配置与口令（口令走 Keystore 加密）。 */
    fun saveSftpConfig(settings: SftpBackupSettings, password: String) {
        viewModelScope.launch { repository.saveSftpSettings(settings) }
        repository.saveSftpPassword(password)
    }

    fun export(uri: Uri, passphrase: String) = runBackup(passphrase, BackupOutcome.EXPORTED) { chars ->
        repository.exportBackup(uri, chars)
    }

    fun import(uri: Uri, passphrase: String) = runBackup(passphrase, BackupOutcome.IMPORTED) { chars ->
        repository.restoreBackup(uri, chars)
    }

    fun exportToSftp(config: SftpBackupConfig, passphrase: String) = runBackup(passphrase, BackupOutcome.EXPORTED) { chars ->
        repository.exportBackupToSftp(config, chars)
    }

    fun importFromSftp(config: SftpBackupConfig, passphrase: String) = runBackup(passphrase, BackupOutcome.IMPORTED) { chars ->
        repository.restoreBackupFromSftp(config, chars)
    }

    private fun runBackup(passphrase: String, success: BackupOutcome, block: suspend (CharArray) -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _outcome.value = null
            _errorMessage.value = null
            val chars = passphrase.toCharArray()
            try {
                block(chars)
                _outcome.value = success
            } catch (e: BackupException) {
                _errorMessage.value = context.getString(
                    when (e.failure) {
                        BackupFailure.WRONG_PASSPHRASE -> R.string.backup_error_wrong_passphrase
                        BackupFailure.INVALID_FILE -> R.string.backup_error_invalid_file
                        BackupFailure.UNSUPPORTED_VERSION -> R.string.backup_error_unsupported_version
                        BackupFailure.READ_ERROR, BackupFailure.WRITE_ERROR -> R.string.backup_error_io
                    },
                )
            } catch (e: Exception) {
                _errorMessage.value = context.getString(R.string.backup_error_generic)
            } finally {
                chars.fill('\u0000')
                _busy.value = false
            }
        }
    }
}
