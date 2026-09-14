/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SessionExport.kt
 * Date : 2026/09/09 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.settings

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hiylo.starburst.domain.model.Part
import org.hiylo.starburst.domain.model.ToolState
import org.hiylo.starburst.ui.screens.chat.ChatMessage

/**
 * Serializes a conversation (session title + flattened messages) into Markdown or JSON.
 * Each message exposes its text, reasoning and tool outputs for portable export.
 */
object SessionExport {

    @Serializable
    data class ExportedSession(
        val title: String,
        val messages: List<ExportedMessage>,
    )

    @Serializable
    data class ExportedMessage(
        val role: String,
        val text: String = "",
        val reasoning: String = "",
        val tools: List<ExportedTool> = emptyList(),
    )

    @Serializable
    data class ExportedTool(
        val tool: String,
        val status: String,
        val output: String = "",
    )

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Exports the session as a Markdown document.
     */
    fun toMarkdown(title: String, messages: List<ChatMessage>): String {
        val builder = StringBuilder()
        builder.append("# ").append(title.ifBlank { "Chat" }).append('\n').append('\n')
        messages.forEach { message ->
            builder.append("## ").append(if (message.isUser) "User" else "Assistant").append('\n').append('\n')
            val exported = toExportedMessage(message)
            if (exported.reasoning.isNotBlank()) {
                builder.append("*Reasoning:*\n\n").append(exported.reasoning).append('\n').append('\n')
            }
            if (exported.text.isNotBlank()) {
                builder.append(exported.text).append('\n').append('\n')
            }
            exported.tools.forEach { tool ->
                builder.append("**Tool** `").append(tool.tool).append("` (*").append(tool.status).append("*):\n\n")
                if (tool.output.isNotBlank()) {
                    builder.append("```\n").append(tool.output).append("\n```\n\n")
                }
            }
        }
        return builder.toString()
    }

    /**
     * Exports the session as a pretty-printed JSON document.
     */
    fun toJson(title: String, messages: List<ChatMessage>): String {
        val exported = ExportedSession(
            title = title.ifBlank { "Chat" },
            messages = messages.map(::toExportedMessage),
        )
        return json.encodeToString(exported)
    }

    private fun toExportedMessage(message: ChatMessage): ExportedMessage {
        val text = message.parts.filterIsInstance<Part.Text>()
            .map { it.text }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        val reasoning = message.parts.filterIsInstance<Part.Reasoning>()
            .map { it.text }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        val tools = message.parts.filterIsInstance<Part.Tool>().map { part ->
            val (status, output) = when (val state = part.state) {
                is ToolState.Completed -> "completed" to state.output
                is ToolState.Error -> "error" to state.error
                is ToolState.Running -> "running" to (state.title ?: "")
                is ToolState.Pending -> "pending" to ""
            }
            ExportedTool(tool = part.tool, status = status, output = output)
        }
        return ExportedMessage(
            role = if (message.isUser) "user" else "assistant",
            text = text,
            reasoning = reasoning,
            tools = tools,
        )
    }
}
