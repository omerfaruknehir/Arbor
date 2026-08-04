package app.xylune.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictiveNavigationMathTest {
    @Test
    fun completionDurationShrinksAsGestureApproachesDestination() {
        assertEquals(360, predictiveBackCompletionDurationMillis(0f))
        assertEquals(260, predictiveBackCompletionDurationMillis(.5f))
        assertEquals(160, predictiveBackCompletionDurationMillis(1f))
    }

    @Test
    fun predictiveVisualProgressIsClampedAndMonotonic() {
        assertEquals(0f, predictiveBackVisualProgress(-1f), .0001f)
        assertEquals(0f, predictiveBackVisualProgress(0f), .0001f)
        assertEquals(1f, predictiveBackVisualProgress(1f), .0001f)
        assertEquals(1f, predictiveBackVisualProgress(2f), .0001f)

        val values = (0..20).map { predictiveBackVisualProgress(it / 20f) }
        values.zipWithNext().forEach { (a, b) -> assertTrue(b >= a) }
    }

    @Test
    fun pageSlideTravelsHalfTheViewportWhileFadeFinishesTheTransition() {
        assertEquals(0f, pageSlideOffset(1080f, 0f), .0001f)
        assertEquals(270f, pageSlideOffset(1080f, .5f), .0001f)
        assertEquals(540f, pageSlideOffset(1080f, 1f), .0001f)
        assertEquals(540f, pageSlideOffset(1080f, 2f), .0001f)
    }

    @Test
    fun pageOpacityCrossfadesCompletelyWithoutAnEndCut() {
        assertEquals(1f, navigationSourceAlpha(0f), .0001f)
        assertEquals(.5f, navigationSourceAlpha(.5f), .0001f)
        assertEquals(0f, navigationSourceAlpha(1f), .0001f)
        assertEquals(0f, navigationDestinationAlpha(0f), .0001f)
        assertEquals(.5f, navigationDestinationAlpha(.5f), .0001f)
        assertEquals(1f, navigationDestinationAlpha(1f), .0001f)
    }

    @Test
    fun cancellationBeforeFlowCompletionRollsBack() {
        assertEquals(
            PredictiveCancellationResolution.ROLLBACK,
            predictiveCancellationResolution(commitStarted = false),
        )
    }

    @Test
    fun cancellationAfterFlowCompletionFinishesCommit() {
        assertEquals(
            PredictiveCancellationResolution.FINISH_COMMIT,
            predictiveCancellationResolution(commitStarted = true),
        )
    }
}
