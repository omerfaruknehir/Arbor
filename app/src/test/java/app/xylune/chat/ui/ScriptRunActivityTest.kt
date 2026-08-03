package app.xylune.chat.ui

import app.xylune.chat.sandbox.ScriptRunResult
import app.xylune.chat.sandbox.ScriptRuntime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptRunActivityTest {
    @Test fun compactResultIdentifiesDurableRunWithoutContainingCompleteSource() {
        val result = ScriptRunResult(
            runId = "run-12345678",
            revision = 2,
            attempt = 3,
            runtime = ScriptRuntime.PYTHON,
            scriptPath = ".xylune/runs/run-12345678/main.py",
            sourceSha256 = "a".repeat(64),
            exitCode = 0,
            timedOut = false,
            cancelled = false,
            elapsedMs = 10,
        )
        val encoded = Json.encodeToString(result)
        assertEquals("run-12345678", scriptRunId(encoded))
        assertTrue("main.py" in encoded)
        assertTrue("source" !in encoded.lowercase() || "sourceSha256" in encoded)
    }

    @Test fun ordinaryToolOutputIsNotMistakenForScriptActivity() {
        assertNull(scriptRunId("{\"stdout\":\"hello\"}"))
    }
}
