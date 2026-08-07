package app.xylune.chat.provider

import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.ProviderEntity
import app.xylune.chat.data.ProviderKind
import app.xylune.chat.data.ThinkingEffort

data class ThinkingLevelOption(
    val enabled: Boolean,
    val effort: ThinkingEffort?,
    val label: String,
    val description: String,
)

fun supportedThinkingLevels(provider: ProviderEntity?, model: ModelEntity?): List<ThinkingLevelOption> {
    if (model?.supportsThinking != true) return emptyList()
    if (model.reasoningMetadataAvailable) return metadataLevels(provider, model)
    val id = model.modelId.lowercase()
    return when (provider?.kind) {
        ProviderKind.ANTHROPIC -> anthropicLevels(id)
        ProviderKind.GEMINI -> geminiLevels(id)
        ProviderKind.OPENAI_COMPATIBLE, ProviderKind.OPENAI_OAUTH, null -> openAiCompatibleLevels(provider?.id.orEmpty(), id)
    }
}

private fun metadataLevels(provider: ProviderEntity?, model: ModelEntity): List<ThinkingLevelOption> {
    val declared = model.reasoningEffortsCsv.split(',')
        .mapNotNull { value -> runCatching { ThinkingEffort.valueOf(value.trim().uppercase()) }.getOrNull() }
        .distinct()
    if (declared.isEmpty()) {
        // OpenRouter documents null supported_efforts as accepting its complete normalized
        // gateway scale. Other providers use an empty effort list to mean that thinking is
        // controllable only as an on/off mode and must not inherit fake OpenAI effort levels.
        val openRouterMetadata = provider?.id.equals("openrouter", ignoreCase = true) ||
            model.metadataSource.contains("openrouter", ignoreCase = true)
        if (!openRouterMetadata) {
            return buildList {
                if (!model.reasoningMandatory) add(off)
                add(on)
            }
        }
    }
    val efforts = declared.ifEmpty { ThinkingEffort.entries }.sortedBy(ThinkingEffort.entries::indexOf)
    return buildList {
        if (!model.reasoningMandatory) add(off)
        efforts.mapNotNullTo(this) { effortOptions[it] }
    }
}

fun defaultThinkingEffort(model: ModelEntity?, fallback: ThinkingEffort = ThinkingEffort.MEDIUM): ThinkingEffort {
    if (model?.supportsThinking != true) return fallback
    val supported = supportedThinkingLevels(null, model).mapNotNull(ThinkingLevelOption::effort)
    val declared = runCatching { ThinkingEffort.valueOf(model.reasoningDefaultEffort.uppercase()) }.getOrNull()
    return when {
        // A request-selected effort is authoritative when the model supports it. Metadata's
        // default is only a fallback for callers which did not choose a supported level.
        fallback in supported -> fallback
        declared in supported -> requireNotNull(declared)
        else -> supported.getOrNull(supported.size / 2) ?: fallback
    }
}

fun effectiveThinkingEnabled(model: ModelEntity?, requested: Boolean): Boolean = when {
    model?.supportsThinking != true -> false
    model.reasoningMandatory -> true
    else -> requested
}

private val off = ThinkingLevelOption(false, null, "Off", "No deliberate reasoning where the model API allows it")
private val on = ThinkingLevelOption(true, null, "On", "Use the model's provider-managed thinking mode")
private val minimal = ThinkingLevelOption(true, ThinkingEffort.MINIMAL, "Minimal", "Fastest available reasoning")
private val low = ThinkingLevelOption(true, ThinkingEffort.LOW, "Low", "Short reasoning with lower latency")
private val medium = ThinkingLevelOption(true, ThinkingEffort.MEDIUM, "Medium", "Balanced reasoning")
private val high = ThinkingLevelOption(true, ThinkingEffort.HIGH, "High", "Thorough reasoning")
private val xhigh = ThinkingLevelOption(true, ThinkingEffort.XHIGH, "Extra high", "Extended reasoning for difficult agentic work")
private val max = ThinkingLevelOption(true, ThinkingEffort.MAX, "Max", "Maximum supported reasoning effort")
private val effortOptions = linkedMapOf(
    ThinkingEffort.MINIMAL to minimal,
    ThinkingEffort.LOW to low,
    ThinkingEffort.MEDIUM to medium,
    ThinkingEffort.HIGH to high,
    ThinkingEffort.XHIGH to xhigh,
    ThinkingEffort.MAX to max,
)

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
