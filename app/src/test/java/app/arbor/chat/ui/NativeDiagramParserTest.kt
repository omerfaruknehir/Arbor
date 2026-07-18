package app.arbor.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeDiagramParserTest {
    @Test fun parsesLabeledFlowAndChainedEdges() {
        val diagram = NativeDiagramParser.parse("""
            flowchart LR
            A[Request] -->|valid| B{Check} --> C[Done]
        """.trimIndent())
        assertEquals("LR", diagram.direction)
        assertEquals(3, diagram.nodes.size)
        assertEquals(2, diagram.edges.size)
        assertEquals("valid", diagram.edges.first().label)
    }

    @Test fun parsesSequenceMessagesInOrder() {
        val diagram = NativeDiagramParser.parse("""
            sequenceDiagram
            participant U as User
            participant A as Arbor
            U->>A: Send file
            A-->>U: File card
        """.trimIndent())
        assertTrue(diagram.sequence)
        assertEquals(listOf("Send file", "File card"), diagram.edges.map { it.label })
    }

    @Test fun parsesBasicGraphvizDotWithoutAWebRenderer() {
        val diagram = NativeDiagramParser.parse("digraph G { rankdir=LR; A -> B [label=works]; }")
        assertEquals("LR", diagram.direction)
        assertEquals("works", diagram.edges.single().label)
    }

    @Test fun parsesAllMermaidNodeDelimitersWithoutRegexCompilation() {
        val diagram = NativeDiagramParser.parse("""
            flowchart TB
            A[Square] --> B(Round) --> C{Decision}
        """.trimIndent())
        assertEquals(listOf("Square", "Round", "Decision"), diagram.nodes.map { it.label })
        assertEquals(2, diagram.edges.size)
    }

    @Test fun malformedNodeDelimiterFallsBackWithoutCrashing() {
        val diagram = NativeDiagramParser.parse("flowchart LR\nA{unfinished --> B[Done]")
        assertTrue(diagram.nodes.any { it.id == "A" })
    }
}
