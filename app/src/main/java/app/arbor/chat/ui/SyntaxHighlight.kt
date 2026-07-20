package app.arbor.chat.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontFamily
import app.arbor.chat.sandbox.CodeDiagnostic
import app.arbor.chat.sandbox.CodeLintResult
import app.arbor.chat.sandbox.LintSeverity
import app.arbor.chat.sandbox.StaticCodeLinter

internal enum class SyntaxKind {
    KEYWORD, STRING, NUMBER, COMMENT, TYPE, FUNCTION, PROPERTY, ANNOTATION, OPERATOR,
}

internal data class SyntaxSpan(val start: Int, val endExclusive: Int, val kind: SyntaxKind)

private enum class SyntaxFamily { PYTHON, SHELL, C_LIKE, JSON, MARKUP, SQL, YAML, PLAIN }

private val pythonKeywords = setOf(
    "and", "as", "assert", "async", "await", "break", "case", "class", "continue", "def", "del",
    "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is",
    "lambda", "match", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with",
    "yield", "False", "None", "True",
)

private val shellKeywords = setOf(
    "case", "coproc", "do", "done", "elif", "else", "esac", "fi", "for", "function", "if", "in",
    "select", "then", "time", "until", "while", "break", "continue", "declare", "export", "local",
    "readonly", "return", "set", "shift", "source", "trap", "unset",
)

private val cLikeKeywords = setOf(
    "abstract", "as", "async", "await", "base", "bool", "break", "by", "byte", "case", "catch", "char",
    "class", "companion", "const", "continue", "data", "decimal", "default", "defer", "delegate", "do",
    "double", "dynamic", "else", "enum", "event", "explicit", "export", "extends", "extern", "false",
    "final", "finally", "fixed", "float", "fn", "for", "foreach", "from", "fun", "function", "get", "go",
    "goto", "if", "implements", "implicit", "import", "in", "inline", "instanceof", "int", "interface",
    "internal", "is", "lateinit", "let", "lock", "long", "match", "mod", "move", "mut", "namespace",
    "native", "new", "null", "object", "operator", "out", "override", "package", "params", "private",
    "protected", "protocol", "public", "readonly", "record", "ref", "return", "sealed", "set", "short",
    "signed", "sizeof", "static", "struct", "super", "suspend", "switch", "synchronized", "this", "throw",
    "throws", "trait", "transient", "true", "try", "typealias", "typedef", "typeof", "uint", "ulong",
    "unchecked", "union", "unsafe", "unsigned", "use", "using", "val", "var", "virtual", "void", "volatile",
    "when", "where", "while", "with", "yield",
)

private val sqlKeywords = setOf(
    "add", "all", "alter", "and", "as", "asc", "between", "by", "case", "check", "column", "constraint",
    "create", "cross", "database", "default", "delete", "desc", "distinct", "drop", "else", "end", "exists",
    "foreign", "from", "full", "group", "having", "in", "index", "inner", "insert", "into", "is", "join",
    "key", "left", "like", "limit", "not", "null", "on", "or", "order", "outer", "primary", "references",
    "right", "select", "set", "table", "then", "union", "unique", "update", "values", "view", "when", "where",
)

private fun family(language: String): SyntaxFamily = when (language.lowercase().trim()) {
    "py", "python", "python3" -> SyntaxFamily.PYTHON
    "bash", "sh", "shell", "zsh", "fish", "ubuntu", "console" -> SyntaxFamily.SHELL
    "json", "jsonc", "json5" -> SyntaxFamily.JSON
    "html", "htm", "xml", "svg", "xhtml" -> SyntaxFamily.MARKUP
    "sql", "sqlite", "postgres", "postgresql", "mysql" -> SyntaxFamily.SQL
    "yaml", "yml", "toml", "ini", "properties" -> SyntaxFamily.YAML
    "c", "h", "cpp", "c++", "cc", "hpp", "csharp", "c#", "cs", "java", "kotlin", "kt", "kts",
    "javascript", "js", "jsx", "typescript", "ts", "tsx", "dart", "swift", "go", "golang", "rust", "rs",
    "php", "scala", "groovy", "gradle", "css", "scss", "less" -> SyntaxFamily.C_LIKE
    else -> SyntaxFamily.PLAIN
}

internal fun syntaxSpans(language: String, code: String): List<SyntaxSpan> {
    if (code.isEmpty()) return emptyList()
    val family = family(language)
    val result = mutableListOf<SyntaxSpan>()
    var index = 0

    fun add(end: Int, kind: SyntaxKind) {
        result += SyntaxSpan(index, end.coerceAtMost(code.length), kind)
        index = end.coerceAtMost(code.length)
    }

    fun lineEnd(from: Int): Int = code.indexOf('\n', from).let { if (it < 0) code.length else it }
    fun closing(from: Int, marker: String): Int = code.indexOf(marker, from).let { if (it < 0) code.length else it + marker.length }
    fun nextNonWhitespace(from: Int): Int {
        var cursor = from
        while (cursor < code.length && code[cursor].isWhitespace()) cursor++
        return cursor
    }
    fun previousNonWhitespace(from: Int): Int {
        var cursor = from
        while (cursor >= 0 && code[cursor].isWhitespace()) cursor--
        return cursor
    }

    while (index < code.length) {
        val char = code[index]

        if (family == SyntaxFamily.MARKUP && code.startsWith("<!--", index)) {
            add(closing(index + 4, "-->"), SyntaxKind.COMMENT)
            continue
        }
        if (family in setOf(SyntaxFamily.C_LIKE, SyntaxFamily.SQL, SyntaxFamily.JSON, SyntaxFamily.PLAIN) && code.startsWith("/*", index)) {
            add(closing(index + 2, "*/"), SyntaxKind.COMMENT)
            continue
        }
        if (family in setOf(SyntaxFamily.C_LIKE, SyntaxFamily.JSON, SyntaxFamily.PLAIN) && code.startsWith("//", index)) {
            add(lineEnd(index + 2), SyntaxKind.COMMENT)
            continue
        }
        if (family == SyntaxFamily.SQL && code.startsWith("--", index)) {
            add(lineEnd(index + 2), SyntaxKind.COMMENT)
            continue
        }
        if (char == '#' && family in setOf(SyntaxFamily.PYTHON, SyntaxFamily.SHELL, SyntaxFamily.YAML, SyntaxFamily.PLAIN)) {
            add(lineEnd(index + 1), SyntaxKind.COMMENT)
            continue
        }

        if (family == SyntaxFamily.PYTHON && (code.startsWith("\"\"\"", index) || code.startsWith("'''", index))) {
            val marker = code.substring(index, index + 3)
            add(closing(index + 3, marker), SyntaxKind.STRING)
            continue
        }
        if (char == '\'' || char == '"' || (char == '`' && family in setOf(SyntaxFamily.C_LIKE, SyntaxFamily.SHELL, SyntaxFamily.PLAIN))) {
            val quote = char
            var end = index + 1
            var escaped = false
            while (end < code.length) {
                val current = code[end++] 
                if (escaped) escaped = false
                else if (current == '\\') escaped = true
                else if (current == quote) break
            }
            val property = family in setOf(SyntaxFamily.JSON, SyntaxFamily.YAML) &&
                nextNonWhitespace(end).let { it < code.length && (code[it] == ':' || code[it] == '=') }
            add(end, if (property) SyntaxKind.PROPERTY else SyntaxKind.STRING)
            continue
        }

        if (char == '@' && index + 1 < code.length && (code[index + 1].isLetter() || code[index + 1] == '_')) {
            var end = index + 2
            while (end < code.length && (code[end].isLetterOrDigit() || code[end] in "_.$")) end++
            add(end, SyntaxKind.ANNOTATION)
            continue
        }

        if (char.isDigit() || (char == '.' && index + 1 < code.length && code[index + 1].isDigit())) {
            var end = index + 1
            while (end < code.length && (code[end].isLetterOrDigit() || code[end] in "_+-.")) end++
            add(end, SyntaxKind.NUMBER)
            continue
        }

        if (char.isLetter() || char == '_' || (char == '$' && index + 1 < code.length && (code[index + 1].isLetter() || code[index + 1] == '_'))) {
            var end = index + 1
            while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '_' || code[end] == '$')) end++
            val word = code.substring(index, end)
            val lowered = word.lowercase()
            val next = nextNonWhitespace(end)
            val previous = previousNonWhitespace(index - 1)
            val kind = when {
                family == SyntaxFamily.PYTHON && word in pythonKeywords -> SyntaxKind.KEYWORD
                family == SyntaxFamily.SHELL && lowered in shellKeywords -> SyntaxKind.KEYWORD
                family == SyntaxFamily.C_LIKE && lowered in cLikeKeywords -> SyntaxKind.KEYWORD
                family == SyntaxFamily.SQL && lowered in sqlKeywords -> SyntaxKind.KEYWORD
                lowered in setOf("true", "false", "null", "nil", "none", "undefined") -> SyntaxKind.KEYWORD
                family == SyntaxFamily.MARKUP && previous >= 0 && (code[previous] == '<' || code[previous] == '/') -> SyntaxKind.TYPE
                next < code.length && code[next] in setOf(':', '=') && family in setOf(SyntaxFamily.JSON, SyntaxFamily.YAML, SyntaxFamily.MARKUP) -> SyntaxKind.PROPERTY
                next < code.length && code[next] == '(' -> SyntaxKind.FUNCTION
                word.firstOrNull()?.isUpperCase() == true -> SyntaxKind.TYPE
                char == '$' -> SyntaxKind.PROPERTY
                else -> null
            }
            if (kind != null) add(end, kind) else index = end
            continue
        }

        if (char in "{}[]()=+-*/%<>!&|^~?:;,." ) {
            var end = index + 1
            while (end < code.length && code[end] in "=<>!&|:+-" && end - index < 3) end++
            add(end, SyntaxKind.OPERATOR)
            continue
        }
        index++
    }
    return result
}

private data class SyntaxPalette(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val type: Color,
    val function: Color,
    val property: Color,
    val annotation: Color,
    val operator: Color,
    val lintError: Color,
    val lintWarning: Color,
    val lintInfo: Color,
)

@Composable
private fun rememberSyntaxPalette(): SyntaxPalette {
    val dark = isSystemInDarkTheme()
    return remember(dark) {
        if (dark) SyntaxPalette(
            keyword = Color(0xFFC792EA), string = Color(0xFFC3E88D), number = Color(0xFFF78C6C),
            comment = Color(0xFF81909C), type = Color(0xFF82AAFF), function = Color(0xFFFFCB6B),
            property = Color(0xFF89DDFF), annotation = Color(0xFFFF9CAC), operator = Color(0xFF89DDFF),
            lintError = Color(0xFFFF6B6B), lintWarning = Color(0xFFFFC857), lintInfo = Color(0xFF72B7FF),
        ) else SyntaxPalette(
            keyword = Color(0xFF6F42C1), string = Color(0xFF2E7D32), number = Color(0xFFC2410C),
            comment = Color(0xFF667680), type = Color(0xFF1565C0), function = Color(0xFF8A6100),
            property = Color(0xFF007C91), annotation = Color(0xFFB4235A), operator = Color(0xFF455A64),
            lintError = Color(0xFFBA1A1A), lintWarning = Color(0xFF8A5A00), lintInfo = Color(0xFF005DB7),
        )
    }
}

private fun diagnosticRange(code: String, diagnostic: CodeDiagnostic): IntRange? {
    val lineNumber = diagnostic.line ?: return null
    if (lineNumber < 1) return null
    var line = 1
    var start = 0
    while (line < lineNumber && start < code.length) {
        val newline = code.indexOf('\n', start)
        if (newline < 0) return null
        start = newline + 1
        line++
    }
    if (start > code.length) return null
    val lineEnd = code.indexOf('\n', start).let { if (it < 0) code.length else it }
    if (lineEnd <= start) return null
    val requested = (diagnostic.column ?: 1).coerceAtLeast(1) - 1
    val markerStart = (start + requested).coerceIn(start, (lineEnd - 1).coerceAtLeast(start))
    var markerEnd = markerStart + 1
    while (markerEnd < lineEnd && (code[markerEnd].isLetterOrDigit() || code[markerEnd] == '_')) markerEnd++
    return markerStart until markerEnd.coerceAtMost(code.length)
}

private fun renderHighlightedCode(
    language: String,
    code: String,
    palette: SyntaxPalette,
    diagnostics: List<CodeDiagnostic>,
): AnnotatedString = buildAnnotatedString {
    append(code)
    syntaxSpans(language, code).forEach { span ->
        val color = when (span.kind) {
            SyntaxKind.KEYWORD -> palette.keyword
            SyntaxKind.STRING -> palette.string
            SyntaxKind.NUMBER -> palette.number
            SyntaxKind.COMMENT -> palette.comment
            SyntaxKind.TYPE -> palette.type
            SyntaxKind.FUNCTION -> palette.function
            SyntaxKind.PROPERTY -> palette.property
            SyntaxKind.ANNOTATION -> palette.annotation
            SyntaxKind.OPERATOR -> palette.operator
        }
        addStyle(
            SpanStyle(
                color = color,
                fontWeight = if (span.kind in setOf(SyntaxKind.KEYWORD, SyntaxKind.TYPE, SyntaxKind.ANNOTATION)) FontWeight.SemiBold else null,
            ),
            span.start,
            span.endExclusive,
        )
    }
    diagnostics.forEach { diagnostic ->
        val range = diagnosticRange(code, diagnostic) ?: return@forEach
        val color = when (diagnostic.severity) {
            LintSeverity.ERROR -> palette.lintError
            LintSeverity.WARNING -> palette.lintWarning
            LintSeverity.INFO -> palette.lintInfo
        }
        addStyle(
            SpanStyle(
                background = color.copy(alpha = .15f),
                textDecoration = TextDecoration.Underline,
            ),
            range.first,
            range.last + 1,
        )
    }
}

@Composable
internal fun highlightedCode(
    language: String,
    code: String,
    diagnostics: List<CodeDiagnostic> = emptyList(),
): AnnotatedString {
    val palette = rememberSyntaxPalette()
    return remember(language, code, diagnostics, palette) {
        renderHighlightedCode(language, code, palette, diagnostics)
    }
}

@Composable
internal fun rememberCodeVisualTransformation(
    language: String,
    diagnostics: List<CodeDiagnostic>? = null,
): VisualTransformation {
    val palette = rememberSyntaxPalette()
    return remember(language, diagnostics, palette) {
        VisualTransformation { input ->
            val effectiveDiagnostics = diagnostics ?: StaticCodeLinter.lint(language, input.text).diagnostics
            TransformedText(
                renderHighlightedCode(language, input.text, palette, effectiveDiagnostics),
                OffsetMapping.Identity,
            )
        }
    }
}

@Composable
internal fun AutoLintedCodeText(
    language: String,
    code: String,
    modifier: Modifier = Modifier,
    lintResult: CodeLintResult? = null,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    softWrap: Boolean = false,
) {
    val automatic = remember(language, code) { StaticCodeLinter.lint(language, code) }
    val diagnostics = lintResult?.diagnostics ?: automatic.diagnostics
    SelectionContainer(modifier = modifier) {
        Text(
            highlightedCode(language, code, diagnostics),
            fontFamily = FontFamily.Monospace,
            style = style,
            softWrap = softWrap,
        )
    }
}
