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
        val isAlibabaEndpoint = ModelRequestPolicy.isQwenCloudBaseUrl(request.provider.baseUrl)
        if (!isAlibabaEndpoint) {
            // The built-in preset is editable. If it is repointed to another compatible
            // service, do not let its historical qwen-cloud ID trigger Alibaba-only
            // serialization in the generic transport. This copy is request-local only.
            val neutralized = if (request.provider.id.equals("qwen-cloud", ignoreCase = true)) {
                request.copy(provider = request.provider.copy(id = "custom-openai-compatible"))
            } else {
                request
            }
            generic.stream(neutralized, emit)
            return
        }

        if (ModelRequestPolicy.isQwenCloudImageModel(request.provider, request.model)) {
            qwenImage.stream(request, emit)
            return
        }

        // MiniMax-M2.x on Alibaba exposes reasoning_content but the current
        // OpenAI-compatible documentation does not expose an enable_thinking /
        // thinking request control. Keep the UI metadata as thinking-capable while
        // suppressing Qwen/GLM-specific thinking parameters on the wire.
        val normalized = if (!AlibabaRequestCapabilities.usesEnableThinking(request.provider, request.model)) {
            request.copy(model = request.model.copy(supportsThinking = false))
        } else {
            request
        }
        generic.stream(normalized, emit)
    }
}
