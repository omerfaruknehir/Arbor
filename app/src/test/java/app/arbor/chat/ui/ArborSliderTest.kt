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

    @Test fun magneticForceFeelsLikeAStrongContinuousSpringWithoutTeleporting() {
        val result = applyMagneticSliderForce(0.46f, 0f..1f, listOf(0.5f), attractionRadiusFraction = 0.07f, pullStrength = 0.78f)
        assertTrue(result.value > 0.46f)
        assertTrue(result.value < 0.5f)
        assertTrue(result.value > 0.47f)
        assertEquals(0.5f, result.anchor!!, 0f)
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

    @Test fun releaseSnappingUsesAUsableExplicitSpringWell() {
        assertEquals(0.5f, releaseSnapAnchor(0.455f, 0f..1f, listOf(0.5f), 0.05f, false)!!, 0f)
        assertNull(releaseSnapAnchor(0.44f, 0f..1f, listOf(0.5f), 0.05f, false))
        assertEquals(0f, releaseSnapAnchor(0.12f, 0f..1f, listOf(0f, 0.5f, 1f), 0.05f, true)!!, 0f)
    }

    @Test fun boundedSnapLaneChoosesOnlyItsTwoEndpointsAndNeverCapturesSoftness() {
        val anchors = listOf(0f, .2f)
        val lane = 0f..0.2f
        assertEquals(0f, releaseSnapAnchor(.07f, 0f..1f, anchors, .25f, true, lane)!!, 0f)
        assertEquals(.2f, releaseSnapAnchor(.13f, 0f..1f, anchors, .25f, true, lane)!!, 0f)
        assertNull(releaseSnapAnchor(.2001f, 0f..1f, anchors, .25f, true, lane))
        assertNull(releaseSnapAnchor(.24f, 0f..1f, anchors, .25f, true, lane))

        val outside = applyMagneticSliderForce(
            rawValue = .24f,
            valueRange = 0f..1f,
            anchors = anchors,
            attractionRadiusFraction = .14f,
            pullStrength = .98f,
            snapRange = lane,
        )
        assertEquals(.24f, outside.value, 0f)
        assertNull(outside.anchor)

        val values = (0..200).map { it / 1000f }.map {
            applyMagneticSliderForce(
                rawValue = it,
                valueRange = 0f..1f,
                anchors = anchors,
                attractionRadiusFraction = .14f,
                pullStrength = .98f,
                snapRange = lane,
            ).value
        }
        values.zipWithNext().forEach { (a, b) -> assertTrue("$a > $b", b >= a) }
    }

    @Test fun explicitSnapDotsUseTheExactAnchorPositions() {
        assertEquals(listOf(0f, 0.2f, 1f), sliderAnchorFractions(0f..1f, listOf(0f, 0.2f, 1f)))
        assertEquals(listOf(0f, 0.5f, 1f), sliderAnchorFractions(10f..20f, listOf(10f, 15f, 20f)))
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
        assertTrue(thinkingBlock.contains("snapToNearestOnRelease = true"))
        assertTrue(thinkingBlock.contains("showSnapPointDots = true"))
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
        assertTrue(slider.contains("settleAnim.animateTo"))
        assertTrue(slider.contains("animationSpec = spring("))
        assertTrue(slider.contains("track = { sliderState ->"))
        assertTrue(slider.contains("SliderDefaults.Track("))
        assertTrue(slider.contains("Canvas(Modifier.fillMaxSize())"))
        assertTrue(slider.contains("visibleValueFraction"))
        assertTrue(haptics.contains("view.isHapticFeedbackEnabled"))
        assertTrue(haptics.contains("SEGMENT_TICK"))
        assertTrue(haptics.contains("CONFIRM"))
        assertTrue(haptics.contains("REJECT"))
    }
    @Test fun edgeSoftnessUsesHardEdgesWithoutTransitionPercentText() {
        val settings = java.io.File("src/main/java/app/arbor/chat/ui/SettingsScreen.kt").readText()
        val edgeBlock = settings
            .substringAfter("Text(\"Edge softness\"")
            .substringBefore("Text(\"Overlay opacity\"")
        assertTrue(edgeBlock.contains("\"Hard edges\""))
        assertFalse(edgeBlock.contains("Shape transition"))
        assertTrue(edgeBlock.contains("showSnapPointDots = true"))
        assertTrue(edgeBlock.contains("snapRange = CHROME_EDGE_SOFTNESS_ROUNDED_SNAP_POINT..CHROME_EDGE_SOFTNESS_FLAT_SNAP_POINT"))
        assertTrue(edgeBlock.contains("pullStrength = 0.98f"))
    }

    @Test fun continuousAppearanceAndOverlayControlsDoNotInventSnapPoints() {
        val settings = java.io.File("src/main/java/app/arbor/chat/ui/SettingsScreen.kt").readText()
        listOf(
            "value = chromeBlurStrength",
            "value = chromeOverlayOpacity",
            "value = chromeTopPanelHeightDp",
            "value = settings.performanceOverlayBackgroundOpacity",
            "value = settings.performanceOverlayTextOpacity",
        ).forEach { marker ->
            val block = settings.substringAfter(marker).substringBefore(")\n")
            assertFalse("Unexpected snap points after $marker", block.contains("snapPoints"))
        }
    }

}
