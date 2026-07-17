package app.arbor.chat.chat

import app.arbor.chat.data.ConversationEntity
import app.arbor.chat.data.MessageEntity
import app.arbor.chat.data.MessageRole
import app.arbor.chat.data.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ContextAssemblerTest {
    @Test
    fun pairLimitKeepsWholeNewestPairs() {
        val conversation = conversation(contextPairs = 2, tokenLimit = 100_000)
        val newestFirst = listOf(
            message("a3", MessageRole.ASSISTANT, "answer three"),
            message("u3", MessageRole.USER, "question three"),
            message("a2", MessageRole.ASSISTANT, "answer two"),
            message("u2", MessageRole.USER, "question two"),
            message("a1", MessageRole.ASSISTANT, "orphan me never"),
            message("u1", MessageRole.USER, "question one"),
        )

        val selected = ContextAssembler.selectMessages(conversation, newestFirst)

        assertEquals(listOf("u2", "a2", "u3", "a3"), selected.map { it.nodeId })
        assertFalse(selected.any { it.nodeId == "a1" })
    }

    @Test
    fun interruptedNewestPairSurvivesTinyBudgetForResume() {
        val conversation = conversation(contextPairs = 4, tokenLimit = 1)
        val newestFirst = listOf(
            message("a2", MessageRole.ASSISTANT, "partial response", MessageStatus.INTERRUPTED),
            message("u2", MessageRole.USER, "latest request"),
            message("a1", MessageRole.ASSISTANT, "old answer"),
            message("u1", MessageRole.USER, "old question"),
        )

        val selected = ContextAssembler.selectMessages(conversation, newestFirst)

        assertEquals(listOf("u2", "a2"), selected.map { it.nodeId })
    }

    @Test
    fun steeringKeepsImmediatelyPreviousInterruptedStateBeyondPairLimit() {
        val conversation = conversation(contextPairs = 1, tokenLimit = 1)
        val newestFirst = listOf(
            message("a3", MessageRole.ASSISTANT, "" , MessageStatus.STREAMING),
            message("u3", MessageRole.USER, "steer now"),
            message("a2", MessageRole.ASSISTANT, "partial", MessageStatus.INTERRUPTED),
            message("u2", MessageRole.USER, "original request"),
            message("a1", MessageRole.ASSISTANT, "old answer"),
            message("u1", MessageRole.USER, "old question"),
        )

        val selected = ContextAssembler.selectMessages(conversation, newestFirst)

        assertEquals(listOf("u2", "a2", "u3", "a3"), selected.map { it.nodeId })
    }

    private fun conversation(contextPairs: Int, tokenLimit: Int) = ConversationEntity(
        id = "c",
        title = "test",
        createdAt = 0,
        updatedAt = 0,
        contextPairs = contextPairs,
        contextTokenLimit = tokenLimit,
    )

    private fun message(
        id: String,
        role: MessageRole,
        content: String,
        status: MessageStatus = MessageStatus.COMPLETE,
    ) = MessageEntity(
        nodeId = id,
        conversationId = "c",
        parentNodeId = null,
        branchId = "b",
        role = role,
        content = content,
        status = status,
        createdAt = 0,
        updatedAt = 0,
    )
}
