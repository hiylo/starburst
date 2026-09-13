/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : ServerAsrRecorder.kt
 * Date : 2026/09/12 11:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.opencode.ml

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hiylo.opencode.logging.AppLogger as Log

/**
 * 服务端语音识别：录音分片上传到 OpenCode Backend，由 NAS 上的流式引擎返回增量文本。
 *
 * 与 [MnnAsrRecorder] 的交互约定一致（AudioRecord 16kHz 单声道、按住说话松手上屏），
 * 区别是编码格式为 PCM16LE 裸字节，且识别在远端完成。用于端侧 MNN 模型不可用的设备。
 *
 * 单片失败不会立刻终止录音：连续失败 [maxConsecutiveFailures] 次才报错，
 * 期间已上屏的 partial 保留。
 *
 * @author Hsi Chu
 * @since V1.3.0
 */
class ServerAsrRecorder(
    private val api: ServerAsrApi,
    private val backendUrl: String,
    private val backendToken: String,
) : AsrSession {

    /** 采样率，必须与引擎一致。 */
    private val sampleRate = 16000

    /** 每次读取的样本数（200ms 一片，6400 字节）。 */
    private val chunkSamples = 3200

    /** 连续多少片上传失败后放弃本次录音。 */
    private val maxConsecutiveFailures = 3

    private var record: AudioRecord? = null
    private var job: Job? = null

    /** 后台校对任务：不阻塞 stop() 返回。 */
    private var refineJob: Job? = null

    /** 录音代数：开始新录音时递增，用于丢弃上一次录音的迟到校对结果。 */
    @Volatile
    private var generation = 0
    private var listener: AsrSession.Listener? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var running = false

    /** 松手为 true（需要 finish 收尾），上滑取消为 false。 */
    @Volatile
    private var stopRequested = false

    @Volatile
    private var sessionId: String? = null

    override suspend fun start(listener: AsrSession.Listener): Boolean = withContext(Dispatchers.IO) {
        if (running) return@withContext false
        generation++
        val sid = api.createSession(backendUrl, backendToken)
        if (sid == null) {
            listener.onError("stt service unavailable")
            return@withContext false
        }
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            sampleRate * 2,
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            api.deleteSession(backendUrl, backendToken, sid)
            listener.onError("AudioRecord init failed")
            return@withContext false
        }
        sessionId = sid
        this@ServerAsrRecorder.listener = listener
        record = rec
        running = true
        withContext(Dispatchers.Main) { listener.onStart() }
        job = scope.launch { runRecording() }
        true
    }

    override suspend fun stop() {
        stopRequested = true
        running = false
        job?.join()
        job = null
    }

    override suspend fun cancel() {
        stopRequested = false
        running = false
        job?.join()
        job = null
        sessionId?.let { sid -> api.deleteSession(backendUrl, backendToken, sid) }
        sessionId = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun runRecording() {
        val rec = record ?: return
        val sid = sessionId ?: return
        val l = listener ?: return
        try {
            rec.startRecording()
            val buffer = ByteArray(chunkSamples * 2)
            var emptyRuns = 0
            var failures = 0
            var aborted = false
            while (currentCoroutineContext().isActive) {
                val read = rec.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read <= 0) {
                    emptyRuns++
                    if (emptyRuns > 10 || !running) break
                    delay(10)
                    continue
                }
                emptyRuns = 0
                val pcm = buffer.copyOf(read)
                // 松手之后仍然把最后这一块送上去（否则丢掉尾巴），但不再回传中间结果，
                // 避免松手后输入框继续蹦字。
                val text = api.sendChunk(backendUrl, backendToken, sid, pcm)
                if (text == null) {
                    failures++
                    if (failures >= maxConsecutiveFailures) {
                        throw IllegalStateException("stt request failed $failures times")
                    }
                } else {
                    failures = 0
                    if (running && text.isNotBlank()) {
                        withContext(Dispatchers.Main) { l.onPartialResult(text) }
                    }
                }
                if (!running) {
                    aborted = failures >= maxConsecutiveFailures
                    break
                }
            }
            // 松手：让引擎冲刷尾部，取最终文本；上滑取消则丢弃会话。
            if (stopRequested && !aborted) {
                val final = api.finish(backendUrl, backendToken, sid)
                if (final != null && final.isNotBlank()) {
                    withContext(Dispatchers.Main) { l.onPartialResult(final) }
                    // 流式识别常有重字与同音错字，交给大模型就地校对。放到后台跑：
                    // 不阻塞 stop() 返回，校正好就直接替换输入框里的文字。后端未配
                    // LLM、调用失败或校对结果被判定不可信时返回原文，等于跳过这一步。
                    val gen = generation
                    refineJob = scope.launch {
                        val polished = api.refine(backendUrl, backendToken, final)
                        // 用户可能已经开始了下一段录音，那就别覆盖新内容。
                        if (generation == gen && polished != null &&
                            polished.isNotBlank() && polished != final
                        ) {
                            withContext(Dispatchers.Main) { l.onPartialResult(polished) }
                        }
                    }
                }
            }
            sessionId = null
            withContext(Dispatchers.Main) { l.onStopped() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "server asr failed", e)
            sessionId = null
            withContext(Dispatchers.Main) { l.onError(e.message ?: "server asr failed") }
        } finally {
            runCatching { rec.stop() }
            runCatching { rec.release() }
            record = null
        }
    }

    private companion object {
        const val TAG = "ServerAsrRecorder"
    }
}
