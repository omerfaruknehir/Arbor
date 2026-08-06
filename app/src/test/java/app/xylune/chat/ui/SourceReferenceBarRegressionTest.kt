package app.xylune.chat.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SourceReferenceBarRegressionTest {
    private fun repositoryFile(path: String): File = sequenceOf(File(path), File("..", path))
        .firstOrNull(File::isFile)
        ?: error("Could not locate repository file: $path")

    @Test
    fun `bottom source bar stays horizontal scrollable and opens anchored previews`() {
        val source = repositoryFile(
            "app/src/main/java/app/xylune/chat/ui/SourceReferenceBar.kt",
        ).readText()
        val richMessage = repositoryFile(
            "app/src/main/java/app/xylune/chat/ui/RichMessage.kt",
        ).readText()

        assertTrue(source.contains("LowSensitivityHorizontalScroll"))
        assertTrue(source.contains("horizontalArrangement = Arrangement.spacedBy(8.dp)"))
        assertTrue(source.contains("AnchoredLinkPreview"))
        assertTrue(source.contains("anchorBoundsInWindow = anchor"))
        assertTrue(source.contains("widthIn(max = 230.dp)"))
        assertTrue(richMessage.contains("SourceReferenceBar("))
        assertTrue(!richMessage.contains("sourceReferencesFooterMarkdown"))
    }
}
