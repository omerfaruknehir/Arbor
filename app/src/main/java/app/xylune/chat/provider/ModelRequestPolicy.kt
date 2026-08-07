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
    private val qwen3OpenSourceHybridModels = setOf(
        "qwen3-235b-a22b",
        "qwen3-32b",
        "qwen3-30b-a3b",
        "qwen3-14b",
        "qwen3-8b",
    )

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
            (provider.id.equals("qwen-cloud", ignoreCase = true) || isQwenCloudBaseUrl(provider.baseUrl))

    /** Qwen-Image is served by DashScope native multimodal generation, not compatible-mode Images. */
    fun isQwenCloudImageModel(provider: ProviderEntity, model: ModelEntity): Boolean =
        provider.kind == ProviderKind.OPENAI_COMPATIBLE &&
            isQwenCloudBaseUrl(provider.baseUrl) &&
            isQwenNativeImageModelId(model.modelId)

    fun qwenCloudImageAcceptsInputImages(model: ModelEntity): Boolean =
        qwenImageAcceptsInputImages(model.modelId)

    fun qwenCloudImageRequiresInputImage(model: ModelEntity): Boolean =
        qwenImageRequiresInputImage(model.modelId)

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

    /** Repair model rows cached by older Xylune builds without waiting for a manual catalog refresh. */
    fun enrichQwenCloudStoredModel(model: ModelEntity): ModelEntity {
        if (!model.providerId.equals("qwen-cloud", ignoreCase = true)) return model
        val hint = alibabaModelHint(model.modelId) ?: return model
        return model.copy(
            contextWindow = hint.contextWindow ?: model.contextWindow,
            maxOutputTokens = hint.maxOutputTokens ?: model.maxOutputTokens,
            supportsThinking = hint.supportsThinking ?: model.supportsThinking,
            supportsVision = hint.supportsVision ?: model.supportsVision,
            supportsTools = hint.supportsTools ?: model.supportsTools,
            supportsImageGeneration = hint.supportsImageGeneration ?: model.supportsImageGeneration,
            reasoningMetadataAvailable = model.reasoningMetadataAvailable || hint.supportsThinking == true,
            reasoningDefaultEnabled = if (hint.supportsThinking == true) {
                hint.reasoningDefaultEnabled
            } else model.reasoningDefaultEnabled,
            reasoningMandatory = if (hint.supportsThinking == true) hint.reasoningMandatory else false,
            metadataSource = model.metadataSource.ifBlank { "Alibaba Cloud Model Studio" },
        )
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
            supportsThinking = hint.supportsThinking ?: model.supportsThinking,
            supportsVision = hint.supportsVision ?: model.supportsVision,
            supportsTools = hint.supportsTools ?: model.supportsTools,
            supportsImageGeneration = hint.supportsImageGeneration ?: model.supportsImageGeneration,
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
        val id = rawId.trim().lowercase()
        val leaf = id.substringAfterLast('/')
        return when {
            isQwenNativeImageModelId(leaf) -> AlibabaModelHint(
                supportsThinking = false,
                supportsVision = qwenImageAcceptsInputImages(leaf),
                supportsTools = false,
                supportsImageGeneration = true,
            )

            leaf.startsWith("qwen3.7-plus") -> AlibabaModelHint(
                contextWindow = 1_000_000,
                maxOutputTokens = 65_536,
                supportsThinking = true,
                supportsVision = true,
                supportsTools = true,
                reasoningDefaultEnabled = true,
            )
            leaf.startsWith("qwen3.7-max") -> AlibabaModelHint(
                contextWindow = 1_000_000,
                maxOutputTokens = 65_536,
                supportsThinking = true,
                supportsVision = leaf == "qwen3.7-max-2026-06-08",
                supportsTools = true,
                reasoningDefaultEnabled = true,
                reasoningMandatory = leaf == "qwen3.7-max-preview" || leaf == "qwen3.7-max-2026-05-17",
            )

            leaf.startsWith("qwen3.6-max-preview") -> AlibabaModelHint(
                supportsThinking = true,
                supportsVision = false,
                supportsTools = true,
                reasoningDefaultEnabled = true,
            )
            leaf.startsWith("qwen3.6-plus") || leaf.startsWith("qwen3.6-flash") -> AlibabaModelHint(
                contextWindow = 1_000_000,
                maxOutputTokens = 65_536,
                supportsThinking = true,
                supportsVision = true,
                supportsTools = true,
                reasoningDefaultEnabled = true,
            )
            leaf == "qwen3.6-35b-a3b" -> AlibabaModelHint(
                supportsThinking = true,
                supportsVision = true,
                supportsTools = true,
                reasoningDefaultEnabled = true,
            )

            leaf.startsWith("qwen3.5-plus") || leaf.startsWith("qwen3.5-flash") -> AlibabaModelHint(
                contextWindow = 1_000_000,
                maxOutputTokens = 65_536,
                supportsThinking = true,
                supportsVision = true,
                supportsTools = true,
                reasoningDefaultEnabled = true,
            )
            leaf in setOf("qwen3.5-397b-a17b", "qwen3.5-122b-a10b", "qwen3.5-27b", "qwen3.5-35b-a3b") -> AlibabaModelHint(
                supportsThinking = true,
                supportsVision = true,
                supportsTools = true,
                reasoningDefaultEnabled = true,
            )

            leaf.startsWith("qwen3-vl-plus") || leaf.startsWith("qwen3-vl-flash") -> AlibabaModelHint(
                supportsThinking = true,
                supportsVision = true,
                supportsTools = true,
                reasoningDefaultEnabled = false,
            )
            leaf.startsWith("qwen3-vl-") && leaf.contains("-thinking") -> AlibabaModelHint(
                supportsThinking = true,
                supportsVision = true,
                supportsTools = true,
                reasoningDefaultEnabled = true,
                reasoningMandatory = true,
            )
            leaf.startsWith("qwen3-vl-") && leaf.contains("-instruct") -> AlibabaModelHint(
                supportsThinking = false,
                supportsVision = true,
                supportsTools = true,
            )

            leaf.startsWith("qwen3-next-") && leaf.contains("-thinking") -> AlibabaModelHint(
                supportsThinking = true,
                supportsTools = true,
                reasoningDefaultEnabled = true,
                reasoningMandatory = true,
            )
            leaf.startsWith("qwen3-next-") && leaf.contains("-instruct") -> AlibabaModelHint(
                supportsThinking = false,
                supportsTools = true,
            )
            leaf.startsWith("qwen3-") && leaf.contains("-thinking") -> AlibabaModelHint(
                supportsThinking = true,
                supportsTools = true,
                reasoningDefaultEnabled = true,
                reasoningMandatory = true,
            )
            leaf.startsWith("qwen3-") && leaf.contains("-instruct") -> AlibabaModelHint(
                supportsThinking = false,
                supportsTools = true,
            )
            leaf in qwen3OpenSourceHybridModels -> AlibabaModelHint(
                supportsThinking = true,
                supportsTools = true,
                reasoningDefaultEnabled = true,
            )
            leaf == "qwen3-max" || leaf.startsWith("qwen3-max-") -> AlibabaModelHint(
                supportsThinking = true,
                supportsTools = true,
                reasoningDefaultEnabled = false,
            )
            isQwenPlusThinkingModel(leaf) || isQwenFlashThinkingModel(leaf) || leaf.startsWith("qwen-turbo") -> AlibabaModelHint(
                supportsThinking = true,
                supportsTools = true,
                reasoningDefaultEnabled = false,
            )

            leaf.startsWith("qvq-max") || leaf.startsWith("qvq-plus") -> AlibabaModelHint(
                supportsThinking = true,
                supportsVision = true,
                reasoningDefaultEnabled = true,
                reasoningMandatory = true,
            )
            leaf == "qwq-plus" || leaf.startsWith("qwq-plus-") -> AlibabaModelHint(
                supportsThinking = true,
                reasoningDefaultEnabled = true,
                reasoningMandatory = true,
            )

            leaf.startsWith("glm-5.2") || leaf.startsWith("glm-5.1") || leaf == "glm-5" ||
                leaf.startsWith("glm-4.7") || leaf.startsWith("glm-4.6") || leaf.startsWith("glm-4.5") -> AlibabaModelHint(
                supportsThinking = true,
                supportsVision = false,
                // GLM Function Calling needs Alibaba's tool_stream request option. Keep tools hidden until
                // that transport option is wired so Xylune never advertises a control that cannot work.
                supportsTools = false,
                reasoningDefaultEnabled = true,
            )

            leaf.startsWith("kimi-k2.7-code") -> AlibabaModelHint(
                supportsThinking = true,
                supportsVision = true,
                reasoningDefaultEnabled = true,
                reasoningMandatory = true,
            )
            leaf == "kimi-k2-thinking" || leaf.startsWith("kimi-k2-thinking-") -> AlibabaModelHint(
                supportsThinking = true,
                supportsTools = true,
                reasoningDefaultEnabled = true,
                reasoningMandatory = true,
            )
            leaf.startsWith("kimi-k2.6") || leaf.startsWith("kimi-k2.5") -> AlibabaModelHint(
                supportsThinking = true,
                supportsVision = true,
                supportsTools = true,
                reasoningDefaultEnabled = id.startsWith("kimi/"),
            )

            leaf == "minimax-m2.5" || leaf.startsWith("minimax-m2.5-") -> AlibabaModelHint(
                supportsThinking = true,
                supportsTools = true,
                reasoningDefaultEnabled = true,
                reasoningMandatory = true,
            )
            leaf == "minimax-m2.1" || leaf.startsWith("minimax-m2.1-") -> AlibabaModelHint(
                supportsThinking = true,
                reasoningDefaultEnabled = true,
                reasoningMandatory = true,
            )

            leaf.startsWith("deepseek-v4-") || leaf.startsWith("deepseek-v3.2") || leaf.startsWith("deepseek-v3.1") -> AlibabaModelHint(
                supportsThinking = true,
                supportsVision = false,
                supportsTools = true,
                reasoningDefaultEnabled = false,
            )
            leaf.startsWith("deepseek-r1") -> AlibabaModelHint(
                supportsThinking = true,
                supportsVision = false,
                supportsTools = true,
                reasoningDefaultEnabled = true,
                reasoningMandatory = true,
            )
            else -> null
        }
    }

    private fun isQwenPlusThinkingModel(id: String): Boolean = when {
        id == "qwen-plus" || id == "qwen-plus-latest" -> true
        id.startsWith("qwen-plus-") -> snapshotDateAtLeast(id.removePrefix("qwen-plus-"), "2025-04-28")
        else -> false
    }

    private fun isQwenFlashThinkingModel(id: String): Boolean = when {
        id == "qwen-flash" || id == "qwen-flash-latest" -> true
        id.startsWith("qwen-flash-") -> snapshotDateAtLeast(id.removePrefix("qwen-flash-"), "2025-07-28")
        else -> false
    }

    private fun snapshotDateAtLeast(raw: String, threshold: String): Boolean {
        val date = Regex("\\d{4}-\\d{2}-\\d{2}").find(raw)?.value ?: return false
        return date >= threshold
    }

    private fun isQwenNativeImageModelId(rawId: String): Boolean =
        rawId.substringAfterLast('/').lowercase().startsWith("qwen-image")

    private fun qwenImageAcceptsInputImages(rawId: String): Boolean {
        val id = rawId.substringAfterLast('/').lowercase()
        return id.startsWith("qwen-image-2.0") ||
            id.startsWith("qwen-image-3.0") ||
            id.startsWith("qwen-image-edit")
    }

    private fun qwenImageRequiresInputImage(rawId: String): Boolean =
        rawId.substringAfterLast('/').lowercase().startsWith("qwen-image-edit")
}
