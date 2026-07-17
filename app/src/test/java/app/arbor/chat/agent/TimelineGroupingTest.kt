package app.arbor.chat.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineGroupingTest {
    @Test
    fun normalTextSplitsWorkingGroupsWithoutChangingOrder() {
        val events = listOf(
            event("reasoning", "think one"),
            event("search", "search one"),
            event("text", "A normal answer paragraph."),
            event("reasoning", "think two"),
            event("python", "python one"),
            event("text", "Final answer."),
        )

        val runs = groupOrderedTimeline(events)

        assertEquals(listOf(true, false, true, false), runs.map { it.working })
        assertEquals(
            listOf("think one", "search one", "A normal answer paragraph.", "think two", "python one", "Final answer."),
            runs.flatMap { it.events }.map { it.content },
        )
        assertEquals(listOf(2, 1, 2, 1), runs.map { it.events.size })
    }

    @Test
    fun adjacentWorkingEventsShareOneRun() {
        val runs = groupOrderedTimeline(listOf(event("reasoning", "r"), event("search", "s"), event("python", "p")))
        assertEquals(1, runs.size)
        assertEquals(true, runs.single().working)
        assertEquals(listOf("reasoning", "search", "python"), runs.single().events.map { it.kind })
    }

    @Test
    fun returnedFilesStayVisibleBetweenWorkingAndAnswerText() {
        val runs = groupOrderedTimeline(listOf(event("python", "run"), event("file", "plot.png"), event("text", "Here it is.")))
        assertEquals(listOf(true, false), runs.map { it.working })
        assertEquals(listOf("python", "file", "text"), runs.flatMap { it.events }.map { it.kind })
    }

    private fun event(kind: String, content: String) = MessageTimelineEvent(
        kind = kind,
        content = content,
        startedAt = 1,
    )
}
