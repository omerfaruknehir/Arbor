package app.arbor.chat.generation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal data class ToolCallPresentation(
    val kind: String,
    val preparingLabel: String,
    val runningLabel: String,
    val input: String,
)

private val ToolPreviewJson = Json { ignoreUnknownKeys = true }

internal fun toolCallPresentation(name: String, argumentsJson: String): ToolCallPresentation {
    val normalized = name.lowercase()
    fun value(field: String): String = completeJsonString(argumentsJson, field)
        ?: partialJsonString(argumentsJson, field)
        ?: ""

    return when (normalized) {
        "web_search", "search" -> ToolCallPresentation(
            kind = "search",
            preparingLabel = "Preparing web search",
            runningLabel = "Searching the web",
            input = value("query"),
        )
        "web_fetch", "fetch" -> ToolCallPresentation(
            kind = "fetch",
            preparingLabel = "Preparing web fetch",
            runningLabel = "Reading a web page",
            input = value("url"),
        )
        "linux_exec", "ubuntu_exec", "shell", "linux", "ubuntu" -> ToolCallPresentation(
            kind = "ubuntu",
            preparingLabel = "Preparing Linux tool call",
            runningLabel = "Using Linux tools",
            input = value("command"),
        )
        "send_file", "file_send" -> ToolCallPresentation(
            kind = "file_send",
            preparingLabel = "Preparing file delivery",
            runningLabel = "Preparing a file",
            input = value("path"),
        )
        "python", "python_exec" -> ToolCallPresentation(
            kind = "python",
            preparingLabel = "Preparing Python tool call",
            runningLabel = "Running Python",
            input = value("code"),
        )
        "memory_save" -> ToolCallPresentation(
            kind = "memory",
            preparingLabel = "Preparing memory update",
            runningLabel = "Saving memory",
            input = value("text"),
        )
        "memory_list" -> ToolCallPresentation(
            kind = "memory",
            preparingLabel = "Preparing memory lookup",
            runningLabel = "Reading memory",
            input = "Enabled memories",
        )
        "memory_forget" -> ToolCallPresentation(
            kind = "memory",
            preparingLabel = "Preparing memory removal",
            runningLabel = "Forgetting memory",
            input = value("id"),
        )
        "compile_widget", "widget_compile" -> ToolCallPresentation(
            kind = "widget_compile",
            preparingLabel = "Preparing widget compile",
            runningLabel = "Compiling Home widget",
            input = value("source").let { source ->
                if (source.isBlank()) "Internal widget candidate" else "Internal widget candidate • ${source.length} characters"
            },
        )
        else -> ToolCallPresentation(
            kind = "tool_call",
            preparingLabel = if (name.isBlank()) "Preparing tool call" else "Preparing $name tool call",
            runningLabel = if (name.isBlank()) "Running tool" else "Running $name",
            input = argumentsJson.take(4_000),
        )
    }
}

private fun completeJsonString(json: String, field: String): String? = runCatching {
    val root = ToolPreviewJson.parseToJsonElement(json) as? JsonObject ?: return@runCatching null
    root[field]?.jsonPrimitive?.contentOrNull
}.getOrNull()

/**
 * Extracts a still-incomplete JSON string value without reparsing on every token.
 * This is deliberately narrow: Arbor only previews known string arguments.
 */
internal fun partialJsonString(json: String, field: String): String? {
    val key = "\"$field\""
    var searchFrom = 0
    while (searchFrom < json.length) {
        val keyStart = json.indexOf(key, searchFrom)
        if (keyStart < 0) return null
        var cursor = keyStart + key.length
        while (cursor < json.length && json[cursor].isWhitespace()) cursor++
        if (cursor >= json.length || json[cursor] != ':') {
            searchFrom = keyStart + key.length
            continue
        }
        cursor++
        while (cursor < json.length && json[cursor].isWhitespace()) cursor++
        if (cursor >= json.length || json[cursor] != '"') return null
        cursor++

        val result = StringBuilder()
        while (cursor < json.length && result.length < 4_000) {
            val char = json[cursor++]
            when (char) {
                '"' -> return result.toString()
                '\\' -> {
                    if (cursor >= json.length) return result.toString()
                    when (val escaped = json[cursor++]) {
                        '"', '\\', '/' -> result.append(escaped)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000C')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> {
                            if (cursor + 4 > json.length) return result.toString()
                            val hex = json.substring(cursor, cursor + 4)
                            val decoded = hex.toIntOrNull(16) ?: return result.toString()
                            result.append(decoded.toChar())
                            cursor += 4
                        }
                        else -> result.append(escaped)
                    }
                }
                else -> result.append(char)
            }
        }
        return result.toString()
    }
    return null
}
