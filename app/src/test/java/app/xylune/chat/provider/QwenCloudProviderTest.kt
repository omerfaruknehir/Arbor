package app.xylune.chat.provider

import app.xylune.chat.data.DefaultCatalog
import app.xylune.chat.data.MessageRole
import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.ProviderEntity
import app.xylune.chat.data.ProviderKind
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenCloudProviderTest {
    private val webSearch = NativeToolDefinition(
        name = "web_search",
        description = "Search the web",
        parametersJson = """{"type":"object","properties":{"query":{"type":"string"}}}""",
    )
    private val webFetch = NativeToolDefinition(
        name = "web_fetch",
        description = "Fetch a page",
        parametersJson = """{"type":"object","properties":{"url":{"type":"string"}}}""",
    )

    @Test
    fun defaultCatalogContainsAWorkingQwenCloudPreset() {
        val provider = DefaultCatalog.providers.single { it.id == "qwen-cloud" }
        assertEquals(ProviderKind.OPENAI_COMPATIBLE, provider.kind)
        assertTrue(ModelRequestPolicy.isQwenCloudBaseUrl(provider.baseUrl))
        assertNotNull(DefaultCatalog.models.singleOrNull { it.providerId == provider.id && it.modelId == "qwen3.7-plus" })
        assertNotNull(DefaultCatalog.models.singleOrNull { it.providerId == provider.id && it.modelId == "qwen3.7-max" })
        assertNotNull(DefaultCatalog.models.singleOrNull { it.providerId == provider.id && it.modelId == "qwen3.6-flash" })
    }

    @Test
    fun chatCompletionsUsesQwenThinkingAndCompletionParameters() {
        val request = request().copy(tools = emptyList())
        val body = OpenAiCompatibleProvider().buildRequestBody(request)

        assertTrue(body["enable_thinking"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("2048", body["max_completion_tokens"]!!.jsonPrimitive.content)
        assertFalse("max_tokens" in body)
        assertTrue(body["stream_options"]!!.jsonObject["include_usage"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun qwenNativeSearchUsesResponsesAndAddsWebExtractor() {
        val request = request()
        assertEquals(NativeWebSearchMode.RESPONSES, NativeWebSearch.mode(request))

        val body = ResponsesApiTransport(OkHttpClient()).buildRequestBody(request)
        val toolTypes = body["tools"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content }
        assertTrue("web_search" in toolTypes)
        assertTrue("web_extractor" in toolTypes)
        assertTrue(body["enable_thinking"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun qwenResponsesActionSourcesBecomeVisibleCitations() {
        val state = ResponsesApiStreamState("Qwen Cloud native search")
        val chunk = state.accept(
            """{"type":"response.completed","response":{"status":"completed","output":[{"type":"web_search_call","id":"ws_1","status":"completed","action":{"type":"search","query":"Xylune","sources":[{"url":"https://example.com/xylune","title":"Xylune source"}]}},{"type":"message","id":"m_1","role":"assistant","content":[{"type":"output_text","text":"Result"}]}],"usage":{"input_tokens":4,"output_tokens":2}}}""",
        )!!

        assertTrue(chunk.text.contains("Xylune source"))
        assertTrue(chunk.text.contains("https://example.com/xylune"))
        assertTrue(chunk.nativeProviderPayloadJson.contains("web_search_call"))
    }

    @Test
    fun sparseAlibabaCatalogGetsDocumentedThinkingAndVisionMetadata() {
        val merged = ModelRequestPolicy.mergeQwenCloudCatalog(
            discovered = listOf(
                DiscoveredModel(id = "glm-5.2", displayName = "GLM 5.2"),
                DiscoveredModel(id = "kimi-k2.6", displayName = "Kimi K2.6"),
                DiscoveredModel(id = "qwen3.6-plus", displayName = "Qwen3.6 Plus"),
                DiscoveredModel(id = "qwen-image-2.0", displayName = "Qwen Image 2.0"),
            ),
        )

        val glm = merged.single { it.id == "glm-5.2" }
        assertEquals(true, glm.supportsThinking)
        assertEquals(false, glm.supportsVision)
        assertTrue(glm.reasoningMetadataAvailable)
        assertTrue(glm.reasoningDefaultEnabled)

        val kimi = merged.single { it.id == "kimi-k2.6" }
        assertEquals(true, kimi.supportsThinking)
        assertEquals(true, kimi.supportsVision)
        assertEquals(true, kimi.supportsTools)
        assertFalse(kimi.reasoningDefaultEnabled)

        val qwen = merged.single { it.id == "qwen3.6-plus" }
        assertEquals(true, qwen.supportsThinking)
        assertEquals(true, qwen.supportsVision)
        assertEquals(true, qwen.supportsTools)
        assertEquals(1_000_000, qwen.contextWindow)
        assertEquals(65_536, qwen.maxOutputTokens)

        val image = merged.single { it.id == "qwen-image-2.0" }
        assertEquals(true, image.supportsImageGeneration)
        assertEquals(false, image.supportsThinking)
        assertEquals(false, image.supportsTools)
    }

    @Test
    fun qwenImageUsesDashScopeNativeSingleUserMessageSchema() {
        val provider = DefaultCatalog.providers.single { it.id == "qwen-cloud" }
        val imageModel = ModelEntity(
            providerId = provider.id,
            modelId = "qwen-image-2.0",
            displayName = "Qwen Image 2.0",
            contextWindow = 0,
            maxOutputTokens = 0,
            inputCacheHitUsdPerMillion = 0.0,
            inputCacheMissUsdPerMillion = 0.0,
            outputUsdPerMillion = 0.0,
            supportsImageGeneration = true,
        )
        val imageRequest = request().copy(
            provider = provider,
            model = imageModel,
            messages = listOf(
                InputMessage(MessageRole.SYSTEM, "This history must not be sent to Qwen-Image"),
                InputMessage(MessageRole.ASSISTANT, "Nor this"),
                InputMessage(MessageRole.USER, "A red moon above Antalya"),
            ),
            tools = emptyList(),
        )
        val transport = QwenCloudImageProvider(OpenAiCompatibleProvider())

        assertTrue(ModelRequestPolicy.isQwenCloudImageModel(provider, imageModel))
        assertEquals(
            "https://dashscope-intl.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
            transport.endpointFor(imageRequest),
        )

        val body = transport.buildRequestBody(imageRequest)
        assertFalse("prompt" in body)
        val messages = body["input"]!!.jsonObject["messages"]!!.jsonArray
        assertEquals(1, messages.size)
        val message = messages.single().jsonObject
        assertEquals("user", message["role"]!!.jsonPrimitive.content)
        val content = message["content"]!!.jsonArray
        assertEquals(1, content.size)
        assertEquals("A red moon above Antalya", content.single().jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("1", body["parameters"]!!.jsonObject["n"]!!.jsonPrimitive.content)
    }

    @Test
    fun qwenImageNativeResponseUrlsAreExtracted() {
        val root = ProviderJson.parseToJsonElement(
            """{"output":{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":[{"image":"https://example.com/result-1.png"},{"image":"https://example.com/result-2.png"}]}}]},"usage":{"image_count":2}}""",
        ).jsonObject
        val transport = QwenCloudImageProvider(OpenAiCompatibleProvider())

        assertEquals(
            listOf("https://example.com/result-1.png", "https://example.com/result-2.png"),
            transport.imageUrls(root),
        )
    }

    private fun request() = ChatRequest(
        provider = ProviderEntity(
            id = "qwen-cloud",
            displayName = "Qwen Cloud",
            kind = ProviderKind.OPENAI_COMPATIBLE,
            baseUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
        ),
        model = ModelEntity(
            providerId = "qwen-cloud",
            modelId = "qwen3.7-plus",
            displayName = "Qwen3.7 Plus",
            contextWindow = 1_000_000,
            maxOutputTokens = 65_536,
            inputCacheHitUsdPerMillion = 0.0,
            inputCacheMissUsdPerMillion = 0.0,
            outputUsdPerMillion = 0.0,
            supportsVision = true,
            supportsThinking = true,
            supportsTools = true,
        ),
        apiKey = "test-key",
        messages = listOf(InputMessage(MessageRole.USER, "Search for Xylune")),
        maxOutputTokens = 2_048,
        thinkingEnabled = true,
        tools = listOf(webSearch, webFetch),
    )
}
