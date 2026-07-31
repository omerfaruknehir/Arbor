package app.arbor.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserMessageRenderingRegressionTest {
    @Test
    fun `paging keys never reuse a previous message id for an unloaded row`() {
        val chat = File("src/main/java/app/arbor/chat/ui/ChatScreen.kt").readText()
        assertFalse(chat.contains("stableMessageKeysByUiIndex"))
        assertTrue(chat.contains("loading-${'$'}{conversation?.id.orEmpty()}-${'$'}uiIndex-${'$'}sourceIndex"))
    }

    @Test
    fun `markdown view shows complete current source while spans parse`() {
        val rich = File("src/main/java/app/arbor/chat/ui/RichMessage.kt").readText()
        assertTrue(rich.contains("parsedMarkdown?.takeIf { it.source == markdown }"))
        assertTrue(rich.contains("markdownRenderFallbackText(markdown)"))
        assertTrue(rich.contains("parsedMarkdown = null"))
    }
}
