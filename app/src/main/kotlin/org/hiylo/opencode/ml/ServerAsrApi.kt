/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : ServerAsrApi.kt
 * Date : 2026/09/12 11:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.opencode.ml

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import org.hiylo.opencode.logging.AppLogger as Log

/** `POST /api/stt/sessions` 的响应。 */
@Serializable
internal data class SttSessionCreated(
    @SerialName("session_id") val sessionId: String = "",
)

/** `/chunks` 与 `/finish` 的响应：text 为当前累积文本。 */
@Serializable
internal data class SttTranscript(
    val text: String = "",
    val final: Boolean = false,
)

/**
 * OpenCode Backend 的流式语音识别端点客户端（`/api/stt`）。
 *
 * 后端把音频分片代理给部署在 NAS 上的流式识别引擎；这里只负责协议与错误收敛，
 * 分片重试与 UI 交互由 [ServerAsrRecorder] 处理。失败一律返回 null，不抛异常，
 * 让录音循环自己决定容忍多少次。
 *
 * 每个请求都套 [callTimeoutMs] 的超时：分片只有 6.4KB、本地往返约 5ms，超时就说明
 * 网络或引擎出了问题。共享 HttpClient 的 120s 超时对语音输入不可接受——它会卡住
 * 松手手势，让连续失败保护要等 6 分钟才触发。
 *
 * @author Hsi Chu
 * @since V1.3.0
 */
@Singleton
class ServerAsrApi @Inject constructor(
    private val httpClient: HttpClient,
    @Suppress("unused") private val json: Json,
) {

    /** 单个请求的超时（毫秒）。 */
    private val callTimeoutMs = 8_000L

    /** 校对请求的超时（毫秒）：后端用深度推理模型纠错，长句思考可达十几秒，放宽到 95s。 */
    private val refineTimeoutMs = 95_000L

    /**
     * 统一的请求包装：切到 IO 线程、套超时、把网络与解析异常收敛成 null。
     * 协程取消必须原样抛出，否则录音循环收不到取消信号会继续跑。
     */
    private suspend fun <T> call(
        timeoutMs: Long = callTimeoutMs,
        block: suspend () -> T,
    ): T? = withContext(Dispatchers.IO) {
        try {
            withTimeout(timeoutMs) { block() }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "stt request timed out after ${timeoutMs}ms")
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }

    /** 引擎是否可达且已启用（后端未配置 --stt-url 时返回 false）。 */
    suspend fun isAvailable(backendUrl: String, token: String): Boolean =
        call {
            val resp = httpClient.get(sttUrl(backendUrl)) {
                header("Authorization", "Bearer $token")
            }
            resp.status.value in 200..299
        } == true

    /** 新建识别会话，返回 session_id；失败返回 null。 */
    suspend fun createSession(backendUrl: String, token: String): String? {
        val resp = call {
            httpClient.post(sttUrl(backendUrl, "sessions")) {
                header("Authorization", "Bearer $token")
            }.body<SttSessionCreated>()
        } ?: return null
        return resp.sessionId.takeIf { it.isNotBlank() }
    }

    /**
     * 上传一个裸 PCM16LE 单声道分片，返回当前累积文本。
     * 返回 null 表示本次请求失败（网络错误或引擎异常），会话仍然有效。
     */
    suspend fun sendChunk(
        backendUrl: String,
        token: String,
        sessionId: String,
        pcm: ByteArray,
    ): String? {
        val resp = call {
            httpClient.post(sttUrl(backendUrl, "sessions/$sessionId/chunks")) {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.OctetStream)
                setBody(pcm)
            }.body<SttTranscript>()
        } ?: return null
        return resp.text
    }

    /** 结束输入并返回最终文本；失败返回 null（此前已上屏的 partial 仍有效）。 */
    suspend fun finish(backendUrl: String, token: String, sessionId: String): String? =
        call {
            httpClient.post(sttUrl(backendUrl, "sessions/$sessionId/finish")) {
                header("Authorization", "Bearer $token")
            }.body<SttTranscript>()
        }?.text

    /**
     * 用大模型就地校对一段转写文本，修掉流式识别的重字、同音错字与标点问题。
     *
     * 后端未配置 LLM、调用失败或校对结果被判定不可信时都会原样返回输入，所以调用方
     * 可以放心地用返回值覆盖原始 final。返回 null 表示请求本身失败，应保留原文。
     */
    suspend fun refine(backendUrl: String, token: String, text: String): String? =
        call(refineTimeoutMs) {
            httpClient.post(sttUrl(backendUrl, "refine")) {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(mapOf("text" to text))
            }.body<SttTranscript>()
        }?.text?.takeIf { it.isNotBlank() }

    /** 丢弃会话（上滑取消）。尽力而为，不抛异常。 */
    suspend fun deleteSession(backendUrl: String, token: String, sessionId: String) {
        call {
            httpClient.delete(sttUrl(backendUrl, "sessions/$sessionId")) {
                header("Authorization", "Bearer $token")
            }
        }
    }

    private fun sttUrl(backendUrl: String, path: String = ""): String =
        "${backendUrl.trimEnd('/')}/api/stt${if (path.isEmpty()) "" else "/" + path}"

    private companion object {
        const val TAG = "ServerAsrApi"
    }
}
