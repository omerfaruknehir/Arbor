package app.xylune.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichMessageReferenceTest {
    @Test fun sourceNotationBecomesAnXyluneSourceLink() {
        val rendered = prepareReferenceMarkdown(
            "Claim [[source|Android docs|https://developer.android.com/guide]]",
        )
        assertTrue(rendered.contains("[Android docs](xylune-source://reference?target="))
        assertTrue(rendered.contains("https%3A%2F%2Fdeveloper.android.com%2Fguide"))
        assertFalse(rendered.contains("[[source|"))
    }

    @Test fun fileNotationBecomesAnXyluneFileLink() {
        val rendered = prepareReferenceMarkdown("See [[file|Build log|logs/build output.txt]]")
        assertTrue(rendered.contains("[Build log](xylune-file://reference?target="))
        assertTrue(rendered.contains("logs%2Fbuild%20output.txt"))
    }

    @Test fun longReferenceLabelsAreShortenedForCompactPills() {
        val rendered = prepareReferenceMarkdown(
            "[[source|This is an excessively long source label which should not make a huge pill|https://example.com]]",
        )
        assertTrue(rendered.contains("…](xylune-source://"))
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
| Xylune | A native Android chat client with a long description |

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
| Xylune | Ready |

Outro""",
        )
        assertEquals(
            listOf(
                "Intro",
                """| Name | Status |
| --- | --- |
| Xylune | Ready |""",
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
| Xylune | `left|right` |""",
        )
        assertEquals(1, segments.size)
        assertTrue(segments.single().table)
    }

    @Test fun wideTablesReceiveAWidthLargerThanTheViewport() {
        val width = estimateMarkdownTableWidthDp(
            """| Package | Very long explanation | Platform | Status |
| --- | --- | --- | --- |
| Xylune | This column intentionally contains enough text to require horizontal scrolling | Android | Ready |""",
            viewportDp = 360,
        )
        assertTrue(width > 360)
    }

    @Test fun streamingPartialTableRowStaysInsideTheTable() {
        val segments = splitMarkdownTables(
            """| Name | Status |
| --- | --- |
| Xylune""",
            streaming = true,
        )
        assertEquals(1, segments.size)
        assertTrue(segments.single().table)
        assertTrue(segments.single().text.lines().last().count { it == '|' } >= 3)
    }

    @Test fun partialStreamingRowsArePaddedToTheExpectedColumnCount() {
        val stabilized = stabilizeStreamingTableRow("Xylune | Ready", 3)
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
        val first = parser.update("| Name | Status |\n| --- | --- |\n| Xylune", streaming = true)
        val second = parser.update("| Name | Status |\n| --- | --- |\n| Xylune | Ready", streaming = true)
        assertEquals(first.single().key, second.single().key)
        assertTrue(first.single().block is RichBlock.Table)
        assertTrue(second.single().block is RichBlock.Table)
    }

    @Test fun incrementalParserPromotesLateTableSyntaxAndKeepsFollowingMarkdown() {
        val parser = IncrementalRichTextParser()
        val headerOnly = parser.update(
            "Intro\n\n| Element | Purpose |\n",
            streaming = true,
        )
        assertTrue(headerOnly.last().block is RichBlock.Markdown)

        val completed = parser.update(
            "Intro\n\n| Element | Purpose |\n| --- | --- |\n| Header | Labels columns |\n\nBelow the table is still streaming.",
            streaming = true,
        )
        assertTrue(completed.any { it.block is RichBlock.Table })
        assertTrue(
            completed.any { block ->
                block.block is RichBlock.Markdown && "Below the table" in block.block.text
            },
        )
    }

    @Test fun tableCandidateDetectionStartsAfterTheSeparatorArrives() {
        assertFalse(containsMarkdownTableCandidate("| A | B |\n"))
        assertTrue(containsMarkdownTableCandidate("| A | B |\n| --- | --- |"))
    }

    @Test fun completedSmallTablesCanUseMarkwonButStreamingTablesUseTheSafeGrid() {
        val small = "| A | B |\n| --- | --- |\n| 1 | 2 |"
        // RichMessage routes every live table to StreamingTablePreviewText before
        // consulting this size helper. Completed small tables can still use Markwon.
        assertFalse(shouldUseLightweightTableRenderer(small, streaming = false))
    }

    @Test fun oversizedStreamingTablesUseTheBoundedRenderer() {
        val rows = buildString {
            append("| A | B |\n| --- | --- |\n")
            repeat(StreamingTablePreviewMaxLines + 1) { append("| ").append(it).append(" | value |\n") }
        }
        assertTrue(shouldUseLightweightTableRenderer(rows, streaming = true))
    }

    @Test fun oversizedCompletedTablesAvoidMarkwonTableLayout() {
        val rows = buildString {
            append("| A | B |\n| --- | --- |\n")
            repeat(CompletedTablePreviewMaxLines + 1) { append("| ").append(it).append(" | value |\n") }
        }
        assertTrue(shouldUseLightweightTableRenderer(rows, streaming = false))
    }

    @Test fun liveTablePreviewIsBoundedAndKeepsHeaderAndNewestRows() {
        val table = buildString {
            append("| Index | Value |\n| --- | --- |\n")
            repeat(200) { append("| ").append(it).append(" | row-").append(it).append(" |\n") }
        }
        val preview = boundedTablePreviewText(table, maxChars = 1_000, maxLines = 12)
        assertTrue(preview.startsWith("| Index | Value |\n| --- | --- |"))
        assertTrue("hidden from the inline preview" in preview)
        assertTrue("row-199" in preview)
        assertTrue(preview.length <= 1_000)
        assertTrue(preview.lines().size <= 12)
    }

    @Test fun tableDetectionIsBoundedButStillFindsANewTableAtTheTail() {
        val source = "x".repeat(8_192) +
            "\n| A | B |\n| --- | --- |"
        assertTrue(containsMarkdownTableCandidate(source))
    }

    @Test fun oversizedPreviewDoesNotNeedToEnumerateEveryGeneratedRow() {
        val huge = buildString {
            append("| A | B |\n| --- | --- |\n")
            repeat(20_000) { append("| ").append(it).append(" | value |\n") }
        }
        val preview = boundedTablePreviewText(huge, maxChars = 1_000, maxLines = 12)
        assertTrue(preview.length <= 1_000)
        assertTrue(preview.startsWith("| A | B |\n| --- | --- |"))
        assertTrue("| 19999 | value |" in preview)
    }

    @Test fun streamingTableGridRendersAlignedCellsInsteadOfRawMarkdown() {
        val markdown = "Intro\n\n| Name | Status |\n| --- | --- |\n| Xylune | Streaming |"
        val start = findFirstMarkdownTableStart(markdown)
        assertEquals(markdown.indexOf("| Name"), start)
        val rendered = renderStreamingTableGrid(markdown, startOffset = start ?: 0)
        assertTrue(rendered.text.startsWith("┌"))
        assertTrue("│ Name" in rendered.text)
        assertTrue("Xylune" in rendered.text)
        assertFalse("| --- |" in rendered.text)
    }

    @Test fun hugeStreamingTableGridKeepsHeaderAndNewestRowsBounded() {
        val table = buildString {
            append("| Index | Value |\n| --- | --- |\n")
            repeat(2_000) { append("| ").append(it).append(" | row-").append(it).append(" |\n") }
        }
        val rendered = renderStreamingTableGrid(table, maxChars = 1_200, maxLines = 14)
        assertTrue("Index" in rendered.text)
        assertTrue("row-1999" in rendered.text)
        assertTrue("omitted" in rendered.text)
        assertTrue(rendered.displayedRows <= 14)
    }

    @Test fun rendererFailureFallbackPreservesOrdinaryMarkdownSource() {
        val markdown = "**Xylune** keeps streaming."
        assertEquals(markdown, markdownRenderFallbackText(markdown))
    }

    @Test fun rendererFailureFallbackKeepsTablesVisualInsteadOfRawMarkdown() {
        val table = "| Name | Status |\n| --- | --- |\n| Xylune | Streaming"
        val fallback = markdownRenderFallbackText(table)
        assertTrue(fallback.startsWith("┌"))
        assertTrue("│ Name" in fallback)
        assertTrue("Xylune" in fallback)
        assertFalse("| --- |" in fallback)
    }

    @Test fun everyProgressiveTableFragmentCanUseTheNativeGridWithoutMarkwon() {
        val fragments = listOf(
            "| Name | Status |\n| --- | --- |\n|",
            "| Name | Status |\n| --- | --- |\n| Xylune",
            "| Name | Status |\n| --- | --- |\n| Xylune |",
            "| Name | Status |\n| --- | --- |\n| Xylune | Ready",
            "| Name | Status |\n| --- | --- |\n| Xylune | Ready |",
        )
        fragments.forEach { source ->
            val block = parseBlocks(source, streaming = true).single()
            assertTrue(block is RichBlock.Table)
            val rendered = renderStreamingTableGrid((block as RichBlock.Table).text)
            assertTrue(rendered.text.startsWith("┌"))
            assertFalse("| --- |" in rendered.text)
        }
    }

}
