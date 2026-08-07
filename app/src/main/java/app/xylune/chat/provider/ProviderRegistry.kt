package app.xylune.chat.provider

import app.xylune.chat.data.ProviderKind

class ProviderRegistry(oauth: OpenAiOAuthManager) {
    private val openAiCompatible = OpenAiCompatibleProvider()
    private val openAi = AlibabaCloudRequestRoutingProvider(
        NativeWebSearchProvider(AlibabaImageRoutingProvider(openAiCompatible)),
    )
    private val openAiOAuth = OpenAiOAuthProvider(oauth)
    private val anthropic = NativeWebSearchProvider(AnthropicProvider())
    private val gemini = NativeWebSearchProvider(GeminiProvider())

    fun get(kind: ProviderKind): ChatProvider = when (kind) {
        ProviderKind.OPENAI_COMPATIBLE -> openAi
        ProviderKind.OPENAI_OAUTH -> openAiOAuth
        ProviderKind.ANTHROPIC -> anthropic
        ProviderKind.GEMINI -> gemini
    }
}
