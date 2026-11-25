package com.aura.agent.router

import com.aura.agent.data.ProviderConfig
import com.aura.agent.data.RouterConfig
import com.aura.agent.data.RoutingMode
import com.aura.agent.network.LlmClientFactory
import com.aura.agent.network.LlmResponse
import com.aura.agent.network.LlmTurnMessage
import com.aura.agent.skills.ToolSpec
import java.util.concurrent.ConcurrentHashMap

data class ProviderHealth(
    val providerId: String,
    val consecutiveFailures: Int = 0,
    val cooldownUntilMs: Long = 0L,
    val avgLatencyMs: Long = 0L,
    val totalCalls: Int = 0,
    val lastError: String? = null
) {
    fun isInCooldown(nowMs: Long = System.currentTimeMillis()): Boolean = cooldownUntilMs > nowMs
}

data class RoutedLlmResult(
    val response: LlmResponse,
    val usedProvider: ProviderConfig,
    val fallbackTriggered: Boolean,
    val latencyMs: Long
)

class AuraRouter {
    private val healthMap = ConcurrentHashMap<String, ProviderHealth>()
    private val sessionAffinity = ConcurrentHashMap<String, String>()

    fun getHealthSnapshot(): Map<String, ProviderHealth> = healthMap.toMap()

    fun isConfigured(provider: ProviderConfig): Boolean {
        if (provider.apiKey.isNotBlank()) return true
        val url = provider.baseUrl.lowercase()
        return url.contains("11434") || url.contains("localhost") || url.contains("127.0.0.1") || url.contains("10.0.2.2")
    }

    fun buildCandidateOrder(
        providers: List<ProviderConfig>,
        activeProviderId: String,
        config: RouterConfig,
        prompt: String,
        hasTools: Boolean,
        sessionId: String? = null
    ): List<ProviderConfig> {
        val configured = providers.filter { isConfigured(it) }
        if (configured.isEmpty()) return emptyList()

        val active = configured.firstOrNull { it.id == activeProviderId } ?: configured.first()
        if (config.mode == RoutingMode.MANUAL || !config.enableFallback) {
            return listOf(active)
        }

        val now = System.currentTimeMillis()
        val (healthy, cooling) = configured.partition { !(healthMap[it.id]?.isInCooldown(now) ?: false) }

        // Phase 3: Session-Sticky Cache Affinity — keep warmed provider pinned for the entire session to maximize KV-cache hits
        val stickyId = if (sessionId != null) sessionAffinity[sessionId] else null
        val stickyProvider = stickyId?.let { id -> healthy.firstOrNull { it.id == id } }
        if (stickyProvider != null && config.mode != RoutingMode.MANUAL) {
            return listOf(stickyProvider) + healthy.filterNot { it.id == stickyProvider.id } + cooling
        }

        val rankedHealthy = when (config.mode) {
            RoutingMode.PRIORITY_FALLBACK -> {
                listOfNotNull(healthy.firstOrNull { it.id == active.id }) +
                    healthy.filterNot { it.id == active.id }
            }
            RoutingMode.LOWEST_LATENCY -> {
                healthy.sortedBy { p ->
                    val h = healthMap[p.id]
                    if (h == null || h.avgLatencyMs == 0L) 1500L else h.avgLatencyMs
                }
            }
            RoutingMode.SMART_AUTO -> {
                val complex = isComplexPrompt(prompt, hasTools)
                healthy.sortedByDescending { p ->
                    scoreProviderForComplexity(p, complex, p.id == active.id)
                }
            }
            RoutingMode.MANUAL -> listOf(active)
        }

        return rankedHealthy + cooling
    }

    suspend fun completeWithRouting(
        providers: List<ProviderConfig>,
        activeProviderId: String,
        config: RouterConfig,
        systemPrompt: String,
        messages: List<LlmTurnMessage>,
        tools: List<ToolSpec>,
        latestUserPrompt: String,
        sessionId: String? = null,
        onDelta: (String) -> Unit
    ): RoutedLlmResult {
        val candidates = buildCandidateOrder(
            providers = providers,
            activeProviderId = activeProviderId,
            config = config,
            prompt = latestUserPrompt,
            hasTools = tools.isNotEmpty(),
            sessionId = sessionId
        )
        if (candidates.isEmpty()) {
            throw IllegalStateException("No configured API provider found. Add an API key in Settings.")
        }

        var lastException: Exception? = null
        candidates.forEachIndexed { idx, provider ->
            val start = System.currentTimeMillis()
            try {
                val client = LlmClientFactory.forProtocol(provider.protocol)
                val response = client.complete(
                    provider = provider,
                    systemPrompt = systemPrompt,
                    messages = messages,
                    tools = tools,
                    onDelta = onDelta
                )
                val elapsed = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                recordSuccess(provider.id, elapsed)
                if (sessionId != null) sessionAffinity[sessionId] = provider.id
                return RoutedLlmResult(
                    response = response,
                    usedProvider = provider,
                    fallbackTriggered = idx > 0,
                    latencyMs = elapsed
                )
            } catch (e: Exception) {
                lastException = e
                recordFailure(provider.id, e.message ?: e.javaClass.simpleName)
                if (!config.enableFallback || idx == candidates.lastIndex) {
                    throw e
                }
            }
        }
        throw lastException ?: IllegalStateException("All configured providers failed.")
    }

    private fun recordSuccess(providerId: String, latencyMs: Long) {
        val prev = healthMap[providerId]
        val newAvg = if (prev == null || prev.avgLatencyMs == 0L) {
            latencyMs
        } else {
            ((prev.avgLatencyMs * 3) + latencyMs) / 4
        }
        healthMap[providerId] = ProviderHealth(
            providerId = providerId,
            consecutiveFailures = 0,
            cooldownUntilMs = 0L,
            avgLatencyMs = newAvg,
            totalCalls = (prev?.totalCalls ?: 0) + 1,
            lastError = null
        )
    }

    private fun recordFailure(providerId: String, errorMsg: String) {
        val prev = healthMap[providerId]
        val fails = (prev?.consecutiveFailures ?: 0) + 1
        // Exponential backoff cooldown: 30s, 60s, max 180s
        val cooldownMs = (30_000L * fails).coerceAtMost(180_000L)
        healthMap[providerId] = ProviderHealth(
            providerId = providerId,
            consecutiveFailures = fails,
            cooldownUntilMs = System.currentTimeMillis() + cooldownMs,
            avgLatencyMs = prev?.avgLatencyMs ?: 0L,
            totalCalls = (prev?.totalCalls ?: 0) + 1,
            lastError = errorMsg.take(140)
        )
    }

    private fun isComplexPrompt(prompt: String, hasTools: Boolean): Boolean {
        if (prompt.length > 320 || prompt.contains("```")) return true
        val lower = prompt.lowercase()
        val keywords = listOf(
            "architect", "debug", "refactor", "algorithm", "analyze", "compare",
            "معماری", "دیباگ", "تحلیل", "الگوریتم", "مقایسه", "کد کامل"
        )
        return (hasTools && prompt.length > 140) || keywords.any { lower.contains(it) }
    }

    private fun scoreProviderForComplexity(p: ProviderConfig, complex: Boolean, isActive: Boolean): Int {
        var score = if (isActive) 25 else 0
        val m = p.model.lowercase()
        val isFlagship = m.contains("pro") || m.contains("sonnet") || m.contains("opus") || m.contains("argon") || m.contains("gpt-6") || m.contains("grok-4.7") || m.contains("405b") || m.contains("codex")
        val isFast = m.contains("flash") || m.contains("lite") || m.contains("mini") || m.contains("haiku") || m.contains("8b") || m.contains("32b")

        if (complex && isFlagship) score += 40
        if (!complex && isFast) score += 40

        val h = healthMap[p.id]
        if (h != null && h.avgLatencyMs in 1..1500) score += 10
        return score
    }
}
