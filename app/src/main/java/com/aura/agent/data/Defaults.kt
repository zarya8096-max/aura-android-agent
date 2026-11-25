package com.aura.agent.data

object Defaults {
    const val DEFAULT_SOUL_MD = """# SOUL.md — Core Agent Identity

## Voice & Stance
- Be direct, analytical, and grounded — act like a sharp peer collaborator, never a sycophantic assistant.
- Challenge weak assumptions constructively when warranted instead of defaulting to automatic agreement.
- Match the user's language naturally (Persian or English) with crisp formatting and zero filler.

## Autonomous Execution (Hermes Principles)
- Break complex requests into concrete tool calls before answering.
- Proactively store enduring user preferences, project facts, and verified findings in structured memory using `memory_save`.
- Before answering questions about past projects or preferences, check `memory_search`.
- Use the local `file_workspace` scratchpad to save structured notes, scripts, or drafts when useful.
"""

    const val DEFAULT_USER_MD = """# USER.md — Personal Context
- Preferred languages: Persian & English
- Values clean, fast, low-overhead engineering and honest technical pushback.
"""

    fun defaultProviders(): List<ProviderConfig> = listOf(
        ProviderConfig(
            id = "gemini",
            name = "Google Gemini",
            protocol = ApiProtocol.GEMINI,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            model = "gemini-2.5-flash",
            availableModels = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash")
        ),
        ProviderConfig(
            id = "openrouter",
            name = "OpenRouter (200+ Models & Free Tier)",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            baseUrl = "https://openrouter.ai/api/v1",
            model = "google/gemini-2.5-flash",
            availableModels = listOf(
                "google/gemini-2.5-flash",
                "anthropic/claude-3.7-sonnet",
                "deepseek/deepseek-r1:free",
                "meta-llama/llama-3.3-70b-instruct:free",
                "qwen/qwen-2.5-coder-32b-instruct:free",
                "openai/gpt-4o-mini"
            )
        ),
        ProviderConfig(
            id = "openai",
            name = "OpenAI",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-4o-mini",
            availableModels = listOf("gpt-4o-mini", "gpt-4o", "o3-mini")
        ),
        ProviderConfig(
            id = "anthropic",
            name = "Anthropic Claude",
            protocol = ApiProtocol.ANTHROPIC,
            baseUrl = "https://api.anthropic.com/v1",
            model = "claude-3-7-sonnet-latest",
            availableModels = listOf("claude-3-7-sonnet-latest", "claude-3-5-sonnet-latest", "claude-3-5-haiku-latest")
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
            name = "Groq (Ultra-Fast)",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            baseUrl = "https://api.groq.com/openai/v1",
            model = "llama-3.3-70b-versatile",
            availableModels = listOf("llama-3.3-70b-versatile", "qwen-2.5-32b")
        ),
        ProviderConfig(
            id = "xai",
            name = "xAI (Grok)",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            baseUrl = "https://api.x.ai/v1",
            model = "grok-2-latest",
            availableModels = listOf("grok-2-latest", "grok-2-mini")
        ),
        ProviderConfig(
            id = "mistral",
            name = "Mistral AI",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            baseUrl = "https://api.mistral.ai/v1",
            model = "mistral-large-latest",
            availableModels = listOf("mistral-large-latest", "codestral-latest")
        ),
        ProviderConfig(
            id = "together",
            name = "Together AI",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            baseUrl = "https://api.together.xyz/v1",
            model = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
            availableModels = listOf("meta-llama/Llama-3.3-70B-Instruct-Turbo", "Qwen/Qwen2.5-Coder-32B-Instruct")
        ),
        ProviderConfig(
            id = "cerebras",
            name = "Cerebras",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            baseUrl = "https://api.cerebras.ai/v1",
            model = "llama-3.3-70b",
            availableModels = listOf("llama-3.3-70b", "llama3.1-8b")
        ),
        ProviderConfig(
            id = "local-gguf",
            name = "Local GGUF (llama-server)",
            protocol = ApiProtocol.LOCAL_GGUF,
            baseUrl = "http://127.0.0.1:8080/v1",
            apiKey = "local",
            model = "local-model",
            availableModels = listOf("local-model")
        ),
        ProviderConfig(
            id = "ollama",
            name = "Ollama / Local Server",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            baseUrl = "http://127.0.0.1:11434/v1",
            apiKey = "ollama",
            model = "llama3.2",
            availableModels = listOf("llama3.2", "qwen2.5-coder:7b", "deepseek-r1:8b")
        )
    )

    fun defaultGems(): List<Gem> = listOf(
        Gem(
            id = "gem-hermes",
            name = "Hermes Autonomous",
            description = "Full multi-step agent using memory, web search, HTTP fetch, and local workspace.",
            instructions = "Operate as an autonomous Hermes-class agent. Plan multi-step tool execution, verify live facts via web_search/http_fetch, store key learnings in memory_save, and keep final replies concise.",
            iconName = "hub",
            accentHex = 0xFF7AB8FF,
            defaultSkillIds = listOf("skill-http-fetch", "skill-web-search", "skill-memory", "skill-workspace", "skill-calculator", "skill-device-time"),
            isBuiltIn = true
        ),
        Gem(
            id = "gem-coder",
            name = "Systems Engineer",
            description = "Clean architecture, debugging, code reviews, and zero-fluff solutions.",
            instructions = "You are a pragmatic senior systems engineer. Write production-ready code, identify edge cases immediately, and explain trade-offs clearly.",
            iconName = "code",
            accentHex = 0xFFE2C290,
            defaultSkillIds = listOf("skill-http-fetch", "skill-workspace", "skill-json-query", "skill-text-tools"),
            isBuiltIn = true
        ),
        Gem(
            id = "gem-writer",
            name = "Editorial Craft",
            description = "Sharp, human prose without AI-slop cliches.",
            instructions = "You are a ruthless, tasteful editor. Preserve the author's voice, cut filler, and avoid generic AI phrasing.",
            iconName = "edit",
            accentHex = 0xFF9EC5AB,
            isBuiltIn = true
        ),
        Gem(
            id = "gem-critic",
            name = "Critical Sparring",
            description = "Challenges assumptions and stress-tests technical or strategic ideas.",
            instructions = "Play devil's advocate constructively. Spot hidden flaws, missing constraints, and weak reasoning before proposing stronger alternatives.",
            iconName = "lightbulb",
            accentHex = 0xFFD98E73,
            isBuiltIn = true
        )
    )

    fun defaultSkills(): List<AgentSkill> = listOf(
        AgentSkill(
            id = "skill-web-search",
            name = "Web & Wiki Search",
            slug = "web_search",
            description = "Searches public DuckDuckGo Instant Answers and Wikipedia summaries for live facts.",
            type = SkillType.BUILTIN_TOOL,
            enabled = true,
            isBuiltIn = true
        ),
        AgentSkill(
            id = "skill-http-fetch",
            name = "HTTP / REST Fetch",
            slug = "http_fetch",
            description = "Calls public REST APIs or fetches webpage text (GET/POST) with automatic HTML stripping.",
            type = SkillType.BUILTIN_TOOL,
            enabled = true,
            isBuiltIn = true
        ),
        AgentSkill(
            id = "skill-memory",
            name = "Hermes Long-Term Memory",
            slug = "memory_notes",
            description = "Structured memory_save, memory_search, and memory_delete tools across sessions.",
            type = SkillType.BUILTIN_TOOL,
            enabled = true,
            isBuiltIn = true
        ),
        AgentSkill(
            id = "skill-workspace",
            name = "Local File Workspace",
            slug = "file_workspace",
            description = "Read, write, append, and list scratchpad files inside the app's local sandbox.",
            type = SkillType.BUILTIN_TOOL,
            enabled = true,
            isBuiltIn = true
        ),
        AgentSkill(
            id = "skill-calculator",
            name = "Scientific Calculator",
            slug = "calculator",
            description = "Evaluates math expressions (+, -, *, /, ^, parentheses).",
            type = SkillType.BUILTIN_TOOL,
            enabled = true,
            isBuiltIn = true
        ),
        AgentSkill(
            id = "skill-json-query",
            name = "JSON Path Extractor",
            slug = "json_query",
            description = "Extracts nested fields or array items from raw JSON strings.",
            type = SkillType.BUILTIN_TOOL,
            enabled = true,
            isBuiltIn = true
        ),
        AgentSkill(
            id = "skill-text-tools",
            name = "Crypto & Encoding Tools",
            slug = "text_transform",
            description = "Base64 encode/decode, SHA-256 hashing, URL encoding, and token/word counter.",
            type = SkillType.BUILTIN_TOOL,
            enabled = true,
            isBuiltIn = true
        ),
        AgentSkill(
            id = "skill-device-time",
            name = "Device Clock & Timestamp",
            slug = "device_time",
            description = "Returns exact device date, time, timezone, and Unix epoch.",
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
            instructions = "---\nname: persian-tech-style\ndescription: Natural Persian technical communication.\n---\nWhen responding in Persian, keep technical terms accurate, avoid overly stiff translations, and structure steps clearly.",
            isBuiltIn = true
        )
    )
}
