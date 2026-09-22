/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BackendAlerts.kt
 * Date : 2026/09/22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import kotlinx.serialization.Serializable

/** 硬件告警阈值（百分比）。 */
@Serializable
data class AlertsThresholds(
    val enabled: Boolean = false,
    val cpuPct: Double = 0.0,
    val memPct: Double = 0.0,
    val diskPct: Double = 0.0,
)

/** 当前硬件指标快照（百分比）。 */
@Serializable
data class AlertsMetrics(
    val cpuPct: Double = 0.0,
    val memPct: Double = 0.0,
    val diskPct: Double = 0.0,
)

/** `GET/POST /api/alerts` 的响应体：告警配置 + 当前指标 + 各指标是否触警。 */
@Serializable
data class AlertsSnapshot(
    val enabled: Boolean = false,
    val thresholds: AlertsThresholds? = null,
    val metrics: AlertsMetrics? = null,
    val alerts: Map<String, Boolean> = emptyMap(),
)

/** `POST /api/alerts` 的请求体（null 字段表示不修改）。 */
@Serializable
data class AlertsUpdateRequest(
    val enabled: Boolean? = null,
    val cpuPct: Double? = null,
    val memPct: Double? = null,
    val diskPct: Double? = null,
)

/** 硬件告警推送事件（metric：cpu/mem/disk；state：ok | alert | recover）。 */
@Serializable
data class HardwareAlertEvent(
    val metric: String = "",
    val value: Double = 0.0,
    val threshold: Double = 0.0,
    val state: String = "",
    val time: String = "",
)
