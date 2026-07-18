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
    @Test fun composerChromeWaitsUntilContentActuallyApproachesChrome() {
        assertEquals(0f, calculateComposerChromeProgress(0, 0, 56, 176), .0001f)
        assertEquals(0f, calculateComposerChromeProgress(0, 56, 56, 176), .0001f)
        assertEquals(.5f, calculateComposerChromeProgress(0, 116, 56, 176), .0001f)
        assertEquals(1f, calculateComposerChromeProgress(0, 176, 56, 176), .0001f)
        assertEquals(1f, calculateComposerChromeProgress(1, 0, 56, 176), .0001f)
    }

    @Test fun reverseChatHeaderTracksItsStableListAnchor() {
        // The anchor aligned with the viewport start is the expanded state.
        assertEquals(0f, calculateHeaderCollapseProgress(true, 0, 0, 68), .0001f)
        // Moving the anchor half of the collapse distance contracts halfway.
        assertEquals(.5f, calculateHeaderCollapseProgress(true, -34, 0, 68), .0001f)
        // Progress clamps after the compact height has been reached.
        assertEquals(1f, calculateHeaderCollapseProgress(true, -120, 0, 68), .0001f)
        // Once the anchor is outside the measured viewport, the header is compact.
        assertEquals(1f, calculateHeaderCollapseProgress(true, null, 0, 68), .0001f)
        // An empty conversation keeps the large-title state.
        assertEquals(0f, calculateHeaderCollapseProgress(false, null, 0, 68), .0001f)
        // Non-zero viewport starts are handled without hard-coded padding assumptions.
        assertEquals(.5f, calculateHeaderCollapseProgress(true, -24, 10, 68), .0001f)
    }

}
