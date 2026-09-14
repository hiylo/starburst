/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : AsrSession.kt
 * Date : 2026/09/12 11:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ml

/**
 * 录音 + 流式语音识别会话的统一抽象。
 *
 * 端侧 [MnnAsrRecorder]（MNN 模型）与 [ServerAsrRecorder]（后端代理 NAS 上的
 * sherpa-onnx 引擎）都实现它，界面层无需区分识别在哪一端完成：同一样本格式、
 * 同样「按住说话、松手上屏、上滑取消」的交互。
 *
 * @author Hsi Chu
 * @since V1.3.0
 */
interface AsrSession {

    /** 识别过程回调，均在主线程触发。 */
    interface Listener {
        /** 录音已真正开始（麦克风已打开）。 */
        fun onStart()

        /** 当前累积识别文本，用于实时上屏。 */
        fun onPartialResult(text: String)

        /** 识别失败，[message] 为可读原因。 */
        fun onError(message: String)

        /** 会话结束（松手正常结束与取消共用）。 */
        fun onStopped()
    }

    /**
     * 开始录音识别。需调用方已持有 RECORD_AUDIO 权限。
     *
     * @return true 表示已开始；false 表示无法开始，此时已回调 [Listener.onError]
     */
    suspend fun start(listener: Listener): Boolean

    /** 松手：收尾并回调最终文本与 [Listener.onStopped]。 */
    suspend fun stop()

    /** 上滑取消：丢弃会话，不产生文本，回调 [Listener.onStopped]。 */
    suspend fun cancel()
}
