/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : IntelPushParserTest.kt
 * Date : 2026/09/22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.data.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelPushParserTest {

    @Test
    fun `intel run event parses test run snapshot`() {
        val payload = """{"run":{"id":42,"projectId":1,"moduleId":7,"scope":"module","kind":"go","command":"go test","status":"failed","attempts":1,"priority":10,"createdAt":"2026-09-22T10:00:00Z"}}"""
        val event = IntelPushParser.parse("intel.run.event", payload, "info")
        assertTrue(event is IntelRunParsedEvent)
        val parsed = event as IntelRunParsedEvent
        assertEquals(42L, parsed.run.id)
        assertEquals("failed", parsed.run.status)
        assertEquals(7L, parsed.run.moduleId)
        assertEquals(10, parsed.run.priority)
    }

    @Test
    fun `alert hardware alert state parses metric threshold and severity`() {
        val payload = """{"metric":"cpu","value":92.3,"threshold":90.0,"state":"alert","time":"2026-09-22T04:15:30.123Z"}"""
        val event = IntelPushParser.parse("alert.hardware", payload, "warning")
        assertTrue(event is AlertHardwareParsedEvent)
        val parsed = event as AlertHardwareParsedEvent
        assertEquals("cpu", parsed.event.metric)
        assertEquals(92.3, parsed.event.value, 0.0)
        assertEquals(90.0, parsed.event.threshold, 0.0)
        assertEquals("alert", parsed.event.state)
        assertEquals("warning", parsed.severity)
    }

    @Test
    fun `alert hardware ok state parses with info severity`() {
        val payload = """{"metric":"mem","value":40.0,"threshold":90.0,"state":"ok","time":"2026-09-22T05:00:00Z"}"""
        val event = IntelPushParser.parse("alert.hardware", payload, "info")
        assertTrue(event is AlertHardwareParsedEvent)
        val parsed = event as AlertHardwareParsedEvent
        assertEquals("ok", parsed.event.state)
        assertEquals("info", parsed.severity)
    }

    @Test
    fun `gate blocked parses reason and missing dependencies`() {
        val payload = """{"projectId":1,"reason":"missing","missing":["mysql(missing)"]}"""
        val event = IntelPushParser.parse("intel.gate.blocked", payload, "warning")
        assertTrue(event is GateBlockedParsedEvent)
        val parsed = event as GateBlockedParsedEvent
        assertEquals(1L, parsed.projectId)
        assertEquals("missing", parsed.reason)
        assertEquals(listOf("mysql(missing)"), parsed.missing)
    }

    @Test
    fun `audit finding parses all fields`() {
        val payload = """{"projectId":1,"findingId":8,"severity":"high","category":"sensitive_field","summary":"获取用户信息返回密码"}"""
        val event = IntelPushParser.parse("intel.audit.finding", payload, "critical")
        assertTrue(event is AuditFindingParsedEvent)
        val parsed = event as AuditFindingParsedEvent
        assertEquals(1L, parsed.projectId)
        assertEquals(8L, parsed.findingId)
        assertEquals("high", parsed.severity)
        assertEquals("sensitive_field", parsed.category)
        assertEquals("获取用户信息返回密码", parsed.summary)
    }

    @Test
    fun `fix suggested parses fix id and title`() {
        val payload = """{"projectId":1,"fixId":11,"findingId":8,"title":"避免返回密码字段"}"""
        val event = IntelPushParser.parse("intel.fix.suggested", payload, "info")
        assertTrue(event is FixSuggestedParsedEvent)
        val parsed = event as FixSuggestedParsedEvent
        assertEquals(11L, parsed.fixId)
        assertEquals(8L, parsed.findingId)
        assertEquals("避免返回密码字段", parsed.title)
    }

    @Test
    fun `fix applied parses fix id and write mode`() {
        val payload = """{"projectId":1,"fixId":12,"writeMode":"overwrite"}"""
        val event = IntelPushParser.parse("intel.fix.applied", payload, "info")
        assertTrue(event is FixAppliedParsedEvent)
        val parsed = event as FixAppliedParsedEvent
        assertEquals(12L, parsed.fixId)
        assertEquals("overwrite", parsed.writeMode)
        assertNull(parsed.findingId)
    }

    @Test
    fun `chat answer parses feature id and mode`() {
        val payload = """{"projectId":1,"featureId":3,"mode":"ai"}"""
        val event = IntelPushParser.parse("intel.feature.chat.answer", payload, "info")
        assertTrue(event is ChatAnswerParsedEvent)
        val parsed = event as ChatAnswerParsedEvent
        assertEquals(3L, parsed.featureId)
        assertEquals("ai", parsed.mode)
    }

    @Test
    fun `task event parses status and reason`() {
        val payload = """{"id":"task_1","status":"blocked","reason":"前置任务失败"}"""
        val event = IntelPushParser.parse("task.event", payload, "warning")
        assertTrue(event is TaskParsedEvent)
        val parsed = event as TaskParsedEvent
        assertEquals("task_1", parsed.id)
        assertEquals("blocked", parsed.status)
        assertEquals("前置任务失败", parsed.reason)
    }

    @Test
    fun `unknown type returns null`() {
        assertNull(IntelPushParser.parse("intel.unknown", """{"projectId":1}""", "info"))
    }

    @Test
    fun `invalid payload json returns null without throwing`() {
        assertNull(IntelPushParser.parse("intel.run.event", "not json", "info"))
        assertNull(IntelPushParser.parse("alert.hardware", "{broken", "info"))
    }

    @Test
    fun `missing severity defaults to info`() {
        val payload = """{"metric":"cpu","value":95.0,"threshold":90.0,"state":"alert","time":"2026-09-22T06:00:00Z"}"""
        val event = IntelPushParser.parse("alert.hardware", payload, null)
        assertTrue(event is AlertHardwareParsedEvent)
        assertEquals("info", (event as AlertHardwareParsedEvent).severity)
    }

    @Test
    fun `null payload returns null`() {
        assertNull(IntelPushParser.parse("intel.run.event", null, "info"))
        assertNull(IntelPushParser.parse("alert.hardware", null, "warning"))
    }
}
