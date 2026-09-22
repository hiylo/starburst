/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : DocumentGenerateDialog.kt
 * Date : 2026/09/22 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.AppPrimaryButton
import org.hiylo.starburst.ui.components.AppSecondaryButton

/** 可选文档类型（[label] 为界面展示名，[type] 为后端 docType）。 */
internal data class DocumentTypeOption(val label: String, val type: String)

/** 支持生成的文档类型：PPT / Word / Excel（对应后端 pptx / docx / xlsx）。 */
internal val documentTypeOptions = listOf(
    DocumentTypeOption("PPT", "pptx"),
    DocumentTypeOption("Word", "docx"),
    DocumentTypeOption("Excel", "xlsx"),
)

/** 把后端 docType（pptx/docx/xlsx）映射为界面展示名，未识别时原样返回。 */
internal fun documentTypeDisplay(type: String): String =
    documentTypeOptions.firstOrNull { it.type == type }?.label ?: type

/** 各文档类型的展示图标：PPT / Word / Excel。 */
private fun documentTypeIcon(type: String): ImageVector = when (type) {
    "pptx" -> Icons.Default.Slideshow
    "docx" -> Icons.Default.Article
    else -> Icons.Default.TableChart
}

/**
 * 「生成文档」对话框：类型选择（PPT/Word/Excel）+ 内容描述，点击生成。
 *
 * @param isGenerating 是否正在生成（进行中禁用类型选择与输入并展示 loading）。
 * @param onGenerate 生成回调（[type] 为后端 docType：pptx/docx/xlsx）。
 * @param onDismiss 关闭回调。
 *
 * @author Hsi Chu
 * @since 3.1.0
 */
@Composable
internal fun DocumentGenerateDialog(
    isGenerating: Boolean,
    onGenerate: (type: String, prompt: String) -> Unit,
    onDismiss: () -> Unit,
    initialType: String? = null,
    initialPrompt: String = "",
) {
    var selectedType by remember { mutableStateOf(initialType ?: documentTypeOptions.first().type) }
    var prompt by remember { mutableStateOf(initialPrompt) }

    ChatDialog(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.document_generate_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = stringResource(R.string.document_generate_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.document_generate_type_label),
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            documentTypeOptions.forEach { option ->
                val selected = selectedType == option.type
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable(enabled = !isGenerating) { selectedType = option.type }
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        documentTypeIcon(option.type),
                        contentDescription = option.label,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text(stringResource(R.string.document_generate_prompt_label)) },
            placeholder = { Text(stringResource(R.string.document_generate_prompt_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            enabled = !isGenerating,
        )
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            AppSecondaryButton(onClick = onDismiss, enabled = !isGenerating) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(Modifier.width(8.dp))
            AppPrimaryButton(
                onClick = { onGenerate(selectedType, prompt) },
                enabled = !isGenerating && prompt.isNotBlank(),
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.document_generate_button))
            }
        }
    }
}

/**
 * 「按意见修改」对话框：只输入修改意见，文档类型沿用原文档。
 *
 * @param docName 原文档名（对话框副标题展示）。
 * @param isRevising 是否正在重新生成（进行中禁用输入并展示 loading）。
 * @param onRevise 修改意见回调。
 * @param onDismiss 关闭回调。
 *
 * @author Hsi Chu
 * @since 3.1.0
 */
@Composable
internal fun DocumentReviseDialog(
    docName: String,
    isRevising: Boolean,
    onRevise: (instruction: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var instruction by remember { mutableStateOf("") }

    ChatDialog(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.document_revise_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = docName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = instruction,
            onValueChange = { instruction = it },
            label = { Text(stringResource(R.string.document_revise_prompt_label)) },
            placeholder = { Text(stringResource(R.string.document_revise_prompt_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            enabled = !isRevising,
        )
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            AppSecondaryButton(onClick = onDismiss, enabled = !isRevising) {
                Text(stringResource(R.string.cancel))
            }
            Spacer(Modifier.width(8.dp))
            AppPrimaryButton(
                onClick = { onRevise(instruction) },
                enabled = !isRevising && instruction.isNotBlank(),
            ) {
                if (isRevising) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.document_revise_button))
            }
        }
    }
}
