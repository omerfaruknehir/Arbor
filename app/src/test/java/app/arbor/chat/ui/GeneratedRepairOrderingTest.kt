package app.arbor.chat.ui

import app.arbor.chat.generated.GeneratedBlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedRepairOrderingTest {
    @Test fun incompleteStreamingFenceIsNotACompleteRepairCandidate() {
        val block = parseBlocks("before\n\n```arbor-chart\n{\"type\":", streaming = true).last() as RichBlock.Code
        assertFalse(block.complete)
    }

    @Test fun invalidBlockDoesNotHideFollowingTextTableOrParagraph() {
        val blocks = parseBlocks(
            """before

```arbor-chart
{"type":"unsupported","series":[]}
```

after

| A | B |
| --- | --- |
| 1 | 2 |

final paragraph
""", streaming = false)
        assertTrue(blocks.any { it is RichBlock.Code })
        assertTrue(blocks.any { it is RichBlock.Table })
        assertTrue(blocks.filterIsInstance<RichBlock.Markdown>().joinToString { it.text }.contains("final paragraph"))
        assertEquals("before", (blocks.first() as RichBlock.Markdown).text.trim())
    }

    @Test fun rendererPreparationExceptionBecomesBlockError() {
        val errors = generatedPreparationErrors(GeneratedBlockType.DIAGRAM, "flowchart TD")
        assertTrue(errors.any { it.phase == "renderer_preparation" })
    }
}
