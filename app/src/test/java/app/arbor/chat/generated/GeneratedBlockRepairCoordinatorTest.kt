package app.arbor.chat.generated

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedBlockRepairCoordinatorTest {
    private val root = Files.createTempDirectory("arbor-repair").toFile()
    private fun workspace(conversation: String) = File(root, conversation).also(File::mkdirs)

    @After fun clean() { root.deleteRecursively() }

    @Test fun invalidWidgetRepairsSuccessfullyOnAttemptOne() = runTest {
        val coordinator = GeneratedBlockRepairCoordinator(::workspace) { """```arbor-snippet
{"schema":"arbor-snippet/1","id":"pick","title":"Pick","state":{"answer":""},"ui":{"type":"choice","value":"answer","options":[{"label":"A","value":"A"},{"label":"B","value":"B"}]},"actions":{}}
```""" }
        val result = coordinator.repair("c", "m", "b", GeneratedBlockType.CHAT_UI, """{"type":"graph"}""", errors("bad type"), 3)
        assertEquals(GeneratedRepairStatus.ACCEPTED, result.status)
        assertEquals(1, result.attemptCount)
    }

    @Test fun invalidChartRepairsOnAttemptTwo() = runTest {
        var calls = 0
        val coordinator = GeneratedBlockRepairCoordinator(::workspace) {
            calls++
            if (calls == 1) """```arbor-chart
{"type":"made-up","series":[]}
```""" else """```arbor-chart
{"type":"bar","series":[{"name":"Value","values":[{"label":"A","value":1}]}]}
```"""
        }
        val result = coordinator.repair("c", "m", "chart", GeneratedBlockType.CHART, "{}", errors("invalid"), 3)
        assertEquals(GeneratedRepairStatus.ACCEPTED, result.status)
        assertEquals(2, result.attemptCount)
    }

    @Test fun identicalInvalidCandidateThreeTimesEndsInFailure() = runTest {
        val invalid = """```arbor-chart
{"type":"graph","series":[]}
```"""
        val coordinator = GeneratedBlockRepairCoordinator(::workspace) { invalid }
        val result = coordinator.repair("c", "m", "same", GeneratedBlockType.CHART, "{}", errors("invalid"), 3)
        assertEquals(GeneratedRepairStatus.EXHAUSTED, result.status)
        assertEquals(3, result.attemptCount)
        assertFalse(result.attempts.first().repeatedCandidate)
        assertTrue(result.attempts.drop(1).all { it.repeatedCandidate })
    }

    @Test fun validBlockIsNeverSubmittedForRepair() = runTest {
        var calls = 0
        val valid = GeneratedContentCapabilityRegistry.validExamples.getValue(GeneratedBlockType.CHART).first()
        val validation = GeneratedContentCapabilityRegistry.validate(GeneratedBlockType.CHART, valid)
        if (!validation.valid) GeneratedBlockRepairCoordinator(::workspace) { calls++; "" }.repair("c", "m", "valid", GeneratedBlockType.CHART, valid, validation.errors, 3)
        assertEquals(0, calls)
    }

    @Test fun proseAndMultipleBlocksAreRejectedAsMalformedRepairResponses() {
        assertTrue(GeneratedContentCapabilityRegistry.extractSingleReplacement("Here:\n```arbor-snippet\n{}\n```", "arbor-snippet").isFailure)
        assertTrue(GeneratedContentCapabilityRegistry.extractSingleReplacement("```arbor-snippet\n{}\n```\n```arbor-snippet\n{}\n```", "arbor-snippet").isFailure)
    }

    @Test fun explicitUserRetryStartsFreshBoundedCycle() = runTest {
        var valid = false
        val coordinator = GeneratedBlockRepairCoordinator(::workspace) {
            if (valid) """```arbor-chart
{"type":"bar","series":[{"name":"V","values":[{"label":"A","value":1}]}]}
```""" else """```arbor-chart
{}
```"""
        }
        val first = coordinator.repair("c", "m", "retry", GeneratedBlockType.CHART, "{}", errors("invalid"), 1)
        assertEquals(GeneratedRepairStatus.EXHAUSTED, first.status)
        valid = true
        val retried = coordinator.repair("c", "m", "retry", GeneratedBlockType.CHART, "{}", errors("invalid"), 1, newCycle = true)
        assertEquals(GeneratedRepairStatus.ACCEPTED, retried.status)
        assertEquals(2, retried.cycle)
        assertEquals(1, retried.attemptCount)
    }

    @Test fun processRecreationDoesNotDuplicateAcceptedAttempt() = runTest {
        var calls = 0
        fun coordinator() = GeneratedBlockRepairCoordinator(::workspace) {
            calls++
            """```arbor-snippet
{"schema":"arbor-snippet/1","id":"pick","state":{"answer":""},"ui":{"type":"choice","value":"answer","options":[{"label":"A","value":"A"}]},"actions":{}}
```"""
        }
        coordinator().repair("c", "m", "persisted", GeneratedBlockType.CHAT_UI, "{}", errors("invalid"), 3)
        val restored = coordinator().repair("c", "m", "persisted", GeneratedBlockType.CHAT_UI, "{}", errors("invalid"), 3)
        assertEquals(GeneratedRepairStatus.ACCEPTED, restored.status)
        assertEquals(1, calls)
    }

    @Test fun multipleInvalidBlocksHaveIndependentState() = runTest {
        val coordinator = GeneratedBlockRepairCoordinator(::workspace) { state ->
            if (state.type == GeneratedBlockType.DIAGRAM) """```mermaid
flowchart TD
A[Start] --> B[Done]
```""" else """```arbor-chart
{"type":"bar","series":[{"name":"V","values":[{"label":"A","value":1}]}]}
```"""
        }
        val chart = coordinator.repair("c", "m", "one", GeneratedBlockType.CHART, "{}", errors("bad"), 3)
        val diagram = coordinator.repair("c", "m", "two", GeneratedBlockType.DIAGRAM, "bad", errors("bad"), 3)
        assertEquals(GeneratedRepairStatus.ACCEPTED, chart.status)
        assertEquals(GeneratedRepairStatus.ACCEPTED, diagram.status)
        assertFalse(chart.blockId == diagram.blockId)
    }

    private fun errors(message: String) = listOf(GeneratedValidationError("schema", "/", message))
}
