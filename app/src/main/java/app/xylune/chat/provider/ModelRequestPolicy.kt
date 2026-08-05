package app.xylune.chat.provider

import app.xylune.chat.data.DefaultCatalog
import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.ProviderEntity
import app.xylune.chat.data.ProviderKind
import java.net.URI

enum class ModelRequestType { CHAT, IMAGE_GENERATION }

/**
 * Resolves transport from provider presets and model identity. The persisted
 * image flag is only a compact request-type override for genuinely custom
 * OpenAI-compatible endpoints; official OpenAI presets are authoritative.
 */
object ModelRequestPolicy {
    private val officialOpenAiImageIds = setOf("gpt-image-1", "gpt-image-1-mini")
    private val automaticOpenAiCompatiblePresetIds = setOf("openai", "deepseek", "openrouter", "xai", "ollama")

    fun isOfficialOpenAiBaseUrl(rawBaseUrl: String): Boolean {
        val uri = runCatching { URI(rawBaseUrl.trim()) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("api.openai.com", ignoreCase = true) &&
            (uri.path.isNullOrBlank() || uri.path.trimEnd('/') == "/v1")
    }

    fun isOpenRouterBaseUrl(rawBaseUrl: String): Boolean {
        val uri = runCatching { URI(rawBaseUrl.trim()) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("openrouter.ai", ignoreCase = true) &&
            (uri.path.isNullOrBlank() || uri.path.trimEnd('/') == "/api/v1")
    }

    fun isOpenRouter(provider: ProviderEntity): Boolean =
        provider.kind == ProviderKind.OPENAI_COMPATIBLE &&
            (provider.id == "openrouter" || isOpenRouterBaseUrl(provider.baseUrl))

    fun isOfficialOpenAi(provider: ProviderEntity): Boolean =
        provider.kind == ProviderKind.OPENAI_COMPATIBLE &&
            (provider.id == "openai" || isOfficialOpenAiBaseUrl(provider.baseUrl))

    fun usesManualRequestType(provider: ProviderEntity): Boolean =
        provider.kind == ProviderKind.OPENAI_COMPATIBLE &&
            !isOfficialOpenAi(provider) &&
            !isOpenRouter(provider) &&
            provider.id !in automaticOpenAiCompatiblePresetIds

    fun requestType(provider: ProviderEntity, model: ModelEntity): ModelRequestType = when {
        isOfficialOpenAi(provider) -> if (model.modelId.substringAfterLast('/') in officialOpenAiImageIds) {
            ModelRequestType.IMAGE_GENERATION
        } else ModelRequestType.CHAT
        model.supportsImageGeneration -> ModelRequestType.IMAGE_GENERATION
        else -> ModelRequestType.CHAT
    }

    fun normalize(provider: ProviderEntity, model: ModelEntity): ModelEntity = model.copy(
        supportsImageGeneration = requestType(provider, model) == ModelRequestType.IMAGE_GENERATION,
    )

    fun officialOpenAiImageModels(providerId: String = "openai"): List<ModelEntity> =
        DefaultCatalog.models.filter { it.providerId == "openai" && it.modelId in officialOpenAiImageIds }
            .map { it.copy(providerId = providerId, supportsImageGeneration = true) }

    fun mergeOfficialOpenAiCatalog(rawBaseUrl: String, discovered: List<DiscoveredModel>): List<DiscoveredModel> {
        if (!isOfficialOpenAiBaseUrl(rawBaseUrl)) return discovered
        val byId = discovered.associateByTo(linkedMapOf()) { it.id }
        officialOpenAiImageModels().forEach { bundled ->
            val existing = byId[bundled.modelId]
            byId[bundled.modelId] = DiscoveredModel(
                id = bundled.modelId,
                displayName = existing?.displayName ?: bundled.displayName,
                contextWindow = existing?.contextWindow ?: bundled.contextWindow,
                maxOutputTokens = existing?.maxOutputTokens ?: bundled.maxOutputTokens,
                supportsThinking = existing?.supportsThinking ?: bundled.supportsThinking,
                supportsVision = existing?.supportsVision ?: bundled.supportsVision,
                supportsFiles = existing?.supportsFiles ?: bundled.supportsFiles,
                supportsTools = existing?.supportsTools ?: bundled.supportsTools,
                supportsImageGeneration = true,
            )
        }
        return byId.values.sortedBy { it.displayName.lowercase() }
    }

    fun endpoint(provider: ProviderEntity, model: ModelEntity, continuation: Boolean = false): String {
        val root = provider.baseUrl.trimEnd('/')
        return when (requestType(provider, model)) {
            ModelRequestType.IMAGE_GENERATION -> if (isOpenRouter(provider)) "$root/images" else "$root/images/generations"
            ModelRequestType.CHAT -> if (provider.id == "deepseek" && continuation) {
                "$root/beta/chat/completions"
            } else "$root/chat/completions"
        }
    }
}
