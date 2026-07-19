package app.arbor.chat.ui

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
    fun outgoingPageStaysOpaqueUntilEndpointFadeAndEndsTransparent() {
        assertEquals(1f, predictiveBackOutgoingAlpha(0f), .0001f)
        assertEquals(1f, predictiveBackOutgoingAlpha(.62f), .0001f)
        assertEquals(0f, predictiveBackOutgoingAlpha(1f), .0001f)

        val values = (0..20).map { predictiveBackOutgoingAlpha(it / 20f) }
        values.zipWithNext().forEach { (a, b) -> assertTrue(b <= a) }
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
