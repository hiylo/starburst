/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : StarBurst
 * File : ProviderCatalogFallbackTest.kt
 * Date : 2026/09/27 02:40:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 * Version : V1.0
 */
package org.hiylo.starburst.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.hiylo.starburst.data.api.ProviderInfo
import org.hiylo.starburst.data.api.costInput
import org.hiylo.starburst.data.api.costOutput
import org.hiylo.starburst.data.api.ProviderModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 回归「服务商没有和 opencode 一致」。
 *
 * 成因：服务商页只取 V2 `/api/provider`，而 opencode 1.18.30 只提供 V1
 * `/config/providers`（V2 路径 404）→ 页面空白。现两端都要试。
 *
 * 这里锁定 V1 响应体的解码形状（真实抓自 opencode：`{"providers":[{id,name,source,env,models…}]}`）。
 */
class ProviderCatalogFallbackTest {

    /** 与 `NetworkModule.provideJson()` 同配置（单测不跑 Hilt，直接构造）。 */
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun v1ProvidersResponse_decodesRealOpencodeShape() {
        val body = """
            {"providers":[
              {"id":"litellm","name":"LiteLLM Gateway","source":"config","env":[],
               "models":{"glm-5.2":{"id":"glm-5.2","name":"glm-5.2"}}}
            ],"default":{"litellm":"glm-5.2"}}
        """.trimIndent()

        val parsed = json.decodeFromJsonElement<ProvidersResponse>(json.parseToJsonElement(body))

        assertEquals(1, parsed.providers.size)
        val provider = parsed.providers.first()
        assertEquals("litellm", provider.id)
        assertEquals("LiteLLM Gateway", provider.name)
        assertEquals(1, provider.models.size)
        assertTrue(provider.models.containsKey("glm-5.2"))
        assertEquals("glm-5.2", parsed.default["litellm"])
    }

    @Test
    fun v2ProviderList_decodesAgentShapeWithModels() {
        val body = """
            {"data":[
              {"id":"litellm","name":"LiteLLM","models":{
                 "glm-5.2":{"id":"glm-5.2","name":"glm-5.2"},
                 "kimi-k3":{"id":"kimi-k3","name":"kimi-k3"}}}
            ]}
        """.trimIndent()

        val list = json.decodeFromJsonElement<List<ProviderInfo>>(
            json.parseToJsonElement(body).jsonObject.getValue("data"),
        )

        assertEquals(1, list.size)
        assertEquals("LiteLLM", list.first().name)
        assertEquals(2, list.first().models.size)
    }

    @Test
    fun emptyV1Providers_doesNotCrashDecode() {
        val parsed = json.decodeFromJsonElement<ProvidersResponse>(
            json.parseToJsonElement("""{"providers":[]}"""),
        )
        assertTrue(parsed.providers.isEmpty())
    }

    @Test
    fun merge_v1ConfiguredAndV2Known_areUnionedSoNeitherIsLost() {
        // 真实形态（抓自 opencode 100.66.1.1:4096）：
        //  V1 /config/providers = litellm / opencode / opencode-go / llama-4060ti（仅已配置）
        //  V2 /api/provider     = opencode / openai / litellm / llama-4060ti（含内置未配置）
        // 只取 V2 会漏 opencode-go；只取 V1 会漏 openai。
        val v1 = listOf(
            provider("litellm", "LiteLLM Gateway", source = "config", models = listOf("local-qwen3.8-27b")),
            provider("opencode", "OpenCode Zen", source = "config", models = listOf("big-pickle")),
            provider("opencode-go", "OpenCode Go", source = "api", models = listOf("gpt-5.6-luna")),
            provider("llama-4060ti", "llama-4060ti", source = "config", models = listOf("Ornith-1.5-9B")),
        )
        val v2 = listOf(
            provider("opencode", "OpenCode Zen"),
            provider("openai", "OpenAI"),
            provider("litellm", "LiteLLM Gateway"),
            provider("llama-4060ti", "llama-4060ti"),
        )

        val merged = mergeProviderEntries(v1, v2)

        assertEquals(
            listOf("litellm", "opencode", "opencode-go", "llama-4060ti", "openai"),
            merged.map { it.id },
        )
        // V1 条目优先：名称与 source 取 V1，模型表也用 V1 的。
        val goEntry = merged.first { it.id == "opencode-go" }
        assertEquals("OpenCode Go", goEntry.name)
        assertEquals("api", goEntry.source)
        assertEquals(1, goEntry.models.size)
        // V2 独有的内置 openai 也在列表里。
        assertEquals("OpenAI", merged.first { it.id == "openai" }.name)
    }

    @Test
    fun merge_v1EntryWinsButV2FillsMissingModels() {
        val v1 = listOf(provider("litellm", "", source = "config", models = emptyList()))
        val v2 = listOf(provider("litellm", "LiteLLM", models = listOf("glm-5.2")))

        val merged = mergeProviderEntries(v1, v2)

        val entry = merged.single()
        assertEquals("LiteLLM", entry.name)
        assertEquals("config", entry.source)
        assertEquals(1, entry.models.size)
    }

    @Test
    fun merge_v2Only_stillListsAgentProviders() {
        val merged = mergeProviderEntries(emptyList(), listOf(provider("litellm", "LiteLLM", models = listOf("glm-5.2"))))
        assertEquals(listOf("litellm"), merged.map { it.id })
    }

    private fun provider(
        id: String,
        name: String,
        source: String = "",
        models: List<String> = emptyList(),
    ) = ProviderInfo(
        id = id,
        name = name,
        source = source,
        models = models.associateWith { ProviderModel(id = it, name = it) },
    )

    @Test
    fun v1ProviderRegistry_decodesAllDefaultConnected() {
        // 真实形态（抓自 opencode 1.18.30 GET /provider）：顶层 {all, default, connected}。
        // all 含全部内置服务商（实测 225 个）+ 已配置的；connected 只含已配置项。
        val body = """
            {"all":[
              {"id":"anthropic","name":"Anthropic","source":"custom","env":["ANTHROPIC_API_KEY"],
               "models":{"claude-sonnet-4-6":{"id":"claude-sonnet-4-6","name":"Claude Sonnet 4.6"}}},
              {"id":"groq","name":"Groq","source":"custom","env":["GROQ_API_KEY"],
               "models":{"kimi-k3":{"id":"kimi-k3","name":"Kimi K3"}}},
              {"id":"litellm","name":"LiteLLM Gateway","source":"config","env":[],
               "models":{"local-qwen3.8-27b":{"id":"local-qwen3.8-27b","name":"Qwen3.8"}}}
            ],
            "default":{"anthropic":"claude-sonnet-4-6","groq":"kimi-k3","litellm":"local-qwen3.8-27b"},
            "connected":["litellm"]}
        """.trimIndent()

        val root = json.parseToJsonElement(body).jsonObject
        val all = json.decodeFromJsonElement<List<ProviderInfo>>(root.getValue("all"))
        val default = json.decodeFromJsonElement<Map<String, String>>(root.getValue("default"))
        val connected = json.decodeFromJsonElement<List<String>>(root.getValue("connected"))

        // 内置但未配置的 provider 必须出现在 all 里（否则「其他 provider 呢」无解）。
        assertEquals(listOf("anthropic", "groq", "litellm"), all.map { it.id })
        assertEquals("Anthropic", all.first().name)
        assertEquals("custom", all.first().source)
        assertEquals(listOf("ANTHROPIC_API_KEY"), all.first().env)
        assertEquals(1, all.first().models.size)
        assertEquals("kimi-k3", default["groq"])
        assertEquals(listOf("litellm"), connected)
    }

    @Test
    fun mergeProviderRegistry_appendsBuiltinsAfterConfiguredOnes() {
        // 两段式加载：首屏 connected 列表 + 后台全量注册表合并。
        val connected = ProviderCatalogResponse(
            all = listOf(provider("litellm", "LiteLLM Gateway", source = "config", models = listOf("glm-5.2"))),
            default = mapOf("litellm" to "glm-5.2"),
            connected = listOf("litellm"),
        )
        val registry = ProviderCatalogResponse(
            all = listOf(
                provider("anthropic", "Anthropic", source = "custom", models = listOf("claude-sonnet-4-6")),
                provider("litellm", "LiteLLM Gateway", source = "custom"),
            ),
            default = mapOf("anthropic" to "claude-sonnet-4-6", "litellm" to "other"),
            connected = listOf("litellm"),
        )

        val merged = mergeProviderRegistry(connected, registry)

        // 已配置项在前且保留自己的 models（不被内置占位覆盖），内置项追加在后。
        assertEquals(listOf("litellm", "anthropic"), merged.all.map { it.id })
        assertEquals(1, merged.all.first().models.size)
        assertEquals("config", merged.all.first().source)
        // connected 沿用首屏结果，default 取注册表的完整映射。
        assertEquals(listOf("litellm"), merged.connected)
        assertEquals("claude-sonnet-4-6", merged.default["anthropic"])
    }

    @Test
    fun mergeProviderRegistry_emptyConnected_fallsBackToRegistry() {
        val registry = ProviderCatalogResponse(
            all = listOf(provider("openai", "OpenAI")),
            default = mapOf("openai" to "gpt-6-luna"),
            connected = emptyList(),
        )
        val merged = mergeProviderRegistry(ProviderCatalogResponse(all = emptyList()), registry)
        assertEquals(listOf("openai"), merged.all.map { it.id })
        // connected 在两侧都为空时退化为「列表全部视为可连接」，避免页面无任何可操作项。
        assertEquals(listOf("openai"), merged.connected)
    }

    @Test
    fun v2ProviderList_decodesWhenVariantsIsArray_notObject() {
        // 回归：V2（opencode 与 agent 的 /api/provider、/api/model）的 variants 是**数组** `[]`，
        // 此前 ProviderModel.variants 声明为 Map<String,JsonElement>，遇到数组直接抛解码异常，
        // 整个 provider 列表解码失败 → 服务商名退化成 providerID、connected 丢失。
        val body = """
            {"data":[
              {"id":"litellm","name":"LiteLLM","api":{},"request":{},
               "models":{"glm-5.2":{"id":"glm-5.2","name":"glm-5.2","providerID":"litellm",
                                     "variants":[],"cost":[],"limit":{"context":0}}}}
            ]}
        """.trimIndent()

        val list = json.decodeFromJsonElement<List<ProviderInfo>>(
            json.parseToJsonElement(body).jsonObject.getValue("data"),
        )

        // 关键：解码成功且保住真实名称（UI 显示 name，不是退化的 id）。
        assertEquals(1, list.size)
        assertEquals("LiteLLM", list.first().name)
        assertEquals(1, list.first().models.size)
        // 数组形态没有具名变体 → 空列表（与该形态语义一致）。
        assertTrue(list.first().models.values.first().variantKeys.isEmpty())
    }

    @Test
    fun v1ProviderList_keepsVariantKeyOrderFromServer() {
        // V1 的 variants 是对象，variant 选择器依赖其 key 顺序（对齐 Web UI）。
        val body = """
            {"providers":[
              {"id":"opencode","name":"OpenCode Zen","source":"config","env":[],
               "models":{"big-pickle":{"id":"big-pickle","name":"big-pickle",
                 "variants":{"low":{"reasoningEffort":"low"},
                             "medium":{"reasoningEffort":"medium"},
                             "high":{"reasoningEffort":"high"}}}}}
            ]}
        """.trimIndent()

        val parsed = json.decodeFromJsonElement<ProvidersResponse>(json.parseToJsonElement(body))
        val model = parsed.providers.first().models.values.first()
        assertEquals(listOf("low", "medium", "high"), model.variantKeys)
    }

    @Test
    fun v2ProviderList_decodesWhenCostIsArray_notObject() {
        // 同源问题第二处：V2 的 cost 是数组 `[]`，ProviderModel.cost 声明为对象时
        // 解码抛 JsonDecodingException，同样拖垮整个 provider 列表。
        val body = """
            {"data":[{"id":"litellm","name":"LiteLLM","api":{},"request":{},
              "models":{"glm-5.2":{"id":"glm-5.2","name":"glm-5.2","providerID":"litellm",
                                    "variants":[],"cost":[],"limit":{"context":0}}}}]}
        """.trimIndent()

        val list = json.decodeFromJsonElement<List<ProviderInfo>>(
            json.parseToJsonElement(body).jsonObject.getValue("data"),
        )
        assertEquals("LiteLLM", list.first().name)
        // 数组形态无计费字段 → costInput 兜底 0（hasPaidModels 判定为免费）。
        assertEquals(0.0, list.first().models.values.first().costInput, 0.0)
    }

    @Test
    fun v1ProviderList_readsCostInputFromObjectShape() {
        // V1 的 cost 是对象，hasPaidModels 依赖 cost.input > 0 判定付费模型。
        val body = """
            {"providers":[{"id":"anthropic","name":"Anthropic","source":"custom","env":["ANTHROPIC_API_KEY"],
              "models":{"claude-sonnet-4-6":{"id":"claude-sonnet-4-6","name":"Claude Sonnet 4.6",
                "cost":{"input":3.0,"output":15.0},"variants":{}}}}]}
        """.trimIndent()

        val parsed = json.decodeFromJsonElement<ProvidersResponse>(json.parseToJsonElement(body))
        val model = parsed.providers.first().models.values.first()
        assertEquals(3.0, model.costInput, 0.0)
        assertEquals(15.0, model.costOutput, 0.0)
    }

    @Test
    fun mergeProviderRegistry_backfillsBlankFieldsForAlreadyConnectedProvider() {
        // agent 的 V2 /api/provider 不带 source，V1 /provider 带 source=config。
        // 不回填 → 服务商页来源显示成「其他」而非「配置」。
        val connected = ProviderCatalogResponse(
            all = listOf(provider("litellm", "LiteLLM")), // source 为空
            connected = listOf("litellm"),
        )
        val registry = ProviderCatalogResponse(
            all = listOf(provider("litellm", "LiteLLM", source = "config", models = listOf("glm-5.2"))),
            default = mapOf("litellm" to "glm-5.2"),
            connected = listOf("litellm"),
        )

        val merged = mergeProviderRegistry(connected, registry)

        val entry = merged.all.single()
        assertEquals("config", entry.source)
        assertEquals(1, entry.models.size)
        assertEquals(listOf("litellm"), merged.connected)
    }
}
