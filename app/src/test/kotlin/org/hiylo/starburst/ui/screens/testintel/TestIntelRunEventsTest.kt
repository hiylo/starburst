/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : TestIntelRunEventsTest.kt
 * Date : 2026/09/22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.testintel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.hiylo.starburst.data.api.IntelTestRun
import org.hiylo.starburst.data.backend.IntelRunEventBus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** `mergeRunSnapshot` 与 `IntelRunEventBus` 的单元测试：run 推送自动更新的数据层。 */
class TestIntelRunEventsTest {

    private fun run(id: Long, projectId: Long = 1L, status: String = "running"): IntelTestRun =
        IntelTestRun(
            id = id,
            projectId = projectId,
            moduleId = 0,
            scope = "module",
            kind = "go",
            command = "go test",
            status = status,
        )

    @Test
    fun `merge appends new run at top`() {
        val existing = listOf(run(1), run(2))
        val merged = mergeRunSnapshot(existing, run(3))
        assertEquals(listOf(3L, 1L, 2L), merged.map { it.id })
    }

    @Test
    fun `merge replaces run with same id keeping order`() {
        val existing = listOf(run(1, status = "queued"), run(2))
        val updated = run(1, status = "passed")
        val merged = mergeRunSnapshot(existing, updated)
        assertEquals(listOf(1L, 2L), merged.map { it.id })
        assertSame(updated, merged[0])
    }

    @Test
    fun `merge into empty list prepends`() {
        val merged = mergeRunSnapshot(emptyList(), run(9))
        assertEquals(listOf(9L), merged.map { it.id })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `event bus delivers published run snapshot`() = runTest {
        val received = mutableListOf<IntelTestRun>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            IntelRunEventBus.runs.collect { received += it }
        }
        IntelRunEventBus.publish(run(42, status = "failed"))
        IntelRunEventBus.publish(run(43))
        advanceUntilIdle()
        assertEquals(listOf(42L, 43L), received.map { it.id })
        assertEquals("failed", received.first().status)
    }
}
