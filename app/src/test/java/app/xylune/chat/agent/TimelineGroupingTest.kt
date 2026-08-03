package app.xylune.chat.agent

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

    @Test
    fun rangedTimelineEventsReadFromAggregateMessageFields() {
        val events = listOf(
            MessageTimelineEvent(kind = "reasoning", sourceStart = 0, sourceEnd = 5, startedAt = 1),
            MessageTimelineEvent(kind = "text", sourceStart = 0, sourceEnd = 6, startedAt = 2),
            MessageTimelineEvent(kind = "python", input = "print(1)", startedAt = 3),
            MessageTimelineEvent(kind = "text", sourceStart = 6, startedAt = 4),
        )

        val materialized = materializeTimelineContent(
            events = events,
            content = "Hello world",
            reasoning = "think later",
        )

        assertEquals(listOf("think", "Hello ", "", "world"), materialized.map { it.content })
    }

    @Test
    fun malformedOpenRangeStopsAtNextRangeOfSameKind() {
        val events = listOf(
            MessageTimelineEvent(kind = "text", sourceStart = 0, startedAt = 1),
            MessageTimelineEvent(kind = "search", input = "query", startedAt = 2),
            MessageTimelineEvent(kind = "text", sourceStart = 5, startedAt = 3),
        )

        val materialized = materializeTimelineContent(events, content = "firstsecond", reasoning = "")

        assertEquals("first", materialized[0].content)
        assertEquals("second", materialized[2].content)
    }


    @Test
    fun alternatingReasoningAndTextChunksDoNotBecomeTokenBlocks() {
        val events = listOf(
            MessageTimelineEvent(kind = "reasoning", content = "think", startedAt = 1),
            MessageTimelineEvent(kind = "text", content = "**Buil", startedAt = 2),
            MessageTimelineEvent(kind = "reasoning", content = " more", startedAt = 3),
            MessageTimelineEvent(kind = "text", content = "d a UI**", startedAt = 4),
        )

        val materialized = materializeTimelineContent(events, content = "", reasoning = "")

        assertEquals(listOf("reasoning", "text"), materialized.map { it.kind })
        assertEquals(listOf("think more", "**Build a UI**"), materialized.map { it.content })
    }

    @Test
    fun toolEventsRemainHardTimelineBoundaries() {
        val events = listOf(
            MessageTimelineEvent(kind = "text", content = "before", startedAt = 1),
            MessageTimelineEvent(kind = "search", input = "query", startedAt = 2),
            MessageTimelineEvent(kind = "text", content = "after", startedAt = 3),
        )

        val materialized = materializeTimelineContent(events, content = "", reasoning = "")

        assertEquals(listOf("text", "search", "text"), materialized.map { it.kind })
        assertEquals(listOf("before", "", "after"), materialized.map { it.content })
    }

    private fun event(kind: String, content: String) = MessageTimelineEvent(
        kind = kind,
        content = content,
        startedAt = 1,
    )
}
