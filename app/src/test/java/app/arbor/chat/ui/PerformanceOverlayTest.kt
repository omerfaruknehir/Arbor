package app.arbor.chat.ui

import app.arbor.chat.settings.DeveloperSettings
import app.arbor.chat.settings.PerformanceOverlayPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceOverlayTest {
    @Test
    fun updateIntervalIsBounded() {
        assertEquals(250, normalizedPerformanceIntervalMs(1))
        assertEquals(500, normalizedPerformanceIntervalMs(500))
        assertEquals(2_000, normalizedPerformanceIntervalMs(9_000))
    }

    @Test
    fun percentileUsesNearestRank() {
        val values = (1..100).map(Int::toDouble)
        assertEquals(95.0, performancePercentile(values, 0.95), 0.0)
        assertEquals(99.0, performancePercentile(values, 0.99), 0.0)
        assertEquals(0.0, performancePercentile(emptyList(), 0.95), 0.0)
    }

    @Test
    fun missedFramesAreEstimatedFromFrameBudget() {
        assertEquals(0, estimatedMissedFrames(8.0, 16.67))
        assertEquals(0, estimatedMissedFrames(16.67, 16.67))
        assertEquals(1, estimatedMissedFrames(20.0, 16.67))
        assertEquals(2, estimatedMissedFrames(40.0, 16.67))
    }

    @Test
    fun developerSettingsNormalizeIntervalAndKeepDefaultsOff() {
        val defaults = DeveloperSettings()
        assertTrue(!defaults.enabled)
        assertTrue(!defaults.performanceOverlayEnabled)
        assertEquals(PerformanceOverlayPosition.TOP_END, defaults.performanceOverlayPosition)
        assertEquals(250, defaults.copy(performanceUpdateIntervalMs = 1).normalized().performanceUpdateIntervalMs)
        assertEquals(2_000, defaults.copy(performanceUpdateIntervalMs = 5_000).normalized().performanceUpdateIntervalMs)
    }

    @Test
    fun settingsAndRootContainDeveloperPerformanceWiring() {
        val settingsSource = java.io.File("src/main/java/app/arbor/chat/ui/SettingsScreen.kt").readText()
        val rootSource = java.io.File("src/main/java/app/arbor/chat/ui/ArborApp.kt").readText()
        assertTrue(settingsSource.contains("DEVELOPER(\"Developer settings\")"))
        assertTrue(settingsSource.contains("Show performance overlay"))
        assertTrue(settingsSource.contains("Detailed metrics"))
        assertTrue(rootSource.contains("ArborPerformanceOverlay"))
        assertTrue(rootSource.contains("showPerformanceOverlay"))
    }
}
