/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatViewModelDocuments.kt
 * Date : 2026/09/22 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.BuildConfig
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.GeneratedDocument
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.io.IOException

/** 从异常 message 中提取面向用户的简短原因（如后端 503 的 error 详情）。 */
private fun Throwable.userReason(): String = message
    ?.substringAfter(": ", "")
    ?.take(100)
    ?: ""

/** 校验生成响应有效（后端 4xx/5xx 会被反序列化成默认空对象，需显式拦截避免产生垃圾卡片）。 */
private fun requireValidGenerated(doc: GeneratedDocument) {
    if (doc.id <= 0L || doc.downloadUrl.isBlank()) {
        throw IOException("invalid generate response (id=${doc.id}, url='${doc.downloadUrl}')")
    }
}

// ============ Document generation ============

/**
 * 解析当前 server 对应的后端文档服务地址与 token（供 /api/documents 使用）。
 * 优先使用 init 阶段解析缓存的地址，缺失时按 opencode 同主机 18880 端口回退推导。
 */
private suspend fun ChatViewModel.resolveDocumentEndpoint(): Pair<String, String> {
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
 * 生成文档（同步请求，`POST /api/documents/generate`）。
 *
 * 成功：把产物加入「本会话生成文档」列表，并往会话发一条用户文本消息
 * （`已生成文档《{name}》（docId={id}，{type}）`）让模型感知产物存在。
 * 失败/后端不可用：通过 [ChatViewModel.documentToast] 弹 Toast。
 *
 * @param type 后端 docType（pptx / docx / xlsx）。
 * @param prompt 内容描述。
 * @param onResult 完成回调（true=成功，false=失败或后端不可用）。
 */
internal fun ChatViewModel.generateDocument(type: String, prompt: String, onResult: (Boolean) -> Unit = {}) {
    if (_isGeneratingDocument.value) return
    val text = prompt.trim()
    if (text.isBlank()) return
    _isGeneratingDocument.value = true
    viewModelScope.launch {
        try {
            val (url, token) = resolveDocumentEndpoint()
            if (url.isBlank() || token.isBlank()) {
                _documentToast.tryEmit(context.getString(R.string.document_backend_unavailable))
                onResult(false)
                return@launch
            }
            val doc = documentsApi.generate(url, token, type, text)
            requireValidGenerated(doc)
            _generatedDocuments.value = _generatedDocuments.value.filterNot { it.id == doc.id } + doc
            val notice = context.getString(
                R.string.document_generated_notice,
                doc.name,
                doc.id.toString(),
                type,
            )
            sendMessage(notice)
            if (BuildConfig.DEBUG) Log.d(TAG, "Generated document ${doc.id} (${doc.name}, $type)")
            onResult(true)
        } catch (e: Exception) {
            e.rethrowCancellation()
            Log.e(TAG, "Failed to generate document", e)
            _documentToast.tryEmit(
                context.getString(R.string.document_generate_failed_detail, e.userReason()),
            )
            onResult(false)
        } finally {
            _isGeneratingDocument.value = false
        }
    }
}

/**
 * 按意见重新生成文档（`POST /api/documents/regenerate`）。
 *
 * 成功：刷新对应产物卡片并往会话发一条用户文本消息；失败/后端不可用：弹 Toast。
 *
 * @param docId 原文档 id。
 * @param instruction 修改意见。
 * @param onResult 完成回调（true=成功，false=失败或后端不可用）。
 */
internal fun ChatViewModel.reviseDocument(docId: Long, instruction: String, onResult: (Boolean) -> Unit = {}) {
    if (_isRevisingDocument.value) return
    val text = instruction.trim()
    if (text.isBlank()) return
    _isRevisingDocument.value = true
    viewModelScope.launch {
        try {
            val (url, token) = resolveDocumentEndpoint()
            if (url.isBlank() || token.isBlank()) {
                _documentToast.tryEmit(context.getString(R.string.document_backend_unavailable))
                onResult(false)
                return@launch
            }
            val doc = documentsApi.regenerate(url, token, docId, text, sessionId)
            requireValidGenerated(doc)
            _generatedDocuments.value = _generatedDocuments.value.filterNot { it.id == doc.id } + doc
            val notice = context.getString(
                R.string.document_regenerated_notice,
                doc.name,
                doc.id.toString(),
                doc.docType,
            )
            sendMessage(notice)
            if (BuildConfig.DEBUG) Log.d(TAG, "Regenerated document ${doc.id} (${doc.name})")
            onResult(true)
        } catch (e: Exception) {
            e.rethrowCancellation()
            Log.e(TAG, "Failed to revise document $docId", e)
            _documentToast.tryEmit(
                context.getString(R.string.document_revise_failed_detail, e.userReason()),
            )
            onResult(false)
        } finally {
            _isRevisingDocument.value = false
        }
    }
}

/**
 * 下载文档原始字节（`GET /api/documents/{id}/download`，由 UI 写入手持文件）。
 * 失败或后端不可用时返回 null。
 */
internal suspend fun ChatViewModel.fetchDocumentBytes(document: GeneratedDocument): ByteArray? {
    val (url, token) = resolveDocumentEndpoint()
    if (url.isBlank() || token.isBlank()) return null
    return try {
        documentsApi.downloadDocument(url, token, document.downloadUrl)
    } catch (e: Exception) {
        e.rethrowCancellation()
        Log.e(TAG, "Failed to download document ${document.id}", e)
        null
    }
}

/** 从本会话的生成文档列表移除一张卡片（仅本地隐藏，不删除后端记录）。 */
internal fun ChatViewModel.removeGeneratedDocument(id: Long) {
    _generatedDocuments.value = _generatedDocuments.value.filterNot { it.id == id }
}
