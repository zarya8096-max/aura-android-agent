package com.aura.agent.skills

import android.util.Base64
import com.aura.agent.data.AgentSkill
import com.aura.agent.data.MemoryEntry
import com.aura.agent.data.SkillType
import com.aura.agent.router.AuraCacheEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import com.aura.agent.network.SharedHttpClient
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.pow

data class ToolSpec(
    val name: String,
    val description: String,
    val parametersSchema: JsonObject
)

data class ToolExecutionResult(
    val output: String,
    val isError: Boolean = false,
    val fromCache: Boolean = false
)

class SkillExecutor(
    private val workspaceDir: File,
    private val getMemories: () -> List<MemoryEntry>,
    private val upsertMemory: (key: String, content: String, category: String, tags: List<String>) -> Unit,
    private val deleteMemoryByKey: (key: String) -> Boolean,
    private val isToolCacheEnabled: () -> Boolean,
    private val onToolCacheHit: () -> Unit,
    private val runSubagent: suspend (role: String, task: String) -> String
) {
    private val httpClient = SharedHttpClient.fastClient

    private val json = Json { ignoreUnknownKeys = true }

    fun buildToolSpecs(enabledSkills: List<AgentSkill>): List<ToolSpec> {
        val specs = mutableListOf<ToolSpec>()
        for (skill in enabledSkills) {
            when (skill.type) {
                SkillType.BUILTIN_TOOL -> when (skill.slug) {
                    "spawn_subagent" -> specs.add(
                        ToolSpec(
                            name = "spawn_subagent",
                            description = "Spawn an isolated subagent with a focused role to solve a subtask cleanly without polluting main context.",
                            parametersSchema = buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {
                                    put("role", buildJsonObject { put("type", "string"); put("description", "Short role e.g. 'Code Reviewer' or 'Fact Checker'") })
                                    put("task", buildJsonObject { put("type", "string"); put("description", "Clear self-contained subtask prompt") })
                                })
                                put("required", buildJsonArray { add(JsonPrimitive("role")); add(JsonPrimitive("task")) })
                            }
                        )
                    )
                    "web_search" -> specs.add(
                        ToolSpec(
                            name = "web_search",
                            description = "Search public Wikipedia and DuckDuckGo Instant Answers for concise factual summaries.",
                            parametersSchema = buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {
                                    put("query", buildJsonObject {
                                        put("type", "string")
                                        put("description", "Search query in English or Persian.")
                                    })
                                })
                                put("required", buildJsonArray { add(JsonPrimitive("query")) })
                            }
                        )
                    )
                    "http_fetch" -> specs.add(
                        ToolSpec(
                            name = "http_fetch",
                            description = "Fetch a public URL or REST API (GET or POST) and return clean text/JSON (up to 4500 chars).",
                            parametersSchema = buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {
                                    put("url", buildJsonObject { put("type", "string") })
                                    put("method", buildJsonObject { put("type", "string"); put("description", "GET or POST (default GET)") })
                                    put("body", buildJsonObject { put("type", "string"); put("description", "Optional JSON body for POST") })
                                })
                                put("required", buildJsonArray { add(JsonPrimitive("url")) })
                            }
                        )
                    )
                    "calculator" -> specs.add(
                        ToolSpec(
                            name = "calculator",
                            description = "Evaluate a mathematical expression (+, -, *, /, ^, parentheses).",
                            parametersSchema = buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {
                                    put("expression", buildJsonObject { put("type", "string") })
                                })
                                put("required", buildJsonArray { add(JsonPrimitive("expression")) })
                            }
                        )
                    )
                    "device_time" -> specs.add(
                        ToolSpec(
                            name = "device_time",
                            description = "Get current device date, time, timezone, and Unix epoch.",
                            parametersSchema = buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {})
                            }
                        )
                    )
                    "memory_notes" -> {
                        specs.add(
                            ToolSpec(
                                name = "memory_save",
                                description = "Save or update a structured long-term memory entry across sessions.",
                                parametersSchema = buildJsonObject {
                                    put("type", "object")
                                    put("properties", buildJsonObject {
                                        put("key", buildJsonObject { put("type", "string"); put("description", "Short slug key") })
                                        put("content", buildJsonObject { put("type", "string"); put("description", "Verified fact or preference") })
                                        put("category", buildJsonObject { put("type", "string"); put("description", "profile, project, preference, finding, or general") })
                                    })
                                    put("required", buildJsonArray { add(JsonPrimitive("key")); add(JsonPrimitive("content")) })
                                }
                            )
                        )
                        specs.add(
                            ToolSpec(
                                name = "memory_search",
                                description = "Search saved long-term memories by keyword or list all memories if query is empty.",
                                parametersSchema = buildJsonObject {
                                    put("type", "object")
                                    put("properties", buildJsonObject {
                                        put("query", buildJsonObject { put("type", "string") })
                                    })
                                }
                            )
                        )
                        specs.add(
                            ToolSpec(
                                name = "memory_delete",
                                description = "Delete an outdated memory entry by its key.",
                                parametersSchema = buildJsonObject {
                                    put("type", "object")
                                    put("properties", buildJsonObject {
                                        put("key", buildJsonObject { put("type", "string") })
                                    })
                                    put("required", buildJsonArray { add(JsonPrimitive("key")) })
                                }
                            )
                        )
                    }
                    "file_workspace" -> specs.add(
                        ToolSpec(
                            name = "file_workspace",
                            description = "Manage scratchpad files in the agent's local workspace (action: list, read, write, append, delete).",
                            parametersSchema = buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {
                                    put("action", buildJsonObject { put("type", "string"); put("description", "list, read, write, append, or delete") })
                                    put("filename", buildJsonObject { put("type", "string"); put("description", "Safe filename e.g. notes.md") })
                                    put("content", buildJsonObject { put("type", "string"); put("description", "Text content for write/append") })
                                })
                                put("required", buildJsonArray { add(JsonPrimitive("action")) })
                            }
                        )
                    )
                    "json_query" -> specs.add(
                        ToolSpec(
                            name = "json_query",
                            description = "Extract a field from a JSON string using a dot-separated path (e.g. 'data.0.name').",
                            parametersSchema = buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {
                                    put("json_text", buildJsonObject { put("type", "string") })
                                    put("path", buildJsonObject { put("type", "string") })
                                })
                                put("required", buildJsonArray { add(JsonPrimitive("json_text")); add(JsonPrimitive("path")) })
                            }
                        )
                    )
                    "text_transform" -> specs.add(
                        ToolSpec(
                            name = "text_transform",
                            description = "Run utility transforms: base64_encode, base64_decode, sha256, url_encode, stats.",
                            parametersSchema = buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {
                                    put("operation", buildJsonObject { put("type", "string") })
                                    put("text", buildJsonObject { put("type", "string") })
                                })
                                put("required", buildJsonArray { add(JsonPrimitive("operation")); add(JsonPrimitive("text")) })
                            }
                        )
                    )
                }
                SkillType.HTTP_WEBHOOK -> {
                    val safeName = sanitizeToolName("skill_${skill.slug}")
                    specs.add(
                        ToolSpec(
                            name = safeName,
                            description = "${skill.name}: ${skill.description}",
                            parametersSchema = buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {
                                    put("payload", buildJsonObject { put("type", "string") })
                                })
                            }
                        )
                    )
                }
                SkillType.PROMPT_SKILL -> Unit
            }
        }
        return specs
    }

    suspend fun execute(
        toolName: String,
        argumentsJson: String,
        enabledSkills: List<AgentSkill>
    ): ToolExecutionResult = withContext(Dispatchers.IO) {
        val cacheable = toolName in setOf("web_search", "http_fetch", "calculator", "json_query", "text_transform")
        if (cacheable && isToolCacheEnabled()) {
            val cached = AuraCacheEngine.getMemoizedToolResult(toolName, argumentsJson)
            if (cached != null) {
                onToolCacheHit()
                return@withContext ToolExecutionResult(cached.first, cached.second, fromCache = true)
            }
        }

        val args = try {
            if (argumentsJson.isBlank()) buildJsonObject {}
            else json.parseToJsonElement(argumentsJson).jsonObject
        } catch (_: Exception) {
            buildJsonObject {}
        }

        val result = try {
            when (toolName) {
                "spawn_subagent" -> {
                    val role = args["role"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { "Specialist Subagent" }
                    val task = args["task"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (task.isBlank()) ToolExecutionResult("task is required.", true)
                    else ToolExecutionResult(runSubagent(role, task))
                }
                "web_search" -> runWebSearch(args["query"]?.jsonPrimitive?.contentOrNull.orEmpty())
                "http_fetch" -> runHttpFetch(
                    url = args["url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    method = args["method"]?.jsonPrimitive?.contentOrNull ?: "GET",
                    body = args["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
                )
                "calculator" -> {
                    val expr = args["expression"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    ToolExecutionResult("$expr = ${evalMath(expr)}")
                }
                "device_time" -> {
                    val now = Date()
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z (EEEE)", Locale.US)
                    sdf.timeZone = TimeZone.getDefault()
                    ToolExecutionResult("${sdf.format(now)} | epoch=${now.time / 1000L}")
                }
                "memory_save" -> {
                    val key = args["key"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val content = args["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val category = args["category"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { "general" } ?: "general"
                    if (key.isBlank() || content.isBlank()) {
                        ToolExecutionResult("key and content are required.", true)
                    } else {
                        upsertMemory(key, content, category, emptyList())
                        ToolExecutionResult("Saved memory [$category] '$key'.")
                    }
                }
                "memory_search" -> {
                    val q = args["query"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().lowercase()
                    val mems = getMemories()
                    val matched = if (q.isBlank()) mems else mems.filter {
                        it.key.lowercase().contains(q) || it.content.lowercase().contains(q) || it.category.lowercase().contains(q)
                    }
                    if (matched.isEmpty()) ToolExecutionResult("No matching memories found.")
                    else ToolExecutionResult(matched.take(20).joinToString("\n") { "- [${it.category}] ${it.key}: ${it.content}" })
                }
                "memory_delete" -> {
                    val key = args["key"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (deleteMemoryByKey(key)) ToolExecutionResult("Deleted memory '$key'.")
                    else ToolExecutionResult("Memory '$key' not found.", true)
                }
                "file_workspace" -> runWorkspaceFile(
                    action = args["action"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    filename = args["filename"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    content = args["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
                )
                "json_query" -> runJsonQuery(
                    jsonText = args["json_text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    path = args["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
                )
                "text_transform" -> runTextTransform(
                    op = args["operation"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    text = args["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                )
                else -> {
                    val webhookSkill = enabledSkills.firstOrNull {
                        it.type == SkillType.HTTP_WEBHOOK && sanitizeToolName("skill_${it.slug}") == toolName
                    }
                    if (webhookSkill == null) ToolExecutionResult("Unknown tool: $toolName", true)
                    else executeWebhook(webhookSkill, args["payload"]?.jsonPrimitive?.contentOrNull.orEmpty())
                }
            }
        } catch (e: Exception) {
            ToolExecutionResult("Tool error (${e.javaClass.simpleName}): ${e.message}", true)
        }

        if (cacheable && isToolCacheEnabled() && !result.isError) {
            AuraCacheEngine.putMemoizedToolResult(toolName, argumentsJson, result.output, false)
        }
        result
    }

    private fun runWebSearch(query: String): ToolExecutionResult {
        if (query.isBlank()) return ToolExecutionResult("Query cannot be empty.", true)
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val sb = StringBuilder()

        // 1. DuckDuckGo Instant Answer JSON API (official public instant-answer endpoint)
        runCatching {
            val req = Request.Builder()
                .url("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
                .header("User-Agent", "AuraAgent-Android/3.0")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val abstract = obj["AbstractText"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val url = obj["AbstractURL"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (abstract.isNotBlank()) {
                        sb.appendLine("Instant Summary: $abstract")
                        if (url.isNotBlank()) sb.appendLine("Source: $url")
                    }
                }
            }
        }

        // 2. Wikipedia OpenSearch + Summary API
        runCatching {
            val lang = if (query.any { it in '\u0600'..'\u06FF' }) "fa" else "en"
            val req = Request.Builder()
                .url("https://$lang.wikipedia.org/api/rest_v1/page/summary/${URLEncoder.encode(query.trim().replace(" ", "_"), "UTF-8")}")
                .header("User-Agent", "AuraAgent-Android/3.0")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val extract = obj["extract"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (extract.isNotBlank()) {
                        sb.appendLine("Wikipedia ($lang): ${extract.take(1500)}")
                    }
                }
            }
        }

        return if (sb.isNotBlank()) ToolExecutionResult(sb.toString().trim())
        else ToolExecutionResult("No direct instant summary found for '$query'. Try http_fetch with a specific URL.")
    }

    private fun runHttpFetch(url: String, method: String, body: String): ToolExecutionResult {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            return ToolExecutionResult("Invalid URL: must start with http:// or https://", true)
        }
        val rb = Request.Builder().url(cleanUrl).header("User-Agent", "AuraAgent-Android/3.0")
        if (method.uppercase() == "POST") {
            rb.post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
        } else {
            rb.get()
        }
        httpClient.newCall(rb.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val cleaned = if (raw.trimStart().startsWith("<")) {
                raw.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
                    .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            } else raw
            val clipped = cleaned.take(4500)
            return ToolExecutionResult("HTTP ${resp.code}:\n$clipped", !resp.isSuccessful)
        }
    }

    private fun runWorkspaceFile(action: String, filename: String, content: String): ToolExecutionResult {
        workspaceDir.mkdirs()
        val safeName = filename.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
        return when (action.lowercase()) {
            "list" -> {
                val files = workspaceDir.listFiles()?.map { "${it.name} (${it.length()} B)" }.orEmpty()
                if (files.isEmpty()) ToolExecutionResult("Workspace is empty.")
                else ToolExecutionResult(files.joinToString("\n"))
            }
            "read" -> {
                if (safeName.isBlank()) return ToolExecutionResult("filename required", true)
                val f = File(workspaceDir, safeName)
                if (!f.exists()) ToolExecutionResult("File '$safeName' not found.", true)
                else ToolExecutionResult(f.readText().take(5000))
            }
            "write" -> {
                if (safeName.isBlank()) return ToolExecutionResult("filename required", true)
                File(workspaceDir, safeName).writeText(content)
                ToolExecutionResult("Wrote ${content.length} chars to '$safeName'.")
            }
            "append" -> {
                if (safeName.isBlank()) return ToolExecutionResult("filename required", true)
                File(workspaceDir, safeName).appendText("\n$content")
                ToolExecutionResult("Appended to '$safeName'.")
            }
            "delete" -> {
                val f = File(workspaceDir, safeName)
                if (f.exists() && f.delete()) ToolExecutionResult("Deleted '$safeName'.")
                else ToolExecutionResult("File '$safeName' not found.", true)
            }
            else -> ToolExecutionResult("Unsupported action '$action'. Use list, read, write, append, delete.", true)
        }
    }

    private fun runJsonQuery(jsonText: String, path: String): ToolExecutionResult {
        var curr: JsonElement = json.parseToJsonElement(jsonText)
        for (part in path.split('.').filter { it.isNotBlank() }) {
            curr = when (curr) {
                is JsonObject -> curr[part] ?: return ToolExecutionResult("Key '$part' not found", true)
                is JsonArray -> {
                    val idx = part.toIntOrNull() ?: return ToolExecutionResult("Expected array index, got '$part'", true)
                    curr.getOrNull(idx) ?: return ToolExecutionResult("Index $idx out of bounds", true)
                }
                else -> return ToolExecutionResult("Cannot traverse '$part' on primitive", true)
            }
        }
        return ToolExecutionResult(curr.toString().take(3000))
    }

    private fun runTextTransform(op: String, text: String): ToolExecutionResult {
        return when (op.lowercase()) {
            "base64_encode" -> ToolExecutionResult(Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP))
            "base64_decode" -> ToolExecutionResult(String(Base64.decode(text, Base64.DEFAULT), Charsets.UTF_8))
            "sha256" -> {
                val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
                ToolExecutionResult(digest.joinToString("") { "%02x".format(it) })
            }
            "url_encode" -> ToolExecutionResult(URLEncoder.encode(text, "UTF-8"))
            "stats" -> {
                val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
                ToolExecutionResult("chars=${text.length}, words=$words, approx_tokens=${text.length / 4}")
            }
            else -> ToolExecutionResult("Unknown operation '$op'.", true)
        }
    }

    private fun executeWebhook(skill: AgentSkill, payload: String): ToolExecutionResult {
        val method = skill.webhookMethod.uppercase(Locale.US)
        val url = if (method == "GET" && payload.isNotBlank() && !skill.webhookUrl.contains("?")) {
            "${skill.webhookUrl}?q=${URLEncoder.encode(payload, "UTF-8")}"
        } else {
            skill.webhookUrl
        }
        val builder = Request.Builder().url(url)
        skill.webhookHeaders.forEach { (k, v) -> builder.header(k, v) }
        if (method == "POST" || method == "PUT") {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val bodyStr = if (payload.trim().startsWith("{")) payload else buildJsonObject { put("input", payload) }.toString()
            builder.method(method, bodyStr.toRequestBody(mediaType))
        } else {
            builder.get()
        }
        httpClient.newCall(builder.build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty().take(4000)
            return ToolExecutionResult("HTTP ${resp.code}:\n$body", !resp.isSuccessful)
        }
    }

    companion object {
        fun sanitizeToolName(raw: String): String =
            raw.lowercase(Locale.US).replace(Regex("[^a-z0-9_]"), "_").take(60)

        fun parseSkillMarkdown(rawMarkdown: String): Triple<String, String, String> {
            val trimmed = rawMarkdown.trim()
            if (trimmed.startsWith("---")) {
                val endIdx = trimmed.indexOf("\n---", startIndex = 3)
                if (endIdx != -1) {
                    val frontmatter = trimmed.substring(3, endIdx)
                    val body = trimmed.substring(endIdx + 4).trim()
                    var name = "Custom Skill"
                    var desc = "Imported SKILL.md instruction pack"
                    frontmatter.lines().forEach { line ->
                        val parts = line.split(":", limit = 2)
                        if (parts.size == 2) {
                            val k = parts[0].trim().lowercase(Locale.US)
                            val v = parts[1].trim().trim('"', '\'')
                            if (k == "name" && v.isNotBlank()) name = v
                            if (k == "description" && v.isNotBlank()) desc = v
                        }
                    }
                    return Triple(name, desc, body.ifBlank { trimmed })
                }
            }
            return Triple("Custom Skill", "Custom prompt skill", trimmed)
        }

        fun evalMath(expr: String): Double {
            val cleaned = expr.replace(" ", "")
            var pos = -1
            var ch = 0
            fun nextChar() { pos++; ch = if (pos < cleaned.length) cleaned[pos].code else -1 }
            fun eat(c: Int): Boolean { if (ch == c) { nextChar(); return true }; return false }
            lateinit var parseExpression: () -> Double
            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()
                var x: Double
                val startPos = pos
                if (eat('('.code)) {
                    x = parseExpression()
                    if (!eat(')'.code)) throw IllegalArgumentException("Missing ')'")
                } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                    while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                    x = cleaned.substring(startPos, pos).toDouble()
                } else {
                    throw IllegalArgumentException("Unexpected char: ${ch.toChar()}")
                }
                if (eat('^'.code)) x = x.pow(parseFactor())
                return x
            }
            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    when {
                        eat('*'.code) -> x *= parseFactor()
                        eat('/'.code) -> x /= parseFactor()
                        else -> return x
                    }
                }
            }
            parseExpression = {
                var x = parseTerm()
                while (true) {
                    when {
                        eat('+'.code) -> x += parseTerm()
                        eat('-'.code) -> x -= parseTerm()
                        else -> break
                    }
                }
                x
            }
            nextChar()
            val result = parseExpression()
            if (pos < cleaned.length) throw IllegalArgumentException("Unexpected trailing input")
            return result
        }
    }
}
