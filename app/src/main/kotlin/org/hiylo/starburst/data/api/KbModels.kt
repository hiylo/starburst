/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : KbModels.kt
 * Date : 2026/09/22 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import kotlinx.serialization.Serializable

/** 知识库集合（后端 /api/kb/collections）。 */
@Serializable
data class KbCollection(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val documentCount: Long = 0,
    val chunkCount: Long = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)

/** 已入库的知识库文档（status：pending | indexed | failed）。 */
@Serializable
data class KbDocument(
    val id: Long = 0,
    val collectionId: Long = 0,
    val name: String = "",
    val mime: String = "",
    val sizeBytes: Long = 0,
    val status: String = "",
    val chunkCount: Long = 0,
    val error: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

/** 向量检索命中片段（后端 /api/kb/search）。 */
@Serializable
data class KbSearchResult(
    val source: String = "",
    val section: String = "",
    val content: String = "",
    val score: Double = 0.0,
)

/** KB 文档的一个切片（摄入时切分，`GET /api/kb/documents/{id}/chunks` 返回）。 */
@Serializable
data class KbChunk(
    val id: Long = 0,
    val seq: Int = 0,
    val title: String = "",
    val content: String = "",
    val createdAt: String = "",
)

/** `GET /api/kb/documents/{id}/chunks` 的响应包装。 */
@Serializable
internal data class KbChunksResponse(
    val documentId: Long = 0,
    val chunks: List<KbChunk> = emptyList(),
)

/** 已生成的文档（后端 /api/documents，docType：xlsx | docx | pptx）。 */
@Serializable
data class GeneratedDocument(
    val id: Long = 0,
    val name: String = "",
    val docType: String = "",
    val downloadUrl: String = "",
)

/** `GET /api/kb/collections` 的响应包装。 */
@Serializable
internal data class KbCollectionsResponse(val collections: List<KbCollection> = emptyList())

/** `POST /api/kb/collections` 的请求体。 */
@Serializable
internal data class KbCreateCollectionRequest(
    val name: String,
    val description: String = "",
)

/** `GET /api/kb/documents` 的响应包装。 */
@Serializable
internal data class KbDocumentsResponse(val documents: List<KbDocument> = emptyList())

/** `POST /api/kb/ingest` 的请求体（content 与 contentBase64 二选一）。 */
@Serializable
internal data class KbIngestRequest(
    val collectionId: Long,
    val name: String,
    val mime: String? = null,
    val content: String? = null,
    val contentBase64: String? = null,
)

/** `POST /api/kb/ingest` 的响应体（document + 分块数）。 */
@Serializable
data class KbIngestResponse(
    val document: KbDocument = KbDocument(),
    val chunks: Int = 0,
)

/** `POST /api/kb/search` 的请求体。 */
@Serializable
internal data class KbSearchRequest(
    val query: String,
    val collectionIds: List<Long>? = null,
    val topK: Int? = null,
    val minScore: Double? = null,
)

/** `POST /api/kb/search` 的响应包装。 */
@Serializable
internal data class KbSearchResponse(val results: List<KbSearchResult> = emptyList())

/** `GET /api/documents` 的响应包装。 */
@Serializable
internal data class GeneratedDocumentsResponse(val documents: List<GeneratedDocument> = emptyList())

/** `POST /api/documents/generate` 的请求体。 */
@Serializable
internal data class DocumentGenerateRequest(
    val type: String,
    val prompt: String,
)

/** `POST /api/documents/regenerate` 的请求体。 */
@Serializable
internal data class DocumentRegenerateRequest(
    val docId: Long,
    val instruction: String? = null,
    val sessionId: String? = null,
)

/** `DELETE /api/kb` 与 `DELETE /api/documents` 系列端点的统一成功响应。 */
@Serializable
internal data class BackendOkResponse(val ok: Boolean = false)