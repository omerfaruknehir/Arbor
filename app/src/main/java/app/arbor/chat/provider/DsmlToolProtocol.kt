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

    // DeepSeek-compatible endpoints currently emit both <|DSML|...> and
    // <||DSML||...>. Some gateways also insert whitespace around each pipe.
    // Treat a run of ASCII or full-width pipes as the protocol fence while still
    // requiring the DSML namespace and an exact supported element name.
    private const val PIPE_RUN = "(?:[|｜]\\s*)+"
    private val startMarker = Regex("(?is)<\\s*$PIPE_RUN\\s*DSML\\s*$PIPE_RUN\\s*tool_calls\\s*>")
    private val endMarker = Regex("(?is)<\\s*/\\s*$PIPE_RUN\\s*DSML\\s*$PIPE_RUN\\s*tool_calls\\s*>")
    private val invokeMarker = Regex(
        "(?is)<\\s*$PIPE_RUN\\s*DSML\\s*$PIPE_RUN\\s*invoke\\b([^>]*)>(.*?)" +
            "<\\s*/\\s*$PIPE_RUN\\s*DSML\\s*$PIPE_RUN\\s*invoke\\s*>",
    )
    private val parameterMarker = Regex(
        "(?is)<\\s*$PIPE_RUN\\s*DSML\\s*$PIPE_RUN\\s*parameter\\b([^>]*)>(.*?)" +
            "<\\s*/\\s*$PIPE_RUN\\s*DSML\\s*$PIPE_RUN\\s*parameter\\s*>",
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


/**
 * Extracts only an exact, valid sequence of allowed function calls appended to the end of
 * assistant content. Calls inside Markdown fences or followed by ordinary prose are ignored.
 *
 * This is intentionally a last-resort recovery path. DeepSeek text-encoded calls are retried
 * with a correction prompt first; Arbor executes this parsed form only if that retry also fails.
 */
internal object PlainTextToolCallDetector {
    private val json = Json { ignoreUnknownKeys = false }

    fun extractTrailingCalls(value: String, allowedTools: Set<String>): List<NativeToolCall> {
        if (value.isBlank() || allowedTools.isEmpty()) return emptyList()
        val canonical = allowedTools.associateBy { it.lowercase() }
        val alternatives = canonical.keys
            .sortedByDescending(String::length)
            .joinToString("|") { Regex.escape(it) }
        val callStart = Regex(
            "(?<![A-Za-z0-9_])($alternatives)\\s*(?=\\{)",
            RegexOption.IGNORE_CASE,
        )

        callStart.findAll(value).forEach { first ->
            if (insideCodeFence(value, first.range.first)) return@forEach
            val calls = mutableListOf<NativeToolCall>()
            var cursor = first.range.first

            while (true) {
                while (cursor < value.length && value[cursor].isWhitespace()) cursor++
                val match = callStart.find(value, cursor)
                    ?.takeIf { it.range.first == cursor }
                    ?: break
                if (insideCodeFence(value, match.range.first)) break

                val name = canonical[match.groupValues[1].lowercase()] ?: break
                val objectStart = value.indexOf('{', match.range.last + 1)
                if (objectStart < 0) break
                val objectEnd = findJsonObjectEnd(value, objectStart) ?: break
                val rawArguments = value.substring(objectStart, objectEnd + 1)
                val arguments = runCatching {
                    json.parseToJsonElement(rawArguments) as? JsonObject
                }.getOrNull() ?: break

                calls += NativeToolCall(
                    id = "text-${calls.size + 1}-${rawArguments.hashCode().toUInt().toString(16)}",
                    name = name,
                    argumentsJson = arguments.toString(),
                )
                cursor = objectEnd + 1
                while (cursor < value.length && value[cursor].isWhitespace()) cursor++
                if (cursor == value.length) return calls
            }
        }
        return emptyList()
    }

    private fun findJsonObjectEnd(value: String, start: Int): Int? {
        var depth = 0
        var insideString = false
        var escaped = false
        for (index in start until value.length) {
            val character = value[index]
            if (insideString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> insideString = false
                }
                continue
            }
            when (character) {
                '"' -> insideString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index
                    if (depth < 0) return null
                }
            }
        }
        return null
    }

    private fun insideCodeFence(value: String, index: Int): Boolean {
        var cursor = 0
        var fences = 0
        while (true) {
            val next = value.indexOf("```", cursor)
            if (next < 0 || next >= index) break
            fences++
            cursor = next + 3
        }
        return fences % 2 == 1
    }
}

/** Incrementally removes DSML from streamed assistant text and emits native calls at EOF. */
internal class DsmlToolStreamAdapter(private val allowedTools: Set<String>) {
    private val pending = StringBuilder()
    private val protocol = StringBuilder()
    private val calls = mutableListOf<NativeToolCall>()
    private var insideProtocol = false
    private var malformed = false

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
                if (parsed.malformed || parsed.calls.isEmpty()) malformed = true
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
            malformed = true
        } else {
            visible.append(pending)
        }
        val wasMalformed = malformed
        val completedCalls = calls.toList()
        if (wasMalformed) visible.append(DsmlToolProtocol.MALFORMED_NOTICE)
        pending.clear()
        protocol.clear()
        calls.clear()
        insideProtocol = false
        malformed = false
        return DsmlToolProtocolResult(
            visibleText = visible.toString(),
            calls = completedCalls,
            malformed = wasMalformed,
        )
    }

    private companion object {
        const val MARKER_LOOKBEHIND = 96
    }
}
