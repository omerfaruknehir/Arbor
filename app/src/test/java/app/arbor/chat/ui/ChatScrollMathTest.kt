package app.arbor.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScrollMathTest {
    @Test
    fun autoFollowStepIsPositiveBoundedAndMonotonic() {
        val small = calculateAutoFollowStepPx(8f, 1f / 60f, 4_800f)
        val medium = calculateAutoFollowStepPx(80f, 1f / 60f, 4_800f)
        val large = calculateAutoFollowStepPx(800f, 1f / 60f, 4_800f)
        assertTrue(small > 0f)
        assertTrue(medium > small)
        assertTrue(large >= medium)
        assertTrue(small <= 8f)
        assertTrue(medium <= 80f)
        assertTrue(large <= 80f + .001f) // speed cap: 4800 / 60
    }

    @Test
    fun autoFollowStepHandlesInvalidInputs() {
        assertEquals(0f, calculateAutoFollowStepPx(0f, 1f / 60f, 4_800f), 0f)
        assertEquals(0f, calculateAutoFollowStepPx(10f, 0f, 4_800f), 0f)
        assertEquals(0f, calculateAutoFollowStepPx(10f, 1f / 60f, 0f), 0f)
    }

    @Test
    fun viewportCorrectionUsesDriftDirection() {
        assertEquals(24f, calculateViewportCorrectionDeltaPx(124, 100), 0f)
        assertEquals(-18f, calculateViewportCorrectionDeltaPx(82, 100), 0f)
        assertEquals(0f, calculateViewportCorrectionDeltaPx(100, 100), 0f)
    }
}
