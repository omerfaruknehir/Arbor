package app.xylune.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingMotionTest {
    @Test fun largeAppendIsRevealedProgressively() {
        val target = "a".repeat(1_000)
        val first = nextStreamingTextFrame("", target)
        assertEquals(32, first.length)
        assertTrue(target.startsWith(first))
        assertTrue(first.length < target.length)
    }

    @Test fun smallAppendUsesTokenSizedMicroBatch() {
        assertEquals("hello strea", nextStreamingTextFrame("hello", "hello streaming"))
    }

    @Test fun nonAppendCorrectionIsAppliedImmediately() {
        assertEquals("replacement", nextStreamingTextFrame("old text", "replacement"))
    }

    @Test fun configuredCapIsActuallyHonored() {
        val target = "x".repeat(2_000)
        assertEquals(12, nextStreamingTextFrame("", target, maxStepChars = 12).length)
    }

    @Test fun tableCadenceCanCommitOneCompleteSnapshot() {
        val target = "x".repeat(2_000)
        assertEquals(target, nextStreamingTextFrame("", target, maxStepChars = Int.MAX_VALUE))
    }

    @Test fun finalBacklogStaysOnStreamingRenderPathUntilCaughtUp() {
        assertTrue(isStreamingRenderActive(providerStreaming = false, renderedText = "partial", targetText = "partial tail"))
        assertTrue(!isStreamingRenderActive(providerStreaming = false, renderedText = "done", targetText = "done"))
    }
}
