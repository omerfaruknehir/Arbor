package app.arbor.chat.provider

import app.arbor.chat.data.ProviderEntity

/** Keeps provider availability decisions identical in selectors and at execution time. */
object ProviderCredentialPolicy {
    fun allowsBlankApiKey(provider: ProviderEntity): Boolean = !provider.apiKeyRequired

    fun isRegistered(provider: ProviderEntity, apiKey: String): Boolean =
        provider.registered || apiKey.isNotBlank()

    fun isUsable(provider: ProviderEntity, apiKey: String): Boolean =
        isRegistered(provider, apiKey) && provider.enabled && (apiKey.isNotBlank() || allowsBlankApiKey(provider))
}
