/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatMessageBubble.kt
 * Date : 2026/09/14 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.hiylo.starburst.domain.model.*
import org.hiylo.starburst.ui.theme.CodeTypography
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.AppSecondaryButton
import org.hiylo.starburst.ui.components.isAmoledTheme
import java.util.Locale

@Composable
internal fun TodoListCard(tool: Part.Tool) {
    val isAmoled = isAmoledTheme()
    // Extract todos from metadata first, then fall back to input
    val todos = remember(tool) {
        val source = when (val state = tool.state) {
            is ToolState.Completed -> state.metadata?.get("todos") ?: state.input["todos"]
            is ToolState.Running -> state.metadata?.get("todos") ?: state.input["todos"]
            is ToolState.Pending -> state.input["todos"]
            is ToolState.Error -> state.metadata?.get("todos") ?: state.input["todos"]
        }
        if (source != null) {
            try {
                source.jsonArray.mapNotNull { element ->
                    try {
                        val obj = element.jsonObject
                        val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val status = obj["status"]?.jsonPrimitive?.contentOrNull ?: "pending"
                        val priority = obj["priority"]?.jsonPrimitive?.contentOrNull ?: "medium"
                        TodoItem(content = content, status = status, priority = priority)
                    } catch (_: Exception) { null }
                }
            } catch (_: Exception) { emptyList() }
        } else {
            emptyList()
        }
    }

    if (todos.isEmpty()) {
        // Fallback to generic tool card if we can't parse todos
        ToolCallCard(tool = tool)
        return
    }

    val completedCount = todos.count { it.status == "completed" }
    val totalCount = todos.size
    var expanded by remember { mutableStateOf(true) }
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
        tonalElevation = if (isAmoled) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .expandableToolHeader(expanded) {
                        performHaptic(hapticView, hapticOn)
                        expanded = !expanded
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Checklist,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (completedCount == totalCount) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = stringResource(R.string.chat_tasks_label),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$completedCount/$totalCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) stringResource(R.string.chat_collapse) else stringResource(R.string.chat_expand),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // Real-time progress bar (completed / total)
            LinearProgressIndicator(
                progress = { if (totalCount == 0) 0f else completedCount.toFloat() / totalCount },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(4.dp),
                color = if (completedCount == totalCount) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )

            // Todo items
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (todo in todos) {
                        TodoItemRow(todo = todo)
                    }
                }
            }
        }
    }
}

private data class TodoItem(
    val content: String,
    val status: String,
    val priority: String
)

@Composable
private fun TodoItemRow(todo: TodoItem) {
    val isCompleted = todo.status == "completed"
    val isInProgress = todo.status == "in_progress"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isCompleted,
            onCheckedChange = null,
            modifier = Modifier.size(20.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = if (isInProgress) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
        )
        Text(
            text = todo.content,
            style = MaterialTheme.typography.bodySmall.copy(
                color = if (isCompleted) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StepFinishInfo(step: Part.StepFinish) {
    if (step.tokens != null || step.cost != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            step.tokens?.let { tokens ->
                Text(
                    text = stringResource(R.string.chat_tokens_format, tokens.input, tokens.output),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
            step.cost?.let { cost ->
                Text(
                    text = stringResource(R.string.chat_cost_format, String.format(Locale.ROOT, "%.4f", cost)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

/**
 * Compact horizontal row of image thumbnails with tap-to-preview.
 */

@Composable
internal fun PermissionCard(
    permission: SseEvent.PermissionAsked,
    position: String,
    onReply: (reply: String, onResult: (Boolean) -> Unit) -> Unit,
) {
    val isAmoled = isAmoledTheme()
    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current
    val containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.tertiaryContainer
    val contentColor = if (isAmoled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onTertiaryContainer
    var submitting by remember(permission.sessionId, permission.id) { mutableStateOf(false) }
    var confirmAlways by remember(permission.sessionId, permission.id) { mutableStateOf(false) }

    if (confirmAlways) {
        ChatDialog(onDismiss = { confirmAlways = false }) {
            Text(stringResource(R.string.permission_always_confirm_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.permission_always_confirm_message))
                if (permission.always.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.permission_always_scope, permission.always.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AppSecondaryButton(onClick = { confirmAlways = false }) { Text(stringResource(R.string.cancel)) }
                AppPrimaryButton(onClick = {
                    confirmAlways = false
                    submitting = true
                    onReply("always") { success -> submitting = false }
                }) {
                    Text(stringResource(R.string.permission_allow_always))
                }
            }
        }
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isAmoled) MaterialTheme.colorScheme.tertiary else contentColor
                )
                Text(
                    text = stringResource(R.string.permission_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor
                )
                Spacer(Modifier.weight(1f))
                Text(position, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.7f))
            }
            Text(
                text = permission.permission,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor
            )
            if (permission.patterns.isNotEmpty()) {
                Text(
                    text = permission.patterns.joinToString(", "),
                    style = CodeTypography.copy(
                        fontSize = 11.sp,
                        color = contentColor.copy(alpha = 0.7f)
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (permission.always.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = contentColor.copy(alpha = 0.6f)
                    )
                    Text(
                        text = stringResource(R.string.permission_always_scope, permission.always.joinToString(", ")),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppSecondaryButton(
                    onClick = {
                        performHaptic(hapticView, hapticOn)
                        submitting = true
                        onReply("reject") { submitting = false }
                    },
                    enabled = !submitting,
                    modifier = Modifier.weight(1f),
                    destructive = true,
                ) {
                    Text(stringResource(R.string.permission_deny), maxLines = 1)
                }
                AppSecondaryButton(
                    onClick = {
                        performHaptic(hapticView, hapticOn)
                        submitting = true
                        onReply("once") { submitting = false }
                    },
                    enabled = !submitting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.permission_allow_once), maxLines = 1)
                }
                AppPrimaryButton(
                    onClick = { performHaptic(hapticView, hapticOn); confirmAlways = true },
                    enabled = !submitting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.permission_allow_always), maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun QuestionCard(
    question: SseEvent.QuestionAsked,
    position: String,
    onSubmit: (answers: List<List<String>>, onResult: (Boolean) -> Unit) -> Unit,
    onReject: (onResult: (Boolean) -> Unit) -> Unit,
) {
    val isAmoled = isAmoledTheme()
    val isSingle = question.questions.size == 1 && question.questions[0].multiple != true

    val hapticView = LocalView.current
    val hapticOn = LocalHapticFeedbackEnabled.current

    // Prevent multiple submissions
    var submitted by remember(question) { mutableStateOf(false) }

    // Track answers per question
    val answersPerQuestion = remember(question) {
        mutableStateListOf<List<String>>().apply {
            repeat(question.questions.size) { add(emptyList()) }
        }
    }

    val containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isAmoled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = MaterialTheme.colorScheme.primary

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row — matches PermissionCard style
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    @Suppress("DEPRECATION")
                    Icons.Default.HelpOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = accentColor
                )
                Text(
                    text = stringResource(R.string.chat_question_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor
                )
                Spacer(Modifier.weight(1f))
                Text(position, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.7f))
            }

            // Question sections
            question.questions.forEachIndexed { index, q ->
                if (q.header.isNotBlank()) {
                    Text(
                        text = q.header,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor
                    )
                }
                Text(
                    text = q.question,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f)
                )

                Spacer(Modifier.height(2.dp))

                if (q.multiple) {
                    // ── Multi-select: checkboxes ──
                    val selectedLabels = remember(question, index) { mutableStateListOf<String>() }

                    q.options.forEach { option ->
                        val checked = option.label in selectedLabels
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (checked) accentColor.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .toggleable(
                                    value = checked,
                                    enabled = !submitted,
                                    role = Role.Checkbox,
                                    onValueChange = {
                                        if (it) selectedLabels.add(option.label) else selectedLabels.remove(option.label)
                                        if (index < answersPerQuestion.size) {
                                            answersPerQuestion[index] = selectedLabels.toList()
                                        }
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = accentColor,
                                    uncheckedColor = contentColor.copy(alpha = 0.5f)
                                )
                            )
                            Column {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor
                                )
                                if (option.description.isNotBlank()) {
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = contentColor.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ── Single-select: tappable option rows ──
                    q.options.forEach { option ->
                        val isSelected = index < answersPerQuestion.size && option.label in answersPerQuestion[index]
                        Surface(
                            onClick = {
                                if (!submitted) {
                                    performHaptic(hapticView, hapticOn)
                                    if (isSingle) {
                                        submitted = true
                                        onSubmit(listOf(listOf(option.label))) { submitted = false }
                                    } else {
                                        if (index < answersPerQuestion.size) {
                                            answersPerQuestion[index] = listOf(option.label)
                                        }
                                    }
                                }
                            },
                                enabled = !submitted,
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) accentColor.copy(alpha = 0.12f) else if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                border = if (!isSelected && isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) accentColor else accentColor.copy(alpha = 0.7f)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) accentColor else contentColor
                                    )
                                    if (option.description.isNotBlank()) {
                                        Text(
                                            text = option.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = contentColor.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // "Type your own answer" — inline text field
                if (q.custom != false) {
                    val currentAnswers = if (index < answersPerQuestion.size) answersPerQuestion[index] else emptyList()
                    val customAnswer = currentAnswers.firstOrNull { ans -> q.options.none { it.label == ans } }
                    
                    if (customAnswer != null) {
                        // Show selected custom answer
                         Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.RadioButtonChecked,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = accentColor
                                )
                                Text(
                                    text = customAnswer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = accentColor,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        if (!submitted && index < answersPerQuestion.size) {
                                            answersPerQuestion[index] = emptyList()
                                        }
                                    },
                                    enabled = !submitted,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.chat_clear),
                                        modifier = Modifier.size(16.dp),
                                        tint = accentColor.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    } else {
                        var isEditingCustom by remember(question, index) { mutableStateOf(false) }
                        var customText by remember(question, index) { mutableStateOf("") }

                        if (!isEditingCustom) {
                            Surface(
                                onClick = {
                                    isEditingCustom = true
                                },
                                enabled = !submitted,
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = accentColor.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = stringResource(R.string.question_custom_answer),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accentColor.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = customText,
                                onValueChange = { customText = it },
                                enabled = !submitted,
                                placeholder = {
                                    Text(
                                        stringResource(R.string.chat_type_answer),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodySmall,
                                shape = RoundedCornerShape(8.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                trailingIcon = {
                                    Row {
                                        IconButton(
                                            onClick = {
                                                val trimmed = customText.trim()
                                                if (trimmed.isNotBlank()) {
                                                    performHaptic(hapticView, hapticOn)
                                                    if (isSingle) {
                                                        submitted = true
                                                        onSubmit(listOf(listOf(trimmed))) { submitted = false }
                                                    } else {
                                                        if (index < answersPerQuestion.size) {
                                                            answersPerQuestion[index] = listOf(trimmed)
                                                        }
                                                        isEditingCustom = false
                                                        customText = "" 
                                                    }
                                                }
                                            },
                                            enabled = customText.isNotBlank() && !submitted
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Send,
                                                contentDescription = stringResource(R.string.question_submit),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(onClick = { isEditingCustom = false; customText = "" }) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(R.string.question_cancel),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Bottom actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(
                    onClick = {
                        performHaptic(hapticView, hapticOn)
                        submitted = true
                        onReject { submitted = false }
                    },
                    enabled = !submitted
                ) {
                    Text(stringResource(R.string.chat_dismiss), style = MaterialTheme.typography.labelMedium)
                }
                if (!isSingle) {
                    AppPrimaryButton(
                        onClick = {
                            performHaptic(hapticView, hapticOn)
                            submitted = true
                            onSubmit(answersPerQuestion.map { it.toList() }) { submitted = false }
                        },
                        enabled = answersPerQuestion.any { it.isNotEmpty() } && !submitted,
                    ) {
                        Text(stringResource(R.string.question_submit), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** 清理子会话/工具的服务器标题：去路径、去常见 agent 前缀与哈希后缀。 */
internal fun cleanSessionTitle(title: String?): String? {
    if (title.isNullOrBlank()) return null
    var t = title.trim()
    val lastSlash = t.lastIndexOf('/')
    if (lastSlash >= 0 && lastSlash < t.length - 1) {
        t = t.substring(lastSlash + 1)
    }
    // 去掉 "agent-" / "subagent-" 之类前缀与形如 "-abc12345" 的哈希后缀。
    t = t.replace(Regex("^(agent|subagent|sub_agent)[-_]", RegexOption.IGNORE_CASE), "")
    t = t.replace(Regex("[-_][0-9a-f]{6,32}$", RegexOption.IGNORE_CASE), "")
    return t.ifBlank { null }
}
