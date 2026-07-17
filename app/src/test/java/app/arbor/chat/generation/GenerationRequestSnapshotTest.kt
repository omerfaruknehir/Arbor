package app.arbor.chat.generation

import app.arbor.chat.data.ConversationEntity
import app.arbor.chat.data.ModelEntity
import app.arbor.chat.data.ProviderEntity
import app.arbor.chat.data.ProviderKind
import app.arbor.chat.data.ThinkingEffort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GenerationRequestSnapshotTest {
    @Test
    fun capturesThinkingControlsForQueuedAndResumedWork() {
        val conversation = ConversationEntity(
            id = "chat",
            title = "Chat",
            createdAt = 1,
            updatedAt = 1,
            thinkingEnabled = false,
            thinkingEffort = ThinkingEffort.MINIMAL,
        )
        val provider = ProviderEntity("deepseek", "DeepSeek", ProviderKind.OPENAI_COMPATIBLE, "https://api.deepseek.com")
        val model = ModelEntity(
            providerId = "deepseek",
            modelId = "deepseek-v4-flash",
            displayName = "DeepSeek V4 Flash",
            contextWindow = 1_000_000,
            maxOutputTokens = 128_000,
            inputCacheHitUsdPerMillion = 0.0,
            inputCacheMissUsdPerMillion = 0.0,
            outputUsdPerMillion = 0.0,
            supportsThinking = true,
        )

        val snapshot = GenerationRequestSnapshot.capture(conversation, provider, model)
        val restored = snapshot.applyTo(conversation.copy(thinkingEnabled = true, thinkingEffort = ThinkingEffort.HIGH))

        assertFalse(snapshot.thinkingEnabled)
        assertEquals(ThinkingEffort.MINIMAL, snapshot.thinkingEffort)
        assertFalse(restored.thinkingEnabled)
        assertEquals(ThinkingEffort.MINIMAL, restored.thinkingEffort)
    }
}
