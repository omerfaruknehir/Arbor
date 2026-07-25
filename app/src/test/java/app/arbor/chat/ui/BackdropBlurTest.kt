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

    @Test fun nativeGaussianRadiusIsScaledAndBounded() {
        assertEquals(0f, nativeBlurRadiusPx(16f, 0f), .0001f)
        assertEquals(34.56f, nativeBlurRadiusPx(16f, 3f), .001f)
        assertEquals(96f, nativeBlurRadiusPx(56f, 4f), .0001f)
    }

    @Test fun panelTintIsBoostedButNeverExceedsOpaque() {
        assertEquals(.442f, panelTintAlpha(.34f), .0001f)
        assertEquals(1f, panelTintAlpha(.9f), .0001f)
    }

    @Test fun topUsesOneCanonicalBlurAndTintRange() {
        assertEquals(0f to 128f, alignedTopBlurRange(128f, 900f))
    }

    @Test fun bottomUsesTheMeasuredPanelBounds() {
        assertEquals(
            620f to 760f,
            alignedBottomBlurRange(
                sourceTopInRootPx = 40f,
                panelStartInRootPx = 660f,
                panelEndInRootPx = 800f,
                fallbackEndInRootPx = Float.NaN,
                fallbackExtentPx = 208f,
                contentHeightPx = 900f,
            ),
        )
    }

    @Test fun radiusQuantizationSuppressesSubPixelStateChurn() {
        assertEquals(0f, quantizeBlurRadiusDp(-2f), 0f)
        assertEquals(0f, quantizeBlurRadiusDp(.12f), 0f)
        assertEquals(.25f, quantizeBlurRadiusDp(.13f), 0f)
        assertEquals(18.5f, quantizeBlurRadiusDp(18.49f), 0f)
    }
}
