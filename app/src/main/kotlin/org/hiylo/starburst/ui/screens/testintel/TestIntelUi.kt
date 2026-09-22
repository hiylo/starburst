/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : TestIntelUi.kt
 * Date : 2026/09/22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.testintel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.AppCardShape
import org.hiylo.starburst.ui.components.appAmoledBorder
import org.hiylo.starburst.ui.components.isAmoledTheme
import org.hiylo.starburst.ui.theme.StatusConnected
import org.hiylo.starburst.ui.theme.StatusError
import org.hiylo.starburst.ui.theme.StatusProcessing
import org.hiylo.starburst.ui.theme.StatusWarning

/** 分析状态颜色：ok 绿 / failed 红 / running 蓝 / 其它灰。 */
internal fun analysisStatusColor(status: String): Color = when (status.lowercase()) {
    "ok" -> StatusConnected
    "failed", "error" -> StatusError
    "running", "analyzing", "pending" -> StatusProcessing
    else -> Color.Gray
}

/** 问题严重级别颜色：high 红 / medium 橙 / low 黄。 */
internal fun severityColor(severity: String): Color = when (severity.lowercase()) {
    "high" -> StatusError
    "medium" -> Color(0xFFF97316)
    "low" -> StatusWarning
    else -> Color.Gray
}

/** 测试运行状态颜色：queued 灰 / running 蓝 / passed 绿 / failed 红。 */
internal fun runStatusColor(status: String): Color = when (status.lowercase()) {
    "queued" -> Color.Gray
    "running" -> StatusProcessing
    "passed", "success" -> StatusConnected
    "failed", "error" -> StatusError
    else -> Color.Gray
}

/** 修复方案状态颜色：applied 绿 / rolled_back 橙 / rejected 红 / proposed 蓝。 */
internal fun fixStatusColor(status: String): Color = when (status.lowercase()) {
    "applied" -> StatusConnected
    "rolled_back", "rollback" -> StatusWarning
    "rejected" -> StatusError
    else -> StatusProcessing
}

/** 问题状态颜色：resolved 绿 / acknowledged 蓝 / open 黄。 */
internal fun issueStatusColor(status: String): Color = when (status.lowercase()) {
    "resolved", "fixed", "closed" -> StatusConnected
    "acknowledged", "ack" -> StatusProcessing
    else -> StatusWarning
}

@Composable
internal fun severityLabel(severity: String): String = when (severity.lowercase()) {
    "high" -> stringResource(R.string.test_intel_sev_high)
    "medium" -> stringResource(R.string.test_intel_sev_medium)
    "low" -> stringResource(R.string.test_intel_sev_low)
    else -> severity
}

@Composable
internal fun runStatusLabel(status: String): String = when (status.lowercase()) {
    "queued" -> stringResource(R.string.test_intel_run_status_queued)
    "running" -> stringResource(R.string.test_intel_run_status_running)
    "passed", "success" -> stringResource(R.string.test_intel_run_status_passed)
    "failed", "error" -> stringResource(R.string.test_intel_run_status_failed)
    else -> status
}

@Composable
internal fun fixStatusLabel(status: String): String = when (status.lowercase()) {
    "applied" -> stringResource(R.string.test_intel_fix_status_applied)
    "rolled_back", "rollback" -> stringResource(R.string.test_intel_fix_status_rolled_back)
    "rejected" -> stringResource(R.string.test_intel_fix_status_rejected)
    else -> stringResource(R.string.test_intel_fix_status_proposed)
}

@Composable
internal fun issueStatusLabel(status: String): String = when (status.lowercase()) {
    "resolved", "fixed", "closed" -> stringResource(R.string.test_intel_issue_status_resolved)
    "acknowledged", "ack" -> stringResource(R.string.test_intel_issue_status_acknowledged)
    else -> stringResource(R.string.test_intel_issue_status_open)
}

/** 解析 ISO 时间字符串为「yyyy-MM-dd HH:mm」；解析失败原样返回。 */
internal fun formatTimestamp(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    return runCatching {
        java.time.OffsetDateTime.parse(raw).format(formatter)
    }.getOrElse {
        runCatching {
            java.time.LocalDateTime.parse(raw).format(formatter)
        }.getOrDefault(raw)
    }
}

@Composable
internal fun TestIntelSectionCard(content: @Composable ColumnScope.() -> Unit) {
    val isAmoled = isAmoledTheme()
    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = appAmoledBorder(0.65f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
internal fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** 状态圆点：分析状态 / 运行状态共用。 */
@Composable
internal fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(color, CircleShape),
    )
}
