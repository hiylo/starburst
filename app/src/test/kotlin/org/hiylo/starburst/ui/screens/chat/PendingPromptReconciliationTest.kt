/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : PendingPromptReconciliationTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.hiylo.starburst.data.api.PromptPart
import org.hiylo.starburst.data.repository.PendingPromptRecord
import org.hiylo.starburst.domain.model.Message
import org.hiylo.starburst.domain.model.MessageWithParts
import org.hiylo.starburst.domain.model.TimeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingPromptReconciliationTest {
    @Test
    fun `missing pending prompt expires when authoritative window covers its id`() {
        val pending = pending("msg_0200", createdAt = 1_000)
        val authoritative = listOf(message("msg_0100"), message("msg_0300"))

        assertEquals(
            setOf(pending.messageId),
            missingPendingPromptIds(
                pending = listOf(pending),
                authoritative = authoritative,
                now = 20_000,
                minimumAgeMs = 10_000,
            ),
        )
    }

    @Test
    fun `pending prompt remains when history window does not reach its id`() {
        val pending = pending("msg_0100", createdAt = 1_000)

        assertTrue(
            missingPendingPromptIds(
                pending = listOf(pending),
                authoritative = listOf(message("msg_0200"), message("msg_0300")),
                now = 20_000,
                minimumAgeMs = 10_000,
            ).isEmpty(),
        )
    }

    @Test
    fun `confirmed pending prompt never expires`() {
        val pending = pending("msg_0200", createdAt = 1_000)

        assertTrue(
            missingPendingPromptIds(
                pending = listOf(pending),
                authoritative = listOf(message(pending.messageId)),
                now = 20_000,
                minimumAgeMs = 0,
            ).isEmpty(),
        )
    }

    private fun pending(id: String, createdAt: Long) = PendingPromptRecord(
        messageId = id,
        sessionId = "session",
        parts = listOf(PromptPart(type = "text", text = "prompt")),
        createdAt = createdAt,
    )

    private fun message(id: String) = MessageWithParts(
        info = Message.User(
            id = id,
            sessionId = "session",
            time = TimeInfo(created = 1_000),
        ),
        parts = emptyList(),
    )
}
