package com.aura.agent.network

import com.aura.agent.data.ProviderConfig
import com.aura.agent.data.TokenUsage
import kotlinx.serialization.json.longOrNull
import com.aura.agent.router.AuraCacheEngine
import com.aura.agent.skills.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit

class OpenAiCompatibleClient : LlmClient {
    private val client = SharedHttpClient.streamingClient

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun complete(
        provider: ProviderConfig,
        systemPrompt: String,
        messages: List<LlmTurnMessage>,
        tools: List<ToolSpec>,
        onDelta: (String) -> Unit
    ): LlmResponse = withContext(Dispatchers.IO) {
        val endpoint = provider.baseUrl.trimEnd('/') + "/chat/completions"

        val bodyJson = buildJsonObject {
            put("model", provider.model)
            put("temperature", provider.temperature.toDouble())
            put("max_tokens", provider.maxTokens)
            put("stream", true)
            val urlLower = provider.baseUrl.lowercase()
            if (urlLower.contains("openai.com") || urlLower.contains("openrouter.ai") || urlLower.contains("deepseek.com") || urlLower.contains("nousresearch.com") || urlLower.contains("groq.com")) {
                put("stream_options", buildJsonObject { put("include_usage", true) })
            }
            if (urlLower.contains("openai.com") && systemPrompt.isNotBlank()) {
                put("prompt_cache_key", "aura_${AuraCacheEngine.sha256Short(systemPrompt)}")
            }
            put("messages", buildJsonArray {
                if (systemPrompt.isNotBlank()) {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                }
                for (m in messages) {
                    when (m.role) {
                        "tool" -> add(buildJsonObject {
                            put("role", "tool")
                            put("tool_call_id", m.toolCallId ?: "")
                            put("content", m.content)
                        })
                        "assistant" -> add(buildJsonObject {
                            put("role", "assistant")
                            if (m.content.isNotBlank()) put("content", m.content)
                            if (m.toolCalls.isNotEmpty()) {
                                put("tool_calls", buildJsonArray {
                                    for (tc in m.toolCalls) {
                                        add(buildJsonObject {
                                            put("id", tc.id)
                                            put("type", "function")
                                            put("function", buildJsonObject {
                                                put("name", tc.name)
                                                put("arguments", tc.argumentsJson)
                                            })
                                        })
                                    }
                                })
                            }
                        })
                        else -> add(buildJsonObject {
                            put("role", m.role)
                            put("content", m.content)
                        })
                    }
                }
            })
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    for (t in tools) {
                        add(buildJsonObject {
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", t.name)
                                put("description", t.description)
                                put("parameters", t.parametersSchema)
                            })
                        })
                    }
                })
            }
        }

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (provider.apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer ${provider.apiKey.trim()}")
        }
        provider.customHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

        val fullText = StringBuilder()
        val toolCallMap = linkedMapOf<Int, MutableList<String>>()
        var inTok = 0L
        var outTok = 0L
        var cachedTok = 0L

        client.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body?.string().orEmpty().take(1200)
                throw IllegalStateException("API Error ${resp.code}: $errBody")
            }
            val stream = resp.body?.byteStream() ?: throw IllegalStateException("Empty response body")
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val raw = line!!.trim()
                    if (!raw.startsWith("data:")) continue
                    val data = raw.removePrefix("data:").trim()
                    if (data == "[DONE]" || data.isBlank()) continue

                    val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                    root["usage"]?.jsonObject?.let { u ->
                        u["prompt_tokens"]?.jsonPrimitive?.longOrNull?.let { inTok = it }
                        u["completion_tokens"]?.jsonPrimitive?.longOrNull?.let { outTok = it }
                        u["prompt_tokens_details"]?.jsonObject?.get("cached_tokens")?.jsonPrimitive?.longOrNull?.let { cachedTok = it }
                    }
                    val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: continue
                    val delta = choice["delta"]?.jsonObject ?: continue

                    val chunk = delta["content"]?.jsonPrimitive?.contentOrNull
                    if (!chunk.isNullOrEmpty()) {
                        fullText.append(chunk)
                        onDelta(chunk)
                    }

                    val tcArray = delta["tool_calls"]?.jsonArray
                    if (tcArray != null) {
                        for (tcElem in tcArray) {
                            val tcObj = tcElem.jsonObject
                            val idx = tcObj["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                            val entry = toolCallMap.getOrPut(idx) { mutableListOf("", "", "") }
                            tcObj["id"]?.jsonPrimitive?.contentOrNull?.let { if (it.isNotEmpty()) entry[0] = it }
                            val fn = tcObj["function"]?.jsonObject
                            fn?.get("name")?.jsonPrimitive?.contentOrNull?.let { if (it.isNotEmpty()) entry[1] += it }
                            fn?.get("arguments")?.jsonPrimitive?.contentOrNull?.let { entry[2] += it }
                        }
                    }
                }
            }
        }

        val parsedCalls = toolCallMap.values
            .filter { it[1].isNotBlank() }
            .map {
                LlmToolCall(
                    id = it[0].ifBlank { "call_${UUID.randomUUID().toString().take(8)}" },
                    name = it[1],
                    argumentsJson = it[2].ifBlank { "{}" }
                )
            }

        LlmResponse(text = fullText.toString(), toolCalls = parsedCalls, usage = TokenUsage(inTok, outTok, cachedTok))
    }
}
