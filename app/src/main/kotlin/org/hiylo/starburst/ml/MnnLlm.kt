/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : MnnLlm.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Wrapper around MNN's on-device LLM (Qwen3.5-0.8B-MNN) for the next-step suggestion feature.
 *
 * The model is distributed as a GitHub Release asset (starburst-model.zip) and downloaded to
 * internal storage on first use. The zip is verified against a pinned SHA-256 before unpacking.
 */
object MnnLlm {

    /** ModelScope mirror of the same MNN model (fast inside mainland China). */
    private const val MODEL_SCOPE_BASE_URL =
        "https://modelscope.cn/models/MNN/Qwen3.5-0.8B-MNN/resolve/master/"

    /**
     * ModelScope file manifest: name → (SHA-256 pin, byte size).
     * Byte-identical to the bundled model weights; config.json is written from the app's
     * tuned copy instead of the upstream one (upstream enables thinking + 4 threads).
     */
    private data class ModelScopeFile(val sha256: String, val size: Long)

    private val MODEL_SCOPE_FILES: Map<String, ModelScopeFile> = mapOf(
        "llm.mnn" to ModelScopeFile(
            "94e0459e10584487a3bda77dc3c3322c0c01cfc9d223a7284e8d07345bf877d5", 2148136L
        ),
        "llm.mnn.weight" to ModelScopeFile(
            "4e25ef4cbfb49a33013d17300c2f68cd9c9ce8f2e3f7b947fd2c574ec297ca5e", 470382614L
        ),
        "llm.mnn.json" to ModelScopeFile(
            "fe58a15987b391b443ade62a92221956349e11628fa36b1bfb9503af32923a1a", 5343877L
        ),
        "llm_config.json" to ModelScopeFile(
            "33f2c15bda1911ed666418437f45900e78185b57b883545e5d02c14f92a0907b", 8691L
        ),
        "tokenizer.txt" to ModelScopeFile(
            "7e75de1f279a10b65bd9dc1a5207205cb8993823861c4c42bbbd74e48e1c23a4", 6465727L
        ),
        "visual.mnn" to ModelScopeFile(
            "05e92b95a1295ee747b0436061b7ae7b1d1d3c7903306fe99e4af13e5aca3de3", 251528L
        ),
        "visual.mnn.weight" to ModelScopeFile(
            "4c03fcac0d164d014e5cd1a1d7ab4fc95babbec724a268f3ba486c5db3511161", 63043212L
        ),
        "export_args.json" to ModelScopeFile(
            "5c82a104779d8840d84349e17ca1c1b97dd0754afd5dcfc73e95c47ac96b754a", 1044L
        ),
    )

    /** App-tuned config.json (thread 12, low precision, thinking off); written after download. */
    private val TUNED_CONFIG_JSON =
        """
        {
            "max_new_tokens": 8192,
            "llm_model": "llm.mnn",
            "llm_weight": "llm.mnn.weight",
            "backend_type": "cpu",
            "thread_num": 12,
            "precision": "low",
            "memory": "low",
            "sampler_type": "mixed",
            "mixed_samplers": [
                "penalty",
                "topK",
                "topP",
                "min_p",
                "temperature"
            ],
            "penalty": 1.1,
            "temperature": 1.0,
            "topP": 0.95,
            "topK": 20,
            "min_p": 0,
            "mllm": {
                "backend_type": "cpu",
                "thread_num": 12,
                "precision": "low",
                "memory": "low"
            },
            "jinja": {
                "context": {
                    "enable_thinking": false
                }
            }
        }
        """.trimIndent()

    private const val TAG = "MnnLlm"
    private const val MODEL_DIR_NAME = "models"

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
    private var nativePtr = 0L

    @Volatile
    private var loaded = false

    /** Serializes native calls so reset()/generate() never overlap with MNN internals. */
    private val nativeLock = Mutex()

    /** Dedicated scope used only to serialize [release] through [nativeLock] without blocking callers. */
    private val releaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private external fun initNative(configDir: String): Long
    private external fun generateNative(ptr: Long, prompt: String, maxTokens: Int): String
    private external fun generateStreamingNative(ptr: Long, prompt: String, maxTokens: Int, callback: StreamingCallback): Long
    private external fun resetNative(ptr: Long)
    private external fun releaseNative(ptr: Long)

    /** Interface invoked incrementally by native code as tokens are decoded. */
    interface StreamingCallback {
        fun onDelta(text: String)
    }

    init {
        System.loadLibrary("starburst_mnn")
    }

    /**
     * Preloads the model in the background (call from app startup or entering a chat).
     * Safe to call repeatedly; only loads once.
     */
    suspend fun preload(context: Context) {
        if (loaded && nativePtr != 0L) return
        withContext(Dispatchers.IO) {
            ensureLoaded(context)
        }
    }

    /**
     * Returns the model directory if the model is already downloaded, or null when it needs
     * to be downloaded first. Does not attempt to load the native session.
     */
    fun modelDirectory(context: Context): File? {
        val dir = File(context.filesDir, "mnn_models/$MODEL_DIR_NAME")
        return if (isModelPresent(dir)) dir else null
    }

    /**
     * Returns true when the model files are bundled inside the app assets (assets/models),
     * e.g. debug builds that ship the weights locally. In that case the model can be
     * extracted to internal storage without a network download.
     */
    fun hasBundledModel(context: Context): Boolean {
        return try {
            val required = listOf("llm.mnn", "llm.mnn.weight", "llm_config.json", "tokenizer.txt")
            required.all { name -> context.assets.open("models/$name").use { true } }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extracts the bundled model from app assets into internal storage so the on-device
     * engine can load it without a network download. Reports progress via [onProgress] (0..100).
     * No-op when the model is already on disk. Returns true when the model is present afterwards.
     */
    suspend fun extractBundledModel(context: Context, onProgress: (Int) -> Unit = {}): Boolean {
        return withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "mnn_models/$MODEL_DIR_NAME")
            val present = extractBundledModelInternal(context, dir, onProgress)
            if (present) onProgress(100)
            present
        }
    }

    /**
     * Ensures the model is extracted to internal storage and the native session is initialized.
     * Returns false if the model is not available at all (caller should prompt for download).
     */
    suspend fun ensureLoaded(context: Context): Boolean {
        if (loaded && nativePtr != 0L) return true
        return withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "mnn_models/$MODEL_DIR_NAME")
            if (!isModelPresent(dir) && !extractBundledModelInternal(context, dir)) {
                state = State.NotDownloaded
                return@withContext false
            }
            nativeLock.withLock {
                if (loaded && nativePtr != 0L) return@withLock true
                nativePtr = initNative(dir.absolutePath)
                loaded = nativePtr != 0L
                state = if (loaded) State.Ready else State.Failed
                if (!loaded) Log.e(TAG, "initNative returned 0 (load failed)")
                loaded
            }
        }
    }

    /**
     * Downloads and verifies the model into internal storage from the ModelScope mirror,
     * downloading each file individually with a pinned SHA-256. Reports progress via
     * [onProgress] (0..100). Safe to call repeatedly.
     */
    suspend fun downloadModel(context: Context, onProgress: (Int) -> Unit = {}): Boolean {
        return withContext(Dispatchers.IO) {
            downloadModelFromModelScope(context, onProgress)
        }
    }

    /**
     * Downloads each model file individually from ModelScope, verifying every file against its
     * pinned SHA-256, then writes the app-tuned config.json. Reports cumulative progress.
     */
    private suspend fun downloadModelFromModelScope(
        context: Context,
        onProgress: (Int) -> Unit = {},
    ): Boolean {
        val dir = File(context.filesDir, "mnn_models/$MODEL_DIR_NAME")
        if (isModelPresent(dir)) {
            onProgress(100)
            return true
        }
        state = State.Downloading
        downloadProgressPercent = 0
        dir.mkdirs()

        val totalBytes = MODEL_SCOPE_FILES.values.sumOf { it.size }
        var lastReported = -1
        return try {
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
                    Log.e(TAG, "ModelScope size mismatch for $name: expected ${meta.size}, got $bytes")
                    dir.deleteRecursively()
                    state = State.Failed
                    return false
                }
                val actualSha = sha256(target)
                if (!actualSha.equals(meta.sha256, ignoreCase = true)) {
                    Log.e(TAG, "ModelScope SHA-256 mismatch for $name")
                    dir.deleteRecursively()
                    state = State.Failed
                    return false
                }
                cumulativeBytes += bytes
            }
            writeTunedConfig(context, dir)
            if (isModelPresent(dir)) {
                state = State.Ready
                onProgress(100)
                true
            } else {
                dir.deleteRecursively()
                state = State.Failed
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "ModelScope download failed", e)
            dir.deleteRecursively()
            state = State.Failed
            false
        }
    }

    /**
     * Writes config.json: prefers the app's bundled (tracked) copy, falling back to the tuned
     * constant. The upstream ModelScope config differs (thinking on, 4 threads) and is not used.
     */
    private fun writeTunedConfig(context: Context, dir: File) {
        val content = runCatching {
            context.assets.open("models/config.json").use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrElse {
            Log.w(TAG, "Bundled config.json missing, using embedded tuned config")
            TUNED_CONFIG_JSON
        }
        File(dir, "config.json").writeText(content)
    }

    /** Generates a full response synchronously (blocking). Assumes [ensureLoaded] succeeded. */
    suspend fun generate(prompt: String, maxTokens: Int = 256): String {
        return withContext(Dispatchers.IO) {
            nativeLock.withLock {
                val ptr = nativePtr
                if (ptr == 0L) return@withLock ""
                runCatching { generateNative(ptr, prompt, maxTokens) }.getOrElse {
                    Log.e(TAG, "generate failed", it)
                    ""
                }
            }
        }
    }

    /**
     * Generates a response, invoking [onDelta] incrementally on the calling thread
     * as tokens are decoded. Assumes [ensureLoaded] succeeded. Returns the full text.
     */
    suspend fun generateStreaming(
        prompt: String,
        maxTokens: Int = 256,
        onDelta: (String) -> Unit,
    ): String {
        return withContext(Dispatchers.IO) {
            val collector = StringBuilder()
            val callback = object : StreamingCallback {
                override fun onDelta(text: String) {
                    collector.append(text)
                    onDelta(text)
                }
            }
            // NOTE: do NOT wrap the native call in withTimeoutOrNull — cancelling the Kotlin
            // coroutine does not interrupt the blocking MNN call, and releasing / re-entering
            // MNN while a generation is still running corrupts its buffer allocator (Scudo crash).
            // Generation length is bounded by maxTokens; the lock prevents concurrent native use.
            // nativePtr 必须在锁内读取，避免与 release() 释放竞态导致 use-after-free。
            val rc = runCatching {
                nativeLock.withLock {
                    val ptr = nativePtr
                    if (ptr == 0L) -1L else generateStreamingNative(ptr, prompt, maxTokens, callback)
                }
            }.getOrDefault(-1L)
            if (rc != 0L) Log.e(TAG, "generateStreamingNative returned $rc")
            collector.toString()
        }
    }

    /** Clears conversation context so the next prompt starts fresh. */
    suspend fun reset() {
        withContext(Dispatchers.IO) {
            nativeLock.withLock {
                if (nativePtr != 0L) runCatching { resetNative(nativePtr) }
            }
        }
    }

    /** Releases native resources. Idempotent; serialized behind [nativeLock] to avoid freeing
     *  memory while a generation is still in flight (use-after-free). */
    fun release() {
        releaseScope.launch {
            nativeLock.withLock {
                if (nativePtr != 0L) {
                    runCatching { releaseNative(nativePtr) }
                    nativePtr = 0L
                    loaded = false
                }
            }
        }
    }

    private fun isModelPresent(dir: File): Boolean {
        if (!dir.exists()) return false
        val required = listOf("llm.mnn", "llm.mnn.weight", "llm_config.json", "tokenizer.txt")
        return required.all { File(dir, it).exists() }
    }

    /**
     * Copies the bundled model files from assets/models into [dir] if the model is not
     * already present. Reports progress via [onProgress] (0..100). Non-suspend; call on IO.
     */
    private fun extractBundledModelInternal(
        context: Context,
        dir: File,
        onProgress: (Int) -> Unit = {},
    ): Boolean {
        if (isModelPresent(dir)) {
            onProgress(100)
            return true
        }
        if (!hasBundledModel(context)) {
            state = State.NotDownloaded
            return false
        }
        state = State.Downloading
        dir.mkdirs()
        val files = listOf(
            "llm.mnn", "llm.mnn.weight", "llm.mnn.json", "llm_config.json",
            "tokenizer.txt", "visual.mnn", "visual.mnn.weight", "config.json", "export_args.json",
        )
        var copied = 0L
        var lastReported = -1
        // Approximate unpacked bundle size, used only to report progress.
        val total = 547L * 1024 * 1024
        files.forEach { name ->
            val target = File(dir, name)
            if (!target.exists()) {
                runCatching {
                    context.assets.open("models/$name").use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                copied += read
                                val pct = ((copied * 100) / total).toInt().coerceIn(0, 99)
                                if (pct != lastReported) {
                                    lastReported = pct
                                    onProgress(pct)
                                }
                            }
                        }
                    }
                }
            }
        }
        val present = isModelPresent(dir)
        state = if (present) State.Ready else State.Failed
        return present
    }

    /**
     * Downloads [url] to [outFile] with connect/read timeouts; returns bytes downloaded.
     * Caller verifies size and SHA-256 afterwards.
     */
    private fun downloadFile(url: String, outFile: File, onChunk: (Long) -> Unit): Long {
        val connection = java.net.URL(url).openConnection()
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
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