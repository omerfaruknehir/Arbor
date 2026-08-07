package app.xylune.chat.provider

import app.xylune.chat.data.DefaultCatalog
import app.xylune.chat.data.MessageRole
import app.xylune.chat.data.ModelEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AlibabaRepointedPresetTest {
    @Test
    fun repointedQwenCloudPresetDoesNotLeakAlibabaProviderSemantics() = runBlocking {
        val preset = DefaultCatalog.providers.single { it.id == "qwen-cloud" }.copy(
            baseUrl = "https://example.invalid/v1",
        )
        val model = ModelEntity(
            providerId = preset.id,
            modelId = "some-compatible-model",
            displayName = "Some compatible model",
            contextWindow = 32_000,
            maxOutputTokens = 4_096,
            inputCacheHitUsdPerMillion = 0.0,
            inputCacheMissUsdPerMillion = 0.0,
            outputUsdPerMillion = 0.0,
            supportsThinking = true,
        )
        var forwarded: ChatRequest? = null
        val capture = object : ChatProvider {
            override suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) {
                forwarded = request
            }
        }
        val request = ChatRequest(
            provider = preset,
            model = model,
            apiKey = "test-key",
            messages = listOf(InputMessage(MessageRole.USER, "Hello")),
            maxOutputTokens = 1_024,
            thinkingEnabled = true,
        )

        AlibabaImageRoutingProvider(capture, capture).stream(request) {}

        val actual = requireNotNull(forwarded)
        assertEquals("custom-openai-compatible", actual.provider.id)
        assertEquals(preset.baseUrl, actual.provider.baseUrl)
    }
}
