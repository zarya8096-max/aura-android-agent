package com.aura.agent.router

import com.aura.agent.data.CacheEntry
import com.aura.agent.data.RouterConfig
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

data class CacheLookupResult(
    val entry: CacheEntry,
    val isExact: Boolean,
    val isCrossSession: Boolean,
    val similarityScore: Float
)

private data class MemoizedToolOutput(
    val output: String,
    val isError: Boolean,
    val expiresAtMs: Long
)

object AuraCacheEngine {
    private val toolMemoCache = ConcurrentHashMap<String, MemoizedToolOutput>()

    fun getMemoizedToolResult(toolName: String, argsJson: String): Pair<String, Boolean>? {
        val key = "$toolName:${sha256Short(argsJson)}"
        val hit = toolMemoCache[key] ?: return null
        if (System.currentTimeMillis() > hit.expiresAtMs) {
            toolMemoCache.remove(key)
            return null
        }
        return hit.output to hit.isError
    }

    fun putMemoizedToolResult(toolName: String, argsJson: String, output: String, isError: Boolean, ttlSeconds: Long = 300L) {
        if (isError) return
        val key = "$toolName:${sha256Short(argsJson)}"
        toolMemoCache[key] = MemoizedToolOutput(output, false, System.currentTimeMillis() + ttlSeconds * 1000L)
    }

    fun clearToolMemo() {
        toolMemoCache.clear()
    }

    /**
     * Bilingual Persian/Arabic/English normalization for high cache hit rates.
     */
    fun normalizeText(raw: String): String {
        return raw
            .lowercase(Locale.ROOT)
            .replace('\u064A', '\u06CC') // Arabic Yeh -> Persian Yeh
            .replace('\u0643', '\u06A9') // Arabic Kaf -> Persian Kaf
            .replace('\u0622', '\u0627') // Alef Madda -> Alef
            .replace('\u0623', '\u0627') // Alef Hamza -> Alef
            .replace('\u200C', ' ')      // ZWNJ -> space
            .replace(Regex("[\u064B-\u065F\u0670]"), "")
            .replace(Regex("[؟?!.,،؛:;\"'()\\[\\]{}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun sha256Short(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.take(8).joinToString("") { "%02x".format(it) }
    }


    private val volatileMarkers = listOf(
        "right now", "today", "current time", "latest news", "weather", "stock price",
        "الان", "امروز", "ساعت چنده", "قیمت لحظه", "آب و هوا", "اخبار امروز"
    )

    fun isTimeSensitiveQuery(prompt: String): Boolean {
        val norm = normalizeText(prompt)
        return volatileMarkers.any { norm.contains(it) }
    }

    fun isStandaloneQuery(prompt: String): Boolean {
        val norm = normalizeText(prompt)
        val contextRefs = listOf("this", "that", "above", "it ", "این ", "اون ", "قبلی", "بالا", "ادامه")
        return norm.length >= 10 && contextRefs.none { norm.contains(it) }
    }

    fun lookup(
        entries: List<CacheEntry>,
        config: RouterConfig,
        gemId: String,
        systemHash: String,
        contextHash: String,
        prompt: String,
        nowMs: Long = System.currentTimeMillis()
    ): CacheLookupResult? {
        if (!config.exactCacheEnabled && !config.semanticCacheEnabled) return null
        if (isTimeSensitiveQuery(prompt)) return null
        val normPrompt = normalizeText(prompt)
        if (normPrompt.length < 3) return null

        val ttlMs = config.cacheTtlMinutes.toLong() * 60_000L
        val validEntries = entries.filter { (nowMs - it.createdAt) <= ttlMs && it.gemId == gemId }
        if (validEntries.isEmpty()) return null

        val standalone = isStandaloneQuery(prompt)

        // Tier 1a: Exact match in same context & system
        if (config.exactCacheEnabled) {
            val exactSameCtx = validEntries.firstOrNull {
                it.systemHash == systemHash && it.contextHash == contextHash && it.normalizedPrompt == normPrompt
            }
            if (exactSameCtx != null) {
                return CacheLookupResult(exactSameCtx, isExact = true, isCrossSession = false, similarityScore = 1.0f)
            }

            // Tier 1b: Cross-session exact match for standalone prompts
            if (config.crossSessionCacheEnabled && standalone) {
                val exactGlobal = validEntries.firstOrNull {
                    it.isStandalone && it.normalizedPrompt == normPrompt
                }
                if (exactGlobal != null) {
                    return CacheLookupResult(exactGlobal, isExact = true, isCrossSession = true, similarityScore = 1.0f)
                }
            }
        }

        // Tier 2: Semantic / Fuzzy similarity (same context first, then cross-session for standalone queries)
        if (config.semanticCacheEnabled && normPrompt.length >= 6) {
            val queryTokens = tokenize(normPrompt)
            val queryGrams = charTrigrams(normPrompt)

            var bestEntry: CacheEntry? = null
            var bestScore = 0f
            var bestCross = false

            for (candidate in validEntries) {
                val sameCtx = candidate.contextHash == contextHash && candidate.systemHash == systemHash
                val allowCross = config.crossSessionCacheEnabled && standalone && candidate.isStandalone
                if (!sameCtx && !allowCross) continue

                val score = hybridSimilarity(
                    queryTokens, queryGrams,
                    tokenize(candidate.normalizedPrompt),
                    charTrigrams(candidate.normalizedPrompt)
                )
                val required = if (sameCtx) config.semanticThreshold else (config.semanticThreshold + 0.03f).coerceAtMost(0.95f)
                if (score >= required && score > bestScore) {
                    bestScore = score
                    bestEntry = candidate
                    bestCross = !sameCtx
                }
            }

            if (bestEntry != null) {
                return CacheLookupResult(bestEntry, isExact = false, isCrossSession = bestCross, similarityScore = bestScore)
            }
        }

        return null
    }

    fun putEntry(
        existing: List<CacheEntry>,
        config: RouterConfig,
        gemId: String,
        systemHash: String,
        contextHash: String,
        prompt: String,
        response: String,
        providerLabel: String
    ): List<CacheEntry> {
        val normPrompt = normalizeText(prompt)
        if (normPrompt.length < 3 || response.isBlank()) return existing

        val newEntry = CacheEntry(
            gemId = gemId,
            systemHash = systemHash,
            contextHash = contextHash,
            normalizedPrompt = normPrompt,
            originalPrompt = prompt,
            response = response,
            providerLabel = providerLabel,
            isStandalone = isStandaloneQuery(prompt)
        )
        val deduplicated = existing.filterNot {
            it.gemId == gemId && it.contextHash == contextHash && it.normalizedPrompt == normPrompt
        }
        return (listOf(newEntry) + deduplicated)
            .sortedByDescending { it.lastAccessedAt }
            .take(config.maxCacheEntries.coerceAtLeast(50))
    }

    fun estimateTokens(text: String): Long = (text.length / 4L).coerceAtLeast(1L)

    private val stopwords = setOf(
        "the", "a", "an", "is", "are", "to", "for", "of", "in", "on", "please", "can", "you", "me", "my",
        "لطفا", "میشه", "برام", "بگو", "یک", "یه", "که", "از", "به", "در", "رو", "را", "این", "اون", "من"
    )

    private fun tokenize(norm: String): Set<String> {
        val raw = norm.split(' ').filter { it.length >= 2 }
        val filtered = raw.filterNot { it in stopwords }
        return (if (filtered.isNotEmpty()) filtered else raw).toSet()
    }

    private fun charTrigrams(norm: String): Map<String, Int> {
        val padded = " $norm "
        val map = HashMap<String, Int>()
        if (padded.length < 3) return map
        for (i in 0..padded.length - 3) {
            val gram = padded.substring(i, i + 3)
            map[gram] = (map[gram] ?: 0) + 1
        }
        return map
    }

    private fun hybridSimilarity(
        t1: Set<String>,
        g1: Map<String, Int>,
        t2: Set<String>,
        g2: Map<String, Int>
    ): Float {
        val jaccard = if (t1.isEmpty() || t2.isEmpty()) 0f else {
            val inter = t1.intersect(t2).size.toFloat()
            val union = t1.union(t2).size.toFloat()
            if (union == 0f) 0f else inter / union
        }
        val cosine = trigramCosine(g1, g2)
        return (0.42f * jaccard) + (0.58f * cosine)
    }

    private fun trigramCosine(a: Map<String, Int>, b: Map<String, Int>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for ((k, v) in a) {
            normA += (v * v).toDouble()
            val bv = b[k]
            if (bv != null) dot += (v * bv).toDouble()
        }
        for (v in b.values) {
            normB += (v * v).toDouble()
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0.0) 0f else (dot / denom).toFloat()
    }
}
