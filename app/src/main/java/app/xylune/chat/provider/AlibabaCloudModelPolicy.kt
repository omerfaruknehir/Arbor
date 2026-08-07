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
        val id = leaf(model.id)
        return when {
            id.startsWith("glm-5.2") -> model.copy(
                contextWindow = 1_048_576,
                maxOutputTokens = 131_072,
                supportsThinking = true,
                supportsVision = false,
                supportsTools = true,
                reasoningMetadataAvailable = true,
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
                reasoningMandatory = false,
                metadataSource = "Alibaba Cloud Model Studio",
            )
            else -> model
        }
    }

    fun correct(model: ModelEntity): ModelEntity {
        val id = leaf(model.modelId)
        return when {
            id.startsWith("glm-5.2") -> model.copy(
                contextWindow = 1_048_576,
                maxOutputTokens = 131_072,
                supportsThinking = true,
                supportsVision = false,
                supportsTools = true,
                reasoningMetadataAvailable = true,
                reasoningEffortsCsv = "MINIMAL,LOW,MEDIUM,HIGH,XHIGH,MAX",
                reasoningDefaultEffort = "HIGH",
                reasoningDefaultEnabled = true,
                reasoningMandatory = false,
                metadataSource = "Alibaba Cloud Model Studio",
            )
            else -> model
        }
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

    private fun modelOrSnapshotAtLeast(id: String, family: String, minimumDate: String): Boolean =
        id == family || snapshotAtLeast(id, family, minimumDate)

    private fun snapshotAtLeast(id: String, family: String, minimumDate: String): Boolean {
        if (!id.startsWith("$family-")) return false
        val suffix = id.removePrefix("$family-")
        val date = Regex("^\\d{4}-\\d{2}-\\d{2}$").matchEntire(suffix)?.value ?: return false
        return date >= minimumDate
    }

    private fun leaf(raw: String): String = raw.substringAfterLast('/').trim().lowercase()
}
