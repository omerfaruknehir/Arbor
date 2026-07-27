package app.arbor.chat.sandbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
