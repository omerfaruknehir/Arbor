package app.xylune.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SourceReferenceBarRegressionTest {
    private fun repositoryFile(path: String): File = sequenceOf(File(path), File("..", path))
        .firstOrNull(File::isFile)
        ?: error("Could not locate repository file: $path")

    @Test
    fun `bottom source bar stays horizontal and source pills expand inline`() {
        val source = repositoryFile(
            "app/src/main/java/app/xylune/chat/ui/SourceReferenceBar.kt",
        ).readText()
        val richMessage = repositoryFile(
            "app/src/main/java/app/xylune/chat/ui/RichMessage.kt",
        ).readText()
        val preview = repositoryFile(
            "app/src/main/java/app/xylune/chat/ui/LinkPreview.kt",
        ).readText()

        assertTrue(source.contains("LowSensitivityHorizontalScroll"))
        assertTrue(source.contains("horizontalArrangement = Arrangement.spacedBy(8.dp)"))
        assertTrue(source.contains("var expandedTarget"))
        assertTrue(source.contains("animateContentSize("))
        assertTrue(source.contains("AnimatedVisibility("))
        assertTrue(source.contains("LinkPreviewDetails("))
        assertTrue(source.contains("showHeader = false"))
        assertTrue(source.contains("widthIn(max = if (expanded) 340.dp else 230.dp)"))
        assertFalse(source.contains("AnchoredLinkPreview("))
        assertFalse(source.contains("anchorBoundsInWindow = anchor"))
        assertTrue(richMessage.contains("SourceReferenceBar("))
        assertTrue(!richMessage.contains("sourceReferencesFooterMarkdown"))
        assertTrue(preview.contains("showHeader: Boolean = true"))
        assertTrue(preview.contains("onDismissRequest = onDismiss"))
        assertTrue(preview.contains("focusable = true"))
        assertTrue(preview.contains("dismissOnClickOutside = true"))
        assertFalse(preview.contains("ReleaseDismissOutsideLayer(visible = true"))
    }
}
