package app.arbor.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArborSliderTest {
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
    fun implementationDelegatesGestureAndAccessibilityBehaviorToMaterial() {
        val slider = java.io.File("src/main/java/app/arbor/chat/ui/ArborSlider.kt").readText()

        assertTrue(slider.contains("import androidx.compose.material3.Slider"))
        assertTrue(slider.contains(".horizontalGesturePriority(enabled)"))
        assertTrue(slider.contains("ProgressBarRangeInfo"))
        assertTrue(slider.contains("collectIsDraggedAsState"))
        assertFalse(slider.contains("pointerInput("))
        assertFalse(slider.contains("Animatable"))
        assertFalse(slider.contains("VelocityTracker"))
        assertFalse(slider.contains("Magnetic"))
        assertFalse(slider.contains("snapPoints"))
    }

    @Test
    fun namedThinkingChoicesUseAReadableMenuInsteadOfASlider() {
        val chat = java.io.File("src/main/java/app/arbor/chat/ui/ChatScreen.kt").readText()
        val thinkingBlock = chat
            .substringAfter("private fun ThinkingComposerChip")
            .substringBefore("private val ThinkingEffort.effortDescription")

        assertTrue(thinkingBlock.contains("options.forEachIndexed"))
        assertTrue(thinkingBlock.contains("DropdownMenuItem("))
        assertTrue(thinkingBlock.contains("option.description"))
        assertTrue(thinkingBlock.contains("dismissOnClickOutside = true"))
        assertFalse(thinkingBlock.contains("ArborSlider("))
        assertFalse(thinkingBlock.contains("sliderValue"))
    }

    @Test
    fun edgeShapeAndSoftnessAreSeparateVisibleControls() {
        val settings = java.io.File("src/main/java/app/arbor/chat/ui/SettingsScreen.kt").readText()
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
        val settings = java.io.File("src/main/java/app/arbor/chat/ui/SettingsScreen.kt").readText()
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
}
