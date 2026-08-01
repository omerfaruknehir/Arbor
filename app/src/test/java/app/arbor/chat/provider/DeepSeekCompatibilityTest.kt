package app.arbor.chat.provider

import app.arbor.chat.data.MessageRole
import app.arbor.chat.data.ModelEntity
import app.arbor.chat.data.ProviderEntity
import app.arbor.chat.data.ProviderKind
import app.arbor.chat.data.ThinkingEffort
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekCompatibilityTest {
    @Test
    fun toolReplayUsesNonNullAssistantContent() {
        val request = ChatRequest(
            provider = ProviderEntity("deepseek", "DeepSeek", ProviderKind.OPENAI_COMPATIBLE, "https://api.deepseek.com"),
            model = ModelEntity(
                providerId = "deepseek",
                modelId = "deepseek-v4-pro",
                displayName = "DeepSeek V4 Pro",
                contextWindow = 1_000_000,
                maxOutputTokens = 384_000,
                inputCacheHitUsdPerMillion = 0.0,
                inputCacheMissUsdPerMillion = 0.0,
                outputUsdPerMillion = 0.0,
                supportsThinking = true,
                supportsTools = true,
            ),
            apiKey = "test",
            messages = listOf(
                InputMessage(MessageRole.USER, "Use a tool"),
                InputMessage(
                    role = MessageRole.ASSISTANT,
                    content = "",
                    reasoning = "Need a tool",
                    nativeToolCalls = listOf(NativeToolCall("call_1", "web_search", "{}")),
                ),
            ),
            maxOutputTokens = 1_024,
            thinkingEnabled = true,
            thinkingEffort = ThinkingEffort.HIGH,
        )

        val assistant = OpenAiCompatibleProvider().buildRequestBody(request)
            .getValue("messages").jsonArray[1].jsonObject

        assertEquals("", assistant.getValue("content").jsonPrimitive.content)
        assertFalse(assistant.getValue("content") == JsonNull)
        assertEquals("Need a tool", assistant.getValue("reasoning_content").jsonPrimitive.content)
    }

    @Test
    fun correctionRetryAddsStrictStructuredToolReminder() {
        val request = ChatRequest(
            provider = ProviderEntity("deepseek", "DeepSeek", ProviderKind.OPENAI_COMPATIBLE, "https://api.deepseek.com"),
            model = ModelEntity(
                providerId = "deepseek",
                modelId = "deepseek-v4-pro",
                displayName = "DeepSeek V4 Pro",
                contextWindow = 1_000_000,
                maxOutputTokens = 384_000,
                inputCacheHitUsdPerMillion = 0.0,
                inputCacheMissUsdPerMillion = 0.0,
                outputUsdPerMillion = 0.0,
                supportsThinking = true,
                supportsTools = true,
            ),
            apiKey = "test",
            messages = listOf(InputMessage(MessageRole.USER, "Build the widget")),
            maxOutputTokens = 1_024,
            thinkingEnabled = true,
            tools = listOf(
                NativeToolDefinition(
                    name = "compile_widget",
                    description = "Compile a widget",
                    parametersJson = "{\"type\":\"object\",\"properties\":{\"source\":{\"type\":\"string\"}},\"required\":[\"source\"]}",
                ),
            ),
        )

        val guarded = OpenAiCompatibleProvider().deepSeekToolGuardedRequest(request, correctionAttempt = 1)
        val system = guarded.messages.first { it.role == MessageRole.SYSTEM }.content

        assertTrue(system.contains("structured tool_calls"))
        assertTrue(system.contains("previous attempt"))
        assertEquals(1, request.messages.size)
        assertEquals(MessageRole.USER, request.messages.single().role)
    }

}
