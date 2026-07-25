package app.arbor.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropBlurTest {
    @Test fun progressIsClampedAndMonotonic() {
        val values = listOf(-1f, 0f, .25f, .5f, .75f, 1f, 2f).map(::arborBlurProgress)
        assertEquals(values.first(), values[1], 0f)
        assertEquals(values[values.lastIndex - 1], values.last(), 0f)
        values.zipWithNext().forEach { (a, b) -> assertTrue(b >= a) }
    }

    @Test fun endpointsAreStable() {
        assertEquals(0f, arborBlurProgress(0f), .0001f)
        assertEquals(1f, arborBlurProgress(1f), .0001f)
    }

    @Test fun enabledGradualBlurHasANonZeroBaselineBeforeScrolling() {
        assertEquals(16f, calculateBlurRadiusDp(true, 0f, .7f), .0001f)
        assertEquals(44f, calculateBlurRadiusDp(true, 1f, .7f), .0001f)
        assertEquals(0f, calculateBlurRadiusDp(false, 1f, 1f), .0001f)
    }

    @Test fun uniformPanelBlurUsesItsConfiguredMaximumImmediately() {
        assertEquals(44f, calculateBlurRadiusDp(true, 1f, .7f), .0001f)
    }

    @Test fun topPanelPreservesTheKnownCorrectSourceGeometry() {
        assertEquals(ArborPanelRange(0f, 128f), resolveTopPanelRange(900f, 128f))
        assertEquals(ArborPanelRange(0f, 90f), resolveTopPanelRange(90f, 128f))
    }

    @Test fun bottomPanelUsesTheMeasuredOverlayGeometryInsteadOfFixed208Dp() {
        val range = resolveBottomPanelRange(
            sourceTopInRootPx = 24f,
            panelStartInRootPx = 690f,
            panelEndInRootPx = 810f,
            sourceHeightPx = 900f,
            fallbackExtentPx = 208f,
        )
        assertEquals(ArborPanelRange(666f, 786f), range)
        assertEquals(120f, range.extentPx, .0001f)
    }

    @Test fun bottomPanelHasASafeFallbackUntilComposerIsMeasured() {
        assertEquals(
            ArborPanelRange(692f, 900f),
            resolveBottomPanelRange(0f, Float.NaN, Float.NaN, 900f, 208f),
        )
    }

    @Test fun sampleDensityWasRaisedWithoutChangingTheThreeGlassAxes() {
        assertEquals(15, BLUR_SAMPLES_PER_PASS)
        assertTrue(BLUR_SAMPLES_PER_PASS > 9)
        val lenA = BLUR_AXIS_A_X * BLUR_AXIS_A_X + BLUR_AXIS_A_Y * BLUR_AXIS_A_Y
        val lenB = BLUR_AXIS_B_X * BLUR_AXIS_B_X + BLUR_AXIS_B_Y * BLUR_AXIS_B_Y
        val lenC = BLUR_AXIS_C_X * BLUR_AXIS_C_X + BLUR_AXIS_C_Y * BLUR_AXIS_C_Y
        assertEquals(1f, lenA, .0001f)
        assertEquals(1f, lenB, .0001f)
        assertEquals(1f, lenC, .0001f)
    }

    @Test fun radiusQuantizationSuppressesSubPixelStateChurn() {
        assertEquals(0f, quantizeBlurRadiusDp(-2f), 0f)
        assertEquals(0f, quantizeBlurRadiusDp(.12f), 0f)
        assertEquals(.25f, quantizeBlurRadiusDp(.13f), 0f)
        assertEquals(18.5f, quantizeBlurRadiusDp(18.49f), 0f)
    }
}
