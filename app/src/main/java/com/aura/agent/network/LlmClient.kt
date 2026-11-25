package com.aura.agent.network

import com.aura.agent.data.ApiProtocol
import com.aura.agent.data.ProviderConfig
import com.aura.agent.skills.ToolSpec

data class LlmTurnMessage(
    val role: String,
    val content: String = "",
    val toolCalls: List<LlmToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null
)

data class LlmToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String
)

data class LlmResponse(
    val text: String,
    val toolCalls: List<LlmToolCall> = emptyList()
)

interface LlmClient {
    suspend fun complete(
        provider: ProviderConfig,
        systemPrompt: String,
        messages: List<LlmTurnMessage>,
        tools: List<ToolSpec>,
        onDelta: (String) -> Unit
    ): LlmResponse
}

object LlmClientFactory {
    private val openAi = OpenAiCompatibleClient()
    private val gemini = GeminiClient()
    private val anthropic = AnthropicClient()

    fun forProtocol(protocol: ApiProtocol): LlmClient = when (protocol) {
        ApiProtocol.GEMINI -> gemini
        ApiProtocol.OPENAI_COMPATIBLE, ApiProtocol.LOCAL_GGUF -> openAi
        ApiProtocol.ANTHROPIC -> anthropic
    }
}
