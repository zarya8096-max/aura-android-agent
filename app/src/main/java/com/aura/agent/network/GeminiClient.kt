package com.aura.agent.network

import com.aura.agent.data.ProviderConfig
import com.aura.agent.skills.ToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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

class GeminiClient : LlmClient {
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
        val base = provider.baseUrl.trimEnd('/')
        val key = provider.apiKey.trim()
        val endpoint = "$base/models/${provider.model}:streamGenerateContent?alt=sse&key=$key"

        val bodyJson = buildJsonObject {
            if (systemPrompt.isNotBlank()) {
                put("system_instruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", systemPrompt) })
                    })
                })
            }
            put("contents", buildJsonArray {
                for (m in messages) {
                    when (m.role) {
                        "tool" -> add(buildJsonObject {
                            put("role", "user")
                            put("parts", buildJsonArray {
                                add(buildJsonObject {
                                    put("functionResponse", buildJsonObject {
                                        put("name", m.toolName ?: "tool")
                                        put("response", buildJsonObject {
                                            put("result", m.content)
                                        })
                                    })
                                })
                            })
                        })
                        "assistant" -> add(buildJsonObject {
                            put("role", "model")
                            put("parts", buildJsonArray {
                                if (m.content.isNotBlank()) {
                                    add(buildJsonObject { put("text", m.content) })
                                }
                                for (tc in m.toolCalls) {
                                    val argsObj = runCatching {
                                        json.parseToJsonElement(tc.argumentsJson).jsonObject
                                    }.getOrElse { buildJsonObject {} }
                                    add(buildJsonObject {
                                        put("functionCall", buildJsonObject {
                                            put("name", tc.name)
                                            put("args", argsObj)
                                        })
                                    })
                                }
                            })
                        })
                        else -> add(buildJsonObject {
                            put("role", "user")
                            put("parts", buildJsonArray {
                                add(buildJsonObject { put("text", m.content) })
                            })
                        })
                    }
                }
            })
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    add(buildJsonObject {
                        put("function_declarations", buildJsonArray {
                            for (t in tools) {
                                add(buildJsonObject {
                                    put("name", t.name)
                                    put("description", t.description)
                                    put("parameters", t.parametersSchema)
                                })
                            }
                        })
                    })
                })
            }
            put("generationConfig", buildJsonObject {
                put("temperature", provider.temperature.toDouble())
                put("maxOutputTokens", provider.maxTokens)
            })
        }

        val req = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val fullText = StringBuilder()
        val toolCalls = mutableListOf<LlmToolCall>()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty().take(1200)
                throw IllegalStateException("Gemini API Error ${resp.code}: $err")
            }
            val stream = resp.body?.byteStream() ?: throw IllegalStateException("Empty Gemini stream")
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val raw = line!!.trim()
                    if (!raw.startsWith("data:")) continue
                    val data = raw.removePrefix("data:").trim()
                    if (data.isBlank()) continue

                    val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                    val parts = root["candidates"]?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: continue

                    for (partElem in parts) {
                        val part = partElem.jsonObject
                        part["text"]?.jsonPrimitive?.contentOrNull?.let { txt ->
                            if (txt.isNotEmpty()) {
                                fullText.append(txt)
                                onDelta(txt)
                            }
                        }
                        part["functionCall"]?.jsonObject?.let { fc ->
                            val name = fc["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            val args = fc["args"]?.toString() ?: "{}"
                            if (name.isNotBlank()) {
                                toolCalls.add(
                                    LlmToolCall(
                                        id = "gem_${UUID.randomUUID().toString().take(8)}",
                                        name = name,
                                        argumentsJson = args
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        LlmResponse(text = fullText.toString(), toolCalls = toolCalls)
    }
}
