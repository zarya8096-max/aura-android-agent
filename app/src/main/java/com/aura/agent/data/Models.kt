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
    val role: String, // "user", "assistant", "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolTraces: List<ToolCallTrace> = emptyList(),
    val isStreaming: Boolean = false,
    val isError: Boolean = false
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
    val darkMode: Boolean = true
)
