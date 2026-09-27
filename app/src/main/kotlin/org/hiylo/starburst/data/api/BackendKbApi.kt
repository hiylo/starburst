/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BackendKbApi.kt
 * Date : 2026/09/22 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.io.IOException
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * starburst-backend v2.1.0「知识库」接口的 HTTP 客户端，对接 `/api/kb/` 全部端点。
 *
 * 认证与 [BackendApi] 一致：`Authorization: Bearer <token>`；所有端点均以
 * 「后端地址 + token」为参数，不依赖 ServerConnection。
 *
 * @author Hsi Chu
 * @since 3.1.0
 */
@Singleton
class BackendKbApi @Inject constructor(
    private val httpClient: HttpClient,
) {
    /** 新建知识库集合（`POST /api/kb/collections`），返回持久化后的集合对象。 */
    suspend fun createCollection(
        backendUrl: String,
        token: String,
        name: String,
        description: String = "",
    ): KbCollection {
        val resp = httpClient.post("${backendUrl.trimEnd('/')}/api/kb/collections") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(KbCreateCollectionRequest(name, description))
        }
        resp.requireSuccess("kb create collection failed")
        return resp.body()
    }

    /** 列出全部知识库集合（`GET /api/kb/collections`）。 */
    suspend fun listCollections(backendUrl: String, token: String): List<KbCollection> {
        val resp = httpClient.get("${backendUrl.trimEnd('/')}/api/kb/collections") {
            header("Authorization", "Bearer $token")
        }
        resp.requireSuccess("kb list collections failed")
        return resp.body<KbCollectionsResponse>().collections
    }

    /**
     * 更新知识库集合（`PATCH /api/kb/collections/{id}`）。
     * name/description 二者至少一个非空；null 字段不写入请求体，未传字段保持原值。
     */
    suspend fun updateCollection(
        backendUrl: String,
        token: String,
        id: Long,
        name: String? = null,
        description: String? = null,
    ): KbCollection {
        val resp = httpClient.patch("${backendUrl.trimEnd('/')}/api/kb/collections/$id") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(KbUpdateCollectionRequest(name, description))
        }
        resp.requireSuccess("kb update collection failed")
        return resp.body()
    }

    /**
     * 删除知识库集合（`DELETE /api/kb/collections/{id}`），级联删除文档与分块。
     */
    suspend fun deleteCollection(backendUrl: String, token: String, id: Long): Boolean {
        val resp: HttpResponse = httpClient.delete("${backendUrl.trimEnd('/')}/api/kb/collections/$id") {
            header("Authorization", "Bearer $token")
        }
        return resp.status.value in 200..299
    }

    /**
     * 摄入文档（`POST /api/kb/ingest`）：文本/markdown 走 [content]，
     * 二进制文件 base64 编码后走 [contentBase64]（二者二选一）。
     * 服务端同步分块 + 向量化，耗时可能较长，给予 5 分钟超时。
     */
    suspend fun ingest(
        backendUrl: String,
        token: String,
        collectionId: Long,
        name: String,
        mime: String? = null,
        content: String? = null,
        contentBase64: String? = null,
    ): KbIngestResponse {
        val resp = httpClient.post("${backendUrl.trimEnd('/')}/api/kb/ingest") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(KbIngestRequest(collectionId, name, mime, content, contentBase64))
            timeout { requestTimeoutMillis = 300_000L }
        }
        // 必须显式判定状态码：KbIngestResponse 全字段有默认值，服务端 4xx/5xx 的
        // {"error":"..."} 会被 ignoreUnknownKeys 丢弃、coerceInputValues 补默认值，
        // 于是反序列化「成功」返回空 document——上传实际失败却提示成功，列表自然看不到。
        resp.requireSuccess("kb ingest failed")
        return resp.body()
    }

    /** 列出某集合下的文档（`GET /api/kb/documents?collectionId=&limit=&offset=`，分页）。 */
    suspend fun listDocuments(
        backendUrl: String,
        token: String,
        collectionId: Long,
        limit: Int = 50,
        offset: Int = 0,
    ): List<KbDocument> {
        val resp = httpClient.get("${backendUrl.trimEnd('/')}/api/kb/documents") {
            header("Authorization", "Bearer $token")
            parameter("collectionId", collectionId)
            parameter("limit", limit)
            parameter("offset", offset)
        }
        resp.requireSuccess("kb list documents failed")
        return resp.body<KbDocumentsResponse>().documents
    }

    /** 获取 KB 文档的已摄入切片（`GET /api/kb/documents/{id}/chunks`），用于查看实际内容。 */
    suspend fun getDocumentChunks(backendUrl: String, token: String, id: Long): List<KbChunk> {
        val resp = httpClient.get("${backendUrl.trimEnd('/')}/api/kb/documents/$id/chunks") {
            header("Authorization", "Bearer $token")
        }
        resp.requireSuccess("kb get document chunks failed")
        return resp.body<KbChunksResponse>().chunks
    }

    /** 下载 KB 文档的原始文件字节（`GET /api/kb/documents/{id}/file`）；非 2xx 抛 IOException。 */
    suspend fun downloadOriginalFile(backendUrl: String, token: String, id: Long): ByteArray {
        val resp = httpClient.get("${backendUrl.trimEnd('/')}/api/kb/documents/$id/file") {
            header("Authorization", "Bearer $token")
        }
        if (resp.status.value !in 200..299) {
            throw IOException("download original file failed (HTTP ${resp.status.value})")
        }
        return resp.body<ByteArray>()
    }

    /** 获取 RAG-in-Prompt 使用统计（`GET /api/kb/stats`）。 */
    suspend fun kbStats(backendUrl: String, token: String): KbRagStats {
        val resp = httpClient.get("${backendUrl.trimEnd('/')}/api/kb/stats") {
            header("Authorization", "Bearer $token")
        }
        resp.requireSuccess("kb stats failed")
        return resp.body<KbStatsResponse>().rag
    }

    /** 删除知识库文档（`DELETE /api/kb/documents/{id}`）。 */
    suspend fun deleteDocument(backendUrl: String, token: String, id: Long): Boolean {
        val resp: HttpResponse = httpClient.delete("${backendUrl.trimEnd('/')}/api/kb/documents/$id") {
            header("Authorization", "Bearer $token")
        }
        return resp.status.value in 200..299
    }

    /**
     * 向量检索（`POST /api/kb/search`）。
     * [collectionIds] 为空表示全库检索；[topK] 与 [minScore] 不传用后端默认值。
     */
    suspend fun search(
        backendUrl: String,
        token: String,
        query: String,
        collectionIds: List<Long>? = null,
        topK: Int? = null,
        minScore: Double? = null,
    ): List<KbSearchResult> {
        val resp = httpClient.post("${backendUrl.trimEnd('/')}/api/kb/search") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(KbSearchRequest(query, collectionIds, topK, minScore))
        }
        resp.requireSuccess("kb search failed")
        return resp.body<KbSearchResponse>().results
    }
}

/**
 * 非 2xx 时抛出含后端 error 详情的 [IOException]。
 *
 * 本项目共享 HttpClient 未开 `expectSuccess`（见 `NetworkModule.provideJson`），
 * 且 KB 响应模型字段均带默认值，因此必须显式判定状态码，否则 4xx/5xx 会被
 * 当成「空但合法」的响应体吞掉。
 */
private suspend fun HttpResponse.requireSuccess(context: String) {
    if (status.value in 200..299) return
    val body = runCatching { bodyAsText() }.getOrNull().orEmpty()
    val detail = Regex("\"error\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
        ?: body.take(120)
    throw IOException("$context (HTTP ${status.value}): $detail")
}

/** `PATCH /api/kb/collections/{id}` 的请求体；null 字段不序列化（未传字段保持原值）。 */
@Serializable
internal data class KbUpdateCollectionRequest(
    val name: String? = null,
    val description: String? = null,
)