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
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import org.hiylo.starburst.data.api.OpenCodeApi
import org.hiylo.starburst.data.api.ServerConnection
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.domain.model.ServerHealth
import org.hiylo.starburst.data.sync.SyncServer
import org.hiylo.starburst.ui.screens.chat.ServerTerminalRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
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

/**
 * Server Repository - manages saved OpenCode servers
 * 
 * Uses DataStore to persist server configurations
 */
@Singleton
class ServerRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val api: OpenCodeApi,
    private val json: Json
) {
    
    private val serversKey = stringPreferencesKey(SERVERS_KEY)
    
    /**
     * Get all saved servers as Flow
     */
    val servers: Flow<List<ServerConfig>> = dataStore.data.map { preferences ->
        val serversJson = preferences[serversKey] ?: "[]"
        try {
            json.decodeFromString<List<ServerConfig>>(serversJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode servers", e)
            emptyList()
        }
    }
    
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
            preferences[serversKey] = json.encodeToString(readServers(preferences) + server)
        }
        
        return server
    }
    
    /**
     * Update a server
     */
    suspend fun updateServer(server: ServerConfig) {
        dataStore.edit { preferences ->
            preferences[serversKey] = json.encodeToString(readServers(preferences).map {
                if (it.id == server.id) server else it
            })
        }
    }

    suspend fun setAutoConnect(serverId: String, autoConnect: Boolean) {
        dataStore.edit { preferences ->
            preferences[serversKey] = json.encodeToString(readServers(preferences).map { server ->
                if (server.id == serverId) server.copy(autoConnect = autoConnect) else server
            })
        }
    }
    
    /**
     * Delete a server
     */
    suspend fun deleteServer(serverId: String) {
        dataStore.edit { preferences ->
            preferences[serversKey] = json.encodeToString(readServers(preferences).filter { it.id != serverId })
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
            val jsch = JSch()
            val session = jsch.getSession(server.sshUsername, host, server.sshPort)
            session.setPassword(server.sshPassword ?: "")
            session.setConfig("StrictHostKeyChecking", "no")
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
        val result = mergeSyncServers(readServers(preferences), remote, passwords)
        preferences[serversKey] = json.encodeToString(result.servers)
        return result.idMapping
    }
    
    // ============ Private ============
    
    private fun readServers(preferences: Preferences): List<ServerConfig> {
        return preferences[serversKey]?.let { encoded ->
            runCatching { json.decodeFromString<List<ServerConfig>>(encoded) }.getOrDefault(emptyList())
        }.orEmpty()
    }
}
