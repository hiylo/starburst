/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : OpenCodeApiFiles.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

// ============ Files ============

suspend fun OpenCodeApi.searchText(conn: ServerConnection, pattern: String): List<SearchMatch> {
    return httpClient.get("${conn.baseUrl}/find") {
        conn.authHeader?.let { header("Authorization", it) }
        parameter("pattern", pattern)
    }.body()
}

suspend fun OpenCodeApi.findFiles(conn: ServerConnection, query: String, type: String? = null, directory: String? = null, limit: Int? = null, dirs: String? = null): List<String> {
    return httpClient.get("${conn.baseUrl}/find/file") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let {
            parameter("directory", it)
            header("x-starburst-directory", it)
        }
        parameter("query", query)
        type?.let { parameter("type", it) }
        limit?.let { parameter("limit", it) }
        dirs?.let { parameter("dirs", it) }
    }.body()
}

suspend fun OpenCodeApi.readFile(conn: ServerConnection, path: String, directory: String? = null): FileContent {
    return httpClient.get("${conn.baseUrl}/file/content") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let {
            parameter("directory", it)
            header("x-starburst-directory", it)
        }
        parameter("path", path)
    }.body()
}

suspend fun OpenCodeApi.listDirectory(conn: ServerConnection, path: String = "", directory: String? = null): List<FileNode> {
    return httpClient.get("${conn.baseUrl}/file") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let {
            parameter("directory", it)
            header("x-starburst-directory", it)
        }
        parameter("path", path)
    }.body()
}