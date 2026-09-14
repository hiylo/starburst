/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : OpenCodeApiMessages.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import org.hiylo.starburst.domain.model.MessageWithParts
import org.hiylo.starburst.logging.AppLogger as Log
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi

// ============ Messages ============

suspend fun OpenCodeApi.listMessages(
    conn: ServerConnection,
    sessionId: String,
    limit: Int? = null,
    directory: String? = null,
): List<MessageWithParts> {
    return listMessagesPage(conn, sessionId, limit, directory = directory).messages
}

@OptIn(ExperimentalSerializationApi::class)
suspend fun OpenCodeApi.listMessagesPage(
    conn: ServerConnection,
    sessionId: String,
    limit: Int? = null,
    before: String? = null,
    directory: String? = null,
): MessagePage {
    val maxResponseBytes = settingsRepository.messageHistoryResponseLimitMb.first() * OpenCodeApi.BYTES_PER_MEGABYTE
    return listMessagesPage(conn, sessionId, limit, before, directory, maxResponseBytes)
}

/** Returns messages as raw JSON string (for export without re-serialization). */
suspend fun OpenCodeApi.listMessagesRaw(conn: ServerConnection, sessionId: String): String {
    return httpClient.get("${conn.baseUrl}/session/$sessionId/message") {
        conn.authHeader?.let { header("Authorization", it) }
    }.bodyAsText()
}

/**
 * Stream session export JSON directly to an OutputStream.
 * Writes: {"info":<session>,"messages":<messages>}
 * Uses raw OkHttp for the messages request to enable true streaming
 * (Ktor's ContentNegotiation plugin buffers the entire response).
 * @param onProgress called with bytes written so far
 */
suspend fun OpenCodeApi.exportSessionToStream(
    conn: ServerConnection,
    sessionId: String,
    outputStream: java.io.OutputStream,
    onProgress: (Long) -> Unit = {}
) {
    var bytesWritten = 0L
    // Write session info (small, safe to hold in memory)
    val sessionJson = httpClient.get("${conn.baseUrl}/session/$sessionId") {
        conn.authHeader?.let { header("Authorization", it) }
    }.bodyAsText()
    val header = """{"info":$sessionJson,"messages":"""
    outputStream.write(header.toByteArray())
    bytesWritten += header.toByteArray().size
    outputStream.flush()
    onProgress(bytesWritten)

    // Stream messages via raw OkHttp to get true byte-level streaming
    val okClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    val request = okhttp3.Request.Builder()
        .url("${conn.baseUrl}/session/$sessionId/message")
        .apply { conn.authHeader?.let { addHeader("Authorization", it) } }
        .build()

    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        okClient.newCall(request).execute().use { response ->
            val body = response.body ?: throw java.io.IOException("Empty response body")
            val source = body.source()
            val buffer = ByteArray(8192)
            while (true) {
                val read = source.read(buffer)
                if (read == -1) break
                outputStream.write(buffer, 0, read)
                bytesWritten += read
                onProgress(bytesWritten)
            }
        }
    }

    outputStream.write("}".toByteArray())
    bytesWritten += 1
    outputStream.flush()
    onProgress(bytesWritten)
}

suspend fun OpenCodeApi.getMessage(conn: ServerConnection, sessionId: String, messageId: String): MessageWithParts {
    return httpClient.get("${conn.baseUrl}/session/$sessionId/message/$messageId") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
}

/**
 * Send a prompt asynchronously (fire-and-forget).
 * Returns 204 No Content immediately.
 * @param directory The session's working directory, sent as x-starburst-directory header
 *                  so the server resolves the correct project context.
 */
suspend fun OpenCodeApi.promptAsync(
    conn: ServerConnection,
    sessionId: String,
    messageId: String,
    parts: List<PromptPart>,
    model: ModelSelection? = null,
    agent: String? = null,
    variant: String? = null,
    directory: String? = null,
    system: String? = null,
    tools: Map<String, Boolean>? = null
) {
    val textPreview = parts.asSequence()
        .filter { it.type == "text" }
        .joinToString(" ") { it.text.orEmpty() }
        .trim()
        .take(120)
    val textBytes = parts.sumOf { it.text?.length ?: 0 }
    val startedAt = System.currentTimeMillis()
    try {
        val response = httpClient.post("${conn.baseUrl}/session/$sessionId/prompt_async") {
            conn.authHeader?.let { header("Authorization", it) }
            directory?.let { header("x-starburst-directory", it) }
            contentType(ContentType.Application.Json)
            setBody(PromptRequest(
                messageId = messageId,
                parts = parts,
                model = model,
                agent = agent,
                variant = variant,
                system = system,
                tools = tools
            ))
        }
        val elapsedMs = System.currentTimeMillis() - startedAt
        Log.i(
            "PromptAsyncTrace",
            "send sid=$sessionId mid=$messageId dir=${directory ?: "-"} " +
                "types=${parts.joinToString(",") { it.type }} bytes=$textBytes http=${response.status.value} " +
                "elapsed=${elapsedMs}ms text='$textPreview'",
        )
        if (!response.status.isSuccess()) {
            throw RuntimeException("prompt_async failed: ${response.status}")
        }
    } catch (error: Exception) {
        val elapsedMs = System.currentTimeMillis() - startedAt
        Log.e(
            "PromptAsyncTrace",
            "failed sid=$sessionId mid=$messageId dir=${directory ?: "-"} bytes=$textBytes elapsed=${elapsedMs}ms " +
                "text='$textPreview' error=${error.message}",
            error,
        )
        throw error
    }
}

suspend fun OpenCodeApi.promptV2(
    conn: ServerConnection,
    sessionId: String,
    request: V2PromptRequest,
    directory: String? = null,
    workspaceId: String? = null,
): V2AdmittedPrompt {
    val response = httpClient.post("${conn.baseUrl}/api/session/$sessionId/prompt") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
        workspaceId?.let { parameter("workspace", it) }
        contentType(ContentType.Application.Json)
        setBody(request)
    }
    if (!response.status.isSuccess()) throw RuntimeException("V2 prompt admission failed: ${response.status}")
    return response.body<V2DataResponse<V2AdmittedPrompt>>().data
}