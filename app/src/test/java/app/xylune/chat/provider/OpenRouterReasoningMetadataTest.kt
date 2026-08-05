package app.xylune.chat.provider

import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.ProviderEntity
import app.xylune.chat.data.ProviderKind
import app.xylune.chat.data.ThinkingEffort
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OpenRouterReasoningMetadataTest {
    private val provider = ProviderEntity(
        id = "openrouter",
        displayName = "OpenRouter",
        kind = ProviderKind.OPENAI_COMPATIBLE,
        baseUrl = "https://openrouter.ai/api/v1",
    )

    @Test
    fun `authoritative effort list and mandatory state drive controls`() {
        val model = reasoningModel(mandatory = true)
        val options = supportedThinkingLevels(provider, model)

        assertFalse(options.any { !it.enabled })
        assertEquals(listOf(ThinkingEffort.LOW, ThinkingEffort.HIGH), options.mapNotNull { it.effort })
        assertEquals(ThinkingEffort.HIGH, defaultThinkingEffort(model, ThinkingEffort.MINIMAL))
    }

    @Test
    fun `OpenRouter receives unified reasoning object including explicit off`() {
        val providerClient = OpenAiCompatibleProvider()
        val optional = reasoningModel(mandatory = false)
        val disabled = providerClient.buildRequestBody(
            ChatRequest(
                provider = provider,
                model = optional,
                apiKey = "test",
                messages = emptyList(),
                maxOutputTokens = 2_000,
                thinkingEnabled = false,
            ),
        )
        assertEquals("none", disabled["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)

        val mandatory = providerClient.buildRequestBody(
            ChatRequest(
                provider = provider,
                model = reasoningModel(mandatory = true),
                apiKey = "test",
                messages = emptyList(),
                maxOutputTokens = 2_000,
                thinkingEnabled = false,
                thinkingEffort = ThinkingEffort.MINIMAL,
            ),
        )
        assertEquals("high", mandatory["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
    }

    @Test
    fun `OpenRouter image responses preserve their declared media type`() {
        val root = Json.parseToJsonElement(
            """{"data":[{"b64_json":"aGk=","media_type":"image/svg+xml"}]}""",
        ).jsonObject

        val image = OpenAiCompatibleProvider().parseImageResponse(root).single()

        assertEquals("image/svg+xml", image.mimeType)
        assertEquals("hi", image.bytes.decodeToString())
        assertEquals("generated-image-1.svg", image.displayName)
    }

    private fun reasoningModel(mandatory: Boolean) = ModelEntity(
        providerId = provider.id,
        modelId = "vendor/reasoner",
        displayName = "Reasoner",
        contextWindow = 128_000,
        maxOutputTokens = 16_384,
        inputCacheHitUsdPerMillion = 0.0,
        inputCacheMissUsdPerMillion = 1.0,
        outputUsdPerMillion = 2.0,
        supportsThinking = true,
        reasoningMetadataAvailable = true,
        reasoningEffortsCsv = "HIGH,LOW",
        reasoningDefaultEffort = "HIGH",
        reasoningDefaultEnabled = true,
        reasoningMandatory = mandatory,
        metadataSource = "OpenRouter",
    )
}
