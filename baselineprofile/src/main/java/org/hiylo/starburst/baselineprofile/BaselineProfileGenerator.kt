/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : BaselineProfileGenerator.kt
 * Date : 2026/09/22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 采集应用启动热路径并生成 baseline-prof.txt，由 `:baselineprofile:generateBaselineProfile` 触发。
 *
 * 只覆盖冷启动到主界面（会话列表）这一最小集合：启动后的超大工作量（SSE 连接、后台服务、
 * WorkManager 初始化）大多在 Compose 首次帧之后，对「可感知启动耗时」影响有限。
 * 后续如需覆盖 navigable 主流程（会话详情、设置、Workbench）再逐步扩展 collect 遍历。
 *
 * @author Hsi Chu
 * @since 3.1.0
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(packageName = "org.hiylo.starburst") {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg("org.hiylo.starburst").depth(0)), 5_000)
            device.waitForIdle()
            pressHome()
        }
    }
}