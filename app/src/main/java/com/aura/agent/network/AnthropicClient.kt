package com.aura.agent.network

import com.aura.agent.data.ProviderConfig
import com.aura.agent.skills.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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

class AnthropicClient : LlmClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun complete(
        provider: ProviderConfig,
        systemPrompt: String,
        messages: List<LlmTurnMessage>,
        tools: List<ToolSpec>,
        onDelta: (String) -> Unit
    ): LlmResponse = withContext(Dispatchers.IO) {
        val endpoint = provider.baseUrl.trimEnd('/') + "/messages"

        val bodyJson = buildJsonObject {
            put("model", provider.model)
            put("max_tokens", provider.maxTokens)
            put("temperature", provider.temperature.toDouble())
            put("stream", true)
            if (systemPrompt.isNotBlank()) {
                put("system", systemPrompt)
            }
            put("messages", buildJsonArray {
                for (m in messages) {
                    when (m.role) {
                        "tool" -> add(buildJsonObject {
                            put("role", "user")
                            put("content", buildJsonArray {
                                add(buildJsonObject {
                                    put("type", "tool_result")
                                    put("tool_use_id", m.toolCallId ?: "")
                                    put("content", m.content)
                                })
                            })
                        })
                        "assistant" -> add(buildJsonObject {
                            put("role", "assistant")
                            put("content", buildJsonArray {
                                if (m.content.isNotBlank()) {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", m.content)
                                    })
                                }
                                for (tc in m.toolCalls) {
                                    val inputObj = runCatching {
                                        json.parseToJsonElement(tc.argumentsJson).jsonObject
                                    }.getOrElse { buildJsonObject {} }
                                    add(buildJsonObject {
                                        put("type", "tool_use")
                                        put("id", tc.id)
                                        put("name", tc.name)
                                        put("input", inputObj)
                                    })
                                }
                            })
                        })
                        else -> add(buildJsonObject {
                            put("role", "user")
                            put("content", m.content)
                        })
                    }
                }
            })
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    for (t in tools) {
                        add(buildJsonObject {
                            put("name", t.name)
                            put("description", t.description)
                            put("input_schema", t.parametersSchema)
                        })
                    }
                })
            }
        }

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .header("x-api-key", provider.apiKey.trim())
            .header("anthropic-version", "2023-06-01")
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

        provider.customHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }

        val fullText = StringBuilder()
        val toolUseBlocks = linkedMapOf<Int, MutableList<String>>() // index -> [id, name, partialJson]

        client.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty().take(1200)
                throw IllegalStateException("Anthropic API Error ${resp.code}: $err")
            }
            val stream = resp.body?.byteStream() ?: throw IllegalStateException("Empty Anthropic stream")
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val raw = line!!.trim()
                    if (!raw.startsWith("data:")) continue
                    val data = raw.removePrefix("data:").trim()
                    if (data.isBlank()) continue

                    val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                    val type = root["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val index = root["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0

                    when (type) {
                        "content_block_start" -> {
                            val cb = root["content_block"]?.jsonObject
                            if (cb?.get("type")?.jsonPrimitive?.contentOrNull == "tool_use") {
                                val id = cb["id"]?.jsonPrimitive?.contentOrNull ?: "tu_${UUID.randomUUID().toString().take(8)}"
                                val name = cb["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                toolUseBlocks[index] = mutableListOf(id, name, "")
                            }
                        }
                        "content_block_delta" -> {
                            val delta = root["delta"]?.jsonObject
                            when (delta?.get("type")?.jsonPrimitive?.contentOrNull) {
                                "text_delta" -> {
                                    val txt = delta["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                    if (txt.isNotEmpty()) {
                                        fullText.append(txt)
                                        onDelta(txt)
                                    }
                                }
                                "input_json_delta" -> {
                                    val partial = delta["partial_json"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                    toolUseBlocks[index]?.let { it[2] += partial }
                                }
                            }
                        }
                    }
                }
            }
        }

        val parsedTools = toolUseBlocks.values
            .filter { it[1].isNotBlank() }
            .map { LlmToolCall(id = it[0], name = it[1], argumentsJson = it[2].ifBlank { "{}" }) }

        LlmResponse(text = fullText.toString(), toolCalls = parsedTools)
    }
}
