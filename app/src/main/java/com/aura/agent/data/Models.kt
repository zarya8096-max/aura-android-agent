package com.aura.agent.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ApiProtocol(val label: String) {
    GEMINI("Google Gemini"),
    OPENAI_COMPATIBLE("OpenAI-Compatible"),
    ANTHROPIC("Anthropic Claude")
}

@Serializable
enum class RoutingMode(val label: String, val subtitle: String) {
    SMART_AUTO("Smart Auto", "Routes simple prompts to fast models & complex tasks to flagship models with auto-fallback"),
    PRIORITY_FALLBACK("Priority Fallback", "Uses active provider first and fails over automatically on 429/5xx errors"),
    LOWEST_LATENCY("Lowest Latency", "Picks the fastest healthy provider based on measured response time"),
    MANUAL("Manual", "Always uses the selected provider only")
}

@Serializable
data class RouterConfig(
    val mode: RoutingMode = RoutingMode.SMART_AUTO,
    val enableFallback: Boolean = true,
    val exactCacheEnabled: Boolean = true,
    val semanticCacheEnabled: Boolean = true,
    val semanticThreshold: Float = 0.84f,
    val cacheTtlMinutes: Int = 1440,
    val maxCacheEntries: Int = 250
)

@Serializable
data class CacheEntry(
    val id: String = UUID.randomUUID().toString(),
    val gemId: String = "",
    val systemHash: String,
    val contextHash: String,
    val normalizedPrompt: String,
    val originalPrompt: String,
    val response: String,
    val providerLabel: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val hitCount: Int = 0
)

@Serializable
data class RouterStats(
    val totalRequests: Int = 0,
    val exactCacheHits: Int = 0,
    val semanticCacheHits: Int = 0,
    val fallbackRecoveries: Int = 0,
    val estimatedTokensSaved: Long = 0L
) {
    val totalCacheHits: Int get() = exactCacheHits + semanticCacheHits
    val hitRatePercent: Int
        get() = if (totalRequests <= 0) 0 else ((totalCacheHits * 100f) / totalRequests).toInt().coerceIn(0, 100)
}

@Serializable
data class ProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: ApiProtocol,
    val baseUrl: String,
    val apiKey: String = "",
    val model: String,
    val availableModels: List<String> = emptyList(),
    val customHeaders: Map<String, String> = emptyMap(),
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096
)

@Serializable
data class Gem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val instructions: String,
    val iconName: String = "sparkle",
    val accentHex: Long = 0xFF8AB4F8,
    val defaultSkillIds: List<String> = emptyList(),
    val preferredProviderId: String? = null,
    val isBuiltIn: Boolean = false
)

@Serializable
enum class SkillType(val label: String) {
    PROMPT_SKILL("Prompt Skill (SKILL.md)"),
    BUILTIN_TOOL("Built-in Agent Tool"),
    HTTP_WEBHOOK("Custom HTTP API Tool")
}

@Serializable
data class AgentSkill(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val slug: String,
    val description: String,
    val type: SkillType,
    val enabled: Boolean = true,
    val instructions: String = "",
    val webhookUrl: String = "",
    val webhookMethod: String = "GET",
    val webhookHeaders: Map<String, String> = emptyMap(),
    val isBuiltIn: Boolean = false
)

@Serializable
data class ToolCallTrace(
    val toolName: String,
    val arguments: String,
    val result: String,
    val isError: Boolean = false
)

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolTraces: List<ToolCallTrace> = emptyList(),
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    val cacheHitBadge: String? = null,
    val routedProviderBadge: String? = null
)

@Serializable
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val gemId: String? = null,
    val providerId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class AgentNote(
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class AppStateSnapshot(
    val providers: List<ProviderConfig> = emptyList(),
    val activeProviderId: String = "",
    val gems: List<Gem> = emptyList(),
    val skills: List<AgentSkill> = emptyList(),
    val sessions: List<ChatSession> = emptyList(),
    val notes: List<AgentNote> = emptyList(),
    val darkMode: Boolean = true,
    val routerConfig: RouterConfig = RouterConfig(),
    val cacheEntries: List<CacheEntry> = emptyList(),
    val routerStats: RouterStats = RouterStats()
)
