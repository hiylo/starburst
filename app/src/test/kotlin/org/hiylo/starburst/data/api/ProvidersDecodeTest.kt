/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ProvidersDecodeTest.kt
 * Date : 2026/09/26 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `/config/providers`（V1）响应解码回归：部分实现 content-type 为 text/html 但响应体为
 * 合法 JSON，且含未知键（api/request）与 provider 自带 models；解码须成功并保留 models。
 */
class ProvidersDecodeTest {

    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun decodesProvidersWithModelsAndUnknownKeys() {
        val body = """
            {"providers":[
              {"id":"litellm","name":"LiteLLM Gateway","source":"config","env":[],
               "options":{"baseURL":"http://example.invalid:4000/v1","reasoning":true},
               "api":{"type":"aisdk","package":"@ai-sdk/openai-compatible"},
               "request":{"headers":{}},
               "models":{
                 "glm-5.2":{"id":"glm-5.2","providerID":"litellm","name":"GLM 5.2","family":"",
                   "capabilities":{"input":["text"],"output":["text"],"toolcall":true},
                   "cost":{"input":0,"output":0,"cache":{"read":0,"write":0}},
                   "limit":{"context":32768,"input":0,"output":4096}},
                 "kimi-k3":{"id":"kimi-k3","providerID":"litellm","name":"Kimi K3","family":""}
               }},
              {"id":"opencode","name":"OpenCode Zen","models":{}}
            ],"default":{}}
        """.trimIndent()
        val resp = json.decodeFromJsonElement<ProvidersResponse>(json.parseToJsonElement(body))
        assertEquals(2, resp.providers.size)
        val litellm = resp.providers.first { it.id == "litellm" }
        assertEquals(2, litellm.models.size)
        assertEquals("GLM 5.2", litellm.models["glm-5.2"]?.name)
        assertEquals(32768, litellm.models["glm-5.2"]?.limit?.context)
        assertEquals(0, resp.providers.first { it.id == "opencode" }.models.size)
    }
}
