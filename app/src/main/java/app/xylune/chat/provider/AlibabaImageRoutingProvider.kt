package app.xylune.chat.provider

/**
 * Keeps Qwen-Image's DashScope-native transport scoped to an actual Alibaba
 * compatible-mode endpoint. If a user repoints the qwen-cloud preset at a
 * different OpenAI-compatible server, image models stay on that server's
 * generic image transport rather than inheriting an Alibaba URL from the preset id.
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
        } else {
            generic.stream(request, emit)
        }
    }
}
