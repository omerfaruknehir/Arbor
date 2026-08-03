package app.xylune.chat.ui

import app.xylune.chat.data.ConversationEntity
import app.xylune.chat.data.MessageRole
import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.ProviderEntity
import app.xylune.chat.data.ProviderKind
import app.xylune.chat.generation.GenerationRequestSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchStateProtocolTest {
    @Test fun explicitStateIsParsedAndRemovedFromVisibleText() {
        val input = """
            <xylune-research-state>
            {"status":"Reading primary sources","reportState":"researching","progress":0.4,"steps":[{"id":"docs","title":"Read official docs","state":"active"}]}
            </xylune-research-state>
            User-facing prose.
        """.trimIndent()
        val extracted = ResearchStateProtocol.extract(input)
        assertEquals("User-facing prose.", extracted.cleanedText.trim())
        assertEquals("Reading primary sources", extracted.states.single().status)
        assertEquals(.4f, extracted.states.single().progress, .0001f)
        assertEquals("active", extracted.states.single().steps.single().state)
    }

    @Test fun partialStreamingStateNeverLeaksJson() {
        val input = "Visible text\n<xylune-research-state>{\"status\":\"Planning"
        val extracted = ResearchStateProtocol.extract(input)
        assertEquals("Visible text", extracted.cleanedText)
        assertTrue(extracted.states.isEmpty())
    }

    @Test fun percentageProgressIsNormalized() {
        val input = "<xylune-research-state>{\"progress\":75,\"steps\":[]}</xylune-research-state>"
        assertEquals(.75f, ResearchStateProtocol.extract(input).states.single().progress, .0001f)
    }

    @Test fun researchUiUsesImmutableRequestSnapshotNotCurrentConversationState() {
        val provider = ProviderEntity("p", "Provider", ProviderKind.OPENAI_COMPATIBLE, "https://example.com")
        val model = ModelEntity("p", "m", "Model", 128_000, 8_192, 0.0, 0.0, 0.0)
        val researchConversation = ConversationEntity("c", "Chat", 1, 1, deepResearchEnabled = true)
        val ordinaryConversation = researchConversation.copy(deepResearchEnabled = false)
        val json = Json { encodeDefaults = true }
        val researchSnapshot = json.encodeToString(GenerationRequestSnapshot.capture(researchConversation, provider, model))
        val ordinarySnapshot = json.encodeToString(GenerationRequestSnapshot.capture(ordinaryConversation, provider, model))

        assertTrue(ResearchStateProtocol.isDeepResearchResponse(MessageRole.ASSISTANT, researchSnapshot))
        assertFalse(ResearchStateProtocol.isDeepResearchResponse(MessageRole.USER, researchSnapshot))
        assertFalse(ResearchStateProtocol.isDeepResearchResponse(MessageRole.ASSISTANT, ordinarySnapshot))
        assertFalse(ResearchStateProtocol.isDeepResearchResponse(MessageRole.ASSISTANT, null))
    }
}
