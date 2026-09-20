/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatInputBarDisplay.kt
 * Date : 2026/09/20 10:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.ui.screens.chat

/** 底部选择栏中模型显示名的最大字符数，超出后截断并追加省略号，防止挤压右侧上下文预算指示器。 */
internal const val MODEL_LABEL_MAX_CHARS = 24

/** 上下文预算比例达到该值（含）时显示告警色。 */
internal const val CONTEXT_BUDGET_WARNING_RATIO = 0.8

/** 上下文预算比例达到该值（含）时显示错误色。 */
internal const val CONTEXT_BUDGET_CRITICAL_RATIO = 0.9

/**
 * 上下文预算用量占有效窗口的比例。
 *
 * @param estimatedTokens 当前估算的上下文 token 用量。
 * @param effectiveWindow 当前生效的上下文窗口上限，为 0 表示未知。
 * @return 用量占窗口比例，窗口为 0 或未传入时用 0.0 表示未启用预算指示。
 */
internal fun contextBudgetRatio(estimatedTokens: Int, effectiveWindow: Int): Double =
    if (effectiveWindow > 0) estimatedTokens.toDouble() / effectiveWindow else 0.0

/**
 * 将上下文预算比例换算为四舍五入的百分比整数。
 *
 * @param ratio 用量占窗口比例。
 * @return 百分比整数，例如 0.855 返回 86。
 */
internal fun contextBudgetPercentage(ratio: Double): Int = Math.round(ratio * 100).toInt()

/** 上下文预算指示器的告警等级，决定圆环与预算文字的颜色。 */
internal enum class ContextBudgetLevel {
    NORMAL,
    WARNING,
    CRITICAL,
}

/**
 * 判定上下文预算等级：≥ [CONTEXT_BUDGET_CRITICAL_RATIO] 为 CRITICAL，
 * > [CONTEXT_BUDGET_WARNING_RATIO] 为 WARNING，其余为 NORMAL。
 *
 * @param ratio 用量占窗口比例。
 * @return 对应的预算等级。
 */
internal fun contextBudgetLevel(ratio: Double): ContextBudgetLevel = when {
    ratio >= CONTEXT_BUDGET_CRITICAL_RATIO -> ContextBudgetLevel.CRITICAL
    ratio > CONTEXT_BUDGET_WARNING_RATIO -> ContextBudgetLevel.WARNING
    else -> ContextBudgetLevel.NORMAL
}

/**
 * 将模型显示名截断为不超过 [maxChars] 字符的短标签，超出部分以单个省略号表示。
 * 截断点会避开代理对（surrogate pair）中间，避免截出半个 emoji 或生僻字。
 *
 * @param label 原始模型显示名，为空时原样返回。
 * @param maxChars 最大字符数，须为正数。
 * @return 长度不超过 maxChars 且已去尾空白的原始标签，或截断后追加省略号的短标签。
 */
internal fun displayModelLabel(label: String, maxChars: Int = MODEL_LABEL_MAX_CHARS): String {
    if (maxChars <= 0 || label.length <= maxChars) return label
    var cut = maxChars
    if (cut < label.length &&
        Character.isLowSurrogate(label[cut]) &&
        Character.isHighSurrogate(label[cut - 1])
    ) {
        cut--
    }
    return label.substring(0, cut).trimEnd() + "…"
}
