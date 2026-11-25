package com.aura.agent.router

import com.aura.agent.data.CacheEntry
import com.aura.agent.data.RouterConfig
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.sqrt

data class CacheLookupResult(
    val entry: CacheEntry,
    val isExact: Boolean,
    val similarityScore: Float
)

object AuraCacheEngine {

    /**
     * Normalizes both English and Persian/Arabic text to maximize cache hit rates
     * (unifies Arabic/Persian Yeh/Kaf, strips ZWNJ/diacritics, collapses whitespace and punctuation).
     */
    fun normalizeText(raw: String): String {
        val unified = raw
            .lowercase(Locale.ROOT)
            .replace('\u064A', '\u06CC') // Arabic Yeh -> Persian Yeh
            .replace('\u0643', '\u06A9') // Arabic Kaf -> Persian Kaf
            .replace('\u200C', ' ')      // ZWNJ -> space
            .replace(Regex("[\u064B-\u065F\u0670]"), "") // Strip Arabic diacritics
            .replace(Regex("[؟?!.,،؛:;\"'()\\[\\]{}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return unified
    }

    fun sha256Short(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.take(8).joinToString("") { "%02x".format(it) }
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
        val normPrompt = normalizeText(prompt)
        if (normPrompt.length < 3) return null

        val ttlMs = config.cacheTtlMinutes.toLong() * 60_000L
        val validEntries = entries.filter { (nowMs - it.createdAt) <= ttlMs && it.gemId == gemId && it.systemHash == systemHash }
        if (validEntries.isEmpty()) return null

        // Layer 1: Exact normalized match (same context)
        if (config.exactCacheEnabled) {
            val exact = validEntries.firstOrNull {
                it.contextHash == contextHash && it.normalizedPrompt == normPrompt
            }
            if (exact != null) {
                return CacheLookupResult(exact, isExact = true, similarityScore = 1.0f)
            }
        }

        // Layer 2: Semantic / Fuzzy similarity match (word Jaccard + char 3-gram cosine)
        if (config.semanticCacheEnabled && normPrompt.length >= 8) {
            val queryTokens = tokenize(normPrompt)
            val queryGrams = charTrigrams(normPrompt)

            var bestEntry: CacheEntry? = null
            var bestScore = 0f

            for (candidate in validEntries) {
                if (candidate.contextHash != contextHash) continue
                val score = hybridSimilarity(
                    queryTokens, queryGrams,
                    tokenize(candidate.normalizedPrompt),
                    charTrigrams(candidate.normalizedPrompt)
                )
                if (score >= config.semanticThreshold && score > bestScore) {
                    bestScore = score
                    bestEntry = candidate
                }
            }

            if (bestEntry != null) {
                return CacheLookupResult(bestEntry, isExact = false, similarityScore = bestScore)
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
            providerLabel = providerLabel
        )
        val deduplicated = existing.filterNot {
            it.gemId == gemId && it.systemHash == systemHash &&
                it.contextHash == contextHash && it.normalizedPrompt == normPrompt
        }
        return (listOf(newEntry) + deduplicated)
            .sortedByDescending { it.lastAccessedAt }
            .take(config.maxCacheEntries.coerceAtLeast(20))
    }

    fun estimateTokens(text: String): Long = (text.length / 4L).coerceAtLeast(1L)

    private fun tokenize(norm: String): Set<String> =
        norm.split(' ').filter { it.length >= 2 }.toSet()

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
        return (0.45f * jaccard) + (0.55f * cosine)
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
