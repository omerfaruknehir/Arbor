package app.xylune.chat.ui

import app.xylune.chat.data.MessageRole
import app.xylune.chat.generation.GenerationRequestSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Explicit state reported by the model while a Deep Research request is running.
 *
 * Xylune deliberately does not infer roadmap stages from tool counts. A roadmap is
 * shown only when the model reports this protocol from the immutable request that
 * enabled Deep Research for that response.
 */
@Serializable
data class ReportedResearchStep(
    val id: String = "",
    val title: String = "",
    val state: String = "pending",
    val detail: String = "",
)

@Serializable
data class ReportedResearchState(
    val status: String = "",
    val reportState: String = "working",
    val progress: Float = 0f,
    val steps: List<ReportedResearchStep> = emptyList(),
)

internal data class ResearchStateExtraction(
    val cleanedText: String,
    val states: List<ReportedResearchState>,
)

internal object ResearchStateProtocol {
    const val OPEN_TAG = "<xylune-research-state>"
    const val CLOSE_TAG = "</xylune-research-state>"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    private val completeBlock = Regex(
        """<xylune-research-state>\s*([\s\S]*?)\s*</xylune-research-state>""",
        setOf(RegexOption.IGNORE_CASE),
    )


    fun isDeepResearchResponse(role: MessageRole, requestSnapshotJson: String?): Boolean =
        role == MessageRole.ASSISTANT && requestSnapshotJson
            ?.let { runCatching { json.decodeFromString<GenerationRequestSnapshot>(it) }.getOrNull() }
            ?.deepResearchEnabled == true

    fun extract(text: String): ResearchStateExtraction {
        val states = completeBlock.findAll(text).mapNotNull { match ->
            runCatching { json.decodeFromString<ReportedResearchState>(match.groupValues[1]) }
                .getOrNull()
                ?.normalized()
        }.toList()

        var cleaned = completeBlock.replace(text, "")
        // During streaming, hide a partially emitted state object rather than
        // flashing machine-readable JSON in the answer.
        val danglingOpen = cleaned.lastIndexOf(OPEN_TAG, ignoreCase = true)
        val danglingClose = cleaned.lastIndexOf(CLOSE_TAG, ignoreCase = true)
        if (danglingOpen > danglingClose) cleaned = cleaned.substring(0, danglingOpen)
        return ResearchStateExtraction(cleaned.trimEnd(), states)
    }

    fun latest(textsInOrder: Iterable<String>): ReportedResearchState? {
        var latest: ReportedResearchState? = null
        textsInOrder.forEach { text ->
            extract(text).states.lastOrNull()?.let { latest = it }
        }
        return latest
    }

    private fun ReportedResearchState.normalized(): ReportedResearchState {
        val normalizedProgress = when {
            progress.isNaN() || progress.isInfinite() -> 0f
            progress > 1f -> (progress / 100f).coerceIn(0f, 1f)
            else -> progress.coerceIn(0f, 1f)
        }
        val normalizedReportState = reportState.lowercase().let {
            if (it in setOf("planning", "working", "researching", "synthesizing", "complete", "blocked")) it else "working"
        }
        val normalizedSteps = steps.take(12).mapIndexedNotNull { index, step ->
            val title = step.title.trim().take(80)
            if (title.isBlank()) return@mapIndexedNotNull null
            val normalizedState = step.state.lowercase().let {
                if (it in setOf("pending", "active", "complete", "blocked")) it else "pending"
            }
            step.copy(
                id = step.id.trim().take(48).ifBlank { "step-${index + 1}" },
                title = title,
                state = normalizedState,
                detail = step.detail.trim().take(240),
            )
        }
        return copy(
            status = status.trim().take(180),
            reportState = normalizedReportState,
            progress = normalizedProgress,
            steps = normalizedSteps,
        )
    }
}
