/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : EventReducerMessageExt.kt
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.data.repository

import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.BuildConfig
import org.hiylo.starburst.domain.model.*
import kotlinx.coroutines.flow.update

private const val TAG = "EventReducer"

/**
 * 消息与 part 的归并写入，以及高频 text delta 的累积缓冲与定时 flush。
 * 从 EventReducer 原样搬移为同包扩展函数，无行为变更。
 */

// ============ Message Events ============

internal fun EventReducer.handleMessageUpdated(event: SseEvent.MessageUpdated) {
    if (isMessageRemoved(event.info.id)) return
    val sessionId = event.info.sessionId
    recordLastUserMessage(event.info)
    _messages.update { current ->
        val sessionMessages = current[sessionId]?.toMutableList() ?: mutableListOf()
        val existingIndex = sessionMessages.indexOfFirst { it.id == event.info.id }
        
        if (existingIndex >= 0) {
            sessionMessages[existingIndex] = event.info
        } else {
            sessionMessages.add(event.info)
            sessionMessages.sortBy { it.time.created }
        }
        
        current + (sessionId to sessionMessages)
    }
}

internal fun EventReducer.recordLastUserMessage(message: Message) {
    if (message !is Message.User) return
    val created = message.time.created
    _lastUserMessageAt.update { current ->
        if (created > (current[message.sessionId] ?: 0L)) {
            current + (message.sessionId to created)
        } else {
            current
        }
    }
}

internal fun EventReducer.handleMessageRemoved(event: SseEvent.MessageRemoved) {
    synchronized(removedMessageLock) { removedMessageSessions[event.messageId] = event.sessionId }
    _messages.update { current ->
        val sessionMessages = current[event.sessionId]?.filter { it.id != event.messageId }
        if (sessionMessages != null) {
            if (sessionMessages.isEmpty()) current - event.sessionId else current + (event.sessionId to sessionMessages)
        } else {
            current
        }
    }
    _parts.update { it - event.messageId }
    synchronized(deltaLock) {
        pendingDeltas.keys.removeAll { it.messageId == event.messageId }
    }
}

internal fun EventReducer.isMessageRemoved(messageId: String): Boolean =
    synchronized(removedMessageLock) { messageId in removedMessageSessions }

// ============ Part Events ============

internal fun EventReducer.handleMessagePartUpdated(event: SseEvent.MessagePartUpdated) {
    val messageId = event.part.messageId
    if (isMessageRemoved(messageId)) return
    val key = PendingDeltaKey(event.part.sessionId, messageId, event.part.id)
    // message.part.updated 携带的是 part 的权威全量文本，未刷的流式 delta 已包含其中，
    // 不能叠加（否则结尾重复）；只有 part 尚不存在时早到的 delta（pendingDeltas）才需要合并。
    synchronized(deltaLock) {
        val pending = pendingDeltas.remove(key)?.toString().orEmpty()
        deltaAccumulator.remove(key)
        val updatedPart = if (pending.isNotEmpty()) {
            applyTextDelta(event.part, pending)
        } else {
            event.part
        }
        _parts.update { current ->
            val messageParts = current[messageId]?.toMutableList() ?: mutableListOf()
            val existingIndex = messageParts.indexOfFirst { it.id == updatedPart.id }

            if (existingIndex >= 0) {
                messageParts[existingIndex] = updatedPart
            } else {
                messageParts.add(updatedPart)
            }

            current + (messageId to messageParts)
        }
    }
}

/**
 * 全量替换 part（start/end 事件）：携带的全量文本不应再叠加残留 delta，
 * 否则 50ms flush 前到达的 ended 事件会与未刷的尾部 delta 重复拼接。
 * 调用前清空该 part 的残留缓冲；且 flush 与本次替换都持有 deltaLock，
 * 避免 flush 已快照的 delta 在替换完成后再被追加导致结尾重复。
 */
internal fun EventReducer.handleMessagePartFinal(event: SseEvent.MessagePartUpdated) {
    val messageId = event.part.messageId
    if (isMessageRemoved(messageId)) return
    synchronized(deltaLock) {
        pendingDeltas.keys.remove(PendingDeltaKey(event.part.sessionId, messageId, event.part.id))
        deltaAccumulator.remove(PendingDeltaKey(event.part.sessionId, messageId, event.part.id))
        _parts.update { current ->
            val messageParts = current[messageId]?.toMutableList() ?: mutableListOf()
            val existingIndex = messageParts.indexOfFirst { it.id == event.part.id }
            if (existingIndex >= 0) {
                messageParts[existingIndex] = event.part
            } else {
                messageParts.add(event.part)
            }
            current + (messageId to messageParts)
        }
    }
}

internal fun EventReducer.handleMessagePartDelta(event: SseEvent.MessagePartDelta) {
    if (isMessageRemoved(event.messageId)) return
    if (event.field != "text") {
        if (BuildConfig.DEBUG) Log.d(TAG, "Ignoring unsupported delta field=${event.field} part=${event.partId}")
        return
    }
    val key = PendingDeltaKey(event.sessionId, event.messageId, event.partId)
    // part 尚不存在时走旧缓冲（等 part.updated 事件到达再合并）。
    val partExists = _parts.value[event.messageId]?.any { it.id == event.partId } == true
    if (!partExists) {
        bufferDelta(event)
        return
    }
    // part 已存在：delta 累积到 buffer，由定时 flush 一次性合并，避免每次 delta 复制整段已累计文本（O(n²)）。
    synchronized(deltaLock) {
        deltaAccumulator.getOrPut(key) { StringBuilder() }.append(event.delta)
    }
}

/** 供单元测试同步 flush 累积 delta（生产走 50ms 定时 flush，测试无协程推进）。 */
internal fun EventReducer.flushAccumulatedDeltasForTest() {
    flushAccumulatedDeltas()
}

/** 把累积的 delta 一次性合并进 _parts（每次合并只复制一次整段文本）。 */
internal fun EventReducer.flushAccumulatedDeltas() {
    // 拿快照与写入 _parts 必须在同一把锁内完成：若先快照后释放锁再写入，
    // handleMessagePartFinal/Updated 的全量替换可能挤进来，把已含这些 delta 的全量
    // 文本写好后 flush 再把旧快照追加一遍，导致结尾内容重复（1,2,3→1,1,2）。
    synchronized(deltaLock) {
        if (deltaAccumulator.isEmpty()) return
        val snapshot = deltaAccumulator.entries.map { it.key to it.value.toString() }.also { deltaAccumulator.clear() }
        if (snapshot.isEmpty()) return
        _parts.update { current ->
            var updated = current
            for ((key, text) in snapshot) {
                val messageParts = updated[key.messageId]?.toMutableList() ?: continue
                val idx = messageParts.indexOfFirst { it.id == key.partId }
                if (idx < 0) continue
                val part = messageParts[idx]
                messageParts[idx] = applyTextDelta(part, text)
                updated = updated + (key.messageId to messageParts)
            }
            updated
        }
    }
}

internal fun EventReducer.handleMessagePartRemoved(event: SseEvent.MessagePartRemoved) {
    _parts.update { current ->
        val messageParts = current[event.messageId]?.filter { it.id != event.partId }
        if (messageParts != null) {
            if (messageParts.isEmpty()) current - event.messageId else current + (event.messageId to messageParts)
        } else {
            current
        }
    }
    synchronized(deltaLock) {
        pendingDeltas.remove(PendingDeltaKey(event.sessionId, event.messageId, event.partId))
        deltaAccumulator.remove(PendingDeltaKey(event.sessionId, event.messageId, event.partId))
    }
}

internal fun EventReducer.applyTextDelta(part: Part, delta: String): Part = when (part) {
    is Part.Text -> part.copy(text = part.text + delta)
    is Part.Reasoning -> part.copy(text = part.text + delta)
    else -> part
}

internal fun EventReducer.bufferDelta(event: SseEvent.MessagePartDelta) {
    synchronized(deltaLock) {
        val key = PendingDeltaKey(event.sessionId, event.messageId, event.partId)
        if (key !in pendingDeltas && pendingDeltas.size >= MAX_PENDING_DELTA_KEYS) {
            pendingDeltas.remove(pendingDeltas.keys.first())
        }
        val buffer = pendingDeltas.getOrPut(key) { StringBuilder() }
        val available = MAX_PENDING_DELTA_CHARS - buffer.length
        if (available > 0) buffer.append(event.delta.take(available))
    }
}
