package com.aura.agent.router

import com.aura.agent.data.ProviderConfig
import com.aura.agent.data.RouterConfig
import com.aura.agent.network.LlmTurnMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

class LocalApiGateway(
    private val router: AuraRouter,
    private val getProviders: () -> List<ProviderConfig>,
    private val getActiveProviderId: () -> String,
    private val getRouterConfig: () -> RouterConfig,
    private val onRequestLogged: (String) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var boundPort: Int = 8910
        private set

    fun start(port: Int = 8910, bindAllInterfaces: Boolean = false): Boolean {
        if (isRunning) return true
        return try {
            val addr = if (bindAllInterfaces) InetAddress.getByName("0.0.0.0") else InetAddress.getByName("127.0.0.1")
            val ss = ServerSocket(port, 50, addr)
            serverSocket = ss
            boundPort = port
            isRunning = true
            acceptJob = scope.launch {
                while (isActive && isRunning) {
                    try {
                        val client = ss.accept()
                        launch { handleClient(client) }
                    } catch (_: Exception) {
                        if (!isRunning) break
                    }
                }
            }
            onRequestLogged("Gateway started on ${addr.hostAddress}:$port")
            true
        } catch (e: Exception) {
            isRunning = false
            onRequestLogged("Gateway failed to start: ${e.message}")
            false
        }
    }

    fun stop() {
        isRunning = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        onRequestLogged("Gateway stopped")
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 60_000
            val input = BufferedInputStream(sock.getInputStream())
            val output = BufferedOutputStream(sock.getOutputStream())

            val headerBytes = readUntilHeaderEnd(input) ?: return
            val headerText = headerBytes.toString(Charsets.UTF_8)
            val lines = headerText.split("\r\n")
            val reqLine = lines.firstOrNull()?.split(" ") ?: return
            if (reqLine.size < 2) return
            val method = reqLine[0].uppercase()
            val path = reqLine[1].substringBefore("?")

            val headers = mutableMapOf<String, String>()
            for (line in lines.drop(1)) {
                val idx = line.indexOf(':')
                if (idx > 0) {
                    headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
            }

            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val bodyStr = if (contentLength > 0) {
                val buf = ByteArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = input.read(buf, read, contentLength - read)
                    if (n <= 0) break
                    read += n
                }
                buf.toString(Charsets.UTF_8)
            } else ""

            if (method == "OPTIONS") {
                writeJsonResponse(output, 204, "")
                return
            }

            when {
                method == "GET" && (path == "/health" || path == "/") -> {
                    val payload = buildJsonObject {
                        put("status", "ok")
                        put("service", "AuraRouter Local Gateway")
                        put("version", "2.1.0")
                        put("compatible_with", "OpenAI, OpenCode, Cline, Continue")
                    }.toString()
                    writeJsonResponse(output, 200, payload)
                }
                method == "GET" && path == "/v1/models" -> {
                    handleModels(output)
                }
                method == "POST" && path == "/v1/chat/completions" -> {
                    handleChatCompletions(bodyStr, output)
                }
                else -> {
                    val err = buildJsonObject {
                        put("error", buildJsonObject {
                            put("message", "Unknown route: $method $path")
                            put("type", "invalid_request_error")
                        })
                    }.toString()
                    writeJsonResponse(output, 404, err)
                }
            }
        }
    }

    private fun handleModels(output: BufferedOutputStream) {
        val providers = getProviders()
        val nowSec = System.currentTimeMillis() / 1000L
        val payload = buildJsonObject {
            put("object", "list")
            put("data", buildJsonArray {
                add(buildJsonObject {
                    put("id", "aura/auto")
                    put("object", "model")
                    put("created", nowSec)
                    put("owned_by", "aura-router")
                })
                for (p in providers) {
                    if (!router.isConfigured(p)) continue
                    add(buildJsonObject {
                        put("id", "${p.id}/${p.model}")
                        put("object", "model")
                        put("created", nowSec)
                        put("owned_by", p.name)
                    })
                }
            })
        }.toString()
        writeJsonResponse(output, 200, payload)
    }

    private suspend fun handleChatCompletions(bodyStr: String, output: BufferedOutputStream) {
        try {
            val reqObj = json.parseToJsonElement(bodyStr).jsonObject
            val requestedModel = reqObj["model"]?.jsonPrimitive?.contentOrNull ?: "aura/auto"
            val stream = reqObj["stream"]?.jsonPrimitive?.contentOrNull == "true"
            val msgArray = reqObj["messages"]?.jsonArray ?: buildJsonArray {}

            val systemParts = mutableListOf<String>()
            val turns = mutableListOf<LlmTurnMessage>()
            var latestUserPrompt = ""

            for (elem in msgArray) {
                val obj = elem.jsonObject
                val role = obj["role"]?.jsonPrimitive?.contentOrNull ?: "user"
                val contentElem = obj["content"]
                val content = when {
                    contentElem is JsonPrimitive -> contentElem.contentOrNull.orEmpty()
                    else -> contentElem?.toString().orEmpty()
                }
                if (role == "system") {
                    systemParts.add(content)
                } else {
                    if (role == "user") latestUserPrompt = content
                    turns.add(LlmTurnMessage(role = role, content = content))
                }
            }

            val providers = getProviders()
            val targetProviderId = if (requestedModel != "aura/auto" && requestedModel.contains("/")) {
                val prefix = requestedModel.substringBefore("/")
                providers.firstOrNull { it.id == prefix }?.id ?: getActiveProviderId()
            } else {
                getActiveProviderId()
            }

            val completionId = "chatcmpl-${UUID.randomUUID().toString().take(12)}"
            val created = System.currentTimeMillis() / 1000L

            if (stream) {
                val headers = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/event-stream; charset=utf-8\r\n" +
                    "Cache-Control: no-cache\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n"
                output.write(headers.toByteArray(Charsets.UTF_8))
                output.flush()

                val routed = router.completeWithRouting(
                    providers = providers,
                    activeProviderId = targetProviderId,
                    config = getRouterConfig(),
                    systemPrompt = systemParts.joinToString("\n\n"),
                    messages = turns,
                    tools = emptyList(),
                    latestUserPrompt = latestUserPrompt,
                    onDelta = { delta ->
                        val chunk = buildJsonObject {
                            put("id", completionId)
                            put("object", "chat.completion.chunk")
                            put("created", created)
                            put("model", requestedModel)
                            put("choices", buildJsonArray {
                                add(buildJsonObject {
                                    put("index", 0)
                                    put("delta", buildJsonObject { put("content", delta) })
                                    put("finish_reason", JsonPrimitive(null as String?))
                                })
                            })
                        }.toString()
                        runCatching {
                            output.write("data: $chunk\n\n".toByteArray(Charsets.UTF_8))
                            output.flush()
                        }
                    }
                )
                onRequestLogged("POST /v1/chat/completions -> ${routed.usedProvider.name} (${routed.latencyMs}ms)")
                output.write("data: [DONE]\n\n".toByteArray(Charsets.UTF_8))
                output.flush()
            } else {
                val routed = router.completeWithRouting(
                    providers = providers,
                    activeProviderId = targetProviderId,
                    config = getRouterConfig(),
                    systemPrompt = systemParts.joinToString("\n\n"),
                    messages = turns,
                    tools = emptyList(),
                    latestUserPrompt = latestUserPrompt,
                    onDelta = {}
                )
                onRequestLogged("POST /v1/chat/completions -> ${routed.usedProvider.name} (${routed.latencyMs}ms)")
                val respJson = buildJsonObject {
                    put("id", completionId)
                    put("object", "chat.completion")
                    put("created", created)
                    put("model", "${routed.usedProvider.id}/${routed.usedProvider.model}")
                    put("choices", buildJsonArray {
                        add(buildJsonObject {
                            put("index", 0)
                            put("message", buildJsonObject {
                                put("role", "assistant")
                                put("content", routed.response.text)
                            })
                            put("finish_reason", "stop")
                        })
                    })
                }.toString()
                writeJsonResponse(output, 200, respJson)
            }
        } catch (e: Exception) {
            val err = buildJsonObject {
                put("error", buildJsonObject {
                    put("message", e.message ?: "Gateway routing error")
                    put("type", "server_error")
                })
            }.toString()
            writeJsonResponse(output, 502, err)
        }
    }

    private fun writeJsonResponse(output: BufferedOutputStream, status: Int, body: String) {
        val statusText = when (status) {
            200 -> "OK"
            204 -> "No Content"
            404 -> "Not Found"
            else -> "Bad Gateway"
        }
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $status $statusText\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bodyBytes.size}\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Headers: Authorization, Content-Type\r\n" +
            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
            "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.UTF_8))
        if (bodyBytes.isNotEmpty()) output.write(bodyBytes)
        output.flush()
    }

    private fun readUntilHeaderEnd(input: BufferedInputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        var matched = 0
        val marker = byteArrayOf(13, 10, 13, 10)
        while (out.size() < 32768) {
            val b = input.read()
            if (b == -1) return null
            out.write(b)
            if (b.toByte() == marker[matched]) {
                matched++
                if (matched == 4) return out.toByteArray()
            } else {
                matched = if (b.toByte() == marker[0]) 1 else 0
            }
        }
        return out.toByteArray()
    }
}
