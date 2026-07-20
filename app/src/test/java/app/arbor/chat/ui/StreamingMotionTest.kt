package app.arbor.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingMotionTest {
    @Test fun largeAppendIsRevealedProgressively() {
        val target = "a".repeat(1_000)
        val first = nextStreamingTextFrame("", target)
        assertEquals(36, first.length)
        assertTrue(target.startsWith(first))
        assertTrue(first.length < target.length)
    }

    @Test fun smallAppendUsesTokenSizedMicroBatch() {
        assertEquals("hello strea", nextStreamingTextFrame("hello", "hello streaming"))
    }

    @Test fun nonAppendCorrectionIsAppliedImmediately() {
        assertEquals("replacement", nextStreamingTextFrame("old text", "replacement"))
    }
    @Test fun expensiveStreamingBlocksCanCatchUpInLargerBatches() {
        val target = "x".repeat(2_000)
        assertEquals(512, nextStreamingTextFrame("", target, maxStepChars = 512).length)
    }

    @Test fun finalBacklogStaysOnStreamingRenderPathUntilCaughtUp() {
        assertTrue(isStreamingRenderActive(providerStreaming = false, renderedText = "partial", targetText = "partial tail"))
        assertTrue(!isStreamingRenderActive(providerStreaming = false, renderedText = "done", targetText = "done"))
    }

}
