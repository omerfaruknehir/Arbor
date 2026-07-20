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
    @Test
    fun cardPinningAndCenteringUseTheSameScrollDirection() {
        assertEquals(18f, calculateCardViewportCorrectionPx(118f, 100f), 0f)
        assertEquals(-12f, calculateCardViewportCorrectionPx(88f, 100f), 0f)
        assertEquals(50f, calculateCenteredCardCorrectionPx(450f, 550f, 100f, 800f), 0f)
    }

    @Test
    fun onlyLargeExpandedCardsAreCenteredAfterCollapse() {
        assertTrue(shouldCenterCollapsedCard(550f, 900f))
        assertTrue(!shouldCenterCollapsedCard(400f, 900f))
    }

    @Test
    fun descendingPagingIsMappedToChronologicalUiOrder() {
        assertEquals(4, chronologicalSourceIndex(0, 5))
        assertEquals(0, chronologicalSourceIndex(4, 5))
        assertEquals(0, chronologicalUiIndex(4, 5))
        assertEquals(4, chronologicalUiIndex(0, 5))
    }

    @Test
    fun visibleViewportEndsAboveComposerAndBottomGutter() {
        assertEquals(700, calculateVisibleChatViewportEndPx(viewportEndPx = 1_000, obscuredBottomPx = 300))
        assertEquals(0, calculateVisibleChatViewportEndPx(viewportEndPx = 200, obscuredBottomPx = 300))
        assertEquals(1_000, calculateVisibleChatViewportEndPx(viewportEndPx = 1_000, obscuredBottomPx = -10))
    }

}
