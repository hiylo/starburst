/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BackupPayloadTest.kt
 * Date : 2026/09/17 10:05:36
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.backup

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hiylo.starburst.data.repository.SettingsRepository
import org.hiylo.starburst.data.sync.PasswordCrypto
import org.hiylo.starburst.domain.model.ServerConfig
import org.hiylo.starburst.domain.model.SessionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPayloadTest {
    private val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun samplePayload() = BackupPayload(
        version = BackupPayload.VERSION,
        createdAt = 1_700_000_000_000L,
        chatLineHeight = 1.3f,
        sessionCategories = listOf(
            SessionCategory(id = "cat-1", name = "Work", color = "#FF0000", icon = "work"),
        ),
        promptTemplates = listOf(
            SettingsRepository.PromptTemplate(id = "tpl-1", name = "Review", prompt = "Review this code"),
        ),
        customCommands = listOf(
            SettingsRepository.CustomCommand(name = "fix", prompt = "Fix the bug"),
        ),
        llmProviderBaseUrl = "https://llm.example.test",
        llmProviderModel = "model-x",
        servers = listOf(
            ServerConfig(id = "server-1", url = "http://127.0.0.1:4096", name = "Local"),
        ),
        serverSavedPaths = mapOf("server-1" to listOf("/tmp", "/workspaces")),
        hiddenModels = mapOf("server-1" to setOf("provider:model")),
    )

    @Test
    fun backupPayloadRoundTripsThroughJson() {
        val payload = samplePayload()

        val restored = json.decodeFromString<BackupPayload>(json.encodeToString(payload))

        assertEquals(BackupPayload.VERSION, restored.version)
        assertEquals(1_700_000_000_000L, restored.createdAt)
        assertEquals(1.3f, restored.chatLineHeight)
        assertEquals("Work", restored.sessionCategories.single().name)
        assertEquals("Review", restored.promptTemplates.single().name)
        assertEquals("fix", restored.customCommands.single().name)
        assertEquals("https://llm.example.test", restored.llmProviderBaseUrl)
        assertEquals("model-x", restored.llmProviderModel)
        assertEquals("server-1", restored.servers.single().id)
        assertEquals(listOf("/tmp", "/workspaces"), restored.serverSavedPaths["server-1"])
        assertEquals(setOf("provider:model"), restored.hiddenModels.getValue("server-1"))
    }

    @Test
    fun encryptedEnvelopeRoundTripsThroughJson() {
        val payload = samplePayload()
        val plaintext = json.encodeToString(payload).toByteArray(Charsets.UTF_8)

        val envelope = PasswordCrypto.encrypt(plaintext, "backup passphrase".toCharArray())
        val file = BackupFile(createdAt = 1_700_000_000_000L, encrypted = envelope)

        val restoredFile = json.decodeFromString<BackupFile>(json.encodeToString(file))
        val restoredPlaintext = PasswordCrypto.decrypt(restoredFile.encrypted, "backup passphrase".toCharArray())
        val restoredPayload = json.decodeFromString<BackupPayload>(restoredPlaintext.decodeToString())

        assertEquals(BackupFile.VERSION, restoredFile.version)
        assertEquals("Review", restoredPayload.promptTemplates.single().name)
        assertEquals("server-1", restoredPayload.servers.single().id)
    }

    @Test
    fun encryptedEnvelopeRejectsWrongPassphrase() {
        val plaintext = json.encodeToString(samplePayload()).toByteArray(Charsets.UTF_8)
        val envelope = PasswordCrypto.encrypt(plaintext, "correct passphrase".toCharArray())
        val file = BackupFile(encrypted = envelope)

        val restoredFile = json.decodeFromString<BackupFile>(json.encodeToString(file))

        assertThrows(IllegalArgumentException::class.java) {
            PasswordCrypto.decrypt(restoredFile.encrypted, "wrong passphrase".toCharArray())
        }
    }

    @Test
    fun defaultPayloadUsesCurrentVersion() {
        assertTrue(BackupPayload().version == BackupPayload.VERSION)
        assertEquals(1, BackupPayload.VERSION)
        assertEquals(1, BackupFile.VERSION)
    }
}
