package com.aura.agent.commands

data class SlashCommandSpec(
    val command: String,
    val usage: String,
    val description: String
)

object SlashCommandRegistry {
    val all: List<SlashCommandSpec> = listOf(
        SlashCommandSpec("/compact", "/compact [focus]", "Summarize & compress current chat context to save tokens"),
        SlashCommandSpec("/context", "/context", "Inspect current context size, token estimate, and active injections"),
        SlashCommandSpec("/clear", "/clear", "Clear all messages in the current chat"),
        SlashCommandSpec("/new", "/new [gem]", "Start a fresh chat (optionally with a specific Gem)"),
        SlashCommandSpec("/model", "/model [name]", "List providers or switch active API provider/model"),
        SlashCommandSpec("/gem", "/gem [name|off]", "List Gems, activate a Gem, or turn Gem off"),
        SlashCommandSpec("/remember", "/remember key: fact", "Save a pinned fact directly to long-term memory"),
        SlashCommandSpec("/memory", "/memory [query|open]", "Search long-term memory or open Agent Brain"),
        SlashCommandSpec("/soul", "/soul [edit|reset]", "Inspect, edit, or reset SOUL.md"),
        SlashCommandSpec("/skills", "/skills [toggle name]", "List or toggle agent skills and tools"),
        SlashCommandSpec("/router", "/router [auto|fallback|latency|manual]", "Show AuraRouter status or switch routing strategy"),
        SlashCommandSpec("/cache", "/cache [stats|clear]", "Show 4-tier cache hit rate or clear local cache"),
        SlashCommandSpec("/nocache", "/nocache <prompt>", "Send prompt directly to API, bypassing local cache"),
        SlashCommandSpec("/retry", "/retry", "Regenerate the last prompt live (bypassing cache)"),
        SlashCommandSpec("/gateway", "/gateway [on|off]", "Start or stop Local OpenCode API Gateway (127.0.0.1:8910)"),
        SlashCommandSpec("/export", "/export", "Save current conversation as Markdown in local workspace"),
        SlashCommandSpec("/effort", "/effort [low|medium|high]", "Set DeepSeek Harness reasoning effort for this chat"),
        SlashCommandSpec("/policy", "/policy [tool allow|ask|deny]", "View or set OpenBot tool permission policies"),
        SlashCommandSpec("/help", "/help", "Show all available agent slash commands")
    )

    fun filter(input: String): List<SlashCommandSpec> {
        if (!input.startsWith("/")) return emptyList()
        val token = input.substringBefore(" ").lowercase()
        return all.filter { it.command.startsWith(token) }
    }

    fun helpMarkdown(): String = buildString {
        appendLine("Available Agent Commands:")
        appendLine()
        all.forEach { spec ->
            appendLine("${spec.usage} — ${spec.description}")
        }
    }.trim()
}
