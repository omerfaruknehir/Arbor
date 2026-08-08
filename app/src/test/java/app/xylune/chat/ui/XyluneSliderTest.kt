package app.xylune.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XyluneSliderTest {
    @Test
    fun steppedValuesMapToStableMaterialIntervals() {
        assertEquals(0, sliderStepIndex(1f, 1f..5f, 3))
        assertEquals(1, sliderStepIndex(2f, 1f..5f, 3))
        assertEquals(2, sliderStepIndex(3f, 1f..5f, 3))
        assertEquals(3, sliderStepIndex(4f, 1f..5f, 3))
        assertEquals(4, sliderStepIndex(5f, 1f..5f, 3))
        assertEquals(0, sliderStepIndex(-20f, 1f..5f, 3))
        assertEquals(4, sliderStepIndex(20f, 1f..5f, 3))
    }

    @Test
    fun continuousAndDegenerateRangesDoNotInventState() {
        assertEquals(-1, sliderStepIndex(.7f, 0f..1f, 0))
        assertEquals(0, sliderStepIndex(2f, 2f..2f, 4))
    }

    @Test
    fun releaseSnapSupportsTwoOrMoreNamedAnchors() {
        assertEquals(0, sliderSnapIndex(.2f, 0f..1f, 2))
        assertEquals(1, sliderSnapIndex(.8f, 0f..1f, 2))
        assertEquals(0, sliderSnapIndex(0f, 0f..4f, 5))
        assertEquals(2, sliderSnapIndex(2.2f, 0f..4f, 5))
        assertEquals(4, sliderSnapIndex(4f, 0f..4f, 5))
    }

    @Test
    fun implementationDelegatesGestureAndAccessibilityBehaviorToMaterial() {
        val slider = java.io.File("src/main/java/app/xylune/chat/ui/XyluneSlider.kt").readText()

        assertTrue(slider.contains("import androidx.compose.material3.Slider"))
        assertTrue(slider.contains(".horizontalGesturePriority(enabled)"))
        assertTrue(slider.contains("ProgressBarRangeInfo"))
        assertTrue(slider.contains("collectIsDraggedAsState"))
        assertTrue(slider.contains(".background(thumbColor, CircleShape)"))
        assertTrue(slider.contains("steps = if (snapOnRelease) 0"))
        assertTrue(slider.contains("if (steps > 0 || snapOnRelease) haptics.snap()"))
        assertFalse(slider.contains("pointerInput("))
        assertFalse(slider.contains("Animatable"))
        assertFalse(slider.contains("VelocityTracker"))
        assertFalse(slider.contains("Magnetic"))
    }

    @Test
    fun thinkingUsesAContinuousTrackingSliderWithVisibleReleaseSnap() {
        val chat = java.io.File("src/main/java/app/xylune/chat/ui/ChatScreen.kt").readText()
        val thinkingBlock = chat
            .substringAfter("private fun ThinkingComposerChip")
            .substringBefore("private val ThinkingEffort.effortDescription")

        assertTrue(thinkingBlock.contains("XyluneSlider("))
        assertTrue(thinkingBlock.contains("snapOnRelease = true"))
        assertTrue(thinkingBlock.contains("spring(dampingRatio = .72f, stiffness = 430f)"))
        assertTrue(thinkingBlock.contains("Release to snap to the nearest supported level"))
        assertTrue(thinkingBlock.contains("preview?.description"))
        assertTrue(thinkingBlock.contains("dismissOnClickOutside = true"))
        assertFalse(thinkingBlock.contains("options.forEachIndexed"))
    }

    @Test
    fun edgeShapeAndSoftnessAreSeparateVisibleControls() {
        val settings = java.io.File("src/main/java/app/xylune/chat/ui/SettingsScreen.kt").readText()
        val edgeBlock = settings
            .substringAfter("Text(\"Panel shape\"")
            .substringBefore("label = \"Tint opacity\"")

        assertTrue(edgeBlock.contains("Text(\"Rounded\")"))
        assertTrue(edgeBlock.contains("Text(\"Flat\")"))
        assertTrue(edgeBlock.contains("label = \"Edge softness\""))
        assertTrue(edgeBlock.contains("chromeEdgeControlPositionForSoftness"))
        assertFalse(edgeBlock.contains("snapRange"))
        assertFalse(edgeBlock.contains("pullStrength"))
        assertFalse(edgeBlock.contains("maximumLivePull"))
    }

    @Test
    fun continuousControlsDoNotInventSnapPoints() {
        val settings = java.io.File("src/main/java/app/xylune/chat/ui/SettingsScreen.kt").readText()
        listOf(
            "value = chromeBlurStrength",
            "value = chromeOverlayOpacity",
            "value = settings.performanceOverlayBackgroundOpacity",
            "value = settings.performanceOverlayTextOpacity",
        ).forEach { marker ->
            val block = settings.substringAfter(marker).substringBefore(")\n")
            assertFalse("Unexpected snap points after $marker", block.contains("snapPoints"))
        }
    }

    @Test
    fun composerModesAndToolsLiveInThePlusMenuWithShortLabels() {
        val chat = java.io.File("src/main/java/app/xylune/chat/ui/ChatScreen.kt").readText()
        val composer = chat.substringAfter("private fun Composer(").substringBefore("private fun StagedAttachmentPreview")
        val promptArea = composer.substringBefore("if (plusMenu)")
        val plusMenu = composer.substringAfter("if (plusMenu)").substringBefore("if (sendMenu)")

        assertFalse(promptArea.contains("ThinkingComposerChip("))
        assertFalse(promptArea.contains("SearchComposerChip("))
        assertFalse(promptArea.contains("ToolComposerChip("))
        assertTrue(plusMenu.contains("Text("Modes & tools""))
        assertTrue(plusMenu.contains("ThinkingComposerChip("))
        assertTrue(plusMenu.contains("SearchComposerChip("))
        assertTrue(plusMenu.contains("ToolComposerChip("))
        assertFalse(chat.contains("Think ·"))
        assertFalse(chat.contains("searchSettings.activeLabel"))
        assertFalse(chat.contains("Tools ·"))
    }

    @Test
    fun opaqueTintExplainsWhyBlurCannotBeVisible() {
        val settings = java.io.File("src/main/java/app/xylune/chat/ui/SettingsScreen.kt").readText()

        assertTrue(settings.contains("\"Hidden by tint\""))
        assertTrue(settings.contains("\"100% is fully opaque and hides background blur.\""))
    }
}
