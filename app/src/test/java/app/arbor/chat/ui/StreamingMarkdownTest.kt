package app.arbor.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingMarkdownTest {
    @Test
    fun stableBoundaryCommitsEveryCompletedBlock() {
        val source = "first\n\nsecond\n\nthird"
        assertEquals("first\n\nsecond\n\n".length, stableMarkdownCommitBoundary(source, 0))
    }

    @Test
    fun stableBoundaryOnlyMovesForwardFromCommittedPrefix() {
        val first = "first\n\nsecond\n\nthird"
        val committed = stableMarkdownCommitBoundary(first, 0)
        val appended = "$first\n\nfourth"
        val next = stableMarkdownCommitBoundary(appended, committed)
        assertTrue(next >= committed)
        assertEquals("first\n\nsecond\n\nthird\n\n".length, next)
    }

    @Test
    fun renderedPrefixTracksOnlyChangedSuffix() {
        assertEquals(6, commonRenderedPrefixLength("hello world", "hello there"))
        assertEquals(3, commonRenderedPrefixLength("abc", "abcdef"))
        assertEquals(0, commonRenderedPrefixLength("old", "new"))
    }

    @Test
    fun streamingBlockParserPromotesAFencedBlockOnce() {
        val parser = StreamingRichBlockParser()

        val opening = parser.update("Before\n\n```kotlin\nval x = 1", streaming = true)
        assertEquals(2, opening.size)
        assertEquals("Before\n\n", (opening[0] as RichBlock.Markdown).text)
        assertEquals("kotlin", (opening[1] as RichBlock.Code).language)
        assertEquals("val x = 1", (opening[1] as RichBlock.Code).code)

        val closed = parser.update("Before\n\n```kotlin\nval x = 1\n```\nAfter", streaming = true)
        assertEquals(3, closed.size)
        assertEquals("val x = 1", (closed[1] as RichBlock.Code).code)
        assertEquals("After", (closed[2] as RichBlock.Markdown).text)

        val appended = parser.update("Before\n\n```kotlin\nval x = 1\n```\nAfter text", streaming = true)
        assertEquals(3, appended.size)
        assertEquals("val x = 1", (appended[1] as RichBlock.Code).code)
        assertEquals("After text", (appended[2] as RichBlock.Markdown).text)
    }

    @Test
    fun finalPartialClosingFenceIsRecognized() {
        val parser = StreamingRichBlockParser()
        parser.update("```text\nhello\n```", streaming = true)
        val finished = parser.update("```text\nhello\n```", streaming = false)
        assertEquals(1, finished.size)
        assertEquals("hello", (finished.single() as RichBlock.Code).code)
    }
}
