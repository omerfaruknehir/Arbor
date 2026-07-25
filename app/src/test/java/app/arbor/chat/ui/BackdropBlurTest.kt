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
    @Test fun topChromeBlurWaitsUntilChatContentActuallyScrollsUnderIt() {
        assertEquals(0f, calculateTopChromeProgress(0, 0, 56, 176), .0001f)
        assertEquals(0f, calculateTopChromeProgress(0, 56, 56, 176), .0001f)
        assertEquals(.5f, calculateTopChromeProgress(0, 116, 56, 176), .0001f)
        assertEquals(1f, calculateTopChromeProgress(0, 176, 56, 176), .0001f)
        assertEquals(1f, calculateTopChromeProgress(1, 0, 56, 176), .0001f)
    }
    @Test fun rotatedBlurAxesAreNormalizedOrthogonalAndNotScreenAligned() {
        val lenA = BLUR_AXIS_A_X * BLUR_AXIS_A_X + BLUR_AXIS_A_Y * BLUR_AXIS_A_Y
        val lenB = BLUR_AXIS_B_X * BLUR_AXIS_B_X + BLUR_AXIS_B_Y * BLUR_AXIS_B_Y
        val dot = BLUR_AXIS_A_X * BLUR_AXIS_B_X + BLUR_AXIS_A_Y * BLUR_AXIS_B_Y
        assertEquals(1f, lenA, .00001f)
        assertEquals(1f, lenB, .00001f)
        assertEquals(0f, dot, .00001f)
        assertTrue(kotlin.math.abs(BLUR_AXIS_A_X) > .1f)
        assertTrue(kotlin.math.abs(BLUR_AXIS_A_Y) > .1f)
        assertTrue(kotlin.math.abs(BLUR_AXIS_B_X) > .1f)
        assertTrue(kotlin.math.abs(BLUR_AXIS_B_Y) > .1f)
    }

    @Test fun radiusQuantizationSuppressesSubPixelStateChurn() {
        assertEquals(0f, quantizeBlurRadiusDp(-2f), 0f)
        assertEquals(0f, quantizeBlurRadiusDp(.12f), 0f)
        assertEquals(.25f, quantizeBlurRadiusDp(.13f), 0f)
        assertEquals(18.5f, quantizeBlurRadiusDp(18.49f), 0f)
    }

}

