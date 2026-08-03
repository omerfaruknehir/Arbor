package app.xylune.chat.sandbox

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunRecordStoreTest {
    private val root = Files.createTempDirectory("xylune-run-records").toFile()
    private val store = RunRecordStore { conversation -> File(root, conversation).also(File::mkdirs) }

    @After fun clean() { root.deleteRecursively() }

    private fun run(source: String = "alpha = 1\nvalue = alpha + missing\nprint(value)\n") =
        store.create("conversation", ScriptRuntime.PYTHON, source, "python", emptyList(), 45, mapOf("SECRET" to "never persist", "python" to "3.12"))

    @Test fun failedScriptReceivesTwoLinePatchAndCanSucceedAtNextAttempt() {
        var metadata = run()
        metadata = store.markStarted(metadata, 45, emptyList())
        val failed = store.finish(metadata, "", "File main.py, line 2\nNameError: missing", 1, false, false, 20, emptyList())
        val patch = """--- a/main.py
            |+++ b/main.py
            |@@ -1,3 +1,3 @@
            | alpha = 1
            |-value = alpha + missing
            |+value = alpha + 2
            | print(value)
        """.trimMargin()
        val applied = store.applyPatch("conversation", failed.scriptPath, patch, failed.sourceSha256)
        metadata = store.markStarted(store.load("conversation", failed.runId), 45, emptyList())
        val succeeded = store.finish(metadata, "3\n", "", 0, false, false, 15, emptyList())
        assertEquals(2, applied.changedLines)
        assertEquals(2, succeeded.revision)
        assertEquals(0, succeeded.exitCode)
        assertEquals(2, succeeded.attempt)
    }

    @Test fun unchangedLinesRemainByteIdentical() {
        val original = "α = 1\nproblem = nope\ntrailing = 'unchanged'\n"
        val metadata = run(original)
        val patch = """@@ -1,3 +1,3 @@
            | α = 1
            |-problem = nope
            |+problem = 2
            | trailing = 'unchanged'
        """.trimMargin()
        store.applyPatch("conversation", metadata.scriptPath, patch, metadata.sourceSha256)
        val updated = store.resolveFile("conversation", metadata.scriptPath, true).readText()
        assertEquals("α = 1", updated.lineSequence().first())
        assertTrue(updated.endsWith("trailing = 'unchanged'\n"))
    }

    @Test fun unchangedCrLfLinesKeepTheirOriginalBytes() {
        val original = "first\r\nbroken\r\nlast\r\n"
        val metadata = run(original)
        val patch = "@@ -1,3 +1,3 @@\n first\n-broken\n+fixed\n last"
        store.applyPatch("conversation", metadata.scriptPath, patch, metadata.sourceSha256)
        assertEquals("first\r\nfixed\r\nlast\r\n", store.scriptFile(metadata).readText())
    }

    @Test fun staleHashIsRejectedWithoutChangingFile() {
        val metadata = run()
        val file = store.resolveFile("conversation", metadata.scriptPath, true)
        val before = file.readBytes()
        val result = runCatching { store.applyPatch("conversation", metadata.scriptPath, "@@ -1 +1 @@\n-alpha = 1\n+alpha = 2", "0".repeat(64)) }
        assertTrue(result.isFailure)
        assertTrue(before.contentEquals(file.readBytes()))
    }

    @Test fun malformedPatchIsAtomic() {
        val metadata = run()
        val file = store.resolveFile("conversation", metadata.scriptPath, true)
        val beforeHash = RunRecordStore.sha256(file)
        assertTrue(runCatching { store.applyPatch("conversation", metadata.scriptPath, "@@ -1 +1 @@\n-not the context\n+changed", beforeHash) }.isFailure)
        assertEquals(beforeHash, RunRecordStore.sha256(file))
    }

    @Test fun ambiguousMultiFilePatchIsRejectedAtomically() {
        val metadata = run()
        val file = store.scriptFile(metadata)
        val before = RunRecordStore.sha256(file)
        val patch = """--- a/main.py
            |+++ b/main.py
            |@@ -1 +1 @@
            |-alpha = 1
            |+alpha = 2
            |--- a/other.py
            |+++ b/other.py
            |@@ -1 +1 @@
            |-x
            |+y
        """.trimMargin()
        assertTrue(runCatching { store.applyPatch("conversation", metadata.scriptPath, patch, before) }.isFailure)
        assertEquals(before, RunRecordStore.sha256(file))
    }

    @Test fun pathTraversalIsRejected() {
        assertTrue(runCatching { store.readWorkspace("conversation", "../outside", null, null, null) }.isFailure)
        assertTrue(runCatching { store.resolveFile("conversation", "/tmp/outside", false) }.isFailure)
    }

    @Test fun rerunMetadataReferencesCanonicalSourceWithoutDuplicatingIt() {
        val metadata = run()
        val before = store.scriptFile(metadata).readBytes()
        val rerun = store.markStarted(store.load("conversation", metadata.runId), metadata.timeoutSeconds, emptyList())
        assertEquals(metadata.scriptPath, rerun.scriptPath)
        assertTrue(before.contentEquals(store.scriptFile(rerun).readBytes()))
        assertEquals(1, File(root, "conversation/.xylune/runs/${metadata.runId}").listFiles { file -> file.name.startsWith("main.") }!!.size)
    }

    @Test fun diagnosticsAndSourceExcerptAreBoundedAndRelevant() {
        var metadata = run((1..1_000).joinToString("\n") { "line_$it = $it" } + "\n")
        metadata = store.markStarted(metadata, 45, emptyList())
        val result = store.finish(metadata, "x".repeat(50_000), "e".repeat(20_000) + "\nmain.py:500: failure", 1, false, false, 9, emptyList())
        assertTrue(result.stdoutTail.length <= 8_000)
        assertTrue(result.stderrTail.length <= 8_000)
        assertTrue("   500 | line_500 = 500" in result.sourceExcerpt)
        assertFalse(result.instruction.contains("line_1 = 1"))
    }

    @Test fun timeoutAndCancellationRemainRecordedPerAttempt() {
        var metadata = store.markStarted(run(), 1, emptyList())
        store.finish(metadata, "", "timeout", 124, true, false, 1_000, emptyList())
        metadata = store.markStarted(store.load("conversation", metadata.runId), 1, emptyList())
        store.finish(metadata, "", "cancelled", 130, false, true, 10, emptyList())
        val loaded = store.load("conversation", metadata.runId)
        assertEquals(2, loaded.attempts.size)
        assertTrue(loaded.attempts.first().timedOut)
        assertTrue(loaded.attempts.last().cancelled)
    }

    @Test fun unsafeEnvironmentValuesAreNeverPersisted() {
        val metadata = run()
        assertFalse("SECRET" in metadata.environment)
        assertNotEquals("never persist", metadata.environment["python"])
    }
}
