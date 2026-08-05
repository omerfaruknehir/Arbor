package app.xylune.chat.generation

import app.xylune.chat.data.ConversationEntity
import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.ProviderEntity
import app.xylune.chat.data.ProviderKind
import app.xylune.chat.data.ThinkingEffort
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
            webSearchEnabled = true,
            agentPythonEnabled = false,
            agentUbuntuEnabled = true,
            deepResearchEnabled = true,
            hybridTokenCountingEnabled = true,
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
            supportsImageGeneration = true,
            reasoningMetadataAvailable = true,
            reasoningEffortsCsv = "LOW,HIGH",
            reasoningDefaultEffort = "HIGH",
            reasoningDefaultEnabled = true,
            reasoningMandatory = true,
            reasoningSupportsMaxTokens = true,
        )

        val snapshot = GenerationRequestSnapshot.capture(conversation, provider, model)
        val restored = snapshot.applyTo(conversation.copy(thinkingEnabled = true, thinkingEffort = ThinkingEffort.HIGH))

        assertFalse(snapshot.thinkingEnabled)
        assertEquals(ThinkingEffort.MINIMAL, snapshot.thinkingEffort)
        assertFalse(restored.thinkingEnabled)
        assertEquals(ThinkingEffort.MINIMAL, restored.thinkingEffort)
        assertEquals(true, restored.webSearchEnabled)
        assertEquals(false, restored.agentPythonEnabled)
        assertEquals(true, restored.agentUbuntuEnabled)
        assertEquals(true, restored.deepResearchEnabled)
        assertEquals(true, restored.hybridTokenCountingEnabled)
        assertEquals(true, snapshot.supportsImageGeneration)
        assertEquals(true, snapshot.model().supportsImageGeneration)
        assertEquals(true, snapshot.model().reasoningMetadataAvailable)
        assertEquals("LOW,HIGH", snapshot.model().reasoningEffortsCsv)
        assertEquals("HIGH", snapshot.model().reasoningDefaultEffort)
        assertEquals(true, snapshot.model().reasoningDefaultEnabled)
        assertEquals(true, snapshot.model().reasoningMandatory)
        assertEquals(true, snapshot.model().reasoningSupportsMaxTokens)
    }
}
