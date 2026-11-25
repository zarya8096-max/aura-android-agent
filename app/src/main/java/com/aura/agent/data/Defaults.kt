package com.aura.agent.data

object Defaults {
    const val DEFAULT_SOUL_MD = """# SOUL.md — Aura Core Identity

## Voice & Stance
- Be direct, analytical, and grounded — act like a sharp peer collaborator, never a sycophantic assistant.
- Challenge weak assumptions constructively when warranted instead of defaulting to automatic agreement.
- Match the user's language naturally (Persian or English) with crisp formatting and zero filler.

## Autonomous Execution
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
            model = "gemini-3-8-flash",
            availableModels = listOf(
                "gemini-3-8-flash",
                "gemini-4-argon",
                "gemini-3.6-flash",
                "gemini-3.5-flash-lite",
                "gemini-3-pro-preview"
            )
        ),
        ProviderConfig(
            id = "openai",
            name = "OpenAI",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-6.2-sol",
            availableModels = listOf(
                "gpt-6.2-sol",
                "gpt-6.1-sol",
                "gpt-5.4",
                "gpt-5.4-mini"
            )
        ),
        ProviderConfig(
            id = "openai-codex",
            name = "OpenAI Codex",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-5.3-codex",
            availableModels = listOf("gpt-5.3-codex", "gpt-5.2-codex", "gpt-6.2-sol")
        ),
        ProviderConfig(
            id = "anthropic",
            name = "Anthropic Claude",
            protocol = ApiProtocol.ANTHROPIC,
            baseUrl = "https://api.anthropic.com/v1",
            model = "claude-sonnet-5",
            availableModels = listOf(
                "claude-sonnet-5",
                "claude-opus-5-5",
                "claude-opus-4-6",
                "claude-sonnet-4-6"
            )
        ),
        ProviderConfig(
            id = "deepseek",
            name = "DeepSeek",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            baseUrl = "https://api.deepseek.com/v1",
            model = "deepseek-v4.1-flash",
            availableModels = listOf("deepseek-v4.1-flash", "deepseek-pro-latest", "deepseek-v4", "deepseek-v3.2")
        ),
        ProviderConfig(
            id = "xai",
            name = "xAI (Grok)",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            baseUrl = "https://api.x.ai/v1",
            model = "grok-4.7",
            availableModels = listOf("grok-4.7", "grok-4.6", "grok-4.5", "grok-4.3")
        ),
        ProviderConfig(
            id = "openrouter",
            name = "OpenRouter (Unified Catalog)",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            baseUrl = "https://openrouter.ai/api/v1",
            model = "google/gemini-3-8-flash",
            availableModels = listOf(
                "google/gemini-3-8-flash",
                "anthropic/claude-sonnet-5",
                "openai/gpt-6.2-sol",
                "deepseek/deepseek-v4.1-flash",
                "x-ai/grok-4.7"
            )
        ),
        ProviderConfig(
            id = "nous-portal",
            name = "Nous Portal",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            baseUrl = "https://inference-api.nousresearch.com/v1",
            model = "Hermes-4-70B",
            availableModels = listOf("Hermes-4-70B", "Hermes-4-405B", "Hermes-4.3-36B")
        ),
        ProviderConfig(
            id = "google-antigravity",
            name = "Google Antigravity / AI Studio",
            protocol = ApiProtocol.GEMINI,
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            model = "gemini-4-argon",
            availableModels = listOf("gemini-4-argon", "gemini-3-8-flash", "gemini-3-pro-preview")
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
            id = "mistral",
            name = "Mistral AI",
            protocol = ApiProtocol.OPENAI_COMPATIBLE,
            baseUrl = "https://api.mistral.ai/v1",
            model = "mistral-large-latest",
            availableModels = listOf("mistral-large-latest", "codestral-latest")
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
            model = "qwen3:8b",
            availableModels = listOf("qwen3:8b", "llama3.3", "deepseek-r1:8b")
        )
    )

    fun defaultGems(): List<Gem> = listOf(
        Gem(
            id = "gem-autonomous",
            name = "Aura Autonomous",
            description = "Multi-step agent using memory, web search, HTTP fetch, and local workspace.",
            instructions = "Operate as an autonomous multi-step agent. Plan tool execution, verify live facts via web_search/http_fetch, store key learnings in memory_save, and keep final replies concise.",
            iconName = "hub",
            accentHex = 0xFF8AB4F8,
            defaultSkillIds = listOf("skill-http-fetch", "skill-web-search", "skill-memory", "skill-workspace", "skill-calculator", "skill-device-time"),
            pinned = true,
            isBuiltIn = true
        ),
        Gem(
            id = "gem-coder",
            name = "Coding Partner",
            description = "Level up your coding skills, debug issues, and design clean architecture.",
            instructions = "You are a pragmatic senior software engineer and coding partner. Write clean, production-ready code, catch edge cases early, and explain trade-offs concisely.",
            iconName = "code",
            accentHex = 0xFF7AB8FF,
            defaultSkillIds = listOf("skill-http-fetch", "skill-workspace", "skill-json-query", "skill-text-tools"),
            pinned = true,
            isBuiltIn = true
        ),
        Gem(
            id = "gem-brainstorm",
            name = "Brainstormer",
            description = "Easily find inspiration, fresh angles, and structured ideas for any project.",
            instructions = "Act as a sharp brainstorming partner. Generate diverse, non-generic ideas, challenge weak assumptions constructively, and organize concepts into clear next steps.",
            iconName = "lightbulb",
            accentHex = 0xFFFDD663,
            isBuiltIn = true
        ),
        Gem(
            id = "gem-learning",
            name = "Learning Coach",
            description = "Break down complex topics step-by-step with quizzes and intuition.",
            instructions = "You are a patient, high-clarity learning coach. Explain complex topics from first principles using concrete examples, then check understanding with short practice questions.",
            iconName = "school",
            accentHex = 0xFF81C995,
            isBuiltIn = true
        ),
        Gem(
            id = "gem-writer",
            name = "Writing Editor",
            description = "Elevate your writing with crisp feedback on clarity, structure, and tone.",
            instructions = "You are a sharp, tasteful editor. Preserve the author's authentic voice, cut filler, improve rhythm and clarity, and avoid generic phrasing.",
            iconName = "edit",
            accentHex = 0xFFC58AF9,
            isBuiltIn = true
        ),
        Gem(
            id = "gem-career",
            name = "Career Guide",
            description = "Unlock career clarity with tailored interview prep, strategy, and feedback.",
            instructions = "Act as a pragmatic career strategist. Give concrete, actionable advice on resumes, interviews, negotiation, and skill roadmaps.",
            iconName = "work",
            accentHex = 0xFFF28B82,
            isBuiltIn = true
        )
    )

    fun defaultSkills(): List<AgentSkill> = listOf(
        AgentSkill(
            id = "skill-subagent",
            name = "Subagent Delegation",
            slug = "spawn_subagent",
            description = "Delegates an isolated subtask to a specialized subagent and returns its result.",
            type = SkillType.BUILTIN_TOOL,
            enabled = true,
            isBuiltIn = true
        ),
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
            name = "Long-Term Memory",
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

    fun defaultSubagents(): List<SubagentConfig> = listOf(
        SubagentConfig(
            id = "sub-reviewer",
            name = "Code Reviewer",
            role = "Senior Code & Security Reviewer",
            systemPrompt = "Audit code strictly for bugs, race conditions, security flaws, and performance bottlenecks. Return concise, prioritized findings with exact fixes.",
            reasoningEffort = ReasoningEffort.HIGH,
            temperature = 0.2f,
            isBuiltIn = true
        ),
        SubagentConfig(
            id = "sub-researcher",
            name = "Fact Checker",
            role = "Fast Research & Verification Subagent",
            systemPrompt = "Verify claims concisely, separate confirmed facts from assumptions, and return a compact summary.",
            reasoningEffort = ReasoningEffort.MEDIUM,
            temperature = 0.3f,
            isBuiltIn = true
        ),
        SubagentConfig(
            id = "sub-summarizer",
            name = "Fast Summarizer",
            role = "Ultra-Fast Context Compactor",
            systemPrompt = "Compress long inputs into dense bullet points preserving all numbers, names, and decisions.",
            reasoningEffort = ReasoningEffort.LOW,
            temperature = 0.2f,
            isBuiltIn = true
        )
    )
}
