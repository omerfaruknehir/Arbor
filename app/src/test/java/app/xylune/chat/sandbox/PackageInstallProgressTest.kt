package app.xylune.chat.sandbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Reader

class PackageInstallProgressTest {
    @Test
    fun parsesAptDownloadStatusIntoOverallProgress() {
        val progress = packageInstallProgressFromApt(
            ExecutionProgress(
                stdoutTail = "Get:1 package\ndlstatus:libssl3:50.0000:Downloading libssl3",
                elapsedMs = 2_500,
            ),
            fallbackPhase = "Installing packages",
            rangeStart = 0.30f,
            rangeEnd = 0.90f,
        )

        assertEquals("Downloading packages", progress.phase)
        assertEquals("libssl3", progress.currentPackage)
        assertEquals(0.60f, progress.percent ?: -1f, 0.001f)
        assertEquals(2_500L, progress.elapsedMs)
        assertFalse(progress.stdoutTail.contains("dlstatus:"))
    }

    @Test
    fun parsesDpkgConfigurationPhaseAndPreservesLiveOutput() {
        val progress = packageInstallProgressFromApt(
            ExecutionProgress(
                stdoutTail = "Unpacking dependency\npmstatus:ffmpeg:72.0000:Setting up ffmpeg",
                stderrTail = "debconf: delaying package configuration",
            ),
            fallbackPhase = "Installing packages",
            rangeStart = 0.30f,
            rangeEnd = 0.99f,
        )

        assertEquals("Configuring packages", progress.phase)
        assertEquals("ffmpeg", progress.currentPackage)
        assertTrue(progress.detail.contains("Setting up ffmpeg"))
        assertTrue(progress.stderrTail.contains("debconf"))
    }

    @Test
    fun parsesApkPackageCounterIntoProgress() {
        val progress = packageInstallProgressFromOutput(
            ExecutionProgress(stdoutTail = "(6/12) Installing python3 (3.12.11-r0)"),
            fallbackPhase = "Installing Python tools",
            rangeStart = 0.74f,
            rangeEnd = 0.98f,
        )

        assertEquals(0.86f, progress.percent ?: -1f, 0.001f)
        assertTrue(progress.detail.contains("Installing python3"))
    }

    @Test
    fun cappedOutputStillDrainsTheEntireChildPipe() {
        val source = "x".repeat(32_768)
        var consumed = 0
        val reader = object : Reader() {
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                if (consumed >= source.length) return -1
                val count = minOf(length, source.length - consumed)
                source.toCharArray(buffer, offset, consumed, consumed + count)
                consumed += count
                return count
            }

            override fun close() = Unit
        }
        val retained = StringBuilder()

        drainCappedText(reader, retained, 256)

        assertEquals(source.length, consumed)
        assertEquals(256, retained.length)
    }

    @Test
    fun fileBackedCaptureKeepsAReadableTailWithoutPipes() {
        val log = java.io.File.createTempFile("xylune-log", ".txt")
        try {
            val body = "prefix-" + "x".repeat(32_000) + "-final-status"
            log.writeText(body)

            assertEquals(body.take(512), readCappedLogFile(log, 512))
            assertEquals(body.takeLast(256), readLogTail(log, 256))
        } finally {
            log.delete()
        }
    }

    @Test
    fun fallsBackToHumanReadableAptOutput() {
        val progress = packageInstallProgressFromApt(
            ExecutionProgress(stdoutTail = "Reading package lists... Done\nBuilding dependency tree... Done"),
            fallbackPhase = "Preparing",
            rangeStart = 0f,
            rangeEnd = 1f,
        )

        assertEquals("Resolving dependencies", progress.phase)
        assertEquals(null, progress.percent)
    }
}
