package com.aura.agent.network

import com.aura.agent.data.ApiProtocol
import com.aura.agent.data.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ModelCatalogService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches the live model catalog from any supported provider (Gemini, OpenAI-Compatible, Anthropic, Local GGUF).
     */
    suspend fun fetchLiveModels(provider: ProviderConfig): List<String> = withContext(Dispatchers.IO) {
        val base = provider.baseUrl.trimEnd('/')
        when (provider.protocol) {
            ApiProtocol.GEMINI -> {
                val url = "$base/models?key=${provider.apiKey.trim()}&pageSize=100"
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("Gemini models HTTP ${resp.code}")
                    val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    root["models"]?.jsonArray?.mapNotNull { elem ->
                        val obj = elem.jsonObject
                        val methods = obj["supportedGenerationMethods"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
                        if (methods.isNotEmpty() && "generateContent" !in methods) return@mapNotNull null
                        obj["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/")
                    }.orEmpty().distinct().sortedDescending()
                }
            }
            ApiProtocol.ANTHROPIC -> {
                val url = "$base/models?limit=100"
                val req = Request.Builder()
                    .url(url)
                    .header("x-api-key", provider.apiKey.trim())
                    .header("anthropic-version", "2023-06-01")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("Anthropic models HTTP ${resp.code}")
                    val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    root["data"]?.jsonArray?.mapNotNull {
                        it.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                    }.orEmpty().distinct()
                }
            }
            ApiProtocol.OPENAI_COMPATIBLE, ApiProtocol.LOCAL_GGUF -> {
                val url = "$base/models"
                val rb = Request.Builder().url(url).get()
                if (provider.apiKey.isNotBlank()) {
                    rb.header("Authorization", "Bearer ${provider.apiKey.trim()}")
                }
                provider.customHeaders.forEach { (k, v) -> rb.header(k, v) }
                client.newCall(rb.build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw IllegalStateException("Models HTTP ${resp.code}")
                    val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    root["data"]?.jsonArray?.mapNotNull {
                        it.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                    }.orEmpty().distinct()
                }
            }
        }
    }
}
