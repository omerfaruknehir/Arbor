package app.xylune.chat.data

object DefaultCatalog {
    val providers = listOf(
        ProviderEntity("deepseek", "DeepSeek", ProviderKind.OPENAI_COMPATIBLE, "https://api.deepseek.com"),
        ProviderEntity("openai", "OpenAI", ProviderKind.OPENAI_COMPATIBLE, "https://api.openai.com/v1"),
        ProviderEntity("openai-oauth", "ChatGPT account", ProviderKind.OPENAI_OAUTH, "https://chatgpt.com/backend-api/codex", apiKeyRequired = false),
        ProviderEntity("anthropic", "Anthropic", ProviderKind.ANTHROPIC, "https://api.anthropic.com/v1"),
        ProviderEntity("gemini", "Google Gemini", ProviderKind.GEMINI, "https://generativelanguage.googleapis.com/v1beta"),
        ProviderEntity("openrouter", "OpenRouter", ProviderKind.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1"),
        ProviderEntity("xai", "xAI", ProviderKind.OPENAI_COMPATIBLE, "https://api.x.ai/v1"),
        ProviderEntity("generic", "OpenAI-compatible", ProviderKind.OPENAI_COMPATIBLE, "https://example.com/v1"),
        ProviderEntity("ollama", "Ollama / llama.cpp / LM Studio", ProviderKind.OPENAI_COMPATIBLE, "http://127.0.0.1:11434/v1", apiKeyRequired = false),
    )

    // Prices are USD per million tokens, verified against DeepSeek's API pricing page on 2026-07-16.
    val models = listOf(
        ModelEntity("deepseek", "deepseek-v4-flash", "DeepSeek V4 Flash", 1_000_000, 384_000, 0.0028, 0.14, 0.28, pricingConfigured = true, supportsVision = false, supportsFiles = false, supportsThinking = true, supportsTools = true),
        ModelEntity("deepseek", "deepseek-v4-pro", "DeepSeek V4 Pro", 1_000_000, 384_000, 0.003625, 0.435, 0.87, pricingConfigured = true, supportsVision = false, supportsFiles = false, supportsThinking = true, supportsTools = true),
        ModelEntity("openai", "gpt-4.1", "GPT-4.1", 1_000_000, 32_768, 0.0, 0.0, 0.0, supportsVision = true, supportsFiles = true, supportsTools = true),
        ModelEntity("openai", "gpt-image-1", "GPT Image 1", 32_000, 1, 0.0, 0.0, 0.0, supportsVision = true, supportsImageGeneration = true),
        ModelEntity("openai", "gpt-image-1-mini", "GPT Image 1 Mini", 32_000, 1, 0.0, 0.0, 0.0, supportsVision = true, supportsImageGeneration = true),
        ModelEntity("anthropic", "claude-sonnet-4-20250514", "Claude Sonnet 4", 200_000, 64_000, 0.0, 0.0, 0.0, supportsVision = true, supportsFiles = true, supportsThinking = true, supportsTools = true),
        ModelEntity("gemini", "gemini-2.5-pro", "Gemini 2.5 Pro", 1_000_000, 65_536, 0.0, 0.0, 0.0, supportsVision = true, supportsFiles = true, supportsThinking = true, supportsTools = true),
        ModelEntity("openrouter", "openrouter/auto", "OpenRouter Auto", 128_000, 32_768, 0.0, 0.0, 0.0, supportsVision = true, supportsFiles = true, supportsTools = true),
        ModelEntity("xai", "grok-3", "Grok 3", 131_072, 32_768, 0.0, 0.0, 0.0, supportsVision = true, supportsTools = true),
        ModelEntity("generic", "custom-model", "Custom model", 128_000, 16_384, 0.0, 0.0, 0.0, supportsVision = true, supportsTools = true),
        ModelEntity("ollama", "local-model", "Local model", 128_000, 16_384, 0.0, 0.0, 0.0, supportsVision = true, supportsTools = true),
    )
}
