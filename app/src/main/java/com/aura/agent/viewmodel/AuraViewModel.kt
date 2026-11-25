package com.aura.agent.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aura.agent.channels.TelegramBotBridge
import com.aura.agent.commands.SlashCommandRegistry
import com.aura.agent.data.TelegramBridgeConfig
import java.util.concurrent.ConcurrentHashMap
import com.aura.agent.data.AgentSkill
import com.aura.agent.data.Defaults
import com.aura.agent.data.AuditLogEntry
import com.aura.agent.data.GlobalApprovalMode
import com.aura.agent.data.SubagentConfig
import com.aura.agent.data.ReasoningEffort
import com.aura.agent.data.RoutingMode
import com.aura.agent.data.ToolPermissionMode
import kotlinx.coroutines.CompletableDeferred
import com.aura.agent.data.ApiProtocol
import com.aura.agent.data.AppStateSnapshot
import com.aura.agent.data.AppThemePreset
import com.aura.agent.data.CacheEntry
import com.aura.agent.data.ChatMessage
import com.aura.agent.data.ChatSession
import com.aura.agent.data.DownloadedLocalModel
import com.aura.agent.data.Gem
import com.aura.agent.data.LocalStore
import com.aura.agent.data.MemoryEntry
import com.aura.agent.data.ProviderConfig
import com.aura.agent.data.RouterConfig
import com.aura.agent.data.RouterStats
import com.aura.agent.data.SkillType
import com.aura.agent.data.TokenUsage
import com.aura.agent.data.ToolCallTrace
import com.aura.agent.hf.HfGgufFile
import com.aura.agent.hf.HfModelSummary
import com.aura.agent.hf.HuggingFaceClient
import com.aura.agent.network.LlmTurnMessage
import com.aura.agent.network.ModelCatalogService
import com.aura.agent.router.AuraCacheEngine
import com.aura.agent.router.AuraRouter
import com.aura.agent.router.LocalApiGateway
import com.aura.agent.router.ProviderHealth
import com.aura.agent.skills.SkillExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class ActiveScreen {
    CHAT, GEMS, SKILLS, SUBAGENTS, AGENT_BRAIN, LOCAL_MODELS, TELEGRAM, ROUTER, SETTINGS
}

data class DownloadProgress(
    val repoId: String,
    val fileName: String,
    val downloadedBytes: Long,
    val totalBytes: Long
)


data class PendingToolApproval(
    val toolName: String,
    val arguments: String,
    val deferred: CompletableDeferred<Boolean>
)

data class AuraUiState(
    val providers: List<ProviderConfig> = emptyList(),
    val activeProviderId: String = "",
    val gems: List<Gem> = emptyList(),
    val skills: List<AgentSkill> = emptyList(),
    val sessions: List<ChatSession> = emptyList(),
    val activeSessionId: String? = null,
    val activeGemId: String? = null,
    val memories: List<MemoryEntry> = emptyList(),
    val soulMarkdown: String = "",
    val userProfileMarkdown: String = "",
    val localModels: List<DownloadedLocalModel> = emptyList(),
    val hfSearchResults: List<HfModelSummary> = emptyList(),
    val hfSelectedRepoFiles: List<HfGgufFile> = emptyList(),
    val hfSelectedRepoId: String? = null,
    val hfLoading: Boolean = false,
    val hfError: String? = null,
    val activeDownload: DownloadProgress? = null,
    val themePreset: AppThemePreset = AppThemePreset.OBSIDIAN_GRAPHITE,
    val isGenerating: Boolean = false,
    val activeScreen: ActiveScreen = ActiveScreen.CHAT,
    val routerConfig: RouterConfig = RouterConfig(),
    val cacheEntries: List<CacheEntry> = emptyList(),
    val routerStats: RouterStats = RouterStats(),
    val providerHealth: Map<String, ProviderHealth> = emptyMap(),
    val gatewayRunning: Boolean = false,
    val gatewayStatusLog: String = "Stopped (127.0.0.1:8910)",
    val providerTestStatus: String? = null,
    val draftUseSoulMd: Boolean = true,
    val draftUseMemoryMd: Boolean = true,
    val draftReasoningEffort: ReasoningEffort = ReasoningEffort.MEDIUM,
    val globalApprovalMode: GlobalApprovalMode = GlobalApprovalMode.CUSTOM,
    val toolPolicies: Map<String, ToolPermissionMode> = emptyMap(),
    val subagents: List<SubagentConfig> = emptyList(),
    val auditLogs: List<AuditLogEntry> = emptyList(),
    val pendingApproval: PendingToolApproval? = null,
    val telegramConfig: TelegramBridgeConfig = TelegramBridgeConfig(),
    val telegramRunning: Boolean = false,
    val telegramLogs: List<String> = emptyList()
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

    val effectiveUseSoulMd: Boolean
        get() = activeSession?.useSoulMd ?: draftUseSoulMd

    val effectiveUseMemoryMd: Boolean
        get() = activeSession?.useMemoryMd ?: draftUseMemoryMd

    val effectiveReasoningEffort: ReasoningEffort
        get() = activeSession?.reasoningEffort ?: draftReasoningEffort
}

class AuraViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application.applicationContext)
    private val router = AuraRouter()
    private val hfClient = HuggingFaceClient()

    private val _state = MutableStateFlow(AuraUiState())
    val state: StateFlow<AuraUiState> = _state.asStateFlow()

    private var activeJob: Job? = null
    private var downloadJob: Job? = null
    private var persistJob: Job? = null

    private val telegramHistories = ConcurrentHashMap<Long, MutableList<LlmTurnMessage>>()

    private val telegramBridge = TelegramBotBridge(
        getConfig = { _state.value.telegramConfig },
        onHandleMessage = { chatId, _, text -> handleTelegramIncoming(chatId, text) },
        onLog = { line ->
            _state.update { s -> s.copy(telegramLogs = (listOf(line) + s.telegramLogs).take(60)) }
        }
    )

    private val gateway = LocalApiGateway(
        router = router,
        getProviders = { _state.value.providers },
        getActiveProviderId = { _state.value.activeProviderId },
        getRouterConfig = { _state.value.routerConfig },
        onRequestLogged = { log ->
            _state.update { it.copy(gatewayStatusLog = log, providerHealth = router.getHealthSnapshot()) }
        }
    )

    private val skillExecutor = SkillExecutor(
        workspaceDir = store.workspaceDir,
        getMemories = { _state.value.memories },
        upsertMemory = { k, c, cat, tags -> upsertMemory(k, c, cat, tags) },
        deleteMemoryByKey = { k -> deleteMemoryByKey(k) },
        isToolCacheEnabled = { _state.value.routerConfig.toolMemoizationEnabled },
        onToolCacheHit = {
            _state.update { s -> s.copy(routerStats = s.routerStats.copy(toolCacheHits = s.routerStats.toolCacheHits + 1)) }
        },
        runSubagent = { role, task ->
            executeSubagentCall(role, task)
        }
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
            memories = snap.memories,
            soulMarkdown = snap.soulMarkdown,
            userProfileMarkdown = snap.userProfileMarkdown,
            localModels = snap.localModels.filter { File(it.localPath).exists() },
            themePreset = snap.themePreset,
            routerConfig = snap.routerConfig,
            cacheEntries = snap.cacheEntries,
            routerStats = snap.routerStats,
            globalApprovalMode = snap.globalApprovalMode,
            toolPolicies = snap.toolPolicies,
            auditLogs = snap.auditLogs,
            subagents = snap.subagents,
            telegramConfig = snap.telegramConfig
        )
    }

    private fun persist() {
        val s = _state.value
        val snapshot = AppStateSnapshot(
            providers = s.providers,
            activeProviderId = s.activeProviderId,
            gems = s.gems,
            skills = s.skills,
            sessions = s.sessions,
            memories = s.memories,
            soulMarkdown = s.soulMarkdown,
            userProfileMarkdown = s.userProfileMarkdown,
            localModels = s.localModels,
            themePreset = s.themePreset,
            routerConfig = s.routerConfig,
            cacheEntries = s.cacheEntries,
            routerStats = s.routerStats,
            globalApprovalMode = s.globalApprovalMode,
            toolPolicies = s.toolPolicies,
            auditLogs = s.auditLogs.take(100),
            subagents = s.subagents
        )
        persistJob?.cancel()
        persistJob = viewModelScope.launch(Dispatchers.IO) {
            delay(120L)
            store.save(snapshot)
        }
    }

    fun navigateTo(screen: ActiveScreen) {
        _state.update { it.copy(activeScreen = screen, providerHealth = router.getHealthSnapshot(), providerTestStatus = null) }
        if (screen == ActiveScreen.LOCAL_MODELS && _state.value.hfSearchResults.isEmpty()) {
            searchHuggingFace("")
        }
    }


    fun toggleChatSoulMd() {
        _state.update { s ->
            val sid = s.activeSessionId
            if (sid == null) {
                s.copy(draftUseSoulMd = !s.draftUseSoulMd)
            } else {
                s.copy(sessions = s.sessions.map { if (it.id == sid) it.copy(useSoulMd = !it.useSoulMd) else it })
            }
        }
        persist()
    }

    fun toggleChatMemoryMd() {
        _state.update { s ->
            val sid = s.activeSessionId
            if (sid == null) {
                s.copy(draftUseMemoryMd = !s.draftUseMemoryMd)
            } else {
                s.copy(sessions = s.sessions.map { if (it.id == sid) it.copy(useMemoryMd = !it.useMemoryMd) else it })
            }
        }
        persist()
    }

    fun setReasoningEffort(effort: ReasoningEffort) {
        _state.update { s ->
            val sid = s.activeSessionId
            if (sid == null) {
                s.copy(draftReasoningEffort = effort)
            } else {
                s.copy(sessions = s.sessions.map { if (it.id == sid) it.copy(reasoningEffort = effort) else it })
            }
        }
        persist()
    }

    fun cycleReasoningEffort() {
        val curr = _state.value.effectiveReasoningEffort
        val next = when (curr) {
            ReasoningEffort.LOW -> ReasoningEffort.MEDIUM
            ReasoningEffort.MEDIUM -> ReasoningEffort.HIGH
            ReasoningEffort.HIGH -> ReasoningEffort.LOW
        }
        setReasoningEffort(next)
    }


    fun setGlobalApprovalMode(mode: GlobalApprovalMode) {
        _state.update { it.copy(globalApprovalMode = mode) }
        persist()
    }

    fun clearAuditLogs() {
        _state.update { it.copy(auditLogs = emptyList()) }
        persist()
    }

    fun saveSubagent(sub: SubagentConfig) {
        _state.update { s ->
            val exists = s.subagents.any { it.id == sub.id }
            val updated = if (exists) s.subagents.map { if (it.id == sub.id) sub else it } else listOf(sub) + s.subagents
            s.copy(subagents = updated)
        }
        persist()
    }

    fun deleteSubagent(id: String) {
        _state.update { s -> s.copy(subagents = s.subagents.filterNot { it.id == id }) }
        persist()
    }

    private suspend fun executeSubagentCall(roleOrName: String, task: String): String {
        val s = _state.value
        val matched = s.subagents.firstOrNull {
            it.name.equals(roleOrName, ignoreCase = true) ||
                it.role.equals(roleOrName, ignoreCase = true) ||
                it.name.contains(roleOrName, ignoreCase = true)
        }
        val baseProviders = if (matched?.providerId != null) {
            s.providers.map { p ->
                if (p.id == matched.providerId) {
                    p.copy(
                        model = matched.modelOverride.ifBlank { p.model },
                        temperature = matched.temperature
                    )
                } else p
            }
        } else if (matched != null && matched.modelOverride.isNotBlank()) {
            s.providers.map { p ->
                if (p.id == s.activeProviderId) p.copy(model = matched.modelOverride, temperature = matched.temperature) else p
            }
        } else s.providers

        val targetPid = matched?.providerId ?: s.activeProviderId
        val eff = matched?.reasoningEffort ?: s.effectiveReasoningEffort
        val sys = buildString {
            if (matched != null && matched.systemPrompt.isNotBlank()) {
                appendLine(matched.systemPrompt)
            } else {
                appendLine("You are a focused subagent acting as: $roleOrName. Complete the delegated task concisely and accurately.")
            }
            if (eff == ReasoningEffort.HIGH) appendLine("Reasoning Effort: HIGH — verify all edge cases carefully.")
            if (eff == ReasoningEffort.LOW) appendLine("Reasoning Effort: LOW — be brief and direct.")
        }.trim()

        val routed = router.completeWithRouting(
            providers = baseProviders,
            activeProviderId = targetPid,
            config = s.routerConfig,
            systemPrompt = sys,
            messages = listOf(LlmTurnMessage(role = "user", content = task)),
            tools = emptyList(),
            latestUserPrompt = task,
            onDelta = {}
        )
        return "[Subagent: ${matched?.name ?: roleOrName} · ${routed.usedProvider.model} · Effort: ${eff.label}]\n${routed.response.text}"
    }

    fun setToolPolicy(toolName: String, mode: ToolPermissionMode) {
        _state.update { s -> s.copy(toolPolicies = s.toolPolicies + (toolName to mode)) }
        persist()
    }

    fun respondToToolApproval(approved: Boolean) {
        val pending = _state.value.pendingApproval ?: return
        _state.update { it.copy(pendingApproval = null) }
        pending.deferred.complete(approved)
    }

    private fun recordAudit(toolName: String, decision: String, summary: String) {
        val gemName = _state.value.activeGem?.name ?: "Default"
        val entry = AuditLogEntry(toolName = toolName, sourceGem = gemName, decision = decision, summary = summary.take(180))
        _state.update { s -> s.copy(auditLogs = (listOf(entry) + s.auditLogs).take(100)) }
    }

    fun toggleDarkLightMode() {
        val curr = _state.value.themePreset
        val next = if (curr.isDark) AppThemePreset.WARM_ALABASTER else AppThemePreset.GEMINI_MIDNIGHT
        setThemePreset(next)
    }

    fun setThemePreset(preset: AppThemePreset) {
        _state.update { it.copy(themePreset = preset) }
        persist()
    }

    fun updateSoulMarkdown(md: String) {
        _state.update { it.copy(soulMarkdown = md) }
        persist()
    }

    fun updateUserProfileMarkdown(md: String) {
        _state.update { it.copy(userProfileMarkdown = md) }
        persist()
    }

    fun upsertMemory(key: String, content: String, category: String = "general", tags: List<String> = emptyList(), pinned: Boolean = false) {
        _state.update { s ->
            val existing = s.memories.firstOrNull { it.key.equals(key, ignoreCase = true) }
            val entry = (existing ?: MemoryEntry(key = key, content = content, category = category)).copy(
                key = key.trim(),
                content = content.trim(),
                category = category.trim().ifBlank { "general" },
                tags = tags,
                pinned = pinned || (existing?.pinned ?: false),
                updatedAt = System.currentTimeMillis()
            )
            val rest = s.memories.filterNot { it.key.equals(key, ignoreCase = true) }
            s.copy(memories = listOf(entry) + rest)
        }
        persist()
    }

    fun toggleMemoryPin(id: String) {
        _state.update { s ->
            s.copy(memories = s.memories.map { if (it.id == id) it.copy(pinned = !it.pinned) else it })
        }
        persist()
    }

    fun deleteMemoryById(id: String) {
        _state.update { s -> s.copy(memories = s.memories.filterNot { it.id == id }) }
        persist()
    }

    private fun deleteMemoryByKey(key: String): Boolean {
        val exists = _state.value.memories.any { it.key.equals(key, ignoreCase = true) }
        if (exists) {
            _state.update { s -> s.copy(memories = s.memories.filterNot { it.key.equals(key, ignoreCase = true) }) }
            persist()
        }
        return exists
    }

    // --- Hugging Face Local Model Catalog & Downloader ---
    fun searchHuggingFace(query: String) {
        viewModelScope.launch {
            _state.update { it.copy(hfLoading = true, hfError = null, hfSelectedRepoId = null, hfSelectedRepoFiles = emptyList()) }
            try {
                val results = hfClient.searchGgufModels(query)
                _state.update { it.copy(hfSearchResults = results, hfLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(hfLoading = false, hfError = "HF Catalog error: ${e.message}") }
            }
        }
    }

    fun inspectHfRepo(repoId: String) {
        viewModelScope.launch {
            _state.update { it.copy(hfLoading = true, hfError = null, hfSelectedRepoId = repoId, hfSelectedRepoFiles = emptyList()) }
            try {
                val files = hfClient.listGgufFiles(repoId)
                _state.update { it.copy(hfSelectedRepoFiles = files, hfLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(hfLoading = false, hfError = "Failed to load GGUF files: ${e.message}") }
            }
        }
    }

    fun closeHfRepoInspect() {
        _state.update { it.copy(hfSelectedRepoId = null, hfSelectedRepoFiles = emptyList()) }
    }

    fun downloadHfModel(repoId: String, file: HfGgufFile) {
        if (_state.value.activeDownload != null) return
        val safeFileName = file.path.substringAfterLast("/")
        val destFile = File(store.modelsDir, safeFileName)
        downloadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    activeDownload = DownloadProgress(repoId, safeFileName, 0L, file.sizeBytes),
                    hfError = null
                )
            }
            try {
                val saved = hfClient.downloadGguf(file, destFile) { dl, total ->
                    _state.update {
                        it.copy(activeDownload = DownloadProgress(repoId, safeFileName, dl, total))
                    }
                }
                val model = DownloadedLocalModel(
                    repoId = repoId,
                    fileName = safeFileName,
                    localPath = saved.absolutePath,
                    sizeBytes = saved.length()
                )
                _state.update { s ->
                    val rest = s.localModels.filterNot { it.localPath == model.localPath }
                    s.copy(localModels = listOf(model) + rest, activeDownload = null)
                }
                persist()
            } catch (e: Exception) {
                _state.update { it.copy(activeDownload = null, hfError = "Download failed: ${e.message}") }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _state.update { it.copy(activeDownload = null) }
    }

    fun deleteLocalModel(model: DownloadedLocalModel) {
        runCatching { File(model.localPath).delete() }
        _state.update { s -> s.copy(localModels = s.localModels.filterNot { it.id == model.id }) }
        persist()
    }

    fun activateLocalModel(model: DownloadedLocalModel) {
        val localProvider = _state.value.providers.firstOrNull { it.protocol == ApiProtocol.LOCAL_GGUF }
            ?: ProviderConfig(
                id = "local-gguf",
                name = "Local GGUF (llama-server)",
                protocol = ApiProtocol.LOCAL_GGUF,
                baseUrl = "http://127.0.0.1:8080/v1",
                apiKey = "local",
                model = model.fileName
            )
        updateProvider(localProvider.copy(model = model.fileName))
        navigateTo(ActiveScreen.CHAT)
    }

    fun testProviderConnection(provider: ProviderConfig) {
        viewModelScope.launch {
            _state.update { it.copy(providerTestStatus = "Syncing live models for ${provider.name}...") }
            try {
                val models = ModelCatalogService.fetchLiveModels(provider)
                val merged = (models + provider.availableModels).distinct().take(40)
                if (merged.isNotEmpty()) {
                    updateProvider(provider.copy(availableModels = merged))
                }
                val msg = if (models.isNotEmpty()) {
                    "Synced ${models.size} live models from ${provider.name} (top: ${models.take(3).joinToString(", ")})"
                } else {
                    "Connected to ${provider.name} (OK)"
                }
                _state.update { it.copy(providerTestStatus = msg) }
            } catch (e: Exception) {
                _state.update { it.copy(providerTestStatus = "Sync failed for ${provider.name}: ${e.message}") }
            }
        }
    }

    fun refreshAllProviderModels() {
        viewModelScope.launch {
            val configured = _state.value.providers.filter { router.isConfigured(it) }
            if (configured.isEmpty()) {
                _state.update { it.copy(providerTestStatus = "Add at least one API key first to sync live models.") }
                return@launch
            }
            _state.update { it.copy(providerTestStatus = "Syncing live models for ${configured.size} providers...") }
            var okCount = 0
            var totalFound = 0
            for (p in configured) {
                runCatching {
                    val live = ModelCatalogService.fetchLiveModels(p)
                    if (live.isNotEmpty()) {
                        okCount++
                        totalFound += live.size
                        val merged = (live + p.availableModels).distinct().take(40)
                        _state.update { s ->
                            s.copy(providers = s.providers.map { if (it.id == p.id) it.copy(availableModels = merged) else it })
                        }
                    }
                }
            }
            persist()
            _state.update { it.copy(providerTestStatus = "Model catalog updated: $totalFound models synced across $okCount providers.") }
        }
    }

    fun updateRouterConfig(config: RouterConfig) {
        _state.update { it.copy(routerConfig = config) }
        persist()
    }


    fun updateTelegramConfig(cfg: TelegramBridgeConfig) {
        _state.update { it.copy(telegramConfig = cfg) }
        persist()
    }

    fun verifyTelegramToken(cfg: TelegramBridgeConfig) {
        viewModelScope.launch {
            _state.update { s -> s.copy(telegramLogs = (listOf("Verifying token...") + s.telegramLogs).take(60)) }
            try {
                val username = telegramBridge.verifyToken(cfg.botToken, cfg.apiBaseUrl)
                runCatching { telegramBridge.syncBotCommands(cfg.botToken, cfg.apiBaseUrl) }
                _state.update { s ->
                    s.copy(
                        telegramConfig = s.telegramConfig.copy(botUsername = username),
                        telegramLogs = (listOf("Verified bot: $username") + s.telegramLogs).take(60)
                    )
                }
                persist()
            } catch (e: Exception) {
                _state.update { s -> s.copy(telegramLogs = (listOf("Verify failed: ${e.message}") + s.telegramLogs).take(60)) }
            }
        }
    }

    fun toggleTelegramBot(enable: Boolean) {
        if (enable) {
            val ok = telegramBridge.start()
            _state.update { it.copy(telegramRunning = ok) }
        } else {
            telegramBridge.stop()
            _state.update { it.copy(telegramRunning = false) }
        }
    }

    private suspend fun handleTelegramIncoming(chatId: Long, text: String): String {
        var trimmed = text.trim()
        var bypassCache = false
        if (trimmed.startsWith("/")) {
            val (cmd, arg) = TelegramBotBridge.normalizeTelegramCommand(trimmed)
            when (cmd) {
                "/start" -> return "Aura Agent connected! Tap the '/' menu button to see all commands, or send any question."
                "/help" -> return TelegramBotBridge.telegramCommands.joinToString("\n") { "/${it.first} — ${it.second}" }
                "/clear" -> {
                    telegramHistories.remove(chatId)
                    return "Telegram chat history cleared."
                }
                "/status" -> {
                    val st = _state.value
                    val kvPct = if (st.routerStats.totalInputTokens > 0) ((st.routerStats.serverCachedTokens * 100L) / st.routerStats.totalInputTokens).toInt() else 0
                    return "Aura Status:\n- Provider: ${st.activeProvider?.name} (${st.activeProvider?.model})\n- Gem: ${st.activeGem?.name ?: "Default"}\n- Effort: ${st.effectiveReasoningEffort.label}\n- Local Cache Hit: ${st.routerStats.hitRatePercent}% | KV Prefix: $kvPct%\n- Tokens: ${st.routerStats.totalInputTokens} in / ${st.routerStats.totalOutputTokens} out (~${st.routerStats.estimatedTokensSaved} saved)"
                }
                "/model" -> {
                    val st = _state.value
                    if (arg.isBlank()) {
                        return "Providers:\n" + st.providers.joinToString("\n") { p ->
                            val mark = if (p.id == st.activeProviderId) " *" else ""
                            "- ${p.name}: ${p.model}$mark"
                        } + "\n\nUse /model <name> to switch."
                    }
                    val match = st.providers.firstOrNull {
                        it.name.contains(arg, ignoreCase = true) || it.id.contains(arg, ignoreCase = true) || it.model.contains(arg, ignoreCase = true)
                    }
                    return if (match != null) {
                        selectProvider(match.id)
                        "Switched to ${match.name} (${match.model})."
                    } else "No provider matching '$arg'."
                }
                "/gem" -> {
                    val st = _state.value
                    if (arg.isBlank()) {
                        return "Gems:\n" + st.gems.joinToString("\n") { "- ${it.name}: ${it.description}" } + "\n\nUse /gem <name> or /gem off."
                    }
                    if (arg.equals("off", ignoreCase = true)) {
                        _state.update { it.copy(activeGemId = null) }
                        return "Active Gem cleared."
                    }
                    val match = st.gems.firstOrNull { it.name.contains(arg, ignoreCase = true) }
                    return if (match != null) {
                        _state.update { it.copy(activeGemId = match.id) }
                        "Activated Gem: ${match.name}"
                    } else "No Gem matching '$arg'."
                }
                "/effort" -> {
                    val eff = when (arg.lowercase()) {
                        "low" -> ReasoningEffort.LOW
                        "medium", "med" -> ReasoningEffort.MEDIUM
                        "high" -> ReasoningEffort.HIGH
                        else -> null
                    }
                    return if (eff != null) {
                        setReasoningEffort(eff)
                        "Reasoning effort set to ${eff.label} (max ${eff.maxToolSteps} steps)."
                    } else "Current effort: ${_state.value.effectiveReasoningEffort.label}. Use /effort low|medium|high."
                }
                "/soul" -> {
                    return if (arg.equals("toggle", ignoreCase = true)) {
                        toggleChatSoulMd()
                        "SOUL.md is now ${if (_state.value.effectiveUseSoulMd) "ON" else "OFF"}."
                    } else {
                        "SOUL.md (${if (_state.value.effectiveUseSoulMd) "ON" else "OFF"}):\n${_state.value.soulMarkdown.take(1500)}"
                    }
                }
                "/remember" -> {
                    if (arg.isBlank()) return "Usage: /remember key: fact"
                    val idx = arg.indexOf(':')
                    val (k, v) = if (idx > 0) arg.substring(0, idx).trim() to arg.substring(idx + 1).trim()
                    else "tg_${System.currentTimeMillis() % 10000}" to arg
                    upsertMemory(k, v, "general", emptyList(), pinned = true)
                    return "Saved to long-term memory: $k -> $v"
                }
                "/memory" -> {
                    val mems = _state.value.memories
                    val matched = if (arg.isBlank()) mems else mems.filter {
                        it.key.contains(arg, ignoreCase = true) || it.content.contains(arg, ignoreCase = true)
                    }
                    return if (matched.isEmpty()) "No matching memories."
                    else matched.take(15).joinToString("\n") { "- [${it.category}] ${it.key}: ${it.content}" }
                }
                "/skills" -> {
                    return "Tools & Skills:\n" + _state.value.skills.joinToString("\n") {
                        "- [${if (it.enabled) "ON" else "OFF"}] ${it.name}"
                    }
                }
                "/cache" -> {
                    if (arg.equals("clear", ignoreCase = true)) {
                        clearCache()
                        return "Cache cleared."
                    }
                    val st = _state.value.routerStats
                    return "Cache Hit Rate: ${st.hitRatePercent}% (${st.exactCacheHits} exact, ${st.semanticCacheHits} semantic, ${st.toolCacheHits} tool) | Saved ~${st.estimatedTokensSaved} tokens."
                }
                "/compact" -> {
                    val hist = telegramHistories[chatId]
                    if (hist == null || hist.size < 3) return "Not enough messages in this Telegram chat to compact yet."
                    val transcript = hist.joinToString("\n\n") { "${it.role.uppercase()}: ${it.content.take(1200)}" }
                    val routed = router.completeWithRouting(
                        providers = _state.value.providers,
                        activeProviderId = _state.value.activeProviderId,
                        config = _state.value.routerConfig,
                        systemPrompt = "Compress this chat into a dense lossless checkpoint.",
                        messages = listOf(LlmTurnMessage("user", transcript)),
                        tools = emptyList(),
                        latestUserPrompt = transcript,
                        sessionId = "tg_$chatId",
                        onDelta = {}
                    )
                    hist.clear()
                    hist.add(LlmTurnMessage("assistant", "Compacted Context:\n${routed.response.text}"))
                    return "Compacted Telegram context:\n\n${routed.response.text}"
                }
                "/nocache" -> {
                    if (arg.isBlank()) return "Usage: /nocache <your prompt>"
                    trimmed = arg
                    bypassCache = true
                }
            }
        }

        val s = _state.value
        val currentGem = s.activeGem
        val enabledSkills = if (s.telegramConfig.enableTools) resolveEnabledSkills(currentGem) else emptyList()
        val systemPrompt = buildSystemPrompt(currentGem, enabledSkills, trimmed)
        val systemHash = AuraCacheEngine.sha256Short(systemPrompt)
        val history = telegramHistories.getOrPut(chatId) { mutableListOf() }
        val ctxHash = AuraCacheEngine.sha256Short(history.takeLast(4).joinToString("|") { "${it.role}:${it.content.take(60)}" })

        // Check AuraCache first!
        val hit = if (bypassCache) null else AuraCacheEngine.lookup(
            entries = s.cacheEntries,
            config = s.routerConfig,
            gemId = currentGem?.id.orEmpty(),
            systemHash = systemHash,
            contextHash = ctxHash,
            prompt = trimmed
        )
        if (hit != null) {
            history.add(LlmTurnMessage("user", trimmed))
            history.add(LlmTurnMessage("assistant", hit.entry.response))
            if (history.size > 20) history.subList(0, history.size - 20).clear()
            return hit.entry.response
        }

        val turns = (history + LlmTurnMessage("user", trimmed)).toMutableList()
        val toolSpecs = skillExecutor.buildToolSpecs(enabledSkills).sortedBy { it.name }
        var finalReply = ""
        var usedLabel = ""
        var round = 0
        while (round < 5) {
            round++
            val routed = router.completeWithRouting(
                providers = _state.value.providers,
                activeProviderId = _state.value.activeProviderId,
                config = _state.value.routerConfig,
                systemPrompt = systemPrompt,
                messages = turns,
                tools = toolSpecs,
                latestUserPrompt = trimmed,
                sessionId = "tg_$chatId",
                onDelta = {}
            )
            usedLabel = "${routed.usedProvider.name} · ${routed.usedProvider.model}"
            if (routed.response.toolCalls.isEmpty()) {
                finalReply = routed.response.text
                break
            }
            turns.add(LlmTurnMessage("assistant", routed.response.text, routed.response.toolCalls))
            for (tc in routed.response.toolCalls) {
                val res = skillExecutor.execute(tc.name, tc.argumentsJson, enabledSkills)
                turns.add(LlmTurnMessage("tool", res.output, toolCallId = tc.id, toolName = tc.name))
            }
        }

        val answer = finalReply.ifBlank { "(No response returned)" }
        history.add(LlmTurnMessage("user", trimmed))
        history.add(LlmTurnMessage("assistant", answer))
        if (history.size > 20) history.subList(0, history.size - 20).clear()

        _state.update { st ->
            st.copy(
                cacheEntries = AuraCacheEngine.putEntry(
                    st.cacheEntries, st.routerConfig, currentGem?.id.orEmpty(),
                    systemHash, ctxHash, trimmed, answer, usedLabel
                )
            )
        }
        persist()
        return answer
    }

    fun toggleLocalGateway(enable: Boolean) {
        if (enable) {
            val ok = gateway.start(port = 8910, bindAllInterfaces = false)
            _state.update { it.copy(gatewayRunning = ok) }
        } else {
            gateway.stop()
            _state.update { it.copy(gatewayRunning = false) }
        }
    }

    override fun onCleared() {
        gateway.stop()
        telegramBridge.stop()
        super.onCleared()
    }

    fun clearCache() {
        AuraCacheEngine.clearToolMemo()
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
                draftUseSoulMd = gem?.useSoulMd ?: true,
                draftUseMemoryMd = gem?.useMemoryMd ?: true,
                draftReasoningEffort = gem?.reasoningEffort ?: ReasoningEffort.MEDIUM,
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

        if (prompt.startsWith("/")) {
            if (handleSlashCommand(prompt)) return
        }

        val snap = _state.value
        val hasAnyConfigured = snap.providers.any { router.isConfigured(it) }
        if (!hasAnyConfigured) {
            appendErrorSessionMessage(
                prompt,
                "No API key or local server configured yet. Open Settings or Local Models from the drawer."
            )
            return
        }

        val currentGem = snap.activeGem
        val gemId = currentGem?.id.orEmpty()
        val enabledSkills = resolveEnabledSkills(currentGem)
        val systemPrompt = buildSystemPrompt(currentGem, enabledSkills, prompt)
        val systemHash = AuraCacheEngine.sha256Short(systemPrompt)

        val priorContext = snap.activeSession?.messages
            ?.filter { !it.isError && it.content.isNotBlank() }
            ?.takeLast(4)
            ?.joinToString("|") { "${it.role}:${AuraCacheEngine.normalizeText(it.content).take(80)}" }
            .orEmpty()
        val contextHash = AuraCacheEngine.sha256Short(priorContext)

        _state.update { s -> s.copy(routerStats = s.routerStats.copy(totalRequests = s.routerStats.totalRequests + 1)) }

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
                val scopeTag = if (hit.isCrossSession) "Global " else ""
                val badge = if (hit.isExact) "${scopeTag}Exact Cache (0ms)" else "${scopeTag}Semantic Cache ($pct%)"
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
                useSoulMd = _state.value.draftUseSoulMd,
                useMemoryMd = _state.value.draftUseMemoryMd,
                reasoningEffort = _state.value.draftReasoningEffort,
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

        val toolSpecs = skillExecutor.buildToolSpecs(enabledSkills).sortedBy { it.name }

        activeJob = viewModelScope.launch {
            try {
                val historyTurns = buildInitialTurnMessages(sessionId)
                val traces = mutableListOf<ToolCallTrace>()
                val streamedBuffer = StringBuilder()
                var usedProviderLabel = ""
                var anyFallback = false
                var totalIn = 0L
                var totalOut = 0L
                var totalCached = 0L
                var lastUiFlushMs = 0L

                val maxRounds = _state.value.effectiveReasoningEffort.maxToolSteps
                var round = 0
                while (round < maxRounds) {
                    round++
                    val routed = router.completeWithRouting(
                        providers = _state.value.providers,
                        activeProviderId = _state.value.activeProviderId,
                        config = _state.value.routerConfig,
                        systemPrompt = systemPrompt,
                        messages = historyTurns,
                        tools = toolSpecs,
                        latestUserPrompt = prompt,
                        sessionId = sessionId,
                        onDelta = { delta ->
                            streamedBuffer.append(delta)
                            val now = System.currentTimeMillis()
                            if (now - lastUiFlushMs >= 45L) {
                                lastUiFlushMs = now
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
                        }
                    )

                    usedProviderLabel = "${routed.usedProvider.name} · ${routed.usedProvider.model}" +
                        if (routed.fallbackTriggered) " (Fallback)" else ""
                    totalIn += routed.response.usage.inputTokens
                    totalOut += routed.response.usage.outputTokens
                    totalCached += routed.response.usage.cachedInputTokens
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
                        val policy = when (_state.value.globalApprovalMode) {
                            GlobalApprovalMode.AUTONOMOUS -> _state.value.toolPolicies[tc.name] ?: ToolPermissionMode.ALLOW
                            GlobalApprovalMode.ASK_EVERY_TOOL -> if (_state.value.toolPolicies[tc.name] == ToolPermissionMode.DENY) ToolPermissionMode.DENY else ToolPermissionMode.ASK
                            GlobalApprovalMode.CUSTOM -> _state.value.toolPolicies[tc.name] ?: ToolPermissionMode.ALLOW
                        }
                        val execResult = when (policy) {
                            ToolPermissionMode.DENY -> {
                                recordAudit(tc.name, "DENIED", "Blocked by Action Policy")
                                com.aura.agent.skills.ToolExecutionResult("Action blocked by user policy for tool '${tc.name}'.", isError = true)
                            }
                            ToolPermissionMode.ASK -> {
                                val def = CompletableDeferred<Boolean>()
                                _state.update { it.copy(pendingApproval = PendingToolApproval(tc.name, tc.argumentsJson, def)) }
                                val ok = def.await()
                                if (!ok) {
                                    recordAudit(tc.name, "DENIED", "User rejected tool call")
                                    com.aura.agent.skills.ToolExecutionResult("User denied permission to run '${tc.name}'.", isError = true)
                                } else {
                                    val r = skillExecutor.execute(tc.name, tc.argumentsJson, enabledSkills)
                                    recordAudit(tc.name, if (r.isError) "ERROR" else "APPROVED", r.output)
                                    r
                                }
                            }
                            ToolPermissionMode.ALLOW -> {
                                val r = skillExecutor.execute(tc.name, tc.argumentsJson, enabledSkills)
                                recordAudit(tc.name, if (r.isError) "ERROR" else "ALLOWED", r.output)
                                r
                            }
                        }
                        traces.add(
                            ToolCallTrace(
                                toolName = tc.name,
                                arguments = tc.argumentsJson,
                                result = execResult.output,
                                isError = execResult.isError,
                                fromCache = execResult.fromCache
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
                val finalUsage = TokenUsage(
                    inputTokens = if (totalIn > 0L) totalIn else AuraCacheEngine.estimateTokens(systemPrompt + prompt),
                    outputTokens = if (totalOut > 0L) totalOut else AuraCacheEngine.estimateTokens(finalContent),
                    cachedInputTokens = totalCached
                )
                updateAssistantMessage(
                    sessionId = sessionId,
                    messageId = assistantMsg.id,
                    content = finalContent,
                    traces = traces.toList(),
                    streaming = false,
                    isError = false,
                    routedBadge = usedProviderLabel,
                    usage = finalUsage
                )
                _state.update { s ->
                    s.copy(
                        routerStats = s.routerStats.copy(
                            totalInputTokens = s.routerStats.totalInputTokens + finalUsage.inputTokens,
                            totalOutputTokens = s.routerStats.totalOutputTokens + finalUsage.outputTokens,
                            serverCachedTokens = s.routerStats.serverCachedTokens + finalUsage.cachedInputTokens
                        )
                    )
                }

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
                        s.copy(cacheEntries = newCache, routerStats = newStats, providerHealth = router.getHealthSnapshot())
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

    /**
     * Aura structured system prompt assembly:
     * SOUL.md -> USER.md -> Pinned/Relevant Long-Term Memories -> Active Gem -> Prompt Skills.
     */
    private fun buildSystemPrompt(gem: Gem?, enabledSkills: List<AgentSkill>, prompt: String): String {
        val sb = StringBuilder()
        val s = _state.value

        if (s.effectiveUseSoulMd && s.soulMarkdown.isNotBlank()) {
            sb.appendLine(s.soulMarkdown.trim())
            sb.appendLine()
        }
        if (s.effectiveUseMemoryMd && s.userProfileMarkdown.isNotBlank()) {
            sb.appendLine(s.userProfileMarkdown.trim())
            sb.appendLine()
        }

        if (s.effectiveUseMemoryMd) {
            val activeGemId = gem?.id
            val visibleMemories = s.memories.filter { m ->
                m.scope == "global" || (activeGemId != null && m.sourceGemId == activeGemId)
            }
            // Phase 1 Immutable Prefix: keep system memory block strictly deterministic & sorted so KV-cache never invalidates mid-session
            val injectedMemories = visibleMemories
                .sortedWith(compareByDescending<MemoryEntry> { it.pinned }.thenBy { it.category }.thenBy { it.key })
                .take(16)
            if (injectedMemories.isNotEmpty()) {
                sb.appendLine("# Active Long-Term Memory (MEMORY.md)")
                for (m in injectedMemories) {
                    sb.appendLine("- [${m.category}] ${m.key}: ${m.content}")
                }
                sb.appendLine()
            }
        }

        when (s.effectiveReasoningEffort) {
            ReasoningEffort.LOW -> sb.appendLine("# Reasoning Effort: LOW — Be extra brief and direct.")
            ReasoningEffort.MEDIUM -> Unit
            ReasoningEffort.HIGH -> sb.appendLine("# Reasoning Effort: HIGH — Think deeply, verify edge cases, and delegate subtasks via spawn_subagent when helpful.")
        }

        if (gem != null && gem.instructions.isNotBlank()) {
            sb.appendLine("# Active Gem: ${gem.name}")
            sb.appendLine(gem.instructions.trim())
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
        routedBadge: String?,
        usage: TokenUsage? = null
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
                                        routedProviderBadge = routedBadge,
                                        tokenUsage = usage ?: msg.tokenUsage
                                    )
                                } else msg
                            }
                        )
                    } else sess
                }
            )
        }
    }


    private fun handleSlashCommand(raw: String): Boolean {
        val parts = raw.trim().split(Regex("\\s+"), limit = 2)
        val cmd = parts[0].lowercase()
        val arg = parts.getOrNull(1)?.trim().orEmpty()

        when (cmd) {
            "/help" -> {
                appendLocalCommandReply(raw, SlashCommandRegistry.helpMarkdown(), "Command · /help")
                return true
            }
            "/clear" -> {
                val sid = _state.value.activeSessionId
                if (sid != null) {
                    _state.update { s ->
                        s.copy(sessions = s.sessions.map { if (it.id == sid) it.copy(messages = emptyList()) else it })
                    }
                    persist()
                }
                return true
            }
            "/new" -> {
                val gem = if (arg.isNotBlank()) {
                    _state.value.gems.firstOrNull { it.name.contains(arg, ignoreCase = true) }
                } else null
                startNewChat(gem?.id)
                return true
            }
            "/compact" -> {
                runContextCompaction(raw, arg)
                return true
            }
            "/context" -> {
                val s = _state.value
                val msgs = s.activeSession?.messages.orEmpty()
                val chatTokens = msgs.sumOf { AuraCacheEngine.estimateTokens(it.content) }
                val soulTokens = AuraCacheEngine.estimateTokens(s.soulMarkdown + s.userProfileMarkdown)
                val memTokens = s.memories.sumOf { AuraCacheEngine.estimateTokens(it.key + it.content) }
                val enabledTools = resolveEnabledSkills(s.activeGem).count { it.type != SkillType.PROMPT_SKILL }
                val report = buildString {
                    appendLine("Context Diagnostics:")
                    appendLine("- Messages in thread: ${msgs.size}")
                    appendLine("- Chat history tokens: ~$chatTokens")
                    appendLine("- SOUL.md + USER.md: ~$soulTokens tokens")
                    appendLine("- Stored memories (${s.memories.size}): ~$memTokens tokens")
                    appendLine("- Active Gem: ${s.activeGem?.name ?: "None"}")
                    appendLine("- Enabled Tools: $enabledTools")
                    appendLine("- Active Provider: ${s.activeProvider?.name} (${s.activeProvider?.model})")
                    appendLine()
                    appendLine("Tip: Run /compact anytime to compress long history and save tokens.")
                }.trim()
                appendLocalCommandReply(raw, report, "Command · /context")
                return true
            }
            "/model" -> {
                val s = _state.value
                if (arg.isBlank()) {
                    val list = s.providers.joinToString("\n") { p ->
                        val active = if (p.id == s.activeProviderId) " (active)" else ""
                        "- ${p.name}: ${p.model}$active"
                    }
                    appendLocalCommandReply(raw, "Configured Providers:\n$list\n\nUse `/model <name>` to switch.", "Command · /model")
                } else {
                    val match = s.providers.firstOrNull {
                        it.name.contains(arg, ignoreCase = true) ||
                            it.id.contains(arg, ignoreCase = true) ||
                            it.model.contains(arg, ignoreCase = true)
                    }
                    if (match != null) {
                        selectProvider(match.id)
                        appendLocalCommandReply(raw, "Switched active provider to **${match.name}** (`${match.model}`).", "Command · /model")
                    } else {
                        appendLocalCommandReply(raw, "No provider matching '$arg'. Run `/model` to list available providers.", "Command · /model")
                    }
                }
                return true
            }
            "/gem" -> {
                val s = _state.value
                when {
                    arg.isBlank() -> {
                        val list = s.gems.joinToString("\n") { g -> "- ${g.name}: ${g.description}" }
                        appendLocalCommandReply(raw, "Available Gems:\n$list\n\nUse `/gem <name>` or `/gem off`.", "Command · /gem")
                    }
                    arg.equals("off", ignoreCase = true) -> {
                        _state.update { it.copy(activeGemId = null) }
                        appendLocalCommandReply(raw, "Active Gem cleared.", "Command · /gem")
                    }
                    else -> {
                        val match = s.gems.firstOrNull { it.name.contains(arg, ignoreCase = true) }
                        if (match != null) {
                            startNewChat(match.id)
                        } else {
                            appendLocalCommandReply(raw, "No Gem matching '$arg'.", "Command · /gem")
                        }
                    }
                }
                return true
            }
            "/remember" -> {
                if (arg.isBlank()) {
                    appendLocalCommandReply(raw, "Usage: `/remember key: fact to remember`", "Command · /remember")
                } else {
                    val idx = arg.indexOf(':')
                    val (k, v) = if (idx > 0) {
                        arg.substring(0, idx).trim() to arg.substring(idx + 1).trim()
                    } else {
                        "note_${System.currentTimeMillis() % 10000}" to arg
                    }
                    upsertMemory(k, v, "general", emptyList(), pinned = true)
                    appendLocalCommandReply(raw, "Pinned to long-term memory: **$k** -> $v", "Command · /remember")
                }
                return true
            }
            "/memory" -> {
                if (arg.equals("open", ignoreCase = true)) {
                    navigateTo(ActiveScreen.AGENT_BRAIN)
                    return true
                }
                val mems = _state.value.memories
                val matched = if (arg.isBlank()) mems else mems.filter {
                    it.key.contains(arg, ignoreCase = true) || it.content.contains(arg, ignoreCase = true)
                }
                val text = if (matched.isEmpty()) {
                    "No matching memories. Use `/remember key: fact` to save one."
                } else {
                    matched.take(15).joinToString("\n") { m ->
                        val pin = if (m.pinned) "[Pinned] " else ""
                        "- $pin[${m.category}] ${m.key}: ${m.content}"
                    }
                }
                appendLocalCommandReply(raw, text, "Command · /memory")
                return true
            }
            "/soul" -> {
                when (arg.lowercase()) {
                    "edit", "open" -> navigateTo(ActiveScreen.AGENT_BRAIN)
                    "reset" -> {
                        updateSoulMarkdown(Defaults.DEFAULT_SOUL_MD)
                        appendLocalCommandReply(raw, "SOUL.md reset to Aura default.", "Command · /soul")
                    }
                    else -> {
                        appendLocalCommandReply(raw, _state.value.soulMarkdown, "Command · /soul")
                    }
                }
                return true
            }
            "/skills" -> {
                if (arg.startsWith("toggle ", ignoreCase = true)) {
                    val target = arg.removePrefix("toggle ").trim()
                    val match = _state.value.skills.firstOrNull {
                        it.name.contains(target, ignoreCase = true) || it.slug.contains(target, ignoreCase = true)
                    }
                    if (match != null) {
                        toggleSkill(match.id)
                        val newState = if (!match.enabled) "enabled" else "disabled"
                        appendLocalCommandReply(raw, "Skill **${match.name}** is now **$newState**.", "Command · /skills")
                    } else {
                        appendLocalCommandReply(raw, "No skill matching '$target'.", "Command · /skills")
                    }
                } else {
                    val list = _state.value.skills.joinToString("\n") { sk ->
                        val status = if (sk.enabled) "ON" else "OFF"
                        "- [$status] ${sk.name} (`${sk.slug}`)"
                    }
                    appendLocalCommandReply(raw, "Skills & Tools:\n$list\n\nUse `/skills toggle <name>` to toggle.", "Command · /skills")
                }
                return true
            }
            "/router" -> {
                val mode = when (arg.lowercase()) {
                    "auto", "smart" -> RoutingMode.SMART_AUTO
                    "fallback", "priority" -> RoutingMode.PRIORITY_FALLBACK
                    "latency", "fast" -> RoutingMode.LOWEST_LATENCY
                    "manual" -> RoutingMode.MANUAL
                    else -> null
                }
                if (mode != null) {
                    updateRouterConfig(_state.value.routerConfig.copy(mode = mode))
                    appendLocalCommandReply(raw, "AuraRouter mode set to **${mode.label}**.", "Command · /router")
                } else {
                    val st = _state.value.routerStats
                    val cfg = _state.value.routerConfig
                    val summary = "AuraRouter Status:\n- Mode: ${cfg.mode.label}\n- Auto-Fallback: ${cfg.enableFallback}\n- Cache Hit Rate: ${st.hitRatePercent}% (${st.exactCacheHits} exact, ${st.semanticCacheHits} semantic, ${st.toolCacheHits} tool)\n- Fallback Recoveries: ${st.fallbackRecoveries}\n- Estimated Tokens Saved: ~${st.estimatedTokensSaved}"
                    appendLocalCommandReply(raw, summary, "Command · /router")
                }
                return true
            }
            "/cache" -> {
                if (arg.equals("clear", ignoreCase = true)) {
                    clearCache()
                    appendLocalCommandReply(raw, "Local prompt & tool cache cleared.", "Command · /cache")
                } else {
                    val st = _state.value.routerStats
                    val entries = _state.value.cacheEntries.size
                    appendLocalCommandReply(
                        raw,
                        "Cache Stats:\n- Hit Rate: ${st.hitRatePercent}%\n- Stored Entries: $entries\n- Exact Hits: ${st.exactCacheHits}\n- Semantic Hits: ${st.semanticCacheHits}\n- Tool Memo Hits: ${st.toolCacheHits}\n- Saved Tokens: ~${st.estimatedTokensSaved}\n\nUse `/cache clear` to flush.",
                        "Command · /cache"
                    )
                }
                return true
            }
            "/nocache" -> {
                if (arg.isBlank()) {
                    appendLocalCommandReply(raw, "Usage: `/nocache <your prompt>`", "Command · /nocache")
                } else {
                    sendMessage(arg, bypassCache = true)
                }
                return true
            }
            "/retry" -> {
                val lastUser = _state.value.activeSession?.messages?.lastOrNull { it.role == "user" && !it.content.startsWith("/") }
                if (lastUser != null) {
                    sendMessage(lastUser.content, bypassCache = true)
                } else {
                    appendLocalCommandReply(raw, "No previous user message to retry.", "Command · /retry")
                }
                return true
            }
            "/gateway" -> {
                when (arg.lowercase()) {
                    "on", "start" -> {
                        toggleLocalGateway(true)
                        appendLocalCommandReply(raw, "Local OpenCode API Gateway started at `http://127.0.0.1:8910/v1` (model: `aura/auto`).", "Command · /gateway")
                    }
                    "off", "stop" -> {
                        toggleLocalGateway(false)
                        appendLocalCommandReply(raw, "Local API Gateway stopped.", "Command · /gateway")
                    }
                    else -> {
                        val st = if (_state.value.gatewayRunning) "RUNNING at http://127.0.0.1:8910/v1" else "STOPPED"
                        appendLocalCommandReply(raw, "Local Gateway is **$st**. Use `/gateway on` or `/gateway off`.", "Command · /gateway")
                    }
                }
                return true
            }
            "/export" -> {
                val sess = _state.value.activeSession
                if (sess == null || sess.messages.isEmpty()) {
                    appendLocalCommandReply(raw, "Current chat is empty; nothing to export.", "Command · /export")
                } else {
                    val fileName = "chat_${System.currentTimeMillis() / 1000L}.md"
                    val md = buildString {
                        appendLine("# ${sess.title}")
                        appendLine()
                        sess.messages.forEach { m ->
                            appendLine("### ${m.role.uppercase()}")
                            appendLine(m.content)
                            appendLine()
                        }
                    }
                    val outFile = File(store.workspaceDir, fileName)
                    outFile.writeText(md)
                    appendLocalCommandReply(raw, "Exported ${sess.messages.size} messages to local workspace: `$fileName`", "Command · /export")
                }
                return true
            }
            "/subagent" -> {
                val sp = arg.split(Regex("\\s+"), limit = 2)
                if (sp.size < 2) {
                    val list = _state.value.subagents.joinToString("\n") { "- ${it.name} (${it.role}) · Effort: ${it.reasoningEffort.label}" }
                    appendLocalCommandReply(raw, "Custom Subagents:\n$list\n\nUsage: `/subagent <name> <task>`", "Command · /subagent")
                } else {
                    val targetName = sp[0]
                    val taskText = sp[1]
                    _state.update { it.copy(isGenerating = true) }
                    activeJob = viewModelScope.launch {
                        try {
                            val out = executeSubagentCall(targetName, taskText)
                            appendLocalCommandReply(raw, out, "Subagent · $targetName")
                        } catch (e: Exception) {
                            appendLocalCommandReply(raw, "Subagent error: ${e.message}", "Subagent · Error")
                        } finally {
                            _state.update { it.copy(isGenerating = false) }
                        }
                    }
                }
                return true
            }
            "/effort" -> {
                val eff = when (arg.lowercase()) {
                    "low" -> ReasoningEffort.LOW
                    "medium", "med" -> ReasoningEffort.MEDIUM
                    "high" -> ReasoningEffort.HIGH
                    else -> null
                }
                if (eff != null) {
                    setReasoningEffort(eff)
                    appendLocalCommandReply(raw, "Reasoning effort set to **${eff.label}** (max ${eff.maxToolSteps} tool steps).", "Command · /effort")
                } else {
                    appendLocalCommandReply(raw, "Current reasoning effort: **${_state.value.effectiveReasoningEffort.label}**. Use `/effort low|medium|high`.", "Command · /effort")
                }
                return true
            }
            "/policy" -> {
                val pParts = arg.split(Regex("\\s+"))
                if (pParts.size == 2) {
                    val tool = pParts[0]
                    val mode = when (pParts[1].lowercase()) {
                        "allow" -> ToolPermissionMode.ALLOW
                        "ask" -> ToolPermissionMode.ASK
                        "deny", "block" -> ToolPermissionMode.DENY
                        else -> null
                    }
                    if (mode != null) {
                        setToolPolicy(tool, mode)
                        appendLocalCommandReply(raw, "Policy for `$tool` set to **${mode.label}**.", "Command · /policy")
                    } else {
                        appendLocalCommandReply(raw, "Invalid mode. Use allow, ask, or deny.", "Command · /policy")
                    }
                } else {
                    val policies = _state.value.toolPolicies
                    val summary = if (policies.isEmpty()) "All tools default to Auto-Allow."
                    else policies.entries.joinToString("\n") { "- `${it.key}`: ${it.value.label}" }
                    appendLocalCommandReply(raw, "Action Policies:\n$summary\n\nUsage: `/policy http_fetch ask`", "Command · /policy")
                }
                return true
            }
            else -> return false
        }
    }

    private fun runContextCompaction(rawCmd: String, focus: String) {
        val sess = _state.value.activeSession
        if (sess == null || sess.messages.size < 3) {
            appendLocalCommandReply(rawCmd, "Not enough messages in this chat to compact yet (need at least 3 messages).", "Command · /compact")
            return
        }
        val beforeTokens = sess.messages.sumOf { AuraCacheEngine.estimateTokens(it.content) }
        val transcript = sess.messages.joinToString("\n\n") { m ->
            "${m.role.uppercase()}: ${m.content.take(1600)}"
        }
        val focusNote = if (focus.isNotBlank()) "Pay special attention to: $focus\n" else ""
        val compactPrompt = "${focusNote}Summarize this conversation into a dense, lossless working memory checkpoint. Preserve all user goals, key facts, decisions, code identifiers, file paths, and unresolved tasks. Be concise.\n\n$transcript"

        _state.update { it.copy(isGenerating = true) }
        activeJob = viewModelScope.launch {
            try {
                val routed = router.completeWithRouting(
                    providers = _state.value.providers,
                    activeProviderId = _state.value.activeProviderId,
                    config = _state.value.routerConfig,
                    systemPrompt = "You are a precise context compaction engine.",
                    messages = listOf(LlmTurnMessage(role = "user", content = compactPrompt)),
                    tools = emptyList(),
                    latestUserPrompt = compactPrompt,
                    onDelta = {}
                )
                val summary = routed.response.text.trim()
                val afterTokens = AuraCacheEngine.estimateTokens(summary)
                val saved = (beforeTokens - afterTokens).coerceAtLeast(0L)
                val compactedMsg = ChatMessage(
                    role = "assistant",
                    content = "Compacted Context Checkpoint (~$beforeTokens -> ~$afterTokens tokens, saved ~$saved tokens):\n\n$summary",
                    cacheHitBadge = "Compacted (-$saved tok)",
                    routedProviderBadge = "${routed.usedProvider.name} · ${routed.usedProvider.model}"
                )
                _state.update { s ->
                    s.copy(
                        sessions = s.sessions.map { sc ->
                            if (sc.id == sess.id) sc.copy(messages = listOf(compactedMsg), updatedAt = System.currentTimeMillis()) else sc
                        },
                        routerStats = s.routerStats.copy(estimatedTokensSaved = s.routerStats.estimatedTokensSaved + saved)
                    )
                }
            } catch (e: Exception) {
                appendLocalCommandReply(rawCmd, "Compaction failed: ${e.message}", "Command · /compact")
            } finally {
                _state.update { it.copy(isGenerating = false) }
                persist()
            }
        }
    }

    private fun appendLocalCommandReply(commandText: String, replyText: String, badge: String) {
        val userMsg = ChatMessage(role = "user", content = commandText)
        val replyMsg = ChatMessage(role = "assistant", content = replyText, cacheHitBadge = badge)
        val existing = _state.value.activeSession
        if (existing == null) {
            val newSession = ChatSession(
                title = commandText.take(36),
                gemId = _state.value.activeGemId,
                messages = listOf(userMsg, replyMsg)
            )
            _state.update { it.copy(sessions = listOf(newSession) + it.sessions, activeSessionId = newSession.id) }
        } else {
            _state.update { s ->
                s.copy(
                    sessions = s.sessions.map { sess ->
                        if (sess.id == existing.id) sess.copy(messages = sess.messages + userMsg + replyMsg) else sess
                    }
                )
            }
        }
        persist()
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
