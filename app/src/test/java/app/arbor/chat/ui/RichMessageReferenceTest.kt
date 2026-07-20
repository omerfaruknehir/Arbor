package app.arbor.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichMessageReferenceTest {
    @Test fun sourceNotationBecomesAnArborSourceLink() {
        val rendered = prepareReferenceMarkdown(
            "Claim [[source|Android docs|https://developer.android.com/guide]]",
        )
        assertTrue(rendered.contains("[Android docs](arbor-source://reference?target="))
        assertTrue(rendered.contains("https%3A%2F%2Fdeveloper.android.com%2Fguide"))
        assertFalse(rendered.contains("[[source|"))
    }

    @Test fun fileNotationBecomesAnArborFileLink() {
        val rendered = prepareReferenceMarkdown("See [[file|Build log|logs/build output.txt]]")
        assertTrue(rendered.contains("[Build log](arbor-file://reference?target="))
        assertTrue(rendered.contains("logs%2Fbuild%20output.txt"))
    }

    @Test fun longReferenceLabelsAreShortenedForCompactPills() {
        val rendered = prepareReferenceMarkdown(
            "[[source|This is an excessively long source label which should not make a huge pill|https://example.com]]",
        )
        assertTrue(rendered.contains("…](arbor-source://"))
        assertFalse(rendered.contains("excessively long source label which should not make a huge pill"))
    }

    @Test fun ordinaryMarkdownLinksArePreservedForPreviewInterception() {
        val link = "[Example](https://example.com/path)"
        assertTrue(prepareReferenceMarkdown(link).contains(link))
    }
    @Test fun markdownTablesAreSplitFromSurroundingText() {
        val segments = splitMarkdownTables(
            """Intro

| Name | Description |
| --- | --- |
| Arbor | A native Android chat client with a long description |

Outro""",
        )
        assertTrue(segments.any { !it.table && "Intro" in it.text })
        assertTrue(segments.any { it.table && "| Name | Description |" in it.text })
        assertTrue(segments.any { !it.table && "Outro" in it.text })
    }

    @Test fun tableBoundariesDoNotLeakBlankLinesIntoAdjacentBlocks() {
        val segments = splitMarkdownTables(
            """Intro

| Name | Status |
| --- | --- |
| Arbor | Ready |

Outro""",
        )
        assertEquals(
            listOf(
                "Intro",
                """| Name | Status |
| --- | --- |
| Arbor | Ready |""",
                "Outro",
            ),
            segments.map { it.text },
        )
        assertTrue(segments.none { it.text.startsWith('\n') || it.text.endsWith('\n') })
    }

    @Test fun escapedAndInlineCodePipesDoNotCreatePhantomColumns() {
        assertEquals(
            listOf(" Name ", " `a|b` ", " c\\|d "),
            splitMarkdownTableCells("| Name | `a|b` | c\\|d |"),
        )
        val segments = splitMarkdownTables(
            """| Name | Expression |
| --- | --- |
| Arbor | `left|right` |""",
        )
        assertEquals(1, segments.size)
        assertTrue(segments.single().table)
    }

    @Test fun wideTablesReceiveAWidthLargerThanTheViewport() {
        val width = estimateMarkdownTableWidthDp(
            """| Package | Very long explanation | Platform | Status |
| --- | --- | --- | --- |
| Arbor | This column intentionally contains enough text to require horizontal scrolling | Android | Ready |""",
            viewportDp = 360,
        )
        assertTrue(width > 360)
    }

    @Test fun streamingPartialTableRowStaysInsideTheTable() {
        val segments = splitMarkdownTables(
            """| Name | Status |
| --- | --- |
| Arbor""",
            streaming = true,
        )
        assertEquals(1, segments.size)
        assertTrue(segments.single().table)
        assertTrue(segments.single().text.lines().last().count { it == '|' } >= 3)
    }

    @Test fun partialStreamingRowsArePaddedToTheExpectedColumnCount() {
        val stabilized = stabilizeStreamingTableRow("Arbor | Ready", 3)
        assertEquals(3, splitMarkdownTableCells(stabilized!!)?.size)
    }

    @Test fun incrementalParserKeepsCompletedBlocksStable() {
        val parser = IncrementalRichTextParser()
        val first = parser.update("First paragraph.\n\nTail", streaming = true)
        assertEquals(2, first.size)
        assertFalse(first.first().liveTail)
        assertTrue(first.last().liveTail)

        val second = parser.update("First paragraph.\n\nTail grows", streaming = true)
        assertEquals(first.first().key, second.first().key)
        assertEquals(first.first().block, second.first().block)
        assertEquals(first.last().key, second.last().key)
    }

    @Test fun blankLinesInsideCodeFencesAreNotCommittedAsMarkdown() {
        val source = "```python\nprint('a')\n\nprint('b')"
        assertEquals(0, stableMarkdownPrefixLength(source))
        val block = parseBlocks(source, streaming = true).single() as RichBlock.Code
        assertFalse(block.complete)
        assertTrue("print('b')" in block.code)
    }

    @Test fun closingFenceCommitsTheCodeBlock() {
        val source = "```python\nprint('ok')\n```\n"
        assertEquals(source.length, stableMarkdownPrefixLength(source))
        val block = parseBlocks(source, streaming = true).single() as RichBlock.Code
        assertTrue(block.complete)
        assertEquals("python", block.language)
    }

    @Test fun streamingTableKeepsOneTailIdentityWhileCellsArrive() {
        val parser = IncrementalRichTextParser()
        val first = parser.update("| Name | Status |\n| --- | --- |\n| Arbor", streaming = true)
        val second = parser.update("| Name | Status |\n| --- | --- |\n| Arbor | Ready", streaming = true)
        assertEquals(first.single().key, second.single().key)
        assertTrue(first.single().block is RichBlock.Table)
        assertTrue(second.single().block is RichBlock.Table)
    }

}
