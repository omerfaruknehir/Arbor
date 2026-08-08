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
    fun `dialog Back handler reads the dialog IME before dismissing`() {
        val source = repositoryFile("app/src/main/java/app/xylune/chat/ui/ReleaseDismissPopup.kt").readText()
        val dialog = source.substringAfter("fun XyluneAlertDialog(")
        val beforeMaterialDialog = dialog.substringBefore("MaterialAlertDialog(")

        assertFalse(beforeMaterialDialog.contains("XylunePopupBackHandler("))
        assertTrue(dialog.contains("confirmButton = {"))
        assertTrue(dialog.contains("XylunePopupBackHandler("))
        assertTrue(source.contains("val imeVisibleAtGestureStart = imeInsets.getBottom(density) > 0"))
        assertTrue(source.contains("keyboard?.hide()"))
        assertTrue(source.contains("focusManager.clearFocus(force = true)"))
        assertTrue(source.contains("dismissOnBackPress = false"))
    }

    @Test
    fun `source pills expand in place instead of opening detached popup`() {
        val sourceBar = repositoryFile("app/src/main/java/app/xylune/chat/ui/SourceReferenceBar.kt").readText()
        val preview = repositoryFile("app/src/main/java/app/xylune/chat/ui/LinkPreview.kt").readText()

        assertTrue(sourceBar.contains("var expandedTarget"))
        assertTrue(sourceBar.contains("animateContentSize("))
        assertTrue(sourceBar.contains("AnimatedVisibility("))
        assertTrue(sourceBar.contains("showHeader = false"))
        assertFalse(sourceBar.contains("AnchoredLinkPreview("))
        assertTrue(preview.contains("showHeader: Boolean = true"))
        assertTrue(preview.contains("if (showHeader)"))
    }
}
