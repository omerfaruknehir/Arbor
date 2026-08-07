package app.xylune.chat.provider

import app.xylune.chat.data.ProviderKind

/**
 * Applies current Model Studio corrections after normal discovery. Alibaba's
 * OpenAI-compatible /models endpoint is intentionally sparse, so provider discovery
 * alone cannot be treated as a complete capability contract.
 */
class AlibabaCloudModelDiscoveryService(
    private val delegate: ModelDiscoveryService,
) {
    constructor(oauth: OpenAiOAuthManager?) : this(ModelDiscoveryService(oauth))

    suspend fun discover(
        kind: ProviderKind,
        rawBaseUrl: String,
        apiKey: String,
        customHeadersJson: String,
        providerId: String? = null,
    ): List<DiscoveredModel> {
        val discovered = delegate.discover(
            kind = kind,
            rawBaseUrl = rawBaseUrl,
            apiKey = apiKey,
            customHeadersJson = customHeadersJson,
            providerId = providerId,
        )
        val isAlibaba = kind == ProviderKind.OPENAI_COMPATIBLE &&
            (providerId.equals("qwen-cloud", ignoreCase = true) || ModelRequestPolicy.isQwenCloudBaseUrl(rawBaseUrl))
        return if (isAlibaba) discovered.map(AlibabaCloudModelPolicy::correct) else discovered
    }
}
