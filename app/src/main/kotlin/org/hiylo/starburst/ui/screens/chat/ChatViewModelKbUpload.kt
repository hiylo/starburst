/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatViewModelKbUpload.kt
 * Date : 2026/09/23 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.FileNode
import org.hiylo.starburst.data.api.KbCollection
import org.hiylo.starburst.data.api.listDirectory
import org.hiylo.starburst.data.api.readFile
import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.ui.screens.files.workspaceFileBytes

/** 知识库上传候选：当前项目内可上传的文档类文件。 */
internal data class KbUploadCandidate(
    val path: String,
    val name: String,
    val size: Long? = null,
)

/** 上传前扫描结果：文件列表 + 因读取失败被跳过的子目录数。 */
internal data class KbUploadScan(
    val files: List<KbUploadCandidate> = emptyList(),
    val skippedDirs: Int = 0,
)

/** 单文件上传大小上限（超过即排除）。 */
private const val KB_MAX_UPLOAD_BYTES = 1024 * 1024

/** 递归枚举时跳过的常见重型/构建目录与版本库目录，避免扫描爆炸。 */
private val KB_SKIP_DIRS = setOf(
    ".git", ".github", ".idea", ".gradle", ".kotlin", ".cache", "node_modules",
    "build", "dist", "out", "target", "release", "debug", "vendor", "Pods",
    "venv", ".venv", "__pycache__", ".next", ".nuxt", "coverage", "tmp", ".tmp",
)

/** 视为「文档类」参与上传的扩展名（文本类）。 */
private val KB_UPLOAD_EXTENSIONS = setOf(
    "md", "markdown", "txt", "rst", "log",
    "kt", "kts", "java", "js", "jsx", "mjs", "cjs", "ts", "tsx", "py", "rb", "go", "rs",
    "c", "h", "cpp", "cc", "cxx", "hpp", "cs", "swift", "sh", "bash", "zsh", "fish",
    "json", "jsonl", "xml", "yaml", "yml", "toml", "ini", "conf", "properties", "gradle",
    "sql", "html", "htm", "css", "scss", "sass", "less", "vue", "svelte", "dart", "lua", "php",
)

private val KB_UPLOAD_FILENAMES = setOf(
    "dockerfile", "makefile", ".gitignore", ".gitattributes", ".editorconfig",
)

private fun isKbUploadable(node: FileNode): Boolean {
    if (node.type == "directory") return false
    val name = node.name.lowercase()
    val extension = name.substringAfterLast('.', "")
    return extension in KB_UPLOAD_EXTENSIONS || name in KB_UPLOAD_FILENAMES
}

/**
 * 解析当前 server 的文档后端地址与 token（与文档生成共用的解析逻辑在
 * [ChatViewModelDocuments.resolveDocumentEndpoint] 中是 private，此处提供私有副本）。
 */
private suspend fun ChatViewModel.kbEndpoint(): Pair<String, String> {
    val cachedUrl = _documentBackendUrl.value
    val cachedToken = _documentBackendToken.value
    if (cachedUrl.isNotBlank() && cachedToken.isNotBlank()) return cachedUrl to cachedToken
    val server = serverRepository.getServer(serverId)
    val host = runCatching { java.net.URL(serverUrl).host }.getOrNull()
        ?: serverUrl.substringAfter("://").substringBefore(":")
    val url = (server?.backendResolvedUrl ?: "http://$host:18880").trimEnd('/')
    if (url.isNotBlank()) {
        _documentBackendUrl.value = url
        _documentBackendToken.value = server?.backendResolvedToken.orEmpty()
    }
    return url to server?.backendResolvedToken.orEmpty()
}

/**
 * 扫描当前会话项目目录下的文档类文件（递归子目录，深度 ≤ [limitDepth]），
 * 过大/空文件排除；子目录读取失败时跳过并计入 [KbUploadScan.skippedDirs]。
 */
internal suspend fun ChatViewModel.enumerateKbUploadFiles(limitDepth: Int = 3): KbUploadScan {
    val directory = sessionDirectory ?: return KbUploadScan()
    val files = mutableListOf<KbUploadCandidate>()
    var skippedDirs = 0
    suspend fun walk(path: String, depth: Int) {
        val nodes = try {
            api.listDirectory(shellConn, path = path, directory = directory)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (depth > 0) {
                skippedDirs++
                return
            }
            throw e
        }
        for (node in nodes) {
            if (node.ignored) continue
            if (node.type == "directory") {
                if (depth < limitDepth && node.name !in KB_SKIP_DIRS) walk(node.path, depth + 1)
                continue
            }
            if (!isKbUploadable(node)) continue
            val size = node.size
            if (size != null && (size <= 0L || size > KB_MAX_UPLOAD_BYTES)) continue
            files += KbUploadCandidate(path = node.path, name = node.name, size = size)
        }
    }
    walk(path = "", depth = 0)
    return KbUploadScan(files = files, skippedDirs = skippedDirs)
}

/** 拉取知识库集合列表，供上传弹窗选择目标集合。 */
internal fun ChatViewModel.loadKbCollectionsForUpload(onResult: (List<KbCollection>) -> Unit) {
    viewModelScope.launch {
        val (url, token) = kbEndpoint()
        if (url.isBlank() || token.isBlank()) {
            onResult(emptyList())
            return@launch
        }
        val collections = try {
            kbApi.listCollections(url, token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list KB collections for upload", e)
            emptyList()
        }
        onResult(collections)
    }
}

/** 创建默认上传集合（名称来自字符串资源），返回新集合或 null。 */
internal fun ChatViewModel.createKbUploadCollection(onResult: (KbCollection?) -> Unit) {
    viewModelScope.launch {
        val (url, token) = kbEndpoint()
        if (url.isBlank() || token.isBlank()) {
            onResult(null)
            return@launch
        }
        try {
            val name = context.getString(R.string.chat_kb_upload_default_collection_name)
            onResult(kbApi.createCollection(url, token, name))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create default KB collection", e)
            onResult(null)
        }
    }
}

/**
 * 逐个读取选中文件文本并上传到指定知识库集合（`POST /api/kb/ingest`）。
 *
 * @param collectionId 目标集合 id。
 * @param files 待上传文件。
 * @param onProgress 进度回调（done, total），每个文件处理完调用一次。
 * @param onResult 全部处理完后的回调（全成功标志、成功上传数）。
 */
internal fun ChatViewModel.uploadDocumentsToKb(
    collectionId: Long,
    files: List<KbUploadCandidate>,
    onProgress: (Int, Int) -> Unit,
    onResult: (Boolean, Int) -> Unit,
) {
    if (files.isEmpty()) {
        onResult(false, 0)
        return
    }
    viewModelScope.launch {
        val (url, token) = kbEndpoint()
        if (url.isBlank() || token.isBlank()) {
            onResult(false, 0)
            return@launch
        }
        var done = 0
        var failed = 0
        for (file in files) {
            try {
                val content = api.readFile(shellConn, file.path, sessionDirectory)
                val text = workspaceFileBytes(content)?.toString(Charsets.UTF_8).orEmpty()
                kbApi.ingest(url, token, collectionId, name = file.name, mime = "text/plain", content = text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                Log.e(TAG, "Failed to ingest ${file.name}", e)
            }
            done++
            onProgress(done, files.size)
        }
        onResult(failed == 0, done - failed)
    }
}
