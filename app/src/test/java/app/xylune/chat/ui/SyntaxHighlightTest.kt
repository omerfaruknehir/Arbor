package app.xylune.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlightTest {
    @Test
    fun pythonRecognizesSemanticTokens() {
        val code = "def greet(name: str):\n    # friendly\n    return f\"Hello {name}\" + str(42)"
        val tokens = syntaxSpans("python", code).associateBy { code.substring(it.start, it.endExclusive) }

        assertEquals(SyntaxKind.KEYWORD, tokens["def"]?.kind)
        assertEquals(SyntaxKind.FUNCTION, tokens["greet"]?.kind)
        assertEquals(SyntaxKind.COMMENT, tokens["# friendly"]?.kind)
        assertEquals(SyntaxKind.STRING, tokens["\"Hello {name}\""]?.kind)
        assertEquals(SyntaxKind.NUMBER, tokens["42"]?.kind)
    }

    @Test
    fun jsonSeparatesPropertiesStringsAndLiterals() {
        val code = "{\"name\": \"Xylune\", \"enabled\": true, \"count\": 3}"
        val spans = syntaxSpans("json", code)
        fun kinds(text: String) = spans.filter { code.substring(it.start, it.endExclusive) == text }.map(SyntaxSpan::kind)

        assertTrue(SyntaxKind.PROPERTY in kinds("\"name\""))
        assertTrue(SyntaxKind.STRING in kinds("\"Xylune\""))
        assertTrue(SyntaxKind.KEYWORD in kinds("true"))
        assertTrue(SyntaxKind.NUMBER in kinds("3"))
    }

    @Test
    fun commonLanguagesProduceOrderedNonOverlappingSpans() {
        val samples = mapOf(
            "bash" to "for file in *.txt; do echo \"${'$'}file\"; done # files",
            "kotlin" to "data class Item(val count: Int = 2)",
            "sql" to "SELECT name FROM users WHERE id = 7 -- one row",
            "html" to "<section class=\"card\">Hello</section>",
            "yaml" to "enabled: true\ncount: 4",
        )

        samples.forEach { (language, code) ->
            val spans = syntaxSpans(language, code)
            assertTrue("$language should be highlighted", spans.isNotEmpty())
            spans.zipWithNext().forEach { (left, right) -> assertTrue(left.endExclusive <= right.start) }
            spans.forEach { span ->
                assertTrue(span.start >= 0)
                assertTrue(span.endExclusive in (span.start + 1)..code.length)
            }
        }
    }
}
