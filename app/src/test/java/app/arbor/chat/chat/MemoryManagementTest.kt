package app.arbor.chat.chat

import app.arbor.chat.data.MemoryEntity
import app.arbor.chat.data.MessageEntity
import app.arbor.chat.data.MessageRole
import app.arbor.chat.data.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryManagementTest {
    @Test fun punctuationAndCaseDoNotCreateDuplicateMemories() {
        val existing = memory(
            id = "one",
            content = "Prefers Linux for development.",
            category = "preferences",
        )
        val duplicate = MemoryManagement.findDuplicate(
            memories = listOf(existing),
            content = "prefers  Linux for development",
            category = "Preferences",
        )
        assertEquals(existing.id, duplicate?.id)
    }

    @Test fun negatedPreferenceIsNotMergedIntoPositivePreference() {
        val existing = memory(
            id = "one",
            content = "Prefers Linux for development",
            category = "preferences",
        )
        val duplicate = MemoryManagement.findDuplicate(
            memories = listOf(existing),
            content = "Does not prefer Linux for development",
            category = "preferences",
        )
        assertEquals(null, duplicate)
    }

    @Test fun relevantMemoriesWinWhenContextBudgetIsFull() {
        val memories = buildList {
            repeat(40) { index ->
                add(memory("noise-$index", "Unrelated saved detail number $index ${"x".repeat(120)}", updatedAt = index.toLong()))
            }
            add(memory("linux", "Uses an ASUS Snapdragon laptop and develops on Linux", category = "devices", updatedAt = 1L))
            add(memory("sensor", "Frequently works on Linux sensor and camera support", category = "projects", updatedAt = 2L))
        }
        val selected = MemoryManagement.selectForContext(
            memories = memories,
            messagesNewestFirst = listOf(userMessage("Need help debugging the Linux camera sensor on my Snapdragon laptop")),
            currentConversationId = "chat",
            maxItems = 8,
            maxCharacters = 1_500,
        )

        assertTrue(selected.any { it.id == "linux" })
        assertTrue(selected.any { it.id == "sensor" })
        assertTrue(selected.size <= 8)
        assertTrue(selected.sumOf { it.content.length + it.category.length + 48 } <= 1_500)
    }

    @Test fun disabledMemoriesStayOutOfContextAndDefaultSearch() {
        val disabled = memory("disabled", "Private disabled fact", enabled = false)
        val enabled = memory("enabled", "Public enabled fact", enabled = true)
        val selected = MemoryManagement.selectForContext(
            memories = listOf(disabled, enabled),
            messagesNewestFirst = listOf(userMessage("fact")),
            currentConversationId = null,
        )
        assertEquals(listOf("enabled"), selected.map { it.id })
        assertFalse(MemoryManagement.search(listOf(disabled, enabled), "fact").any { it.id == "disabled" })
        assertTrue(MemoryManagement.search(listOf(disabled, enabled), "fact", includeDisabled = true).any { it.id == "disabled" })
    }


    @Test fun exactDuplicateCanMoveAcrossCategoriesWithoutCreatingAnotherItem() {
        val existing = memory("one", "User prefers compact answers", category = "general")
        val duplicate = MemoryManagement.findDuplicate(
            memories = listOf(existing),
            content = "User prefers compact answers.",
            category = "preferences",
        )
        assertEquals("one", duplicate?.id)
    }

    @Test fun unrelatedSmallLibraryIsNotInjectedWholesale() {
        val selected = MemoryManagement.selectForContext(
            memories = listOf(memory("unrelated", "The user's bicycle is red", category = "general")),
            messagesNewestFirst = listOf(userMessage("Explain Kotlin coroutine cancellation")),
            currentConversationId = null,
        )
        assertTrue(selected.isEmpty())
    }

    @Test fun baselineProfileMemoryRemainsAvailableWithoutKeywordOverlap() {
        val selected = MemoryManagement.selectForContext(
            memories = listOf(memory("language", "Prefers replies in Turkish", category = "language")),
            messagesNewestFirst = listOf(userMessage("Explain coroutine cancellation")),
            currentConversationId = null,
        )
        assertEquals(listOf("language"), selected.map { it.id })
    }

    private fun memory(
        id: String,
        content: String,
        category: String = "general",
        enabled: Boolean = true,
        updatedAt: Long = 10L,
    ) = MemoryEntity(
        id = id,
        normalizedKey = MemoryManagement.canonicalKey(content, category),
        content = content,
        category = category,
        sourceConversationId = null,
        enabled = enabled,
        createdAt = 1L,
        updatedAt = updatedAt,
    )

    private fun userMessage(content: String) = MessageEntity(
        nodeId = "user",
        conversationId = "chat",
        parentNodeId = null,
        branchId = "branch",
        role = MessageRole.USER,
        content = content,
        status = MessageStatus.COMPLETE,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
