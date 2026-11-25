package com.aura.agent.channels

import com.aura.agent.data.TelegramBridgeConfig
import com.aura.agent.network.SharedHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap

class TelegramBotBridge(
    private val getConfig: () -> TelegramBridgeConfig,
    private val onHandleMessage: suspend (chatId: Long, username: String, text: String) -> String,
    private val onLog: (String) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = SharedHttpClient.streamingClient
    private val fastClient = SharedHttpClient.fastClient

    private var pollJob: Job? = null
    private var lastUpdateId: Long = 0L

    @Volatile
    var isRunning: Boolean = false
        private set

    suspend fun verifyToken(token: String, baseUrl: String = "https://api.telegram.org"): String = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trimEnd('/').ifBlank { "https://api.telegram.org" }
        val url = "$cleanBase/bot${token.trim()}/getMe"
        val req = Request.Builder().url(url).get().build()
        fastClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("Telegram HTTP ${resp.code}")
            val root = json.parseToJsonElement(body).jsonObject
            val ok = root["ok"]?.jsonPrimitive?.contentOrNull == "true"
            if (!ok) throw IllegalStateException(root["description"]?.jsonPrimitive?.contentOrNull ?: "Invalid bot token")
            val username = root["result"]?.jsonObject?.get("username")?.jsonPrimitive?.contentOrNull.orEmpty()
            "@$username"
        }
    }

    fun start(): Boolean {
        if (isRunning) return true
        val cfg = getConfig()
        if (cfg.botToken.isBlank()) {
            onLog("Bot token is empty. Paste your token from @BotFather first.")
            return false
        }
        isRunning = true
        onLog("Starting Telegram polling loop...")
        pollJob = scope.launch {
            while (isActive && isRunning) {
                try {
                    val currentCfg = getConfig()
                    val cleanBase = currentCfg.apiBaseUrl.trimEnd('/').ifBlank { "https://api.telegram.org" }
                    val token = currentCfg.botToken.trim()
                    val offsetParam = if (lastUpdateId > 0L) "&offset=${lastUpdateId + 1}" else ""
                    val url = "$cleanBase/bot$token/getUpdates?timeout=25$offsetParam"
                    val req = Request.Builder().url(url).get().build()

                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            onLog("Telegram poll HTTP ${resp.code}; retrying in 5s...")
                            delay(5000L)
                            return@use
                        }
                        val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                        val updates = root["result"]?.jsonArray ?: return@use
                        for (uElem in updates) {
                            val uObj = uElem.jsonObject
                            val uid = uObj["update_id"]?.jsonPrimitive?.longOrNull ?: continue
                            if (uid > lastUpdateId) lastUpdateId = uid

                            val msg = uObj["message"]?.jsonObject ?: continue
                            val text = msg["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                            if (text.isBlank()) continue

                            val chat = msg["chat"]?.jsonObject ?: continue
                            val chatId = chat["id"]?.jsonPrimitive?.longOrNull ?: continue
                            val fromUser = msg["from"]?.jsonObject?.get("username")?.jsonPrimitive?.contentOrNull.orEmpty()

                            if (!isChatAllowed(chatId, fromUser, currentCfg.allowedChatIds)) {
                                onLog("Blocked unauthorized chat $chatId (@$fromUser)")
                                continue
                            }

                            launch {
                                onLog("Incoming [$chatId / @$fromUser]: ${text.take(60)}")
                                sendTyping(cleanBase, token, chatId)
                                val reply = try {
                                    onHandleMessage(chatId, fromUser, text)
                                } catch (e: Exception) {
                                    "Aura error: ${e.message ?: e.javaClass.simpleName}"
                                }
                                sendTextChunked(cleanBase, token, chatId, reply)
                                onLog("Replied to [$chatId] (${reply.length} chars)")
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isRunning) {
                        onLog("Poll error: ${e.message ?: e.javaClass.simpleName} (retrying in 4s)")
                        delay(4000L)
                    }
                }
            }
        }
        return true
    }

    fun stop() {
        isRunning = false
        pollJob?.cancel()
        pollJob = null
        onLog("Telegram bot stopped.")
    }

    private fun sendTyping(baseUrl: String, token: String, chatId: Long) {
        runCatching {
            val payload = buildJsonObject {
                put("chat_id", chatId)
                put("action", "typing")
            }.toString()
            val req = Request.Builder()
                .url("$baseUrl/bot$token/sendChatAction")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            fastClient.newCall(req).execute().close()
        }
    }

    private fun sendTextChunked(baseUrl: String, token: String, chatId: Long, fullText: String) {
        val chunks = chunkTelegramText(fullText, 3900)
        for (chunk in chunks) {
            runCatching {
                val payload = buildJsonObject {
                    put("chat_id", chatId)
                    put("text", chunk)
                }.toString()
                val req = Request.Builder()
                    .url("$baseUrl/bot$token/sendMessage")
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                fastClient.newCall(req).execute().close()
            }
        }
    }

    companion object {
        fun isChatAllowed(chatId: Long, username: String, allowListRaw: String): Boolean {
            val entries = allowListRaw
                .split(',', ' ', '\n')
                .map { it.trim().removePrefix("@").lowercase() }
                .filter { it.isNotBlank() }
            if (entries.isEmpty()) return true
            return chatId.toString() in entries || (username.isNotBlank() && username.lowercase() in entries)
        }

        fun chunkTelegramText(text: String, maxLen: Int = 3900): List<String> {
            val clean = text.ifBlank { "(Empty response)" }
            if (clean.length <= maxLen) return listOf(clean)
            val result = mutableListOf<String>()
            var idx = 0
            while (idx < clean.length) {
                val end = (idx + maxLen).coerceAtMost(clean.length)
                result.add(clean.substring(idx, end))
                idx = end
            }
            return result
        }
    }
}
