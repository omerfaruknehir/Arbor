package app.arbor.chat.provider

import app.arbor.chat.data.ProviderKind

class ProviderRegistry(oauth: OpenAiOAuthManager) {
    private val openAi = OpenAiCompatibleProvider()
    private val openAiOAuth = OpenAiOAuthProvider(oauth)
    private val anthropic = AnthropicProvider()
    private val gemini = GeminiProvider()

    fun get(kind: ProviderKind): ChatProvider = when (kind) {
        ProviderKind.OPENAI_COMPATIBLE -> openAi
        ProviderKind.OPENAI_OAUTH -> openAiOAuth
        ProviderKind.ANTHROPIC -> anthropic
        ProviderKind.GEMINI -> gemini
    }
}
