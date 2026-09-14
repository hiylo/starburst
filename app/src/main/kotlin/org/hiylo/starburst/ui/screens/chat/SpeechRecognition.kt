/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SpeechRecognition.kt
 * Date : 2026/09/09 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.chat

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * 封装系统 [SpeechRecognizer] 的语音识别能力，实现「按住说话、分段累积、松手上屏」。
 *
 * 系统识别器（小米小爱、Google 等）对单次连续语音有时长上限，说太长会提前结束或报错。
 * 因此这里采用「分段累积」策略：一段识别完成（[RecognitionListener.onResults]）后，如果用户
 * 仍按住未松手，就自动复用识别器继续听下一段，把每段结果累积起来；松手时一次性交付全部
 * 累积文本。这避免了长句被截断、也避免了识别器超时报错导致「白说一场」。
 *
 * 另外保留两项增强：
 * 1. 识别服务自动降级：当系统默认识别服务因权限/服务不可用而失败时，自动尝试 Google 识别器
 *    等备选服务，避免在小米等国内 ROM 上「当前设备不支持」「需要麦克风权限」这类问题。
 * 2. 部分结果兜底：停顿/无匹配时把已识别到的部分文本累积，而不是丢弃。
 */
class SpeechRecognition(private val context: Context) {

    /** 语音识别结果与状态回调。 */
    interface Listener {
        /** 已开始监听。 */
        fun onListening()

        /** 识别到一条最终文本结果（松手后交付的完整累积文本）。 */
        fun onResult(text: String)

        /** 识别过程中的实时部分结果（累积文本 + 当前段部分结果）。 */
        fun onPartialResult(text: String)

        /** 麦克风音量变化（rmsdB，约 0~10），可用于绘制波形。 */
        fun onRmsChanged(rmsdB: Float)

        /** 识别出错，错误码为 [SpeechRecognizer] 的错误常量。 */
        fun onError(error: Int)

        /** 监听结束（含正常结束与取消）。 */
        fun onStopped()
    }

    private var recognizer: SpeechRecognizer? = null
    private var listener: Listener? = null
    private var pendingCandidates: List<ComponentName?> = emptyList()
    private var lastPartial: String? = null
    private val accumulated = StringBuilder()
    /** 用户是否已松手（松手后等待当前段收尾即交付，不再继续下一段）。 */
    private var stopping = false
    private var startedAt = 0L

    /** 单次按住识别的最大总时长（毫秒），超过后自动停止并交付，避免无限占用麦克风。 */
    private val maxTotalDurationMs = 3 * 60 * 1000L

    /** 当前是否处于监听状态。 */
    val isListening: Boolean get() = recognizer != null

    /** 设备上是否存在可用的语音识别服务。 */
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * 枚举系统可用的识别服务组件，优先 Google 识别器（跨厂商兼容性最好），
     * 其次其它厂商识别器，最后系统默认识别器兜底（避免枚举清单与默认不一致）。
     */
    fun candidateComponents(): List<ComponentName?> {
        val services = runCatching {
            context.packageManager.queryIntentServices(
                Intent(RecognitionService.SERVICE_INTERFACE),
                PackageManager.GET_META_DATA,
            )
        }.getOrElse { emptyList() }
        val components = services.mapNotNull { it.serviceInfo?.let { si ->
            runCatching { ComponentName(si.packageName, si.name) }.getOrNull()
        } }
        val google = components.filter { it.packageName.contains("google", ignoreCase = true) }
        val others = components.filterNot { it.packageName.contains("google", ignoreCase = true) }
        // null 表示「系统默认识别器」，排在最后作为兜底。
        return google + others + listOf(null)
    }

    /**
     * 开始监听。若已在监听中则直接返回。
     */
    fun start(listener: Listener) {
        if (recognizer != null) return
        this.listener = listener
        lastPartial = null
        accumulated.setLength(0)
        stopping = false
        startedAt = System.currentTimeMillis()
        pendingCandidates = candidateComponents()
        if (pendingCandidates.isEmpty()) {
            listener.onError(SpeechRecognizer.ERROR_CLIENT)
            cleanup()
            return
        }
        startNext()
    }

    private fun startNext() {
        if (stopping) return
        // 超过最大总时长则直接收尾交付。
        if (System.currentTimeMillis() - startedAt > maxTotalDurationMs) {
            finish()
            return
        }
        val candidates = pendingCandidates
        if (candidates.isEmpty()) {
            listener?.onError(SpeechRecognizer.ERROR_CLIENT)
            cleanup()
            return
        }
        val component = candidates.first()
        pendingCandidates = candidates.drop(1)
        val created = runCatching {
            if (component != null) {
                SpeechRecognizer.createSpeechRecognizer(context, component)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        }.getOrElse { e ->
            Log.w("SpeechRecognition", "createSpeechRecognizer failed: ${e.message}")
            if (pendingCandidates.isNotEmpty()) {
                startNext()
            } else {
                listener?.onError(SpeechRecognizer.ERROR_CLIENT)
                cleanup()
            }
            return
        }
        recognizer = created
        created.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                this@SpeechRecognition.listener?.onRmsChanged(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    appendSegment(text)
                }
                if (stopping) {
                    finish()
                } else {
                    // 用户仍按住：自动继续听下一段，实现长语音分段累积。
                    continueListening()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    lastPartial = partial
                    this@SpeechRecognition.listener?.onPartialResult(accumulated.toString() + partial)
                }
            }

            override fun onError(error: Int) {
                val hadSpeech = !lastPartial.isNullOrBlank()
                // 已识别到部分内容：把部分结果累积，再决定继续或收尾。
                if (hadSpeech) {
                    appendSegment(lastPartial?.trim().orEmpty())
                }
                val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                if (stopping || !recoverable) {
                    // 松手了，或这是服务级错误：交付已累积的内容并结束。
                    if (accumulated.isNotEmpty() || hadSpeech) {
                        finish()
                    } else {
                        this@SpeechRecognition.listener?.onError(error)
                        cleanup()
                    }
                    return
                }
                // 停顿/超时但用户仍按住：继续下一段。
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY && pendingCandidates.isNotEmpty()) {
                    destroyCurrent()
                    startNext()
                } else {
                    continueListening()
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = buildIntent()
        created.startListening(intent)
    }

    private fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // 放宽静音判定：单段内短暂停顿不被误判为「说完了」。
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
        @Suppress("DEPRECATION")
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
    }

    /** 把一段结果拼接到累积文本，段间用空格分隔。 */
    private fun appendSegment(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (accumulated.isNotEmpty() && accumulated.last() != ' ' && t.first() != ' ') {
            accumulated.append(' ')
        }
        accumulated.append(t)
    }

    /** 复用当前识别器继续听下一段。 */
    private fun continueListening() {
        val r = recognizer
        if (r == null) {
            startNext()
            return
        }
        r.startListening(buildIntent())
    }

    /** 停止监听并触发最终结果回调（松手上屏）。 */
    fun stop() {
        if (recognizer == null) return
        stopping = true
        // 若已有部分结果但识别器迟迟不回调 onResults/onError，直接按累积内容收尾。
        recognizer?.stopListening()
    }

    /** 取消监听，不产生结果（上滑取消）。 */
    fun cancel() {
        stopping = true
        recognizer?.cancel()
        cleanup()
    }

    /** 释放资源。 */
    fun destroy() {
        stopping = true
        cleanup()
    }

    /** 交付累积结果并清理。 */
    private fun finish() {
        val text = accumulated.toString().trim()
        if (text.isNotEmpty()) {
            listener?.onResult(text)
        }
        cleanup()
    }

    private fun destroyCurrent() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun cleanup() {
        recognizer?.destroy()
        recognizer = null
        listener?.onStopped()
        listener = null
        pendingCandidates = emptyList()
        lastPartial = null
        accumulated.setLength(0)
        stopping = false
    }
}
