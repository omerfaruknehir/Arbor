package app.xylune.chat.ui

import app.xylune.chat.data.MessageEntity
import app.xylune.chat.data.MessageRole
import app.xylune.chat.data.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BranchNavigationTest {
    private fun message(
        nodeId: String,
        parentNodeId: String?,
        role: MessageRole,
        createdAt: Long,
        superseded: Boolean,
        conversationId: String = "conversation",
    ) = MessageEntity(
        rowId = createdAt,
        nodeId = nodeId,
        conversationId = conversationId,
        parentNodeId = parentNodeId,
        branchId = "branch-$nodeId",
        role = role,
        content = nodeId,
        status = MessageStatus.COMPLETE,
        createdAt = createdAt,
        updatedAt = createdAt,
        supersededAt = if (superseded) createdAt + 100 else null,
    )

    @Test
    fun editedUserBranchesAreOrderedAndIncludeTheActiveMessage() {
        val original = message("user-1", "assistant-before", MessageRole.USER, 1, superseded = true)
        val second = message("user-2", "assistant-before", MessageRole.USER, 2, superseded = true)
        val active = message("user-3", "assistant-before", MessageRole.USER, 3, superseded = false)

        val groups = buildRevisionBranchGroups(listOf(second, original))
        val options = inlineBranchOptions(active, groups)

        assertEquals(listOf("user-1", "user-2", "user-3"), options.map { it.nodeId })
    }

    @Test
    fun assistantRetriesDoNotMixWithEditedUserBranches() {
        val oldAssistant = message("assistant-1", "user-current", MessageRole.ASSISTANT, 10, superseded = true)
        val unrelatedUser = message("user-old", "user-current", MessageRole.USER, 11, superseded = true)
        val activeAssistant = message("assistant-2", "user-current", MessageRole.ASSISTANT, 12, superseded = false)

        val options = inlineBranchOptions(
            activeAssistant,
            buildRevisionBranchGroups(listOf(unrelatedUser, oldAssistant)),
        )

        assertEquals(listOf("assistant-1", "assistant-2"), options.map { it.nodeId })
    }

    @Test
    fun normalMessagesHaveNoInlineBranchControl() {
        val active = message("only", null, MessageRole.USER, 1, superseded = false)
        assertTrue(inlineBranchOptions(active, emptyMap()).isEmpty())
    }
}
