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
