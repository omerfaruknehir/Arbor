package app.xylune.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlainMarkdownLinksTest {
    @Test
    fun inlineLinksRemainVisibleMarkdownInsteadOfClickableSpans() {
        val rendered = renderMarkdownLinksLiterally(
            "Read [the source](https://example.com/a).",
        )
        assertTrue(rendered.contains("\\[the source\\]"))
        assertTrue(rendered.contains("\\(https://example.com/a\\)"))
        assertFalse(rendered.contains("[the source](https://example.com/a)"))
    }

    @Test
    fun normalMarkdownFormattingIsNotEscaped() {
        assertEquals(
            "**bold** and `code`",
            renderMarkdownLinksLiterally("**bold** and `code`"),
        )
    }

    @Test
    fun angleAutolinksAreShownLiterally() {
        val rendered = renderMarkdownLinksLiterally("<https://example.com>")
        assertTrue(rendered.startsWith("\\<"))
        assertTrue(rendered.endsWith("\\>"))
    }
}
