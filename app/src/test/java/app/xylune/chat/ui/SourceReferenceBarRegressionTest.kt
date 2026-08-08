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
    fun `bottom source bar stays horizontal while preview is a separate animated popup`() {
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
        assertTrue(source.contains("var pendingReference"))
        assertTrue(source.contains("onGloballyPositioned"))
        assertTrue(source.contains("anchorBoundsInWindow = anchor"))
        assertTrue(source.contains("AnchoredLinkPreview("))
        assertTrue(source.contains("widthIn(max = 230.dp)"))
        assertFalse(source.contains("animateContentSize("))
        assertFalse(source.contains("var expandedTarget"))
        assertTrue(richMessage.contains("SourceReferenceBar("))
        assertTrue(!richMessage.contains("sourceReferencesFooterMarkdown"))

        assertTrue(preview.contains("MutableTransitionState(false)"))
        assertTrue(preview.contains("scaleIn("))
        assertTrue(preview.contains("initialScale = initialScale"))
        assertTrue(preview.contains("Modifier.width(330.dp).heightIn(max = 420.dp)"))
        assertTrue(preview.contains("ReleaseDismissOutsideLayer("))
        assertTrue(preview.contains("dismissOnBackPress = false"))
        assertTrue(preview.contains("dismissOnClickOutside = false"))
    }
}
