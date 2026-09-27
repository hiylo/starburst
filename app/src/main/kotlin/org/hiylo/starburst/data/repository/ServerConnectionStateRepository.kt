/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ServerConnectionStateRepository.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.repository

import org.hiylo.starburst.data.api.ServerConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerConnectionStateRepository @Inject constructor() {
    private val _connectedServerIds = MutableStateFlow<Set<String>>(emptySet())
    val connectedServerIds: StateFlow<Set<String>> = _connectedServerIds.asStateFlow()

    /**
     * 每台服务器解析后的「直连服务器」连接：SSH 隧道模式下为 `127.0.0.1:localPort`，
     * 否则为配置的服务器地址。Git 页 / 终端等 shell 消费者应优先用它而不是裸 `serverUrl`，
     * 否则手机在蜂窝/VPN 下连不上配置地址（请求永远到不了服务器，页面空白无转圈）。
     */
    private val _resolvedDirectConnections = MutableStateFlow<Map<String, ServerConnection>>(emptyMap())
    val resolvedDirectConnections: StateFlow<Map<String, ServerConnection>> = _resolvedDirectConnections.asStateFlow()

    fun updateConnectedServerIds(serverIds: Set<String>) {
        _connectedServerIds.value = serverIds
    }

    /** 由连接服务发布最新解析连接；传空表清空（全部断开时）。 */
    fun updateResolvedDirectConnections(connections: Map<String, ServerConnection>) {
        _resolvedDirectConnections.value = connections
    }

    /**
     * 按 serverId 取已解析直连连接；serverId 为空/未命中时，按 baseUrl 匹配兜底
     * （部分屏以 URL 作为 serverId fallback，resolved 表按 DB serverId 键控会 miss，
     * 导致对 token 鉴权的 V2 服务器请求无 Bearer → 401 → 列表空）。
     */
    fun resolvedConnectionFor(serverId: String, fallbackBaseUrl: String): ServerConnection? {
        val resolved = _resolvedDirectConnections.value
        if (serverId.isNotBlank()) {
            resolved[serverId]?.let { return it }
        }
        if (fallbackBaseUrl.isNotBlank()) {
            val trimmed = fallbackBaseUrl.trimEnd('/')
            return resolved.values.firstOrNull { it.baseUrl.trimEnd('/') == trimmed }
        }
        return null
    }
}
