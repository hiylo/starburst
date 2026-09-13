/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : ServerRulesScreen.kt
 * Date : 2026/09/13 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.opencode.ui.screens.server

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.hiylo.opencode.R
import org.hiylo.opencode.data.api.BackendRule
import org.hiylo.opencode.ui.components.AppCardShape
import org.hiylo.opencode.ui.components.appAmoledBorder
import org.hiylo.opencode.ui.components.isAmoledTheme

private val RULE_KINDS = listOf("cron", "git", "http")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerRulesScreen(
    onNavigateBack: () -> Unit,
    viewModel: ServerRulesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val isAmoled = isAmoledTheme()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.server_rules_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.skills_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.server_rules_add))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(padding)
        ) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(state.error.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.skills_retry)) }
                    }
                }
                state.rules.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.server_rules_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.rules, key = { it.id }) { rule ->
                            RuleCard(
                                rule = rule,
                                isAmoled = isAmoled,
                                onShowExecutions = { viewModel.loadExecutions(rule) },
                                onDelete = { viewModel.deleteRule(rule.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateRuleDialog(
            isGenerating = state.isGenerating,
            generateError = state.generateError,
            generateDraft = state.generateDraft,
            isSaving = state.isSaving,
            onGenerate = viewModel::generate,
            onDismiss = {
                viewModel.clearDraft()
                showCreateDialog = false
            },
            onCreate = { name, kind, schedule, directory, prompt, enabled ->
                viewModel.createRule(name, kind, schedule, directory, prompt, enabled) { ok ->
                    if (ok) showCreateDialog = false
                }
            },
        )
    }

    if (state.executions.isNotEmpty() || state.executionsLoading) {
        ExecutionsDialog(
            ruleName = state.executionsRuleName,
            isLoading = state.executionsLoading,
            executions = state.executions,
            onDismiss = viewModel::closeExecutions,
        )
    }
}

@Composable
private fun RuleCard(
    rule: BackendRule,
    isAmoled: Boolean,
    onShowExecutions: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer
        ),
        border = appAmoledBorder(0.65f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onShowExecutions),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rule.name.ifBlank { rule.id },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = rule.kind.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                if (rule.enabled) {
                    Text(
                        text = stringResource(R.string.server_rules_enabled),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.server_rules_disabled),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.server_rules_delete), tint = MaterialTheme.colorScheme.error)
                }
            }
            if (rule.schedule.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = rule.schedule,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = rule.prompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (rule.lastFiredAt != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${stringResource(R.string.server_rules_last_fired)} ${formatBackendTime(rule.lastFiredAt.orEmpty())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.server_rules_delete_confirm)) },
            text = { Text(rule.name.ifBlank { rule.id }) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun CreateRuleDialog(
    isGenerating: Boolean,
    generateError: String?,
    generateDraft: org.hiylo.opencode.data.api.BackendRuleDraft?,
    isSaving: Boolean,
    onGenerate: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String, Boolean) -> Unit,
) {
    var description by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("cron") }
    var schedule by remember { mutableStateOf("") }
    var directory by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }

    LaunchedEffect(generateDraft) {
        generateDraft?.let {
            name = it.name
            kind = it.kind.ifBlank { "cron" }
            schedule = it.schedule
            directory = it.directory
            prompt = it.prompt
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.server_rules_add)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.server_rules_generate_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { onGenerate(description) },
                    enabled = !isGenerating && description.isNotBlank(),
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.server_rules_generating))
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.server_rules_generate))
                    }
                }
                if (generateError != null) {
                    Text(generateError, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.server_rules_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RULE_KINDS.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = { Text(k) },
                        )
                    }
                }
                OutlinedTextField(
                    value = schedule,
                    onValueChange = { schedule = it },
                    label = { Text(stringResource(R.string.server_rules_schedule)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = directory,
                    onValueChange = { directory = it },
                    label = { Text(stringResource(R.string.server_rules_directory)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.server_rules_prompt)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.server_rules_enabled),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, kind, schedule, directory, prompt, enabled) },
                enabled = !isSaving && prompt.isNotBlank() && name.isNotBlank(),
            ) { Text(stringResource(R.string.server_rules_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ExecutionsDialog(
    ruleName: String,
    isLoading: Boolean,
    executions: List<org.hiylo.opencode.data.api.BackendRuleExecution>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.server_rules_executions)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "$ruleName · ${stringResource(R.string.server_rules_execution_total)} ${executions.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (executions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.server_rules_executions_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(executions, key = { it.id }) { exec ->
                            Column {
                                Text(
                                    text = formatBackendTime(exec.triggeredAt),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = exec.taskId,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
