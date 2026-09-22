/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : KbCollectionDetailViewModel.kt
 * Date : 2026/09/22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.kb

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.BackendKbApi
import org.hiylo.starburst.data.api.KbCollection
import org.hiylo.starburst.data.api.KbDocument
import org.hiylo.starburst.data.api.KbSearchResult
import org.hiylo.starburst.data.repository.ServerRepository
import java.io.ByteArrayOutputStream
import java.util.Locale
import javax.inject.Inject

/** 知识库集合详情页的 UI 状态。 */
data class KbCollectionDetailUiState(
    val collection: KbCollection? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val documents: List<KbDocument> = emptyList(),
    val ingesting: Boolean = false,
    val ingestError: String? = null,
    val deletingId: Long? = null,
    val query: String = "",
    val searching: Boolean = false,
    val results: List<KbSearchResult> = emptyList(),
    val searchError: String? = null,
)

/** 文件大小超限异常：携带实际大小与上限，用于给出明确提示。 */
private class KbFileTooLargeException(
    val actualBytes: Long,
    val limitBytes: Long,
) : Exception()

/**
 * 知识库：集合详情 ViewModel。
 *
 * 从路由参数读取 serverId/collectionId → 解析后端地址与 token →
 * 加载集合元信息与文档列表，并承载文本/文件摄入（`POST /api/kb/ingest`）
 * 与集合内向量检索（`POST /api/kb/search`）。
 *
 * @author Hsi Chu
 * @since 3.1.0
 */
@HiltViewModel
class KbCollectionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val kbApi: BackendKbApi,
    private val serverRepository: ServerRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val serverId = savedStateHandle.get<String>("serverId").orEmpty()
    private val collectionId: Long = savedStateHandle.get<String>("collectionId")?.toLongOrNull() ?: 0L

    private var backendUrl = ""
    private var backendToken = ""

    private val _uiState = MutableStateFlow(KbCollectionDetailUiState())
    val uiState: StateFlow<KbCollectionDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val server = serverRepository.getServer(serverId)
            backendUrl = server?.backendResolvedUrl.orEmpty()
            backendToken = server?.backendResolvedToken.orEmpty()
            loadCollection()
            loadDocuments()
        }
    }

    /** 从集合列表里解析当前集合元信息（名称/描述/计数）用于页面标题。 */
    private suspend fun loadCollection() {
        if (backendUrl.isBlank() || backendToken.isBlank()) return
        try {
            val match = kbApi.listCollections(backendUrl, backendToken).firstOrNull { it.id == collectionId }
            _uiState.update { it.copy(collection = match) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.message ?: context.getString(R.string.kb_load_error)) }
        }
    }

    /** 加载集合下的文档列表。 */
    fun loadDocuments() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            if (backendUrl.isBlank() || backendToken.isBlank()) {
                _uiState.update {
                    it.copy(loading = false, error = context.getString(R.string.kb_backend_error))
                }
                return@launch
            }
            try {
                val documents = kbApi.listDocuments(backendUrl, backendToken, collectionId)
                _uiState.update { it.copy(loading = false, error = null, documents = documents) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: context.getString(R.string.kb_documents_load_failed),
                    )
                }
            }
        }
    }

    /** 摄入纯文本 / Markdown 内容（走 `content` 字段）。 */
    fun ingestText(name: String, content: String, onResult: (Boolean) -> Unit = {}) {
        val trimmedName = name.trim()
        val trimmedContent = content.trim()
        if (trimmedName.isEmpty() || trimmedContent.isEmpty() || _uiState.value.ingesting) return
        viewModelScope.launch {
            _uiState.update { it.copy(ingesting = true, ingestError = null) }
            try {
                kbApi.ingest(
                    backendUrl = backendUrl,
                    token = backendToken,
                    collectionId = collectionId,
                    name = trimmedName,
                    content = trimmedContent,
                )
                _uiState.update { it.copy(ingesting = false) }
                onResult(true)
                loadDocuments()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(ingesting = false, ingestError = context.getString(R.string.kb_ingest_failed))
                }
                onResult(false)
            }
        }
    }

    /** 摄入文件：文本类走 `content` 字段，二进制（PDF/Office）base64 后走 `contentBase64`。 */
    fun ingestFile(name: String, uri: Uri, mime: String, onResult: (Boolean) -> Unit = {}) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || _uiState.value.ingesting) return
        viewModelScope.launch {
            _uiState.update { it.copy(ingesting = true, ingestError = null) }
            try {
                if (isTextMime(mime)) {
                    val text = withContext(Dispatchers.IO) { readTextFile(uri) }
                    if (text.isNullOrBlank()) {
                        _uiState.update {
                            it.copy(ingesting = false, ingestError = context.getString(R.string.kb_ingest_failed))
                        }
                        onResult(false)
                        return@launch
                    }
                    kbApi.ingest(
                        backendUrl = backendUrl,
                        token = backendToken,
                        collectionId = collectionId,
                        name = trimmedName,
                        mime = mime.ifBlank { null },
                        content = text,
                    )
                } else {
                    val contentBase64 = withContext(Dispatchers.IO) { readBinaryFileBase64(uri) }
                    if (contentBase64.isNullOrBlank()) {
                        _uiState.update {
                            it.copy(ingesting = false, ingestError = context.getString(R.string.kb_ingest_failed))
                        }
                        onResult(false)
                        return@launch
                    }
                    kbApi.ingest(
                        backendUrl = backendUrl,
                        token = backendToken,
                        collectionId = collectionId,
                        name = trimmedName,
                        mime = mime.ifBlank { null },
                        contentBase64 = contentBase64,
                    )
                }
                _uiState.update { it.copy(ingesting = false) }
                onResult(true)
                loadDocuments()
            } catch (e: CancellationException) {
                throw e
            } catch (e: KbFileTooLargeException) {
                _uiState.update {
                    it.copy(
                        ingesting = false,
                        ingestError = context.getString(
                            R.string.kb_file_too_large,
                            formatBytes(e.actualBytes),
                            formatBytes(e.limitBytes),
                        ),
                    )
                }
                onResult(false)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(ingesting = false, ingestError = context.getString(R.string.kb_ingest_failed))
                }
                onResult(false)
            }
        }
    }

    /** 删除文档（`DELETE /api/kb/documents/{id}`）；成功后刷新文档列表。 */
    fun deleteDocument(id: Long, onResult: (Boolean) -> Unit = {}) {
        if (_uiState.value.deletingId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(deletingId = id) }
            try {
                kbApi.deleteDocument(backendUrl, backendToken, id)
                _uiState.update { it.copy(deletingId = null) }
                onResult(true)
                loadDocuments()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(deletingId = null) }
                Toast.makeText(context, R.string.kb_delete_document_failed, Toast.LENGTH_SHORT).show()
                onResult(false)
            }
        }
    }

    /** 读取 content Uri 的文本内容；失败返回 null，超限抛 [KbFileTooLargeException]。 */
    private fun readTextFile(uri: Uri): String? {
        if (uri.scheme != "content") return null
        // 先查元数据大小预检，超大文件直接拒绝，避免全量读入内存。
        contentSize(uri)?.let { size ->
            if (size > KB_FILE_SIZE_LIMIT_BYTES) throw KbFileTooLargeException(size, KB_FILE_SIZE_LIMIT_BYTES)
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > KB_FILE_SIZE_LIMIT_BYTES) {
                        throw KbFileTooLargeException(total.toLong(), KB_FILE_SIZE_LIMIT_BYTES)
                    }
                    output.write(buffer, 0, read)
                }
                String(output.toByteArray(), Charsets.UTF_8)
            }
        } catch (e: KbFileTooLargeException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** 读取 content Uri 的二进制并 base64（NO_WRAP）；失败返回 null，超限抛异常。 */
    private fun readBinaryFileBase64(uri: Uri): String? {
        if (uri.scheme != "content") return null
        contentSize(uri)?.let { size ->
            if (size > KB_BINARY_SIZE_LIMIT_BYTES) throw KbFileTooLargeException(size, KB_BINARY_SIZE_LIMIT_BYTES)
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > KB_BINARY_SIZE_LIMIT_BYTES) {
                        throw KbFileTooLargeException(total.toLong(), KB_BINARY_SIZE_LIMIT_BYTES)
                    }
                    output.write(buffer, 0, read)
                }
                Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            }
        } catch (e: KbFileTooLargeException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** 查询 content Uri 的文件大小（字节）；无法获取时返回 null。 */
    private fun contentSize(uri: Uri): Long? = runCatching {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?: return@runCatching null
        cursor.use {
            if (!it.moveToFirst()) {
                null
            } else {
                val index = it.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !it.isNull(index)) it.getLong(index) else null
            }
        }
    }.getOrNull()

    /** 判断 MIME 是否走文本 `content` 摄入路径（其余视为二进制 base64 摄入）。 */
    private fun isTextMime(mime: String): Boolean =
        mime.startsWith("text/") || mime in TEXT_LIKE_MIMES

    /** 字节数转可读文本（MiB/KiB/B），用于超限提示。 */
    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MiB", bytes / 1024.0 / 1024.0)
        bytes >= 1024L -> String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0)
        else -> "$bytes B"
    }

    /** 更新搜索输入框内容。 */
    fun setQuery(text: String) {
        _uiState.update { it.copy(query = text) }
    }

    /** 在集合内执行向量检索（`POST /api/kb/search`）。 */
    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty() || _uiState.value.searching) return
        viewModelScope.launch {
            _uiState.update { it.copy(searching = true, searchError = null, results = emptyList()) }
            if (backendUrl.isBlank() || backendToken.isBlank()) {
                _uiState.update {
                    it.copy(searching = false, searchError = context.getString(R.string.kb_backend_error))
                }
                return@launch
            }
            try {
                val results = kbApi.search(
                    backendUrl = backendUrl,
                    token = backendToken,
                    query = query,
                    collectionIds = listOf(collectionId),
                )
                _uiState.update { it.copy(searching = false, results = results) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(searching = false, searchError = e.message ?: context.getString(R.string.kb_search_failed))
                }
            }
        }
    }

    /** 关闭搜索并清空输入与结果。 */
    fun clearSearch() {
        _uiState.update { it.copy(query = "", results = emptyList(), searchError = null, searching = false) }
    }

    private companion object {
        /** 文本/JSON 类文件摄入大小上限（字节）；对齐后端 JSON body 4 MiB 限制。 */
        const val KB_FILE_SIZE_LIMIT_BYTES = 4L * 1024 * 1024

        /** 二进制文件摄入大小上限（字节）；base64 后约膨胀 4/3，限 3 MiB 防 413。 */
        const val KB_BINARY_SIZE_LIMIT_BYTES = 3L * 1024 * 1024

        /** 走 `content` 字段的文本类 MIME。 */
        val TEXT_LIKE_MIMES = setOf("application/json", "application/xml")
    }
}
