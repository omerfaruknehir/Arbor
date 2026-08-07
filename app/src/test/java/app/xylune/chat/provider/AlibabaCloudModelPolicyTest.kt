package app.xylune.chat.provider

import app.xylune.chat.data.DefaultCatalog
import app.xylune.chat.data.MessageRole
import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.ThinkingEffort
import app.xylune.chat.settings.WebSearchRoute
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AlibabaCloudModelPolicyTest {
    private val webSearch = NativeToolDefinition(
        name = "web_search",
        description = "Search the web",
        parametersJson = """{"type":"object","properties":{"query":{"type":"string"}}}""",
    )

    @Test
    fun glm52UsesCurrentModelSpecificLimitsAndEfforts() {
        val provider = DefaultCatalog.providers.single { it.id == "qwen-cloud" }
        val stale = ModelEntity(
            providerId = provider.id,
            modelId = "glm-5.2",
            displayName = "GLM 5.2",
            contextWindow = 198_000,
            maxOutputTokens = 32_000,
            inputCacheHitUsdPerMillion = 0.0,
            inputCacheMissUsdPerMillion = 0.0,
            outputUsdPerMillion = 0.0,
            supportsThinking = true,
            supportsTools = true,
            metadataSource = "Alibaba Cloud Model Studio",
        )

        val corrected = AlibabaCloudModelPolicy.correct(stale)

        assertEquals(1_048_576, corrected.contextWindow)
        assertEquals(131_072, corrected.maxOutputTokens)
        assertTrue(corrected.supportsThinking)
        assertTrue(corrected.supportsTools)
        assertFalse(corrected.supportsVision)
        assertEquals("MINIMAL,LOW,MEDIUM,HIGH,XHIGH,MAX", corrected.reasoningEffortsCsv)
        assertEquals("HIGH", corrected.reasoningDefaultEffort)
    }

    @Test
    fun qwenPlusAndFlashRegionalIdsKeepTheirDifferentToolCapabilities() {
        val provider = DefaultCatalog.providers.single { it.id == "qwen-cloud" }
        val globalPlus = AlibabaCloudModelPolicy.correct(model(provider.id, "qwen-plus"))
        val usPlus = AlibabaCloudModelPolicy.correct(model(provider.id, "qwen-plus-us"))
        val globalFlash = AlibabaCloudModelPolicy.correct(model(provider.id, "qwen-flash"))
        val usFlash = AlibabaCloudModelPolicy.correct(model(provider.id, "qwen-flash-us"))

        listOf(globalPlus, usPlus, globalFlash, usFlash).forEach { corrected ->
            assertEquals(1_000_000, corrected.contextWindow)
            assertEquals(32_768, corrected.maxOutputTokens)
            assertTrue(corrected.supportsThinking)
            assertFalse(corrected.supportsVision)
        }
        assertTrue(globalPlus.supportsTools)
        assertTrue(globalFlash.supportsTools)
        assertFalse(usPlus.supportsTools)
        assertFalse(usFlash.supportsTools)
    }

    @Test
    fun qwenRegionalSnapshotsDoNotInheritGlobalCapabilities() {
        val provider = DefaultCatalog.providers.single { it.id == "qwen-cloud" }
        val usPlus = AlibabaCloudModelPolicy.correct(model(provider.id, "qwen-plus-2025-12-01-us"))
        val usFlash = AlibabaCloudModelPolicy.correct(model(provider.id, "qwen-flash-2025-07-28-us"))

        assertFalse(usPlus.supportsTools)
        assertFalse(usFlash.supportsTools)
        assertEquals(1_000_000, usPlus.contextWindow)
        assertEquals(1_000_000, usFlash.contextWindow)
    }

    @Test
    fun responsesSearchUsesExactSupportedIdsInsteadOfFamilyPrefixes() {
        assertTrue(AlibabaCloudModelPolicy.supportsResponsesWebSearch("qwen3.7-plus", true))
        assertTrue(AlibabaCloudModelPolicy.supportsResponsesWebSearch("qwen3.7-plus-2026-05-26", true))
        assertTrue(AlibabaCloudModelPolicy.supportsResponsesWebSearch("qwen3.7-max-preview", true))
        assertTrue(AlibabaCloudModelPolicy.supportsResponsesWebSearch("qwen3.6-flash-2026-04-16", false))
        assertFalse(AlibabaCloudModelPolicy.supportsResponsesWebSearch("qwen3.7-plus-us", true))
        assertFalse(AlibabaCloudModelPolicy.supportsResponsesWebSearch("qwen3.7-plus-preview", true))
        assertFalse(AlibabaCloudModelPolicy.supportsResponsesWebSearch("qwen-plus", true))
        assertFalse(AlibabaCloudModelPolicy.supportsResponsesWebSearch("qwen-flash", true))
        assertFalse(AlibabaCloudModelPolicy.supportsResponsesWebSearch("glm-5.2", true))
        assertFalse(AlibabaCloudModelPolicy.supportsResponsesWebSearch("kimi-k2.6", true))
        assertFalse(AlibabaCloudModelPolicy.supportsResponsesWebSearch("qwen3-max", false))
        assertTrue(AlibabaCloudModelPolicy.supportsResponsesWebSearch("qwen3-max", true))
    }

    @Test
    fun unsupportedAlibabaNativeSearchIsRoutedToClientSearch() = runBlocking {
        var forwarded: ChatRequest? = null
        val capture = object : ChatProvider {
            override suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) {
                forwarded = request
            }
        }
        val router = AlibabaCloudRequestRoutingProvider(capture)
        val request = request("qwen3.7-plus-us").copy(
            webSearchRoute = WebSearchRoute.AUTO,
            tools = listOf(webSearch),
        )

        router.stream(request) {}

        val actual = requireNotNull(forwarded)
        assertEquals(WebSearchRoute.SEARCH_ENGINE, actual.webSearchRoute)
        assertEquals(NativeWebSearchMode.NONE, NativeWebSearch.mode(actual))
    }

    @Test
    fun nativeOnlyRejectsAlibabaModelWithoutNativeSearch() = runBlocking {
        val capture = object : ChatProvider {
            override suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) = Unit
        }
        val router = AlibabaCloudRequestRoutingProvider(capture)
        val request = request("glm-5.2").copy(
            webSearchRoute = WebSearchRoute.NATIVE_ONLY,
            tools = listOf(webSearch),
        )

        try {
            router.stream(request) {}
            fail("Expected unsupported native search to be rejected")
        } catch (error: ProviderProtocolException) {
            assertTrue(error.message.orEmpty().contains("does not expose native web search"))
        }
    }

    @Test
    fun miniMaxKeepsReasoningMetadataButSendsNoUnsupportedThinkingControl() = runBlocking {
        val provider = DefaultCatalog.providers.single { it.id == "qwen-cloud" }
        val model = ModelRequestPolicy.enrichQwenCloudStoredModel(
            model(provider.id, "MiniMax-M2.5"),
        )
        assertTrue(model.supportsThinking)
        assertTrue(model.reasoningMandatory)

        var forwarded: ChatRequest? = null
        val capture = object : ChatProvider {
            override suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) {
                forwarded = request
            }
        }
        AlibabaImageRoutingProvider(capture, capture).stream(
            request("MiniMax-M2.5").copy(model = model),
        ) {}

        val normalized = requireNotNull(forwarded)
        assertFalse(normalized.model.supportsThinking)
        val body = OpenAiCompatibleProvider().buildRequestBody(normalized)
        assertFalse("enable_thinking" in body)
        assertFalse("thinking" in body)
    }

    @Test
    fun requestedSupportedEffortWinsOverMetadataDefault() {
        val provider = DefaultCatalog.providers.single { it.id == "qwen-cloud" }
        val model = AlibabaCloudModelPolicy.correct(model(provider.id, "glm-5.2")).copy(
            reasoningDefaultEffort = "HIGH",
        )

        assertEquals(ThinkingEffort.MAX, defaultThinkingEffort(model, ThinkingEffort.MAX))
    }

    private fun request(modelId: String): ChatRequest {
        val provider = DefaultCatalog.providers.single { it.id == "qwen-cloud" }
        val model = AlibabaCloudModelPolicy.correct(model(provider.id, modelId)).copy(
            supportsTools = true,
        )
        return ChatRequest(
            provider = provider,
            model = model,
            apiKey = "test-key",
            messages = listOf(InputMessage(MessageRole.USER, "Hello")),
            maxOutputTokens = model.maxOutputTokens.coerceAtLeast(2_048),
            thinkingEnabled = true,
        )
    }

    private fun model(providerId: String, modelId: String) = ModelEntity(
        providerId = providerId,
        modelId = modelId,
        displayName = modelId,
        contextWindow = 0,
        maxOutputTokens = 32_000,
        inputCacheHitUsdPerMillion = 0.0,
        inputCacheMissUsdPerMillion = 0.0,
        outputUsdPerMillion = 0.0,
    )
}
