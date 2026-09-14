/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatAttachment.kt
 * Date : 2026/09/14 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.compose.elements.MarkdownImage
import org.hiylo.starburst.domain.model.*
import org.hiylo.starburst.data.api.PromptPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import android.os.Build
import android.util.Base64
import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.R


/**
 * VisualTransformation that highlights confirmed @file mentions as colored pills.
 * Only paths present in [confirmedFilePaths] are highlighted; unconfirmed @queries
 * remain unstyled so the user can see they haven't been selected yet.
 */
internal class FileMentionVisualTransformation(
    private val confirmedFilePaths: Set<String>,
    private val highlightColor: Color,
    private val bgColor: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (confirmedFilePaths.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val raw = text.text
        val annotated = buildAnnotatedString {
            append(raw)
            // For each confirmed path, find all occurrences of @path in the text
            for (path in confirmedFilePaths) {
                val needle = "@$path"
                var searchFrom = 0
                while (true) {
                    val idx = raw.indexOf(needle, searchFrom)
                    if (idx == -1) break
                    // Ensure the match is not part of a longer token:
                    // next char after needle should be whitespace, end-of-string, or another @
                    val endIdx = idx + needle.length
                    if (endIdx < raw.length) {
                        val next = raw[endIdx]
                        if (!next.isWhitespace() && next != '@') {
                            searchFrom = endIdx
                            continue
                        }
                    }
                    addStyle(
                        SpanStyle(
                            color = highlightColor,
                            background = bgColor,
                            fontWeight = FontWeight.SemiBold
                        ),
                        start = idx,
                        end = endIdx
                    )
                    searchFrom = endIdx
                }
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}

/**
 * Splits raw input text into a list of [PromptPart] objects.
 * Text around confirmed @file mentions becomes type="text" parts,
 * and each @file mention becomes a type="file" part with a file:// URL.
 */
internal fun buildPromptParts(
    text: String,
    confirmedPaths: Set<String>,
    sessionDirectory: String?
): List<PromptPart> {
    if (confirmedPaths.isEmpty()) {
        val trimmed = text.trim()
        return if (trimmed.isEmpty()) emptyList()
        else listOf(PromptPart(type = "text", text = trimmed))
    }

    // Find all confirmed @path mentions with their positions
    data class Mention(val start: Int, val end: Int, val path: String)
    val mentions = mutableListOf<Mention>()

    for (path in confirmedPaths) {
        val needle = "@$path"
        var searchFrom = 0
        while (true) {
            val idx = text.indexOf(needle, searchFrom)
            if (idx == -1) break
            val endIdx = idx + needle.length
            // Boundary check: next char must be whitespace, end-of-string, or @
            if (endIdx < text.length) {
                val next = text[endIdx]
                if (!next.isWhitespace() && next != '@') {
                    searchFrom = endIdx
                    continue
                }
            }
            mentions.add(Mention(idx, endIdx, path))
            searchFrom = endIdx
        }
    }

    if (mentions.isEmpty()) {
        val trimmed = text.trim()
        return if (trimmed.isEmpty()) emptyList()
        else listOf(PromptPart(type = "text", text = trimmed))
    }

    // Sort by position
    mentions.sortBy { it.start }

    val parts = mutableListOf<PromptPart>()
    var cursor = 0

    for (mention in mentions) {
        // Add text before this mention
        if (mention.start > cursor) {
            val segment = text.substring(cursor, mention.start).trim()
            if (segment.isNotEmpty()) {
                parts.add(PromptPart(type = "text", text = segment))
            }
        }
        // Add file part
        val isDir = mention.path.endsWith("/")
        val absPath = if (sessionDirectory != null) "$sessionDirectory/${mention.path}" else mention.path
        val displayName = mention.path.trimEnd('/').substringAfterLast('/')
        parts.add(
            PromptPart(
                type = "file",
                path = mention.path,
                mime = if (isDir) "application/x-directory" else "text/plain",
                url = "file:///$absPath",
                filename = displayName
            )
        )
        cursor = mention.end
    }

    // Trailing text
    if (cursor < text.length) {
        val segment = text.substring(cursor).trim()
        if (segment.isNotEmpty()) {
            parts.add(PromptPart(type = "text", text = segment))
        }
    }

    return parts
}

/** An image attachment ready to send. */
internal data class ImageAttachment(
    val uri: Uri,
    val mime: String,
    val filename: String,
    val dataUrl: String, // "data:<mime>;base64,..."
    val sizeBytes: Int = 0,
) {
    val isImage: Boolean get() = mime.startsWith("image/")
}

internal enum class LocalAttachmentValidation { ACCEPTED, UNSUPPORTED, TOO_LARGE }

private const val MAX_DOCUMENT_ATTACHMENT_BYTES = 10 * 1024 * 1024
private const val MAX_TEXT_ATTACHMENT_BYTES = 2 * 1024 * 1024

/**
 * 单条消息进入 Markdown 渲染器的最大字符数。
 * 超过该阈值时降级为纯文本预览，避免病态内容（如大量未闭合的 HTML 标签）
 * 让 mikepenz 解析器长时间占满主线程并耗尽堆内存（曾导致 512MB OOM 闪退）。
 */
private val TEXT_FILE_EXTENSIONS = setOf(
    "txt", "md", "markdown", "json", "jsonl", "xml", "yaml", "yml", "toml", "csv", "tsv",
    "kt", "kts", "java", "js", "jsx", "ts", "tsx", "py", "rb", "go", "rs", "c", "h", "cpp", "hpp",
    "cs", "swift", "sh", "bash", "zsh", "fish", "sql", "html", "css", "scss", "gradle", "properties",
    "ini", "conf", "config", "log", "env", "gitignore",
)

internal fun validateLocalAttachment(mime: String, filename: String, sizeBytes: Long): LocalAttachmentValidation {
    val extension = filename.substringAfterLast('.', "").lowercase()
    val isText = mime.startsWith("text/") || extension in TEXT_FILE_EXTENSIONS || mime in setOf(
        "application/json", "application/xml", "application/javascript", "application/x-yaml", "application/yaml",
    )
    val supported = mime.startsWith("image/") || mime == "application/pdf" || isText
    if (!supported) return LocalAttachmentValidation.UNSUPPORTED
    val limit = if (isText) MAX_TEXT_ATTACHMENT_BYTES else MAX_DOCUMENT_ATTACHMENT_BYTES
    return if (sizeBytes > limit) LocalAttachmentValidation.TOO_LARGE else LocalAttachmentValidation.ACCEPTED
}

private fun attachmentMetadata(contentResolver: android.content.ContentResolver, uri: Uri): Pair<String, Long?> {
    var name: String? = null
    var size: Long? = null
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0) name = cursor.getString(nameIndex)
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
        }
    }
    return (name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "attachment") to size
}

private fun readBytesLimited(input: java.io.InputStream, limit: Int): ByteArray? {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (output.size() + count > limit) return null
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal data class ImageSaveRequest(
    val bytes: ByteArray,
    val mime: String,
    val filename: String,
)

internal data class DownloadedMarkdownImage(
    val bytes: ByteArray,
    val mime: String,
    val filename: String,
)

internal fun decodeDataUrlBytes(dataUrl: String): ByteArray? {
    val encoded = dataUrl.substringAfter(',', missingDelimiterValue = "")
    if (encoded.isBlank()) return null
    return try {
        Base64.decode(encoded, Base64.DEFAULT)
    } catch (_: Exception) {
        null
    }
}

internal fun resolveCachedMessageImage(url: String, appCacheDirectory: java.io.File): java.io.File? {
    if (!url.startsWith("file:", ignoreCase = true)) return null
    return try {
        val imageCacheDirectory = java.io.File(appCacheDirectory, "message-images").canonicalFile
        java.io.File(java.net.URI(url)).canonicalFile.takeIf {
            it.parentFile == imageCacheDirectory && it.isFile
        }
    } catch (_: Exception) {
        null
    }
}

internal fun decodePartFileBytes(file: Part.File, appCacheDirectory: java.io.File): ByteArray? {
    val url = file.url ?: return null
    if (url.startsWith("file:", ignoreCase = true)) {
        return try {
            resolveCachedMessageImage(url, appCacheDirectory)?.readBytes()
        } catch (_: Exception) {
            null
        }
    }
    val encoded = if (url.contains(',')) url.substringAfter(',') else url
    if (encoded.isBlank()) return null
    return try {
        Base64.decode(encoded, Base64.DEFAULT)
    } catch (_: Exception) {
        null
    }
}

internal fun partFileImageModel(file: Part.File, appCacheDirectory: java.io.File): Any? {
    val url = file.url ?: return null
    if (url.startsWith("file:", ignoreCase = true)) {
        return resolveCachedMessageImage(url, appCacheDirectory)
    }
    return decodePartFileBytes(file, appCacheDirectory)
}

internal fun extensionForMime(mime: String): String {
    return when (mime.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "img"
    }
}

internal suspend fun downloadMarkdownImage(url: String): DownloadedMarkdownImage? = withContext(Dispatchers.IO) {
    if (url.startsWith("data:", ignoreCase = true)) {
        val mime = url.substringAfter("data:").substringBefore(';').takeIf { it.startsWith("image/") }
            ?: "image/png"
        val bytes = decodeDataUrlBytes(url) ?: return@withContext null
        return@withContext DownloadedMarkdownImage(bytes, mime, "image.${extensionForMime(mime)}")
    }

    val connection = try {
        (java.net.URL(url).openConnection() as? java.net.HttpURLConnection)?.apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "image/*")
        }
    } catch (e: Exception) {
        Log.e("MarkdownImage", "Failed to open image URL", e)
        null
    } ?: return@withContext null

    try {
        val bytes = connection.inputStream.use {
            readBytesLimited(it, MAX_DOCUMENT_ATTACHMENT_BYTES)
        } ?: return@withContext null
        val mime = connection.contentType
            ?.substringBefore(';')
            ?.takeIf { it.startsWith("image/") }
            ?: java.net.URLConnection.guessContentTypeFromName(url)
            ?: "image/png"
        val pathFilename = runCatching { java.net.URL(url).path.substringAfterLast('/') }.getOrNull()
            ?.takeIf(String::isNotBlank)
        val filename = pathFilename ?: "image.${extensionForMime(mime)}"
        DownloadedMarkdownImage(bytes, mime, filename)
    } catch (e: Exception) {
        Log.e("MarkdownImage", "Failed to download image", e)
        null
    } finally {
        connection.disconnect()
    }
}

internal fun imageThumbnailModel(attachment: ImageAttachment): Any {
    if (attachment.uri.scheme.equals("data", ignoreCase = true)) {
        val encoded = attachment.dataUrl.substringAfter(',', missingDelimiterValue = "")
        if (encoded.isNotBlank()) {
            return try {
                Base64.decode(encoded, Base64.DEFAULT)
            } catch (_: Exception) {
                attachment.dataUrl
            }
        }
    }
    return attachment.uri
}

internal data class PreparedAttachment(
    val attachment: ImageAttachment,
    val comparison: AttachmentComparison? = null
)

internal data class AttachmentComparison(
    val originalBytes: Int,
    val optimizedBytes: Int,
    val originalEstimatedTokens: Int,
    val optimizedEstimatedTokens: Int
)

internal fun estimateVisionTokens(width: Int, height: Int): Int {
    if (width <= 0 || height <= 0) return 0
    return ((width.toLong() * height.toLong()) / 750.0).toInt()
}

internal fun formatFileSize(bytes: Int): String {
    val value = bytes.toDouble()
    return when {
        value >= 1024.0 * 1024.0 -> String.format("%.2f MB", value / (1024.0 * 1024.0))
        value >= 1024.0 -> String.format("%.1f KB", value / 1024.0)
        else -> "$bytes B"
    }
}

internal suspend fun buildAttachmentFromUri(
    contentResolver: android.content.ContentResolver,
    uri: Uri,
    compressImages: Boolean,
    maxLongSidePx: Int = 1440,
    webpQuality: Int = 60
): PreparedAttachment? = withContext(Dispatchers.IO) {
    val (originalFilename, declaredSize) = attachmentMetadata(contentResolver, uri)
    var mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
    val extension = originalFilename.substringAfterLast('.', "").lowercase()
    if (mimeType == "application/octet-stream" && extension in TEXT_FILE_EXTENSIONS) mimeType = "text/plain"
    if (validateLocalAttachment(mimeType, originalFilename, declaredSize ?: 0) != LocalAttachmentValidation.ACCEPTED) {
        return@withContext null
    }
    val isText = mimeType.startsWith("text/") || extension in TEXT_FILE_EXTENSIONS || mimeType in setOf(
        "application/json", "application/xml", "application/javascript", "application/x-yaml", "application/yaml",
    )
    val byteLimit = if (isText) MAX_TEXT_ATTACHMENT_BYTES else MAX_DOCUMENT_ATTACHMENT_BYTES
    val bytes = contentResolver.openInputStream(uri)?.use { readBytesLimited(it, byteLimit) } ?: return@withContext null

    val shouldOptimize = compressImages && (mimeType == "image/png" || mimeType == "image/jpeg")
    if (!shouldOptimize) {
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return@withContext PreparedAttachment(
            attachment = ImageAttachment(
                uri = uri,
                mime = mimeType,
                filename = originalFilename,
                dataUrl = "data:$mimeType;base64,$base64",
                sizeBytes = bytes.size,
            )
        )
    }

    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    if (bitmap == null) {
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return@withContext PreparedAttachment(
            attachment = ImageAttachment(
                uri = uri,
                mime = mimeType,
                filename = originalFilename,
                dataUrl = "data:$mimeType;base64,$base64",
                sizeBytes = bytes.size,
            )
        )
    }

    val srcWidth = bitmap.width
    val srcHeight = bitmap.height
    val longSide = maxOf(srcWidth, srcHeight)
    val resizeEnabled = maxLongSidePx > 0
    val scale = if (resizeEnabled && longSide > maxLongSidePx) {
        maxLongSidePx.toFloat() / longSide.toFloat()
    } else {
        1f
    }
    val outWidth = (srcWidth * scale).toInt().coerceAtLeast(1)
    val outHeight = (srcHeight * scale).toInt().coerceAtLeast(1)
    val resizedBitmap = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, outWidth, outHeight, true) else bitmap

    val output = java.io.ByteArrayOutputStream()
    @Suppress("DEPRECATION")
    val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Bitmap.CompressFormat.WEBP_LOSSY
    } else {
        Bitmap.CompressFormat.WEBP
    }
    val compressed = resizedBitmap.compress(format, webpQuality.coerceIn(1, 100), output)
    if (resizedBitmap !== bitmap) {
        resizedBitmap.recycle()
    }
    bitmap.recycle()

    if (!compressed) {
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return@withContext PreparedAttachment(
            attachment = ImageAttachment(
                uri = uri,
                mime = mimeType,
                filename = originalFilename,
                dataUrl = "data:$mimeType;base64,$base64",
                sizeBytes = bytes.size,
            )
        )
    }

    val webpBytes = output.toByteArray()
    if (scale >= 0.999f && webpBytes.size >= bytes.size) {
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return@withContext PreparedAttachment(
            attachment = ImageAttachment(
                uri = uri,
                mime = mimeType,
                filename = originalFilename,
                dataUrl = "data:$mimeType;base64,$base64",
                sizeBytes = bytes.size,
            )
        )
    }
    val base64 = Base64.encodeToString(webpBytes, Base64.NO_WRAP)
    val optimizedFilename = originalFilename.substringBeforeLast('.', originalFilename) + ".webp"
    return@withContext PreparedAttachment(
        attachment = ImageAttachment(
            uri = uri,
            mime = "image/webp",
            filename = optimizedFilename,
            dataUrl = "data:image/webp;base64,$base64",
            sizeBytes = webpBytes.size,
        ),
        comparison = AttachmentComparison(
            originalBytes = bytes.size,
            optimizedBytes = webpBytes.size,
            originalEstimatedTokens = estimateVisionTokens(srcWidth, srcHeight),
            optimizedEstimatedTokens = estimateVisionTokens(outWidth, outHeight)
        )
    )
}

/**
 * 在独立的重组作用域内监听软键盘可见性，仅在可见状态翻转时通过 [onChanged] 上报，
 * 避免在 ChatScreen 根作用域直接读取 [WindowInsets.ime] 导致键盘动画期间全屏逐帧重组。
 */
