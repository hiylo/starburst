/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : OpenCodeApiModels.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import org.hiylo.starburst.domain.model.MessageWithParts
import org.hiylo.starburst.domain.model.ToolRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// ============ Request/Response DTOs ============

@Serializable
data class PromptRequest(
    @SerialName("messageID") val messageId: String,
    val parts: List<PromptPart>,
    val model: ModelSelection? = null,
    val agent: String? = null,
    val variant: String? = null,
    val format: OutputFormat? = null,
    val system: String? = null,
    val noReply: Boolean? = null,
    val tools: Map<String, Boolean>? = null
)

@Serializable
data class ProjectDirectory(
    val directory: String,
    val strategy: String? = null,
)

@Serializable
data class WorkspaceInfo(
    val id: String,
    val type: String,
    val branch: String,
    val name: String? = null,
    val directory: String,
    @SerialName("projectID") val projectId: String,
    val timeUsed: Long,
    val extra: JsonElement? = null,
)

@Serializable
data class V2DataResponse<T>(val data: T)

@Serializable
data class V2PromptRequest(
    val id: String,
    val prompt: V2Prompt,
    val delivery: String = "steer",
    val resume: Boolean = true,
)

@Serializable
data class V2Prompt(
    val text: String,
    val files: List<V2FileAttachment> = emptyList(),
    val agents: List<V2AgentAttachment> = emptyList(),
)

@Serializable
data class V2FileAttachment(
    val uri: String,
    val name: String? = null,
    val description: String? = null,
)

@Serializable
data class V2AgentAttachment(val name: String)

@Serializable
data class V2ModelRef(
    @SerialName("providerID") val providerId: String,
    @SerialName("modelID") val modelId: String,
    val variant: String? = null,
)

@Serializable
data class V2AdmittedPrompt(
    val admittedSeq: Long,
    val id: String,
    @SerialName("sessionID") val sessionId: String,
    val prompt: V2Prompt,
    val delivery: String,
    val timeCreated: Long,
    val promotedSeq: Long? = null,
)

data class MessagePage(
    val messages: List<MessageWithParts>,
    val nextCursor: String?,
)

/** 分享后端 `/api/share/{shareId}/data` 返回的条目；按 `type` 区分 session/message/part 等。 */
@Serializable
data class ShareDataItem(
    val type: String,
    val data: JsonElement,
)

@Serializable
data class PromptPart(
    val type: String,
    val text: String? = null,
    val path: String? = null,
    val mime: String? = null,
    val url: String? = null,
    val filename: String? = null
)

@Serializable
data class ShellRequest(
    val agent: String,
    val model: ModelSelection? = null,
    val command: String
)

@Serializable
data class PtyCreateRequest(
    val title: String? = null,
    val cwd: String? = null
)

@Serializable
data class PtyInfo(
    val id: String,
    val title: String,
    val command: String,
    val args: List<String>,
    val cwd: String,
    val status: String,
    val pid: Int
)

@Serializable
data class PtyUpdateRequest(
    val title: String? = null,
    val size: PtySize? = null
)

@Serializable
data class PtySize(
    val rows: Int,
    val cols: Int
)

@Serializable
data class ModelSelection(
    @SerialName("providerID") val providerId: String,
    @SerialName("modelID") val modelId: String
)

@Serializable
data class OutputFormat(
    val type: String,
    val schema: String? = null
)

@Serializable
data class QuestionReplyBody(
    val answers: List<List<String>>
)

@Serializable
data class SearchMatch(
    val path: String,
    val lines: String,
    val lineNumber: Int,
    val absoluteOffset: Int
)

@Serializable
data class FileContent(
    val type: String,
    val content: String,
    val encoding: String? = null,
    val mimeType: String? = null,
)

@Serializable
data class FileNode(
    val name: String,
    val path: String,
    val type: String,
    val absolute: String? = null,
    val ignored: Boolean = false,
    val size: Long? = null,
    val modified: Long? = null
)

// ============ Permission/Question Request DTOs ============

@Serializable
data class PermissionRequest(
    val id: String,
    @SerialName("sessionID") val sessionId: String,
    val permission: String,
    val patterns: List<String> = emptyList(),
    val metadata: Map<String, JsonElement>? = null,
    val always: List<String> = emptyList(),
    val tool: ToolRef? = null
)

@Serializable
data class McpStatus(
    val status: String,
    val error: String? = null,
)

@Serializable
data class McpAuthStart(
    val authorizationUrl: String,
)

@Serializable
data class QuestionRequest(
    val id: String,
    @SerialName("sessionID") val sessionId: String,
    val questions: List<QuestionInfo>,
    val tool: ToolRef? = null
)

@Serializable
data class QuestionInfo(
    val question: String,
    val header: String,
    val options: List<QuestionOption>,
    val multiple: Boolean = false,
    val custom: Boolean = true
)

@Serializable
data class QuestionOption(
    val label: String,
    val description: String
)

// ============ Provider DTOs ============

@Serializable
data class ProvidersResponse(
    val providers: List<ProviderInfo>,
    val default: Map<String, String> = emptyMap()
)

@Serializable
data class ProviderCatalogResponse(
    val all: List<ProviderInfo>,
    val default: Map<String, String> = emptyMap(),
    val connected: List<String> = emptyList()
)

@Serializable
data class ProviderAuthMethod(
    val type: String,
    val label: String
)

@Serializable
data class ProviderOauthAuthorization(
    val url: String = "",
    val method: String = "none",
    val instructions: String = ""
)

@Serializable
internal data class ProviderOauthAuthorizeRequest(
    val method: Int,
)

@Serializable
internal data class ProviderOauthCallbackRequest(
    val method: Int,
    val code: String? = null,
)

internal class ProviderAuthException(
    val statusCode: Int,
    message: String,
) : Exception(message)

internal fun providerAuthErrorMessage(
    json: Json,
    statusCode: Int,
    body: String,
    fallback: String,
): String {
    val parsed = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
    val data = parsed?.get("data") as? JsonObject
    val error = parsed?.get("error") as? JsonObject
    val errors = parsed?.get("errors") as? JsonArray
    val detail = sequenceOf(
        data?.get("message"),
        error?.get("message"),
        parsed?.get("message"),
        errors?.firstOrNull()?.let { it as? JsonObject }?.get("message"),
    ).mapNotNull { element ->
        runCatching { element?.jsonPrimitive?.contentOrNull }.getOrNull()
    }.firstOrNull { it.isNotBlank() }
        ?: body.takeIf { it.isNotBlank() && !it.trimStart().startsWith("<") }

    val conciseDetail = detail
        ?.lineSequence()
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.removePrefix("Error: ")
        ?.take(240)
    return if (conciseDetail.isNullOrBlank()) {
        "$fallback (HTTP $statusCode)"
    } else {
        "$conciseDetail (HTTP $statusCode)"
    }
}

@Serializable
data class ServerConfigResponse(
    @SerialName("disabled_providers") val disabledProviders: List<String> = emptyList(),
    @SerialName("enabled_providers") val enabledProviders: List<String>? = null,
    val model: String? = null,
    @SerialName("small_model") val smallModel: String? = null,
    @SerialName("default_agent") val defaultAgent: String? = null,
    @SerialName("provider") val provider: Map<String, ProviderConfigDefinition>? = null
)

@Serializable
data class ServerConfigPatch(
    @SerialName("disabled_providers") val disabledProviders: List<String>? = null,
    val model: String? = null,
    @SerialName("small_model") val smallModel: String? = null,
    @SerialName("default_agent") val defaultAgent: String? = null,
    @SerialName("provider") val provider: Map<String, ProviderConfigDefinition>? = null
)

/**
 * 单个自定义服务商的配置定义（来自 /config 顶层 provider 映射）。
 *
 * @author Hsi Chu
 * @since 1.0
 */
@Serializable
data class ProviderConfigDefinition(
    val name: String? = null,
    val npm: String? = null,
    val options: Map<String, JsonElement> = emptyMap(),
    val models: Map<String, ProviderModelDefinition> = emptyMap()
)

/**
 * 自定义服务商下的单个模型定义。
 *
 * @author Hsi Chu
 * @since 1.0
 */
@Serializable
data class ProviderModelDefinition(
    val name: String? = null,
    val limit: ModelLimit? = null
)

@Serializable
data class ProviderInfo(
    val id: String,
    val name: String,
    val source: String = "",
    val env: List<String> = emptyList(),
    val key: String? = null,
    val options: Map<String, JsonElement> = emptyMap(),
    val models: Map<String, ProviderModel> = emptyMap()
)

@Serializable
data class ProviderModel(
    val id: String,
    @SerialName("providerID") val providerId: String = "",
    val name: String,
    val family: String? = null,
    val status: String = "active",
    val capabilities: ModelCapabilities? = null,
    val cost: ModelCost? = null,
    val limit: ModelLimit? = null,
    val variants: Map<String, JsonElement>? = null
)

@Serializable
data class ModelCapabilities(
    val temperature: Boolean = false,
    val reasoning: Boolean = false,
    val attachment: Boolean = false,
    val toolcall: Boolean = false
)

@Serializable
data class ModelCost(
    val input: Double = 0.0,
    val output: Double = 0.0,
    val cache: CacheCost? = null
) {
    @Serializable
    data class CacheCost(
        val read: Double = 0.0,
        val write: Double = 0.0
    )
}

@Serializable
data class ModelLimit(
    val context: Int = 0,
    val input: Int? = null,
    val output: Int = 0
)

// ============ Agent DTOs ============

@Serializable
data class AgentInfo(
    val name: String,
    val description: String? = null,
    val mode: String = "primary", // "primary", "subagent", "all"
    val hidden: Boolean = false,
    val color: String? = null
)

// ============ Command DTOs ============

@Serializable
data class CommandInfo(
    val name: String,
    val description: String? = null,
    val source: String? = null, // "command", "mcp", "skill"
    val hints: List<String> = emptyList()
)

// ============ Server Paths ============

@Serializable
data class ServerPaths(
    val home: String = "",
    val state: String = "",
    val config: String = "",
    val worktree: String = "",
    val directory: String = ""
)
