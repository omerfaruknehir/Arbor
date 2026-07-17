package app.arbor.chat.generation

import app.arbor.chat.data.ConversationEntity
import app.arbor.chat.data.ModelEntity
import app.arbor.chat.data.ProviderEntity
import app.arbor.chat.data.ProviderKind
import app.arbor.chat.data.ThinkingEffort
import kotlinx.serialization.Serializable

/** Immutable request identity captured before work is queued. Secrets are deliberately excluded. */
@Serializable
data class GenerationRequestSnapshot(
    val providerId: String,
    val providerName: String,
    val providerKind: ProviderKind,
    val baseUrl: String,
    val customHeadersJson: String,
    val apiKeyRequired: Boolean,
    val modelId: String,
    val modelName: String,
    val contextWindow: Int,
    val modelMaxOutputTokens: Int,
    val inputCacheHitUsdPerMillion: Double,
    val inputCacheMissUsdPerMillion: Double,
    val outputUsdPerMillion: Double,
    val pricingConfigured: Boolean = false,
    val supportsVision: Boolean,
    val supportsFiles: Boolean,
    val supportsThinking: Boolean,
    val supportsTools: Boolean,
    val contextPairs: Int,
    val contextTokenLimit: Int,
    val workingTokenLimit: Int = 16_000,
    val requestedMaxOutputTokens: Int,
    val systemPrompt: String,
    val thinkingEnabled: Boolean = true,
    val thinkingEffort: ThinkingEffort = ThinkingEffort.MEDIUM,
) {
    fun provider(): ProviderEntity = ProviderEntity(
        id = providerId,
        displayName = providerName,
        kind = providerKind,
        baseUrl = baseUrl,
        customHeadersJson = customHeadersJson,
        registered = true,
        apiKeyRequired = apiKeyRequired,
    )

    fun model(): ModelEntity = ModelEntity(
        providerId = providerId,
        modelId = modelId,
        displayName = modelName,
        contextWindow = contextWindow,
        maxOutputTokens = modelMaxOutputTokens,
        inputCacheHitUsdPerMillion = inputCacheHitUsdPerMillion,
        inputCacheMissUsdPerMillion = inputCacheMissUsdPerMillion,
        outputUsdPerMillion = outputUsdPerMillion,
        pricingConfigured = pricingConfigured,
        supportsVision = supportsVision,
        supportsFiles = supportsFiles,
        supportsThinking = supportsThinking,
        supportsTools = supportsTools,
    )

    fun applyTo(conversation: ConversationEntity): ConversationEntity {
        val output = requestedMaxOutputTokens.coerceAtMost(modelMaxOutputTokens).coerceAtLeast(1)
        val reserved = output.toLong() + SYSTEM_AND_TOOL_RESERVE_TOKENS
        val safeInput = (contextWindow.toLong() - reserved).coerceAtLeast(MIN_INPUT_TOKENS.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return conversation.copy(
            selectedProviderId = providerId,
            selectedModelId = modelId,
            contextPairs = contextPairs.coerceAtLeast(1),
            contextTokenLimit = contextTokenLimit.coerceIn(MIN_INPUT_TOKENS, safeInput),
            workingTokenLimit = workingTokenLimit.coerceIn(0, safeInput),
            maxOutputTokens = output,
            systemPrompt = systemPrompt,
            thinkingEnabled = thinkingEnabled,
            thinkingEffort = thinkingEffort,
        )
    }

    companion object {
        private const val SYSTEM_AND_TOOL_RESERVE_TOKENS = 12_000L
        private const val MIN_INPUT_TOKENS = 1_024

        fun capture(conversation: ConversationEntity, provider: ProviderEntity, model: ModelEntity) = GenerationRequestSnapshot(
            providerId = provider.id,
            providerName = provider.displayName,
            providerKind = provider.kind,
            baseUrl = provider.baseUrl,
            customHeadersJson = provider.customHeadersJson,
            apiKeyRequired = provider.apiKeyRequired,
            modelId = model.modelId,
            modelName = model.displayName,
            contextWindow = model.contextWindow,
            modelMaxOutputTokens = model.maxOutputTokens,
            inputCacheHitUsdPerMillion = model.inputCacheHitUsdPerMillion,
            inputCacheMissUsdPerMillion = model.inputCacheMissUsdPerMillion,
            outputUsdPerMillion = model.outputUsdPerMillion,
            pricingConfigured = model.pricingConfigured,
            supportsVision = model.supportsVision,
            supportsFiles = model.supportsFiles,
            supportsThinking = model.supportsThinking,
            supportsTools = model.supportsTools,
            contextPairs = conversation.contextPairs,
            contextTokenLimit = conversation.contextTokenLimit,
            workingTokenLimit = conversation.workingTokenLimit,
            requestedMaxOutputTokens = conversation.maxOutputTokens,
            systemPrompt = conversation.systemPrompt,
            thinkingEnabled = conversation.thinkingEnabled,
            thinkingEffort = conversation.thinkingEffort,
        )
    }
}
