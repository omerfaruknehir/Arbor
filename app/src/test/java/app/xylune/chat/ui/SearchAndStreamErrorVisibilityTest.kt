package app.xylune.chat.ui

import app.xylune.chat.data.MessageEntity
import app.xylune.chat.data.MessageRole
import app.xylune.chat.data.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAndStreamErrorVisibilityTest {
    @Test
    fun `provider markdown and Xylune source notation expose titles and urls`() {
        val links = extractTimelineSourceLinks(
            """Results [[PNA|https://www.pna.gov.ph/a]] and [Android docs](https://developer.android.com/b). """ +
                "Duplicate https://www.pna.gov.ph/a",
        )
        assertEquals(2, links.size)
        assertEquals("PNA", links[0].title)
        assertEquals("https://www.pna.gov.ph/a", links[0].url)
        assertEquals("Android docs", links[1].title)
    }

    @Test
    fun `recovery notice identity changes for a new error revision`() {
        val first = failedMessage(updatedAt = 10, error = "HTTP 429: rate limited")
        val updated = first.copy(updatedAt = 11, error = "HTTP 503: unavailable")
        assertNotEquals(recoveryNoticeKey(first), recoveryNoticeKey(updated))
        assertTrue(recoveryErrorSummary(first).contains("429"))
    }

    private fun failedMessage(updatedAt: Long, error: String) = MessageEntity(
        nodeId = "assistant-1",
        conversationId = "conversation-1",
        parentNodeId = "user-1",
        branchId = "branch-1",
        role = MessageRole.ASSISTANT,
        content = "",
        status = MessageStatus.ERROR,
        providerId = "openai",
        modelId = "gpt-test",
        createdAt = 1,
        updatedAt = updatedAt,
        error = error,
    )
}
