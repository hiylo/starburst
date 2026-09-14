/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatDialogs.kt
 * Date : 2026/09/14 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.key
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.hiylo.starburst.domain.model.*
import org.hiylo.starburst.data.api.ProviderInfo
import org.hiylo.starburst.data.api.ProviderModel
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.ProviderIcon
import org.hiylo.starburst.ui.components.AppDialog
import org.hiylo.starburst.ui.components.AppPickerItemShape
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.AppSecondaryButton
import org.hiylo.starburst.ui.components.AppCardShape
import org.hiylo.starburst.ui.components.appAmoledBorder
import org.hiylo.starburst.ui.components.appSelectedItemColor
import org.hiylo.starburst.ui.components.isAmoledTheme


@Composable
internal fun AttachmentSourceCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    val isAmoled = isAmoledTheme()
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(13.dp),
                color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.primaryContainer,
                border = if (isAmoled) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f))
                } else null,
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(23.dp),
                        tint = if (isAmoled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}
@Composable
internal fun ChatDialog(
    onDismiss: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    AppDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            content = content,
        )
    }
}

@Composable
internal fun RevertConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ChatDialog(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.chat_revert_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.chat_revert_message))
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            AppSecondaryButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
            AppSecondaryButton(onClick = onConfirm, destructive = true) {
                Text(stringResource(R.string.chat_revert))
            }
        }
    }
}

@Composable
internal fun SummaryDialog(
    isGenerating: Boolean,
    summary: String?,
    error: String?,
    onDismiss: () -> Unit,
) {
    ChatDialog(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.chat_summary_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(12.dp))
        when {
            error != null -> {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            summary != null -> {
                Column {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                    if (isGenerating) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text(
                                stringResource(R.string.chat_summary_generating),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            isGenerating -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.chat_summary_generating))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            AppSecondaryButton(onClick = onDismiss) {
                Text(stringResource(R.string.chat_dismiss))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelPickerDialog(
    providers: List<ProviderInfo>,
    selectedProviderId: String?,
    selectedModelId: String?,
    onSelect: (providerId: String, modelId: String) -> Unit,
    onManageModels: () -> Unit,
    onDismiss: () -> Unit
) {
    val isAmoled = isAmoledTheme()
    var search by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    fun isModelFree(providerId: String, model: ProviderModel): Boolean {
        if (providerId != "opencode") return false
        val cost = model.cost ?: return true
        return cost.input == 0.0
    }

    val popularProviders = remember {
        listOf("opencode", "anthropic", "github-copilot", "openai", "google", "openrouter", "vercel")
    }
    val modelGroups = remember(providers, search) {
        val query = search.trim().lowercase()
        providers
            .filter { it.models.isNotEmpty() }
            .sortedWith(
                compareBy<ProviderInfo> {
                    popularProviders.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
                }.thenBy { it.name.lowercase() },
            )
            .mapNotNull { provider ->
                val providerMatches = provider.name.lowercase().contains(query) || provider.id.lowercase().contains(query)
                val models = provider.models.values
                    .filter { model ->
                        query.isEmpty() || providerMatches ||
                            model.name.lowercase().contains(query) || model.id.lowercase().contains(query)
                    }
                    .sortedBy { it.name.lowercase() }
                if (models.isEmpty()) null else provider to models
            }
    }
    LaunchedEffect(modelGroups, selectedProviderId, selectedModelId) {
        if (search.isNotBlank()) return@LaunchedEffect
        var listIndex = 0
        for ((provider, models) in modelGroups) {
            val modelIndex = models.indexOfFirst { provider.id == selectedProviderId && it.id == selectedModelId }
            if (modelIndex >= 0) {
                listState.scrollToItem(listIndex + modelIndex + 1)
                return@LaunchedEffect
            }
            listIndex += models.size + 1
        }
    }

    AppDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .height(44.dp),
            shape = AppPickerItemShape,
            color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BasicTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (search.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.server_settings_search_placeholder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                if (search.isNotEmpty()) {
                    IconButton(
                        onClick = { search = "" },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
        ) {
            if (modelGroups.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.server_settings_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                    )
                }
            } else {
                for ((index, group) in modelGroups.withIndex()) {
                    val (provider, models) = group
                    val topPad = if (index == 0) 0.dp else 12.dp

                    item(key = "provider_header_${provider.id}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = topPad, bottom = 2.dp, start = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ProviderIcon(
                                providerId = provider.id,
                                size = 14.dp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                text = (provider.name.ifEmpty { provider.id }).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    items(
                        models,
                        key = { "model_${provider.id}_${it.id}" }
                    ) { model ->
                        val isSelected = provider.id == selectedProviderId && model.id == selectedModelId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(AppPickerItemShape)
                                .background(
                                    if (isSelected) appSelectedItemColor()
                                    else Color.Transparent
                                )
                                .then(
                                    if (isSelected && isAmoled) {
                                        Modifier.border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                                            AppPickerItemShape,
                                        )
                                    } else Modifier,
                                )
                                .clickable { onSelect(provider.id, model.id) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.name.ifEmpty { model.id },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isModelFree(provider.id, model)) {
                                    Text(
                                        text = stringResource(R.string.chat_free_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onManageModels() }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Tune,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.server_settings_models),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
@Composable
internal fun CustomCommandsDialog(
    commands: List<CustomSlashCommand>,
    onAdd: (name: String, prompt: String) -> Boolean,
    onRemove: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var showBlankError by remember { mutableStateOf(false) }

    ChatDialog(onDismiss = onDismiss) {
        Text(stringResource(R.string.custom_command_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        if (commands.isEmpty()) {
            Text(
                text = stringResource(R.string.custom_command_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                commands.forEach { cmd ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "/${cmd.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = cmd.prompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onRemove(cmd.name) }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.custom_command_delete, cmd.name),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; showBlankError = false },
            label = { Text(stringResource(R.string.custom_command_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it; showBlankError = false },
            label = { Text(stringResource(R.string.custom_command_prompt_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        if (showBlankError) {
            Text(
                text = stringResource(R.string.custom_command_add_failed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            AppSecondaryButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            AppPrimaryButton(
                onClick = {
                    val added = onAdd(name, prompt)
                    if (added) {
                        name = ""
                        prompt = ""
                        showBlankError = false
                    } else {
                        showBlankError = true
                    }
                },
                enabled = name.isNotBlank() && prompt.isNotBlank(),
            ) {
                Text(stringResource(R.string.custom_command_add))
            }
        }
    }
}

/** Rotating placeholder hints for the input bar, similar to the WebUI prompt input. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContextUsageDialog(
    usage: ContextUsageDetails,
    contextWindow: Int,
    onDismiss: () -> Unit,
) {
    val isAmoled = isAmoledTheme()
    val used = usage.currentTotal
    val percentage = if (contextWindow > 0) (used.toDouble() / contextWindow * 100).roundToInt() else 0
    val remaining = (contextWindow - used).coerceAtLeast(0)
    val progressColor = when {
        percentage >= 90 -> MaterialTheme.colorScheme.error
        percentage >= 70 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    AppDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
    ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.chat_context_details), style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = "$percentage%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = progressColor,
                    )
                    Text(
                        text = stringResource(
                            R.string.chat_context_used,
                            formatTokenCount(used),
                            formatTokenCount(contextWindow),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { if (contextWindow > 0) (used.toFloat() / contextWindow).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = progressColor,
                    trackColor = progressColor.copy(alpha = 0.16f),
                )
                Text(
                    text = stringResource(R.string.chat_context_remaining, formatTokenCount(remaining)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                Text(stringResource(R.string.chat_context_current_turn), style = MaterialTheme.typography.labelLarge)
                ContextTokenRow(stringResource(R.string.chat_context_input), usage.input)
                ContextTokenRow(stringResource(R.string.chat_context_output), usage.output)
                if (usage.reasoning > 0) ContextTokenRow(stringResource(R.string.chat_context_reasoning), usage.reasoning)
                if (usage.cacheRead > 0) ContextTokenRow(stringResource(R.string.chat_context_cache_read), usage.cacheRead)
                if (usage.cacheWrite > 0) ContextTokenRow(stringResource(R.string.chat_context_cache_write), usage.cacheWrite)
                HorizontalDivider()
                Text(stringResource(R.string.chat_context_session_totals), style = MaterialTheme.typography.labelLarge)
                ContextTokenRow(stringResource(R.string.chat_context_tokens_processed), usage.sessionTotal)
                ContextTokenRow(
                    stringResource(R.string.chat_context_messages),
                    usage.userMessages + usage.assistantMessages,
                    raw = true,
                )
                if (usage.totalCost > 0) {
                    ContextTokenRow(
                        stringResource(R.string.chat_context_cost),
                        0,
                        value = String.format(Locale.US, "$%.4f", usage.totalCost),
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AppSecondaryButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
            }
    }
}

@Composable
private fun ContextTokenRow(label: String, tokens: Int, raw: Boolean = false, value: String? = null) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: if (raw) tokens.toString() else formatTokenCount(tokens), style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Card that displays a pending question from the server.
 *
 * Single-select: each option is an OutlinedButton that immediately submits.
 * Multi-select: checkboxes + Submit button.
 * "Type your own answer" expands an inline text field.
 */
/**
 * 会话变更面板：展示 agent 在本次会话中改动过的文件列表（文件名 + 增删行数 + 状态），
 * 点击单个文件展开 before/after 对比。
 */
@Composable
internal fun SessionDiffDialog(
    diffs: List<FileDiff>,
    onDismiss: () -> Unit,
) {
    var expanded by remember { mutableStateOf<String?>(null) }
    ChatDialog(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.session_changes_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            if (diffs.isEmpty()) {
                Text(
                    text = stringResource(R.string.session_changes_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(diffs, key = { it.file }) { diff ->
                        FileDiffCard(
                            diff = diff,
                            expanded = expanded == diff.file,
                            onToggle = {
                                expanded = if (expanded == diff.file) null else diff.file
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AppSecondaryButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@Composable
private fun FileDiffCard(
    diff: FileDiff,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val isAmoled = isAmoledTheme()
    val statusColor = when (diff.status) {
        "added" -> Color(0xFF2E7D32)
        "deleted" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = appAmoledBorder(0.65f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = diff.file,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "+${diff.additions}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2E7D32),
                        )
                        Text(
                            text = "-${diff.deletions}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        diff.status?.let { status ->
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                SessionFileDiffContent(before = diff.before, after = diff.after)
            }
        }
    }
}

@Composable
private fun SessionFileDiffContent(before: String, after: String) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (before.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x1AEF5350), RoundedCornerShape(6.dp))
                    .padding(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.session_changes_before),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = before,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
            }
        }
        if (after.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x1A66BB6A), RoundedCornerShape(6.dp))
                    .padding(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.session_changes_after),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = after,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** 快捷模板：标题资源 + 预设 prompt。 */
data class PromptTemplate(
    val id: String,
    val titleRes: Int,
    val prompt: String,
)

/** 预设的快捷模板列表。 */
private val promptTemplates = listOf(
    PromptTemplate(
        id = "code_review",
        titleRes = R.string.chat_template_code_review,
        prompt = "请对当前项目的代码做一次全面的代码审查，重点关注：潜在 bug、安全问题、性能瓶颈、代码风格一致性。请指出具体的文件和位置，并给出可执行的修复建议。",
    ),
    PromptTemplate(
        id = "generate_tests",
        titleRes = R.string.chat_template_generate_tests,
        prompt = "请为项目中的核心功能生成单元测试，覆盖主要的正常流程和边界情况，遵循项目现有的测试框架和风格。",
    ),
    PromptTemplate(
        id = "explain_code",
        titleRes = R.string.chat_template_explain_code,
        prompt = "请解释当前项目的核心架构和关键代码逻辑，帮助我快速理解项目。如有相关文件，请结合具体代码说明。",
    ),
    PromptTemplate(
        id = "fix_bug",
        titleRes = R.string.chat_template_fix_bug,
        prompt = "请帮我排查并修复项目中的 bug。先定位问题的根因，再给出修复方案和具体的代码修改。",
    ),
)

/** 快捷模板选择弹窗。 */
@Composable
internal fun TemplatePickerDialog(
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_template_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                promptTemplates.forEach { template ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = { onSelect(template.prompt) })
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = stringResource(template.titleRes),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = template.prompt.take(48) + "…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
