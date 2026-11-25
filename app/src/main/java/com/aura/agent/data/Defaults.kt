package com.aura.agent.data

object Defaults {
    fun defaultProviders(): List<ProviderConfig> = listOf(
        ProviderConfig(
            id = "gemini",
            name = "Google Gemini",
            protocol = ApiProtocol.GEMINI,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            model = "gemini-2.5-flash",
            availableModels = listOf(
                "gemini-2.5-flash",
                "gemini-2.5-pro",
                "gemini-2.0-flash"
            )
        ),
        ProviderConfig(
            id = "openai",
            name = "OpenAI",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-4o-mini",
            availableModels = listOf(
                "gpt-4o-mini",
                "gpt-4o",
                "o3-mini"
            )
        ),
        ProviderConfig(
            id = "anthropic",
            name = "Anthropic Claude",
            protocol = ApiProtocol.ANTHROPIC,
            baseUrl = "https://api.anthropic.com/v1",
            model = "claude-3-7-sonnet-latest",
            availableModels = listOf(
                "claude-3-7-sonnet-latest",
                "claude-3-5-sonnet-latest",
                "claude-3-5-haiku-latest"
            )
        ),
        ProviderConfig(
            id = "openrouter",
            name = "OpenRouter (200+ Models)",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            baseUrl = "https://openrouter.ai/api/v1",
            model = "google/gemini-2.5-flash",
            availableModels = listOf(
                "google/gemini-2.5-flash",
                "anthropic/claude-3.7-sonnet",
                "deepseek/deepseek-r1",
                "openai/gpt-4o-mini",
                "meta-llama/llama-3.3-70b-instruct"
            )
        ),
        ProviderConfig(
            id = "deepseek",
            name = "DeepSeek",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            baseUrl = "https://api.deepseek.com/v1",
            model = "deepseek-chat",
            availableModels = listOf("deepseek-chat", "deepseek-reasoner")
        ),
        ProviderConfig(
            id = "groq",
            name = "Groq",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            baseUrl = "https://api.groq.com/openai/v1",
            model = "llama-3.3-70b-versatile",
            availableModels = listOf("llama-3.3-70b-versatile", "qwen-2.5-32b")
        ),
        ProviderConfig(
            id = "ollama",
            name = "Ollama / Local Server",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            baseUrl = "http://10.0.2.2:11434/v1",
            apiKey = "ollama",
            model = "llama3.2",
            availableModels = listOf("llama3.2", "qwen2.5:7b", "deepseek-r1:8b")
        )
    )

    fun defaultGems(): List<Gem> = listOf(
        Gem(
            id = "gem-coder",
            name = "Coding Partner",
            description = "Senior engineer for architecture, debugging, and clean code.",
            instructions = "You are a pragmatic senior software engineer. Write clean, production-ready code, explain trade-offs concisely, and catch edge cases early. Match the user's language.",
            iconName = "code",
            accentHex = 0xFF8AB4F8,
            defaultSkillIds = listOf("skill-http-fetch", "skill-calculator"),
            isBuiltIn = true
        ),
        Gem(
            id = "gem-writer",
            name = "Writing Editor",
            description = "Polishes prose, structure, and tone without generic filler.",
            instructions = "You are a sharp, thoughtful editor. Improve clarity, rhythm, and precision while preserving the user's authentic voice. Avoid corporate cliches and robotic phrasing.",
            iconName = "edit",
            accentHex = 0xFFC58AF9,
            isBuiltIn = true
        ),
        Gem(
            id = "gem-brainstorm",
            name = "Brainstormer",
            description = "Generates creative angles, stress-tests ideas, and pushes back.",
            instructions = "You are a creative strategist and critical thinker. Offer unexpected angles, challenge weak assumptions constructively, and organize ideas clearly.",
            iconName = "lightbulb",
            accentHex = 0xFFFDD663,
            isBuiltIn = true
        ),
        Gem(
            id = "gem-research",
            name = "API & Web Agent",
            description = "Uses HTTP fetch, memory notes, and calculator skills autonomously.",
            instructions = "You are an autonomous Android AI agent. Proactively use your enabled tools (http_fetch, memory_save, memory_read, calculator, device_time) whenever live data, calculations, or persistent notes help answer accurately.",
            iconName = "hub",
            accentHex = 0xFF81C995,
            defaultSkillIds = listOf("skill-http-fetch", "skill-calculator", "skill-device-time", "skill-memory"),
            isBuiltIn = true
        )
    )

    fun defaultSkills(): List<AgentSkill> = listOf(
        AgentSkill(
            id = "skill-http-fetch",
            name = "HTTP / Web Fetch",
            slug = "http_fetch",
            description = "Fetches JSON or text content from any public URL or REST API endpoint.",
            type = SkillType.BUILTIN_TOOL,
            enabled = true,
            isBuiltIn = true
        ),
        AgentSkill(
            id = "skill-calculator",
            name = "Math Calculator",
            slug = "calculator",
            description = "Evaluates arithmetic expressions (+, -, *, /, ^, parentheses) accurately.",
            type = SkillType.BUILTIN_TOOL,
            enabled = true,
            isBuiltIn = true
        ),
        AgentSkill(
            id = "skill-device-time",
            name = "Device Clock & Date",
            slug = "device_time",
            description = "Returns the Android device's current date, time, and timezone.",
            type = SkillType.BUILTIN_TOOL,
            enabled = true,
            isBuiltIn = true
        ),
        AgentSkill(
            id = "skill-memory",
            name = "Persistent Memory Notes",
            slug = "memory_notes",
            description = "Lets the agent save, list, and retrieve key-value notes locally on your phone.",
            type = SkillType.BUILTIN_TOOL,
            enabled = true,
            isBuiltIn = true
        ),
        AgentSkill(
            id = "skill-concise-persian",
            name = "Persian Technical Style",
            slug = "persian_tech_style",
            description = "Prompt skill: Keeps Persian technical answers crisp, natural, and well-structured.",
            type = SkillType.PROMPT_SKILL,
            enabled = false,
            instructions = "---
name: persian-tech-style
description: Natural Persian technical communication.
---
When responding in Persian, keep technical terms accurate, avoid overly stiff translations, and structure steps clearly.",
            isBuiltIn = true
        )
    )
}
