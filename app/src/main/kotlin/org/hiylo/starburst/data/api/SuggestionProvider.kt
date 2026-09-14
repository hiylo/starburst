/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : SuggestionProvider.kt
 * Date : 2026/09/06
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calls an externally configured OpenAI-compatible LLM provider (e.g. OpenAI, DeepSeek
 * or any v1/chat/completions endpoint) to generate next-step suggestions on the server.
 * Used in preference to the on-device MNN model; the caller falls back when this is
 * unavailable, times out, or returns non-JSON.
 */
@Singleton
class SuggestionProvider @Inject constructor(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    data class Config(
        val baseUrl: String,
        val apiKey: String,
        val model: String,
    )

    /**
     * Thrown when the provider responds with a non-success HTTP status.
     * Carries [status] so callers can surface a friendly, localized message.
     */
    class ProviderHttpException(val status: Int, message: String) : Exception(message)

    /** Returns true when a provider is configured (non-blank URL + model). */
    fun isConfigured(config: Config?): Boolean =
        config != null && config.baseUrl.isNotBlank() && config.model.isNotBlank()

    /**
     * Requests exactly three next-step suggestions from the provider.
     * @throws Exception when the request fails, times out, or the response is unusable.
     */
    suspend fun suggest(
        config: Config,
        systemPrompt: String,
        userContent: String,
    ): List<String> {
        val messages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = userContent),
        )
        val content = postCompletion(config, messages, maxTokens = SUGGESTION_MAX_TOKENS)
        return parseSuggestionJson(content)
    }

    /**
     * Sends a simple single-turn chat message and returns the raw reply text.
     * Used for connection tests, where a natural conversation attempt better
     * validates that the endpoint accepts and answers messages end-to-end.
     * @throws Exception when the request fails or the response is unusable.
     */
    suspend fun chat(config: Config, userMessage: String, maxTokens: Int = CHAT_TEST_MAX_TOKENS): String {
        val messages = listOf(ChatMessage(role = "user", content = userMessage))
        return postCompletion(config, messages, maxTokens = maxTokens)
    }

    /** Posts a chat completion and returns the raw reply content. */
    private suspend fun postCompletion(
        config: Config,
        messages: List<ChatMessage>,
        maxTokens: Int,
    ): String {
        val url = config.baseUrl.trimEnd('/') + "/chat/completions"
        val payload = ChatCompletionRequest(
            model = config.model,
            messages = messages,
            temperature = 0.7,
            maxTokens = maxTokens,
        )
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            if (config.apiKey.isNotBlank()) {
                bearerAuth(config.apiKey)
            }
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            throw ProviderHttpException(
                status = response.status.value,
                message = "LLM provider returned HTTP ${response.status.value}",
            )
        }
        val body = response.body<ChatCompletionResponse>()
        return body.choices.firstOrNull()?.message?.content ?: ""
    }

    private fun parseSuggestionJson(text: String): List<String> {
        val trimmed = text.trim()
        val start = trimmed.indexOf('[')
        val end = trimmed.lastIndexOf(']')
        if (start >= 0 && end > start) {
            val candidate = trimmed.substring(start, end + 1)
            return runCatching {
                json.decodeFromString<List<String>>(candidate)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .take(3)
            }.getOrElse { emptyList() }
        }
        return emptyList()
    }

    private companion object {
        const val SUGGESTION_MAX_TOKENS = 100
        const val CHAT_TEST_MAX_TOKENS = 50
    }
}

@Serializable
internal data class ChatCompletionRequest(
    @SerialName("model") val model: String,
    @SerialName("messages") val messages: List<ChatMessage>,
    @SerialName("temperature") val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int = 100,
)

@Serializable
internal data class ChatMessage(
    @SerialName("role") val role: String,
    @SerialName("content") val content: String,
)

@Serializable
internal data class ChatCompletionResponse(
    @SerialName("choices") val choices: List<ChatChoice> = emptyList(),
)

@Serializable
internal data class ChatChoice(
    @SerialName("message") val message: ChatResponseMessage? = null,
)

@Serializable
internal data class ChatResponseMessage(
    @SerialName("content") val content: String? = null,
)

/**
 * Next-step suggestion prompt for the external LLM provider.
 * Keeps the output constrained to a JSON array.
 */
internal const val SUGGESTION_API_PROMPT =
    "You are a coding assistant. Based on the conversation, suggest 3 concise next actions " +
        "the user could take. Reply with ONLY a JSON array of 3 short strings, no explanation."

/**
 * Recipient prompt (system) requesting a JSON array of suggestions.
 */
internal const val SUGGESTION_API_SYSTEM =
    "You are a helpful coding assistant. You reply only with a valid JSON array of 3 short strings."