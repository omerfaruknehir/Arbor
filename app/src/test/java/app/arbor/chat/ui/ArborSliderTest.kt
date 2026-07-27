package app.arbor.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArborSliderTest {
    @Test fun magneticForceLeavesValuesOutsideTheCaptureRadiusUntouched() {
        val result = applyMagneticSliderForce(.40f, 0f..1f, listOf(0f, .5f, 1f), attractionRadiusFraction = .03f)
        assertEquals(.40f, result.value, .0001f)
        assertNull(result.anchor)
        assertFalse(result.captured)
    }

    @Test fun magneticForceAddsContinuousResistanceWithoutHardSnapping() {
        val result = applyMagneticSliderForce(.48f, 0f..1f, listOf(.5f), attractionRadiusFraction = .04f)
        assertTrue(result.value > .48f)
        assertTrue(result.value < .5f)
        assertEquals(.5f, result.anchor!!, 0f)
        assertTrue(result.captured)
    }

    @Test fun liveMagnetismIsMonotonicAcrossAnAnchor() {
        val rawValues = (450..550).map { it / 1000f }
        val values = rawValues.map {
            applyMagneticSliderForce(it, 0f..1f, listOf(.5f), attractionRadiusFraction = .06f).value
        }
        values.zipWithNext().forEach { (a, b) -> assertTrue("$a > $b", b >= a) }
        assertEquals(.5f, values[50], 0f)
    }

    @Test fun releaseSnappingUsesOnlyTheSmallExplicitCore() {
        assertEquals(.5f, releaseSnapAnchor(.487f, 0f..1f, listOf(.5f), .018f, false)!!, 0f)
        assertNull(releaseSnapAnchor(.47f, 0f..1f, listOf(.5f), .018f, false))
        assertEquals(0f, releaseSnapAnchor(.12f, 0f..1f, listOf(0f, .5f, 1f), .018f, true)!!, 0f)
    }

    @Test fun discreteSliderAnchorsIncludeBothEndpointsAndAllSteps() {
        assertEquals(listOf(1f, 2f, 3f, 4f, 5f), sliderStepAnchors(1f..5f, 3))
        assertEquals(listOf(0f, .5f, 1f), sliderStepAnchors(0f..1f, 1))
    }

    @Test fun fastFlicksHaveAWeakerReleaseCaptureThanSlowDrags() {
        assertEquals(1f, magneticReleaseRadiusMultiplier(.2f), 0f)
        assertEquals(.70f, magneticReleaseRadiusMultiplier(1.5f), 0f)
        assertEquals(.40f, magneticReleaseRadiusMultiplier(-3f), 0f)
    }

    @Test fun thinkingSliderMovesContinuouslyAndSnapsOnlyOnRelease() {
        val chat = java.io.File("src/main/java/app/arbor/chat/ui/ChatScreen.kt").readText()
        val thinkingBlock = chat.substringAfter("private fun ThinkingComposerChip").substringBefore("private val ThinkingEffort.effortDescription")
        assertTrue(thinkingBlock.contains("snapPoints = options.indices.map(Int::toFloat)"))
        assertTrue(thinkingBlock.contains("liveMagnetism = false"))
        assertTrue(thinkingBlock.contains("snapToNearestOnRelease = false"))
        assertFalse(thinkingBlock.contains("steps = (options.size - 2)"))
        assertTrue(thinkingBlock.contains("var sliderValue by remember(options)"))
        assertFalse(thinkingBlock.contains("remember(options, selectedIndex, menu)"))
    }

    @Test fun implementationProvidesGestureTicksSnapAndSystemRespectingHaptics() {
        val slider = java.io.File("src/main/java/app/arbor/chat/ui/ArborSlider.kt").readText()
        val priority = java.io.File("src/main/java/app/arbor/chat/ui/HorizontalGesturePriority.kt").readText()
        val haptics = java.io.File("src/main/java/app/arbor/chat/ui/ArborHaptics.kt").readText()
        assertTrue(slider.contains("modifier.horizontalGesturePriority(enabled)"))
        assertTrue(priority.contains("fun Modifier.horizontalGesturePriority"))
        assertTrue(priority.contains("registry?.update(owner, coordinates.boundsInRoot())"))
        assertTrue(slider.contains("haptics.gestureStart()"))
        assertTrue(slider.contains("haptics.frequentTick()"))
        assertTrue(slider.contains("haptics.snap()"))
        assertTrue(haptics.contains("view.isHapticFeedbackEnabled"))
        assertTrue(haptics.contains("SEGMENT_TICK"))
        assertTrue(haptics.contains("CONFIRM"))
        assertTrue(haptics.contains("REJECT"))
    }
}
