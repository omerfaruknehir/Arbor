package app.arbor.chat.provider

import app.arbor.chat.data.ProviderKind

class ProviderRegistry {
    private val openAi = OpenAiCompatibleProvider()
    private val anthropic = AnthropicProvider()
    private val gemini = GeminiProvider()

    fun get(kind: ProviderKind): ChatProvider = when (kind) {
        ProviderKind.OPENAI_COMPATIBLE -> openAi
        ProviderKind.ANTHROPIC -> anthropic
        ProviderKind.GEMINI -> gemini
    }
}
