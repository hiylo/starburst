/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : EventReducerTest.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.opencode.data.repository

import org.hiylo.opencode.domain.model.Session
import org.hiylo.opencode.domain.model.SessionStatus
import org.hiylo.opencode.domain.model.SseEvent
import org.hiylo.opencode.domain.model.Message
import org.hiylo.opencode.domain.model.MessageWithParts
import org.hiylo.opencode.domain.model.Part
import org.hiylo.opencode.domain.model.PendingInteraction
import org.hiylo.opencode.domain.model.TimeInfo
import org.hiylo.opencode.domain.model.ToolState
import org.hiylo.opencode.data.search.MessageFtsIndex
import org.hiylo.opencode.data.search.FtsHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class EventReducerTest {

    /** no-op 全文索引实现：纯 JVM 测试无需 Android SQLite/Context。 */
    private object NoopFtsIndex : MessageFtsIndex {
        override suspend fun index(
            serverId: String,
            sessionId: String,
            messageId: String,
            title: String,
            content: String,
        ) = Unit

        override suspend fun search(query: String, limit: Int, serverId: String?): List<FtsHit> =
            emptyList()

        override suspend fun deleteSession(sessionId: String) = Unit

        override suspend fun clear() = Unit
    }

    private fun reducer(): EventReducer = EventReducer(NoopFtsIndex)


    @Test
    fun sessionCreated_upsertsWithoutReplacingBusyOrRetryStatus() {
        val reducer = reducer()
        val busySession = session("busy", updated = 1)
        val retrySession = session("retry", updated = 1)
        val retry = SessionStatus.Retry(attempt = 2, message = "later", next = 10)

        reducer.processEvent(SseEvent.SessionStatus("busy", SessionStatus.Busy), "server")
        reducer.processEvent(SseEvent.SessionStatus("retry", retry), "server")
        reducer.processEvent(SseEvent.SessionCreated(busySession), "server")
        reducer.processEvent(SseEvent.SessionCreated(retrySession), "server")
        reducer.processEvent(SseEvent.SessionCreated(busySession.copy(title = "updated", time = busySession.time.copy(updated = 2))), "server")

        assertEquals(SessionStatus.Busy, reducer.sessionStatuses.value["busy"])
        assertEquals(retry, reducer.sessionStatuses.value["retry"])
        assertEquals(2, reducer.sessions.value.size)
        assertEquals("updated", reducer.sessions.value.single { it.id == "busy" }.title)
    }

    @Test
    fun sessionUpdated_promotesMostRecentlyActiveSession() {
        val reducer = reducer()
        val older = session("older", updated = 1)
        val newer = session("newer", updated = 2)
        reducer.processEvent(SseEvent.SessionCreated(older), "server")
        reducer.processEvent(SseEvent.SessionCreated(newer), "server")

        reducer.processEvent(
            SseEvent.SessionUpdated(older.copy(time = older.time.copy(updated = 3))),
            "server",
        )

        assertEquals(listOf("older", "newer"), reducer.sessions.value.map(Session::id))
        assertEquals(setOf("older", "newer"), reducer.serverSessions.value["server"])
    }

    @Test
    fun statusAndIdleEvents_establishOwnershipForServerCleanup() {
        val reducer = reducer()

        reducer.processEvent(SseEvent.SessionStatus("busy", SessionStatus.Busy), "server")
        reducer.processEvent(SseEvent.SessionIdle("idle"), "server")

        assertEquals(setOf("busy", "idle"), reducer.serverSessions.value["server"])
        reducer.clearForServer("server")
        assertTrue(reducer.sessionStatuses.value.isEmpty())
        assertFalse(reducer.serverSessions.value.containsKey("server"))
    }

    @Test
    fun sessionDeleted_removesStateAndOwnership() {
        val reducer = reducer()
        val session = session("deleted")
        reducer.processEvent(SseEvent.SessionCreated(session), "server")

        reducer.processEvent(SseEvent.SessionDeleted(session), "server")

        assertTrue(reducer.sessions.value.isEmpty())
        assertNull(reducer.sessionStatuses.value[session.id])
        assertFalse(reducer.serverSessions.value.containsKey("server"))
    }

    @Test
    fun clearForServer_doesNotClearAnotherServersSessions() {
        val reducer = reducer()
        reducer.processEvent(SseEvent.SessionStatus("first", SessionStatus.Busy), "server-1")
        reducer.processEvent(SseEvent.SessionIdle("second"), "server-2")

        reducer.clearForServer("server-1")

        assertNull(reducer.sessionStatuses.value["first"])
        assertEquals(SessionStatus.Idle, reducer.sessionStatuses.value["second"])
        assertEquals(setOf("second"), reducer.serverSessions.value["server-2"])
    }

    @Test
    fun instanceDisposal_preservesPersistedSessionsAndMessages() {
        val reducer = reducer()
        val disposed = session("disposed").copy(directory = "/first")
        val retained = session("retained").copy(directory = "/second")
        reducer.processEvent(SseEvent.SessionCreated(disposed), "server")
        reducer.processEvent(SseEvent.SessionCreated(retained), "server")
        reducer.processEvent(SseEvent.MessageUpdated(Message.User(
            id = "message",
            sessionId = disposed.id,
            time = TimeInfo(created = 1),
        )), "server")

        reducer.processEvent(SseEvent.ServerInstanceDisposed("/first"), "server")

        assertEquals(setOf(disposed, retained), reducer.sessions.value.toSet())
        assertEquals(setOf(disposed.id, retained.id), reducer.serverSessions.value["server"])
        assertEquals(listOf("message"), reducer.messages.value[disposed.id]?.map { it.id })
    }

    @Test
    fun transientServerClear_preservesHistoryButResetsBusyStatus() {
        val reducer = reducer()
        val session = session("session")
        reducer.processEvent(SseEvent.SessionCreated(session), "server")
        reducer.processEvent(SseEvent.SessionStatus(session.id, SessionStatus.Busy), "server")
        reducer.processEvent(SseEvent.MessageUpdated(Message.User(
            id = "message",
            sessionId = session.id,
            time = TimeInfo(created = 1),
        )), "server")

        reducer.clearTransientForServer("server")

        assertEquals(listOf(session), reducer.sessions.value)
        assertEquals(listOf("message"), reducer.messages.value[session.id]?.map { it.id })
        assertEquals(SessionStatus.Idle, reducer.sessionStatuses.value[session.id])
    }

    @Test
    fun directoryScopedEvents_doNotOverwriteAnotherWorkspace() {
        val reducer = reducer()
        val first = DirectoryScope("server", "/project", "workspace-1")
        val second = DirectoryScope("server", "/project", "workspace-2")

        reducer.processEvent(SseEvent.VcsBranchUpdated("main"), "server", first.directory, first.workspaceId)
        reducer.processEvent(SseEvent.VcsBranchUpdated("feature"), "server", second.directory, second.workspaceId)

        assertEquals("main", reducer.vcsBranches.value[first])
        assertEquals("feature", reducer.vcsBranches.value[second])
    }

    @Test
    fun promptLifecycle_tracksAdmissionAndPromotion() {
        val reducer = reducer()
        val admitted = SseEvent.PromptAdmitted("session", "message", "queue")

        reducer.processEvent(admitted, "server")
        assertEquals(PromptDeliveryState.ADMITTED, reducer.promptDeliveries.value["message"]?.state)

        reducer.processEvent(SseEvent.Prompted("session", "message", "queue"), "server")
        assertEquals(PromptDeliveryState.PROMOTED, reducer.promptDeliveries.value["message"]?.state)
    }

    @Test
    fun nextStream_projectsPromptAssistantTextAndToolLifecycle() {
        val reducer = reducer()
        val prompt = buildJsonObject { put("text", "hello") }
        reducer.processEvent(SseEvent.Prompted("session", "user", "steer", prompt, 1), "server")
        reducer.processEvent(SseEvent.NextStepStarted(
            "session",
            "assistant",
            "build",
            buildJsonObject { put("providerID", "provider"); put("modelID", "model") },
            2,
        ), "server")
        reducer.processEvent(SseEvent.NextTextStarted("session", "assistant", "text", 3), "server")
        reducer.processEvent(SseEvent.NextTextDelta("session", "assistant", "text", "answer"), "server")
        reducer.processEvent(SseEvent.NextToolInputStarted("session", "assistant", "call", "bash", 4), "server")
        reducer.processEvent(SseEvent.NextToolCalled(
            "session", "assistant", "call", "bash", buildJsonObject { put("command", "pwd") }, 5,
        ), "server")
        reducer.processEvent(SseEvent.NextToolSuccess(
            "session",
            "assistant",
            "call",
            buildJsonObject { put("exit", 0) },
            buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", "/tmp") }) },
            6,
        ), "server")

        reducer.flushAccumulatedDeltasForTest()
        assertEquals(listOf("user", "assistant"), reducer.messages.value["session"]?.map { it.id })
        assertEquals("answer", reducer.parts.value["assistant"]?.filterIsInstance<Part.Text>()?.single()?.text)
        val tool = reducer.parts.value["assistant"]?.filterIsInstance<Part.Tool>()?.single()
        assertEquals("/tmp", (tool?.state as ToolState.Completed).output)
    }

    @Test
    fun lateToolCalled_preservesRunningSubagentSessionMetadata() {
        val reducer = reducer()
        val metadata = buildJsonObject { put("sessionId", "child") }
        reducer.processEvent(
            SseEvent.MessagePartUpdated(Part.Tool(
                id = "part",
                sessionId = "session",
                messageId = "assistant",
                callId = "call",
                tool = "task",
                state = ToolState.Running(
                    input = buildJsonObject { put("subagent_type", "Deep") },
                    title = "Deep",
                    metadata = metadata,
                ),
            )),
            "server",
        )

        reducer.processEvent(SseEvent.NextToolCalled(
            "session",
            "assistant",
            "call",
            "task",
            buildJsonObject { put("subagent_type", "Deep") },
            5,
        ), "server")

        val state = reducer.parts.value["assistant"]?.single()?.let { it as Part.Tool }?.state as ToolState.Running
        assertEquals("Deep", state.title)
        assertEquals(metadata, state.metadata)
    }

    @Test
    fun lateToolInputEvents_preserveRunningSubagentSessionMetadata() {
        val reducer = reducer()
        val metadata = buildJsonObject { put("sessionId", "child") }
        reducer.processEvent(
            SseEvent.MessagePartUpdated(Part.Tool(
                id = "part",
                sessionId = "session",
                messageId = "assistant",
                callId = "call",
                tool = "task",
                state = ToolState.Running(
                    input = buildJsonObject { put("description", "Audit") },
                    title = "my-custom-reviewer",
                    metadata = metadata,
                ),
            )),
            "server",
        )

        reducer.processEvent(SseEvent.NextToolInputStarted(
            "session", "assistant", "call", "task", 5,
        ), "server")
        reducer.processEvent(SseEvent.NextToolInputEnded(
            "session", "assistant", "call", "{\"description\":\"Audit\"}",
        ), "server")

        val state = reducer.parts.value["assistant"]?.single()?.let { it as Part.Tool }?.state as ToolState.Running
        assertEquals("my-custom-reviewer", state.title)
        assertEquals(metadata, state.metadata)
        assertEquals("Audit", state.input["description"]?.toString()?.trim('"'))
    }

    @Test
    fun pendingRequests_areUpsertedByRequestId() {
        val reducer = reducer()
        val first = SseEvent.PermissionAsked("permission", "session", "read")
        val updated = first.copy(permission = "write")

        reducer.processEvent(first, "server")
        reducer.processEvent(updated, "server")

        assertEquals(listOf(PendingInteraction.Permission(updated)), reducer.pendingInteractions.value)
    }

    @Test
    fun pendingRequests_preserveInterleavedOrderAndUpdateInPlace() {
        val reducer = reducer()
        val permission = SseEvent.PermissionAsked("permission", "session", "read")
        val question = question("question", "session", "Original")
        val updated = question("question", "session", "Updated")

        reducer.processEvent(permission, "server")
        reducer.processEvent(question, "server")
        reducer.processEvent(updated, "server")

        assertEquals(
            listOf(PendingInteraction.Permission(permission), PendingInteraction.Question(updated)),
            reducer.pendingInteractions.value,
        )
    }

    @Test
    fun pendingRequests_withSameIdAndDifferentTypesRemainDistinct() {
        val reducer = reducer()

        reducer.processEvent(SseEvent.PermissionAsked("request", "session", "read"), "server")
        reducer.processEvent(question("request", "session", "Question"), "server")

        assertEquals(2, reducer.pendingInteractions.value.size)
        reducer.removePermission("session", "request")
        assertTrue(reducer.pendingInteractions.value.single() is PendingInteraction.Question)
    }

    @Test
    fun stalePendingSnapshot_doesNotResurrectRepliedRequest() {
        val reducer = reducer()
        reducer.processEvent(SseEvent.SessionCreated(session("session")), "server")
        val request = SseEvent.PermissionAsked("permission", "session", "read")
        reducer.processEvent(request, "server")
        val revision = reducer.pendingSnapshotRevision()
        reducer.processEvent(SseEvent.PermissionReplied("session", "permission"), "server")

        val replaced = reducer.replacePendingRequests(
            serverId = "server",
            permissions = listOf(request),
            questions = emptyList(),
            expectedRevision = revision,
        )

        assertFalse(replaced)
        assertTrue(reducer.pendingInteractions.value.isEmpty())
    }

    @Test
    fun pendingSnapshot_preservesKnownOrderAndAppendsRestOnlyRequestsDeterministically() {
        val reducer = reducer()
        reducer.processEvent(question("existing-question", "session", "Existing"), "server")
        reducer.processEvent(SseEvent.PermissionAsked("existing-permission", "session", "read"), "server")
        val revision = reducer.pendingSnapshotRevision()

        val applied = reducer.replacePendingRequests(
            serverId = "server",
            permissions = listOf(
                SseEvent.PermissionAsked("z", "session", "write"),
                SseEvent.PermissionAsked("existing-permission", "session", "updated"),
                SseEvent.PermissionAsked("a", "session", "read"),
            ),
            questions = listOf(question("existing-question", "session", "Updated")),
            expectedRevision = revision,
        )

        assertTrue(applied)
        assertEquals(
            listOf("existing-question", "existing-permission", "a", "z"),
            reducer.pendingInteractions.value.map { it.id },
        )
    }

    @Test
    fun optimisticQuestionRemoval_invalidatesOlderSnapshot() {
        val reducer = reducer()
        val request = question("question", "session", "Question")
        reducer.processEvent(request, "server")
        val revision = reducer.pendingSnapshotRevision()

        reducer.removeQuestion("session", request.id)

        assertFalse(
            reducer.replacePendingRequests(
                serverId = "server",
                permissions = emptyList(),
                questions = listOf(request),
                expectedRevision = revision,
            ),
        )
        assertTrue(reducer.pendingInteractions.value.isEmpty())
    }

    @Test
    fun sessionDeletion_removesPendingAndInvalidatesOlderSnapshot() {
        val reducer = reducer()
        val session = session("session")
        val request = question("question", session.id, "Question")
        reducer.processEvent(SseEvent.SessionCreated(session), "server")
        reducer.processEvent(request, "server")
        val revision = reducer.pendingSnapshotRevision()

        reducer.processEvent(SseEvent.SessionDeleted(session), "server")

        assertFalse(
            reducer.replacePendingRequests(
                serverId = "server",
                permissions = emptyList(),
                questions = listOf(request),
                expectedRevision = revision,
            ),
        )
        assertTrue(reducer.pendingInteractions.value.isEmpty())
    }

    @Test
    fun clearForServer_removesPendingButKeepsOtherServersQueue() {
        val reducer = reducer()
        val first = question("first", "first-session", "First")
        val second = question("second", "second-session", "Second")
        reducer.processEvent(first, "first-server")
        reducer.processEvent(second, "second-server")

        reducer.clearForServer("first-server")

        assertEquals(listOf(PendingInteraction.Question(second)), reducer.pendingInteractions.value)
    }

    @Test
    fun sessionDeleted_removesMessagesPartsTodosAndErrors() {
        val reducer = reducer()
        val session = session("session")
        val message = Message.User("message", session.id, time = TimeInfo(1))
        val part = Part.Text("part", session.id, message.id, text = "text")
        reducer.processEvent(SseEvent.SessionCreated(session), "server")
        reducer.processEvent(SseEvent.MessageUpdated(message), "server")
        reducer.processEvent(SseEvent.MessagePartUpdated(part), "server")
        reducer.processEvent(
            SseEvent.TodoUpdated(session.id, listOf(SseEvent.TodoUpdated.Todo("todo", "pending", "medium"))),
            "server",
        )
        reducer.processEvent(
            SseEvent.SessionError(session.id, Message.Assistant.ErrorInfo(name = "failed")),
            "server",
        )

        reducer.processEvent(SseEvent.SessionDeleted(session), "server")

        assertNull(reducer.messages.value[session.id])
        assertNull(reducer.parts.value[message.id])
        assertNull(reducer.todos.value[session.id])
        assertNull(reducer.sessionErrors.value[session.id])
    }

    @Test
    fun deltaBeforePart_isReplayedOnceInArrivalOrder() {
        val reducer = reducer()
        reducer.processEvent(SseEvent.MessagePartDelta("session", "message", "part", "text", "one"), "server")
        reducer.processEvent(SseEvent.MessagePartDelta("session", "message", "part", "text", " two"), "server")

        reducer.processEvent(
            SseEvent.MessagePartUpdated(Part.Text("part", "session", "message", text = "start ")),
            "server",
        )

        val part = reducer.parts.value["message"]?.single() as Part.Text
        assertEquals("start one two", part.text)
    }

    @Test
    fun restMerge_doesNotReplaceLongerStreamingTextWithStaleSnapshot() {
        val reducer = reducer()
        val message = Message.Assistant("message", "session", time = TimeInfo(1), parentId = "user")
        reducer.processEvent(SseEvent.MessageUpdated(message), "server")
        reducer.processEvent(
            SseEvent.MessagePartUpdated(Part.Text("part", "session", "message", text = "streamed text")),
            "server",
        )

        reducer.mergeMessages(
            "session",
            listOf(MessageWithParts(message, listOf(Part.Text("part", "session", "message", text = "")))),
        )

        assertEquals("streamed text", (reducer.parts.value["message"]?.single() as Part.Text).text)
    }

    @Test
    fun removedMessage_isNotRestoredByLateSseOrRestSnapshot() {
        val reducer = reducer()
        val message = Message.User("message", "session", time = TimeInfo(1))
        val part = Part.Text("part", "session", message.id, text = "original")
        reducer.processEvent(SseEvent.MessageUpdated(message), "server")
        reducer.processEvent(SseEvent.MessagePartUpdated(part), "server")

        reducer.processEvent(SseEvent.MessageRemoved("session", message.id), "server")
        reducer.processEvent(SseEvent.MessageUpdated(message), "server")
        reducer.processEvent(SseEvent.MessagePartUpdated(part.copy(text = "late")), "server")
        reducer.processEvent(SseEvent.MessagePartDelta("session", message.id, part.id, "text", " delta"), "server")
        reducer.mergeMessages("session", listOf(MessageWithParts(message, listOf(part))))

        assertNull(reducer.messages.value["session"])
        assertNull(reducer.parts.value[message.id])
    }

    @Test
    fun clearingSessionHistory_allowsAuthoritativeReloadAfterRemoval() {
        val reducer = reducer()
        val message = Message.User("message", "session", time = TimeInfo(1))
        reducer.processEvent(SseEvent.MessageRemoved("session", message.id), "server")

        reducer.clearSessionHistory("session")
        reducer.mergeMessages("session", listOf(MessageWithParts(message, emptyList())))

        assertEquals(listOf(message), reducer.messages.value["session"])
    }

    @Test
    fun upsertSession_ignoresOlderStateAfterAuthoritativeRevert() {
        val reducer = reducer()
        val reverted = session("session", updated = 2).copy(revert = Session.Revert("message"))
        reducer.upsertSession("server", reverted)

        reducer.processEvent(SseEvent.SessionUpdated(session("session", updated = 1)), "server")

        assertEquals(reverted, reducer.sessions.value.single())
    }

    @Test
    fun clearSessionHistory_preservesOtherSessionsAndMetadata() {
        val reducer = reducer()
        val first = session("first")
        val second = session("second")
        reducer.setSessions("server", listOf(first, second))
        reducer.mergeMessages(
            first.id,
            listOf(MessageWithParts(
                Message.User("first-message", first.id, time = TimeInfo(1)),
                listOf(Part.Text("first-part", first.id, "first-message", text = "first")),
            )),
        )
        reducer.mergeMessages(
            second.id,
            listOf(MessageWithParts(
                Message.User("second-message", second.id, time = TimeInfo(2)),
                listOf(Part.Text("second-part", second.id, "second-message", text = "second")),
            )),
        )

        reducer.clearSessionHistory(first.id)

        assertNull(reducer.messages.value[first.id])
        assertNull(reducer.parts.value["first-message"])
        assertEquals(listOf("second-message"), reducer.messages.value[second.id]?.map { it.id })
        assertEquals("second", (reducer.parts.value["second-message"]?.single() as Part.Text).text)
        assertEquals(setOf(first, second), reducer.sessions.value.toSet())
    }

    @Test
    fun unknownDeltaField_doesNotMutateText() {
        val reducer = reducer()
        reducer.processEvent(
            SseEvent.MessagePartUpdated(Part.Text("part", "session", "message", text = "original")),
            "server",
        )

        reducer.processEvent(SseEvent.MessagePartDelta("session", "message", "part", "metadata", "bad"), "server")

        val part = reducer.parts.value["message"]?.single() as Part.Text
        assertEquals("original", part.text)
    }

    @Test
    fun statusSnapshot_keepsOmittedSessionsWhileConnected() {
        val reducer = reducer()
        reducer.processEvent(SseEvent.SessionStatus("busy", SessionStatus.Busy), "server")
        reducer.processEvent(SseEvent.SessionStatus("retry", SessionStatus.Retry(1, "later", 2)), "server")

        // Connected (default): the poll must only apply explicitly-reported statuses and
        // leave omitted sessions untouched so a lagging snapshot doesn't clobber a live Busy.
        reducer.replaceSessionStatuses(
            serverId = "server",
            sessionIds = setOf("busy", "retry"),
            statuses = mapOf("retry" to SessionStatus.Retry(2, "again", 3)),
        )

        assertEquals(SessionStatus.Busy, reducer.sessionStatuses.value["busy"])
        assertEquals(SessionStatus.Retry(2, "again", 3), reducer.sessionStatuses.value["retry"])
    }

    @Test
    fun statusSnapshot_clearsOmittedSessionsWhileDisconnected() {
        val reducer = reducer()
        reducer.processEvent(SseEvent.SessionStatus("busy", SessionStatus.Busy), "server")
        reducer.processEvent(SseEvent.SessionStatus("retry", SessionStatus.Retry(1, "later", 2)), "server")

        // Disconnected: with no SSE stream the poll is the only signal, so omitted sessions
        // are reconciled to Idle to clear a stale busy badge.
        reducer.replaceSessionStatuses(
            serverId = "server",
            sessionIds = setOf("busy", "retry"),
            statuses = mapOf("retry" to SessionStatus.Retry(2, "again", 3)),
            connected = false,
        )

        assertEquals(SessionStatus.Idle, reducer.sessionStatuses.value["busy"])
        assertEquals(SessionStatus.Retry(2, "again", 3), reducer.sessionStatuses.value["retry"])
    }

    @Test
    fun sessionError_isRetainedAndEndsBusyState() {
        val reducer = reducer()
        val error = Message.Assistant.ErrorInfo(name = "ProviderError")
        reducer.processEvent(SseEvent.SessionStatus("session", SessionStatus.Busy), "server")

        reducer.processEvent(SseEvent.SessionError("session", error), "server")

        assertEquals(error, reducer.sessionErrors.value["session"])
        assertEquals(SessionStatus.Idle, reducer.sessionStatuses.value["session"])
    }

    private fun session(id: String, updated: Long = 1) = Session(
        id = id,
        title = id,
        time = Session.Time(created = 1, updated = updated),
    )

    private fun question(id: String, sessionId: String, text: String) = SseEvent.QuestionAsked(
        id = id,
        sessionId = sessionId,
        questions = listOf(
            SseEvent.QuestionAsked.Question(
                header = "Header",
                question = text,
                options = listOf(SseEvent.QuestionAsked.Option("Yes", "Confirm")),
            ),
        ),
    )
}
