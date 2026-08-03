package app.xylune.chat.chat

import app.xylune.chat.data.ConversationEntity
import app.xylune.chat.data.MessageEntity
import app.xylune.chat.data.MessageRole
import app.xylune.chat.data.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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


    @Test
    fun workingBudgetKeepsNewestCompletedHistoryFirst() {
        val old = message("a1", MessageRole.ASSISTANT, "old answer", reasoning = "old ".repeat(200))
        val newest = message("a2", MessageRole.ASSISTANT, "new answer", reasoning = "newest reasoning")

        val limited = ContextAssembler.limitWorkingStates(listOf(old, newest), tokenLimit = 20)

        assertEquals("newest reasoning", limited["a2"]?.reasoning)
        val older = limited["a1"]?.reasoning.orEmpty()
        assertTrue(older.isBlank() || older.startsWith("[older Working state truncated]"))
        assertFalse(older == "old ".repeat(200))
    }

    @Test
    fun interruptedWorkingSurvivesZeroHistoricalBudget() {
        val interrupted = message(
            "a1",
            MessageRole.ASSISTANT,
            "partial answer",
            status = MessageStatus.INTERRUPTED,
            reasoning = "must survive",
            toolTraceJson = "[{\"type\":\"python\"}]",
        )

        val limited = ContextAssembler.limitWorkingStates(listOf(interrupted), tokenLimit = 0)

        assertEquals("must survive", limited["a1"]?.reasoning)
        assertTrue(limited["a1"]?.toolTrace?.contains("python") == true)
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
        reasoning: String = "",
        toolTraceJson: String = "[]",
    ) = MessageEntity(
        nodeId = id,
        conversationId = "c",
        parentNodeId = null,
        branchId = "b",
        role = role,
        content = content,
        reasoning = reasoning,
        toolTraceJson = toolTraceJson,
        status = status,
        createdAt = 0,
        updatedAt = 0,
    )
}
