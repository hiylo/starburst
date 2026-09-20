/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ChatScreenLocals.kt
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */

package org.hiylo.starburst.ui.screens.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.text
import org.hiylo.starburst.domain.model.*
import org.hiylo.starburst.data.api.AgentInfo
import android.webkit.WebView
import androidx.compose.ui.res.stringResource
import org.hiylo.starburst.R
import org.hiylo.starburst.ui.components.AppHaptics
import org.hiylo.starburst.ui.components.AppHapticConfig


// ============ Chat Settings via CompositionLocal ============

/** Chat font size setting: "small", "medium", "large". */
val LocalChatFontSize = compositionLocalOf { "medium" }

/** Chat line spacing multiplier (1.0–2.0). */
val LocalChatLineHeight = compositionLocalOf { 1f }

/** Whether code blocks use word wrap instead of horizontal scroll. */
val LocalCodeWordWrap = compositionLocalOf { false }

/** Whether compact message spacing is enabled. */
val LocalCompactMessages = compositionLocalOf { false }

/** Whether tool cards are collapsed by default. */
val LocalCollapseTools = compositionLocalOf { false }

val LocalExpandReasoning = compositionLocalOf { false }

val LocalShowTurnDividers = compositionLocalOf { true }

/** Whether haptic feedback is enabled. */
val LocalHapticFeedbackEnabled = compositionLocalOf { AppHapticConfig() }

/** Image save request callback available to image preview composables. */
val LocalImageSaveRequest = compositionLocalOf<(ByteArray, String, String?) -> Unit> { { _, _, _ -> } }

/**
 * Chat link handling config, provided around message content.
 * Same-origin links (relative to [serverBaseUrl]) are opened in-app via [openInApp];
 * any other link falls back to the system browser.
 */
internal data class ChatLinkHandler(
    val serverBaseUrl: String = "",
    val openInApp: (String) -> Unit = {},
)

internal val LocalChatLinkHandler = compositionLocalOf { ChatLinkHandler() }

/**
 * Returns true when [link] belongs to the same origin as [serverBaseUrl]
 * (scheme + host + port prefix match). Used to decide whether a conversation
 * link should open inside the built-in WebView instead of the system browser.
 */
internal fun isSameServerUrl(link: String, serverBaseUrl: String): Boolean {
    if (serverBaseUrl.isBlank() || link.isBlank()) return false
    val base = serverBaseUrl.trimEnd('/').lowercase()
    val target = link.trim().lowercase()
    return target == base || target.startsWith("$base/")
}


/**
 * Perform a light haptic tick if haptic feedback is enabled.
 * Call from composable context or from a click lambda that has access to a View.
 */
internal fun performHaptic(view: android.view.View, config: AppHapticConfig) {
    AppHaptics.perform(view, config)
}

/**
 * Agent color matching the TUI's opencode theme.
 * Color cycle: secondary, accent, success, warning, primary, error, info
 * (same order as TUI's local.tsx color array).
 * Fixed palette — tool-specific color, not themed.
 */
private val agentColorCycle = listOf(
    Color(0xFF5C9CF5), // secondary — build (blue)
    Color(0xFF9D7CD8), // accent — plan (purple)
    Color(0xFF7FD88F), // success (green)
    Color(0xFFF5A742), // warning (orange)
    Color(0xFFFAB283), // primary (peach)
    Color(0xFFE06C75), // error (red)
    Color(0xFF56B6C2)  // info (cyan)
)

internal fun agentColor(agentName: String, agents: List<AgentInfo> = emptyList()): Color {
    val index = agents.indexOfFirst { it.name == agentName }
    return if (index >= 0) {
        agentColorCycle[index % agentColorCycle.size]
    } else {
        agentColorCycle[0]
    }
}

/**
 * Conditionally applies horizontalScroll for code blocks.
 * When word wrap is enabled, no horizontal scroll is applied.
 */

/**
 * Slash command definition for the suggestion popup.
 * @param name Command name without the "/" prefix
 * @param description Human-readable description
 * @param type "server" commands are sent via API, "client" commands trigger local actions
 */
internal data class SlashCommand(
    val name: String,
    val description: String?,
    val type: String, // "server", "client", or "custom"
    val prompt: String? = null, // for "custom" commands: text inserted into the input
)

internal enum class ChatInputMode {
    NORMAL,
    SHELL
}

/** Client-side slash commands that mirror the original opencode TUI. */
@Composable
internal fun clientCommands(): List<SlashCommand> {
    return listOf(
        SlashCommand("new", stringResource(R.string.cmd_new), "client"),
        SlashCommand("compact", stringResource(R.string.cmd_compact), "client"),
        SlashCommand("fork", stringResource(R.string.cmd_fork), "client"),
        SlashCommand("share", stringResource(R.string.cmd_share), "client"),
        SlashCommand("unshare", stringResource(R.string.cmd_unshare), "client"),
        SlashCommand("undo", stringResource(R.string.cmd_undo), "client"),
        SlashCommand("redo", stringResource(R.string.cmd_redo), "client"),
        SlashCommand("rename", stringResource(R.string.cmd_rename), "client"),
        SlashCommand("shell", stringResource(R.string.cmd_shell_mode), "client"),
    )
}



