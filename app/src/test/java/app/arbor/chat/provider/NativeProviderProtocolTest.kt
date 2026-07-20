package app.arbor.chat.provider

import app.arbor.chat.data.MessageRole
import app.arbor.chat.data.ModelEntity
import app.arbor.chat.data.ProviderEntity
import app.arbor.chat.data.ProviderKind
import app.arbor.chat.data.ThinkingEffort
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeProviderProtocolTest {
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
