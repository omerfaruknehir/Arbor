package app.xylune.chat.provider

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
 * decoded arguments through Xylune's normal native-tool validation and execution.
 */
internal object DsmlToolProtocol {
    private val json = Json { ignoreUnknownKeys = false }

    // DeepSeek-compatible gateways vary the fence glyphs, HTML-escape angle
    // brackets, and sometimes insert Unicode format/space characters. Match only
    // the exact DSML namespace and known element names, but tolerate those wire
    // representation differences.
    private const val GAP = "[\\s\\p{Z}\\p{Cf}]*"
    private const val PIPE_TOKEN =
        "(?:[|｜¦∣│❘￨]|&(?:vert|VerticalLine);|&#0*124;|&#x0*7c;)"
    private const val PIPE_RUN = "(?:$PIPE_TOKEN$GAP)+"
    private const val OPEN_ANGLE = "(?:<|&lt;|&#0*60;|&#x0*3c;)"
    private const val CLOSE_ANGLE = "(?:>|&gt;|&#0*62;|&#x0*3e;)"
    private const val SLASH = "(?:/|&#0*47;|&#x0*2f;)"

    private fun openingTag(element: String, captureAttributes: Boolean = false): String {
        val attributes = if (captureAttributes) "\\b(.*?)" else ""
        return "$OPEN_ANGLE$GAP$PIPE_RUN${GAP}DSML$GAP$PIPE_RUN$GAP$element$attributes$GAP$CLOSE_ANGLE"
    }

    private fun closingTag(element: String): String =
        "$OPEN_ANGLE$GAP$SLASH$GAP$PIPE_RUN${GAP}DSML$GAP$PIPE_RUN$GAP$element$GAP$CLOSE_ANGLE"

    private val startMarker = Regex("(?is)${openingTag("tool_calls")}")
    private val endMarker = Regex("(?is)${closingTag("tool_calls")}")
    private val invokeMarker = Regex(
        "(?is)${openingTag("invoke", captureAttributes = true)}(.*?)${closingTag("invoke")}",
    )
    private val parameterMarker = Regex(
        "(?is)${openingTag("parameter", captureAttributes = true)}(.*?)${closingTag("parameter")}",
    )

    internal fun findStart(value: CharSequence): MatchResult? = startMarker.find(value)
    internal fun findEnd(value: CharSequence): MatchResult? = endMarker.find(value)

    internal fun containsProtocolHint(value: CharSequence): Boolean {
        if (value.isEmpty()) return false
        val compact = buildString(value.length) {
            value.forEach { character ->
                if (character.isLetterOrDigit()) append(character.lowercaseChar())
            }
        }
        return compact.contains("dsml") &&
            compact.contains("toolcalls") &&
            (compact.contains("invoke") || compact.contains("parameter"))
    }

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
        .replace(Regex("(?i)&quot;"), "\"")
        .replace(Regex("(?i)&#(?:0*39|x0*27);"), "'")
        .replace(Regex("(?i)&lt;|&#(?:0*60|x0*3c);"), "<")
        .replace(Regex("(?i)&gt;|&#(?:0*62|x0*3e);"), ">")
        .replace(Regex("(?i)&vert;|&VerticalLine;|&#(?:0*124|x0*7c);"), "|")
        .replace(Regex("(?i)&amp;"), "&")

    private fun malformed() = DsmlToolProtocolResult(
        visibleText = MALFORMED_NOTICE,
        calls = emptyList(),
        malformed = true,
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    internal const val MALFORMED_NOTICE =
        "\n\n*The provider returned a malformed tool request. Xylune ignored the protocol text instead of displaying or executing it.*\n\n"
}


/**
 * Extracts only an exact, valid sequence of allowed function calls appended to the end of
 * assistant content. Calls inside Markdown fences or followed by ordinary prose are ignored.
 *
 * This is intentionally a last-resort recovery path. DeepSeek text-encoded calls are retried
 * with a correction prompt first; Xylune executes this parsed form only if that retry also fails.
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

internal data class DsmlChannelDelta(
    val text: String,
    val reasoning: String,
)

internal data class DsmlChannelsResult(
    val tailText: String,
    val tailReasoning: String,
    val calls: List<NativeToolCall>,
    val malformed: Boolean,
)

/** Applies the DSML quarantine independently to assistant content and reasoning. */
internal class DsmlChannelsAdapter(allowedTools: Set<String>) {
    private val text = DsmlToolStreamAdapter(allowedTools)
    private val reasoning = DsmlToolStreamAdapter(allowedTools)

    fun accept(textDelta: String, reasoningDelta: String): DsmlChannelDelta = DsmlChannelDelta(
        text = text.accept(textDelta),
        reasoning = reasoning.accept(reasoningDelta),
    )

    fun finish(): DsmlChannelsResult {
        val textResult = text.finish()
        val reasoningResult = reasoning.finish()
        val calls = (textResult.calls + reasoningResult.calls).distinctBy { call ->
            "${call.name.lowercase()}\u0000${call.argumentsJson}"
        }
        return DsmlChannelsResult(
            tailText = textResult.visibleText,
            tailReasoning = reasoningResult.visibleText,
            calls = calls,
            malformed = textResult.malformed || reasoningResult.malformed,
        )
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
                val flushCount = safeVisiblePrefixLength(pending)
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

    /**
     * Returns the ordinary-text prefix which cannot become the beginning of a
     * split DSML marker. The old fixed 256-character window delayed every
     * tool-capable response even when the bytes were plainly normal prose.
     */
    private fun safeVisiblePrefixLength(value: CharSequence): Int {
        for (index in value.indices) {
            val character = value[index]
            if (character != '<' && character != '&') continue
            val normalized = normalizeOpeningCandidate(value.subSequence(index, value.length))
                ?: continue
            if (isOpeningMarkerPrefix(normalized)) return index
        }
        return value.length
    }

    private fun normalizeOpeningCandidate(candidate: CharSequence): String? {
        val normalized = StringBuilder(candidate.length)
        var index = 0
        while (index < candidate.length) {
            val character = candidate[index]
            when {
                isMarkerGap(character) -> index++
                character == '<' || character == '>' || character == '/' -> {
                    normalized.append(character)
                    index++
                }
                character in PIPE_GLYPHS -> {
                    normalized.append('|')
                    index++
                }
                character == '&' -> {
                    var end = index + 1
                    while (end < candidate.length && candidate[end] != ';') end++
                    if (end >= candidate.length) {
                        val partial = candidate.subSequence(index, candidate.length)
                            .toString()
                            .lowercase()
                        if (!isMarkerEntityPrefix(partial)) return null
                        return normalized.toString()
                    }
                    val decoded = decodeMarkerEntity(
                        candidate.subSequence(index, end + 1).toString(),
                    ) ?: return null
                    normalized.append(decoded)
                    index = end + 1
                }
                character.isLetterOrDigit() || character == '_' -> {
                    normalized.append(character.lowercaseChar())
                    index++
                }
                else -> return null
            }
        }
        return normalized.toString()
    }

    private fun isOpeningMarkerPrefix(value: String): Boolean {
        var index = 0
        if (value.isEmpty()) return true
        if (value[index++] != '<') return false
        if (index == value.length) return true

        var pipes = 0
        while (index < value.length && value[index] == '|') {
            pipes++
            index++
        }
        if (pipes == 0) return false
        if (index == value.length) return true

        for (expected in "dsml") {
            if (index == value.length) return true
            if (value[index++] != expected) return false
        }
        if (index == value.length) return true

        pipes = 0
        while (index < value.length && value[index] == '|') {
            pipes++
            index++
        }
        if (pipes == 0) return false
        if (index == value.length) return true

        for (expected in "tool_calls") {
            if (index == value.length) return true
            if (value[index++] != expected) return false
        }
        if (index == value.length) return true
        if (value[index++] != '>') return false
        return index == value.length
    }

    private fun isMarkerGap(character: Char): Boolean {
        if (character.isWhitespace()) return true
        return when (Character.getType(character)) {
            Character.SPACE_SEPARATOR.toInt(),
            Character.LINE_SEPARATOR.toInt(),
            Character.PARAGRAPH_SEPARATOR.toInt(),
            Character.FORMAT.toInt(),
            -> true
            else -> false
        }
    }

    private fun decodeMarkerEntity(value: String): Char? {
        val normalized = value.lowercase()
        return when (normalized) {
            "&lt;" -> '<'
            "&gt;" -> '>'
            "&vert;", "&verticalline;" -> '|'
            else -> {
                val number = when {
                    normalized.startsWith("&#x") && normalized.endsWith(';') ->
                        normalized.substring(3, normalized.length - 1).toIntOrNull(16)
                    normalized.startsWith("&#") && normalized.endsWith(';') ->
                        normalized.substring(2, normalized.length - 1).toIntOrNull(10)
                    else -> null
                }
                when (number) {
                    47 -> '/'
                    60 -> '<'
                    62 -> '>'
                    124 -> '|'
                    else -> null
                }
            }
        }
    }

    private fun isMarkerEntityPrefix(value: String): Boolean {
        if (NAMED_MARKER_ENTITIES.any { it.startsWith(value) }) return true
        if (!value.startsWith("&#")) return false
        val digits = value.removePrefix("&#")
        return if (digits.firstOrNull() == 'x') {
            val hexadecimal = digits.drop(1)
            hexadecimal.length <= 8 && hexadecimal.all {
                it.isDigit() || it in 'a'..'f'
            }
        } else {
            digits.length <= 8 && digits.all(Char::isDigit)
        }
    }

    private companion object {
        const val MARKER_LOOKBEHIND = 256
        val PIPE_GLYPHS = setOf('|', '｜', '¦', '∣', '│', '❘', '￨')
        val NAMED_MARKER_ENTITIES = listOf(
            "&lt;",
            "&gt;",
            "&vert;",
            "&verticalline;",
        )
    }
}
