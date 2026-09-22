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
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
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
    ): KbCollection = httpClient.post("${backendUrl.trimEnd('/')}/api/kb/collections") {
        header("Authorization", "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(KbCreateCollectionRequest(name, description))
    }.body()

    /** 列出全部知识库集合（`GET /api/kb/collections`）。 */
    suspend fun listCollections(backendUrl: String, token: String): List<KbCollection> {
        val resp: KbCollectionsResponse = httpClient.get("${backendUrl.trimEnd('/')}/api/kb/collections") {
            header("Authorization", "Bearer $token")
        }.body()
        return resp.collections
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
    ): KbIngestResponse = httpClient.post("${backendUrl.trimEnd('/')}/api/kb/ingest") {
        header("Authorization", "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(KbIngestRequest(collectionId, name, mime, content, contentBase64))
        timeout { requestTimeoutMillis = 300_000L }
    }.body()

    /** 列出某集合下的文档（`GET /api/kb/documents?collectionId=&limit=`）。 */
    suspend fun listDocuments(
        backendUrl: String,
        token: String,
        collectionId: Long,
        limit: Int = 50,
    ): List<KbDocument> {
        val resp: KbDocumentsResponse = httpClient.get("${backendUrl.trimEnd('/')}/api/kb/documents") {
            header("Authorization", "Bearer $token")
            parameter("collectionId", collectionId)
            parameter("limit", limit)
        }.body()
        return resp.documents
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
        val resp: KbSearchResponse = httpClient.post("${backendUrl.trimEnd('/')}/api/kb/search") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(KbSearchRequest(query, collectionIds, topK, minScore))
        }.body()
        return resp.results
    }
}