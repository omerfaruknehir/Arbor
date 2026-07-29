package app.arbor.chat.ui

import app.arbor.chat.agent.MessageTimelineEvent
import app.arbor.chat.data.ReasoningVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkingTimelineUiTest {
    @Test
    fun workingVisibilityActuallyControlsAutomaticExpansion() {
        assertTrue(workingBlockDefaultExpanded(ReasoningVisibility.ALWAYS, active = false))
        assertTrue(workingBlockDefaultExpanded(ReasoningVisibility.ALWAYS, active = true))
        assertFalse(workingBlockDefaultExpanded(ReasoningVisibility.SHOW_WHILE_WORKING, active = false))
        assertTrue(workingBlockDefaultExpanded(ReasoningVisibility.SHOW_WHILE_WORKING, active = true))
        assertFalse(workingBlockDefaultExpanded(ReasoningVisibility.COLLAPSED, active = false))
        assertFalse(workingBlockDefaultExpanded(ReasoningVisibility.COLLAPSED, active = true))
    }

    @Test
    fun activeWorkNamesTheCurrentActionInsteadOfOnlySayingWorking() {
        val search = event(
            kind = "search",
            label = "Searching the web",
            status = "running",
        )

        assertEquals("Searching the web", workingBlockHeadline(listOf(search), active = true))
        assertEquals("Running", workingBlockSummary(listOf(search), active = true))
        assertEquals("Web search", workEventTitle(search.copy(label = "")))
    }

    @Test
    fun activeReasoningIsNotPrematurelyReportedAsDone() {
        val reasoning = event(
            kind = "reasoning",
            status = "complete",
        )

        assertEquals("Reasoning", workingBlockHeadline(listOf(reasoning), active = true))
        assertEquals("Thinking", workingBlockSummary(listOf(reasoning), active = true))
    }

    @Test
    fun completedWorkSummarizesStepsAndErrors() {
        val complete = event("reasoning", status = "complete", finishedAt = 125)
        val failed = event("script", label = "Running Python", status = "error", finishedAt = 350)

        assertEquals("Finished with an error", workingBlockHeadline(listOf(complete, failed), active = false))
        assertEquals("2 steps • 1 error", workingBlockSummary(listOf(complete, failed), active = false))
        assertEquals("Failed", workEventStateLabel(failed))
    }

    @Test
    fun composerMakesBackgroundActionsDiscoverable() {
        val chat = java.io.File("src/main/java/app/arbor/chat/ui/ChatScreen.kt").readText()
        val composer = chat.substringAfter("private fun Composer(").substringBefore("private fun StagedAttachmentPreview")

        assertTrue(composer.contains("Arbor is working in the background"))
        assertTrue(composer.contains("Send queues a message; use ⋮ for Steer"))
        assertTrue(composer.contains("viewModel.send(if (generating) SendMode.QUEUE else SendMode.SEND_NOW)"))
        assertTrue(composer.contains("Choose Queue, Steer, or separate turn"))
        assertTrue(composer.contains("Text(\"Stop\")"))
        assertFalse(composer.contains("\"Stop or send\""))
    }

    @Test
    fun searchAndFetchFailuresUseHonestStatusText() {
        val chat = java.io.File("src/main/java/app/arbor/chat/ui/ChatScreen.kt").readText()

        assertTrue(chat.contains("\"error\" -> \"Search failed\""))
        assertTrue(chat.contains("\"error\" -> \"Source failed\""))
        assertFalse(chat.contains("\"No opened sources\""))
    }

    @Test
    fun executionDurationsStayReadable() {
        assertEquals("", formatExecutionDuration(0))
        assertEquals("640 ms", formatExecutionDuration(640))
        assertEquals("1.2 s", formatExecutionDuration(1_240))
        assertEquals("12 s", formatExecutionDuration(12_400))
    }

    private fun event(
        kind: String,
        label: String = "",
        status: String,
        finishedAt: Long? = null,
    ) = MessageTimelineEvent(
        id = "$kind-$status",
        kind = kind,
        label = label,
        status = status,
        startedAt = 100,
        finishedAt = finishedAt,
    )
}
