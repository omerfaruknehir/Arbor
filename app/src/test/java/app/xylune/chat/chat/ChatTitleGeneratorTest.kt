package app.xylune.chat.chat

import app.xylune.chat.data.MessageEntity
import app.xylune.chat.data.MessageRole
import app.xylune.chat.data.MessageStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTitleGeneratorTest {
    @Test
    fun newerSpecificRequestReplacesGenericFollowUp() {
        val messages = listOf(
            message("thanks"),
            message("Add unread indicators and global chat search"),
            message("Build an Android chat application"),
        )

        val title = ChatTitleGenerator.generate(messages)

        assertTrue(title.contains("unread", ignoreCase = true))
        assertTrue(title.contains("search", ignoreCase = true))
        assertFalse(title.startsWith("thanks", ignoreCase = true))
    }

    private fun message(text: String) = MessageEntity(
        nodeId = text,
        conversationId = "c",
        parentNodeId = null,
        branchId = "b",
        role = MessageRole.USER,
        content = text,
        status = MessageStatus.COMPLETE,
        createdAt = 0,
        updatedAt = 0,
    )
}
