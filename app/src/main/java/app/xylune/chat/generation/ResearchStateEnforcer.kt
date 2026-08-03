package app.xylune.chat.generation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Validates model-reported Deep Research state without inventing app-side progress.
 * The app may request a repair, but only a state block actually emitted by the model
 * is persisted and rendered.
 */
internal object ResearchStateEnforcer {
    const val OPEN_TAG = "<xylune-research-state>"
    const val CLOSE_TAG = "</xylune-research-state>"

    private val completeBlock = Regex(
        """<xylune-research-state>\s*([\s\S]*?)\s*</xylune-research-state>""",
        setOf(RegexOption.IGNORE_CASE),
    )
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun firstValidBlock(text: String): String? = completeBlock.findAll(text).firstNotNullOfOrNull { match ->
        val state = runCatching { json.parseToJsonElement(match.groupValues[1]).jsonObject }.getOrNull()
            ?: return@firstNotNullOfOrNull null
        if (!isValid(state)) return@firstNotNullOfOrNull null
        OPEN_TAG + "\n" + match.groupValues[1].trim() + "\n" + CLOSE_TAG
    }

    fun hasValidBlock(text: String): Boolean = firstValidBlock(text) != null

    fun hasTerminalBlock(text: String): Boolean = completeBlock.findAll(text).any { match ->
        val state = runCatching { json.parseToJsonElement(match.groupValues[1]).jsonObject }.getOrNull()
            ?: return@any false
        if (!isValid(state)) return@any false
        state["reportState"]?.jsonPrimitive?.content?.lowercase() in setOf("complete", "blocked")
    }

    private fun isValid(state: JsonObject): Boolean {
        val status = state["status"]?.jsonPrimitive?.content?.trim().orEmpty()
        val reportState = state["reportState"]?.jsonPrimitive?.content?.lowercase().orEmpty()
        val progress = state["progress"]?.jsonPrimitive?.content?.toFloatOrNull()
        val steps = state["steps"] as? JsonArray
        if (status.isBlank() || reportState !in setOf("planning", "working", "researching", "synthesizing", "complete", "blocked")) return false
        if (progress == null || !progress.isFinite() || progress !in 0f..100f) return false
        if (steps == null || steps.isEmpty()) return false
        return steps.all { element ->
            val step = element as? JsonObject ?: return@all false
            val id = step["id"]?.jsonPrimitive?.content?.trim().orEmpty()
            val title = step["title"]?.jsonPrimitive?.content?.trim().orEmpty()
            val stepState = step["state"]?.jsonPrimitive?.content?.lowercase().orEmpty()
            id.isNotBlank() && title.isNotBlank() && stepState in setOf("pending", "active", "complete", "blocked")
        }
    }
}
