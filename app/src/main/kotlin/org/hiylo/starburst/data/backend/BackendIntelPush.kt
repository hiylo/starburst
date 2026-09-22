/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BackendIntelPush.kt
 * Date : 2026/09/22 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hiylo.starburst.data.api.HardwareAlertEvent
import org.hiylo.starburst.data.api.IntelTestRun

/**
 * 后端 `/api/ws` 广播的 Intel / 硬件告警 / 任务事件信封与解析结果。
 *
 * 外层信封结构：`{"type":"<eventType>","payload":{...},"severity":"info|warning|critical"}`；
 * payload 为内联 JSON 对象，severity 可能缺省（按 info 处理）。
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Serializable
internal data class IntelPushEnvelope(
    val type: String = "",
    val payload: JsonObject? = null,
    val severity: String? = null,
)

/** 解析成功后的目标事件；type 未放行或解析失败返回 null。 */
sealed interface IntelParsedEvent

/** intel.run.event：一次测试运行的快照。 */
data class IntelRunParsedEvent(val run: IntelTestRun) : IntelParsedEvent

/** alert.hardware：硬件指标越线 / 恢复事件。 */
data class AlertHardwareParsedEvent(val event: HardwareAlertEvent, val severity: String) : IntelParsedEvent

/** intel.gate.blocked：测试门禁被阻断（探测失败 / 缺失前置）。 */
data class GateBlockedParsedEvent(
    val projectId: Long,
    val reason: String,
    val detail: String?,
    val missing: List<String>,
) : IntelParsedEvent

/** intel.env.ready：项目环境就绪。 */
data class EnvReadyParsedEvent(val projectId: Long) : IntelParsedEvent

/** intel.audit.finding：安全 / 合规审计发现。 */
data class AuditFindingParsedEvent(
    val projectId: Long,
    val findingId: Long,
    val severity: String,
    val category: String,
    val summary: String,
) : IntelParsedEvent

/** intel.fix.suggested：自动修复方案建议。 */
data class FixSuggestedParsedEvent(
    val projectId: Long,
    val fixId: Long,
    val findingId: Long?,
    val title: String,
) : IntelParsedEvent

/** intel.fix.applied：自动修复已落盘。 */
data class FixAppliedParsedEvent(
    val projectId: Long,
    val fixId: Long,
    val findingId: Long?,
    val writeMode: String,
) : IntelParsedEvent

/** intel.feature.chat.answer：功能点问答已生成。 */
data class ChatAnswerParsedEvent(
    val projectId: Long,
    val featureId: Long,
    val mode: String,
) : IntelParsedEvent

/** task.event / task.failure：后台任务状态变更 / 失败。 */
data class TaskParsedEvent(
    val id: String,
    val status: String,
    val reason: String? = null,
) : IntelParsedEvent

/**
 * Intel 推送解析器：按 type 把信封 payload 解析为目标事件对象。
 * 纯函数、无副作用；解析失败（字段缺失 / 类型不符）一律返回 null 而非抛出。
 *
 * @author Hsi Chu
 * @since V1.0
 */
internal object IntelPushParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 解析单个推送帧。
     *
     * @param type 事件类型（信封 type）
     * @param payloadJson payload 的 JSON 文本（可能为 null）
     * @param severity 信封 severity（可能为 null，按 info 处理）
     * @return 解析成功返回目标事件；type 未放行或解析失败返回 null
     */
    fun parse(type: String, payloadJson: String?, severity: String?): IntelParsedEvent? {
        val body = payloadJson ?: return null
        return try {
            when (type) {
                "intel.run.event" -> {
                    val run = json.parseToJsonElement(body).jsonObject["run"]
                        ?.let { json.decodeFromJsonElement(IntelTestRun.serializer(), it) }
                        ?: return null
                    IntelRunParsedEvent(run)
                }
                "alert.hardware" -> AlertHardwareParsedEvent(
                    json.decodeFromString(HardwareAlertEvent.serializer(), body),
                    severity ?: "info",
                )
                "intel.gate.blocked" -> {
                    val obj = bodyObj(body)
                    GateBlockedParsedEvent(
                        projectId = obj.long("projectId") ?: return null,
                        reason = obj.string("reason") ?: return null,
                        detail = obj.stringOrNull("detail"),
                        missing = obj.stringList("missing"),
                    )
                }
                "intel.env.ready" -> EnvReadyParsedEvent(
                    projectId = bodyObj(body).long("projectId") ?: return null,
                )
                "intel.audit.finding" -> {
                    val obj = bodyObj(body)
                    AuditFindingParsedEvent(
                        projectId = obj.long("projectId") ?: return null,
                        findingId = obj.long("findingId") ?: return null,
                        severity = obj.string("severity") ?: return null,
                        category = obj.string("category") ?: return null,
                        summary = obj.string("summary") ?: return null,
                    )
                }
                "intel.fix.suggested" -> {
                    val obj = bodyObj(body)
                    FixSuggestedParsedEvent(
                        projectId = obj.long("projectId") ?: return null,
                        fixId = obj.long("fixId") ?: return null,
                        findingId = obj.long("findingId"),
                        title = obj.string("title") ?: return null,
                    )
                }
                "intel.fix.applied" -> {
                    val obj = bodyObj(body)
                    FixAppliedParsedEvent(
                        projectId = obj.long("projectId") ?: return null,
                        fixId = obj.long("fixId") ?: return null,
                        findingId = obj.long("findingId"),
                        writeMode = obj.string("writeMode") ?: return null,
                    )
                }
                "intel.feature.chat.answer" -> {
                    val obj = bodyObj(body)
                    ChatAnswerParsedEvent(
                        projectId = obj.long("projectId") ?: return null,
                        featureId = obj.long("featureId") ?: return null,
                        mode = obj.string("mode") ?: return null,
                    )
                }
                "task.event", "task.failure" -> {
                    val obj = bodyObj(body)
                    TaskParsedEvent(
                        id = obj.string("id") ?: return null,
                        status = obj.string("status") ?: return null,
                        reason = obj.stringOrNull("reason"),
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun bodyObj(payloadJson: String): JsonObject =
        json.parseToJsonElement(payloadJson).jsonObject

    private fun JsonObject.long(key: String): Long? =
        this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.stringOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.stringList(key: String): List<String> =
        this[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
}
