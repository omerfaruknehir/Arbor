package app.xylune.chat.generation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallStreamingTest {
    @Test fun incompletePythonJsonExposesCodeAsItStreams() {
        val presentation = toolCallPresentation("python", """{"code":"import os\nprint(os.getc""")
        assertEquals("python", presentation.kind)
        assertEquals("import os\nprint(os.getc", presentation.input)
        assertEquals("Preparing Python tool call", presentation.preparingLabel)
    }

    @Test fun escapedCharactersAreDecodedWithoutWaitingForClosingJson() {
        val value = partialJsonString("""{"command":"printf \"hello\"\nnext""", "command")
        assertEquals("printf \"hello\"\nnext", value)
    }


    @Test fun widgetCompilerHidesRawCandidateFromActivitySummary() {
        val presentation = toolCallPresentation(
            "compile_widget",
            """{"source":"{\"schema\":\"xylune-widget/1\"}"}""",
        )
        assertEquals("widget_compile", presentation.kind)
        assertTrue(presentation.input.startsWith("Internal widget candidate"))
        assertTrue(!presentation.input.contains("xylune-widget/1"))
        assertEquals("Compiling Home widget", presentation.runningLabel)
    }

    @Test fun unknownToolsStillExposeBoundedRawArguments() {
        val presentation = toolCallPresentation("custom_tool", "{" + "x".repeat(5_000))
        assertEquals("tool_call", presentation.kind)
        assertEquals(4_000, presentation.input.length)
        assertTrue(presentation.preparingLabel.contains("custom_tool"))
    }
}
