/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatTemplateDialogs.kt
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.key.key
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.domain.model.*
import org.hiylo.starburst.data.repository.SettingsRepository
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.AppSecondaryButton

/**
 * Prompt-template picker and its editor, plus the built-in template catalogue.
 * Extracted verbatim from ChatDialogs.kt.
 */

/** 内置快捷模板：标题资源 + 预设 prompt（不可编辑、不可删除，始终置顶）。 */
private data class BuiltinPromptTemplate(
    val titleRes: Int,
    val promptRes: Int,
)

/** 预设的内置快捷模板列表。 */
private val builtinPromptTemplates = listOf(
    BuiltinPromptTemplate(R.string.chat_template_code_review, R.string.chat_template_code_review_prompt),
    BuiltinPromptTemplate(R.string.chat_template_generate_tests, R.string.chat_template_generate_tests_prompt),
    BuiltinPromptTemplate(R.string.chat_template_explain_code, R.string.chat_template_explain_code_prompt),
    BuiltinPromptTemplate(R.string.chat_template_fix_bug, R.string.chat_template_fix_bug_prompt),
    BuiltinPromptTemplate(R.string.chat_template_continue_task, R.string.chat_template_continue_task_prompt),
)

@Composable
internal fun TemplatePickerDialog(
    userTemplates: List<SettingsRepository.PromptTemplate>,
    onSelect: (String) -> Unit,
    onAdd: (String, String) -> Boolean,
    onEdit: (String, String, String) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var editorState by remember { mutableStateOf<EditorState?>(null) }
    var expandedKeys by remember { mutableStateOf(setOf<String>()) }

    fun toggleExpanded(key: String) {
        expandedKeys = if (key in expandedKeys) expandedKeys - key else expandedKeys + key
    }

    ChatDialog(onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.chat_template_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { editorState = EditorState.Add() }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.template_add))
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            builtinPromptTemplates.forEach { template ->
                val key = "builtin:${template.titleRes}"
                item(key) {
                    val expanded = key in expandedKeys
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(context.getString(template.promptRes)) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(template.titleRes),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { toggleExpanded(key) },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = stringResource(
                                        if (expanded) R.string.template_collapse else R.string.template_expand
                                    ),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (expanded) {
                            Text(
                                text = context.getString(template.promptRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                            )
                        }
                    }
                }
            }
            if (userTemplates.isNotEmpty()) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
                itemsIndexed(userTemplates, key = { _, t -> t.id }) { index, template ->
                    val key = template.id
                    val expanded = key in expandedKeys
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(template.prompt) }
                                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = template.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (index > 0) {
                                IconButton(onClick = { onMove(template.id, -1) }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        Icons.Default.ArrowUpward,
                                        contentDescription = stringResource(R.string.template_move_up),
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (index < userTemplates.lastIndex) {
                                IconButton(onClick = { onMove(template.id, 1) }, modifier = Modifier.size(28.dp)) {
                                    Icon(
                                        Icons.Default.ArrowDownward,
                                        contentDescription = stringResource(R.string.template_move_down),
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            IconButton(
                                onClick = { editorState = EditorState.Edit(template.id, template.name, template.prompt) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.template_edit),
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onDelete(template.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.template_delete),
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { toggleExpanded(key) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = stringResource(
                                        if (expanded) R.string.template_collapse else R.string.template_expand
                                    ),
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (expanded) {
                            Text(
                                text = template.prompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    editorState?.let { state ->
        TemplateEditorDialog(
            initialName = state.name.orEmpty(),
            initialPrompt = state.prompt.orEmpty(),
            onDismiss = { editorState = null },
            onSave = { name, prompt ->
                if (name.isBlank() || prompt.isBlank()) return@TemplateEditorDialog
                if (state.id != null) {
                    onEdit(state.id, name, prompt)
                } else {
                    onAdd(name, prompt)
                }
                editorState = null
            },
        )
    }
}

/** 快捷模板增/改表单的编辑状态：新增或编辑指定模板。 */
private data class EditorState(
    val id: String? = null,
    val name: String? = null,
    val prompt: String? = null,
) {
    companion object {
        fun Add() = EditorState()
        fun Edit(id: String, name: String, prompt: String) = EditorState(id, name, prompt)
    }
}

@Composable
private fun TemplateEditorDialog(
    initialName: String,
    initialPrompt: String,
    onDismiss: () -> Unit,
    onSave: (name: String, prompt: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var prompt by remember { mutableStateOf(initialPrompt) }
    var showBlankError by remember { mutableStateOf(false) }

    ChatDialog(onDismiss = onDismiss) {
        Text(
            text = stringResource(if (initialName.isBlank()) R.string.template_add else R.string.template_edit),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; showBlankError = false },
            label = { Text(stringResource(R.string.template_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it; showBlankError = false },
            label = { Text(stringResource(R.string.template_prompt_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        if (showBlankError) {
            Text(
                text = stringResource(R.string.template_save_failed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            AppSecondaryButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(Modifier.width(8.dp))
            AppPrimaryButton(onClick = {
                if (name.isBlank() || prompt.isBlank()) {
                    showBlankError = true
                } else {
                    onSave(name.trim(), prompt.trim())
                }
            }) {
                Text(stringResource(R.string.template_save))
            }
        }
    }
}
