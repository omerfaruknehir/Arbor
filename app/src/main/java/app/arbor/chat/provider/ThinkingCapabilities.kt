package app.arbor.chat.provider

import app.arbor.chat.data.ModelEntity
import app.arbor.chat.data.ProviderEntity
import app.arbor.chat.data.ProviderKind
import app.arbor.chat.data.ThinkingEffort

data class ThinkingLevelOption(
    val enabled: Boolean,
    val effort: ThinkingEffort?,
    val label: String,
    val description: String,
)

fun supportedThinkingLevels(provider: ProviderEntity?, model: ModelEntity?): List<ThinkingLevelOption> {
    if (model?.supportsThinking != true) return emptyList()
    val id = model.modelId.lowercase()
    return when (provider?.kind) {
        ProviderKind.ANTHROPIC -> anthropicLevels(id)
        ProviderKind.GEMINI -> geminiLevels(id)
        ProviderKind.OPENAI_COMPATIBLE, null -> openAiCompatibleLevels(provider?.id.orEmpty(), id)
    }
}

private val off = ThinkingLevelOption(false, null, "Off", "No deliberate reasoning where the model API allows it")
private val minimal = ThinkingLevelOption(true, ThinkingEffort.MINIMAL, "Minimal", "Fastest available reasoning")
private val low = ThinkingLevelOption(true, ThinkingEffort.LOW, "Low", "Short reasoning with lower latency")
private val medium = ThinkingLevelOption(true, ThinkingEffort.MEDIUM, "Medium", "Balanced reasoning")
private val high = ThinkingLevelOption(true, ThinkingEffort.HIGH, "High", "Thorough reasoning")
private val xhigh = ThinkingLevelOption(true, ThinkingEffort.XHIGH, "Extra high", "Extended reasoning for difficult agentic work")
private val max = ThinkingLevelOption(true, ThinkingEffort.MAX, "Max", "Maximum supported reasoning effort")

private fun openAiCompatibleLevels(providerId: String, modelId: String): List<ThinkingLevelOption> {
    if (modelId.contains("gpt-5-pro")) return listOf(high)
    if (providerId == "openai" && modelId.contains("gpt-5.1") && !modelId.contains("codex-max")) {
        return listOf(off, low, medium, high)
    }
    val result = mutableListOf(off, minimal, low, medium, high)
    val supportsXHigh = modelId.contains("codex-max") ||
        Regex("gpt-5\\.[2-9]").containsMatchIn(modelId) ||
        modelId.contains("gpt-6")
    if (supportsXHigh) result += xhigh
    return result
}

private fun anthropicLevels(modelId: String): List<ThinkingLevelOption> {
    val alwaysOn = modelId.contains("fable-5") || modelId.contains("mythos-5") || modelId.contains("mythos-preview")
    val supportsEffort = listOf(
        "fable-5", "mythos-5", "mythos-preview", "opus-4-8", "opus-4-7", "opus-4-6",
        "sonnet-5", "sonnet-4-6", "opus-4-5",
    ).any(modelId::contains)
    if (!supportsEffort) return buildList {
        if (!alwaysOn) add(off)
        addAll(listOf(minimal, low, medium, high))
    }
    val supportsXHigh = listOf("fable-5", "mythos-5", "opus-4-8", "opus-4-7", "sonnet-5").any(modelId::contains)
    val supportsMax = listOf(
        "fable-5", "mythos-5", "mythos-preview", "opus-4-8", "opus-4-7", "opus-4-6",
        "sonnet-5", "sonnet-4-6",
    ).any(modelId::contains)
    return buildList {
        if (!alwaysOn) add(off)
        addAll(listOf(low, medium, high))
        if (supportsXHigh) add(xhigh)
        if (supportsMax) add(max)
    }
}

private fun geminiLevels(modelId: String): List<ThinkingLevelOption> = when {
    modelId.contains("3.1-pro") -> listOf(low, medium, high)
    modelId.contains("3.1-flash-lite-image") -> listOf(minimal, high)
    modelId.contains("3-pro") -> listOf(low, high)
    modelId.contains("3.5-flash") || modelId.contains("3-flash") || modelId.contains("3.1-flash-lite") ->
        listOf(minimal, low, medium, high)
    modelId.contains("2.5-pro") -> listOf(minimal, low, medium, high)
    modelId.contains("2.5-flash") || modelId.contains("robotics-er") -> listOf(off, minimal, low, medium, high)
    else -> listOf(off, minimal, low, medium, high)
}
