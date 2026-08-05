package app.xylune.chat.provider

import app.xylune.chat.data.ThinkingEffort
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterModelDiscoveryTest {
    @Test
    fun `catalog metadata is parsed instead of guessed from model names`() {
        val payload = Json.parseToJsonElement(
            """
            {
              "data": [
                {
                  "id": "vendor/opaque-name",
                  "name": "Opaque Reasoner",
                  "description": "Authoritative metadata test",
                  "created": 1770000000,
                  "context_length": 200000,
                  "architecture": {
                    "input_modalities": ["text", "image", "file"],
                    "output_modalities": ["text"]
                  },
                  "supported_parameters": ["tools", "reasoning", "temperature"],
                  "top_provider": {"max_completion_tokens": 32000},
                  "pricing": {
                    "prompt": "0.0000015",
                    "completion": "0.000006",
                    "input_cache_read": "0.0000003"
                  },
                  "reasoning": {
                    "supported_efforts": ["low", "high", "xhigh"],
                    "default_effort": "high",
                    "default_enabled": true,
                    "supports_max_tokens": true,
                    "mandatory": false
                  }
                },
                {
                  "id": "vendor/embed-only",
                  "architecture": {
                    "input_modalities": ["text"],
                    "output_modalities": ["embeddings"]
                  }
                }
              ]
            }
            """.trimIndent(),
        ).jsonObject

        val models = ModelDiscoveryService(oauth = null).parseDataModels(
            payload["data"]!!.jsonArray,
            "https://openrouter.ai/api/v1",
        )

        assertEquals(1, models.size)
        val model = models.single()
        assertEquals("Opaque Reasoner", model.displayName)
        assertEquals(200_000, model.contextWindow)
        assertEquals(32_000, model.maxOutputTokens)
        assertEquals(1.5, model.inputCacheMissUsdPerMillion!!, 0.000001)
        assertEquals(6.0, model.outputUsdPerMillion!!, 0.000001)
        assertEquals(0.3, model.inputCacheHitUsdPerMillion!!, 0.000001)
        assertTrue(model.supportsVision == true)
        assertTrue(model.supportsFiles == true)
        assertTrue(model.supportsTools == true)
        assertTrue(model.supportsThinking == true)
        assertFalse(model.supportsImageGeneration == true)
        assertEquals(listOf(ThinkingEffort.LOW, ThinkingEffort.HIGH, ThinkingEffort.XHIGH), model.reasoningEfforts)
        assertEquals(ThinkingEffort.HIGH, model.reasoningDefaultEffort)
        assertTrue(model.reasoningDefaultEnabled)
        assertTrue(model.reasoningSupportsMaxTokens)
        assertEquals("OpenRouter", model.metadataSource)
    }
}
