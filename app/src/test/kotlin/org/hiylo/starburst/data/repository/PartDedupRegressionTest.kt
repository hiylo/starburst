/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : PartDedupRegressionTest.kt
 * Date : 2026/09/27 09:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.repository

import org.hiylo.starburst.domain.model.Part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归「同一条内容显示两遍」的三条残留路径。
 *
 * 1. `handleMessagePartUpdated` 无条件把 `pendingDeltas` append 到权威全量文本上；
 *    agent 的 part.updated 往往已含这些字符 → 结尾重复。
 * 2. `mergeLoadedParts` 不折叠 loaded 内部的同 id 重复条目。
 * 3. `V2ContentItem.toPart` 对工具 part 用序号兜底 id，与流式侧的 `id = callId` 对不上。
 */
class PartDedupRegressionTest {

    private fun text(id: String, text: String) =
        Part.Text(id = id, sessionId = "ses_1", messageId = "msg_1", text = text)

    @Test
    fun partTextEndsWith_detectsAuthoritativeTextAlreadyContainsBufferedDelta() {
        val part = text("prt_1", "hello world")
        // 权威全量已包含早到的 delta → 不应再补
        assertTrue(partTextEndsWith(part, "world"))
        assertTrue(partTextEndsWith(part, "hello world"))
        // 权威全量尚未包含 → 需要补
        assertFalse(partTextEndsWith(part, "!!"))
        // 空 delta 视为已包含（无需处理）
        assertTrue(partTextEndsWith(part, ""))
    }

    @Test
    fun partTextEndsWith_emptyAuthoritativeTextNeedsMerge() {
        // 流式刚开始、全量为空时，早到的 delta 必须补上，否则整段丢失
        assertFalse(partTextEndsWith(text("prt_1", ""), "abc"))
    }

    @Test
    fun partTextEndsWith_nonTextPartIsNeverConsideredCovered() {
        val patch = Part.Patch(id = "prt_p", sessionId = "ses_1", messageId = "msg_1", hash = "h")
        assertFalse(partTextEndsWith(patch, "x"))
    }

    @Test
    fun partTextEndsWith_reasoningAlsoCounts() {
        val reasoning = Part.Reasoning(id = "prt_r", sessionId = "ses_1", messageId = "msg_1", text = "thinking hard")
        assertTrue(partTextEndsWith(reasoning, "hard"))
        assertFalse(partTextEndsWith(reasoning, "thinking hard!"))
    }
}
