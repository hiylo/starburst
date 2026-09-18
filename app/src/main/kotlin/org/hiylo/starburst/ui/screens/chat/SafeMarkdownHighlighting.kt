/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SafeMarkdownHighlighting.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.hiylo.starburst.R
import org.hiylo.starburst.logging.AppLogger as Log
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownDimens
import com.mikepenz.markdown.compose.LocalMarkdownPadding
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.components.MarkdownComponent
import com.mikepenz.markdown.compose.elements.MarkdownCodeBackground
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.material.MarkdownBasicText
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import org.intellij.markdown.ast.ASTNode

private const val SMH_TAG = "SafeMarkdownHighlight"

/** 代码块折叠阈值：超过该行数时默认折叠，用户可点击展开。 */
private const val CODE_FOLD_LINE_THRESHOLD = 14

internal val safeHighlightedCodeFence: MarkdownComponent = {
    SafeMarkdownHighlightedCodeFence(it.content, it.node)
}

internal val safeHighlightedCodeBlock: MarkdownComponent = {
    SafeMarkdownHighlightedCodeBlock(it.content, it.node)
}

internal val horizontallyScrollableMarkdownTable: MarkdownComponent = {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        MarkdownTable(it.content, it.node, it.typography.text)
    }
}


@Composable
private fun SafeMarkdownHighlightedCodeFence(
    content: String,
    node: ASTNode,
    highlights: Highlights.Builder = Highlights.Builder(),
) {
    MarkdownCodeFence(content, node) { code, language ->
        SafeMarkdownHighlightedCode(code, language, highlights)
    }
}

@Composable
private fun SafeMarkdownHighlightedCodeBlock(
    content: String,
    node: ASTNode,
    highlights: Highlights.Builder = Highlights.Builder(),
) {
    MarkdownCodeBlock(content, node) { code, language ->
        SafeMarkdownHighlightedCode(code, language, highlights)
    }
}

@Composable
private fun SafeMarkdownHighlightedCode(
    code: String,
    language: String?,
    highlights: Highlights.Builder = Highlights.Builder(),
    style: TextStyle = LocalMarkdownTypography.current.code,
) {
    val backgroundCodeColor = LocalMarkdownColors.current.codeBackground
    val codeBackgroundCornerSize = LocalMarkdownDimens.current.codeBackgroundCornerSize
    val codeBlockPadding = LocalMarkdownPadding.current.codeBlock
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val codeTextColor = LocalMarkdownColors.current.codeText
    val codeScrollModifier = if (LocalCodeWordWrap.current) {
        Modifier
    } else {
        Modifier.horizontalScroll(rememberScrollState())
    }

    val needsFold = code.lines().size > CODE_FOLD_LINE_THRESHOLD
    var expanded by remember(code) { mutableStateOf(false) }
    val displayCode = if (needsFold && !expanded) {
        code.lines().take(CODE_FOLD_LINE_THRESHOLD).joinToString("\n")
    } else {
        code
    }
    val annotatedCode = remember(displayCode, language, highlights) {
        buildSafeHighlightedAnnotatedString(displayCode, language, highlights)
    }

    MarkdownCodeBackground(
        color = backgroundCodeColor,
        shape = RoundedCornerShape(codeBackgroundCornerSize),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!language.isNullOrBlank()) {
                    Text(
                        text = language.lowercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = codeTextColor.copy(alpha = 0.6f),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (needsFold) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = codeTextColor.copy(alpha = 0.6f),
                        )
                        Spacer(modifier = Modifier.size(2.dp))
                        Text(
                            text = stringResource(if (expanded) R.string.chat_code_collapse else R.string.chat_code_expand),
                            style = MaterialTheme.typography.labelSmall,
                            color = codeTextColor.copy(alpha = 0.7f),
                        )
                    }
                }
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        Toast.makeText(context, R.string.chat_copied_clipboard, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .padding(end = 4.dp, bottom = 4.dp)
                        .size(22.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.chat_copy),
                        modifier = Modifier.size(14.dp),
                        tint = codeTextColor.copy(alpha = 0.42f),
                    )
                }
            }
            MarkdownBasicText(
                annotatedCode,
                color = codeTextColor,
                modifier = codeScrollModifier
                    .padding(codeBlockPadding),
                style = style,
            )
        }
    }
}

internal fun buildSafeHighlightedAnnotatedString(
    code: String,
    language: String?,
    highlightsBuilder: Highlights.Builder,
): AnnotatedString {
    return try {
        val syntaxLanguage = language?.let { SyntaxLanguage.getByName(it) }
        val codeHighlights = highlightsBuilder
            .code(code)
            .let { builder -> if (syntaxLanguage != null) builder.language(syntaxLanguage) else builder }
            .build()

        val highlightedCode = codeHighlights.getCode()
        buildAnnotatedString {
            text(highlightedCode)

            codeHighlights.getHighlights()
                .filterIsInstance<ColorHighlight>()
                .forEach { highlight ->
                    addSafeColorStyle(
                        style = SpanStyle(color = Color(highlight.rgb).copy(alpha = 1f)),
                        start = highlight.location.start,
                        end = highlight.location.end,
                        text = highlightedCode,
                    )
                }

            codeHighlights.getHighlights()
                .filterIsInstance<BoldHighlight>()
                .forEach { highlight ->
                    addSafeStyle(
                        style = SpanStyle(fontWeight = FontWeight.Bold),
                        start = highlight.location.start,
                        end = highlight.location.end,
                        textLength = highlightedCode.length,
                    )
                }
        }
    } catch (e: Exception) {
        Log.w(SMH_TAG, "Syntax highlighting failed; rendering plain code", e)
        AnnotatedString(code)
    }
}

private fun AnnotatedString.Builder.addSafeColorStyle(
    style: SpanStyle,
    start: Int,
    end: Int,
    text: String,
) {
    if (start < 0 || end > text.length || start >= end) return
    var segmentStart = start
    for (index in start until end) {
        if (text[index].shouldInheritCodeColor()) {
            addSafeStyle(style, segmentStart, index, text.length)
            segmentStart = index + 1
        }
    }
    addSafeStyle(style, segmentStart, end, text.length)
}

private fun Char.shouldInheritCodeColor(): Boolean = when (Character.getType(this).toInt()) {
    Character.CONNECTOR_PUNCTUATION.toInt(),
    Character.DASH_PUNCTUATION.toInt(),
    Character.START_PUNCTUATION.toInt(),
    Character.END_PUNCTUATION.toInt(),
    Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
    Character.FINAL_QUOTE_PUNCTUATION.toInt(),
    Character.OTHER_PUNCTUATION.toInt(),
    Character.MATH_SYMBOL.toInt(),
    Character.CURRENCY_SYMBOL.toInt(),
    Character.MODIFIER_SYMBOL.toInt(),
    -> true
    else -> false
}

private fun AnnotatedString.Builder.addSafeStyle(
    style: SpanStyle,
    start: Int,
    end: Int,
    textLength: Int,
) {
    if (start < 0 || end > textLength || start >= end) return
    addStyle(style = style, start = start, end = end)
}

private fun AnnotatedString.Builder.text(
    text: String,
    style: SpanStyle = SpanStyle(),
) = withStyle(style = style) {
    append(text)
}
