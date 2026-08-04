package app.xylune.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictiveNavigationMathTest {
    @Test
    fun completionDurationShrinksAsGestureApproachesDestination() {
        assertEquals(150, predictiveBackCompletionDurationMillis(0f))
        assertEquals(115, predictiveBackCompletionDurationMillis(.5f))
        assertEquals(80, predictiveBackCompletionDurationMillis(1f))
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
    fun predictiveSourceScaleRemainsVisibleAndEndsAtNinetySixPercent() {
        assertEquals(1f, predictiveBackSourceScale(0f), .0001f)
        assertEquals(.96f, predictiveBackSourceScale(1f), .0001f)
        assertTrue(predictiveBackSourceScale(.5f) in .96f..1f)
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
