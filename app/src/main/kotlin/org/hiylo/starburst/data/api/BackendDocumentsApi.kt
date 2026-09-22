/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BackendDocumentsApi.kt
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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.io.IOException
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * starburst-backend v2.1.0「文档生成」接口的 HTTP 客户端，对接 `/api/documents/` 全部端点。
 *
 * 认证与 [BackendApi] 一致：`Authorization: Bearer <token>`；所有端点均以
 * 「后端地址 + token」为参数，不依赖 ServerConnection。
 *
 * @author Hsi Chu
 * @since 3.1.0
 */
@Singleton
class BackendDocumentsApi @Inject constructor(
    private val httpClient: HttpClient,
) {
    /**
     * 生成文档（`POST /api/documents/generate`），[type] 取 xlsx / docx / pptx。
     * 服务端同步走 LLM 骨架 + 渲染，给予 2 分钟超时。
     */
    suspend fun generate(
        backendUrl: String,
        token: String,
        type: String,
        prompt: String,
    ): GeneratedDocument {
        val resp = httpClient.post("${backendUrl.trimEnd('/')}/api/documents/generate") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(DocumentGenerateRequest(type, prompt))
            timeout { requestTimeoutMillis = 120_000L }
        }
        resp.requireSuccess("document generate failed")
        return resp.body()
    }

    /**
     * 基于既有文档重新生成（`POST /api/documents/regenerate`），响应与 generate 同构。
     * [instruction] 为可选修改意见，[sessionId] 为可选的来源会话。
     */
    suspend fun regenerate(
        backendUrl: String,
        token: String,
        docId: Long,
        instruction: String? = null,
        sessionId: String? = null,
    ): GeneratedDocument {
        val resp = httpClient.post("${backendUrl.trimEnd('/')}/api/documents/regenerate") {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(DocumentRegenerateRequest(docId, instruction, sessionId))
            timeout { requestTimeoutMillis = 120_000L }
        }
        resp.requireSuccess("document regenerate failed")
        return resp.body()
    }

    /** 非 2xx 时抛出含后端 error 详情的 [IOException]（本项目 HttpClient 未开 expectSuccess，需显式判定）。 */
    private suspend fun HttpResponse.requireSuccess(context: String) {
        if (status.value in 200..299) return
        val body = runCatching { bodyAsText() }.getOrNull().orEmpty()
        val detail = Regex("\"error\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
            ?: body.take(120)
        throw IOException("$context: $detail")
    }

    /** 列出已生成的文档（`GET /api/documents?limit=`），最新在前。 */
    suspend fun listDocuments(backendUrl: String, token: String, limit: Int = 50): List<GeneratedDocument> {
        val resp: GeneratedDocumentsResponse = httpClient.get("${backendUrl.trimEnd('/')}/api/documents") {
            header("Authorization", "Bearer $token")
            parameter("limit", limit)
        }.body()
        return resp.documents
    }

    /** 查询单个生成文档详情（`GET /api/documents/{id}`）。 */
    suspend fun getDocument(backendUrl: String, token: String, id: Long): GeneratedDocument =
        httpClient.get("${backendUrl.trimEnd('/')}/api/documents/$id") {
            header("Authorization", "Bearer $token")
        }.body()

    /** 删除生成文档（`DELETE /api/documents/{id}`），连同磁盘文件一起删除。 */
    suspend fun deleteDocument(backendUrl: String, token: String, id: Long): Boolean {
        val resp: HttpResponse = httpClient.delete("${backendUrl.trimEnd('/')}/api/documents/$id") {
            header("Authorization", "Bearer $token")
        }
        return resp.status.value in 200..299
    }

    /** 按 id 下载生成文档的原始字节（`GET /api/documents/{id}/download`）。 */
    suspend fun downloadDocument(backendUrl: String, token: String, id: Long): ByteArray =
        httpClient.get("${backendUrl.trimEnd('/')}/api/documents/$id/download") {
            header("Authorization", "Bearer $token")
        }.body()

    /** 按 [GeneratedDocument.downloadUrl]（相对路径或绝对地址）下载文档字节。 */
    suspend fun downloadDocument(backendUrl: String, token: String, downloadUrl: String): ByteArray =
        httpClient.get(resolveDocumentUrl(backendUrl, downloadUrl)) {
            header("Authorization", "Bearer $token")
        }.body()
}

/**
 * 把后端返回的下载地址解析为完整 URL：已是绝对地址且与 [backendUrl] 同源时原样返回，
 * 相对路径（如 `/api/documents/5/download`）拼接上 [backendUrl]。
 * 绝对地址若 scheme/host/port 与 [backendUrl] 不一致直接抛异常，避免把 Bearer token 发给第三方。
 */
internal fun resolveDocumentUrl(backendUrl: String, downloadUrl: String): String {
    val trimmed = downloadUrl.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        require(isSameOrigin(backendUrl, trimmed)) {
            "Refusing cross-origin document URL: $trimmed"
        }
        return trimmed
    }
    return "${backendUrl.trimEnd('/')}/${trimmed.trimStart('/')}"
}

/** 判断两个 URL 是否同源（scheme、host、port 完全一致）；解析失败视为不同源。 */
internal fun isSameOrigin(left: String, right: String): Boolean = runCatching {
    val a = URL(left)
    val b = URL(right)
    a.protocol == b.protocol && a.host == b.host && a.port == b.port
}.getOrDefault(false)
