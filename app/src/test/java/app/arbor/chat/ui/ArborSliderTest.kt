package app.arbor.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArborSliderTest {
    @Test fun magneticForceLeavesValuesOutsideTheCaptureRadiusUntouched() {
        val result = applyMagneticSliderForce(.40f, 0f..1f, listOf(0f, .5f, 1f), attractionRadiusFraction = .06f)
        assertEquals(.40f, result.value, .0001f)
        assertEquals(null, result.anchor)
        assertFalse(result.captured)
    }

    @Test fun magneticForcePullsSmoothlyBeforeTheHardSettleCore() {
        val result = applyMagneticSliderForce(.46f, 0f..1f, listOf(.5f), attractionRadiusFraction = .08f)
        assertTrue(result.value > .46f)
        assertTrue(result.value < .5f)
        assertEquals(.5f, result.anchor!!, 0f)
        assertTrue(result.captured)
    }

    @Test fun tinySettleCoreProducesAnExactAnchor() {
        val result = applyMagneticSliderForce(.505f, 0f..1f, listOf(.5f), settleRadiusFraction = .01f)
        assertEquals(.5f, result.value, 0f)
        assertEquals(.5f, result.anchor!!, 0f)
        assertTrue(result.captured)
    }

    @Test fun leavingZeroHasNoSixPercentDeadZone() {
        val result = applyMagneticSliderForce(
            rawValue = .059f,
            valueRange = 0f..1f,
            anchors = listOf(0f),
            attractionRadiusFraction = .06f,
            pullStrength = .82f,
            settleRadiusFraction = .012f,
        )
        assertTrue(result.value > 0f)
        assertTrue(result.value <= .059f)
    }

    @Test fun discreteSliderAnchorsIncludeBothEndpointsAndAllSteps() {
        assertEquals(listOf(1f, 2f, 3f, 4f, 5f), sliderStepAnchors(1f..5f, 3))
        assertEquals(listOf(0f, .5f, 1f), sliderStepAnchors(0f..1f, 1))
    }


    @Test fun fastFlicksHaveAWeakerReleaseCaptureThanSlowDrags() {
        assertEquals(1.35f, magneticReleaseRadiusMultiplier(.2f), 0f)
        assertEquals(.75f, magneticReleaseRadiusMultiplier(1.5f), 0f)
        assertEquals(.45f, magneticReleaseRadiusMultiplier(-3f), 0f)
    }

    @Test fun implementationProvidesGestureTicksSnapAndSystemRespectingHaptics() {
        val slider = java.io.File("src/main/java/app/arbor/chat/ui/ArborSlider.kt").readText()
        val haptics = java.io.File("src/main/java/app/arbor/chat/ui/ArborHaptics.kt").readText()
        assertTrue(slider.contains("haptics.gestureStart()"))
        assertTrue(slider.contains("haptics.frequentTick()"))
        assertTrue(slider.contains("haptics.snap()"))
        assertTrue(haptics.contains("view.isHapticFeedbackEnabled"))
        assertTrue(haptics.contains("SEGMENT_TICK"))
        assertTrue(haptics.contains("CONFIRM"))
        assertTrue(haptics.contains("REJECT"))
    }
}
