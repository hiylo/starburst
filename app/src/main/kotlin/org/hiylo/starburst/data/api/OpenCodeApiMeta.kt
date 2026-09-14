/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : OpenCodeApiMeta.kt
 * Date : 2026/09/06 15:42:23
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import org.hiylo.starburst.logging.AppLogger as Log
import org.hiylo.starburst.BuildConfig
import org.hiylo.starburst.domain.model.Project
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.TextContent

// ============ Project ============

suspend fun OpenCodeApi.listProjects(conn: ServerConnection): List<Project> {
    return httpClient.get("${conn.baseUrl}/project") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
}

suspend fun OpenCodeApi.getProjectDirectories(
    conn: ServerConnection,
    projectId: String,
    directory: String? = null,
    workspaceId: String? = null,
): List<ProjectDirectory> = httpClient.get("${conn.baseUrl}/project/$projectId/directories") {
    conn.authHeader?.let { header("Authorization", it) }
    directory?.let { parameter("directory", it) }
    workspaceId?.let { parameter("workspace", it) }
}.body()

suspend fun OpenCodeApi.listWorkspaces(
    conn: ServerConnection,
    directory: String,
    workspaceId: String? = null,
): List<WorkspaceInfo> = httpClient.get("${conn.baseUrl}/experimental/workspace") {
    conn.authHeader?.let { header("Authorization", it) }
    parameter("directory", directory)
    workspaceId?.let { parameter("workspace", it) }
}.body()

suspend fun OpenCodeApi.getCurrentProject(conn: ServerConnection): Project {
    return httpClient.get("${conn.baseUrl}/project/current") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
}

// ============ Agents ============

/**
 * List available agents (build, plan, etc.).
 * GET /agent
 * Returns agents filtered to primary/visible ones for the mode selector.
 */
suspend fun OpenCodeApi.listAgents(conn: ServerConnection): List<AgentInfo> {
    return httpClient.get("${conn.baseUrl}/agent") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
}

// ============ Permissions ============

/**
 * Reply to a permission request.
 * POST /permission/{requestID}/reply
 * Body: { reply: "once" | "always" | "reject", message?: string }
 */
suspend fun OpenCodeApi.replyToPermission(
    conn: ServerConnection,
    requestId: String,
    reply: String, // "once", "always", or "reject"
    message: String? = null,
    directory: String? = null
): Boolean {
    val body = buildMap<String, String> {
        put("reply", reply)
        message?.let { put("message", it) }
    }
    val result = httpClient.post("${conn.baseUrl}/permission/$requestId/reply") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { header("x-starburst-directory", it) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    return result.status.isSuccess()
}

/**
 * List pending permission requests.
 * GET /permission
 */
suspend fun OpenCodeApi.listPendingPermissions(conn: ServerConnection, directory: String? = null): List<PermissionRequest> {
    return httpClient.get("${conn.baseUrl}/permission") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { header("x-starburst-directory", it) }
    }.body()
}

// ============ MCP ============

suspend fun OpenCodeApi.getMcpStatus(conn: ServerConnection): Map<String, McpStatus> {
    val response = httpClient.get("${conn.baseUrl}/mcp") {
        conn.authHeader?.let { header("Authorization", it) }
    }
    if (!response.status.isSuccess()) throw RuntimeException("MCP status failed: ${response.status}")
    return response.body()
}

suspend fun OpenCodeApi.connectMcp(conn: ServerConnection, name: String): Boolean =
    updateMcpConnection(conn, name, connect = true)

suspend fun OpenCodeApi.disconnectMcp(conn: ServerConnection, name: String): Boolean =
    updateMcpConnection(conn, name, connect = false)

suspend fun OpenCodeApi.startMcpAuth(conn: ServerConnection, name: String): McpAuthStart {
    val response = httpClient.post("${conn.baseUrl}/mcp/${name.encodeURLPathPart()}/auth") {
        conn.authHeader?.let { header("Authorization", it) }
    }
    if (!response.status.isSuccess()) throw RuntimeException("MCP authentication failed: ${response.status}")
    return response.body()
}

// ============ Questions ============

/**
 * Reply to a question request.
 * POST /question/{requestID}/reply
 * Body: { answers: string[][] }
 */
suspend fun OpenCodeApi.replyToQuestion(
    conn: ServerConnection,
    requestId: String,
    answers: List<List<String>>,
    directory: String? = null
): Boolean {
    val url = "${conn.baseUrl}/question/$requestId/reply"
    val bodyJson = json.encodeToString(QuestionReplyBody.serializer(), QuestionReplyBody(answers = answers))
    val result = httpClient.post(url) {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { header("x-starburst-directory", it) }
        setBody(TextContent(bodyJson, ContentType.Application.Json))
    }
    Log.i(
        OpenCodeApi.TAG,
        "Question reply: request=$requestId answers=${answers.size} status=${result.status.value}",
    )
    return result.status.isSuccess()
}

/**
 * Reject a question request.
 * POST /question/{requestID}/reject
 */
suspend fun OpenCodeApi.rejectQuestion(
    conn: ServerConnection,
    requestId: String,
    directory: String? = null
): Boolean {
    val url = "${conn.baseUrl}/question/$requestId/reject"
    val result = httpClient.post(url) {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { header("x-starburst-directory", it) }
    }
    Log.i(OpenCodeApi.TAG, "Question reject: request=$requestId status=${result.status.value}")
    return result.status.isSuccess()
}

/**
 * List pending question requests.
 * GET /question
 */
suspend fun OpenCodeApi.listPendingQuestions(conn: ServerConnection, directory: String? = null): List<QuestionRequest> {
    val response = httpClient.get("${conn.baseUrl}/question") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { header("x-starburst-directory", it) }
    }
    Log.i(OpenCodeApi.TAG, "Pending questions request: status=${response.status.value}")
    return response.body()
}

// ============ Config / Providers ============

/**
 * Get available providers and models.
 * GET /config/providers
 */
suspend fun OpenCodeApi.getProviders(conn: ServerConnection): ProvidersResponse {
    return httpClient.get("${conn.baseUrl}/config/providers") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
}

/**
 * Get provider catalog with connection status.
 * GET /provider
 */
suspend fun OpenCodeApi.listProviderCatalog(conn: ServerConnection): ProviderCatalogResponse {
    return httpClient.get("${conn.baseUrl}/provider") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
}

/**
 * Get available auth methods for providers.
 * GET /provider/auth
 */
suspend fun OpenCodeApi.getProviderAuthMethods(conn: ServerConnection): Map<String, List<ProviderAuthMethod>> {
    return httpClient.get("${conn.baseUrl}/provider/auth") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
}

/**
 * Start OAuth authorization for a provider.
 * POST /provider/{providerID}/oauth/authorize
 */
suspend fun OpenCodeApi.authorizeProviderOauth(
    conn: ServerConnection,
    providerId: String,
    methodIndex: Int
): ProviderOauthAuthorization? {
    val response = httpClient.post("${conn.baseUrl}/provider/$providerId/oauth/authorize") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(ProviderOauthAuthorizeRequest(method = methodIndex))
    }
    val body = response.bodyAsText().trim()
    if (BuildConfig.DEBUG) {
        Log.d("OpenCodeApi", "authorizeProviderOauth: status=${response.status}")
    }

    if (!response.status.isSuccess()) {
        throw ProviderAuthException(
            response.status.value,
            providerAuthErrorMessage(json, response.status.value, body, "Failed to start OAuth"),
        )
    }
    if (body.isBlank() || body == "null") return ProviderOauthAuthorization()

    return runCatching {
        json.decodeFromString(ProviderOauthAuthorization.serializer(), body)
    }.getOrElse {
        // Some server builds return an empty object for headless mode.
        ProviderOauthAuthorization()
    }
}

/**
 * Complete OAuth authorization for a provider.
 * POST /provider/{providerID}/oauth/callback
 */
suspend fun OpenCodeApi.completeProviderOauth(
    conn: ServerConnection,
    providerId: String,
    methodIndex: Int,
    code: String? = null
): Boolean {
    val body = ProviderOauthCallbackRequest(method = methodIndex, code = code)
    if (BuildConfig.DEBUG) {
        Log.d(OpenCodeApi.TAG, "completeProviderOauth: POST /provider/$providerId/oauth/callback method=$methodIndex hasCode=${code != null}")
    }
    val response = httpClient.post("${conn.baseUrl}/provider/$providerId/oauth/callback") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }
    val responseBody = response.bodyAsText().trim()
    if (BuildConfig.DEBUG) Log.d(OpenCodeApi.TAG, "completeProviderOauth: status=${response.status}")
    if (!response.status.isSuccess()) {
        throw ProviderAuthException(
            response.status.value,
            providerAuthErrorMessage(json, response.status.value, responseBody, "Failed to complete OAuth"),
        )
    }
    return true
}

/**
 * Set API key auth for provider.
 * PUT /auth/{providerID}
 */
suspend fun OpenCodeApi.setProviderApiKey(conn: ServerConnection, providerId: String, apiKey: String): Boolean {
    val response = httpClient.put("${conn.baseUrl}/auth/$providerId") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(mapOf("type" to "api", "key" to apiKey))
    }
    return response.status.isSuccess()
}

/**
 * Remove stored auth for provider.
 * DELETE /auth/{providerID}
 */
suspend fun OpenCodeApi.removeProviderAuth(conn: ServerConnection, providerId: String): Boolean {
    if (BuildConfig.DEBUG) Log.d(OpenCodeApi.TAG, "removeProviderAuth: request")
    val response = httpClient.delete("${conn.baseUrl}/auth/$providerId") {
        conn.authHeader?.let { header("Authorization", it) }
    }
    if (BuildConfig.DEBUG) {
        Log.d(OpenCodeApi.TAG, "removeProviderAuth: status=${response.status}")
    }
    return response.status.isSuccess()
}

/**
 * Get current server config.
 * GET /config
 */
suspend fun OpenCodeApi.getConfig(conn: ServerConnection): ServerConfigResponse {
    return httpClient.get("${conn.baseUrl}/config") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
}

/**
 * Get global server config.
 * GET /global/config
 */
suspend fun OpenCodeApi.getGlobalConfig(conn: ServerConnection): ServerConfigResponse {
    return httpClient.get("${conn.baseUrl}/global/config") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
}

/**
 * Patch server config.
 * PATCH /config
 */
suspend fun OpenCodeApi.updateConfig(conn: ServerConnection, patch: ServerConfigPatch): ServerConfigResponse {
    return httpClient.patch("${conn.baseUrl}/config") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(patch)
    }.body()
}

/**
 * Patch global server config.
 * PATCH /global/config
 */
suspend fun OpenCodeApi.updateGlobalConfig(conn: ServerConnection, patch: ServerConfigPatch): ServerConfigResponse {
    return httpClient.patch("${conn.baseUrl}/global/config") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(patch)
    }.body()
}

/**
 * Patch custom provider configuration.
 * PATCH /config with only the provider map in the body.
 */
suspend fun OpenCodeApi.updateProviderConfig(
    conn: ServerConnection,
    provider: Map<String, ProviderConfigDefinition>,
): ServerConfigResponse {
    return httpClient.patch("${conn.baseUrl}/config") {
        conn.authHeader?.let { header("Authorization", it) }
        contentType(ContentType.Application.Json)
        setBody(ServerConfigPatch(provider = provider))
    }.body()
}

/**
 * Dispose global instances and force provider/auth state refresh.
 * POST /global/dispose
 */
suspend fun OpenCodeApi.disposeGlobal(conn: ServerConnection): Boolean {
    val response = httpClient.post("${conn.baseUrl}/global/dispose") {
        conn.authHeader?.let { header("Authorization", it) }
    }
    return response.status.isSuccess()
}

// ============ Commands ============

/**
 * List available slash commands.
 * GET /command
 */
suspend fun OpenCodeApi.listCommands(conn: ServerConnection): List<CommandInfo> {
    return httpClient.get("${conn.baseUrl}/command") {
        conn.authHeader?.let { header("Authorization", it) }
    }.body()
}

// ============ Session Agent / Model switch ============

suspend fun OpenCodeApi.switchSessionAgentV2(
    conn: ServerConnection,
    sessionId: String,
    agent: String,
    directory: String? = null,
    workspaceId: String? = null,
) {
    val response = httpClient.post("${conn.baseUrl}/api/session/$sessionId/agent") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
        workspaceId?.let { parameter("workspace", it) }
        contentType(ContentType.Application.Json)
        setBody(mapOf("agent" to agent))
    }
    if (!response.status.isSuccess()) throw RuntimeException("V2 agent switch failed: ${response.status}")
}

suspend fun OpenCodeApi.switchSessionModelV2(
    conn: ServerConnection,
    sessionId: String,
    model: ModelSelection,
    variant: String? = null,
    directory: String? = null,
    workspaceId: String? = null,
) {
    val response = httpClient.post("${conn.baseUrl}/api/session/$sessionId/model") {
        conn.authHeader?.let { header("Authorization", it) }
        directory?.let { parameter("directory", it) }
        workspaceId?.let { parameter("workspace", it) }
        contentType(ContentType.Application.Json)
        setBody(V2ModelRef(model.providerId, model.modelId, variant))
    }
    if (!response.status.isSuccess()) throw RuntimeException("V2 model switch failed: ${response.status}")
}