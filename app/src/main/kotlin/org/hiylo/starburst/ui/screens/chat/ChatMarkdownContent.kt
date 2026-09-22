/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatMarkdownContent.kt
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.ui.screens.chat

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownImage
import com.mikepenz.markdown.coil2.Coil2ImageTransformerImpl
import com.mikepenz.markdown.model.DefaultMarkdownAnnotator
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.hiylo.starburst.domain.model.*
import org.hiylo.starburst.ui.theme.CodeTypography
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.isAmoledTheme

private const val MAX_MARKDOWN_RENDER_CHARS = 40_000

/** 超长内容降级预览时最多展示的字符数。 */
private const val MARKDOWN_PREVIEW_CHARS = 8_000


/**
 * Renders markdown content using mikepenz markdown renderer with code syntax highlighting.
 */
@Composable
internal fun MarkdownContent(
    markdown: String,
    textColor: Color,
    isUser: Boolean
) {
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    val requestSaveImage = LocalImageSaveRequest.current
    val coroutineScope = rememberCoroutineScope()
    val normalizedMarkdown = remember(markdown) {
        normalizeTaskListMarkers(preserveRawHtmlPayload(markdown))
    }

    // 超长内容（含病态 markdown，如大量未闭合 HTML 标签）会让 mikepenz 解析器
    // 长时间占用主线程并耗尽堆内存。超过阈值时降级为可滚动纯文本预览，
    // 点击可展开查看全文（纯文本，不走 markdown 解析）。
    if (normalizedMarkdown.length > MAX_MARKDOWN_RENDER_CHARS) {
        LargeMarkdownFallback(
            text = markdown,
            textColor = textColor,
            isUser = isUser,
        )
        return
    }

    val isAmoled = isAmoledTheme()

    // Inline code: keep text styling, but no opaque background so selection remains visible.
    val inlineCodeFg = when {
        isAmoled -> MaterialTheme.colorScheme.onSurface
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.primary
    }
    // Code blocks: distinct background
    val codeBlockBg = when {
        isAmoled -> MaterialTheme.colorScheme.surfaceContainerLow
        isUser -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val codeBlockFg = when {
        isAmoled -> MaterialTheme.colorScheme.onSurface
        isUser -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    // Font size from settings: small=13sp, medium=14sp (default), large=16sp
    val fontSizeSetting = LocalChatFontSize.current
    val lineHeightMultiplier = LocalChatLineHeight.current
    val (bodyFontSize, bodyLineHeight) = when (fontSizeSetting) {
        "small" -> 13.sp to 18.sp * lineHeightMultiplier
        "large" -> 16.sp to 26.sp * lineHeightMultiplier
        else -> 14.sp to 22.sp * lineHeightMultiplier // medium
    }
    val (codeFontSize, codeLineHeight) = when (fontSizeSetting) {
        "small" -> 11.sp to 16.sp * lineHeightMultiplier
        "large" -> 15.sp to 22.sp * lineHeightMultiplier
        else -> 13.sp to 20.sp * lineHeightMultiplier // medium
    }

    // Balanced text style with better line-height for readability
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
        color = textColor,
        fontSize = bodyFontSize,
        lineHeight = bodyLineHeight
    )

    val linkTextColor = when {
        isAmoled -> MaterialTheme.colorScheme.primary
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.primary
    }

    val colors = markdownColor(
        text = textColor,
        codeText = codeBlockFg,
        inlineCodeText = inlineCodeFg,
        linkText = linkTextColor,
        codeBackground = codeBlockBg,
        inlineCodeBackground = Color.Transparent,
        dividerColor = textColor.copy(alpha = 0.32f)
    )

    val typography = markdownTypography(
        h1 = MaterialTheme.typography.titleLarge.copy(
            color = textColor,
            fontWeight = FontWeight.Bold,
            lineHeight = 32.sp
        ),
        h2 = MaterialTheme.typography.titleMedium.copy(
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 28.sp
        ),
        h3 = MaterialTheme.typography.titleSmall.copy(
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp
        ),
        h4 = MaterialTheme.typography.bodyLarge.copy(
            color = textColor,
            fontWeight = FontWeight.SemiBold
        ),
        h5 = MaterialTheme.typography.bodyMedium.copy(
            color = textColor,
            fontWeight = FontWeight.SemiBold
        ),
        h6 = MaterialTheme.typography.bodyMedium.copy(
            color = textColor.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium
        ),
        text = bodyStyle,
        code = CodeTypography.copy(color = codeBlockFg, fontSize = codeFontSize, lineHeight = codeLineHeight),
        inlineCode = CodeTypography.copy(
            color = inlineCodeFg,
            fontSize = codeFontSize,
            fontWeight = FontWeight.Medium
        ),
        quote = bodyStyle.copy(
            color = textColor.copy(alpha = 0.65f),
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        ),
        paragraph = bodyStyle,
        ordered = bodyStyle,
        bullet = bodyStyle,
        list = bodyStyle,
        link = bodyStyle.copy(
            color = linkTextColor,
            fontWeight = FontWeight.Medium
        )
    )

    val components = markdownComponents(
        codeBlock = safeHighlightedCodeBlock,
        codeFence = safeHighlightedCodeFence,
        image = { model ->
            val imageUrl = remember(model.content, model.node) {
                markdownImageUrl(model.content, model.node)
            }
            Box(
                modifier = Modifier.clickable(enabled = imageUrl != null) {
                    previewImageUrl = imageUrl
                },
            ) {
                MarkdownImage(model.content, model.node)
                if (imageUrl != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.58f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f)),
                    ) {
                        IconButton(
                            onClick = { previewImageUrl = imageUrl },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = stringResource(R.string.chat_image),
                                modifier = Modifier.size(21.dp),
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        },
        table = horizontallyScrollableMarkdownTable,
    )
    val clickableImageTransformer = remember {
        object : ImageTransformer by Coil2ImageTransformerImpl {
            @Composable
            override fun transform(link: String): ImageData? {
                val image = Coil2ImageTransformerImpl.transform(link) ?: return null
                return image.copy(
                    modifier = image.modifier.clickable { previewImageUrl = link },
                )
            }
        }
    }

    val chatLinkHandler = LocalChatLinkHandler.current
    val defaultUriHandler = LocalUriHandler.current
    val chatUriHandler = remember(chatLinkHandler, defaultUriHandler) {
        object : UriHandler {
            override fun openUri(uri: String) {
                if (isSameServerUrl(uri, chatLinkHandler.serverBaseUrl)) {
                    chatLinkHandler.openInApp(uri)
                    return
                }
                val scheme = runCatching { Uri.parse(uri).scheme }.getOrNull()?.lowercase()
                if (scheme == "http" || scheme == "https") {
                    defaultUriHandler.openUri(uri)
                }
                // 其它 scheme（intent:/tel:/自定义）一律忽略，避免不可信内容触发系统能力。
            }
        }
    }

    CompositionLocalProvider(LocalUriHandler provides chatUriHandler) {
        SelectionContainer {
            Markdown(
                content = normalizedMarkdown,
                colors = colors,
                typography = typography,
                flavour = ChatMarkdownFlavour,
                annotator = ChatMarkdownAnnotator,
                components = components,
                imageTransformer = clickableImageTransformer,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    previewImageUrl?.let { imageUrl ->
        ImagePreviewDialog(
            imageModel = imageUrl,
            contentDescription = null,
            onDismiss = { previewImageUrl = null },
            onSave = {
                coroutineScope.launch {
                    downloadMarkdownImage(imageUrl)?.let { image ->
                        requestSaveImage(image.bytes, image.mime, image.filename)
                    }
                }
            },
        )
    }
}

/**
 * 超长内容的降级渲染：不走 markdown 解析器，直接展示可滚动、可选择的纯文本。
 * 默认仅预览前 [MARKDOWN_PREVIEW_CHARS] 个字符，点击「查看全文」后展开完整内容，
 * 避免病态输入把主线程拖死或触发 OOM。
 */
@Composable
private fun LargeMarkdownFallback(
    text: String,
    textColor: Color,
    isUser: Boolean,
) {
    val isAmoled = isAmoledTheme()
    var showFullText by remember { mutableStateOf(false) }
    val displayText = if (showFullText) text else text.take(MARKDOWN_PREVIEW_CHARS)
    val previewTruncated = !showFullText && text.length > MARKDOWN_PREVIEW_CHARS

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (previewTruncated) {
            Text(
                text = stringResource(R.string.chat_large_content_notice, MARKDOWN_PREVIEW_CHARS),
                style = MaterialTheme.typography.bodySmall,
                color = if (isUser) textColor.copy(alpha = 0.75f) else MaterialTheme.colorScheme.tertiary,
            )
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface,
            border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)) else null,
            tonalElevation = if (isAmoled) 0.dp else 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                SelectionContainer {
                    Text(
                        text = displayText,
                        style = CodeTypography.copy(fontSize = 12.sp),
                        color = textColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = (LocalConfiguration.current.screenHeightDp.dp / 2).coerceAtLeast(200.dp))
                            .verticalScroll(rememberScrollState()),
                    )
                }
                if (text.length > MARKDOWN_PREVIEW_CHARS) {
                    TextButton(
                        onClick = { showFullText = !showFullText },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(
                            text = stringResource(
                                if (showFullText) R.string.chat_collapse else R.string.chat_show_full_text,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private val MarkdownImageUrlRegex = Regex("""!\[[^]]*]\(\s*(?:<([^>]+)>|([^\s)]+))""")

private fun org.intellij.markdown.ast.ASTNode.findMarkdownDescendant(
    type: org.intellij.markdown.IElementType,
): org.intellij.markdown.ast.ASTNode? {
    if (this.type == type) return this
    return children.firstNotNullOfOrNull { it.findMarkdownDescendant(type) }
}

internal fun markdownImageUrl(
    content: String,
    node: org.intellij.markdown.ast.ASTNode,
): String? = node
    .findMarkdownDescendant(MarkdownElementTypes.LINK_DESTINATION)
    ?.getUnescapedTextInNode(content)
    ?.removeSurrounding("<", ">")
    ?.takeIf(String::isNotBlank)

internal fun markdownImageUrl(content: String, startOffset: Int, endOffset: Int): String? {
    val markdownImage = content.substring(
        startIndex = startOffset.coerceIn(0, content.length),
        endIndex = endOffset.coerceIn(startOffset.coerceIn(0, content.length), content.length),
    )
    val match = MarkdownImageUrlRegex.find(markdownImage) ?: return null
    return (match.groupValues[1].ifBlank { match.groupValues[2] }).takeIf(String::isNotBlank)
}

private val HtmlDocumentHintRegex = Regex("(?is)<!doctype\\s+html\\b|<\\s*html\\b")
private val HtmlTagRegex = Regex("(?is)<\\s*/?\\s*[a-z][^>]*>")
private val MarkdownFenceStartRegex = Regex("^ {0,3}(`{3,}|~{3,})")
private val TaskListMarkerRegex = Regex("^(\\s*[-+*]\\s+)\\[([ xX])]([ \\t]+)")
internal val ChatMarkdownFlavour = GFMFlavourDescriptor()

internal fun normalizeTaskListMarkers(markdown: String): String {
    var fenceMarker: Char? = null
    var minimumFenceLength = 0
    return markdown.split('\n').joinToString("\n") { line ->
        val marker = MarkdownFenceStartRegex.find(line)?.groupValues?.get(1)
        if (fenceMarker != null) {
            if (marker != null && marker.first() == fenceMarker && marker.length >= minimumFenceLength) {
                fenceMarker = null
                minimumFenceLength = 0
            }
            line
        } else if (marker != null) {
            fenceMarker = marker.first()
            minimumFenceLength = marker.length
            line
        } else {
            TaskListMarkerRegex.replace(line) { match ->
                val checkbox = if (match.groupValues[2].equals("x", ignoreCase = true)) "\u2611" else "\u2610"
                match.groupValues[1] + checkbox + match.groupValues[3]
            }
        }
    }
}
internal val ChatMarkdownAnnotator = DefaultMarkdownAnnotator { content, node ->
    markdownTokenReplacement(content, node)?.let { replacement ->
        append(replacement)
        true
    } ?: false
}

private val EmailAutolinkRegex = Regex("<[^<>\\s@]+@[^<>\\s@]+>")

internal fun markdownTokenReplacement(content: String, node: org.intellij.markdown.ast.ASTNode): String? {
    val raw = content.substring(node.startOffset, node.endOffset)
    return when {
        node.type == GFMTokenTypes.TILDE && node.parent?.type != GFMElementTypes.STRIKETHROUGH -> raw
        node.type == MarkdownTokenTypes.EMAIL_AUTOLINK -> raw
        node.type == MarkdownTokenTypes.LT && EmailAutolinkRegex.matchAt(content, node.startOffset) != null -> ""
        node.type == MarkdownTokenTypes.GT -> {
            val openingOffset = content.lastIndexOf('<', node.startOffset)
            val match = openingOffset.takeIf { it >= 0 }?.let { EmailAutolinkRegex.matchAt(content, it) }
            if (match?.range?.last == node.startOffset) "" else null
        }
        node.type == GFMTokenTypes.CHECK_BOX -> {
            if (raw.contains('x', ignoreCase = true)) "\u2611" else "\u2610"
        }
        else -> null
    }
}

internal fun looksLikeHtmlPayload(text: String): Boolean {
    if (text.isBlank()) return false
    if (HtmlDocumentHintRegex.containsMatchIn(text)) return true
    return HtmlTagRegex.findAll(text).take(12).count() >= 6
}

internal fun normalizeHtmlForEmbeddedPreview(html: String): String {
    if (html.isBlank()) return html
    val overrideCss = """
        html, body {
          margin: 0 !important;
          padding: 8px !important;
          min-height: auto !important;
          height: auto !important;
        }
        body {
          display: block !important;
          align-items: flex-start !important;
          justify-content: flex-start !important;
          overflow: auto !important;
        }
        .container {
          align-items: flex-start !important;
          justify-content: flex-start !important;
          height: auto !important;
          min-height: auto !important;
          width: 100% !important;
          margin: 0 !important;
        }
    """.trimIndent()

    val styleBlock = "<style>$overrideCss</style>"
    return if (html.contains("</head>", ignoreCase = true)) {
        html.replaceFirst(Regex("(?i)</head>"), "$styleBlock</head>")
    } else {
        "<head>$styleBlock</head>$html"
    }
}

private fun preserveRawHtmlPayload(markdown: String): String {
    if (markdown.isBlank()) return markdown
    if ("```" in markdown) return markdown

    val looksLikeHtmlDocument = HtmlDocumentHintRegex.containsMatchIn(markdown)
    val htmlTagCount = HtmlTagRegex.findAll(markdown).take(16).count()
    if (!looksLikeHtmlDocument && htmlTagCount < 8) return markdown

    return buildString(markdown.length + 16) {
        append("```text\n")
        append(markdown.trimEnd())
        append("\n```")
    }
}
