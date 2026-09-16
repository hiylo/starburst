/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : TaskCreateDialog.kt
 * Date : 2026/09/11 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.starburst.ui.screens.tasks

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.BackendPlanDraft
import org.hiylo.starburst.data.api.BackendPlanStepDraft
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 任务创建模式：单个任务或多步骤计划。 */
private enum class TaskMode { Single, Plan }

/** 调度类型：立即 / 延时 / 指定时间 / 周期。 */
private enum class ScheduleType { Immediate, Delay, At, Cron }

/** 表单里的一条计划步骤（可编辑）。 */
private data class PlanStepForm(
    var name: String = "",
    var prompt: String = "",
    var directory: String = "",
)

/** 对话式澄清里的一轮消息。 */
private data class ClarifyMessage(
    val isUser: Boolean,
    val text: String,
)

/** 对话历史最多保留的轮数（每轮=用户+AI 两条）。 */
private const val MAX_CLARIFY_TURNS = 4

/** 把拆解草稿转成可读的一句话摘要，用于对话历史展示。 */
private fun summarizeDraft(context: Context, draft: BackendPlanDraft): String {
    val sb = StringBuilder()
    if (draft.name.isNotBlank()) {
        sb.append(context.getString(R.string.task_draft_name_quoted, draft.name))
    }
    sb.append(context.getString(R.string.task_draft_step_count, draft.steps.size))
    draft.steps.forEachIndexed { i, s ->
        val n = s.name.ifBlank { context.getString(R.string.task_draft_step_default, i + 1) }
        sb.append("\n").append(i + 1).append(". ").append(n)
    }
    draft.schedule?.let { sch ->
        sb.append("\n").append(context.getString(R.string.task_draft_schedule_prefix))
        sb.append(
            when (sch.type) {
                "delay" -> context.getString(R.string.task_draft_schedule_delay, sch.minutes)
                "at" -> sch.at
                "cron" -> context.getString(R.string.task_draft_schedule_cron, sch.cron)
                else -> context.getString(R.string.task_draft_schedule_immediately)
            },
        )
    }
    return sb.toString()
}

/**
 * 安排任务富表单：支持结构化字段（名称/指令/目录/依赖）、定时/周期调度、
 * 多步骤计划（自动串依赖）与对话式澄清（AI 拆解）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskCreateDialog(
    onDismiss: () -> Unit,
    onCreateTask: (name: String?, prompt: String, directory: String?, scheduledAt: String?, cron: String?) -> Unit,
    onCreatePlan: (name: String, steps: List<BackendPlanStepDraft>, scheduledAt: String?, cron: String?) -> Unit,
    onGeneratePlan: (
        description: String,
        draft: BackendPlanDraft?,
        instruction: String,
        onDelta: (String) -> Unit,
        onResult: (Result<BackendPlanDraft>) -> Unit,
    ) -> Unit,
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(TaskMode.Single) }
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var directory by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf(listOf(PlanStepForm())) }

    var scheduleType by remember { mutableStateOf(ScheduleType.Immediate) }
    var delayMinutes by remember { mutableStateOf("30") }
    var atDate by remember { mutableStateOf("") }
    var atTime by remember { mutableStateOf("") }
    var cronExpr by remember { mutableStateOf("0 0 8 * * *") }

    var description by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var clarifyError by remember { mutableStateOf<String?>(null) }
    var streamText by remember { mutableStateOf("") }
    var currentDraft by remember { mutableStateOf<BackendPlanDraft?>(null) }
    var conversation by remember { mutableStateOf(listOf<ClarifyMessage>()) }
    val clarifyFailedMsg = stringResource(R.string.task_clarify_failed)

    fun fillDraft(draft: BackendPlanDraft) {
        name = draft.name
        if (draft.steps.isNotEmpty()) {
            mode = TaskMode.Plan
            steps = draft.steps.map { PlanStepForm(it.name, it.prompt, it.directory) }
        } else {
            mode = TaskMode.Single
            prompt = draft.steps.firstOrNull()?.prompt.orEmpty()
        }
        draft.directory.takeIf { it.isNotBlank() }?.let { directory = it }
        draft.schedule?.let { sched ->
            scheduleType = when (sched.type) {
                "delay" -> ScheduleType.Delay
                "at" -> ScheduleType.At
                "cron" -> ScheduleType.Cron
                else -> ScheduleType.Immediate
            }
            when (sched.type) {
                "delay" -> sched.minutes.takeIf { it > 0 }?.let { delayMinutes = it.toString() }
                "cron" -> sched.cron.takeIf { it.isNotBlank() }?.let { cronExpr = it }
                "at" -> sched.at.takeIf { it.isNotBlank() }?.let { at ->
                    val parts = at.trim().split(Regex("\\s+"))
                    if (parts.size >= 2) {
                        atDate = parts[0]
                        atTime = parts[1].take(5)
                    }
                }
            }
        }
    }

    fun buildSchedule(): Pair<String?, String?> = when (scheduleType) {
        ScheduleType.Immediate -> null to null
        ScheduleType.Delay -> {
            val minutes = delayMinutes.toLongOrNull()?.takeIf { it > 0 }
            val at = if (minutes != null) {
                OffsetDateTime.now().plusMinutes(minutes).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            } else null
            at to null
        }
        ScheduleType.At -> parseLocalToRfc3339(atDate, atTime) to null
        ScheduleType.Cron -> null to cronExpr.trim().ifBlank { null }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.task_create_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.server_cancel))
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                val (scheduledAt, cron) = buildSchedule()
                                when (mode) {
                                    TaskMode.Single -> onCreateTask(
                                        name.ifBlank { null },
                                        prompt,
                                        directory.ifBlank { null },
                                        scheduledAt,
                                        cron,
                                    )
                                    TaskMode.Plan -> onCreatePlan(
                                        name,
                                        steps.map { BackendPlanStepDraft(it.name, it.prompt, it.directory) },
                                        scheduledAt,
                                        cron,
                                    )
                                }
                            },
                            enabled = when (mode) {
                                TaskMode.Single -> prompt.isNotBlank()
                                TaskMode.Plan -> steps.isNotEmpty() && steps.all { it.prompt.isNotBlank() }
                            } && !generating,
                        ) { Text(stringResource(R.string.tasks_submit)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )

                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // 对话式澄清：用一句话让 AI 拆解成结构化计划
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (currentDraft == null) stringResource(R.string.task_clarify_title) else stringResource(R.string.task_clarify_refine_title),
                                style = MaterialTheme.typography.titleSmall,
                            )

                            // 对话历史
                            if (conversation.isNotEmpty()) {
                                conversation.forEach { msg ->
                                    if (msg.isUser) {
                                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                shape = MaterialTheme.shapes.medium,
                                            ) {
                                                Text(
                                                    text = msg.text,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                        }
                                    } else {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = MaterialTheme.shapes.medium,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                text = msg.text,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(if (currentDraft == null) R.string.task_clarify_hint else R.string.task_clarify_refine_hint)) },
                                minLines = 2,
                                maxLines = 4,
                            )
                            if (generating) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text(
                                        text = stringResource(R.string.task_clarify_generating, streamText.length),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (streamText.isNotBlank()) {
                                    Text(
                                        text = streamText,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(
                                        onClick = {
                                            val text = description.trim()
                                            if (text.isEmpty()) return@TextButton
                                            generating = true
                                            clarifyError = null
                                            streamText = ""
                                            val isRefine = currentDraft != null
                                            conversation = (conversation + ClarifyMessage(true, text)).takeLast(MAX_CLARIFY_TURNS * 2)
                                            onGeneratePlan(
                                                if (isRefine) "" else text,
                                                if (isRefine) currentDraft else null,
                                                if (isRefine) text else "",
                                                { delta -> streamText += delta },
                                                { result ->
                                                    generating = false
                                                    result.fold(
                                                        onSuccess = { draft ->
                                                            currentDraft = draft
                                                            fillDraft(draft)
                                                            conversation = (conversation + ClarifyMessage(false, summarizeDraft(context, draft))).takeLast(MAX_CLARIFY_TURNS * 2)
                                                            description = ""
                                                        },
                                                        onFailure = { e ->
                                                            clarifyError = e.message ?: clarifyFailedMsg
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                        enabled = description.isNotBlank(),
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(if (currentDraft == null) R.string.task_clarify_action else R.string.task_clarify_refine_action))
                                    }
                                    if (currentDraft != null) {
                                        TextButton(onClick = {
                                            currentDraft = null
                                            description = ""
                                            clarifyError = null
                                            streamText = ""
                                            conversation = emptyList()
                                        }) {
                                            Text(stringResource(R.string.task_clarify_reset))
                                        }
                                    }
                                }
                            }
                            clarifyError?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    // 模式切换
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = mode == TaskMode.Single,
                            onClick = { mode = TaskMode.Single },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) { Text(stringResource(R.string.task_mode_single)) }
                        SegmentedButton(
                            selected = mode == TaskMode.Plan,
                            onClick = { mode = TaskMode.Plan },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) { Text(stringResource(R.string.task_mode_plan)) }
                    }

                    if (mode == TaskMode.Plan) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.task_name)) },
                            placeholder = { Text(stringResource(R.string.task_name_hint)) },
                            singleLine = true,
                        )
                    }

                    if (mode == TaskMode.Single) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.task_name_optional)) },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.tasks_prompt_hint)) },
                            minLines = 3,
                            maxLines = 8,
                        )
                        OutlinedTextField(
                            value = directory,
                            onValueChange = { directory = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.tasks_directory_hint)) },
                            singleLine = true,
                        )
                    } else {
                        steps.forEachIndexed { index, step ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(R.string.task_step_prefix, index + 1),
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (steps.size > 1) {
                                            IconButton(onClick = { steps = steps.filterIndexed { i, _ -> i != index } }) {
                                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.task_step_delete), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                    OutlinedTextField(
                                        value = step.name,
                                        onValueChange = { v -> steps = steps.toMutableList().also { it[index] = it[index].copy(name = v) } },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(stringResource(R.string.task_step_name)) },
                                        singleLine = true,
                                    )
                                    OutlinedTextField(
                                        value = step.prompt,
                                        onValueChange = { v -> steps = steps.toMutableList().also { it[index] = it[index].copy(prompt = v) } },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(stringResource(R.string.tasks_prompt_hint)) },
                                        minLines = 2,
                                        maxLines = 5,
                                    )
                                    OutlinedTextField(
                                        value = step.directory,
                                        onValueChange = { v -> steps = steps.toMutableList().also { it[index] = it[index].copy(directory = v) } },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(stringResource(R.string.tasks_directory_hint)) },
                                        singleLine = true,
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { steps = steps + PlanStepForm() }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.task_step_add))
                        }
                    }

                    // 调度
                    Text(stringResource(R.string.task_schedule_title), style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        listOf(
                            ScheduleType.Immediate to stringResource(R.string.task_schedule_immediate),
                            ScheduleType.Delay to stringResource(R.string.task_schedule_delay),
                            ScheduleType.At to stringResource(R.string.task_schedule_at),
                            ScheduleType.Cron to stringResource(R.string.task_schedule_cron),
                        ).forEach { (type, label) ->
                            FilterChip(selected = scheduleType == type, onClick = { scheduleType = type }, label = { Text(label) })
                        }
                    }
                    when (scheduleType) {
                        ScheduleType.Delay -> {
                            OutlinedTextField(
                                value = delayMinutes,
                                onValueChange = { delayMinutes = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.task_delay_minutes)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                            )
                        }
                        ScheduleType.At -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = atDate,
                                    onValueChange = { atDate = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(stringResource(R.string.task_at_date)) },
                                    placeholder = { Text("YYYY-MM-DD") },
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = atTime,
                                    onValueChange = { atTime = it },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(stringResource(R.string.task_at_time)) },
                                    placeholder = { Text("HH:MM") },
                                    singleLine = true,
                                )
                            }
                        }
                        ScheduleType.Cron -> {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                listOf(
                                    stringResource(R.string.task_cron_hourly) to "0 0 * * * *",
                                    stringResource(R.string.task_cron_daily) to "0 0 8 * * *",
                                    stringResource(R.string.task_cron_weekly) to "0 0 8 * * 0",
                                ).forEach { (label, expr) ->
                                    FilterChip(selected = cronExpr == expr, onClick = { cronExpr = expr }, label = { Text(label) })
                                }
                            }
                            OutlinedTextField(
                                value = cronExpr,
                                onValueChange = { cronExpr = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.task_cron_custom)) },
                                placeholder = { Text(stringResource(R.string.task_cron_custom)) },
                                singleLine = true,
                            )
                        }
                        ScheduleType.Immediate -> {}
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** 把「YYYY-MM-DD」+「HH:MM」解析成带本地时区偏移的 RFC3339 字符串，失败返回 null。 */
private fun parseLocalToRfc3339(date: String, time: String): String? = runCatching {
    val d = date.trim()
    val t = time.trim()
    val parts = t.split(':')
    val hh = parts.getOrNull(0)?.trim()?.padStart(2, '0') ?: "00"
    val mm = parts.getOrNull(1)?.trim()?.padStart(2, '0') ?: "00"
    val ldt = LocalDateTime.parse("${d}T$hh:$mm:00")
    ldt.atZone(ZoneId.systemDefault()).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}.getOrNull()
