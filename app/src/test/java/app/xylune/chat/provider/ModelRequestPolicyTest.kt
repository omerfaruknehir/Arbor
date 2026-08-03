package app.xylune.chat.provider

import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.ProviderEntity
import app.xylune.chat.data.ProviderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRequestPolicyTest {
    private val official = ProviderEntity(
        id = "openai",
        displayName = "OpenAI API",
        kind = ProviderKind.OPENAI_COMPATIBLE,
        baseUrl = "https://api.openai.com/v1",
    )

    @Test
    fun officialOpenAiImagesAreMergedIntoExistingCatalog() {
        val existing = listOf(DiscoveredModel("gpt-4.1", "GPT-4.1"))
        val merged = ModelRequestPolicy.mergeOfficialOpenAiCatalog(official.baseUrl, existing)

        assertTrue(merged.any { it.id == "gpt-4.1" })
        assertTrue(merged.any { it.id == "gpt-image-1" && it.supportsImageGeneration == true })
        assertTrue(merged.any { it.id == "gpt-image-1-mini" && it.supportsImageGeneration == true })
    }

    @Test
    fun customCatalogIsNotMutatedByOpenAiHeuristics() {
        val custom = listOf(DiscoveredModel("local-image", "Local Image"))
        val merged = ModelRequestPolicy.mergeOfficialOpenAiCatalog("https://models.example/v1", custom)
        assertEquals(custom, merged)
    }

    @Test
    fun officialImageAndChatModelsChooseCorrectEndpoints() {
        val image = model("gpt-image-1", image = false)
        val chat = model("gpt-4.1", image = true)

        assertEquals(ModelRequestType.IMAGE_GENERATION, ModelRequestPolicy.requestType(official, image))
        assertEquals("https://api.openai.com/v1/images/generations", ModelRequestPolicy.endpoint(official, image))
        assertEquals(ModelRequestType.CHAT, ModelRequestPolicy.requestType(official, chat))
        assertEquals("https://api.openai.com/v1/chat/completions", ModelRequestPolicy.endpoint(official, chat))
    }

    @Test
    fun customOpenAiCompatibleProviderUsesCompactPersistedRequestType() {
        val custom = ProviderEntity(
            id = "provider-custom",
            displayName = "Custom",
            kind = ProviderKind.OPENAI_COMPATIBLE,
            baseUrl = "https://models.example/v1",
        )
        assertTrue(ModelRequestPolicy.usesManualRequestType(custom))
        assertEquals(ModelRequestType.IMAGE_GENERATION, ModelRequestPolicy.requestType(custom, model("paint", image = true, providerId = custom.id)))
        assertEquals(ModelRequestType.CHAT, ModelRequestPolicy.requestType(custom, model("chat", image = false, providerId = custom.id)))
        assertFalse(ModelRequestPolicy.usesManualRequestType(official))
    }

    private fun model(id: String, image: Boolean, providerId: String = official.id) = ModelEntity(
        providerId = providerId,
        modelId = id,
        displayName = id,
        contextWindow = 128_000,
        maxOutputTokens = 16_384,
        inputCacheHitUsdPerMillion = 0.0,
        inputCacheMissUsdPerMillion = 0.0,
        outputUsdPerMillion = 0.0,
        supportsImageGeneration = image,
    )
}
