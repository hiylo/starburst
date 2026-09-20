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
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.ProviderIcon
import org.hiylo.starburst.ui.components.AppDialog
import org.hiylo.starburst.ui.components.AppPickerItemShape
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.AppSecondaryButton
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

/** Build a list of stat items for the context panel grid. */

/** 2-column stats grid for the context panel. */

/** 上下文 token 分布条：彩色分段 + 图例。 */

/** 原始消息行：显示角色、ID、时间和展开的 parts。 */

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

/** 快捷模板选择与管理弹窗（内置模板 + 用户自定义模板，紧凑单行列表）。 */

/** 快捷模板新增/编辑对话框。 */
