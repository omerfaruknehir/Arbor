package app.arbor.chat.ui

import app.arbor.chat.data.ModelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPickerAndReasoningTest {
    @Test
    fun imageModelsRemainInNormalPicker() {
        val chat = model("gpt-4.1", "GPT-4.1", false)
        val image = model("gpt-image-1", "GPT Image 1", true)
        val picker = normalModelPickerModels(listOf(image, chat))

        assertEquals(2, picker.size)
        assertTrue(picker.any { it.modelId == "gpt-image-1" && it.supportsImageGeneration })
    }

    @Test
    fun reasoningMarkdownKeepsStructuredMarkdownAndFences() {
        val markdown = """# Heading

**bold** and `inline`

| A | B |
|---|---|
| 1 | 2 |

> quote

```python
print("must not run")
```

```arbor-widget
{"type":"text"}
```
"""
        val blocks = parseBlocks(markdown, streaming = false)
        assertTrue(blocks.any { it is RichBlock.Markdown && it.text.contains("# Heading") })
        assertTrue(blocks.any { it is RichBlock.Table })
        assertEquals(2, blocks.count { it is RichBlock.Code })
        assertTrue(blocks.filterIsInstance<RichBlock.Code>().all { it.complete })
    }

    @Test
    fun fencedReasoningCodeIsNeverExecutable() {
        assertFalse(shouldExecuteRichCodeBlock(displayOnly = true, complete = true))
        assertFalse(shouldExecuteRichCodeBlock(displayOnly = true, complete = false))
        assertTrue(shouldExecuteRichCodeBlock(displayOnly = false, complete = true))
    }

    private fun model(id: String, name: String, image: Boolean) = ModelEntity(
        providerId = "openai",
        modelId = id,
        displayName = name,
        contextWindow = 128_000,
        maxOutputTokens = 16_384,
        inputCacheHitUsdPerMillion = 0.0,
        inputCacheMissUsdPerMillion = 0.0,
        outputUsdPerMillion = 0.0,
        supportsImageGeneration = image,
    )
}
