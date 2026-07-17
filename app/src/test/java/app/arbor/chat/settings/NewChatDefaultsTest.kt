package app.arbor.chat.settings

import app.arbor.chat.data.ConversationEntity
import app.arbor.chat.data.ReasoningVisibility
import app.arbor.chat.data.ThinkingEffort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewChatDefaultsTest {
    @Test
    fun roundTripsAllPerChatSettings() {
        val conversation = ConversationEntity(
            id = "chat",
            title = "Chat",
            createdAt = 1,
            updatedAt = 1,
            selectedProviderId = "anthropic",
            selectedModelId = "claude-sonnet-5",
            contextPairs = 48,
            contextTokenLimit = 250_000,
            workingTokenLimit = 30_000,
            maxOutputTokens = 32_000,
            systemPrompt = "Be strict",
            reasoningVisibility = ReasoningVisibility.COLLAPSED,
            thinkingEnabled = false,
            thinkingEffort = ThinkingEffort.HIGH,
            webSearchEnabled = false,
            agentPythonEnabled = true,
            agentUbuntuEnabled = true,
            deepResearchEnabled = true,
            hybridTokenCountingEnabled = true,
        )

        val defaults = NewChatDefaults.from(conversation)
        val applied = defaults.applyTo(conversation.copy(
            selectedProviderId = "other",
            selectedModelId = "other",
            thinkingEnabled = true,
            webSearchEnabled = true,
            agentUbuntuEnabled = false,
        ))

        assertEquals("anthropic", applied.selectedProviderId)
        assertEquals("claude-sonnet-5", applied.selectedModelId)
        assertEquals(48, applied.contextPairs)
        assertEquals(250_000, applied.contextTokenLimit)
        assertEquals(30_000, applied.workingTokenLimit)
        assertEquals(32_000, applied.maxOutputTokens)
        assertEquals("Be strict", applied.systemPrompt)
        assertEquals(ReasoningVisibility.COLLAPSED, applied.reasoningVisibility)
        assertEquals(ThinkingEffort.HIGH, applied.thinkingEffort)
        assertFalse(applied.thinkingEnabled)
        assertFalse(applied.webSearchEnabled)
        assertTrue(applied.agentPythonEnabled)
        assertTrue(applied.agentUbuntuEnabled)
        assertTrue(applied.deepResearchEnabled)
        assertTrue(applied.hybridTokenCountingEnabled)
    }
}
