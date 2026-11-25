package com.aura.agent.skills

import com.aura.agent.data.AgentNote
import com.aura.agent.data.AgentSkill
import com.aura.agent.data.SkillType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    val isError: Boolean = false
)

class SkillExecutor(
    private val getNotes: () -> List<AgentNote>,
    private val upsertNote: (String, String) -> Unit
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun buildToolSpecs(enabledSkills: List<AgentSkill>): List<ToolSpec> {
        val specs = mutableListOf<ToolSpec>()
        for (skill in enabledSkills) {
            when (skill.type) {
                SkillType.BUILTIN_TOOL -> {
                    when (skill.slug) {
                        "http_fetch" -> specs.add(
                            ToolSpec(
                                name = "http_fetch",
                                description = "Perform an HTTP GET request to a URL and return the response body (truncated to 4000 chars).",
                                parametersSchema = buildJsonObject {
                                    put("type", "object")
                                    put("properties", buildJsonObject {
                                        put("url", buildJsonObject {
                                            put("type", "string")
                                            put("description", "Full https:// or http:// URL to fetch.")
                                        })
                                    })
                                    put("required", buildJsonArray { add(JsonPrimitive("url")) })
                                }
                            )
                        )
                        "calculator" -> specs.add(
                            ToolSpec(
                                name = "calculator",
                                description = "Evaluate a mathematical expression supporting +, -, *, /, ^, and parentheses.",
                                parametersSchema = buildJsonObject {
                                    put("type", "object")
                                    put("properties", buildJsonObject {
                                        put("expression", buildJsonObject {
                                            put("type", "string")
                                            put("description", "Math expression, e.g. '(42 * 3.5) / 2'")
                                        })
                                    })
                                    put("required", buildJsonArray { add(JsonPrimitive("expression")) })
                                }
                            )
                        )
                        "device_time" -> specs.add(
                            ToolSpec(
                                name = "device_time",
                                description = "Get the current date, time, and timezone from the user's Android device.",
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
                                    description = "Save or update a persistent note locally on the user's Android device.",
                                    parametersSchema = buildJsonObject {
                                        put("type", "object")
                                        put("properties", buildJsonObject {
                                            put("key", buildJsonObject {
                                                put("type", "string")
                                                put("description", "Short identifier for the note.")
                                            })
                                            put("value", buildJsonObject {
                                                put("type", "string")
                                                put("description", "Content to remember.")
                                            })
                                        })
                                        put("required", buildJsonArray {
                                            add(JsonPrimitive("key"))
                                            add(JsonPrimitive("value"))
                                        })
                                    }
                                )
                            )
                            specs.add(
                                ToolSpec(
                                    name = "memory_read",
                                    description = "List all saved local notes or retrieve a specific note by key.",
                                    parametersSchema = buildJsonObject {
                                        put("type", "object")
                                        put("properties", buildJsonObject {
                                            put("key", buildJsonObject {
                                                put("type", "string")
                                                put("description", "Optional key to filter by. Leave empty to list all notes.")
                                            })
                                        })
                                    }
                                )
                            )
                        }
                    }
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
                                    put("payload", buildJsonObject {
                                        put("type", "string")
                                        put("description", "Optional JSON body (for POST) or query string (for GET).")
                                    })
                                })
                            }
                        )
                    )
                }
                SkillType.PROMPT_SKILL -> Unit // Injected into system prompt
            }
        }
        return specs
    }

    suspend fun execute(
        toolName: String,
        argumentsJson: String,
        enabledSkills: List<AgentSkill>
    ): ToolExecutionResult = withContext(Dispatchers.IO) {
        val args = try {
            if (argumentsJson.isBlank()) buildJsonObject {}
            else json.parseToJsonElement(argumentsJson).jsonObject
        } catch (_: Exception) {
            buildJsonObject {}
        }

        try {
            when (toolName) {
                "http_fetch" -> {
                    val url = args["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        return@withContext ToolExecutionResult("Invalid URL: must start with http:// or https://", true)
                    }
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "AuraAgent-Android/1.0")
                        .get()
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        val body = resp.body?.string().orEmpty().take(4000)
                        if (resp.isSuccessful) {
                            ToolExecutionResult("HTTP ${resp.code}:\n$body")
                        } else {
                            ToolExecutionResult("HTTP ${resp.code} error:\n$body", true)
                        }
                    }
                }
                "calculator" -> {
                    val expr = args["expression"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val result = evalMath(expr)
                    ToolExecutionResult("$expr = $result")
                }
                "device_time" -> {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z (EEEE)", Locale.US)
                    sdf.timeZone = TimeZone.getDefault()
                    ToolExecutionResult(sdf.format(Date()))
                }
                "memory_save" -> {
                    val key = args["key"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val value = args["value"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (key.isBlank()) {
                        ToolExecutionResult("Error: key cannot be empty.", true)
                    } else {
                        upsertNote(key, value)
                        ToolExecutionResult("Saved note '$key'.")
                    }
                }
                "memory_read" -> {
                    val key = args["key"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val notes = getNotes()
                    if (notes.isEmpty()) {
                        ToolExecutionResult("No saved notes yet.")
                    } else if (key.isNotBlank()) {
                        val found = notes.firstOrNull { it.key.equals(key, ignoreCase = true) }
                        if (found != null) ToolExecutionResult("${found.key}: ${found.value}")
                        else ToolExecutionResult("No note found for key '$key'.")
                    } else {
                        ToolExecutionResult(notes.joinToString("\n") { "- ${it.key}: ${it.value}" })
                    }
                }
                else -> {
                    val webhookSkill = enabledSkills.firstOrNull {
                        it.type == SkillType.HTTP_WEBHOOK &&
                            sanitizeToolName("skill_${it.slug}") == toolName
                    }
                    if (webhookSkill == null) {
                        ToolExecutionResult("Unknown tool: $toolName", true)
                    } else {
                        executeWebhook(webhookSkill, args["payload"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    }
                }
            }
        } catch (e: Exception) {
            ToolExecutionResult("Tool error (${e.javaClass.simpleName}): ${e.message}", true)
        }
    }

    private fun executeWebhook(skill: AgentSkill, payload: String): ToolExecutionResult {
        val method = skill.webhookMethod.uppercase(Locale.US)
        val url = if (method == "GET" && payload.isNotBlank() && !skill.webhookUrl.contains("?")) {
            "${skill.webhookUrl}?q=${java.net.URLEncoder.encode(payload, "UTF-8")}"
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

        /**
         * Parses a SKILL.md document with optional YAML frontmatter (name, description).
         */
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

        /**
         * Lightweight recursive-descent math parser (supports +, -, *, /, ^, parentheses).
         */
        fun evalMath(expr: String): Double {
            val cleaned = expr.replace(" ", "")
            var pos = -1
            var ch = 0

            fun nextChar() {
                pos++
                ch = if (pos < cleaned.length) cleaned[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

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
                    throw IllegalArgumentException("Unexpected character: ${ch.toChar()}")
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
