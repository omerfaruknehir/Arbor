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
    private val automaticOpenAiCompatiblePresetIds = setOf("openai", "deepseek", "openrouter", "xai", "qwen-cloud", "ollama")

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

    fun isQwenCloudBaseUrl(rawBaseUrl: String): Boolean {
        val uri = runCatching { URI(rawBaseUrl.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path?.trimEnd('/').orEmpty()
        return uri.scheme.equals("https", ignoreCase = true) &&
            (host.contains("dashscope") || host.endsWith(".maas.aliyuncs.com")) &&
            path.endsWith("/compatible-mode/v1")
    }

    fun isQwenCloud(provider: ProviderEntity, model: ModelEntity): Boolean =
        provider.kind == ProviderKind.OPENAI_COMPATIBLE &&
            (provider.id.equals("qwen-cloud", ignoreCase = true) ||
                (isQwenCloudBaseUrl(provider.baseUrl) && model.modelId.startsWith("qwen", ignoreCase = true)))

    /** Qwen-Image is served by DashScope native multimodal generation, not compatible-mode Images. */
    fun isQwenCloudImageModel(provider: ProviderEntity, model: ModelEntity): Boolean =
        provider.kind == ProviderKind.OPENAI_COMPATIBLE &&
            isQwenCloudBaseUrl(provider.baseUrl) &&
            isQwenImageGenerationModelId(model.modelId)

    fun qwenCloudImageEndpoint(provider: ProviderEntity): String {
        require(isQwenCloudBaseUrl(provider.baseUrl)) {
            "Qwen-Image requires an Alibaba Cloud Model Studio compatible-mode base URL."
        }
        val uri = URI(provider.baseUrl.trim())
        return "https://${uri.rawAuthority}/api/v1/services/aigc/multimodal-generation/generation"
    }

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

    /**
     * Model Studio's OpenAI-compatible /models response is intentionally sparse.
     * Preserve explicit provider metadata, then fill documented capabilities for
     * model families whose request protocol is stable enough for Xylune to use.
     */
    fun mergeQwenCloudCatalog(
        providerId: String = "qwen-cloud",
        discovered: List<DiscoveredModel>,
    ): List<DiscoveredModel> {
        val byId = discovered.associateByTo(linkedMapOf()) { it.id }
        DefaultCatalog.models.filter { it.providerId == "qwen-cloud" }.forEach { bundled ->
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
                supportsImageGeneration = existing?.supportsImageGeneration ?: bundled.supportsImageGeneration,
                description = existing?.description.orEmpty(),
                createdAtEpochSeconds = existing?.createdAtEpochSeconds ?: 0,
                inputCacheHitUsdPerMillion = existing?.inputCacheHitUsdPerMillion,
                inputCacheMissUsdPerMillion = existing?.inputCacheMissUsdPerMillion,
                outputUsdPerMillion = existing?.outputUsdPerMillion,
                reasoningMetadataAvailable = existing?.reasoningMetadataAvailable ?: false,
                reasoningEfforts = existing?.reasoningEfforts.orEmpty(),
                reasoningDefaultEffort = existing?.reasoningDefaultEffort,
                reasoningDefaultEnabled = existing?.reasoningDefaultEnabled ?: false,
                reasoningMandatory = existing?.reasoningMandatory ?: false,
                reasoningSupportsMaxTokens = existing?.reasoningSupportsMaxTokens ?: false,
                metadataSource = existing?.metadataSource?.ifBlank { "Alibaba Cloud Model Studio" }
                    ?: "Alibaba Cloud Model Studio",
            )
        }
        return byId.values
            .map(::enrichAlibabaModelMetadata)
            .map { model ->
                model.copy(
                    metadataSource = model.metadataSource.ifBlank {
                        if (providerId.equals("qwen-cloud", ignoreCase = true)) {
                            "Alibaba Cloud Model Studio"
                        } else {
                            "Alibaba Cloud Model Studio ($providerId)"
                        }
                    },
                )
            }
            .sortedBy { it.displayName.lowercase() }
    }

    fun endpoint(provider: ProviderEntity, model: ModelEntity, continuation: Boolean = false): String {
        val root = provider.baseUrl.trimEnd('/')
        return when (requestType(provider, model)) {
            ModelRequestType.IMAGE_GENERATION -> when {
                isOpenRouter(provider) -> "$root/images"
                isQwenCloudImageModel(provider, model) -> qwenCloudImageEndpoint(provider)
                else -> "$root/images/generations"
            }
            ModelRequestType.CHAT -> if (provider.id == "deepseek" && continuation) {
                "$root/beta/chat/completions"
            } else "$root/chat/completions"
        }
    }

    private data class AlibabaModelHint(
        val contextWindow: Int? = null,
        val maxOutputTokens: Int? = null,
        val supportsThinking: Boolean? = null,
        val supportsVision: Boolean? = null,
        val supportsTools: Boolean? = null,
        val supportsImageGeneration: Boolean? = null,
        val reasoningDefaultEnabled: Boolean = false,
        val reasoningMandatory: Boolean = false,
    )

    private fun enrichAlibabaModelMetadata(model: DiscoveredModel): DiscoveredModel {
        val hint = alibabaModelHint(model.id) ?: return model
        val hasReasoningHint = hint.supportsThinking == true
        return model.copy(
            contextWindow = model.contextWindow ?: hint.contextWindow,
            maxOutputTokens = model.maxOutputTokens ?: hint.maxOutputTokens,
            supportsThinking = model.supportsThinking ?: hint.supportsThinking,
            supportsVision = model.supportsVision ?: hint.supportsVision,
            supportsTools = model.supportsTools ?: hint.supportsTools,
            supportsImageGeneration = model.supportsImageGeneration ?: hint.supportsImageGeneration,
            reasoningMetadataAvailable = model.reasoningMetadataAvailable || hasReasoningHint,
            reasoningDefaultEnabled = if (model.reasoningMetadataAvailable) {
                model.reasoningDefaultEnabled
            } else hint.reasoningDefaultEnabled,
            reasoningMandatory = if (model.reasoningMetadataAvailable) {
                model.reasoningMandatory
            } else hint.reasoningMandatory,
        )
    }

    private fun alibabaModelHint(rawId: String): AlibabaModelHint? {
        val id = rawId.lowercase()
        return when {
            isQwenImageGenerationModelId(id) -> AlibabaModelHint(
                supportsThinking = false,
                supportsTools = false,
                supportsImageGeneration = true,
            )
            id.startsWith("qwen3.7-plus") -> AlibabaModelHint(
                contextWindow = 1_000_000,
                maxOutputTokens = 65_536,
                supportsThinking = true,
                supportsVision = true,
                supportsTools = true,
                reasoningDefaultEnabled = true,
            )
            id.startsWith("qwen3.7-max") -> AlibabaModelHint(
                contextWindow = 1_000_000,
                maxOutputTokens = 65_536,
                supportsThinking = true,
                supportsVision = false,
                supportsTools = true,
                reasoningDefaultEnabled = true,
                reasoningMandatory = id.contains("max-preview") || id.contains("2026-05-17"),
            )
            id.startsWith("qwen3.6-plus") || id.startsWith("qwen3.6-flash") -> AlibabaModelHint(
                contextWindow = 1_000_000,
                maxOutputTokens = 65_536,
                supportsThinking = true,
                supportsVision = true,
                supportsTools = true,
                reasoningDefaultEnabled = true,
            )
            id.startsWith("qwen3.6-max-preview") -> AlibabaModelHint(
                supportsThinking = true,
                supportsTools = true,
                reasoningDefaultEnabled = true,
            )
            id.startsWith("qwen3.6-") -> AlibabaModelHint(
                supportsThinking = true,
                supportsVision = true,
                supportsTools = true,
                reasoningDefaultEnabled = true,
            )
            id.startsWith("qwen3.5-") -> AlibabaModelHint(
                contextWindow = if (id.startsWith("qwen3.5-plus") || id.startsWith("qwen3.5-flash")) 1_000_000 else null,
                maxOutputTokens = if (id.startsWith("qwen3.5-plus") || id.startsWith("qwen3.5-flash")) 65_536 else null,
                supportsThinking = true,
                supportsVision = true,
                supportsTools = true,
                reasoningDefaultEnabled = true,
            )
            id.startsWith("qwen3-vl-") -> AlibabaModelHint(
                supportsThinking = true,
                supportsVision = true,
                supportsTools = true,
                reasoningDefaultEnabled = id.contains("thinking"),
                reasoningMandatory = id.contains("thinking"),
            )
            id.startsWith("qvq-") -> AlibabaModelHint(
                supportsThinking = true,
                supportsVision = true,
                reasoningDefaultEnabled = true,
                reasoningMandatory = true,
            )
            id.startsWith("qwq-") -> AlibabaModelHint(
                supportsThinking = true,
                reasoningDefaultEnabled = true,
                reasoningMandatory = true,
            )
            id.startsWith("glm-5.2") || id.startsWith("glm-5.1") || id == "glm-5" ||
                id.startsWith("glm-4.7") || id.startsWith("glm-4.6") -> AlibabaModelHint(
                supportsThinking = true,
                supportsVision = false,
                // GLM function calling additionally needs tool_stream; do not advertise it until that transport is enabled.
                reasoningDefaultEnabled = true,
            )
            id.startsWith("kimi-k2.7-code") -> AlibabaModelHint(
                supportsThinking = true,
                supportsVision = true,
                reasoningDefaultEnabled = true,
                reasoningMandatory = true,
            )
            id.startsWith("kimi-k2.6") || id.startsWith("kimi-k2.5") -> AlibabaModelHint(
                supportsThinking = true,
                supportsVision = true,
                supportsTools = true,
                reasoningDefaultEnabled = false,
            )
            id.startsWith("deepseek-v4-") || id.startsWith("deepseek-v3.2") || id.startsWith("deepseek-v3.1") -> AlibabaModelHint(
                supportsThinking = true,
                supportsVision = false,
                supportsTools = true,
            )
            id.startsWith("deepseek-r1") -> AlibabaModelHint(
                supportsThinking = true,
                supportsVision = false,
                supportsTools = true,
                reasoningDefaultEnabled = true,
                reasoningMandatory = true,
            )
            else -> null
        }
    }

    private fun isQwenImageGenerationModelId(rawId: String): Boolean {
        val id = rawId.lowercase()
        return id.startsWith("qwen-image") && !id.startsWith("qwen-image-edit")
    }
}
