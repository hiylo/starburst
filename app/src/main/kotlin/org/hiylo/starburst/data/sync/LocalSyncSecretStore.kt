/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : LocalSyncSecretStore.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps transport credentials off DataStore and out of exported payloads. */
@Singleton
class LocalSyncSecretStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun get(key: SecretKey): String? = preferences.getString(key.preferenceKey, null)?.let(::decrypt)

    fun put(key: SecretKey, value: String?) {
        preferences.edit().apply {
            if (value.isNullOrEmpty()) remove(key.preferenceKey) else putString(key.preferenceKey, encrypt(value))
        }.apply()
    }

    /** 清空全部凭据（含 LLM/SFTP）；断开同步应改用 [clearSyncSecrets]，避免误删无关凭据。 */
    fun clearAll() = preferences.edit().clear().apply()

    /** 仅清除同步相关凭据，保留 LLM_PROVIDER_API_KEY 与 SFTP_PASSWORD（LlmProvider/备份在用）。 */
    fun clearSyncSecrets() {
        put(SecretKey.GITHUB_TOKEN, null)
        put(SecretKey.WEBDAV_PASSWORD, null)
        put(SecretKey.SYNC_PASSPHRASE, null)
        put(SecretKey.BACKEND_TOKEN, null)
    }

    /** 用 Android Keystore AES-GCM 加密任意字符串（返回 iv+密文的 Base64）。供 [org.hiylo.starburst.data.repository.ServerRepository] 加密整个服务器列表。 */
    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key())
        }
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    /** 解密 [encrypt] 生成的密文；解密失败返回 null。 */
    fun decrypt(value: String): String? = runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 12)
        Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }
    }.getOrNull()

    private fun key() = (KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        .getKey(ALIAS, null) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()) as javax.crypto.SecretKey

    enum class SecretKey(val preferenceKey: String) {
        GITHUB_TOKEN("github_token"),
        WEBDAV_PASSWORD("webdav_password"),
        SYNC_PASSPHRASE("sync_passphrase"),
        LLM_PROVIDER_API_KEY("llm_provider_api_key"),
        SFTP_PASSWORD("sftp_backup_password"),
        BACKEND_TOKEN("sync_backend_token"),
    }

    companion object {
        private const val PREFERENCES = "sync_secrets"
        private const val ALIAS = "starburst_sync_secrets"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
