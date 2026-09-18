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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
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
import org.hiylo.starburst.data.repository.SettingsRepository
import java.util.Locale
import java.text.SimpleDateFormat
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
                                val caps = model.capabilities
                                if (caps != null && (caps.toolcall || caps.reasoning || caps.attachment)) {
                                    val capLabels = buildList {
                                        if (caps.toolcall) add(stringResource(R.string.model_cap_toolcall))
                                        if (caps.reasoning) add(stringResource(R.string.model_cap_reasoning))
                                        if (caps.attachment) add(stringResource(R.string.model_cap_attachment))
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = capLabels.joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
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
    messages: List<ChatMessage> = emptyList(),
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
    var showSystemPrompt by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    AppDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // --- Header ---
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

            // --- Stats Grid ---
            HorizontalDivider()
            ContextStatGrid(
                stats = buildContextStats(usage, contextWindow, percentage, timeFormat),
            )

            // --- Token Breakdown Bar ---
            if (usage.breakdown.isNotEmpty()) {
                HorizontalDivider()
                ContextBreakdownBar(usage.breakdown)
            }

            // --- System Prompt ---
            if (!usage.systemPrompt.isNullOrBlank()) {
                HorizontalDivider()
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSystemPrompt = !showSystemPrompt }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.chat_context_system_prompt_title),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = if (showSystemPrompt) stringResource(R.string.chat_collapse) else stringResource(R.string.chat_expand),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showSystemPrompt) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .padding(vertical = 8.dp),
                            color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                            border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                        ) {
                            Text(
                                text = usage.systemPrompt.orEmpty(),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }

            // --- Raw Messages ---
            if (messages.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.chat_context_raw_messages_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(messages) { msg ->
                        ContextRawMessageRow(msg, timeFormat)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                AppSecondaryButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        }
    }
}

/** Build a list of stat items for the context panel grid. */
@Composable
internal fun buildContextStats(
    usage: ContextUsageDetails,
    contextWindow: Int,
    percentage: Int,
    timeFormat: SimpleDateFormat,
): List<Pair<String, String>> {
    val fmt = { v: Int -> if (v == 0) "—" else formatTokenCount(v) }
    val fmtPct = { "$percentage%" }
    val fmtCost = if (usage.totalCost > 0) String.format(Locale.US, "$%.4f", usage.totalCost) else "—"
    val fmtTime = { t: Long? -> t?.let { timeFormat.format(java.util.Date(it)) } ?: "—" }
    val cacheLabel = if (usage.cacheRead > 0 || usage.cacheWrite > 0) {
        "${fmt(usage.cacheRead)} / ${fmt(usage.cacheWrite)}"
    } else "—"

    return listOf(
        stringResource(R.string.chat_context_stats_session) to (usage.sessionTitle ?: "—"),
        stringResource(R.string.chat_context_stats_messages) to "${usage.userMessages + usage.assistantMessages}",
        stringResource(R.string.chat_context_stats_provider) to (usage.providerLabel ?: "—"),
        stringResource(R.string.chat_context_stats_model) to (usage.modelLabel ?: "—"),
        stringResource(R.string.chat_context_stats_limit) to (if (contextWindow > 0) formatTokenCount(contextWindow) else "—"),
        stringResource(R.string.chat_context_stats_total_tokens) to fmt(usage.currentTotal),
        stringResource(R.string.chat_context_stats_usage) to fmtPct(),
        stringResource(R.string.chat_context_stats_input_tokens) to fmt(usage.input),
        stringResource(R.string.chat_context_stats_output_tokens) to fmt(usage.output),
        stringResource(R.string.chat_context_stats_reasoning_tokens) to fmt(usage.reasoning),
        stringResource(R.string.chat_context_stats_cache_tokens) to cacheLabel,
        stringResource(R.string.chat_context_stats_user_messages) to usage.userMessages.toString(),
        stringResource(R.string.chat_context_stats_assistant_messages) to usage.assistantMessages.toString(),
        stringResource(R.string.chat_context_stats_total_cost) to fmtCost,
        stringResource(R.string.chat_context_stats_created) to fmtTime(usage.sessionCreatedAt),
        stringResource(R.string.chat_context_stats_last_activity) to fmtTime(usage.lastActivityAt),
    )
}

/** 2-column stats grid for the context panel. */
@Composable
private fun ContextStatGrid(stats: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val rowSize = 2
        stats.chunked(rowSize).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.forEach { (label, value) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 上下文 token 分布条：彩色分段 + 图例。 */
@Composable
private fun ContextBreakdownBar(segments: List<ContextBreakdownSegment>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.chat_context_breakdown_title),
            style = MaterialTheme.typography.labelLarge,
        )
        // Colored bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .height(8.dp),
        ) {
            segments.forEach { segment ->
                val color = breakdownSegmentColor(segment.key)
                Box(
                    modifier = Modifier
                        .weight((segment.percentage / 100.0).toFloat())
                        .fillMaxHeight()
                        .background(color),
                )
            }
        }
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            segments.forEach { segment ->
                val label = when (segment.key) {
                    ContextBreakdownKey.SYSTEM -> stringResource(R.string.chat_context_breakdown_system)
                    ContextBreakdownKey.USER -> stringResource(R.string.chat_context_breakdown_user)
                    ContextBreakdownKey.ASSISTANT -> stringResource(R.string.chat_context_breakdown_assistant)
                    ContextBreakdownKey.TOOL -> stringResource(R.string.chat_context_breakdown_tool)
                    ContextBreakdownKey.OTHER -> stringResource(R.string.chat_context_breakdown_other)
                }
                val color = breakdownSegmentColor(segment.key)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color),
                    )
                    Text(
                        text = "$label ${String.format(Locale.US, "%.1f", segment.percentage)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.chat_context_breakdown_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

/** 分布条分段颜色。 */
private fun breakdownSegmentColor(key: ContextBreakdownKey): Color = when (key) {
    ContextBreakdownKey.SYSTEM -> Color(0xFF569CD6) // blue
    ContextBreakdownKey.USER -> Color(0xFF4EC9B0)   // green
    ContextBreakdownKey.ASSISTANT -> Color(0xFFC586C0) // purple
    ContextBreakdownKey.TOOL -> Color(0xFFCE9178)  // orange
    ContextBreakdownKey.OTHER -> Color(0xFF808080)  // gray
}

/** 原始消息行：显示角色、ID、时间和展开的 parts。 */
@Composable
private fun ContextRawMessageRow(message: ChatMessage, timeFormat: SimpleDateFormat) {
    var expanded by remember { mutableStateOf(false) }
    val msg = message.message
    val role = msg.role
    val id = msg.id
    val time = timeFormat.format(java.util.Date(msg.time.created))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$role • $id",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "${message.parts.size}p",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (expanded) {
        Column(modifier = Modifier.padding(start = 12.dp)) {
            message.parts.forEach { part ->
                when (part) {
                    is Part.Text -> {
                        if (part.text.isNotBlank()) {
                            Text(
                                text = part.text.take(200) + if (part.text.length > 200) "…" else "",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is Part.Reasoning -> {
                        if (part.text.isNotBlank()) {
                            Text(
                                text = "[reasoning] ${part.text.take(100)}${if (part.text.length > 100) "…" else ""}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is Part.Tool -> {
                        Text(
                            text = "[tool] ${part.tool}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    is Part.File -> {
                        Text(
                            text = "[file] ${part.filename ?: part.url ?: part.mime}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    else -> {}
                }
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

/** 快捷模板选择与管理弹窗（内置模板 + 用户自定义模板，紧凑单行列表）。 */
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

/** 快捷模板新增/编辑对话框。 */
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
