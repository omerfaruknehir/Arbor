package app.arbor.chat.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

internal data class DsmlToolProtocolResult(
    val visibleText: String,
    val calls: List<NativeToolCall>,
    val malformed: Boolean = false,
)

/**
 * Adapter for DeepSeek's alternate DSML function-call serialization.
 *
 * This is deliberately not a generic text-tool fallback: it activates only while
 * native tools were supplied, accepts only those exact tool names, and sends the
 * decoded arguments through Arbor's normal native-tool validation and execution.
 */
internal object DsmlToolProtocol {
    private val json = Json { ignoreUnknownKeys = false }
    private val startMarker = Regex("(?is)<\\s*[|｜]\\s*DSML\\s*[|｜]\\s*tool_calls\\s*>")
    private val endMarker = Regex("(?is)<\\s*/\\s*[|｜]\\s*DSML\\s*[|｜]\\s*tool_calls\\s*>")
    private val invokeMarker = Regex(
        "(?is)<\\s*[|｜]\\s*DSML\\s*[|｜]\\s*invoke\\b([^>]*)>(.*?)" +
            "<\\s*/\\s*[|｜]\\s*DSML\\s*[|｜]\\s*invoke\\s*>",
    )
    private val parameterMarker = Regex(
        "(?is)<\\s*[|｜]\\s*DSML\\s*[|｜]\\s*parameter\\b([^>]*)>(.*?)" +
            "<\\s*/\\s*[|｜]\\s*DSML\\s*[|｜]\\s*parameter\\s*>",
    )

    internal fun findStart(value: CharSequence): MatchResult? = startMarker.find(value)
    internal fun findEnd(value: CharSequence): MatchResult? = endMarker.find(value)

    fun parseBlock(block: String, allowedTools: Set<String>): DsmlToolProtocolResult {
        val start = startMarker.find(block) ?: return malformed()
        val end = endMarker.find(block, start.range.last + 1) ?: return malformed()
        val body = block.substring(start.range.last + 1, end.range.first)
        val invocations = invokeMarker.findAll(body).toList()
        if (invocations.isEmpty() || invokeMarker.replace(body, "").isNotBlank()) return malformed()

        val allowed = allowedTools.mapTo(hashSetOf()) { it.lowercase() }
        val calls = mutableListOf<NativeToolCall>()
        invocations.forEachIndexed { index, invocation ->
            val name = attribute(invocation.groupValues[1], "name")?.lowercase()?.trim().orEmpty()
            if (name.isBlank() || name !in allowed) return malformed()
            val parameterBody = invocation.groupValues[2]
            val parameters = parameterMarker.findAll(parameterBody).toList()
            if (parameterMarker.replace(parameterBody, "").isNotBlank()) return malformed()
            val arguments = linkedMapOf<String, JsonElement>()
            parameters.forEach { parameter ->
                val attributes = parameter.groupValues[1]
                val key = attribute(attributes, "name")?.trim().orEmpty()
                if (key.isBlank() || key in arguments) return malformed()
                val decoded = decodeEntities(parameter.groupValues[2].trim())
                val isString = attribute(attributes, "string")?.equals("true", ignoreCase = true) == true ||
                    attribute(attributes, "type")?.equals("string", ignoreCase = true) == true
                arguments[key] = if (isString) {
                    JsonPrimitive(decoded)
                } else {
                    runCatching { json.parseToJsonElement(decoded) }.getOrElse { JsonPrimitive(decoded) }
                }
            }
            val argumentsJson = JsonObject(arguments).toString()
            calls += NativeToolCall(
                id = "dsml-${index + 1}-${sha256(invocation.value).take(12)}",
                name = name,
                argumentsJson = argumentsJson,
            )
        }
        return DsmlToolProtocolResult(visibleText = "", calls = calls)
    }

    private fun attribute(attributes: String, name: String): String? {
        val expression = Regex("(?is)\\b${Regex.escape(name)}\\s*=\\s*([\\\"'])(.*?)\\1")
        return expression.find(attributes)?.groupValues?.get(2)
    }

    private fun decodeEntities(value: String): String = value
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

    private fun malformed() = DsmlToolProtocolResult(
        visibleText = MALFORMED_NOTICE,
        calls = emptyList(),
        malformed = true,
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    internal const val MALFORMED_NOTICE =
        "\n\n*The provider returned a malformed tool request. Arbor ignored the protocol text instead of displaying or executing it.*\n\n"
}

/** Incrementally removes DSML from streamed assistant text and emits native calls at EOF. */
internal class DsmlToolStreamAdapter(private val allowedTools: Set<String>) {
    private val pending = StringBuilder()
    private val protocol = StringBuilder()
    private val calls = mutableListOf<NativeToolCall>()
    private var insideProtocol = false

    fun accept(delta: String): String {
        if (delta.isEmpty()) return ""
        pending.append(delta)
        val visible = StringBuilder()
        while (true) {
            if (!insideProtocol) {
                val start = DsmlToolProtocol.findStart(pending)
                if (start != null) {
                    visible.append(pending.substring(0, start.range.first))
                    protocol.append(start.value)
                    pending.delete(0, start.range.last + 1)
                    insideProtocol = true
                    continue
                }
                val flushCount = (pending.length - MARKER_LOOKBEHIND).coerceAtLeast(0)
                if (flushCount > 0) {
                    visible.append(pending.substring(0, flushCount))
                    pending.delete(0, flushCount)
                }
                break
            }

            val end = DsmlToolProtocol.findEnd(pending)
            if (end != null) {
                protocol.append(pending.substring(0, end.range.last + 1))
                pending.delete(0, end.range.last + 1)
                val parsed = DsmlToolProtocol.parseBlock(protocol.toString(), allowedTools)
                if (parsed.malformed || parsed.calls.isEmpty()) visible.append(DsmlToolProtocol.MALFORMED_NOTICE)
                else calls += parsed.calls
                protocol.clear()
                insideProtocol = false
                continue
            }
            val flushCount = (pending.length - MARKER_LOOKBEHIND).coerceAtLeast(0)
            if (flushCount > 0) {
                protocol.append(pending.substring(0, flushCount))
                pending.delete(0, flushCount)
            }
            break
        }
        return visible.toString()
    }

    fun finish(): DsmlToolProtocolResult {
        val visible = StringBuilder()
        if (insideProtocol) {
            protocol.append(pending)
            visible.append(DsmlToolProtocol.MALFORMED_NOTICE)
        } else {
            visible.append(pending)
        }
        pending.clear()
        protocol.clear()
        insideProtocol = false
        return DsmlToolProtocolResult(visible.toString(), calls.toList())
    }

    private companion object {
        const val MARKER_LOOKBEHIND = 48
    }
}
