package app.arbor.chat.provider

import app.arbor.chat.data.MessageRole
import app.arbor.chat.data.ModelEntity
import app.arbor.chat.data.ProviderEntity
import app.arbor.chat.data.ProviderKind
import app.arbor.chat.data.ThinkingEffort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridTokenCounterTest {
    @Test
    fun openAiCompatibleUsesLocalFamilyFallbackWithoutNetwork() = runTest {
        val request = ChatRequest(
            provider = ProviderEntity("openai", "OpenAI", ProviderKind.OPENAI_COMPATIBLE, "https://example.invalid/v1"),
            model = ModelEntity(
                providerId = "openai",
                modelId = "gpt-5.4",
                displayName = "GPT-5.4",
                contextWindow = 1_000_000,
                maxOutputTokens = 32_000,
                inputCacheHitUsdPerMillion = 0.0,
                inputCacheMissUsdPerMillion = 0.0,
                outputUsdPerMillion = 0.0,
            ),
            apiKey = "",
            messages = listOf(
                InputMessage(MessageRole.SYSTEM, "Be precise."),
                InputMessage(MessageRole.USER, "Count this prompt, including punctuation: {a: 1}."),
            ),
            maxOutputTokens = 1_000,
            thinkingEnabled = true,
            thinkingEffort = ThinkingEffort.MEDIUM,
        )

        val result = HybridTokenCounter().count(request)

        assertEquals(TokenCountSource.LOCAL_FAMILY_ESTIMATE, result.source)
        assertTrue(result.tokens > 10)
    }
}
