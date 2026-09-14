/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ServerRepositoryMergeTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.repository

import org.hiylo.starburst.data.sync.SyncServer
import org.hiylo.starburst.domain.model.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerRepositoryMergeTest {
    @Test
    fun `sync merge keeps runtime state and remaps colliding IDs`() {
        val current = listOf(
            ServerConfig(
                id = "local-id",
                url = "https://existing.example/",
                password = "local-secret",
                lastConnected = 42,
                isHealthy = true,
            ),
            ServerConfig(id = "occupied", url = "https://other.example"),
        )
        val result = mergeSyncServers(
            current = current,
            remote = listOf(
                SyncServer("remote-existing", "https://existing.example", username = "remote-user"),
                SyncServer("occupied", "https://new.example", username = "new-user"),
            ),
            passwords = emptyMap(),
            idGenerator = { "generated" },
        )

        val existing = result.servers.single { it.id == "local-id" }
        assertEquals("local-secret", existing.password)
        assertEquals(42L, existing.lastConnected)
        assertTrue(existing.isHealthy)
        assertEquals("remote-user", existing.username)
        assertEquals("local-id", result.idMapping["remote-existing"])
        assertEquals("generated", result.idMapping["occupied"])
        assertFalse(result.servers.any { it.id == "occupied" && it.url == "https://new.example" })
    }

    @Test
    fun `sync merge does not silently delete existing duplicate endpoints`() {
        val current = listOf(
            ServerConfig(id = "first", url = "https://same.example", name = "First"),
            ServerConfig(id = "second", url = "https://same.example/", name = "Second"),
        )

        val result = mergeSyncServers(
            current = current,
            remote = listOf(SyncServer("remote", "https://unrelated.example")),
            passwords = emptyMap(),
        )

        assertEquals(listOf("first", "second", "remote"), result.servers.map(ServerConfig::id))
    }

    @Test
    fun `sync snapshot excludes local runtime server`() {
        val servers = portableSyncServers(
            listOf(
                ServerConfig(
                    id = "local",
                    url = " http://127.0.0.1:4096/ ",
                    username = "device-user",
                    password = "device-secret",
                ),
                ServerConfig(id = "remote", url = "https://example.com/", username = "remote-user"),
            ),
        )

        assertEquals(listOf("remote"), servers.map(SyncServer::id))
        assertEquals("https://example.com", servers.single().url)
    }

    @Test
    fun `sync import ignores local runtime server from older payload`() {
        val currentLocal = ServerConfig(
            id = "local-device",
            url = LEGACY_LOCAL_SERVER_URL,
            username = "device-user",
            password = "device-secret",
            autoConnect = false,
        )

        val result = mergeSyncServers(
            current = listOf(currentLocal),
            remote = listOf(
                SyncServer(
                    id = "remote-local",
                    url = "http://127.0.0.1:4096/",
                    username = "other-device-user",
                    autoConnect = true,
                ),
            ),
            passwords = mapOf("remote-local" to "other-device-secret"),
        )

        assertEquals(listOf(currentLocal), result.servers)
        assertTrue(result.idMapping.isEmpty())
    }
}
