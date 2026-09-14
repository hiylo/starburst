/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : LlmProviderSettingsViewModel.kt
 * Date : 2026/09/06
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import org.hiylo.starburst.R
import org.hiylo.starburst.data.api.SuggestionProvider
import org.hiylo.starburst.data.repository.SettingsRepository
import org.hiylo.starburst.data.sync.LocalSyncSecretStore
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URISyntaxException
import java.net.UnknownHostException
import javax.inject.Inject

data class LlmProviderSettingsUiState(
    val baseUrl: String = "",
    val model: String = "",
    val hasApiKey: Boolean = false,
)

/** Result of a provider connection test; message is resolved from string resources. */
data class LlmTestResult(
    val ok: Boolean,
    val message: String,
)

@HiltViewModel
class LlmProviderSettingsViewModel @Inject constructor(
    @ApplicationContext val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val secretStore: LocalSyncSecretStore,
    private val suggestionProvider: SuggestionProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LlmProviderSettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val baseUrl = settingsRepository.llmProviderBaseUrl.first()
            val model = settingsRepository.llmProviderModel.first()
            val hasApiKey = !secretStore.get(LocalSyncSecretStore.SecretKey.LLM_PROVIDER_API_KEY).isNullOrBlank()
            _uiState.value = LlmProviderSettingsUiState(baseUrl, model, hasApiKey)
        }
    }

    suspend fun save(baseUrl: String, model: String, apiKey: String) {
        settingsRepository.setLlmProviderBaseUrl(baseUrl)
        settingsRepository.setLlmProviderModel(model)
        if (apiKey.isNotBlank()) {
            secretStore.put(LocalSyncSecretStore.SecretKey.LLM_PROVIDER_API_KEY, apiKey)
        }
        _uiState.value = LlmProviderSettingsUiState(
            baseUrl = baseUrl,
            model = model,
            hasApiKey = apiKey.isNotBlank() || !secretStore.get(LocalSyncSecretStore.SecretKey.LLM_PROVIDER_API_KEY).isNullOrBlank(),
        )
    }

    /** Calls a minimal completion to validate the configuration. Returns a localized result. */
    suspend fun test(baseUrl: String, model: String, apiKey: String): LlmTestResult {
        if (baseUrl.isBlank() || model.isBlank()) {
            return LlmTestResult(false, appContext.getString(R.string.llm_test_missing))
        }
        val trimmedUrl = baseUrl.trim()
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            return LlmTestResult(false, appContext.getString(R.string.llm_test_bad_scheme))
        }
        val effectiveKey = if (apiKey.isNotBlank()) apiKey
        else secretStore.get(LocalSyncSecretStore.SecretKey.LLM_PROVIDER_API_KEY).orEmpty()
        return runCatching {
            val config = SuggestionProvider.Config(baseUrl = trimmedUrl, apiKey = effectiveKey, model = model)
            // Send a simple single-turn conversation to validate the endpoint end-to-end,
            // and surface the model's actual reply in the success message.
            val reply = suggestionProvider.chat(
                config,
                appContext.getString(R.string.llm_test_probe),
            ).trim().replace('\n', ' ').take(80)
            if (reply.isNotBlank()) {
                LlmTestResult(true, appContext.getString(R.string.llm_test_ok_with, reply))
            } else {
                LlmTestResult(true, appContext.getString(R.string.llm_test_ok))
            }
        }.getOrElse { error -> LlmTestResult(false, friendlyProviderError(error)) }
    }

    /**
     * Maps low-level provider/transport exceptions to friendly, localized messages.
     */
    private fun friendlyProviderError(t: Throwable): String {
        val res = appContext.resources
        return when (t) {
            is SuggestionProvider.ProviderHttpException -> when (t.status) {
                in 401..403 -> res.getString(R.string.llm_test_auth)
                404 -> res.getString(R.string.llm_test_404)
                in 400..499 -> res.getString(R.string.llm_test_4xx, t.status)
                in 500..599 -> res.getString(R.string.llm_test_5xx, t.status)
                else -> res.getString(R.string.llm_test_http, t.status)
            }
            is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException ->
                res.getString(R.string.llm_test_timeout)
            is UnknownHostException -> res.getString(R.string.llm_test_dns)
            is ConnectException -> res.getString(R.string.llm_test_connect)
            is URISyntaxException, is IllegalArgumentException -> res.getString(R.string.llm_test_bad_url)
            is SerializationException -> res.getString(R.string.llm_test_parse)
            else -> res.getString(R.string.llm_test_unknown, t.message ?: "?")
        }
    }
}