/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : PatchVisibilityResolver.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.hiylo.starburst.domain.model.Message
import org.hiylo.starburst.domain.model.Part

internal fun suppressRepeatedPatchCards(messages: List<ChatMessage>): List<ChatMessage> {
    var lastVisiblePatchHash: String? = null

    return messages.map { chatMessage ->
        val assistantMessage = chatMessage.message as? Message.Assistant
        if (assistantMessage == null) {
            chatMessage
        } else {
            val filteredParts = buildList {
                for (part in chatMessage.parts) {
                    if (part is Part.Patch) {
                        val normalizedHash = part.hash.trim()
                        val isRepeatedPatch = normalizedHash.isNotEmpty() && normalizedHash == lastVisiblePatchHash
                        if (!isRepeatedPatch) {
                            add(part)
                            if (normalizedHash.isNotEmpty()) {
                                lastVisiblePatchHash = normalizedHash
                            }
                        }
                    } else {
                        add(part)
                    }
                }
            }
            chatMessage.copy(parts = filteredParts)
        }
    }
}
