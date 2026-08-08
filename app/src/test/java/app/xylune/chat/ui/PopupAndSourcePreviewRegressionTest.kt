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
    fun `modal Back is keyboard first and tap away waits for release`() {
        val source = repositoryFile("app/src/main/java/app/xylune/chat/ui/ReleaseDismissPopup.kt").readText()
        val dialog = source.substringAfter("fun XyluneAlertDialog(")
        val dropdown = source.substringAfter("internal fun XyluneDropdownMenu(")

        assertTrue(source.contains("val imeVisibleAtGestureStart = imeInsets.getBottom(density) > 0"))
        assertTrue(source.contains("events.collect { }"))
        assertTrue(source.contains("keyboard?.hide()"))
        assertTrue(source.contains("focusManager.clearFocus(force = true)"))
        assertTrue(source.contains("BasicAlertDialog("))
        assertTrue(source.contains("dismissOnOutsideRelease("))
        assertTrue(source.contains("awaitFirstDown("))
        assertTrue(source.contains("if (event.changes.none { it.pressed }) break"))
        assertTrue(source.contains("val wasTap = maxTravelSquared <= slop * slop"))
        assertTrue(source.contains("startedInBackEdge"))
        assertTrue(dialog.contains("usePlatformDefaultWidth = false"))
        assertTrue(dialog.contains("dismissOnBackPress = false"))
        assertTrue(dialog.contains("dismissOnClickOutside = false"))

        // Small menus must still be dismissible by an ordinary tap away.
        assertTrue(dropdown.contains("dismissOnBackPress = true"))
        assertTrue(dropdown.contains("dismissOnClickOutside = dismissOnClickOutside"))
        assertFalse(dropdown.contains("ReleaseDismissOutsideLayer("))
    }

    @Test
    fun `source row never grows and invisible overlay cannot trap input`() {
        val sourceBar = repositoryFile("app/src/main/java/app/xylune/chat/ui/SourceReferenceBar.kt").readText()
        val morph = repositoryFile("app/src/main/java/app/xylune/chat/ui/SourceMorphPreview.kt").readText()

        assertTrue(sourceBar.contains("var pendingSource"))
        assertTrue(sourceBar.contains("MorphingSourcePreview("))
        assertTrue(sourceBar.contains("anchorBoundsInWindow = anchor"))
        assertTrue(sourceBar.contains("widthIn(max = 230.dp)"))
        assertTrue(sourceBar.contains("anchorBounds.width > 0 && anchorBounds.height > 0"))
        assertFalse(sourceBar.contains("animateContentSize("))
        assertFalse(sourceBar.contains("var expandedTarget"))

        assertTrue(morph.contains("LocalWindowInfo.current.containerSize"))
        assertTrue(morph.contains(".size(popupWidth, popupHeight)"))
        assertFalse(morph.contains(".fillMaxSize()"))
        assertTrue(morph.contains("anchor.width <= 0"))
        assertTrue(morph.contains("windowSize.width <= 0"))
        assertTrue(morph.contains("delay(750)"))
        assertTrue(morph.contains("cardSize.width <= 0 || cardSize.height <= 0"))
        assertTrue(morph.contains("!boundsReady || !startedInsideCard"))

        assertTrue(morph.contains("Animatable(0f)"))
        assertTrue(morph.contains("anchor.width.toFloat() / cardSize.width.toFloat()"))
        assertTrue(morph.contains("anchor.height.toFloat() / cardSize.height.toFloat()"))
        assertTrue(morph.contains("translationX = (anchorCenterX - targetCenterX)"))
        assertTrue(morph.contains("translationY = (anchorCenterY - targetCenterY)"))
        assertTrue(morph.contains("dismissOnClickOutside = false"))
        assertTrue(morph.contains("if (event.changes.none { it.pressed }) break"))
        assertTrue(morph.contains("!startedInBackEdge"))
    }
}
