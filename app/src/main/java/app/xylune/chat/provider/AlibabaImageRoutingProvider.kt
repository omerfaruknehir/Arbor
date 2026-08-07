package app.xylune.chat.provider

/**
 * Applies provider-level routing and request normalization for Alibaba Model Studio.
 * Qwen-Image uses DashScope's native multimodal endpoint; hosted third-party models
 * otherwise stay on the standard OpenAI-compatible chat transport.
 */
internal class AlibabaImageRoutingProvider(
    private val generic: ChatProvider,
    private val qwenImage: ChatProvider = QwenCloudImageProvider(generic),
) : ChatProvider {
    override suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) {
        val useNativeQwenImage =
            ModelRequestPolicy.isQwenCloudBaseUrl(request.provider.baseUrl) &&
                ModelRequestPolicy.isQwenCloudImageModel(request.provider, request.model)
        if (useNativeQwenImage) {
            qwenImage.stream(request, emit)
            return
        }

        // MiniMax-M2.x on Alibaba exposes reasoning_content but the current
        // OpenAI-compatible documentation does not expose an enable_thinking /
        // thinking request control. Keep the UI metadata as thinking-capable while
        // suppressing Qwen/GLM-specific thinking parameters on the wire.
        val normalized = if (
            ModelRequestPolicy.isAlibabaModelStudio(request.provider) &&
            !AlibabaRequestCapabilities.usesEnableThinking(request.provider, request.model)
        ) {
            request.copy(model = request.model.copy(supportsThinking = false))
        } else {
            request
        }
        generic.stream(normalized, emit)
    }
}
