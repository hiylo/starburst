/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : IntelModels.kt
 * Date : 2026/09/22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** 后端 Intel 分析过的项目。 */
@Serializable
data class IntelProject(
    val id: Long,
    val name: String = "",
    val source: String = "",
    val localPath: String = "",
    val gitUrl: String = "",
    val gitRef: String = "",
    val description: String = "",
    val lastTestedSha: String = "",
    val snapshotSha: String = "",
    val commandsJson: String = "",
    val envName: String = "",
    val analysisStatus: String = "",
    val analyzedAt: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

/** 项目内被分析出的模块（kindType：library / application 等）。 */
@Serializable
data class IntelModule(
    val id: Long,
    val projectId: Long,
    val relPath: String = "",
    val kindType: String = "",
    val kindRole: String = "",
    val buildTool: String = "",
    val commandsJson: String = "",
    val summary: String = "",
    val lastTestedSha: String = "",
    val analyzedAt: String? = null,
    val createdAt: String = "",
)

/** 模块内被分析出的功能点。 */
@Serializable
data class IntelFeature(
    val id: Long,
    val projectId: Long,
    val name: String = "",
    val summary: String = "",
    val endsJson: String = "",
    val sortOrder: Int = 0,
    val source: String = "",
    val anchor: String = "",
    val status: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
) {
    /** 解析 [endsJson]（JSON 数组字符串）为端点列表；解析失败返回空列表。 */
    fun ends(): List<String> = parseStringListJson(endsJson)
}

/** 一次测试运行。 */
@Serializable
data class IntelTestRun(
    val id: Long,
    val projectId: Long,
    val moduleId: Long = 0,
    val scope: String = "",
    val kind: String = "",
    val command: String = "",
    val status: String = "",
    val attempts: Int = 0,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val logPath: String = "",
    val progress: String? = null,
    val output: String = "",
    val priority: Int = 0,
    val createdAt: String = "",
)

/** 测试运行中的单条用例结果。 */
@Serializable
data class IntelTestResult(
    val id: Long,
    val runId: Long,
    val projectId: Long,
    val moduleId: Long = 0,
    val caseId: Long = 0,
    val kind: String = "",
    val endpoint: String = "",
    val passed: Boolean = false,
    val failuresJson: String? = null,
    val rootcauseJson: String? = null,
    val createdAt: String = "",
)

/** 测试失败导出的问题记录。 */
@Serializable
data class IntelIssue(
    val id: Long,
    val projectId: Long,
    val moduleId: Long = 0,
    val featureId: Long? = null,
    val key: String = "",
    val kind: String = "",
    val severity: String = "",
    val location: String = "",
    val commitSeen: String? = null,
    val commitFixed: String? = null,
    val status: String = "",
    val resolvedAt: String? = null,
    val lastCheckAt: String? = null,
    val detailJson: String? = null,
    val createdAt: String = "",
)

/** 针对问题的自动修复方案。 */
@Serializable
data class IntelFix(
    val id: Long,
    val projectId: Long,
    val issueId: Long? = null,
    val findingId: Long? = null,
    val kind: String = "",
    val title: String = "",
    val diffJson: String = "",
    val status: String = "",
    val appliedBackup: String? = null,
    val writeMode: String? = null,
    val appliedAt: String? = null,
    val createdAt: String = "",
)

/** 修复 diff 中的单处替换。 */
@Serializable
data class IntelFixSuggestion(
    val file: String = "",
    val oldText: String = "",
    val newText: String = "",
    val line: Int = 0,
    val confidence: String = "",
)

/** 功能点问答记录。 */
@Serializable
data class IntelFeatureChat(
    val id: Long,
    val featureId: Long,
    val projectId: Long,
    val question: String = "",
    val contextJson: String = "",
    val answer: String = "",
    val createdAt: String = "",
)

/** `GET /api/intel/projects` 的响应包装。 */
@Serializable
internal data class IntelProjectsResponse(val projects: List<IntelProject> = emptyList())

/** `GET /api/intel/modules` 的响应包装。 */
@Serializable
internal data class IntelModulesResponse(val modules: List<IntelModule> = emptyList())

/** `GET /api/intel/features` 的响应包装。 */
@Serializable
internal data class IntelFeaturesResponse(val features: List<IntelFeature> = emptyList())

/** `GET /api/intel/runs` 的响应包装。 */
@Serializable
internal data class IntelRunsResponse(val runs: List<IntelTestRun> = emptyList())

/** `GET /api/intel/runs/{runId}/results` 的响应包装。 */
@Serializable
internal data class IntelResultsResponse(val results: List<IntelTestResult> = emptyList())

/** `GET /api/intel/issues` 的响应包装。 */
@Serializable
internal data class IntelIssuesResponse(val issues: List<IntelIssue> = emptyList())

/** `GET /api/intel/fixes` 的响应包装。 */
@Serializable
internal data class IntelFixesResponse(val fixes: List<IntelFix> = emptyList())

/** `GET /api/intel/features/{featureId}/chats` 的响应包装。 */
@Serializable
internal data class IntelChatsResponse(val chats: List<IntelFeatureChat> = emptyList())

/** `GET /api/intel/runs/{runId}` 的响应体（run + 本次结果）。 */
@Serializable
data class IntelRunDetailResponse(
    val run: IntelTestRun,
    val results: List<IntelTestResult> = emptyList(),
)

/** `POST /api/intel/features/{featureId}/chat` 的响应体。 */
@Serializable
data class IntelChatResponse(
    val chat: IntelFeatureChat,
    val mode: String = "",
)

/** `POST /api/intel/run` 的响应体。 */
@Serializable
internal data class IntelRunResponse(val run: IntelTestRun)

/** 无 body POST 的统一成功响应。 */
@Serializable
internal data class IntelOkResponse(val ok: Boolean = false)

/** `POST /api/intel/features/test` 的单个端点结果。 */
@Serializable
data class IntelFeatureRunEndpointResult(
    val method: String,
    val path: String,
    val url: String = "",
    val status: Int = 0,
    val ok: Boolean = false,
)

/** `POST /api/intel/features/test` 的响应体。 */
@Serializable
data class IntelFeatureRunResponse(
    val feature: IntelFeature? = null,
    val baseUrl: String = "",
    val results: List<IntelFeatureRunEndpointResult> = emptyList(),
    val count: Int = 0,
)

/** `POST /api/intel/run` 的请求体。 */
@Serializable
internal data class IntelRunRequest(
    val projectId: Long,
    val moduleId: Long = 0,
    val node: Long? = null,
    val force: Boolean = false,
    val priority: Int? = null,
)

/** `POST /api/intel/features/test` 的请求体。 */
@Serializable
internal data class IntelFeatureRunTestRequest(
    val projectId: Long,
    val featureId: Long,
)

/** `POST /api/intel/issues/{issueId}/link-feature` 的请求体。 */
@Serializable
internal data class IntelLinkFeatureRequest(val featureId: Long)

/** `POST /api/intel/fixes/{fixId}/apply` 的请求体。 */
@Serializable
internal data class IntelFixApplyRequest(val writeMode: String = "file")

/** `POST /api/intel/features/{featureId}/chat` 的请求体。 */
@Serializable
internal data class IntelFeatureChatRequest(
    val projectId: Long,
    val question: String = "",
)

/** 本地 JSON 解析器：忽略未知字段，避免影响主链路反序列化配置。 */
internal val intelJson = Json { ignoreUnknownKeys = true }

/** 安全解析 JSON 数组字符串为字符串列表；解析失败返回空列表。 */
internal fun parseStringListJson(raw: String?): List<String> = try {
    if (raw.isNullOrBlank()) emptyList() else intelJson.decodeFromString<List<String>>(raw)
} catch (e: Exception) {
    emptyList()
}

/** 安全解析修复 diff JSON 数组；解析失败返回空列表。 */
internal fun parseDiffJson(raw: String?): List<IntelFixSuggestion> = try {
    if (raw.isNullOrBlank()) emptyList() else intelJson.decodeFromString<List<IntelFixSuggestion>>(raw)
} catch (e: Exception) {
    emptyList()
}
