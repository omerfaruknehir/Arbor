package app.arbor.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScrollMathTest {
    @Test
    fun autoFollowStepIsPositiveBoundedAndMonotonic() {
        val small = calculateAutoFollowStepPx(8f, 1f / 60f, 48_000f)
        val medium = calculateAutoFollowStepPx(80f, 1f / 60f, 48_000f)
        val large = calculateAutoFollowStepPx(800f, 1f / 60f, 48_000f)
        assertTrue(small > 0f)
        assertTrue(medium > small)
        assertTrue(large >= medium)
        assertTrue(small <= 8f)
        assertTrue(medium <= 80f)
        assertTrue(large <= 800f + .001f) // speed cap: 48000 / 60
        assertTrue(large > medium * 6f) // distance response is intentionally non-linear
    }

    @Test
    fun autoFollowStepHandlesInvalidInputs() {
        assertEquals(0f, calculateAutoFollowStepPx(0f, 1f / 60f, 4_800f), 0f)
        assertEquals(0f, calculateAutoFollowStepPx(10f, 0f, 4_800f), 0f)
        assertEquals(0f, calculateAutoFollowStepPx(10f, 1f / 60f, 0f), 0f)
    }


    @Test
    fun offscreenSeekAcceleratesNonLinearlyWithDistanceAndTime() {
        val nearInitial = calculateAutoFollowSeekSpeedPxPerSecond(
            hiddenItemCount = 1,
            elapsedSeconds = 0f,
            minSpeedPxPerSecond = 6_000f,
            maxSpeedPxPerSecond = 72_000f,
        )
        val farInitial = calculateAutoFollowSeekSpeedPxPerSecond(
            hiddenItemCount = 8,
            elapsedSeconds = 0f,
            minSpeedPxPerSecond = 6_000f,
            maxSpeedPxPerSecond = 72_000f,
        )
        val nearAfterCatchUp = calculateAutoFollowSeekSpeedPxPerSecond(
            hiddenItemCount = 1,
            elapsedSeconds = 0.25f,
            minSpeedPxPerSecond = 6_000f,
            maxSpeedPxPerSecond = 72_000f,
        )

        assertTrue(nearInitial > 6_000f)
        assertTrue(farInitial > nearInitial)
        assertTrue(nearAfterCatchUp > nearInitial)
        assertTrue(farInitial <= 72_000f)
        assertTrue(nearAfterCatchUp <= 72_000f)
    }

    @Test
    fun offscreenSeekRejectsInvalidInputs() {
        assertEquals(0f, calculateAutoFollowSeekSpeedPxPerSecond(0, 0f, 6_000f, 72_000f), 0f)
        assertEquals(0f, calculateAutoFollowSeekSpeedPxPerSecond(1, -1f, 6_000f, 72_000f), 0f)
        assertEquals(0f, calculateAutoFollowSeekSpeedPxPerSecond(1, 0f, 72_000f, 6_000f), 0f)
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

    @Test
    fun streamingAnchorRestoresOnlyUnexpectedUpwardRegression() {
        assertTrue(shouldRestoreStreamingAnchor(previousItemIndex = 40, currentItemIndex = 0, userDragging = false))
        assertTrue(shouldRestoreStreamingAnchor(previousItemIndex = 40, currentItemIndex = 38, userDragging = false))
        assertTrue(!shouldRestoreStreamingAnchor(previousItemIndex = 40, currentItemIndex = 39, userDragging = false))
        assertTrue(!shouldRestoreStreamingAnchor(previousItemIndex = 40, currentItemIndex = 0, userDragging = true))
        assertTrue(!shouldRestoreStreamingAnchor(previousItemIndex = 40, currentItemIndex = 45, userDragging = false))
    }

}
