package app.xylune.chat.provider

import app.xylune.chat.data.DefaultCatalog
import app.xylune.chat.data.ModelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageModelCapabilitiesTest {
    private val openAi = DefaultCatalog.providers.single { it.id == "openai" }
    private val qwen = DefaultCatalog.providers.single { it.id == "qwen-cloud" }
    private val generic = DefaultCatalog.providers.single { it.id == "generic" }

    @Test
    fun `gpt image 2 supports editing and real progressive previews`() {
        val model = DefaultCatalog.models.single { it.providerId == "openai" && it.modelId == "gpt-image-2" }
        val capabilities = requireNotNull(imageModelCapabilities(openAi, model))

        assertEquals(ImageInputMode.OPTIONAL, capabilities.inputMode)
        assertEquals(16, capabilities.maxInputImages)
        assertTrue(capabilities.supportsProgressivePreview)
        assertEquals(ModelRequestType.IMAGE_GENERATION, ModelRequestPolicy.requestType(openAi, model))
    }

    @Test
    fun `qwen image 2 supports up to three edit references without fake partials`() {
        val model = imageModel("qwen-image-2.0")
        val capabilities = requireNotNull(imageModelCapabilities(qwen, model))

        assertEquals(ImageInputMode.OPTIONAL, capabilities.inputMode)
        assertEquals(3, capabilities.maxInputImages)
        assertFalse(capabilities.supportsProgressivePreview)
    }

    @Test
    fun `legacy qwen edit model requires a reference image`() {
        val model = imageModel("qwen-image-edit-plus")
        val capabilities = requireNotNull(imageModelCapabilities(qwen, model))

        assertEquals(ImageInputMode.REQUIRED, capabilities.inputMode)
        assertTrue(capabilities.supportsEditing)
    }

    @Test
    fun `generation only qwen image does not advertise edit input`() {
        val model = imageModel("qwen-image-plus")
        val capabilities = requireNotNull(imageModelCapabilities(qwen, model))

        assertEquals(ImageInputMode.NONE, capabilities.inputMode)
        assertEquals(0, capabilities.maxInputImages)
    }

    @Test
    fun `generic image endpoint stays conservative`() {
        val model = imageModel("custom-image", providerId = generic.id)
        val capabilities = requireNotNull(imageModelCapabilities(generic, model))

        assertEquals(ImageInputMode.NONE, capabilities.inputMode)
        assertFalse(capabilities.supportsProgressivePreview)
    }

    private fun imageModel(id: String, providerId: String = qwen.id) = ModelEntity(
        providerId = providerId,
        modelId = id,
        displayName = id,
        contextWindow = 32_000,
        maxOutputTokens = 1,
        inputCacheHitUsdPerMillion = 0.0,
        inputCacheMissUsdPerMillion = 0.0,
        outputUsdPerMillion = 0.0,
        supportsImageGeneration = true,
    )
}
