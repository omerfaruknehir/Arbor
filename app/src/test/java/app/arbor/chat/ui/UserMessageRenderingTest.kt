package app.arbor.chat.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserMessageRenderingTest {
    @Test
    fun `completed user messages bypass streaming parser state`() {
        val chat = File("src/main/java/app/arbor/chat/ui/ChatScreen.kt").readText()
        val rich = File("src/main/java/app/arbor/chat/ui/RichMessage.kt").readText()
        assertTrue(chat.contains("staticContent = user"))
        assertTrue(rich.contains("val visibleBlocks = if (staticContent) staticBlocks else blocks"))
    }

    @Test
    fun `markdown view displays the complete fallback until parsing finishes`() {
        val rich = File("src/main/java/app/arbor/chat/ui/RichMessage.kt").readText()
        assertTrue(rich.contains("remember(markwon, markdown)"))
        assertTrue(rich.contains("markdownRenderFallbackText(markdown)"))
        assertTrue(rich.contains("renderedAsFallback"))
    }
}
