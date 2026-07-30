package app.arbor.chat.ui

import app.arbor.chat.agent.MessageTimelineEvent
import app.arbor.chat.data.ReasoningVisibility
import app.arbor.chat.sandbox.ScriptRunResult
import app.arbor.chat.sandbox.ScriptRuntime
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
    fun composerKeepsActiveActionsCompactAndPredictable() {
        val chat = java.io.File("src/main/java/app/arbor/chat/ui/ChatScreen.kt").readText()
        val composer = chat.substringAfter("private fun Composer(").substringBefore("private fun StagedAttachmentPreview")

        assertTrue(composer.contains("generating -> \"Working\""))
        assertTrue(composer.contains("viewModel.send(if (generating) SendMode.STEER else SendMode.SEND_NOW)"))
        assertTrue(composer.contains("Queue this message"))
        assertTrue(composer.contains("Stop current response"))
        assertTrue(composer.contains("if (providerConfigured && !generating)"))
        assertFalse(composer.contains("Arbor is working in the background"))
        assertFalse(composer.contains("Choose Queue, Steer, or separate turn"))
        assertFalse(composer.contains("Start a separate turn"))
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

    @Test
    fun failedScriptSummaryKeepsOnlyTheUsefulErrorLine() {
        val result = ScriptRunResult(
            runId = "run-1",
            revision = 1,
            attempt = 1,
            runtime = ScriptRuntime.PYTHON,
            scriptPath = ".arbor/runs/run-1/main.py",
            sourceSha256 = "abc",
            exitCode = 1,
            timedOut = false,
            cancelled = false,
            elapsedMs = 952,
            diagnostic = """
                Traceback (most recent call last):
                  File "main.py", line 20
                urllib.error.HTTPError: HTTP Error 404: Not Found
            """.trimIndent(),
        )

        assertEquals(
            "urllib.error.HTTPError: HTTP Error 404: Not Found",
            scriptRunSummary(result),
        )
    }

    @Test
    fun completedAndFailedStepsStayCompactAndDiagnosticsRequireDeveloperMode() {
        val chat = java.io.File("src/main/java/app/arbor/chat/ui/ChatScreen.kt").readText()
        val step = chat.substringAfter("private fun TimelineWorkStep").substringBefore("internal fun scriptRunId")
        val toolDetails = chat.substringAfter("private fun ToolStepDetails").substringBefore("internal fun scriptRunSummary")

        assertTrue(step.contains("mutableStateOf(active)"))
        assertTrue(step.contains("expanded = active"))
        assertFalse(step.contains("active || event.status == \"error\""))
        assertTrue(toolDetails.contains("developerSettings.enabled && developerSettings.toolDiagnosticsEnabled"))
        assertTrue(toolDetails.contains("if (showDiagnostics && input.isNotBlank())"))
        assertTrue(toolDetails.contains("if (showDiagnostics && detailsOpen)"))
        assertFalse(toolDetails.contains("Text(\"Copy path\")"))
        assertFalse(toolDetails.contains("Text(\"Copy diagnostics\")"))
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
