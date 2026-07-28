package app.arbor.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArborSliderTest {
    @Test fun anchorsAreNormalizedOnceAndRemainOrdered() {
        assertEquals(listOf(0f, .5f, 1f), normalizedSliderAnchors(0f..1f, listOf(1f, .5f, .5f, -1f, 2f)))
        assertEquals(listOf(1f, 2f, 3f, 4f, 5f), sliderStepAnchors(1f..5f, 3))
    }

    @Test fun magneticWellsNeverExceedHalfTheSmallestAnchorSpacing() {
        assertEquals(.1f, sliderMagneticRadius(0f..1f, listOf(0f, .2f), .4f), .0001f)
        assertEquals(.08f, sliderMagneticRadius(0f..1f, listOf(.5f), .08f), .0001f)
    }

    @Test fun magneticForceIsStrongContinuousAndDoesNotTeleport() {
        val result = applyMagneticSliderForce(
            rawValue = .46f,
            valueRange = 0f..1f,
            anchors = listOf(.5f),
            attractionRadiusFraction = .10f,
            pullStrength = .95f,
            maximumLivePull = .90f,
        )
        assertTrue(result.captured)
        assertEquals(.5f, result.anchor!!, 0f)
        assertTrue(result.value > .46f)
        assertTrue(result.value < .5f)
        assertTrue(result.value >= .49f)
    }

    @Test fun magneticMappingIsMonotonicAcrossAnAnchor() {
        val values = (400..600).map { it / 1000f }.map {
            applyMagneticSliderForce(
                rawValue = it,
                valueRange = 0f..1f,
                anchors = listOf(.5f),
                attractionRadiusFraction = .12f,
            ).value
        }
        values.zipWithNext().forEach { (first, second) ->
            assertTrue("$first > $second", second + .000001f >= first)
        }
    }

    @Test fun capturedAnchorUsesHysteresisInsteadOfFlappingAtTheMidpoint() {
        val anchors = listOf(0f, .2f)
        assertEquals(0f, selectMagneticSliderAnchor(.095f, 0f..1f, anchors, .20f)!!, 0f)
        assertEquals(0f, selectMagneticSliderAnchor(.105f, 0f..1f, anchors, .20f, currentAnchor = 0f)!!, 0f)
        assertEquals(.2f, selectMagneticSliderAnchor(.13f, 0f..1f, anchors, .20f, currentAnchor = 0f)!!, 0f)
    }

    @Test fun edgeSnapLaneChoosesOnlyRoundedOrFlatAndNeverCapturesSoftness() {
        val anchors = listOf(0f, .2f)
        val lane = 0f..0.2f
        assertEquals(0f, releaseSnapAnchor(.07f, 0f..1f, anchors, .25f, true, lane)!!, 0f)
        assertEquals(.2f, releaseSnapAnchor(.13f, 0f..1f, anchors, .25f, true, lane)!!, 0f)
        assertNull(releaseSnapAnchor(.2001f, 0f..1f, anchors, .25f, true, lane))
        assertNull(selectMagneticSliderAnchor(.24f, 0f..1f, anchors, .20f, snapRange = lane))
    }

    @Test fun releaseDecisionRestoresRawValueWhenLeavingAMagnetWithoutSnapping() {
        val decision = sliderReleaseDecision(
            rawValue = .30f,
            displayedValue = .27f,
            valueRange = 0f..1f,
            anchors = listOf(.2f),
            radiusFraction = .03f,
            alwaysNearest = false,
            snapRange = null,
            liveMagnetism = true,
        )
        assertNull(decision.target)
        assertTrue(decision.shouldRestoreRawValue)
    }

    @Test fun fastFlicksNarrowOnlyOptionalCaptureWells() {
        assertEquals(1f, magneticReleaseRadiusMultiplier(.2f), 0f)
        assertEquals(.78f, magneticReleaseRadiusMultiplier(1.5f), 0f)
        assertEquals(.55f, magneticReleaseRadiusMultiplier(-3f), 0f)
    }

    @Test fun trackTicksUseOnlyInteriorMaterialPositions() {
        assertEquals(listOf(0f, .2f, 1f), sliderAnchorFractions(0f..1f, listOf(0f, .2f, 1f)))
        assertEquals(listOf(.2f), sliderInteriorAnchorFractions(0f..1f, listOf(0f, .2f, 1f)))
        assertEquals(listOf(.25f, .5f, .75f), sliderInteriorAnchorFractions(0f..4f, listOf(0f, 1f, 2f, 3f, 4f)))
    }

    @Test fun thinkingSliderMovesContinuouslyAndSnapsOnlyOnRelease() {
        val chat = java.io.File("src/main/java/app/arbor/chat/ui/ChatScreen.kt").readText()
        val thinkingBlock = chat.substringAfter("private fun ThinkingComposerChip").substringBefore("private val ThinkingEffort.effortDescription")
        assertTrue(thinkingBlock.contains("snapPoints = options.indices.map(Int::toFloat)"))
        assertTrue(thinkingBlock.contains("liveMagnetism = false"))
        assertTrue(thinkingBlock.contains("snapToNearestOnRelease = true"))
        assertTrue(thinkingBlock.contains("showSnapPointDots = true"))
        assertTrue(thinkingBlock.contains("dismissOnClickOutside = true"))
        assertFalse(thinkingBlock.contains("steps = (options.size - 2)"))
    }

    @Test fun implementationHasOneStateMachineOneRendererAndNoHiddenMaterialSlider() {
        val slider = java.io.File("src/main/java/app/arbor/chat/ui/ArborSlider.kt").readText()
        val priority = java.io.File("src/main/java/app/arbor/chat/ui/HorizontalGesturePriority.kt").readText()
        val haptics = java.io.File("src/main/java/app/arbor/chat/ui/ArborHaptics.kt").readText()

        assertTrue(slider.contains(".horizontalGesturePriority(enabled)"))
        assertTrue(slider.contains("awaitEachGesture"))
        assertTrue(slider.contains("VelocityTracker()"))
        assertTrue(slider.contains("selectMagneticSliderAnchor"))
        assertTrue(slider.contains("MagneticExitRadiusMultiplier"))
        assertTrue(slider.contains("settleAnimation.animateTo"))
        assertTrue(slider.contains("settling = true\n        settleJob = scope.launch"))
        assertTrue(slider.contains("currentOnValueChange(target)"))
        assertTrue(slider.contains("drawArborSliderTrack"))
        assertTrue(slider.contains("trackHeightPx = with(density) { 16.dp.toPx() }"))
        assertTrue(slider.contains("sliderInteriorAnchorFractions"))
        assertFalse(slider.contains("androidx.compose.material3.Slider\n"))
        assertFalse(slider.contains("SliderDefaults.Track("))
        assertFalse(slider.contains("onValueChange(target)\n                            haptics"))

        assertTrue(priority.contains("registry?.update(owner, coordinates.boundsInRoot())"))
        assertTrue(haptics.contains("view.isHapticFeedbackEnabled"))
        assertTrue(haptics.contains("SEGMENT_TICK"))
        assertTrue(haptics.contains("CONFIRM"))
    }

    @Test fun edgeSoftnessKeepsTheTwoHardEdgeAnchorsAndNoFakePercent() {
        val settings = java.io.File("src/main/java/app/arbor/chat/ui/SettingsScreen.kt").readText()
        val edgeBlock = settings.substringAfter("Text(\"Edge softness\"").substringBefore("Text(\"Overlay opacity\"")
        assertTrue(edgeBlock.contains("\"Hard edges\""))
        assertFalse(edgeBlock.contains("Shape transition"))
        assertTrue(edgeBlock.contains("showSnapPointDots = true"))
        assertTrue(edgeBlock.contains("snapRange = CHROME_EDGE_SOFTNESS_ROUNDED_SNAP_POINT..CHROME_EDGE_SOFTNESS_FLAT_SNAP_POINT"))
        assertTrue(edgeBlock.contains("pullStrength = 0.97f"))
        assertTrue(edgeBlock.contains("maximumLivePull = 0.91f"))
    }

    @Test fun continuousControlsDoNotInventSnapPoints() {
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
