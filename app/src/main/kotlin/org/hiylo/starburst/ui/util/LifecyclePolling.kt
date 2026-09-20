/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : LifecyclePolling.kt
 * Date : 2026/09/20 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.starburst.ui.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 在 [lifecycle] 至少处于 STARTED 时才执行 [block] 的轮询辅助。
 *
 * 用于替代 ViewModel 里无差别 `while(isActive) { delay(...); poll() }` 的后台轮询：
 * 界面退到后台（STOP）时挂起轮询，回到前台（STARTED）自动恢复，从而在 App 不可见期间
 * 不再发起网络 / SSH 请求，降低耗电。业务轮询逻辑不变。
 *
 * @param lifecycle 界面生命周期，通常取自 Composable 的 `LocalLifecycleOwner.current.lifecycle`
 * @param block 轮询体；在其中自行 `while(true) { delay(x); poll() }`
 * @return 返回启动的协程 [Job]，由调用方持有以便幂等去重
 */
fun CoroutineScope.launchWhileStarted(
    lifecycle: Lifecycle,
    block: suspend () -> Unit,
): Job = launch {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        block()
    }
}
