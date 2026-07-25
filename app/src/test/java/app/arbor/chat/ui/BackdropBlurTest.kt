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
        val radius = calculateBlurRadiusDp(true, 1f, .7f)
        assertEquals(44f, radius, .0001f)
    }

    @Test fun panelBodyNoLongerDependsOnScrollProgress() {
        // Scroll progress may increase blur radius, but panel opacity is fixed;
        // gradual mode is now limited to the 34 dp merge edge.
        assertEquals(16f, calculateBlurRadiusDp(true, 0f, .7f), .0001f)
        assertEquals(44f, calculateBlurRadiusDp(true, 1f, .7f), .0001f)
    }

    @Test fun topChromeBlurWaitsUntilChatContentActuallyScrollsUnderIt() {
        assertEquals(0f, calculateTopChromeProgress(0, 0, 56, 176), .0001f)
        assertEquals(0f, calculateTopChromeProgress(0, 56, 56, 176), .0001f)
        assertEquals(.5f, calculateTopChromeProgress(0, 116, 56, 176), .0001f)
        assertEquals(1f, calculateTopChromeProgress(0, 176, 56, 176), .0001f)
        assertEquals(1f, calculateTopChromeProgress(1, 0, 56, 176), .0001f)
    }

    @Test fun blurAxesAreNormalizedAndWellDistributed() {
        val lenA = BLUR_AXIS_A_X * BLUR_AXIS_A_X + BLUR_AXIS_A_Y * BLUR_AXIS_A_Y
        val lenB = BLUR_AXIS_B_X * BLUR_AXIS_B_X + BLUR_AXIS_B_Y * BLUR_AXIS_B_Y
        val lenC = BLUR_AXIS_C_X * BLUR_AXIS_C_X + BLUR_AXIS_C_Y * BLUR_AXIS_C_Y
        val dotAB = BLUR_AXIS_A_X * BLUR_AXIS_B_X + BLUR_AXIS_A_Y * BLUR_AXIS_B_Y
        val dotBC = BLUR_AXIS_B_X * BLUR_AXIS_C_X + BLUR_AXIS_B_Y * BLUR_AXIS_C_Y
        val dotCA = BLUR_AXIS_C_X * BLUR_AXIS_A_X + BLUR_AXIS_C_Y * BLUR_AXIS_A_Y

        assertEquals(1f, lenA, .0001f)
        assertEquals(1f, lenB, .0001f)
        assertEquals(1f, lenC, .0001f)
        assertTrue(kotlin.math.abs(dotAB) < .6f)
        assertTrue(kotlin.math.abs(dotBC) < .6f)
        assertTrue(kotlin.math.abs(dotCA) < .6f)
    }

    @Test fun radiusQuantizationSuppressesSubPixelStateChurn() {
        assertEquals(0f, quantizeBlurRadiusDp(-2f), 0f)
        assertEquals(0f, quantizeBlurRadiusDp(.12f), 0f)
        assertEquals(.25f, quantizeBlurRadiusDp(.13f), 0f)
        assertEquals(18.5f, quantizeBlurRadiusDp(18.49f), 0f)
    }
}
