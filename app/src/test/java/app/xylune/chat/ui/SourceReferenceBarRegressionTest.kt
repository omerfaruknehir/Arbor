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
    fun `bottom source bar stays horizontal while overlay morphs above layout`() {
        val source = repositoryFile(
            "app/src/main/java/app/xylune/chat/ui/SourceReferenceBar.kt",
        ).readText()
        val richMessage = repositoryFile(
            "app/src/main/java/app/xylune/chat/ui/RichMessage.kt",
        ).readText()
        val morph = repositoryFile(
            "app/src/main/java/app/xylune/chat/ui/SourceMorphPreview.kt",
        ).readText()

        assertTrue(source.contains("LowSensitivityHorizontalScroll"))
        assertTrue(source.contains("horizontalArrangement = Arrangement.spacedBy(8.dp)"))
        assertTrue(source.contains("var pendingSource"))
        assertTrue(source.contains("onGloballyPositioned"))
        assertTrue(source.contains("anchorBoundsInWindow = anchor"))
        assertTrue(source.contains("MorphingSourcePreview("))
        assertTrue(source.contains("widthIn(max = 230.dp)"))
        assertFalse(source.contains("animateContentSize("))
        assertFalse(source.contains("var expandedTarget"))
        assertTrue(richMessage.contains("SourceReferenceBar("))
        assertFalse(richMessage.contains("sourceReferencesFooterMarkdown"))

        assertTrue(morph.contains("Animatable(0f)"))
        assertTrue(morph.contains(".fillMaxSize()"))
        assertTrue(morph.contains(".width(330.dp)"))
        assertTrue(morph.contains(".heightIn(max = 420.dp)"))
        assertTrue(morph.contains("this.scaleX = scaleX"))
        assertTrue(morph.contains("this.scaleY = scaleY"))
        assertTrue(morph.contains("this.translationX = translationX"))
        assertTrue(morph.contains("this.translationY = translationY"))
        assertTrue(morph.contains("dismissOnBackPress = false"))
        assertTrue(morph.contains("dismissOnClickOutside = false"))
        assertTrue(morph.contains("val wasTap = maxTravelSquared <= slop * slop"))
    }
}
