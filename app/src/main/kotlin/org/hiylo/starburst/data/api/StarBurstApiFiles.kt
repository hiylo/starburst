/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : StarBurstApiFiles.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import java.io.IOException
import java.util.Base64

// ============ Files（V2 fs 面） ============

/**
 * 按文件名/路径模糊查找工作区文件（V2 `GET /api/fs/find`）。
 *
 * V1 `GET /find?pattern=` 是正文正则文本搜索，返回行号级 [SearchMatch]；V2 `find` 只做
 * 文件名/路径模糊匹配（query），响应为 `FileSystemEntry{path,type}`，不含正文与行号。
 * 此处做宽容映射：仅填充 path，lines/lineNumber/absoluteOffset 置空
 * （调用方不得依赖行号）。
 */
suspend fun StarBurstApi.searchText(conn: ServerConnection, pattern: String): List<SearchMatch> {
    val response = httpClient.get("${conn.baseUrl}/api/fs/find") {
        conn.authHeader?.let { header("Authorization", it) }
        parameter("query", pattern)
    }
    if (!response.status.isSuccess()) throw IOException("fs/find failed: HTTP ${response.status.value}")
    return response.body<V2FsResponse>().data.map { entry ->
        SearchMatch(path = entry.path.trimEnd('/'), lines = "", lineNumber = 0, absoluteOffset = 0)
    }
}

/**
 * 按关键词模糊查找文件/目录路径（V2 `GET /api/fs/find`），
 * 用于 @ 提及补全、目录选择等场景。
 *
 * 参数映射：query→query、type→type（file|directory）、limit→limit（V2 为字符串）、
 * directory→`location[directory]`（并保留 `x-starburst-directory` 头供后端镜像拦截敏感目录）。
 * V2 无 `dirs` 等价参数：省略 type 时同时返回文件与目录，与 V1 `dirs="true"` 一致。
 * V2 目录条目 path 带尾斜杠，此处统一 `trimEnd('/')` 与 V1 返回一致。
 */
suspend fun StarBurstApi.findFiles(
    conn: ServerConnection,
    query: String,
    type: String? = null,
    directory: String? = null,
    limit: Int? = null,
    dirs: String? = null,
): List<String> {
    val response = httpClient.get("${conn.baseUrl}/api/fs/find") {
        conn.authHeader?.let { header("Authorization", it) }
        applyFsLocation(directory)
        parameter("query", query)
        type?.let { parameter("type", it) }
        limit?.let { parameter("limit", it.toString()) }
    }
    if (!response.status.isSuccess()) throw IOException("fs/find failed: HTTP ${response.status.value}")
    return response.body<V2FsResponse>().data.map { it.path.trimEnd('/') }
}

/**
 * 读取单个文件内容（V2 `GET /api/fs/read/{path}`，path 以路径段形式拼在 URL 尾部）。
 *
 * V2 直接返回原始字节流（Content-Type 由服务端按扩展名推断），不再是 V1 的
 * `{type,content,encoding,mimeType}` JSON 包装。这里按 Content-Type + 字节嗅探还原为
 * [FileContent]：文本类直接 UTF-8 解码；其余 base64 编码并标 type="binary"。
 * `application/octet-stream` 时把 mimeType 置空，交给调用方按扩展名兜底猜测。
 */
suspend fun StarBurstApi.readFile(conn: ServerConnection, path: String, directory: String? = null): FileContent {
    val encodedPath = path.trimStart('/').encodeURLPath()
    val response = httpClient.get("${conn.baseUrl}/api/fs/read/$encodedPath") {
        conn.authHeader?.let { header("Authorization", it) }
        applyFsLocation(directory)
    }
    if (!response.status.isSuccess()) throw IOException("fs/read failed: HTTP ${response.status.value}")
    val bytes = response.readBytes()
    return bytes.toFileContent(response.headers[HttpHeaders.ContentType])
}

/**
 * 列出目录直接子项（V2 `GET /api/fs/list`），`path` 为相对 [directory] 的子路径。
 *
 * V2 响应为 `{location, data: FileSystemEntry{path,type}[]}`，仅含 path 与 type；
 * [FileNode] 的 name 由 path 末段推导，absolute/size/modified/ignored V2 不返回故留空
 * （调用方已有 `absolute ?: joinDirectory(parent, name)` 兜底）。
 */
suspend fun StarBurstApi.listDirectory(
    conn: ServerConnection,
    path: String = "",
    directory: String? = null,
): List<FileNode> {
    val response = httpClient.get("${conn.baseUrl}/api/fs/list") {
        conn.authHeader?.let { header("Authorization", it) }
        applyFsLocation(directory)
        parameter("path", path)
    }
    if (!response.status.isSuccess()) throw IOException("fs/list failed: HTTP ${response.status.value}")
    return response.body<V2FsResponse>().data.map { entry ->
        val trimmed = entry.path.trimEnd('/')
        FileNode(
            name = trimmed.substringAfterLast('/').ifEmpty { trimmed },
            path = trimmed,
            type = entry.type,
        )
    }
}

// ============ V2 fs 私有辅助 ============

/**
 * 附加 V2 `location` deepObject 参数（`location[directory]`）。真值源 1.18.30 实测：
 * V2 fs 端点只认 deepObject 的 `location[directory]`，顶层 `directory` query 会被忽略。
 * 同时保留 `x-starburst-directory` 头供 starburst-backend 镜像拦截敏感目录。
 */
private fun HttpRequestBuilder.applyFsLocation(directory: String?, workspace: String? = null) {
    directory?.let {
        parameter("location[directory]", it)
        header("x-starburst-directory", it)
    }
    workspace?.let { parameter("location[workspace]", it) }
}

/** 文本类 MIME：Content-Type 命中即按 UTF-8 解码，不再嗅探。 */
private val TEXT_JSON_MIMES: Set<String> = setOf(
    "application/json", "application/xml", "application/javascript",
    "application/x-javascript", "application/xhtml+xml", "image/svg+xml",
)

/** 服务端兜底 MIME，交给调用方按扩展名猜测。 */
private const val GENERIC_MIME = "application/octet-stream"

/** 把 V2 `read` 返回的原始字节还原为 App 的 [FileContent]。 */
private fun ByteArray.toFileContent(contentType: String?): FileContent {
    val mime = contentType?.substringBefore(';')?.trim()?.takeIf { it.isNotBlank() }
    val isText = when {
        mime == null -> sniffText()
        mime.substringBefore('/') == "text" -> true
        mime in TEXT_JSON_MIMES -> true
        else -> false
    }
    val resolvedMime = mime?.takeUnless { it == GENERIC_MIME }
    return if (isText) {
        FileContent(type = "text", content = decodeToString(), mimeType = resolvedMime)
    } else {
        FileContent(
            type = "binary",
            content = Base64.getEncoder().encodeToString(this),
            encoding = "base64",
            mimeType = resolvedMime,
        )
    }
}

/** 无 Content-Type 时的字节嗅探：含 NUL 或过多不可打印字节即视为二进制。 */
private fun ByteArray.sniffText(): Boolean {
    if (isEmpty()) return true
    var printable = 0
    for (byte in this) {
        when (val b = byte.toInt() and 0xFF) {
            0 -> return false
            in 9..13, in 32..126 -> printable++
        }
    }
    return printable * 10 >= size * 9
}

// ============ V2 fs DTO ============

/** V2 `GET /api/fs/find|list` 统一响应：`{location, data: FileSystemEntry[]}`。 */
@Serializable
data class V2FsResponse(val data: List<V2FsEntry> = emptyList())

/**
 * V2 文件系统条目：仅 path（相对 location 目录）与 type（file|directory）；
 * 目录 path 带尾斜杠。
 */
@Serializable
data class V2FsEntry(val path: String = "", val type: String = "")
