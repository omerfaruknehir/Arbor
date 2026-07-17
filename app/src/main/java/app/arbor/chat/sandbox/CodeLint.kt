package app.arbor.chat.sandbox

import kotlinx.serialization.json.Json

enum class LintSeverity { ERROR, WARNING, INFO }

data class CodeDiagnostic(
    val severity: LintSeverity,
    val line: Int? = null,
    val column: Int? = null,
    val message: String,
)

data class CodeLintResult(
    val language: String,
    val engine: String,
    val diagnostics: List<CodeDiagnostic> = emptyList(),
) {
    val hasErrors: Boolean get() = diagnostics.any { it.severity == LintSeverity.ERROR }
}

object StaticCodeLinter {
    fun lint(language: String, code: String): CodeLintResult {
        val normalized = language.trim().lowercase().ifBlank { "text" }
        val diagnostics = mutableListOf<CodeDiagnostic>()
        code.lineSequence().forEachIndexed { index, line ->
            if (line.endsWith(' ') || line.endsWith('\t')) diagnostics += CodeDiagnostic(LintSeverity.WARNING, index + 1, line.length, "Trailing whitespace")
            if (line.length > 140) diagnostics += CodeDiagnostic(LintSeverity.INFO, index + 1, 141, "Line is longer than 140 characters")
        }
        when (normalized) {
            "json", "jsonc" -> if (normalized == "json") runCatching { Json.parseToJsonElement(code) }.exceptionOrNull()?.let {
                diagnostics += CodeDiagnostic(LintSeverity.ERROR, message = it.message?.lineSequence()?.firstOrNull().orEmpty().ifBlank { "Invalid JSON" })
            }
            "python", "py" -> code.lineSequence().forEachIndexed { index, line ->
                if ('\t' in line.takeWhile(Char::isWhitespace)) diagnostics += CodeDiagnostic(LintSeverity.WARNING, index + 1, 1, "Tab used for indentation")
            }
            "bash", "sh", "shell", "zsh", "ubuntu" -> code.lineSequence().forEachIndexed { index, line ->
                if (Regex("(^|\\s)(sudo\\s+)?(apt|apt-get|dpkg|pip3?|python3?\\s+-m\\s+pip)(\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
                    diagnostics += CodeDiagnostic(LintSeverity.INFO, index + 1, 1, "Package-manager commands require Arbor's package approval flow")
                }
            }
        }
        diagnostics += delimiterDiagnostics(normalized, code)
        return CodeLintResult(normalized, if (normalized == "json") "JSON parser + Arbor style" else "Arbor static lint", diagnostics)
    }

    private fun delimiterDiagnostics(language: String, code: String): List<CodeDiagnostic> {
        if (language !in setOf("kotlin", "kt", "java", "javascript", "js", "typescript", "ts", "c", "cpp", "c++", "csharp", "cs", "rust", "json", "jsonc")) return emptyList()
        val stack = mutableListOf<Pair<Char, Pair<Int, Int>>>()
        var quote: Char? = null
        var escaped = false
        code.lineSequence().forEachIndexed { lineIndex, line ->
            line.forEachIndexed { columnIndex, char ->
                if (escaped) { escaped = false; return@forEachIndexed }
                if (char == '\\' && quote != null) { escaped = true; return@forEachIndexed }
                if (quote != null) {
                    if (char == quote) quote = null
                    return@forEachIndexed
                }
                if (char == '"' || char == '\'') { quote = char; return@forEachIndexed }
                if (char in "([{") stack += char to (lineIndex + 1 to columnIndex + 1)
                if (char in ")]}") {
                    val expected = when (char) { ')' -> '('; ']' -> '['; else -> '{' }
                    if (stack.lastOrNull()?.first == expected) stack.removeAt(stack.lastIndex)
                    else return listOf(CodeDiagnostic(LintSeverity.ERROR, lineIndex + 1, columnIndex + 1, "Unmatched '$char'"))
                }
            }
        }
        return stack.lastOrNull()?.let { (char, position) -> listOf(CodeDiagnostic(LintSeverity.ERROR, position.first, position.second, "Unclosed '$char'")) }.orEmpty()
    }
}
