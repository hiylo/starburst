/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ServerRepository.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.repository

import org.hiylo.starburst.logging.AppLogger as Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jcraft.jsch.Session
import org.hiylo.starburst.data.api.OpenCodeApi
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.domain.model.ServerHealth
import org.hiylo.starburst.data.sync.SyncServer
import org.hiylo.starburst.data.sync.LocalSyncSecretStore
import org.hiylo.starburst.service.SshRunner
import org.hiylo.starburst.ui.screens.chat.ServerTerminalRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ServerRepository"
private const val SERVERS_KEY = "servers"
private const val SSH_CONNECT_TIMEOUT_MS = 15_000

/** Legacy local server URL reserved by the previous Termux runtime feature; excluded from sync. */
internal const val LEGACY_LOCAL_SERVER_URL = "http://127.0.0.1:4096"

internal fun normalizeServerUrl(url: String): String = url.trim().trimEnd('/')

internal fun isPortableSyncServerUrl(url: String): Boolean =
    normalizeServerUrl(url) != normalizeServerUrl(LEGACY_LOCAL_SERVER_URL)

internal fun portableSyncServers(servers: List<ServerConfig>): List<SyncServer> = servers
    .filter { isPortableSyncServerUrl(it.url) }
    .map { SyncServer(it.id, normalizeServerUrl(it.url), it.name, it.username, it.autoConnect) }

internal data class ServerMergeResult(
    val servers: List<ServerConfig>,
    val idMapping: Map<String, String>,
)

internal fun mergeSyncServers(
    current: List<ServerConfig>,
    remote: List<SyncServer>,
    passwords: Map<String, String>,
    idGenerator: () -> String = { UUID.randomUUID().toString() },
): ServerMergeResult {
    val mergedServers = current.toMutableList()
    val usedIds = current.mapTo(mutableSetOf()) { it.id }
    val idMapping = mutableMapOf<String, String>()
    remote.filter { isPortableSyncServerUrl(it.url) }.forEach { source ->
        val normalized = normalizeServerUrl(source.url)
        val existingIndex = mergedServers.indexOfFirst { normalizeServerUrl(it.url) == normalized }
        val existing = mergedServers.getOrNull(existingIndex)
        val password = passwords[source.id]
        val merged = if (existing != null) {
            existing.copy(
                url = normalized,
                name = source.name,
                username = source.username,
                autoConnect = source.autoConnect,
                password = password ?: existing.password,
            )
        } else {
            val id = source.id.takeIf { it !in usedIds } ?: idGenerator()
            usedIds += id
            ServerConfig(id, normalized, source.username, password, source.name, source.autoConnect)
        }
        if (existingIndex >= 0) mergedServers[existingIndex] = merged else mergedServers += merged
        idMapping[source.id] = merged.id
    }
    return ServerMergeResult(mergedServers, idMapping)
}

internal fun mergeServerConfigs(
    current: List<ServerConfig>,
    remote: List<ServerConfig>,
    idGenerator: () -> String = { UUID.randomUUID().toString() },
): ServerMergeResult {
    val mergedServers = current.toMutableList()
    val usedIds = current.mapTo(mutableSetOf()) { it.id }
    val idMapping = mutableMapOf<String, String>()
    remote.filter { isPortableSyncServerUrl(it.url) }.forEach { source ->
        val normalized = normalizeServerUrl(source.url)
        val existingIndex = mergedServers.indexOfFirst { normalizeServerUrl(it.url) == normalized }
        val existing = mergedServers.getOrNull(existingIndex)
        val merged = if (existing != null) {
            source.copy(id = existing.id, lastConnected = null, isHealthy = false)
        } else {
            val id = source.id.takeIf { it !in usedIds } ?: idGenerator()
            usedIds += id
            source.copy(id = id, lastConnected = null, isHealthy = false)
        }
        if (existingIndex >= 0) mergedServers[existingIndex] = merged else mergedServers += merged
        idMapping[source.id] = merged.id
    }
    return ServerMergeResult(mergedServers, idMapping)
}

/**
 * Server Repository - manages saved OpenCode servers
 * 
 * Uses DataStore to persist server configurations
 */
@Singleton
class ServerRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val api: OpenCodeApi,
    private val json: Json,
    private val secretStore: LocalSyncSecretStore,
) {
    
    private val serversKey = stringPreferencesKey(SERVERS_KEY)
    
    /**
     * Get all saved servers as Flow
     */
    val servers: Flow<List<ServerConfig>> = dataStore.data
        .map { preferences -> decodeServers(preferences[serversKey]) }
        .flowOn(Dispatchers.IO)
    
    /**
     * Get all servers (alias for servers Flow)
     */
    fun getAllServers(): Flow<List<ServerConfig>> = servers
    
    /**
     * Add a new server
     */
    suspend fun addServer(
        url: String,
        username: String = "starburst",
        password: String? = null,
        name: String? = null,
        autoConnect: Boolean = false,
        sshPort: Int = 22,
        sshUsername: String = "",
        sshPassword: String? = null,
        backendUrl: String? = null,
        backendToken: String? = null,
    ): ServerConfig {
        val server = ServerConfig(
            id = UUID.randomUUID().toString(),
            url = url.trimEnd('/'),
            username = username,
            password = password,
            name = name,
            autoConnect = autoConnect,
            sshPort = sshPort,
            sshUsername = sshUsername,
            sshPassword = sshPassword,
            backendUrl = backendUrl,
            backendToken = backendToken,
            lastConnected = null,
            isHealthy = false
        )
        
        dataStore.edit { preferences ->
            preferences[serversKey] = encodeServers(readServersStrict(preferences) + server)
        }
        
        return server
    }
    
    /**
     * Update a server
     */
    suspend fun updateServer(server: ServerConfig) {
        dataStore.edit { preferences ->
            preferences[serversKey] = encodeServers(readServersStrict(preferences).map {
                if (it.id == server.id) server else it
            })
        }
    }

    suspend fun setAutoConnect(serverId: String, autoConnect: Boolean) {
        dataStore.edit { preferences ->
            preferences[serversKey] = encodeServers(readServersStrict(preferences).map { server ->
                if (server.id == serverId) server.copy(autoConnect = autoConnect) else server
            })
        }
    }
    
    /**
     * Delete a server
     */
    suspend fun deleteServer(serverId: String) {
        dataStore.edit { preferences ->
            preferences[serversKey] = encodeServers(readServersStrict(preferences).filter { it.id != serverId })
        }
        // 释放该服务器对应的终端 workspace（关闭 socket 协程、清理连接凭据），避免泄漏。
        ServerTerminalRegistry.release(serverId)
    }
    
    /**
     * Check server health
     */
    suspend fun checkHealth(server: ServerConfig): Result<ServerHealth> {
        var sshSession: Session? = null
        return try {
            val conn = if (server.useSsh) {
                val (connection, session) = openSshTunnel(server)
                sshSession = session
                connection
            } else {
                ServerConnection.from(server.url, server.username, server.password)
            }
            val health = api.getHealth(conn)

            // Update server health status
            val updatedServer = server.copy(
                isHealthy = health.healthy,
                lastConnected = System.currentTimeMillis()
            )
            updateServer(updatedServer)

            Result.success(health)
        } catch (e: Exception) {
            Log.e(TAG, "Server health check failed", e)

            // Mark as unhealthy
            val updatedServer = server.copy(isHealthy = false)
            updateServer(updatedServer)

            Result.failure(e)
        } finally {
            try { sshSession?.disconnect() } catch (_: Exception) { }
        }
    }

    /** 为配置了 SSH 的服务器建立临时本地端口转发，返回转发后的连接与 SSH 会话。 */
    private suspend fun openSshTunnel(server: ServerConfig): Pair<ServerConnection, Session> =
        withContext(Dispatchers.IO) {
            val host = server.host
            val openCodePort = server.openCodePort
            val session = SshRunner.buildSession(server)
            session.connect(SSH_CONNECT_TIMEOUT_MS)
            val localPort = session.setPortForwardingL(0, host, openCodePort)
            val connection = ServerConnection.from("http://127.0.0.1:$localPort", server.username, server.password)
            connection to session
        }
    
    /**
     * Check server health (alias returning boolean)
     */
    suspend fun checkServerHealth(server: ServerConfig): Boolean {
        return checkHealth(server).getOrNull()?.healthy == true
    }
    
    /**
     * Get server by ID
     */
    suspend fun getServer(serverId: String): ServerConfig? {
        return servers.firstOrNull()?.find { it.id == serverId }
    }

    suspend fun syncServersSnapshot(): List<SyncServer> = portableSyncServers(servers.firstOrNull() ?: emptyList())

    internal fun syncServersSnapshotFrom(preferences: Preferences): List<SyncServer> =
        portableSyncServers(readServers(preferences))

    internal fun serverConfigsFrom(preferences: Preferences): List<ServerConfig> = readServers(preferences)

    /** Merges by normalized URL, intentionally retaining no remote runtime fields. */
    suspend fun importSyncServers(remote: List<SyncServer>, passwords: Map<String, String>): Map<String, String> {
        var mapping = emptyMap<String, String>()
        dataStore.edit { preferences ->
            mapping = importSyncServersTo(preferences, remote, passwords)
        }
        return mapping
    }

    internal fun importSyncServersTo(
        preferences: MutablePreferences,
        remote: List<SyncServer>,
        passwords: Map<String, String>,
    ): Map<String, String> {
        val result = mergeSyncServers(readServersStrict(preferences), remote, passwords)
        preferences[serversKey] = encodeServers(result.servers)
        return result.idMapping
    }

    /** 按规范化 URL 合并并写回完整服务器配置（含密码、SSH、Backend），返回远端 id → 本地 id 映射。 */
    internal fun importServerConfigsTo(
        preferences: MutablePreferences,
        remote: List<ServerConfig>,
    ): Map<String, String> {
        val result = mergeServerConfigs(readServersStrict(preferences), remote)
        preferences[serversKey] = encodeServers(result.servers)
        return result.idMapping
    }
    
    // ============ Private ============

    /** 序列化并加密服务器列表，写入 DataStore。 */
    private fun encodeServers(servers: List<ServerConfig>): String =
        secretStore.encrypt(json.encodeToString(servers))

    /** 解密并反序列化服务器列表；兼容旧版明文 JSON（下次写入会自动加密）。 */
    private fun decodeServers(encoded: String?): List<ServerConfig> {
        if (encoded.isNullOrEmpty()) return emptyList()
        secretStore.decrypt(encoded)?.let { plain ->
            return runCatching { json.decodeFromString<List<ServerConfig>>(plain) }.getOrElse {
                Log.e(TAG, "Failed to decode servers", it)
                emptyList()
            }
        }
        // 旧格式明文 JSON（迁移期兼容）。
        return runCatching { json.decodeFromString<List<ServerConfig>>(encoded) }.getOrElse {
            // 既非旧明文 JSON，也无法用 Keystore 密钥解密：多为换机/云备份恢复后
            // Keystore 密钥缺失导致的密文不可解。显式告警，避免「服务器列表静默清空」。
            Log.e(TAG, "Servers payload is neither plaintext JSON nor decryptable (Keystore key missing after restore?)", it)
            emptyList()
        }
    }

    /**
     * 严格解码：用于写路径。若存储的密文既非明文 JSON 也无法用 Keystore 解密
     * （换机/云备份恢复后密钥缺失），返回 null —— 调用方必须中止写入，禁止
     * 把空列表写回覆盖掉现有服务器配置（否则一次瞬时 Keystore 抖动即全量清空）。
     */
    private fun decodeServersStrict(encoded: String?): List<ServerConfig>? {
        if (encoded.isNullOrEmpty()) return emptyList()
        secretStore.decrypt(encoded)?.let { plain ->
            return runCatching { json.decodeFromString<List<ServerConfig>>(plain) }
                .getOrNull() ?: run {
                    Log.e(TAG, "Failed to decode servers (strict)")
                    null
                }
        }
        return runCatching { json.decodeFromString<List<ServerConfig>>(encoded) }
            .getOrNull() ?: run {
            // 既非旧明文 JSON，也无法用 Keystore 密钥解密：多为换机/云备份恢复后
            // Keystore 密钥缺失导致的密文不可解。写路径必须中止，避免静默清空。
            Log.e(TAG, "Servers payload is neither plaintext JSON nor decryptable (strict)")
            null
        }
    }

    /** 写路径统一入口：读改写前先严格解码，不可解则抛异常中止本次写入。 */
    private fun readServersStrict(preferences: Preferences): List<ServerConfig> =
        decodeServersStrict(preferences[serversKey]) ?: throw ServerStorageException(
            "Refusing to overwrite undecryptable server list; restore from backup."
        )

    private fun readServers(preferences: Preferences): List<ServerConfig> =
        decodeServers(preferences[serversKey])
}

/** 服务器列表存储异常：写路径遇不可解密数据时抛出，避免静默清空配置。 */
class ServerStorageException(message: String) : RuntimeException(message)
