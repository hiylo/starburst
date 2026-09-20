/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : NetworkModule.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private const val NETWORK_LOG_TAG = "KtorCli"
private const val REDACTED_PLACEHOLDER = "<redacted>"

/** 命中即打码的敏感 header 名（小写比较，保持名称原样输出）。 */
private val SENSITIVE_LOG_HEADERS: Set<String> = setOf(
    HttpHeaders.Authorization.lowercase(),
    HttpHeaders.ProxyAuthorization.lowercase(),
    HttpHeaders.Cookie.lowercase(),
    HttpHeaders.SetCookie.lowercase(),
    HttpHeaders.ProxyAuthenticate.lowercase(),
    HttpHeaders.WWWAuthenticate.lowercase(),
    "x-api-key",
    "x-auth-token",
)

/**
 * 对 Ktor Logging 插件产出的消息逐行脱敏。Ktor 的 header 行格式为
 * `-> NAME: value`（见 logHeader），故先剥离 `->` 前缀再取 header 名，
 * 命中 [SENSITIVE_LOG_HEADERS] 时仅把 value 替换为 [REDACTED_PLACEHOLDER]，
 * 名称照打（含 `->` 前缀）；REQUEST/METHOD/RESPONSE/状态行等非 header 行
 * 原样透传。请求(Send)与响应(Receive)共用同一 logger，SSH / 镜像两条通道
 * 均被覆盖。
 */
private fun redactSensitiveHeaders(message: String): String = message
    .lineSequence()
    .joinToString("\n") { line -> redactSensitiveLogLine(line) }

private fun redactSensitiveLogLine(line: String): String {
    val trimmed = line.trimStart()
    val headerName = trimmed
        .removePrefix("->")
        .substringBefore(':')
        .trim()
    val separator = line.indexOf(':')
    return if (separator > 0 && headerName.lowercase() in SENSITIVE_LOG_HEADERS) {
        line.substring(0, separator) + ": " + REDACTED_PLACEHOLDER
    } else {
        line
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "starburst_prefs")

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }
    
    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        
        // 注：gzip 由 OkHttp 引擎层透明处理（默认添加 Accept-Encoding: gzip 并自动解压），
        // 无需另装 Compression 插件；后端代理会把上游 gzip 透传回来。
        
        install(Logging) {
            // HEADERS：记请求行 + 状态行 + 耗时（Ktor 无更轻等级；不打印响应体，量可控），
            // 便于真机定位「哪次请求慢」——弱网归因的关键。
            level = LogLevel.HEADERS
            // 高危防护：HEADERS 会把 Authorization（Basic 明文 Base64 密码 / Bearer token）、
            // Cookie、Proxy-Authorization 等凭据原样打进 Logcat。改用自定义 Logger，
            // 命中敏感 header 时 value 打码为 <redacted>（名称照打）。请求(Send)与响应
            // (Receive)共用同一 logger，SSH / 镜像两条通道的出入流量均被覆盖。
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d(NETWORK_LOG_TAG, redactSensitiveHeaders(message))
                }
            }
        }
        
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 120_000
        }

        install(HttpRedirect) {
            // Reverse proxies commonly redirect the API origin; 307/308 preserve the prompt POST body.
            checkHttpMethod = false
        }

        install(WebSockets)
        
        install(Auth) {
            // Auth will be configured per-request based on server config
        }
        
        engine {
            config {
                // OkHttp-specific: disable response body buffering for streaming
                retryOnConnectionFailure(true)
                // 多目录扇出 / 并行请求远超 OkHttp 默认的 5 连接/单 host：
                // 提高单 host 并发上限 + 更长的连接复用窗口（VPN 隧道常重建，
                // 5 分钟 keep-alive 让半死连接堆积，这里收紧到 2 分钟）。
                dispatcher(Dispatcher().apply { maxRequestsPerHost = 16 })
                connectionPool(ConnectionPool(maxIdleConnections = 20, keepAliveDuration = 2, TimeUnit.MINUTES))
            }
        }
        
        // Default headers will be set per-request in OpenCodeApi
    }
    
    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}
