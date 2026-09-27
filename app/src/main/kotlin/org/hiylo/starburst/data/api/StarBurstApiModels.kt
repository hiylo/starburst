/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : StarBurstApiModels.kt
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
import kotlinx.serialization.json.doubleOrNull
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
    // 默认值经真值源 1.18.30 实测：queue=新起一轮；resume=false=不续跑/不排空
    // （resume:true 会在工具调用流式时触发服务端 Failed to drain Session 崩溃）。
    val delivery: String = "queue",
    val resume: Boolean = false,
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
    // 真值源服务端的 ModelRef 字段名是 `id`（非 modelID），
    // 这里用 @SerialName 对齐线上契约，属性名保持内部语义不变。
    @SerialName("id") val modelId: String,
    val variant: String? = null,
)

/** `POST /api/session/{id}/model` 的请求体：`{"model":{"id","providerID","variant"}}`。 */
@Serializable
data class V2ModelSwitchBody(val model: V2ModelRef)

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
    @SerialName("action") val permission: String = "",
    @SerialName("resources") val patterns: List<String> = emptyList(),
    val metadata: Map<String, JsonElement>? = null,
    @SerialName("save") val always: List<String> = emptyList(),
    @SerialName("source") val tool: ToolRef? = null
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
    val limit: ModelLimit? = null,
    /**
     * 模型计费信息。**形状随协议而变**，故用原始 [JsonElement] 接收：
     *  - V1（`/config/providers`、`/provider`）：对象 `{"input":n,"output":n}`
     *  - V2（`/api/model`、`/api/provider`）：**数组**（opencode 与 agent 实测均为 `[]`）
     *
     * 与 [variants] 同源问题：声明为对象时 V2 载荷会抛解码异常并拖垮整个列表。
     * 取用请走 [costInput] / [costOutput]。
     */
    val cost: JsonElement? = null,
    /**
     * 模型变体表。**形状随协议而变**，故用原始 [JsonElement] 接收：
     *  - V1（`/config/providers`、`/provider`）：对象，如 `{"low":{"reasoningEffort":"low"}}`
     *  - V2（`/api/model`、`/api/provider`）：**数组**，opencode 与 agent 实测均为 `[]`
     *
     * 此前声明为 `Map<String, JsonElement>`，遇到 V2 载荷的数组会直接抛解码异常，
     * 导致整个 provider 列表解码失败（`listV2ProviderList` 静默回退为空，
     * 服务商名退化成 providerID、连接状态丢失）。取用请走 [variantKeys]。
     */
    val variants: JsonElement? = null
)

/**
 * 变体名称列表（保持服务端给定顺序，对齐 Web UI）。
 *
 * 对象形态取其 key；数组形态（V2）不具名，返回空列表——与该形态下无可选变体一致。
 */
internal val ProviderModel.variantKeys: List<String>
    get() = (variants as? kotlinx.serialization.json.JsonObject)?.keys?.toList().orEmpty()

/**
 * 输入侧单价（每百万 token）。V1 对象形态取 `input`；V2 数组形态无可解析字段，返回 0。
 * 0 表示「无计费信息」——用于 `hasPaidModels` 判定（是否含付费模型）。
 */
internal val ProviderModel.costInput: Double
    get() = (cost as? kotlinx.serialization.json.JsonObject)
        ?.get("input")?.jsonPrimitive?.doubleOrNull ?: 0.0

/** 输出侧单价（每百万 token），形状处理同 [costInput]。 */
internal val ProviderModel.costOutput: Double
    get() = (cost as? kotlinx.serialization.json.JsonObject)
        ?.get("output")?.jsonPrimitive?.doubleOrNull ?: 0.0

/**
 * 模型能力。**字段形状随协议而变**，两种都要认：
 *
 * | 键 | V1（`/config/providers`、`/provider`） | V2（`/api/model`、`/api/provider`） |
 * |---|---|---|
 * | 工具调用 | `toolcall: bool` | `tools: bool` |
 * | 附件（图片/PDF…） | `attachment: bool` | 无该键，靠 `input` 模态推导 |
 * | 输入/输出模态 | `input`/`output` 为**对象**（取 key） | `input`/`output` 为**数组** |
 *
 * V2 侧（opencode 与 agent 实测一致）**完全没有** `toolcall`/`reasoning`/`attachment`
 * 布尔键，若按 V1 声明解码，V2 模型会被一律判为「不支持工具调用/推理/附件」，
 * 模型选择器与模型筛选页一个能力徽章都不显示。故 raw 字段收下全部形态，
 * 对外用 [toolcall] / [attachment] 计算属性统一口径（调用方无需区分协议）。
 */
@Serializable
data class ModelCapabilities(
    val temperature: Boolean = false,
    val reasoning: Boolean = false,
    @SerialName("attachment") val attachmentRaw: Boolean = false,
    @SerialName("toolcall") val toolcallRaw: Boolean = false,
    // V2 形态
    val tools: Boolean = false,
    val input: JsonElement? = null,
    val output: JsonElement? = null,
) {
    /** 支持工具调用：V1 的 `toolcall` 或 V2 的 `tools`，任一为真即可。 */
    val toolcall: Boolean get() = toolcallRaw || tools

    /** 支持附件：V1 的 `attachment`，或 V2 的 `input` 模态里含图片/文档类模态。 */
    val attachment: Boolean get() = attachmentRaw || modalities(input).any { it in ATTACHMENT_MODALITIES }

    private companion object {
        /** 视为「可作为附件输入」的模态。 */
        val ATTACHMENT_MODALITIES = setOf("image", "pdf", "audio", "video")
    }
}

/** 提取模态名：V1 对象取其 key，V2 数组取其字符串元素；其他形态返回空。 */
internal fun modalities(element: JsonElement?): List<String> = when (element) {
    is kotlinx.serialization.json.JsonObject -> element.keys.toList()
    is kotlinx.serialization.json.JsonArray -> element.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
    else -> emptyList()
}

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

/** V2 `/api/model` 目录条目（starburst-agent 等 V2 后端无 `/config/providers` 时的兜底数据源）。 */
@Serializable
data class V2ModelInfo(
    val id: String,
    @SerialName("providerID") val providerId: String = "",
    val name: String = "",
    val family: String? = null,
    val status: String = "active",
    val enabled: Boolean = true,
    val capabilities: Map<String, JsonElement>? = null,
    val cost: List<ModelCost> = emptyList(),
    val limit: Map<String, JsonElement>? = null
)

// ============ Agent DTOs ============

@Serializable
data class AgentInfo(
    val name: String? = null,
    val id: String? = null,
    val description: String? = null,
    val mode: String = "primary", // "primary", "subagent", "all"
    val hidden: Boolean = false,
    val color: String? = null
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: id ?: "agent"
}

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
