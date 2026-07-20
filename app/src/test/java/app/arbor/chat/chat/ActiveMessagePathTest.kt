package app.arbor.chat.chat

import app.arbor.chat.data.MessageEntity
import app.arbor.chat.data.MessageRole
import app.arbor.chat.data.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveMessagePathTest {
    private fun message(id: String, parent: String?, createdAt: Long = 0L) = MessageEntity(
        rowId = createdAt,
        nodeId = id,
        conversationId = "conversation",
        parentNodeId = parent,
        branchId = "branch-$id",
        role = if (id.startsWith("u")) MessageRole.USER else MessageRole.ASSISTANT,
        content = id,
        status = MessageStatus.COMPLETE,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun siblingRetriesAreExcludedFromTheSelectedLeafPath() {
        val root = message("u0", null, 1)
        val oldRetry = message("a-old", "u0", 2)
        val selectedRetry = message("a-selected", "u0", 3)
        val followUp = message("u1", "a-selected", 4)
        val leaf = message("a1", "u1", 5)

        val path = activeMessagePathNodeIds(
            listOf(root, oldRetry, selectedRetry, followUp, leaf),
            leaf.nodeId,
        )

        assertEquals(setOf("u0", "a-selected", "u1", "a1"), path)
        assertTrue("a-old" !in path)
    }

    @Test
    fun missingLeafProducesNoAccidentalFallbackPath() {
        assertTrue(activeMessagePathNodeIds(listOf(message("u0", null)), "missing").isEmpty())
    }

    @Test
    fun malformedParentCycleCannotLoopForever() {
        val first = message("a1", "a2")
        val second = message("a2", "a1")
        assertEquals(setOf("a1", "a2"), activeMessagePathNodeIds(listOf(first, second), "a1"))
    }
}
