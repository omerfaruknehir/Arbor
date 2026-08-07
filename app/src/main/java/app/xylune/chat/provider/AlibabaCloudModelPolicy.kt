package app.xylune.chat.provider

import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.ThinkingEffort

/**
 * Small, current-doc overlay for Model Studio details which are either absent from
 * /models or changed more recently than Xylune's broader family catalog.
 *
 * Keep this deliberately narrow: family-wide stable capability inference lives in
 * [ModelRequestPolicy], while model-specific corrections and routing exceptions live here.
 */
internal object AlibabaCloudModelPolicy {
    fun correct(model: DiscoveredModel): DiscoveredModel {
        val correction = correctionFor(model.id) ?: return model
        return model.copy(
            contextWindow = correction.contextWindow,
            maxOutputTokens = correction.maxOutputTokens,
            supportsThinking = correction.supportsThinking,
            supportsVision = correction.supportsVision,
            supportsTools = correction.supportsTools,
            reasoningMetadataAvailable = correction.supportsThinking,
            reasoningEfforts = correction.reasoningEfforts,
            reasoningDefaultEffort = correction.reasoningDefaultEffort,
            reasoningDefaultEnabled = correction.reasoningDefaultEnabled,
            reasoningMandatory = correction.reasoningMandatory,
            metadataSource = "Alibaba Cloud Model Studio",
        )
    }

    fun correct(model: ModelEntity): ModelEntity {
        val correction = correctionFor(model.modelId) ?: return model
        return model.copy(
            contextWindow = correction.contextWindow,
            maxOutputTokens = correction.maxOutputTokens,
            supportsThinking = correction.supportsThinking,
            supportsVision = correction.supportsVision,
            supportsTools = correction.supportsTools,
            reasoningMetadataAvailable = correction.supportsThinking,
            reasoningEffortsCsv = correction.reasoningEfforts.joinToString(",") { it.name },
            reasoningDefaultEffort = correction.reasoningDefaultEffort?.name.orEmpty(),
            reasoningDefaultEnabled = correction.reasoningDefaultEnabled,
            reasoningMandatory = correction.reasoningMandatory,
            metadataSource = "Alibaba Cloud Model Studio",
        )
    }

    /**
     * Exact Responses web-search allow-list from Model Studio's current web-search
     * documentation. Avoid prefix matching: regional IDs such as qwen3.7-plus-us
     * can share the model family while explicitly not exposing web search.
     */
    fun supportsResponsesWebSearch(modelId: String, thinkingEnabled: Boolean): Boolean {
        val id = leaf(modelId)
        return when {
            id == "qwen3.7-max" || id == "qwen3.7-max-preview" -> true
            snapshotAtLeast(id, "qwen3.7-max", "2026-05-17") -> true
            modelOrSnapshotAtLeast(id, "qwen3.7-plus", "2026-05-26") -> true
            modelOrSnapshotAtLeast(id, "qwen3.6-plus", "2026-04-02") -> true
            modelOrSnapshotAtLeast(id, "qwen3.6-flash", "2026-04-16") -> true
            modelOrSnapshotAtLeast(id, "qwen3.5-plus", "2026-02-15") -> true
            modelOrSnapshotAtLeast(id, "qwen3.5-flash", "2026-02-23") -> true
            id == "qwen3-max" || id == "qwen3-max-2026-01-23" -> thinkingEnabled
            else -> false
        }
    }

    private fun correctionFor(rawId: String): ModelCorrection? {
        val id = leaf(rawId)
        return when {
            id.startsWith("glm-5.2") -> ModelCorrection(
                contextWindow = 1_048_576,
                maxOutputTokens = 131_072,
                supportsThinking = true,
                supportsVision = false,
                supportsTools = true,
                reasoningEfforts = listOf(
                    ThinkingEffort.MINIMAL,
                    ThinkingEffort.LOW,
                    ThinkingEffort.MEDIUM,
                    ThinkingEffort.HIGH,
                    ThinkingEffort.XHIGH,
                    ThinkingEffort.MAX,
                ),
                reasoningDefaultEffort = ThinkingEffort.HIGH,
                reasoningDefaultEnabled = true,
            )

            isQwenPlusUs(id) -> ModelCorrection(
                contextWindow = 1_000_000,
                maxOutputTokens = 32_768,
                supportsThinking = true,
                supportsVision = false,
                supportsTools = false,
                reasoningDefaultEnabled = false,
            )
            isGlobalQwenPlus(id) -> ModelCorrection(
                contextWindow = 1_000_000,
                maxOutputTokens = 32_768,
                supportsThinking = true,
                supportsVision = false,
                supportsTools = true,
                reasoningDefaultEnabled = false,
            )

            isQwenFlashUs(id) -> ModelCorrection(
                contextWindow = 1_000_000,
                maxOutputTokens = 32_768,
                supportsThinking = true,
                supportsVision = false,
                supportsTools = false,
                reasoningDefaultEnabled = false,
            )
            isGlobalQwenFlash(id) -> ModelCorrection(
                contextWindow = 1_000_000,
                maxOutputTokens = 32_768,
                supportsThinking = true,
                supportsVision = false,
                supportsTools = true,
                reasoningDefaultEnabled = false,
            )
            else -> null
        }
    }

    private fun isGlobalQwenPlus(id: String): Boolean = when {
        id == "qwen-plus" || id == "qwen-plus-latest" -> true
        id.startsWith("qwen-plus-") && !id.endsWith("-us") ->
            snapshotDate(id.removePrefix("qwen-plus-"))?.let { it >= "2025-04-28" } == true
        else -> false
    }

    private fun isQwenPlusUs(id: String): Boolean =
        id == "qwen-plus-us" ||
            (id.startsWith("qwen-plus-") && id.endsWith("-us") &&
                snapshotDate(id.removePrefix("qwen-plus-").removeSuffix("-us"))?.let { it >= "2025-12-01" } == true)

    private fun isGlobalQwenFlash(id: String): Boolean = when {
        id == "qwen-flash" -> true
        id.startsWith("qwen-flash-") && !id.endsWith("-us") ->
            snapshotDate(id.removePrefix("qwen-flash-"))?.let { it >= "2025-07-28" } == true
        else -> false
    }

    private fun isQwenFlashUs(id: String): Boolean =
        id == "qwen-flash-us" ||
            (id.startsWith("qwen-flash-") && id.endsWith("-us") &&
                snapshotDate(id.removePrefix("qwen-flash-").removeSuffix("-us"))?.let { it >= "2025-07-28" } == true)

    private fun modelOrSnapshotAtLeast(id: String, family: String, minimumDate: String): Boolean =
        id == family || snapshotAtLeast(id, family, minimumDate)

    private fun snapshotAtLeast(id: String, family: String, minimumDate: String): Boolean {
        if (!id.startsWith("$family-")) return false
        val suffix = id.removePrefix("$family-")
        val date = snapshotDate(suffix) ?: return false
        return date >= minimumDate
    }

    private fun snapshotDate(raw: String): String? =
        Regex("^\\d{4}-\\d{2}-\\d{2}$").matchEntire(raw)?.value

    private fun leaf(raw: String): String = raw.substringAfterLast('/').trim().lowercase()

    private data class ModelCorrection(
        val contextWindow: Int,
        val maxOutputTokens: Int,
        val supportsThinking: Boolean,
        val supportsVision: Boolean,
        val supportsTools: Boolean,
        val reasoningEfforts: List<ThinkingEffort> = emptyList(),
        val reasoningDefaultEffort: ThinkingEffort? = null,
        val reasoningDefaultEnabled: Boolean = false,
        val reasoningMandatory: Boolean = false,
    )
}
