/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BackendSyncTransportTest.kt
 * Date : 2026/09/22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.data.sync

import org.hiylo.starburst.data.repository.SyncBackend
import org.hiylo.starburst.data.repository.SyncConfig
import org.hiylo.starburst.data.repository.SyncTargetConfig
import org.hiylo.starburst.data.repository.requireSingleSyncStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendSyncTransportTest {
    @Test
    fun backendTargetMapsUrlAndTokenWhenSelected() {
        val config = SyncConfig(
            primaryBackend = SyncBackend.BACKEND,
            backendUrl = "https://backend.example",
            backendToken = "ocb_sync_token",
        )

        val target = config.target(SyncBackend.BACKEND)
        assertTrue(target.enabled)
        assertEquals("https://backend.example", target.endpoint)
        assertEquals("ocb_sync_token", target.username)
    }

    @Test
    fun backendIsNotEnabledWhenAnotherStorageIsPrimary() {
        val config = SyncConfig(
            primaryBackend = SyncBackend.GIST,
            gist = SyncTargetConfig(enabled = true, endpoint = "a1b2c3d4e5"),
            backendUrl = "https://backend.example",
            backendToken = "ocb_sync_token",
        )

        assertFalse(config.target(SyncBackend.BACKEND).enabled)
        assertEquals(listOf(SyncBackend.GIST), config.enabledBackends)
    }

    @Test
    fun backendRequiresUrlToBeEnabled() {
        val config = SyncConfig(
            primaryBackend = SyncBackend.BACKEND,
            backendUrl = "",
            backendToken = "ocb_sync_token",
        )

        assertFalse(config.target(SyncBackend.BACKEND).enabled)
        assertTrue(config.enabledBackends.isEmpty())
    }

    @Test
    fun backendCanBeSelectedExclusively() {
        val config = SyncConfig(
            primaryBackend = SyncBackend.BACKEND,
            backendUrl = "https://backend.example",
            backendToken = "ocb_sync_token",
        )

        requireSingleSyncStorage(config)
        assertEquals(listOf(SyncBackend.BACKEND), config.enabledBackends)
        assertEquals("https://backend.example", config.target(SyncBackend.BACKEND).endpoint)
    }

    @Test
    fun backendCountsAsSecondStorageWhenMixedWithGist() {
        val mixed = SyncConfig(
            primaryBackend = SyncBackend.BACKEND,
            gist = SyncTargetConfig(enabled = true, endpoint = "a1b2c3d4e5"),
            backendUrl = "https://backend.example",
            backendToken = "ocb_sync_token",
        )

        assertThrows(IllegalArgumentException::class.java) {
            requireSingleSyncStorage(mixed)
        }
    }

    @Test
    fun backendRevisionRoundTripsThroughRemoteSyncFile() {
        val file = RemoteSyncFile(
            content = "payload",
            revision = "7",
            resolvedEndpoint = "https://backend.example/api/sync?key=global",
        )

        assertEquals("payload", file.content)
        assertEquals("7", file.revision)
        assertEquals("https://backend.example/api/sync?key=global", file.resolvedEndpoint)
    }
}
