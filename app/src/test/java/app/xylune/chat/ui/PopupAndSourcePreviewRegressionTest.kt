package app.xylune.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PopupAndSourcePreviewRegressionTest {
    private fun repositoryFile(path: String): File = sequenceOf(File(path), File("..", path))
        .firstOrNull(File::isFile)
        ?: error("Could not locate repository file: $path")

    @Test
    fun `dialog Back handler keeps IME gesture from dismissing the modal`() {
        val source = repositoryFile("app/src/main/java/app/xylune/chat/ui/ReleaseDismissPopup.kt").readText()
        val dialog = source.substringAfter("fun XyluneAlertDialog(")
        val dropdown = source.substringAfter("internal fun XyluneDropdownMenu(")

        assertTrue(source.contains("val imeVisibleAtGestureStart = imeInsets.getBottom(density) > 0"))
        assertTrue(source.contains("events.collect { }"))
        assertTrue(source.contains("keyboard?.hide()"))
        assertTrue(source.contains("focusManager.clearFocus(force = true)"))
        assertTrue(dialog.contains("dismissOnBackPress = false"))
        assertTrue(dialog.contains("dismissOnClickOutside = false"))
        assertTrue(source.contains("startedInBackEdge"))
        assertTrue(source.contains("dismissOnOutsideRelease"))
        assertTrue(dropdown.contains("ReleaseDismissOutsideLayer("))
        assertTrue(dropdown.contains("dismissOnBackPress = false"))
        assertTrue(dropdown.contains("dismissOnClickOutside = false"))
    }

    @Test
    fun `source pill stays fixed and opens a growing anchored popup`() {
        val sourceBar = repositoryFile("app/src/main/java/app/xylune/chat/ui/SourceReferenceBar.kt").readText()
        val preview = repositoryFile("app/src/main/java/app/xylune/chat/ui/LinkPreview.kt").readText()

        assertTrue(sourceBar.contains("var pendingReference"))
        assertTrue(sourceBar.contains("AnchoredLinkPreview("))
        assertTrue(sourceBar.contains("anchorBoundsInWindow = anchor"))
        assertTrue(sourceBar.contains("widthIn(max = 230.dp)"))
        assertFalse(sourceBar.contains("animateContentSize("))
        assertFalse(sourceBar.contains("var expandedTarget"))

        assertTrue(preview.contains("MutableTransitionState(false)"))
        assertTrue(preview.contains("scaleIn("))
        assertTrue(preview.contains("scaleOut("))
        assertTrue(preview.contains("ReleaseDismissOutsideLayer("))
        assertTrue(preview.contains("dismissOnBackPress = false"))
        assertTrue(preview.contains("dismissOnClickOutside = false"))
    }
}
