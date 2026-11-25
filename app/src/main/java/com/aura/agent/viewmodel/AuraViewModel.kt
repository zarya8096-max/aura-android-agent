package com.aura.agent.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aura.agent.data.AgentNote
import com.aura.agent.data.AgentSkill
import com.aura.agent.data.AppStateSnapshot
import com.aura.agent.data.CacheEntry
import com.aura.agent.data.ChatMessage
import com.aura.agent.data.ChatSession
import com.aura.agent.data.Gem
import com.aura.agent.data.LocalStore
import com.aura.agent.data.ProviderConfig
import com.aura.agent.data.RouterConfig
import com.aura.agent.data.RouterStats
import com.aura.agent.data.SkillType
import com.aura.agent.data.ToolCallTrace
import com.aura.agent.network.LlmTurnMessage
import com.aura.agent.router.AuraCacheEngine
import com.aura.agent.router.AuraRouter
import com.aura.agent.router.ProviderHealth
import com.aura.agent.skills.SkillExecutor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActiveScreen {
    CHAT, GEMS, SKILLS, ROUTER, SETTINGS
}

data class AuraUiState(
    val providers: List<ProviderConfig> = emptyList(),
    val activeProviderId: String = "",
    val gems: List<Gem> = emptyList(),
    val skills: List<AgentSkill> = emptyList(),
    val sessions: List<ChatSession> = emptyList(),
    val activeSessionId: String? = null,
    val activeGemId: String? = null,
    val notes: List<AgentNote> = emptyList(),
    val darkMode: Boolean = true,
    val isGenerating: Boolean = false,
    val activeScreen: ActiveScreen = ActiveScreen.CHAT,
    val routerConfig: RouterConfig = RouterConfig(),
    val cacheEntries: List<CacheEntry> = emptyList(),
    val routerStats: RouterStats = RouterStats(),
    val providerHealth: Map<String, ProviderHealth> = emptyMap()
) {
    val activeProvider: ProviderConfig?
        get() = providers.firstOrNull { it.id == activeProviderId } ?: providers.firstOrNull()

    val activeSession: ChatSession?
        get() = sessions.firstOrNull { it.id == activeSessionId }

    val activeGem: Gem?
        get() {
            val gId = activeSession?.gemId ?: activeGemId
            return gems.firstOrNull { it.id == gId }
        }
}

class AuraViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application.applicationContext)
    private val router = AuraRouter()
    private val _state = MutableStateFlow(AuraUiState())
    val state: StateFlow<AuraUiState> = _state.asStateFlow()

    private var activeJob: Job? = null

    private val skillExecutor = SkillExecutor(
        getNotes = { _state.value.notes },
        upsertNote = { key, value -> upsertNote(key, value) }
    )

    init {
        val snap = store.load()
        _state.value = AuraUiState(
            providers = snap.providers,
            activeProviderId = snap.activeProviderId,
            gems = snap.gems,
            skills = snap.skills,
            sessions = snap.sessions,
            activeSessionId = snap.sessions.firstOrNull()?.id,
            activeGemId = snap.sessions.firstOrNull()?.gemId,
            notes = snap.notes,
            darkMode = snap.darkMode,
            routerConfig = snap.routerConfig,
            cacheEntries = snap.cacheEntries,
            routerStats = snap.routerStats
        )
    }

    private fun persist() {
        val s = _state.value
        store.save(
            AppStateSnapshot(
                providers = s.providers,
                activeProviderId = s.activeProviderId,
                gems = s.gems,
                skills = s.skills,
                sessions = s.sessions,
                notes = s.notes,
                darkMode = s.darkMode,
                routerConfig = s.routerConfig,
                cacheEntries = s.cacheEntries,
                routerStats = s.routerStats
            )
        )
    }

    fun navigateTo(screen: ActiveScreen) {
        _state.update { it.copy(activeScreen = screen, providerHealth = router.getHealthSnapshot()) }
    }

    fun toggleDarkMode() {
        _state.update { it.copy(darkMode = !it.darkMode) }
        persist()
    }

    fun updateRouterConfig(config: RouterConfig) {
        _state.update { it.copy(routerConfig = config) }
        persist()
    }

    fun clearCache() {
        _state.update { it.copy(cacheEntries = emptyList()) }
        persist()
    }

    fun startNewChat(gemId: String? = null) {
        activeJob?.cancel()
        val gem = gemId?.let { id -> _state.value.gems.firstOrNull { it.id == id } }
        _state.update { s ->
            val updatedProviderId = gem?.preferredProviderId?.takeIf { pid ->
                s.providers.any { it.id == pid }
            } ?: s.activeProviderId
            s.copy(
                activeSessionId = null,
                activeGemId = gemId,
                activeProviderId = updatedProviderId,
                activeScreen = ActiveScreen.CHAT,
                isGenerating = false
            )
        }
    }

    fun selectSession(sessionId: String) {
        activeJob?.cancel()
        val session = _state.value.sessions.firstOrNull { it.id == sessionId } ?: return
        _state.update {
            it.copy(
                activeSessionId = session.id,
                activeGemId = session.gemId,
                activeScreen = ActiveScreen.CHAT,
                isGenerating = false
            )
        }
    }

    fun deleteSession(sessionId: String) {
        _state.update { s ->
            val remaining = s.sessions.filterNot { it.id == sessionId }
            val nextActive = if (s.activeSessionId == sessionId) remaining.firstOrNull()?.id else s.activeSessionId
            s.copy(sessions = remaining, activeSessionId = nextActive)
        }
        persist()
    }

    fun selectProvider(providerId: String) {
        _state.update { it.copy(activeProviderId = providerId) }
        persist()
    }

    fun updateProvider(provider: ProviderConfig) {
        _state.update { s ->
            val exists = s.providers.any { it.id == provider.id }
            val updated = if (exists) {
                s.providers.map { if (it.id == provider.id) provider else it }
            } else {
                s.providers + provider
            }
            s.copy(providers = updated, activeProviderId = provider.id)
        }
        persist()
    }

    fun deleteProvider(providerId: String) {
        _state.update { s ->
            if (s.providers.size <= 1) return@update s
            val remaining = s.providers.filterNot { it.id == providerId }
            val nextId = if (s.activeProviderId == providerId) remaining.first().id else s.activeProviderId
            s.copy(providers = remaining, activeProviderId = nextId)
        }
        persist()
    }

    fun saveGem(gem: Gem) {
        _state.update { s ->
            val exists = s.gems.any { it.id == gem.id }
            val updated = if (exists) {
                s.gems.map { if (it.id == gem.id) gem else it }
            } else {
                listOf(gem) + s.gems
            }
            s.copy(gems = updated)
        }
        persist()
    }

    fun deleteGem(gemId: String) {
        _state.update { s ->
            s.copy(
                gems = s.gems.filterNot { it.id == gemId },
                activeGemId = if (s.activeGemId == gemId) null else s.activeGemId
            )
        }
        persist()
    }

    fun toggleSkill(skillId: String) {
        _state.update { s ->
            s.copy(skills = s.skills.map { if (it.id == skillId) it.copy(enabled = !it.enabled) else it })
        }
        persist()
    }

    fun saveSkill(skill: AgentSkill) {
        _state.update { s ->
            val exists = s.skills.any { it.id == skill.id }
            val updated = if (exists) {
                s.skills.map { if (it.id == skill.id) skill else it }
            } else {
                listOf(skill) + s.skills
            }
            s.copy(skills = updated)
        }
        persist()
    }

    fun deleteSkill(skillId: String) {
        _state.update { s ->
            s.copy(skills = s.skills.filterNot { it.id == skillId })
        }
        persist()
    }

    private fun upsertNote(key: String, value: String) {
        _state.update { s ->
            val filtered = s.notes.filterNot { it.key.equals(key, ignoreCase = true) }
            s.copy(notes = listOf(AgentNote(key, value)) + filtered)
        }
        persist()
    }

    fun stopGeneration() {
        activeJob?.cancel()
        _state.update { s ->
            val sid = s.activeSessionId
            val updatedSessions = s.sessions.map { sess ->
                if (sess.id == sid) {
                    sess.copy(messages = sess.messages.map { it.copy(isStreaming = false) })
                } else sess
            }
            s.copy(sessions = updatedSessions, isGenerating = false)
        }
        persist()
    }

    fun sendMessage(userText: String, bypassCache: Boolean = false) {
        val prompt = userText.trim()
        if (prompt.isEmpty() || _state.value.isGenerating) return

        val snap = _state.value
        val hasAnyConfigured = snap.providers.any { router.isConfigured(it) }
        if (!hasAnyConfigured) {
            appendErrorSessionMessage(
                prompt,
                "No API key configured yet. Open API Providers & Settings from the drawer to add at least one provider key."
            )
            return
        }

        val currentGem = snap.activeGem
        val gemId = currentGem?.id.orEmpty()
        val enabledSkills = resolveEnabledSkills(currentGem)
        val systemPrompt = buildSystemPrompt(currentGem, enabledSkills)
        val systemHash = AuraCacheEngine.sha256Short(systemPrompt)

        val priorContext = snap.activeSession?.messages
            ?.filter { !it.isError && it.content.isNotBlank() }
            ?.takeLast(4)
            ?.joinToString("|") { "${it.role}:${AuraCacheEngine.normalizeText(it.content).take(80)}" }
            .orEmpty()
        val contextHash = AuraCacheEngine.sha256Short(priorContext)

        // Increment total requests stat
        _state.update { s -> s.copy(routerStats = s.routerStats.copy(totalRequests = s.routerStats.totalRequests + 1)) }

        // Check Aura Multi-Layer Cache (Exact + Bilingual Semantic Similarity)
        if (!bypassCache) {
            val hit = AuraCacheEngine.lookup(
                entries = _state.value.cacheEntries,
                config = _state.value.routerConfig,
                gemId = gemId,
                systemHash = systemHash,
                contextHash = contextHash,
                prompt = prompt
            )
            if (hit != null) {
                val pct = (hit.similarityScore * 100).toInt().coerceIn(1, 100)
                val badge = if (hit.isExact) "Exact Cache (0ms)" else "Semantic Cache ($pct% match)"
                val savedTokens = AuraCacheEngine.estimateTokens(systemPrompt + prompt + hit.entry.response)
                val userMsg = ChatMessage(role = "user", content = prompt)
                val cachedReply = ChatMessage(
                    role = "assistant",
                    content = hit.entry.response,
                    cacheHitBadge = badge,
                    routedProviderBadge = hit.entry.providerLabel
                )
                _state.update { s ->
                    val updatedEntries = s.cacheEntries.map { ce ->
                        if (ce.id == hit.entry.id) {
                            ce.copy(hitCount = ce.hitCount + 1, lastAccessedAt = System.currentTimeMillis())
                        } else ce
                    }
                    val updatedStats = s.routerStats.copy(
                        exactCacheHits = s.routerStats.exactCacheHits + if (hit.isExact) 1 else 0,
                        semanticCacheHits = s.routerStats.semanticCacheHits + if (hit.isExact) 0 else 1,
                        estimatedTokensSaved = s.routerStats.estimatedTokensSaved + savedTokens
                    )
                    val existing = s.activeSession
                    val newSessions = if (existing == null) {
                        val ns = ChatSession(
                            title = prompt.take(36),
                            gemId = currentGem?.id,
                            providerId = s.activeProviderId,
                            messages = listOf(userMsg, cachedReply)
                        )
                        listOf(ns) + s.sessions
                    } else {
                        s.sessions.map { sess ->
                            if (sess.id == existing.id) {
                                sess.copy(messages = sess.messages + userMsg + cachedReply, updatedAt = System.currentTimeMillis())
                            } else sess
                        }
                    }
                    val newActiveId = existing?.id ?: newSessions.first().id
                    s.copy(
                        sessions = newSessions,
                        activeSessionId = newActiveId,
                        cacheEntries = updatedEntries,
                        routerStats = updatedStats
                    )
                }
                persist()
                return
            }
        }

        val userMsg = ChatMessage(role = "user", content = prompt)
        val assistantMsg = ChatMessage(role = "assistant", content = "", isStreaming = true)

        val sessionId: String
        val existing = _state.value.activeSession
        if (existing == null) {
            val newSession = ChatSession(
                title = prompt.take(36),
                gemId = currentGem?.id,
                providerId = _state.value.activeProviderId,
                messages = listOf(userMsg, assistantMsg)
            )
            sessionId = newSession.id
            _state.update { s ->
                s.copy(
                    sessions = listOf(newSession) + s.sessions,
                    activeSessionId = sessionId,
                    isGenerating = true
                )
            }
        } else {
            sessionId = existing.id
            _state.update { s ->
                s.copy(
                    sessions = s.sessions.map { sess ->
                        if (sess.id == sessionId) {
                            sess.copy(
                                messages = sess.messages + userMsg + assistantMsg,
                                updatedAt = System.currentTimeMillis()
                            )
                        } else sess
                    },
                    isGenerating = true
                )
            }
        }
        persist()

        val toolSpecs = skillExecutor.buildToolSpecs(enabledSkills)

        activeJob = viewModelScope.launch {
            try {
                val historyTurns = buildInitialTurnMessages(sessionId)
                val traces = mutableListOf<ToolCallTrace>()
                val streamedBuffer = StringBuilder()
                var usedProviderLabel = ""
                var anyFallback = false

                var round = 0
                while (round < 5) {
                    round++
                    val routed = router.completeWithRouting(
                        providers = _state.value.providers,
                        activeProviderId = _state.value.activeProviderId,
                        config = _state.value.routerConfig,
                        systemPrompt = systemPrompt,
                        messages = historyTurns,
                        tools = toolSpecs,
                        latestUserPrompt = prompt,
                        onDelta = { delta ->
                            streamedBuffer.append(delta)
                            updateAssistantMessage(
                                sessionId = sessionId,
                                messageId = assistantMsg.id,
                                content = streamedBuffer.toString(),
                                traces = traces.toList(),
                                streaming = true,
                                isError = false,
                                routedBadge = usedProviderLabel.ifBlank { null }
                            )
                        }
                    )

                    usedProviderLabel = "${routed.usedProvider.name} · ${routed.usedProvider.model}" +
                        if (routed.fallbackTriggered) " (Fallback)" else ""
                    if (routed.fallbackTriggered) anyFallback = true

                    if (routed.response.toolCalls.isEmpty()) {
                        if (streamedBuffer.isEmpty() && routed.response.text.isNotBlank()) {
                            streamedBuffer.append(routed.response.text)
                        }
                        break
                    }

                    historyTurns.add(
                        LlmTurnMessage(
                            role = "assistant",
                            content = routed.response.text,
                            toolCalls = routed.response.toolCalls
                        )
                    )

                    for (tc in routed.response.toolCalls) {
                        val execResult = skillExecutor.execute(tc.name, tc.argumentsJson, enabledSkills)
                        traces.add(
                            ToolCallTrace(
                                toolName = tc.name,
                                arguments = tc.argumentsJson,
                                result = execResult.output,
                                isError = execResult.isError
                            )
                        )
                        updateAssistantMessage(
                            sessionId = sessionId,
                            messageId = assistantMsg.id,
                            content = streamedBuffer.toString(),
                            traces = traces.toList(),
                            streaming = true,
                            isError = false,
                            routedBadge = usedProviderLabel
                        )
                        historyTurns.add(
                            LlmTurnMessage(
                                role = "tool",
                                content = execResult.output,
                                toolCallId = tc.id,
                                toolName = tc.name
                            )
                        )
                    }

                    if (streamedBuffer.isNotEmpty() && !streamedBuffer.endsWith("\n\n")) {
                        streamedBuffer.append("\n\n")
                    }
                }

                val finalContent = streamedBuffer.toString().ifBlank { "(No response returned)" }
                updateAssistantMessage(
                    sessionId = sessionId,
                    messageId = assistantMsg.id,
                    content = finalContent,
                    traces = traces.toList(),
                    streaming = false,
                    isError = false,
                    routedBadge = usedProviderLabel
                )

                // Store non-tool answers in AuraCache for instant future hits
                if (traces.isEmpty() && finalContent != "(No response returned)") {
                    _state.update { s ->
                        val newCache = AuraCacheEngine.putEntry(
                            existing = s.cacheEntries,
                            config = s.routerConfig,
                            gemId = gemId,
                            systemHash = systemHash,
                            contextHash = contextHash,
                            prompt = prompt,
                            response = finalContent,
                            providerLabel = usedProviderLabel
                        )
                        val newStats = if (anyFallback) {
                            s.routerStats.copy(fallbackRecoveries = s.routerStats.fallbackRecoveries + 1)
                        } else s.routerStats
                        s.copy(
                            cacheEntries = newCache,
                            routerStats = newStats,
                            providerHealth = router.getHealthSnapshot()
                        )
                    }
                } else {
                    _state.update { s ->
                        val newStats = if (anyFallback) {
                            s.routerStats.copy(fallbackRecoveries = s.routerStats.fallbackRecoveries + 1)
                        } else s.routerStats
                        s.copy(routerStats = newStats, providerHealth = router.getHealthSnapshot())
                    }
                }
            } catch (e: Exception) {
                updateAssistantMessage(
                    sessionId = sessionId,
                    messageId = assistantMsg.id,
                    content = "Request failed: ${e.message ?: e.javaClass.simpleName}",
                    traces = emptyList(),
                    streaming = false,
                    isError = true,
                    routedBadge = null
                )
                _state.update { it.copy(providerHealth = router.getHealthSnapshot()) }
            } finally {
                _state.update { it.copy(isGenerating = false) }
                persist()
            }
        }
    }

    private fun resolveEnabledSkills(gem: Gem?): List<AgentSkill> {
        val allSkills = _state.value.skills
        val gemDefaults = gem?.defaultSkillIds?.toSet().orEmpty()
        return allSkills.filter { it.enabled || it.id in gemDefaults }
    }

    private fun buildSystemPrompt(gem: Gem?, enabledSkills: List<AgentSkill>): String {
        val sb = StringBuilder()
        if (gem != null && gem.instructions.isNotBlank()) {
            sb.appendLine("# Active Gem: ${gem.name}")
            sb.appendLine(gem.instructions.trim())
            sb.appendLine()
        } else {
            sb.appendLine("You are Aura, a helpful, concise, and capable AI agent on Android.")
            sb.appendLine()
        }

        val promptSkills = enabledSkills.filter { it.type == SkillType.PROMPT_SKILL && it.instructions.isNotBlank() }
        if (promptSkills.isNotEmpty()) {
            sb.appendLine("# Enabled Skills")
            for (skill in promptSkills) {
                sb.appendLine("## Skill: ${skill.name}")
                sb.appendLine(skill.instructions.trim())
                sb.appendLine()
            }
        }
        return sb.toString().trim()
    }

    private fun buildInitialTurnMessages(sessionId: String): MutableList<LlmTurnMessage> {
        val sess = _state.value.sessions.firstOrNull { it.id == sessionId } ?: return mutableListOf()
        return sess.messages
            .dropLast(1)
            .filter { !it.isError && it.content.isNotBlank() }
            .map { LlmTurnMessage(role = it.role, content = it.content) }
            .toMutableList()
    }

    private fun updateAssistantMessage(
        sessionId: String,
        messageId: String,
        content: String,
        traces: List<ToolCallTrace>,
        streaming: Boolean,
        isError: Boolean,
        routedBadge: String?
    ) {
        _state.update { s ->
            s.copy(
                sessions = s.sessions.map { sess ->
                    if (sess.id == sessionId) {
                        sess.copy(
                            messages = sess.messages.map { msg ->
                                if (msg.id == messageId) {
                                    msg.copy(
                                        content = content,
                                        toolTraces = traces,
                                        isStreaming = streaming,
                                        isError = isError,
                                        routedProviderBadge = routedBadge
                                    )
                                } else msg
                            }
                        )
                    } else sess
                }
            )
        }
    }

    private fun appendErrorSessionMessage(prompt: String, errorText: String) {
        val userMsg = ChatMessage(role = "user", content = prompt)
        val errMsg = ChatMessage(role = "assistant", content = errorText, isError = true)
        val existing = _state.value.activeSession
        if (existing == null) {
            val newSession = ChatSession(
                title = prompt.take(36),
                gemId = _state.value.activeGemId,
                messages = listOf(userMsg, errMsg)
            )
            _state.update { it.copy(sessions = listOf(newSession) + it.sessions, activeSessionId = newSession.id) }
        } else {
            _state.update { s ->
                s.copy(
                    sessions = s.sessions.map { sess ->
                        if (sess.id == existing.id) sess.copy(messages = sess.messages + userMsg + errMsg) else sess
                    }
                )
            }
        }
        persist()
    }
}
