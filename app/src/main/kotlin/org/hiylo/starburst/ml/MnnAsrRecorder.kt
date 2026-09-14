/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : MnnAsrRecorder.kt
 * Date : 2026/09/09 23:45:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ml

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.mnn.OnlineStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 录音并送入 MNN 流式识别器，实现「按住说话、松手上屏」。
 *
 * 使用系统 [AudioRecord] 采集 16kHz 单声道 PCM，分块喂给 [MnnAsr] 的识别流，
 * 每次解码后回调当前累积识别文本（实时上屏）；松手调用 [stop] 收尾。
 */
class MnnAsrRecorder(private val context: Context) : AsrSession {

    /** 采样率，必须与模型 FeatureConfig 一致。 */
    private val sampleRate = 16000

    /** 每次读取的样本数（约 100ms 一块）。 */
    private val chunkSamples = 1600

    private var record: AudioRecord? = null
    private var stream: OnlineStream? = null
    private var job: Job? = null
    private var listener: AsrSession.Listener? = null
    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)

    @Volatile
    private var running = false

    /**
     * 开始录音识别。需已持有 RECORD_AUDIO 权限且模型已加载。
     */
    /** 松手为 true（需要 finish 收尾并上屏），上滑取消为 false。 */
    @Volatile
    private var stopRequested = false

    override suspend fun start(listener: AsrSession.Listener): Boolean = withContext(Dispatchers.IO) {
        if (running) return@withContext false
        stopRequested = false
        if (!MnnAsr.ensureLoaded(context)) {
            listener.onError("ASR model not loaded")
            return@withContext false
        }
        val stream = MnnAsr.createStream() ?: run {
            listener.onError("Failed to create ASR stream")
            return@withContext false
        }
        this@MnnAsrRecorder.stream = stream
        this@MnnAsrRecorder.listener = listener
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            sampleRate * 2,
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            listener.onError("AudioRecord init failed")
            MnnAsr.releaseStream(stream)
            return@withContext false
        }
        record = rec
        running = true
        withContext(Dispatchers.Main) { listener.onStart() }
        job = scope.launch { runRecording() }
        true
    }

    /** 停止录音，等待最终结果并回调。 */
    override suspend fun stop() {
        stopRequested = true
        running = false
        job?.join()
        job = null
    }

    /** 取消录音，不产生结果。 */
    override suspend fun cancel() {
        stopRequested = false
        running = false
        job?.join()
        job = null
        stream?.let { MnnAsr.releaseStream(it) }
        stream = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun runRecording() {
        val rec = record ?: return
        val stream = stream ?: return
        val l = listener ?: return
        try {
            rec.startRecording()
            val buffer = FloatArray(chunkSamples)
            var emptyRuns = 0
            while (currentCoroutineContext().isActive) {
                val read = rec.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read <= 0) {
                    emptyRuns++
                    if (emptyRuns > 10 || !running) break
                    delay(10)
                    continue
                }
                emptyRuns = 0
                val samples = if (read == buffer.size) buffer else buffer.copyOf(read)
                val text = MnnAsr.acceptWaveform(stream, samples)
                // 松手之后不再回传中间结果，避免松手后输入框继续蹦字。
                if (running && text != null) {
                    withContext(Dispatchers.Main) { l.onPartialResult(text) }
                }
                if (!running) break
            }
            // 松手：收尾并取最终结果；上滑取消则丢弃，不上屏。
            if (stopRequested) {
                val final = MnnAsr.finish(stream)
                withContext(Dispatchers.Main) {
                    if (final.isNotBlank()) l.onPartialResult(final)
                }
            }
            this@MnnAsrRecorder.stream = null
            withContext(Dispatchers.Main) { l.onStopped() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "recording failed", e)
            withContext(Dispatchers.Main) { l.onError(e.message ?: "recording failed") }
        } finally {
            runCatching { rec.stop() }
            runCatching { rec.release() }
            record = null
        }
    }

    private companion object {
        const val TAG = "MnnAsrRecorder"
    }
}
