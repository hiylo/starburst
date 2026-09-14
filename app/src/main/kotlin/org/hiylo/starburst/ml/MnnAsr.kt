/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : MnnAsr.kt
 * Date : 2026/09/09 23:45:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ml

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.mnn.FeatureConfig
import com.k2fsa.sherpa.mnn.OnlineModelConfig
import com.k2fsa.sherpa.mnn.OnlineRecognizer
import com.k2fsa.sherpa.mnn.OnlineRecognizerConfig
import com.k2fsa.sherpa.mnn.OnlineStream
import com.k2fsa.sherpa.mnn.OnlineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 封装 MNN 端侧流式语音识别（sherpa-mnn Zipformer 中英双语，int8），
 * 实现「按住说话、松手上屏」的离线 ASR。
 *
 * 模型与端侧大模型一样，从 ModelScope 下载到内部存储，逐文件校验 SHA-256。
 * 底层识别引擎通过预编译的 `libsherpa-mnn-jni.so`（仅 arm64-v8a）提供。
 */
object MnnAsr {

    /** ModelScope 上 MNN 官方账号的 ASR 模型镜像。 */
    private const val MODEL_SCOPE_BASE_URL =
        "https://www.modelscope.cn/models/MNN/sherpa-mnn-streaming-zipformer-bilingual-zh-en-2023-02-20/resolve/master/"

    /** 模型文件清单：name → (SHA-256 pin, byte size)。 */
    private data class ModelScopeFile(val sha256: String, val size: Long)

    private val MODEL_SCOPE_FILES: Map<String, ModelScopeFile> = mapOf(
        "encoder-epoch-99-avg-1.int8.mnn" to ModelScopeFile(
            "db2230d6e794c5d4ee29827fedd86dcf349eeb7194a47f5da1a7a5d837826bb5", 269246668L
        ),
        "decoder-epoch-99-avg-1.int8.mnn" to ModelScopeFile(
            "63ad2c99be2cacd39870c8b82975e6ae07ab9af61b6efe97a21900cd1c8a5d84", 13191808L
        ),
        "joiner-epoch-99-avg-1.int8.mnn" to ModelScopeFile(
            "053eecaad11f543269d83454d578485193b23d91f3e6105fcfc8a60345a3d6fb", 12835824L
        ),
        "tokens.txt" to ModelScopeFile(
            "a8e0e4ec53810e433789b54a5c0134a7eaa2ffca595a6334d54c00da858841d3", 56317L
        ),
    )

    private const val TAG = "MnnAsr"
    private const val MODEL_DIR_NAME = "asr_models"

    enum class State {
        NotDownloaded,
        Downloading,
        Ready,
        Failed,
    }

    @Volatile
    var state: State = State.NotDownloaded
        private set

    @Volatile
    var downloadProgressPercent: Int = 0
        private set

    @Volatile
    private var recognizer: OnlineRecognizer? = null

    /** 串行化 native 调用，避免并发解码破坏内部缓冲。 */
    private val nativeLock = Mutex()

    /**
     * 模型是否已下载（存在于磁盘）。
     */
    fun modelDirectory(context: Context): File? {
        val dir = File(context.filesDir, "mnn_models/$MODEL_DIR_NAME")
        return if (isModelPresent(dir)) dir else null
    }

    /**
     * 当前设备是否支持语音识别（需 arm64-v8a + JNI 库存在）。
     *
     * `libsherpa-mnn-jni.so` 是按「单体 MNN」编译的（其 DT_NEEDED 只指向 libMNN.so，
     * 依赖的 MNN::Express 符号位于单体 libMNN.so 内），而本 App 打包的是拆分版 MNN
     * （core 在 libMNN.so，Express 在 libMNN_Express.so）。因此加载 sherpa JNI 前先加载
     * libMNN.so 与 libMNN_Express.so，使 MNN::Express 符号在命名空间内可见。
     */
    fun isSupported(): Boolean {
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: return false
        if (abi != "arm64-v8a") return false
        return runCatching {
            System.loadLibrary("MNN")
            System.loadLibrary("MNN_Express")
            System.loadLibrary("sherpa-mnn-jni")
        }.isSuccess
    }

    /**
     * 下载并校验 ASR 模型到内部存储，逐文件校验 SHA-256。报告进度 [onProgress] (0..100)。
     */
    suspend fun downloadModel(context: Context, onProgress: (Int) -> Unit = {}): Boolean {
        return withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "mnn_models/$MODEL_DIR_NAME")
            if (isModelPresent(dir)) {
                onProgress(100)
                state = State.Ready
                return@withContext true
            }
            state = State.Downloading
            downloadProgressPercent = 0
            dir.mkdirs()

            val totalBytes = MODEL_SCOPE_FILES.values.sumOf { it.size }
            var lastReported = -1
            try {
                var cumulativeBytes = 0L
                for ((name, meta) in MODEL_SCOPE_FILES) {
                    val target = File(dir, name)
                    if (target.exists()) target.delete()
                    val bytes = downloadFile("$MODEL_SCOPE_BASE_URL$name", target) { fileBytes ->
                        val pct = ((cumulativeBytes + fileBytes) * 100) / totalBytes
                        val capped = pct.toInt().coerceIn(0, 99)
                        if (capped != lastReported) {
                            lastReported = capped
                            downloadProgressPercent = capped
                            onProgress(capped)
                        }
                    }
                    if (bytes != meta.size) {
                        Log.e(TAG, "size mismatch for $name: expected ${meta.size}, got $bytes")
                        dir.deleteRecursively()
                        state = State.Failed
                        return@withContext false
                    }
                    val actualSha = sha256(target)
                    if (!actualSha.equals(meta.sha256, ignoreCase = true)) {
                        Log.e(TAG, "SHA-256 mismatch for $name")
                        dir.deleteRecursively()
                        state = State.Failed
                        return@withContext false
                    }
                    cumulativeBytes += bytes
                }
                state = State.Ready
                onProgress(100)
                true
            } catch (e: Exception) {
                Log.e(TAG, "download failed", e)
                dir.deleteRecursively()
                state = State.Failed
                false
            }
        }
    }

    /**
     * 加载识别器（若尚未加载）。返回是否成功。必须先 [downloadModel]。
     */
    suspend fun ensureLoaded(context: Context): Boolean {
        if (recognizer != null) return true
        // 确保原生库已按依赖顺序加载（幂等）。
        if (!runCatching {
                System.loadLibrary("MNN")
                System.loadLibrary("MNN_Express")
                System.loadLibrary("sherpa-mnn-jni")
            }.isSuccess) {
            return false
        }
        return withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "mnn_models/$MODEL_DIR_NAME")
            if (!isModelPresent(dir)) return@withContext false
            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = File(dir, "encoder-epoch-99-avg-1.int8.mnn").absolutePath,
                        decoder = File(dir, "decoder-epoch-99-avg-1.int8.mnn").absolutePath,
                        joiner = File(dir, "joiner-epoch-99-avg-1.int8.mnn").absolutePath,
                    ),
                    tokens = File(dir, "tokens.txt").absolutePath,
                    numThreads = 4,
                    debug = false,
                    provider = "cpu",
                    modelType = "",
                ),
                enableEndpoint = false,
                decodingMethod = "greedy_search",
            )
            val r = runCatching { OnlineRecognizer(config) }.getOrElse {
                Log.e(TAG, "OnlineRecognizer init failed", it)
                return@withContext false
            }
            recognizer = r
            state = State.Ready
            true
        }
    }

    /** 创建一个新的识别流（会话）。 */
    fun createStream(): OnlineStream? {
        val r = recognizer ?: return null
        return runCatching { r.createStream() }.getOrNull()
    }

    /**
     * 输入一段 PCM 样本并解码，返回当前识别到的完整文本（流式累积）。
     * 调用方应持续喂入 16kHz 单声道 float 样本；返回的文本为当前全部已识别内容。
     */
    suspend fun acceptWaveform(stream: OnlineStream, samples: FloatArray): String? {
        val r = recognizer ?: return null
        return withContext(Dispatchers.IO) {
            nativeLock.withLock {
                runCatching {
                    stream.acceptWaveform(samples, 16000)
                    if (!r.isReady(stream)) return@withLock null
                    r.decode(stream)
                    r.getResult(stream).text
                }.getOrElse {
                    Log.e(TAG, "acceptWaveform failed", it)
                    null
                }
            }
        }
    }

    /** 标记输入结束，返回最终完整文本。 */
    suspend fun finish(stream: OnlineStream): String {
        val r = recognizer ?: return ""
        return withContext(Dispatchers.IO) {
            nativeLock.withLock {
                runCatching {
                    stream.inputFinished()
                    val text = r.getResult(stream).text
                    stream.release()
                    text
                }.getOrElse {
                    Log.e(TAG, "finish failed", it)
                    stream.release()
                    ""
                }
            }
        }
    }

    /** 释放识别流（未完成的取消场景）。 */
    fun releaseStream(stream: OnlineStream) {
        runCatching { stream.release() }
    }

    /** 释放识别器资源。 */
    fun release() {
        runCatching { recognizer?.release() }
        recognizer = null
    }

    private fun isModelPresent(dir: File): Boolean {
        if (!dir.exists()) return false
        return MODEL_SCOPE_FILES.keys.all { File(dir, it).exists() }
    }

    private fun downloadFile(url: String, outFile: File, onChunk: (Long) -> Unit): Long {
        val connection = java.net.URL(url).openConnection()
        connection.connectTimeout = 30_000
        connection.readTimeout = 300_000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
        val input = connection.getInputStream()
        return try {
            FileOutputStream(outFile).use { output ->
                val buffer = ByteArray(128 * 1024)
                var downloaded = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    onChunk(downloaded)
                }
                downloaded
            }
        } finally {
            input.close()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
