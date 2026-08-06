package app.xylune.chat.provider

import app.xylune.chat.data.MessageRole
import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.ProviderEntity
import app.xylune.chat.data.ProviderKind
import app.xylune.chat.data.ThinkingEffort
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeProviderProtocolTest {
    @Test
    fun openAiTransportEndpointFollowsRequestPolicy() {
        val provider = OpenAiCompatibleProvider()
        val chat = request(ProviderKind.OPENAI_COMPATIBLE, listOf(InputMessage(MessageRole.USER, "Hello")), providerId = "provider-custom")
        val image = chat.copy(model = chat.model.copy(modelId = "gpt-image-1", supportsImageGeneration = false))
        assertEquals("https://example.com/v1/chat/completions", provider.endpointFor(chat))
        // The generic test provider is custom, so its explicit request type controls transport.
        assertEquals("https://example.com/v1/chat/completions", provider.endpointFor(image))
        val explicitImage = image.copy(model = image.model.copy(supportsImageGeneration = true))
        assertEquals("https://example.com/v1/images/generations", provider.endpointFor(explicitImage))
    }

    private val tool = NativeToolDefinition(
        name = "web_search",
        description = "Search",
        parametersJson = """{"type":"object","properties":{"query":{"type":"string"}},"required":["query"],"additionalProperties":false}""",
    )

    @Test
    fun openAiSerializesToolsAndReassemblesFragmentedCalls() {
        val provider = OpenAiCompatibleProvider()
        val request = request(ProviderKind.OPENAI_COMPATIBLE, listOf(InputMessage(MessageRole.USER, "Find it")))
        val body = provider.buildRequestBody(request)
        assertEquals("function", body["tools"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertFalse(body["parallel_tool_calls"]!!.jsonPrimitive.content.toBoolean())

        val calls = linkedMapOf<Int, OpenAiCompatibleProvider.ToolCallAccumulator>()
        val firstProgress = provider.parseChunk(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"web_","arguments":"{\"query\":\"And"}}]}}]}""",
            calls,
        )
        assertEquals("web_", firstProgress!!.toolCallProgress.single().name)
        assertFalse(firstProgress.toolCallProgress.single().complete)
        val secondProgress = provider.parseChunk(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"search","arguments":"roid\"}"}}]},"finish_reason":"tool_calls"}]}""",
            calls,
        )
        assertEquals("web_search", secondProgress!!.toolCallProgress.single().name)
        assertEquals("{\"query\":\"Android\"}", secondProgress.toolCallProgress.single().argumentsJson)
        val call = calls.getValue(0).complete()
        assertEquals("call_1", call.id)
        assertEquals("web_search", call.name)
        assertEquals("{\"query\":\"Android\"}", call.argumentsJson)
    }

    @Test
    fun openAiCompatibleReadsVisibleReasoningAcrossProviderShapes() {
        val provider = OpenAiCompatibleProvider()
        val calls = linkedMapOf<Int, OpenAiCompatibleProvider.ToolCallAccumulator>()

        assertEquals(
            "OpenRouter reasoning",
            provider.parseChunk(
                """{"choices":[{"delta":{"reasoning":"OpenRouter reasoning"}}]}""",
                calls,
            )!!.reasoning,
        )
        assertEquals(
            "Thinking alias",
            provider.parseChunk(
                """{"choices":[{"delta":{"thinking":"Thinking alias"}}]}""",
                calls,
            )!!.reasoning,
        )
        assertEquals(
            "Structured detail",
            provider.parseChunk(
                """{"choices":[{"delta":{"reasoning_details":[{"type":"reasoning.text","text":"Structured detail"}]}}]}""",
                calls,
            )!!.reasoning,
        )
        assertEquals(
            "Summary detail",
            provider.parseChunk(
                """{"choices":[{"delta":{"reasoning_details":[{"type":"reasoning.summary","summary":"Summary detail"},{"type":"reasoning.encrypted","data":"opaque"}]}}]}""",
                calls,
            )!!.reasoning,
        )
    }

    @Test
    fun openAiCompatibleRoutesThinkingTagsAcrossStreamingChunks() {
        val parser = OpenAiCompatibleProvider.ThinkingTagStreamParser()

        val first = parser.accept("Visible before <thin")
        assertEquals("Visible before ", first.text)
        assertEquals("", first.reasoning)

        val second = parser.accept("king>hidden step</think")
        assertEquals("", second.text)
        assertEquals("hidden step", second.reasoning)

        val third = parser.accept("ing> visible after <think>more")
        assertEquals(" visible after ", third.text)
        assertEquals("more", third.reasoning)

        val fourth = parser.accept(" thought</think> done", explicitReasoning = "native reasoning")
        assertEquals(" done", fourth.text)
        assertEquals("native reasoning thought", fourth.reasoning)
        assertEquals(DsmlChannelDelta("", ""), parser.finish())
    }

    @Test
    fun anthropicPreservesThinkingSignatureAndToolUseBlocks() {
        val provider = AnthropicProvider()
        val body = provider.buildRequestBody(request(ProviderKind.ANTHROPIC, listOf(InputMessage(MessageRole.USER, "Find it"))))
        assertEquals("web_search", body["tools"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)

        val state = AnthropicProvider.AnthropicStreamState()
        provider.parseChunk("""{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}""", state)
        provider.parseChunk("""{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"Need search"}}""", state)
        provider.parseChunk("""{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"signed"}}""", state)
        provider.parseChunk("""{"type":"content_block_stop","index":0}""", state)
        val started = provider.parseChunk("""{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_1","name":"web_search","input":{}}}""", state)
        assertEquals("web_search", started!!.toolCallProgress.single().name)
        val streamed = provider.parseChunk("""{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"query\":\"Android\"}"}}""", state)
        assertEquals("{\"query\":\"Android\"}", streamed!!.toolCallProgress.single().argumentsJson)
        val stopped = provider.parseChunk("""{"type":"content_block_stop","index":1}""", state)
        assertTrue(stopped!!.toolCallProgress.single().complete)

        val final = state.finalChunk()
        assertNotNull(final)
        assertEquals("web_search", final!!.toolCalls.single().name)
        assertTrue(final.nativeProviderPayloadJson.contains("signed"))
        assertTrue(final.nativeProviderPayloadJson.contains("tool_use"))
    }

    @Test
    fun geminiPreservesRawPartsAndFunctionCall() {
        val provider = GeminiProvider()
        val body = provider.buildRequestBody(request(ProviderKind.GEMINI, listOf(InputMessage(MessageRole.USER, "Find it"))))
        val declarations = body["tools"]!!.jsonArray[0].jsonObject["functionDeclarations"]!!.jsonArray
        assertEquals("web_search", declarations[0].jsonObject["name"]!!.jsonPrimitive.content)

        val state = GeminiProvider.GeminiStreamState()
        val chunks = provider.parseChunks(
            """{"candidates":[{"content":{"parts":[{"text":"Need search","thought":true,"thoughtSignature":"sig"},{"functionCall":{"id":"call_1","name":"web_search","args":{"query":"Android"}},"thoughtSignature":"sig2"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":3}}""",
            state,
        )
        assertEquals("Need search", chunks.first().reasoning)
        assertEquals("web_search", chunks.single { it.toolCallProgress.isNotEmpty() }.toolCallProgress.single().name)
        assertTrue(chunks.single { it.toolCallProgress.isNotEmpty() }.toolCallProgress.single().complete)
        val final = state.finalChunk()
        assertEquals("call_1", final!!.toolCalls.single().id)
        assertEquals("web_search", final.toolCalls.single().name)
        assertTrue(final.nativeProviderPayloadJson.contains("thoughtSignature"))
    }


    @Test
    fun openAiOAuthSerializesResponsesProtocolAndToolReplay() {
        val provider = OpenAiOAuthProvider()
        val assistantPayload = """[{"type":"reasoning","id":"rs_1","encrypted_content":"opaque"},{"type":"function_call","id":"fc_1","call_id":"call_1","name":"web_search","arguments":"{\"query\":\"Android\"}"}]"""
        val request = request(
            ProviderKind.OPENAI_OAUTH,
            listOf(
                InputMessage(MessageRole.SYSTEM, "Be precise"),
                InputMessage(MessageRole.USER, "Find it"),
                InputMessage(
                    MessageRole.ASSISTANT,
                    "",
                    nativeToolCalls = listOf(NativeToolCall("call_1", "web_search", """{"query":"Android"}""")),
                    nativeProviderPayloadJson = assistantPayload,
                ),
                InputMessage(
                    MessageRole.TOOL,
                    "",
                    nativeToolResults = listOf(NativeToolResult("call_1", "web_search", "result", isError = false)),
                ),
            ),
            modelId = "gpt-5.6",
            providerId = "openai-oauth",
        ).copy(thinkingEffort = ThinkingEffort.HIGH)

        val body = provider.buildRequestBody(request)
        assertEquals("gpt-5.6", body["model"]!!.jsonPrimitive.content)
        assertTrue(body["stream"]!!.jsonPrimitive.content.toBoolean())
        assertFalse(body["store"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("reasoning.encrypted_content", body["include"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("high", body["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals("web_search", body["tools"]!!.jsonArray.single().jsonObject["name"]!!.jsonPrimitive.content)

        val input = body["input"]!!.jsonArray
        assertEquals("developer", input[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("reasoning", input[2].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("function_call", input[3].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("function_call_output", input[4].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("call_1", input[4].jsonObject["call_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun openAiOAuthUsesAdditionalToolsForResponsesLiteModels() {
        val provider = OpenAiOAuthProvider()
        val body = provider.buildRequestBody(
            request(
                ProviderKind.OPENAI_OAUTH,
                listOf(InputMessage(MessageRole.USER, "Find it")),
                modelId = "gpt-5.6-terra",
                providerId = "openai-oauth",
            ),
            OpenAiOAuthModelInfo(
                id = "gpt-5.6-terra",
                displayName = "GPT-5.6 Terra",
                contextWindow = null,
                maxOutputTokens = null,
                supportsThinking = true,
                useResponsesLite = true,
                defaultReasoningLevel = "medium",
            ),
        )

        assertFalse(body.containsKey("tools"))
        assertEquals("all_turns", body["reasoning"]!!.jsonObject["context"]!!.jsonPrimitive.content)
        val firstInput = body["input"]!!.jsonArray.first().jsonObject
        assertEquals("additional_tools", firstInput["type"]!!.jsonPrimitive.content)
        assertEquals("web_search", firstInput["tools"]!!.jsonArray.single().jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun openAiOAuthReassemblesResponsesToolCallsAndUsage() {
        val state = OpenAiOAuthProvider.OpenAiOAuthStreamState()
        assertEquals(
            "Need search",
            state.accept("""{"type":"response.reasoning_summary_text.delta","delta":"Need search"}""")!!.reasoning,
        )
        val started = state.accept(
            """{"type":"response.output_item.added","output_index":0,"item":{"type":"function_call","id":"fc_1","call_id":"call_1","name":"web_search","arguments":""}}""",
        )
        assertEquals("web_search", started!!.toolCallProgress.single().name)
        val streamed = state.accept(
            """{"type":"response.function_call_arguments.delta","output_index":0,"delta":"{\"query\":\"Android\"}"}""",
        )
        assertEquals("{\"query\":\"Android\"}", streamed!!.toolCallProgress.single().argumentsJson)
        state.accept(
            """{"type":"response.output_item.done","output_index":0,"item":{"type":"function_call","id":"fc_1","call_id":"call_1","name":"web_search","arguments":"{\"query\":\"Android\"}"}}""",
        )
        val completed = state.accept(
            """{"type":"response.completed","response":{"status":"completed","output":[{"type":"function_call","id":"fc_1","call_id":"call_1","name":"web_search","arguments":"{\"query\":\"Android\"}"}],"usage":{"input_tokens":10,"output_tokens":4,"input_tokens_details":{"cached_tokens":3}}}}""",
        )
        assertEquals(10L, completed!!.inputTokens)
        assertEquals(4L, completed.outputTokens)
        assertEquals(3L, completed.cachedInputTokens)

        val final = state.finalChunk()
        assertEquals("call_1", final!!.toolCalls.single().id)
        assertEquals("web_search", final.toolCalls.single().name)
        assertTrue(final.nativeProviderPayloadJson.contains("fc_1"))
        assertEquals("stop", final.finishReason)
    }

    @Test
    fun openAiOAuthPreservesReasoningReplayWithoutToolCalls() {
        val state = OpenAiOAuthProvider.OpenAiOAuthStreamState()
        state.accept(
            """{"type":"response.output_item.done","output_index":0,"item":{"type":"reasoning","id":"rs_1","encrypted_content":"opaque"}}""",
        )
        state.accept(
            """{"type":"response.output_item.done","output_index":1,"item":{"type":"message","id":"msg_1","role":"assistant","content":[{"type":"output_text","text":"Done"}]}}""",
        )
        state.accept(
            """{"type":"response.completed","response":{"status":"completed","output":[{"type":"reasoning","id":"rs_1","encrypted_content":"opaque"},{"type":"message","id":"msg_1","role":"assistant","content":[{"type":"output_text","text":"Done"}]}],"usage":{"input_tokens":2,"output_tokens":1}}}""",
        )

        val final = state.finalChunk()
        assertNotNull(final)
        assertTrue(final!!.toolCalls.isEmpty())
        assertTrue(final.nativeProviderPayloadJson.contains("encrypted_content"))
        assertTrue(final.nativeProviderPayloadJson.contains("msg_1"))
    }

    @Test
    fun openAiImageModelsUseImagesGenerationRequestShape() {
        val base = request(
            ProviderKind.OPENAI_COMPATIBLE,
            listOf(InputMessage(MessageRole.USER, "Draw a glass greenhouse")),
            modelId = "gpt-image-1",
        )
        val request = base.copy(model = base.model.copy(supportsImageGeneration = true))
        val body = OpenAiCompatibleProvider().buildImageRequestBody(request)

        assertEquals("gpt-image-1", body["model"]!!.jsonPrimitive.content)
        assertEquals("Draw a glass greenhouse", body["prompt"]!!.jsonPrimitive.content)
        assertEquals("png", body["output_format"]!!.jsonPrimitive.content)
        assertEquals("auto", body["size"]!!.jsonPrimitive.content)
        assertEquals("auto", body["quality"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("stream"))
    }

    @Test
    fun openRouterImageModelsUsePortableDedicatedApiDefaults() {
        val base = request(
            ProviderKind.OPENAI_COMPATIBLE,
            listOf(InputMessage(MessageRole.USER, "Draw a forest")),
            modelId = "vendor/image-model",
            providerId = "openrouter",
        )
        val request = base.copy(
            provider = base.provider.copy(baseUrl = "https://openrouter.ai/api/v1"),
            model = base.model.copy(supportsImageGeneration = true),
        )
        val body = OpenAiCompatibleProvider().buildImageRequestBody(request)

        assertEquals("vendor/image-model", body["model"]!!.jsonPrimitive.content)
        assertEquals("Draw a forest", body["prompt"]!!.jsonPrimitive.content)
        assertEquals("1", body["n"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("size"))
        assertFalse(body.containsKey("quality"))
        assertFalse(body.containsKey("background"))
        assertFalse(body.containsKey("output_format"))
    }

    @Test
    fun dallEImageModelsRequestBase64Responses() {
        val base = request(
            ProviderKind.OPENAI_COMPATIBLE,
            listOf(InputMessage(MessageRole.USER, "Draw a small robot")),
            modelId = "dall-e-3",
        )
        val request = base.copy(model = base.model.copy(supportsImageGeneration = true))
        val body = OpenAiCompatibleProvider().buildImageRequestBody(request)

        assertEquals("b64_json", body["response_format"]!!.jsonPrimitive.content)
        assertEquals("1024x1024", body["size"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("output_format"))
    }

    @Test
    fun openAiImageResponseDecodesBase64AndRevisedPrompt() {
        val encoded = java.util.Base64.getEncoder().encodeToString("fake-png".toByteArray())
        val root = ProviderJson.parseToJsonElement(
            """{"data":[{"b64_json":"$encoded","revised_prompt":"A polished greenhouse"}],"output_format":"png"}""",
        ).jsonObject

        val image = OpenAiCompatibleProvider().parseImageResponse(root).single()
        assertEquals("fake-png", image.bytes.toString(Charsets.UTF_8))
        assertEquals("image/png", image.mimeType)
        assertEquals("generated-image-1.png", image.displayName)
        assertEquals("A polished greenhouse", image.description)
    }

    @Test
    fun openAiOAuthSerializesImageToolAndStreamsGeneratedImage() {
        val base = request(
            ProviderKind.OPENAI_OAUTH,
            listOf(InputMessage(MessageRole.USER, "Draw a forest at night")),
            modelId = "gpt-5.6",
            providerId = "openai-oauth-account",
        )
        val request = base.copy(model = base.model.copy(supportsImageGeneration = true))
        val body = OpenAiOAuthProvider().buildRequestBody(request)
        val imageTool = body["tools"]!!.jsonArray.single { item ->
            item.jsonObject["type"]!!.jsonPrimitive.content == "image_generation"
        }.jsonObject
        assertEquals("png", imageTool["output_format"]!!.jsonPrimitive.content)
        assertEquals("auto", imageTool["quality"]!!.jsonPrimitive.content)

        val encoded = java.util.Base64.getEncoder().encodeToString("oauth-image".toByteArray())
        val state = OpenAiOAuthProvider.OpenAiOAuthStreamState()
        state.accept(
            """{"type":"response.output_item.done","output_index":0,"item":{"type":"reasoning","id":"rs_1","encrypted_content":"opaque"}}""",
        )
        val imageChunk = state.accept(
            """{"type":"response.output_item.done","output_index":1,"item":{"type":"image_generation_call","id":"img_1","result":"$encoded"}}""",
        )
        assertEquals("oauth-image", imageChunk!!.generatedImages.single().bytes.toString(Charsets.UTF_8))
        assertEquals("image/png", imageChunk.generatedImages.single().mimeType)

        val final = state.finalChunk()
        assertNotNull(final)
        assertTrue(final!!.nativeProviderPayloadJson.contains("encrypted_content"))
        assertFalse(final.nativeProviderPayloadJson.contains(encoded))
        assertFalse(final.nativeProviderPayloadJson.contains("image_generation_call"))
    }

    @Test
    fun disabledToolFinalizationQuarantinesPrayerTimeDsmlAndRetriesCleanly() = runBlocking {
        fun sse(text: String): String =
            "data: {\"choices\":[{\"delta\":{\"content\":${JsonPrimitive(text)}}}]}\n\ndata: [DONE]\n\n"

        val attempt = AtomicInteger(0)
        val requestBodies = mutableListOf<String>()
        val responses = listOf(
            sse(
                "Aladhan API'sine compile_widget erişemiyor. Alternatif bir API deneyeyim.\n" +
                    "< | | DSML | | tool_calls>< | | DSML | | invoke name=\"web_fetch\">" +
                    "< | | DSML | | parameter name=\"url\" string=\"true\">" +
                    "https://api.pray.zone/v2/times/today.json?latitude=39.9334&longitude=32.8597&method=13" +
                    "< / | | DSML | | parameter>< / | | DSML | | invoke>< / | | DSML | | tool_calls>",
            ),
            sse("Mevcut araç sonuçları yeterli değil; eksik veriyi açıkça belirterek devam ediyorum."),
        )
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val buffer = Buffer()
                chain.request().body?.writeTo(buffer)
                requestBodies += buffer.readUtf8()
                val index = attempt.getAndIncrement().coerceAtMost(responses.lastIndex)
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responses[index].toResponseBody("text/event-stream".toMediaType()))
                    .build()
            }
            .build()
        val guardedRequest = request(
            ProviderKind.OPENAI_COMPATIBLE,
            listOf(InputMessage(MessageRole.USER, "Namaz vakti widget'i yap")),
            modelId = "deepseek-v4-pro",
            providerId = "deepseek",
        ).copy(
            tools = emptyList(),
            toolProtocolNames = setOf("compile_widget", "web_search", "web_fetch"),
        )
        val chunks = mutableListOf<StreamChunk>()

        OpenAiCompatibleProvider(client).stream(guardedRequest) { chunks += it }

        val visible = chunks.joinToString(separator = "") { it.text + it.reasoning }
        assertEquals(2, attempt.get())
        assertTrue(chunks.any { it.resetCurrentAttempt })
        assertFalse(visible.contains("DSML", ignoreCase = true))
        assertFalse(visible.contains("web_fetch", ignoreCase = true))
        assertTrue(visible.contains("Mevcut araç sonuçları"))
        assertTrue(requestBodies.none { it.contains("\"tools\"") })
        assertTrue(requestBodies.last().contains("Tools are unavailable for this finalization turn"))
    }

    @Test
    fun providerSpecificThinkingControlsAreSerialized() {
        val openAi = OpenAiCompatibleProvider().buildRequestBody(
            request(ProviderKind.OPENAI_COMPATIBLE, listOf(InputMessage(MessageRole.USER, "Think")), modelId = "o3").copy(
                thinkingEffort = ThinkingEffort.MINIMAL,
            ),
        )
        assertEquals("minimal", openAi["reasoning_effort"]!!.jsonPrimitive.content)

        val anthropic = AnthropicProvider().buildRequestBody(
            request(ProviderKind.ANTHROPIC, listOf(InputMessage(MessageRole.USER, "Think")), modelId = "claude-sonnet-5").copy(
                thinkingEffort = ThinkingEffort.MEDIUM,
            ),
        )
        assertEquals("adaptive", anthropic["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("medium", anthropic["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)

        val gemini = GeminiProvider().buildRequestBody(
            request(ProviderKind.GEMINI, listOf(InputMessage(MessageRole.USER, "Think")), modelId = "gemini-3.5-flash").copy(
                thinkingEffort = ThinkingEffort.HIGH,
            ),
        )
        val config = gemini["generationConfig"]!!.jsonObject["thinkingConfig"]!!.jsonObject
        assertEquals("high", config["thinkingLevel"]!!.jsonPrimitive.content)
    }

    @Test
    fun thinkingOffIsRequestedWhereProviderSupportsIt() {
        val deepSeek = OpenAiCompatibleProvider().buildRequestBody(
            request(ProviderKind.OPENAI_COMPATIBLE, listOf(InputMessage(MessageRole.USER, "Direct")), providerId = "deepseek").copy(
                thinkingEnabled = false,
            ),
        )
        assertEquals("disabled", deepSeek["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertFalse(deepSeek.containsKey("reasoning_effort"))

        val anthropic = AnthropicProvider().buildRequestBody(
            request(ProviderKind.ANTHROPIC, listOf(InputMessage(MessageRole.USER, "Direct")), modelId = "claude-sonnet-5").copy(
                thinkingEnabled = false,
            ),
        )
        assertEquals("disabled", anthropic["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun providerRoundTripsNativeToolResults() {
        val resultMessage = InputMessage(
            role = MessageRole.TOOL,
            content = "",
            nativeToolResults = listOf(NativeToolResult("call_1", "web_search", "result", isError = false)),
        )
        val assistant = InputMessage(
            role = MessageRole.ASSISTANT,
            content = "",
            nativeToolCalls = listOf(NativeToolCall("call_1", "web_search", """{"query":"Android"}""")),
        )
        val body = OpenAiCompatibleProvider().buildRequestBody(request(ProviderKind.OPENAI_COMPATIBLE, listOf(assistant, resultMessage)))
        val messages = body["messages"]!!.jsonArray
        assertEquals("assistant", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("tool", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("call_1", messages[1].jsonObject["tool_call_id"]!!.jsonPrimitive.content)
    }

    private fun request(
        kind: ProviderKind,
        messages: List<InputMessage>,
        modelId: String = "m",
        providerId: String = when (kind) {
            ProviderKind.ANTHROPIC -> "anthropic"
            ProviderKind.GEMINI -> "gemini"
            else -> "openai"
        },
    ) = ChatRequest(
        provider = ProviderEntity(
            id = providerId,
            displayName = kind.name,
            kind = kind,
            baseUrl = "https://example.com/v1",
        ),
        model = ModelEntity(
            providerId = "p",
            modelId = modelId,
            displayName = "Model",
            contextWindow = 100_000,
            maxOutputTokens = 8_000,
            inputCacheHitUsdPerMillion = 0.0,
            inputCacheMissUsdPerMillion = 0.0,
            outputUsdPerMillion = 0.0,
            supportsThinking = true,
            supportsTools = true,
        ),
        apiKey = "key",
        messages = messages,
        maxOutputTokens = 1_000,
        thinkingEnabled = true,
        tools = listOf(tool),
    )
}
