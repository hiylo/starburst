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
    // message.part.updated 携带的是 part 的权威全量文本。
    //
    // 早到的 delta（part 尚未建立时进了 pendingDeltas）**只有在权威全量里还没有它时**
    // 才补上：agent 的 part.updated 往往已经包含这些字符（它由服务端同一份文本生成），
    // 无条件 append 会在结尾重复一遍——这正是「同一条消息显示两遍」的成因之一。
    // 判定用「权威文本是否已以该 delta 结尾」：已包含则丢弃，否则补齐。
    synchronized(deltaLock) {
        val pending = pendingDeltas.remove(key)?.toString().orEmpty()
        deltaAccumulator.remove(key)
        val updatedPart = if (pending.isNotEmpty() && !partTextEndsWith(event.part, pending)) {
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

/**
 * 权威全量文本是否已以 [delta] 结尾（即已包含这段缓冲内容）。
 *
 * 为空文本视为「尚未包含」——此时必须补上，否则早到的 delta 会整段丢失。
 */
internal fun partTextEndsWith(part: Part, delta: String): Boolean {
    val text = when (part) {
        is Part.Text -> part.text
        is Part.Reasoning -> part.text
        else -> return false
    }
    if (delta.isEmpty()) return true
    return text.endsWith(delta)
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

// ============ Compaction Events（V2 /api/session/{id}/compact） ============

/**
 * 压缩开始：为一个压缩消息建立占位（避免 ended 到达前 UI 抖动），无实质内容。
 * 压缩完成后会立刻收到 [SseEvent.NextCompactionEnded] 并写入分隔条。
 */
internal fun EventReducer.handleNextCompactionStarted(event: SseEvent.NextCompactionStarted) {
    val messageId = event.messageId
    if (messageId.isBlank()) return
    val existing = _parts.value[messageId]?.filterIsInstance<Part.Text>()
        ?.any { it.id == "$messageId-compacting" } ?: false
    if (existing) return
    handleMessagePartUpdated(SseEvent.MessagePartUpdated(Part.Text(
        "$messageId-compacting", event.sessionId, messageId, "",
        time = Part.Text.Time(event.timestamp),
    )))
}

/**
 * 压缩摘要流式增量：逐段累积进「正在压缩」占位 part（$messageId-compacting），
 * 让用户在压缩期间看到实时摘要文本，而非空白占位；ended 到达后分隔条覆盖。
 */
internal fun EventReducer.handleNextCompactionDelta(event: SseEvent.NextCompactionDelta) {
    if (event.messageId.isBlank() || event.delta.isBlank()) return
    handleNextCompactionStarted(SseEvent.NextCompactionStarted(event.sessionId, event.messageId, "auto", event.timestamp))
    handleMessagePartDelta(SseEvent.MessagePartDelta(
        sessionId = event.sessionId,
        messageId = event.messageId,
        partId = "${event.messageId}-compacting",
        field = "text",
        delta = event.delta,
    ))
}

/**
 * 回合重试事件：与 `session.status{retry}` 并存，用于补齐重试消息文案。
 * 已是 Retry 时仅刷新 message（保留 attempt/next 时序），否则以 attempt 建立 Retry。
 */
internal fun EventReducer.handleNextRetried(event: SseEvent.NextRetried) {
    if (event.sessionId.isBlank()) return
    _sessionStatuses.update { statuses ->
        val current = statuses[event.sessionId]
        val retry = when (current) {
            is SessionStatus.Retry ->
                current.copy(message = event.message.takeIf { it.isNotBlank() } ?: current.message)
            else -> SessionStatus.Retry(event.attempt, event.message, 0)
        }
        statuses + (event.sessionId to retry)
    }
}

/**
 * 压缩完成：在会话中插入「已总结」分隔条（User 消息 + [Part.Compaction]），
 * 供 ChatScreenMessageBody 渲染 divider + 回退入口，与 V1 压缩体验一致。
 */
internal fun EventReducer.handleNextCompactionEnded(event: SseEvent.NextCompactionEnded) {
    val messageId = event.messageId
    if (messageId.isBlank()) return
    _messages.update { current ->
        val sessionMessages = current[event.sessionId]?.toMutableList() ?: mutableListOf()
        val exists = sessionMessages.any { it.id == messageId }
        if (!exists) {
            sessionMessages.add(Message.User(
                id = messageId,
                sessionId = event.sessionId,
                time = TimeInfo(event.timestamp),
            ))
            sessionMessages.sortBy { it.time.created }
            current + (event.sessionId to sessionMessages)
        } else {
            current
        }
    }
    _parts.update { current ->
        val parts = (current[messageId] ?: emptyList())
            .filterNot { it.id == "$messageId-compacting" } // 移除流式摘要占位，避免与分隔条重复
            .toMutableList()
        if (parts.none { it is Part.Compaction }) {
            parts.add(0, Part.Compaction(id = "$messageId-compaction", sessionId = event.sessionId, messageId = messageId))
            current + (messageId to parts)
        } else {
            current
        }
    }
}
