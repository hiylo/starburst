/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : TaskKindMappingTest.kt
 * Date : 2026/09/27 09:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hiylo.starburst.data.api.BackendCreateTaskRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归「任务类型呢？」——后端 `tasks.kind` 一直存在（`store.Task.Kind`，注释即
 * 「任务类型」），但 App 的 [BackendTask] 从未读取，用户看不到任务是什么类型。
 *
 * 同时锁定一条硬约束：**App 只读不写 kind**。后端 `tasks/executor.go` 以
 * `t.Kind != ""` 作为「不走 OpenCode prompt 路径」的开关，且仅 `doc-generate`
 * 有专用 runner——一旦回传自定义 kind，任务会直接无法执行。
 */
class TaskKindMappingTest {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun decodesKindAndWorkflowIdFromBackendTask() {
        val body = """
            {"id":"tsk_1","status":"queued","prompt":"跑单测",
             "kind":"test-run","workflowId":"wf_9","priority":70,"timeoutSeconds":600}
        """.trimIndent()

        val task = json.decodeFromString<BackendTask>(body)

        assertEquals("test-run", task.kind)
        assertEquals("wf_9", task.workflowId)
        assertEquals(70, task.priority)
        assertEquals(600, task.timeoutSeconds)
    }

    @Test
    fun missingKindDefaultsToEmpty_whichMeansAgentOrchestrated() {
        val task = json.decodeFromString<BackendTask>("""{"id":"tsk_2","prompt":"改个 bug"}""")
        assertEquals("", task.kind)
        assertNull(task.workflowId)
    }

    @Test
    fun workflowIdPresenceIdentifiesMultiStepPlan() {
        val single = json.decodeFromString<BackendTask>("""{"id":"a","prompt":"x"}""")
        val planStep = json.decodeFromString<BackendTask>("""{"id":"b","prompt":"y","workflowId":"wf_1"}""")
        assertTrue(single.workflowId.isNullOrBlank())
        assertTrue(!planStep.workflowId.isNullOrBlank())
    }

    @Test
    fun createTaskRequestDoesNotCarryKind() {
        // 回归护栏：创建任务的请求体不得包含 kind，否则新任务会被后端路由到
        // executeTracked（无 runner）而永远不执行。
        val encoded = json.encodeToString(
            BackendCreateTaskRequest(prompt = "跑单测", name = "t", sessionId = "ses_1", directory = "/w"),
        )
        assertTrue("创建请求体不得含 kind", !encoded.contains("\"kind\""))
    }

    @Test
    fun createTaskRequestCarriesWorkflowIdForPlanSteps() {
        // 回归：App 的 createPlan 此前只靠 dependsOn 串链、不传 workflowId，
        // 导致后端 store.Task.WorkflowID 一直为空、计划步骤无法与独立任务区分。
        val encoded = json.encodeToString(
            BackendCreateTaskRequest(
                prompt = "第 1 步",
                dependsOn = null,
                workflowId = "wf_abc123",
            ),
        )
        assertTrue(encoded.contains("\"workflowId\":\"wf_abc123\""))
    }

    @Test
    fun createTaskRequestOmitsWorkflowIdWhenAbsent() {
        // 独立任务不应带空 workflowId（后端以空串表示「独立任务」）。
        val encoded = json.encodeToString(BackendCreateTaskRequest(prompt = "单个任务"))
        assertTrue("独立任务不应携带 workflowId", !encoded.contains("workflowId"))
    }
}
