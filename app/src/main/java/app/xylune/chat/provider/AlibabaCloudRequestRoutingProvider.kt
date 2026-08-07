package app.xylune.chat.provider

import app.xylune.chat.settings.WebSearchRoute

/**
 * Normalizes Model Studio requests before native-web-search selection. This wrapper
 * must stay outside [NativeWebSearchProvider] so unsupported regional/snapshot model
 * IDs are sent through Xylune's client-side search path instead of /responses.
 */
internal class AlibabaCloudRequestRoutingProvider(
    private val delegate: ChatProvider,
) : ChatProvider {
    override suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) {
        if (!ModelRequestPolicy.isAlibabaModelStudio(request.provider)) {
            delegate.stream(request, emit)
            return
        }

        val correctedModel = AlibabaCloudModelPolicy.correct(request.model)
        var corrected = if (correctedModel == request.model) request else request.copy(model = correctedModel)
        if (NativeWebSearch.requested(corrected)) {
            val thinkingEnabled = effectiveThinkingEnabled(corrected.model, corrected.thinkingEnabled)
            val nativeSearchSupported = AlibabaCloudModelPolicy.supportsResponsesWebSearch(
                corrected.model.modelId,
                thinkingEnabled,
            )
            if (!nativeSearchSupported) {
                if (corrected.webSearchRoute == WebSearchRoute.NATIVE_ONLY) {
                    throw ProviderProtocolException(
                        "${corrected.provider.displayName} / ${corrected.model.displayName} does not expose native web search.",
                    )
                }
                corrected = corrected.copy(webSearchRoute = WebSearchRoute.SEARCH_ENGINE)
            }
        }
        delegate.stream(corrected, emit)
    }
}
