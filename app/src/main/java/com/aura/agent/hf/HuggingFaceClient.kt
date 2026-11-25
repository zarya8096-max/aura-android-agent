package com.aura.agent.hf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class HfModelSummary(
    val repoId: String,
    val author: String,
    val downloads: Long,
    val likes: Long,
    val tags: List<String>
)

data class HfGgufFile(
    val path: String,
    val sizeBytes: Long,
    val downloadUrl: String
)

class HuggingFaceClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Queries the live Hugging Face Hub API for GGUF local models (full public catalog).
     */
    suspend fun searchGgufModels(query: String, limit: Int = 35): List<HfModelSummary> = withContext(Dispatchers.IO) {
        val qParam = if (query.isBlank()) "" else "&search=${URLEncoder.encode(query.trim(), "UTF-8")}"
        val url = "https://huggingface.co/api/models?filter=gguf&sort=downloads&direction=-1&limit=$limit$qParam"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "AuraAgent-Android/3.0")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Hugging Face API ${resp.code}")
            }
            val body = resp.body?.string().orEmpty()
            val arr = json.parseToJsonElement(body).jsonArray
            arr.mapNotNull { elem ->
                val obj = elem.jsonObject
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val downloads = obj["downloads"]?.jsonPrimitive?.longOrNull ?: 0L
                val likes = obj["likes"]?.jsonPrimitive?.longOrNull ?: 0L
                val tags = obj["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                HfModelSummary(
                    repoId = id,
                    author = id.substringBefore("/", "community"),
                    downloads = downloads,
                    likes = likes,
                    tags = tags.take(5)
                )
            }
        }
    }

    /**
     * Lists all .gguf quantization files inside a Hugging Face model repo.
     */
    suspend fun listGgufFiles(repoId: String): List<HfGgufFile> = withContext(Dispatchers.IO) {
        val url = "https://huggingface.co/api/models/$repoId/tree/main"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "AuraAgent-Android/3.0")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HF tree API ${resp.code}")
            }
            val body = resp.body?.string().orEmpty()
            val arr = json.parseToJsonElement(body).jsonArray
            arr.mapNotNull { elem ->
                val obj = elem.jsonObject
                val path = obj["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                if (!path.endsWith(".gguf", ignoreCase = true)) return@mapNotNull null
                val lfsSize = obj["lfs"]?.jsonObject?.get("size")?.jsonPrimitive?.longOrNull
                val size = lfsSize ?: obj["size"]?.jsonPrimitive?.longOrNull ?: 0L
                HfGgufFile(
                    path = path,
                    sizeBytes = size,
                    downloadUrl = "https://huggingface.co/$repoId/resolve/main/$path?download=true"
                )
            }.sortedBy { it.sizeBytes }
        }
    }

    /**
     * Downloads a .gguf file to local storage with live progress callbacks.
     */
    suspend fun downloadGguf(
        file: HfGgufFile,
        destFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(file.downloadUrl)
            .header("User-Agent", "AuraAgent-Android/3.0")
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Download failed: HTTP ${resp.code}")
            }
            val body = resp.body ?: throw IllegalStateException("Empty download body")
            val total = if (body.contentLength() > 0) body.contentLength() else file.sizeBytes
            destFile.parentFile?.mkdirs()
            val tmpFile = File(destFile.parentFile, "${destFile.name}.part")
            var downloaded = 0L
            body.byteStream().use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var lastReport = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 250L) {
                            onProgress(downloaded, total)
                            lastReport = now
                        }
                    }
                    output.flush()
                }
            }
            onProgress(downloaded, total)
            if (destFile.exists()) destFile.delete()
            tmpFile.renameTo(destFile)
            destFile
        }
    }

    /**
     * Probes whether a local OpenAI-compatible server (llama-server / Ollama) is live and returns its model IDs.
     */
    suspend fun probeLocalServer(baseUrl: String, apiKey: String = ""): List<String> = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/models"
        val rb = Request.Builder().url(url).get()
        if (apiKey.isNotBlank()) rb.header("Authorization", "Bearer $apiKey")
        client.newCall(rb.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
            root["data"]?.jsonArray?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull } ?: emptyList()
        }
    }
}
