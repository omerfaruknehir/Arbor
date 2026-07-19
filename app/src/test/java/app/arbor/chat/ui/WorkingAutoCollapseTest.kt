package app.arbor.chat.ui

import app.arbor.chat.agent.MessageTimelineEvent
import app.arbor.chat.agent.ToolTraceEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkingAutoCollapseTest {
    @Test fun completedToolCollapsesBeforeMessageEnds() {
        val event = MessageTimelineEvent(
            kind = "python",
            status = "complete",
            startedAt = 1,
            finishedAt = 2,
        )
        assertFalse(isTimelineWorkingActive(listOf(event), isLastSegment = true, messageStreaming = true))
    }

    @Test fun runningToolStaysExpanded() {
        val event = MessageTimelineEvent(kind = "search", status = "running", startedAt = 1)
        assertTrue(isTimelineWorkingActive(listOf(event), isLastSegment = true, messageStreaming = true))
    }

    @Test fun reasoningCollapsesWhenAnswerTextStarts() {
        val event = MessageTimelineEvent(kind = "reasoning", content = "thought", startedAt = 1, finishedAt = 2)
        assertTrue(isTimelineWorkingActive(listOf(event), isLastSegment = true, messageStreaming = true))
        assertFalse(isTimelineWorkingActive(listOf(event), isLastSegment = false, messageStreaming = true))
    }

    @Test fun legacyCompletedToolCollapsesWhileMessageContinues() {
        val trace = ToolTraceEvent(
            type = "python",
            label = "Python",
            status = "complete",
            startedAt = 1,
            finishedAt = 2,
        )
        assertFalse(
            isLegacyWorkingActive(
                traces = listOf(trace),
                reasoningText = "",
                responseTextStarted = false,
                messageStreaming = true,
            ),
        )
    }
}
