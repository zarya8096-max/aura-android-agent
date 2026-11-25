package com.aura.agent

import com.aura.agent.commands.SlashCommandRegistry
import com.aura.agent.data.ApiProtocol
import com.aura.agent.data.CacheEntry
import com.aura.agent.data.ProviderConfig
import com.aura.agent.data.RouterConfig
import com.aura.agent.data.RoutingMode
import com.aura.agent.router.AuraCacheEngine
import com.aura.agent.router.AuraRouter
import com.aura.agent.skills.SkillExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraCoreUnitTest {

    @Test
    fun testPersianAndEnglishNormalization() {
        val a = AuraCacheEngine.normalizeText("سلام، چطور می‌تونم كد بنويسم؟")
        val b = AuraCacheEngine.normalizeText("سلام چطور می تونم کد بنویسم")
        assertEquals(a, b)
    }

    @Test
    fun testExactAndSemanticCrossSessionCacheHit() {
        val cfg = RouterConfig(
            exactCacheEnabled = true,
            semanticCacheEnabled = true,
            crossSessionCacheEnabled = true,
            semanticThreshold = 0.78f
        )
        val sysHash = AuraCacheEngine.sha256Short("sys")
        val ctxA = AuraCacheEngine.sha256Short("ctxA")
        val ctxB = AuraCacheEngine.sha256Short("ctxB")

        val entries = AuraCacheEngine.putEntry(
            existing = emptyList(),
            config = cfg,
            gemId = "gem-1",
            systemHash = sysHash,
            contextHash = ctxA,
            prompt = "How do I reverse a linked list in Kotlin?",
            response = "Use three pointers: prev, curr, next.",
            providerLabel = "Gemini"
        )

        // Exact hit same context
        val exact = AuraCacheEngine.lookup(entries, cfg, "gem-1", sysHash, ctxA, "how do i reverse a linked list in kotlin")
        assertNotNull(exact)
        assertTrue(exact!!.isExact)

        // Cross-session semantic hit in a different conversation
        val sem = AuraCacheEngine.lookup(entries, cfg, "gem-1", sysHash, ctxB, "How do I reverse a linked list in Kotlin language?")
        assertNotNull(sem)
        assertTrue(sem!!.isCrossSession)
        assertTrue(sem.similarityScore >= 0.78f)
    }

    @Test
    fun testToolMemoizationCache() {
        AuraCacheEngine.clearToolMemo()
        AuraCacheEngine.putMemoizedToolResult("calculator", """{"expression":"2+2"}""", "2+2 = 4.0", false)
        val hit = AuraCacheEngine.getMemoizedToolResult("calculator", """{"expression":"2+2"}""")
        assertNotNull(hit)
        assertEquals("2+2 = 4.0", hit!!.first)
    }

    @Test
    fun testAuraRouterSmartComplexitySelection() {
        val router = AuraRouter()
        val fastProvider = ProviderConfig(
            id = "fast",
            name = "Fast",
            protocol = ApiProtocol.GEMINI,
            baseUrl = "https://example.com",
            apiKey = "k1",
            model = "gemini-2.5-flash"
        )
        val proProvider = ProviderConfig(
            id = "pro",
            name = "Pro",
            protocol = ApiProtocol.ANTHROPIC,
            baseUrl = "https://example.com",
            apiKey = "k2",
            model = "claude-3-7-sonnet-latest"
        )
        val cfg = RouterConfig(mode = RoutingMode.SMART_AUTO, enableFallback = true)

        val simpleOrder = router.buildCandidateOrder(listOf(proProvider, fastProvider), "pro", cfg, "Hi there", false)
        assertEquals("fast", simpleOrder.first().id)

        val complexOrder = router.buildCandidateOrder(
            listOf(fastProvider, proProvider),
            "fast",
            cfg,
            "Please architect and debug a distributed Raft consensus algorithm in Kotlin with full edge-case analysis.",
            true
        )
        assertEquals("pro", complexOrder.first().id)
    }

    @Test
    fun testSkillExecutorMathAndMarkdownParser() {
        assertEquals(20.0, SkillExecutor.evalMath("(2 + 3) * 4"), 0.0001)
        assertEquals(16.0, SkillExecutor.evalMath("2 ^ 4"), 0.0001)

        val (name, desc, body) = SkillExecutor.parseSkillMarkdown(
            "---\nname: test-skill\ndescription: A test skill\n---\nAlways answer in bullet points."
        )
        assertEquals("test-skill", name)
        assertEquals("A test skill", desc)
        assertEquals("Always answer in bullet points.", body)
    }

    @Test
    fun testSlashCommandRegistryCoverage() {
        val cmds = SlashCommandRegistry.all.map { it.command }.toSet()
        assertTrue("/compact" in cmds)
        assertTrue("/subagent" in cmds)
        assertTrue("/effort" in cmds)
        assertTrue("/policy" in cmds)
        assertTrue("/remember" in cmds)
        assertTrue(SlashCommandRegistry.filter("/co").any { it.command == "/compact" })
    }
}
