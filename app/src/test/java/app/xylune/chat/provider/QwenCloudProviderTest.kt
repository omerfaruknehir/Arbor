package app.xylune.chat.provider

import app.xylune.chat.data.AttachmentEntity
import app.xylune.chat.data.DefaultCatalog
import app.xylune.chat.data.MessageRole
import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.ProviderEntity
import app.xylune.chat.data.ProviderKind
import app.xylune.chat.data.ThinkingEffort
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QwenCloudProviderTest {
    private val webSearch = NativeToolDefinition(
        name = "web_search",
        description = "Search the web",
        parametersJson = """{"type":"object","properties":{"query":{"type":"string"}}}""",
    )
    private val webFetch = NativeToolDefinition(
        name = "web_fetch",
        description = "Fetch a page",
        parametersJson = """{"type":"object","properties":{"url":{"type":"string"},"selectors":{"type":"array","items":{"type":"string"}}}}""",
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
        assertFalse("reasoning_effort" in body)
        assertEquals("2048", body["max_completion_tokens"]!!.jsonPrimitive.content)
        assertFalse("max_tokens" in body)
        assertTrue(body["stream_options"]!!.jsonObject["include_usage"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun glm52UsesDocumentedEffortAndComplexToolStreamingParameters() {
        val provider = DefaultCatalog.providers.single { it.id == "qwen-cloud" }
        val model = ModelEntity(
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
            reasoningMetadataAvailable = true,
            reasoningEffortsCsv = "MINIMAL,LOW,MEDIUM,HIGH,XHIGH,MAX",
            reasoningDefaultEffort = "HIGH",
            reasoningDefaultEnabled = true,
            metadataSource = "Alibaba Cloud Model Studio",
        )
        val body = OpenAiCompatibleProvider().buildRequestBody(
            request().copy(
                provider = provider,
                model = model,
                thinkingEnabled = true,
                thinkingEffort = ThinkingEffort.MAX,
                tools = listOf(webFetch),
            ),
        )

        assertTrue(body["enable_thinking"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("max", body["reasoning_effort"]!!.jsonPrimitive.content)
        assertTrue(body["tool_stream"]!!.jsonPrimitive.content.toBoolean())
        assertTrue("tools" in body)
    }

    @Test
    fun miniMaxUsesItsOwnThinkingObjectInsteadOfQwenParameter() {
        val provider = DefaultCatalog.providers.single { it.id == "qwen-cloud" }
        val model = ModelRequestPolicy.enrichQwenCloudStoredModel(
            ModelEntity(
                providerId = provider.id,
                modelId = "MiniMax-M2.5",
                displayName = "MiniMax M2.5",
                contextWindow = 0,
                maxOutputTokens = 32_000,
                inputCacheHitUsdPerMillion = 0.0,
                inputCacheMissUsdPerMillion = 0.0,
                outputUsdPerMillion = 0.0,
            ),
        )
        val body = OpenAiCompatibleProvider().buildRequestBody(
            request().copy(provider = provider, model = model, tools = listOf(webSearch)),
        )

        assertTrue(model.reasoningMandatory)
        assertEquals(192_000, model.contextWindow)
        assertFalse("enable_thinking" in body)
        assertEquals("adaptive", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue("tools" in body)
        assertFalse("tool_stream" in body)
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
        assertFalse("reasoning" in body)
    }

    @Test
    fun alibabaThirdPartyModelsUseClientSearchInsteadOfQwenResponses() {
        val provider = DefaultCatalog.providers.single { it.id == "qwen-cloud" }
        listOf(
            ModelRequestPolicy.enrichQwenCloudStoredModel(model(provider, "glm-5.2")),
            ModelRequestPolicy.enrichQwenCloudStoredModel(model(provider, "kimi-k2.6")),
            ModelRequestPolicy.enrichQwenCloudStoredModel(model(provider, "MiniMax-M2.5")),
        ).forEach { model ->
            val thirdPartyRequest = request().copy(provider = provider, model = model)
            assertEquals(model.modelId, NativeWebSearchMode.NONE, NativeWebSearch.mode(thirdPartyRequest))
            assertTrue(NativeWebSearch.clientTools(thirdPartyRequest).any { it.name == "web_search" })
        }
    }

    @Test
    fun unsupportedQwenFamilyFallsBackToClientSearch() {
        val provider = DefaultCatalog.providers.single { it.id == "qwen-cloud" }
        val model = ModelRequestPolicy.enrichQwenCloudStoredModel(model(provider, "qwen3-vl-plus"))
        val qwenVlRequest = request().copy(provider = provider, model = model)

        assertEquals(NativeWebSearchMode.NONE, NativeWebSearch.mode(qwenVlRequest))
        assertTrue(NativeWebSearch.clientTools(qwenVlRequest).any { it.name == "web_search" })
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
                DiscoveredModel(id = "glm-5.1", displayName = "GLM 5.1"),
                DiscoveredModel(id = "kimi-k2.6", displayName = "Kimi K2.6"),
                DiscoveredModel(id = "Moonshot-Kimi-K2-Instruct", displayName = "Kimi K2 Instruct"),
                DiscoveredModel(id = "qwen3.6-plus", displayName = "Qwen3.6 Plus"),
                DiscoveredModel(id = "qwen-image-2.0", displayName = "Qwen Image 2.0"),
                DiscoveredModel(id = "qwen-image-plus", displayName = "Qwen Image Plus"),
                DiscoveredModel(id = "qwen-image-edit-plus", displayName = "Qwen Image Edit Plus"),
                DiscoveredModel(id = "MiniMax-M2.5", displayName = "MiniMax M2.5"),
                DiscoveredModel(id = "deepseek-r1-distill-qwen-32b", displayName = "DeepSeek R1 Distill"),
            ),
        )

        val glm = merged.single { it.id == "glm-5.2" }
        assertEquals(true, glm.supportsThinking)
        assertEquals(false, glm.supportsVision)
        assertEquals(true, glm.supportsTools)
        assertEquals(198_000, glm.contextWindow)
        assertTrue(glm.reasoningMetadataAvailable)
        assertEquals(
            listOf(
                ThinkingEffort.MINIMAL,
                ThinkingEffort.LOW,
                ThinkingEffort.MEDIUM,
                ThinkingEffort.HIGH,
                ThinkingEffort.XHIGH,
                ThinkingEffort.MAX,
            ),
            glm.reasoningEfforts,
        )
        assertEquals(ThinkingEffort.HIGH, glm.reasoningDefaultEffort)
        assertTrue(glm.reasoningDefaultEnabled)

        val glm51 = merged.single { it.id == "glm-5.1" }
        assertFalse(ThinkingEffort.MAX in glm51.reasoningEfforts)
        assertEquals(ThinkingEffort.XHIGH, glm51.reasoningEfforts.last())

        val kimi = merged.single { it.id == "kimi-k2.6" }
        assertEquals(true, kimi.supportsThinking)
        assertEquals(true, kimi.supportsVision)
        assertEquals(true, kimi.supportsTools)
        assertEquals(256_000, kimi.contextWindow)
        assertFalse(kimi.reasoningDefaultEnabled)
        assertTrue(kimi.reasoningEfforts.isEmpty())

        val kimiInstruct = merged.single { it.id == "Moonshot-Kimi-K2-Instruct" }
        assertEquals(false, kimiInstruct.supportsThinking)
        assertEquals(true, kimiInstruct.supportsTools)
        assertEquals(256_000, kimiInstruct.contextWindow)

        val qwen = merged.single { it.id == "qwen3.6-plus" }
        assertEquals(true, qwen.supportsThinking)
        assertEquals(true, qwen.supportsVision)
        assertEquals(true, qwen.supportsTools)
        assertEquals(1_000_000, qwen.contextWindow)
        assertEquals(65_536, qwen.maxOutputTokens)
        assertTrue(qwen.reasoningEfforts.isEmpty())

        val image = merged.single { it.id == "qwen-image-2.0" }
        assertEquals(true, image.supportsImageGeneration)
        assertEquals(false, image.supportsThinking)
        assertEquals(true, image.supportsVision)
        assertEquals(false, image.supportsTools)

        val generationOnlyImage = merged.single { it.id == "qwen-image-plus" }
        assertEquals(true, generationOnlyImage.supportsImageGeneration)
        assertEquals(false, generationOnlyImage.supportsVision)

        val editImage = merged.single { it.id == "qwen-image-edit-plus" }
        assertEquals(true, editImage.supportsImageGeneration)
        assertEquals(true, editImage.supportsVision)

        val minimax = merged.single { it.id == "MiniMax-M2.5" }
        assertEquals(true, minimax.supportsThinking)
        assertTrue(minimax.reasoningMandatory)
        assertEquals(true, minimax.supportsTools)
        assertEquals(192_000, minimax.contextWindow)
        assertTrue(minimax.reasoningEfforts.isEmpty())

        val distill = merged.single { it.id == "deepseek-r1-distill-qwen-32b" }
        assertEquals(true, distill.supportsThinking)
        assertTrue(distill.reasoningMandatory)
        assertEquals(false, distill.supportsTools)
        assertEquals(128_000, distill.contextWindow)
    }

    @Test
    fun providerManagedThinkingDoesNotInventReasoningEffortLevels() {
        val provider = DefaultCatalog.providers.single { it.id == "qwen-cloud" }
        val qwen = ModelRequestPolicy.enrichQwenCloudStoredModel(
            ModelEntity(
                providerId = provider.id,
                modelId = "qwen3.7-plus",
                displayName = "Qwen3.7 Plus",
                contextWindow = 1_000_000,
                maxOutputTokens = 65_536,
                inputCacheHitUsdPerMillion = 0.0,
                inputCacheMissUsdPerMillion = 0.0,
                outputUsdPerMillion = 0.0,
                supportsThinking = true,
                metadataSource = "Alibaba Cloud Model Studio",
            ),
        )

        val options = supportedThinkingLevels(provider, qwen)
        assertEquals(listOf("Off", "On"), options.map { it.label })
        assertTrue(options.filter { it.enabled }.all { it.effort == null })
    }

    @Test
    fun qwenVlAndMaxSnapshotsDoNotUseBroadFamilyGuesses() {
        val merged = ModelRequestPolicy.mergeQwenCloudCatalog(
            discovered = listOf(
                DiscoveredModel(id = "qwen3-vl-30b-a3b-instruct", displayName = "VL Instruct"),
                DiscoveredModel(id = "qwen3-vl-30b-a3b-thinking", displayName = "VL Thinking"),
                DiscoveredModel(id = "qwen3-vl-plus", displayName = "VL Plus"),
                DiscoveredModel(id = "qwen3.7-max", displayName = "Qwen3.7 Max"),
                DiscoveredModel(id = "qwen3.7-max-2026-06-08", displayName = "Qwen3.7 Max Jun 8"),
                DiscoveredModel(id = "qwen-plus", displayName = "Qwen Plus"),
            ),
        )

        val instruct = merged.single { it.id == "qwen3-vl-30b-a3b-instruct" }
        assertEquals(false, instruct.supportsThinking)
        assertEquals(true, instruct.supportsVision)

        val thinking = merged.single { it.id == "qwen3-vl-30b-a3b-thinking" }
        assertEquals(true, thinking.supportsThinking)
        assertTrue(thinking.reasoningMandatory)
        assertEquals(true, thinking.supportsVision)

        val vlPlus = merged.single { it.id == "qwen3-vl-plus" }
        assertEquals(true, vlPlus.supportsThinking)
        assertFalse(vlPlus.reasoningDefaultEnabled)

        assertEquals(false, merged.single { it.id == "qwen3.7-max" }.supportsVision)
        assertEquals(true, merged.single { it.id == "qwen3.7-max-2026-06-08" }.supportsVision)

        val qwenPlus = merged.single { it.id == "qwen-plus" }
        assertEquals(true, qwenPlus.supportsThinking)
        assertFalse(qwenPlus.reasoningDefaultEnabled)
    }

    @Test
    fun cachedQwenCloudRowsAreRepairedWithoutManualRefresh() {
        val stale = ModelEntity(
            providerId = "qwen-cloud",
            modelId = "glm-5.2",
            displayName = "GLM 5.2",
            contextWindow = 198_000,
            maxOutputTokens = 32_000,
            inputCacheHitUsdPerMillion = 0.0,
            inputCacheMissUsdPerMillion = 0.0,
            outputUsdPerMillion = 0.0,
            supportsThinking = false,
            supportsVision = true,
            supportsTools = false,
            metadataSource = "Alibaba Cloud Model Studio",
        )

        val repaired = ModelRequestPolicy.enrichQwenCloudStoredModel(stale)

        assertTrue(repaired.supportsThinking)
        assertFalse(repaired.supportsVision)
        assertTrue(repaired.supportsTools)
        assertTrue(repaired.reasoningMetadataAvailable)
        assertEquals("MINIMAL,LOW,MEDIUM,HIGH,XHIGH,MAX", repaired.reasoningEffortsCsv)
        assertEquals("HIGH", repaired.reasoningDefaultEffort)
        assertTrue(repaired.reasoningDefaultEnabled)
    }

    @Test
    fun qwenImageUsesDashScopeNativeSingleUserMessageSchema() {
        val provider = DefaultCatalog.providers.single { it.id == "qwen-cloud" }
        val imageModel = imageModel(provider, "qwen-image-2.0", supportsVision = true)
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
    fun qwenImage20EncodesLocalAttachmentForEditing() {
        val provider = DefaultCatalog.providers.single { it.id == "qwen-cloud" }
        val imageModel = imageModel(provider, "qwen-image-2.0", supportsVision = true)
        val file = File.createTempFile("qwen-image-input", ".png")
        try {
            file.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))
            val attachment = AttachmentEntity(
                id = "image-1",
                conversationId = "conversation",
                messageNodeId = "message",
                displayName = "input.png",
                mimeType = "image/png",
                sizeBytes = file.length(),
                localPath = file.absolutePath,
                createdAt = 0L,
            )
            val imageRequest = request().copy(
                provider = provider,
                model = imageModel,
                messages = listOf(
                    InputMessage(
                        role = MessageRole.USER,
                        content = "Make the sky purple",
                        attachments = listOf(attachment),
                    ),
                ),
                tools = emptyList(),
            )

            val body = QwenCloudImageProvider(OpenAiCompatibleProvider()).buildRequestBody(imageRequest)
            val content = body["input"]!!.jsonObject["messages"]!!.jsonArray
                .single().jsonObject["content"]!!.jsonArray

            assertEquals(2, content.size)
            assertTrue(content[0].jsonObject["image"]!!.jsonPrimitive.content.startsWith("data:image/png;base64,"))
            assertEquals("Make the sky purple", content[1].jsonObject["text"]!!.jsonPrimitive.content)
        } finally {
            file.delete()
        }
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

    private fun imageModel(provider: ProviderEntity, id: String, supportsVision: Boolean) = ModelEntity(
        providerId = provider.id,
        modelId = id,
        displayName = id,
        contextWindow = 0,
        maxOutputTokens = 0,
        inputCacheHitUsdPerMillion = 0.0,
        inputCacheMissUsdPerMillion = 0.0,
        outputUsdPerMillion = 0.0,
        supportsVision = supportsVision,
        supportsImageGeneration = true,
    )

    private fun model(provider: ProviderEntity, id: String) = ModelEntity(
        providerId = provider.id,
        modelId = id,
        displayName = id,
        contextWindow = 0,
        maxOutputTokens = 32_000,
        inputCacheHitUsdPerMillion = 0.0,
        inputCacheMissUsdPerMillion = 0.0,
        outputUsdPerMillion = 0.0,
        supportsTools = true,
    )

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
