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
     * 每台服务器解析后的「直连 opencode」连接：SSH 隧道模式下为 `127.0.0.1:localPort`，
     * 否则为配置的 opencode 地址。Git 页 / 终端等 shell 消费者应优先用它而不是裸 `serverUrl`，
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
}
