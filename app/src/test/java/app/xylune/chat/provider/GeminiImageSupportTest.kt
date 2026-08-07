package app.xylune.chat.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class GeminiImageSupportTest {
    @Test
    fun discoveryRecognizesCurrentGeminiImageFamilies() {
        val service = ModelDiscoveryService(oauth = null)
        val models = buildJsonArray {
            add(geminiModel("models/gemini-3.1-flash-image", "Gemini 3.1 Flash Image"))
            add(geminiModel("models/gemini-3.1-flash-lite-image-preview", "Gemini 3.1 Flash Lite Image Preview"))
            add(geminiModel("models/gemini-3-pro-image-preview", "Gemini 3 Pro Image Preview"))
            add(geminiModel("models/gemini-2.5-flash-image", "Gemini 2.5 Flash Image"))
            add(geminiModel("models/gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview"))
        }

        val parsed = service.parseGeminiModels(models).associateBy { it.id }

        assertTrue(parsed.getValue("gemini-3.1-flash-image").supportsImageGeneration == true)
        assertTrue(parsed.getValue("gemini-3.1-flash-lite-image-preview").supportsImageGeneration == true)
        assertTrue(parsed.getValue("gemini-3-pro-image-preview").supportsImageGeneration == true)
        assertTrue(parsed.getValue("gemini-2.5-flash-image").supportsImageGeneration == true)
        assertFalse(parsed.getValue("gemini-3.1-pro-preview").supportsImageGeneration == true)
    }

    @Test
    fun geminiImageStreamDecodesInlineImageAndCountsThinkingAsBillableOutput() {
        val provider = GeminiProvider()
        val state = GeminiProvider.GeminiStreamState()
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val payload = buildJsonObject {
            put("candidates", buildJsonArray {
                add(buildJsonObject {
                    put("finishReason", JsonPrimitive("STOP"))
                    put("content", buildJsonObject {
                        put("parts", buildJsonArray {
                            add(buildJsonObject {
                                put("inlineData", buildJsonObject {
                                    put("mimeType", JsonPrimitive("image/png"))
                                    put("data", JsonPrimitive(Base64.getEncoder().encodeToString(bytes)))
                                })
                            })
                        })
                    })
                })
            })
            put("usageMetadata", buildJsonObject {
                put("promptTokenCount", JsonPrimitive(120))
                put("cachedContentTokenCount", JsonPrimitive(20))
                put("candidatesTokenCount", JsonPrimitive(30))
                put("thoughtsTokenCount", JsonPrimitive(40))
                put("totalTokenCount", JsonPrimitive(190))
            })
        }.toString()

        val chunks = provider.parseChunks(payload, state)
        val chunk = chunks.single()

        assertEquals(120L, chunk.inputTokens)
        assertEquals(20L, chunk.cachedInputTokens)
        assertEquals(70L, chunk.outputTokens)
        assertEquals("STOP", chunk.finishReason)
        assertEquals(1, chunk.generatedImages.size)
        assertEquals("image/png", chunk.generatedImages.single().mimeType)
        assertArrayEquals(bytes, chunk.generatedImages.single().bytes)
    }

    @Test
    fun heuristicDoesNotMisclassifyOrdinaryGeminiModels() {
        val service = ModelDiscoveryService(oauth = null)
        assertTrue(service.geminiImageGenerationModelHeuristic("gemini-3.1-flash-image"))
        assertTrue(service.geminiImageGenerationModelHeuristic("gemini-3.1-flash-lite-image-preview"))
        assertTrue(service.geminiImageGenerationModelHeuristic("imagen-4.0-generate-001"))
        assertFalse(service.geminiImageGenerationModelHeuristic("gemini-3.1-flash-lite-preview"))
        assertFalse(service.geminiImageGenerationModelHeuristic("gemini-2.5-pro"))
    }

    private fun geminiModel(name: String, displayName: String): JsonObject = buildJsonObject {
        put("name", JsonPrimitive(name))
        put("displayName", JsonPrimitive(displayName))
        put("inputTokenLimit", JsonPrimitive(65_536))
        put("outputTokenLimit", JsonPrimitive(32_768))
        put("supportedGenerationMethods", JsonArray(listOf(JsonPrimitive("generateContent"))))
    }
}
