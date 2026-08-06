package app.xylune.chat.provider

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
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeWebSearchProviderTest {
    private val webSearch = NativeToolDefinition(
        name = "web_search",
        description = "Search the web",
        parametersJson = """{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}""",
    )
    private val webFetch = NativeToolDefinition(
        name = "web_fetch",
        description = "Fetch a page",
        parametersJson = """{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}""",
    )
    private val python = NativeToolDefinition(
        name = "python",
        description = "Run Python",
        parametersJson = """{"type":"object","properties":{"code":{"type":"string"}},"required":["code"]}""",
    )

    @Test
    fun routesOnlyKnownNativeProviderModelPairs() {
        assertEquals(
            NativeWebSearchMode.RESPONSES,
            request("deepseek", "deepseek-v4-flash", "https://api.deepseek.com").let(NativeWebSearch::mode),
        )
        assertEquals(
            NativeWebSearchMode.NONE,
            request("deepseek", "deepseek-v4-pro", "https://api.deepseek.com").let(NativeWebSearch::mode),
        )
        assertEquals(
            NativeWebSearchMode.ANTHROPIC,
            request("anthropic", "claude-sonnet-4-20250514", "https://api.anthropic.com/v1", ProviderKind.ANTHROPIC)
                .let(NativeWebSearch::mode),
        )
        assertEquals(
            NativeWebSearchMode.NONE,
            request("gemini", "gemini-2.5-pro", "https://generativelanguage.googleapis.com/v1beta", ProviderKind.GEMINI)
                .let(NativeWebSearch::mode),
        )
        assertEquals(
            NativeWebSearchMode.GEMINI,
            request("gemini", "gemini-3-pro", "https://generativelanguage.googleapis.com/v1beta", ProviderKind.GEMINI)
                .let(NativeWebSearch::mode),
        )
        assertEquals(
            NativeWebSearchMode.RESPONSES,
            request("openrouter", "openrouter/auto", "https://openrouter.ai/api/v1").let(NativeWebSearch::mode),
        )
    }

    @Test
    fun responsesRequestUsesServerSearchAndKeepsOtherClientTools() {
        val request = request(
            providerId = "deepseek",
            modelId = "deepseek-v4-flash",
            baseUrl = "https://api.deepseek.com",
        ).copy(tools = listOf(webSearch, webFetch, python))
        val body = ResponsesApiTransport(OkHttpClient()).buildRequestBody(request)
        val tools = body["tools"]!!.jsonArray

        assertEquals("web_search", tools.first().jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(2, tools.size)
        assertEquals("function", tools[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("python", tools[1].jsonObject["name"]!!.jsonPrimitive.content)
        assertTrue(body["stream"]!!.jsonPrimitive.content.toBoolean())
        assertFalse(body["store"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun openRouterUsesItsServerToolName() {
        val request = request(
            providerId = "openrouter",
            modelId = "openrouter/auto",
            baseUrl = "https://openrouter.ai/api/v1",
        )
        val body = ResponsesApiTransport(OkHttpClient()).buildRequestBody(request)

        assertEquals(
            "openrouter:web_search",
            body["tools"]!!.jsonArray.first().jsonObject["type"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun responsesReplaysProviderOwnedSearchItemsUnchanged() {
        val payload = """[{"type":"web_search_call","id":"ws_1","status":"completed","action":{"type":"search","query":"Xylune"}},{"type":"message","id":"msg_1","role":"assistant","content":[{"type":"output_text","text":"Found it"}]}]"""
        val request = request(
            providerId = "deepseek",
            modelId = "deepseek-v4-flash",
            baseUrl = "https://api.deepseek.com",
        ).copy(
            messages = listOf(
                InputMessage(MessageRole.USER, "Search for Xylune"),
                InputMessage(MessageRole.ASSISTANT, "Found it", nativeProviderPayloadJson = payload),
            ),
        )
        val input = ResponsesApiTransport(OkHttpClient()).buildRequestBody(request)["input"]!!.jsonArray

        assertTrue(input.any { item ->
            item.jsonObject["type"]?.jsonPrimitive?.content == "web_search_call" &&
                item.jsonObject["id"]?.jsonPrimitive?.content == "ws_1"
        })
    }

    @Test
    fun responsesStreamExposesSearchProgressAndCitationsWithoutClientExecution() {
        val state = ResponsesApiStreamState("DeepSeek native search")
        val searching = state.accept(
            """{"type":"response.web_search_call.searching","output_index":0,"item_id":"ws_1","action":{"type":"search","query":"Android 16"}}""",
        )
        val progress = searching!!.toolCallProgress.single()
        assertEquals("native_web_search", progress.name)
        assertTrue(progress.argumentsJson.contains("Android 16"))
        assertTrue(progress.argumentsJson.contains("DeepSeek native search"))
        assertFalse(progress.complete)
        assertTrue(searching.toolCalls.isEmpty())

        val completed = state.accept(
            """{"type":"response.completed","response":{"status":"completed","output":[{"type":"web_search_call","id":"ws_1","status":"completed","action":{"type":"search","query":"Android 16"}},{"type":"message","id":"msg_1","role":"assistant","content":[{"type":"output_text","text":"Android result","annotations":[{"type":"url_citation","url":"https://example.com/android","title":"Android source"}]}]}],"usage":{"input_tokens":12,"output_tokens":5,"input_tokens_details":{"cached_tokens":2}}}}""",
        )!!

        assertTrue(completed.text.contains("### Sources"))
        assertTrue(completed.text.contains("https://example.com/android"))
        assertTrue(completed.toolCalls.isEmpty())
        assertTrue(completed.nativeProviderPayloadJson.contains("web_search_call"))
        assertEquals(12L, completed.inputTokens)
        assertEquals(5L, completed.outputTokens)
        assertEquals(2L, completed.cachedInputTokens)
        assertEquals("stop", completed.finishReason)
    }

    private fun request(
        providerId: String,
        modelId: String,
        baseUrl: String,
        kind: ProviderKind = ProviderKind.OPENAI_COMPATIBLE,
    ) = ChatRequest(
        provider = ProviderEntity(
            id = providerId,
            displayName = providerId,
            kind = kind,
            baseUrl = baseUrl,
        ),
        model = ModelEntity(
            providerId = providerId,
            modelId = modelId,
            displayName = modelId,
            contextWindow = 128_000,
            maxOutputTokens = 16_384,
            inputCacheHitUsdPerMillion = 0.0,
            inputCacheMissUsdPerMillion = 0.0,
            outputUsdPerMillion = 0.0,
            supportsThinking = true,
            supportsTools = true,
        ),
        apiKey = "test-key",
        messages = listOf(InputMessage(MessageRole.USER, "Search")),
        maxOutputTokens = 2_048,
        thinkingEnabled = true,
        tools = listOf(webSearch, webFetch),
    )
}
