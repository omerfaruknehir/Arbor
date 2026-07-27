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
        assertTrue(!defaults.diagnosticProfilerEnabled)
        assertEquals(PerformanceOverlayPosition.TOP_END, defaults.performanceOverlayPosition)
        assertEquals(250, defaults.copy(performanceUpdateIntervalMs = 1).normalized().performanceUpdateIntervalMs)
        assertEquals(2_000, defaults.copy(performanceUpdateIntervalMs = 5_000).normalized().performanceUpdateIntervalMs)
        assertEquals(0f, defaults.copy(performanceOverlayBackgroundOpacity = -1f).normalized().performanceOverlayBackgroundOpacity)
        assertEquals(1f, defaults.copy(performanceOverlayBackgroundOpacity = 2f).normalized().performanceOverlayBackgroundOpacity)
        assertEquals(0f, defaults.copy(performanceOverlayTextOpacity = -1f).normalized().performanceOverlayTextOpacity)
    }

    @Test
    fun settingsAndRootContainDeveloperPerformanceWiring() {
        val settingsSource = java.io.File("src/main/java/app/arbor/chat/ui/SettingsScreen.kt").readText()
        val rootSource = java.io.File("src/main/java/app/arbor/chat/ui/ArborApp.kt").readText()
        assertTrue(settingsSource.contains("DEVELOPER(\"Developer settings\")"))
        assertTrue(settingsSource.contains("Show performance overlay"))
        assertTrue(settingsSource.contains("Detailed metrics"))
        assertTrue(settingsSource.contains("Panel opacity"))
        assertTrue(settingsSource.contains("click-through"))
        assertTrue(rootSource.contains("ArborPerformanceOverlay"))
        assertTrue(rootSource.contains("PerformanceOverlayHost"))
        assertTrue(rootSource.contains("val snapshot by monitor.snapshot.collectAsState()"))
        assertTrue(!rootSource.contains("val performanceSnapshot by performanceMonitor.snapshot.collectAsState()"))
        assertTrue(rootSource.contains("showPerformanceOverlay"))
    }

    @Test
    fun frameIntervalAndFpsAreReciprocals() {
        val fps = 70.0
        val intervalMs = 1_000.0 / fps
        assertEquals(fps, 1_000.0 / intervalMs, 0.0001)
    }

    @Test
    fun highRefreshBudgetFlagsOnlyActuallyMissedVsyncs() {
        val budget120Hz = 1_000.0 / 120.0
        assertEquals(0, estimatedMissedFrames(8.0, budget120Hz))
        assertEquals(1, estimatedMissedFrames(16.6, budget120Hz))
        assertEquals(3, estimatedMissedFrames(33.3, budget120Hz))
    }
    @Test
    fun causeDetectorAttributesGpuPressureWhileBlurIsActive() {
        val cause = detectLikelyBottleneck(
            PerformanceCauseInput(
                refreshRateHz = 120f, fps = 30.0, frameTotalMs = 30.0, frameDurationP95Ms = 33.0, jankPercent = 20.0, gpuMs = 19.0,
                inputMs = 0.1, animationMs = 0.2, layoutMs = 1.0, drawMs = 2.0,
                syncMs = 0.5, commandMs = 2.0, swapMs = 1.0, blurCpuMs = 0.5,
                blurFrames = 30, blurSourceDrawsPerFrame = 1.0, appRecompositionsPerSecond = 10.0,
                chatRecompositionsPerSecond = 10.0, allocationMbPerSecond = 2.0,
                blockingGcPerSecond = 0.0,
            ),
        )
        assertEquals("GPU rendering (blur active)", cause)
    }

    @Test
    fun healthyGpuStageIsNotBlamedForSchedulingSpikes() {
        val cause = detectLikelyBottleneck(
            PerformanceCauseInput(
                refreshRateHz = 120f, fps = 98.0, frameTotalMs = 11.0,
                frameDurationP95Ms = 25.0, jankPercent = 4.0, gpuMs = 2.5,
                inputMs = 0.1, animationMs = 0.1, layoutMs = 0.1, drawMs = 2.2,
                syncMs = 0.4, commandMs = 1.4, swapMs = 0.7, blurCpuMs = 0.12,
                blurFrames = 49, blurSourceDrawsPerFrame = 1.0,
                appRecompositionsPerSecond = 0.0, chatRecompositionsPerSecond = 0.0,
                allocationMbPerSecond = 3.0, blockingGcPerSecond = 0.0,
            ),
        )
        assertEquals("Frame pacing / scheduling stalls", cause)
    }

    @Test
    fun duplicateBlurTraversalIsReportedBeforeGenericGpuAttribution() {
        val cause = detectLikelyBottleneck(
            PerformanceCauseInput(
                refreshRateHz = 120f, fps = 80.0, frameTotalMs = 12.0,
                frameDurationP95Ms = 20.0, jankPercent = 8.0, gpuMs = 3.0,
                inputMs = 0.1, animationMs = 0.1, layoutMs = 0.3, drawMs = 2.0,
                syncMs = 0.4, commandMs = 1.0, swapMs = 0.5, blurCpuMs = 0.5,
                blurFrames = 40, blurSourceDrawsPerFrame = 2.0,
                appRecompositionsPerSecond = 0.0, chatRecompositionsPerSecond = 0.0,
                allocationMbPerSecond = 2.0, blockingGcPerSecond = 0.0,
            ),
        )
        assertEquals("Duplicate content recording for blur", cause)
    }

    @Test
    fun causeDetectorAttributesLayoutPressure() {
        val cause = detectLikelyBottleneck(
            PerformanceCauseInput(
                refreshRateHz = 120f, fps = 55.0, frameTotalMs = 17.0, frameDurationP95Ms = 20.0, jankPercent = 10.0, gpuMs = 2.0,
                inputMs = 0.2, animationMs = 0.2, layoutMs = 9.0, drawMs = 2.0,
                syncMs = 0.3, commandMs = 1.0, swapMs = 0.5, blurCpuMs = 0.2,
                blurFrames = 20, blurSourceDrawsPerFrame = 1.0, appRecompositionsPerSecond = 10.0,
                chatRecompositionsPerSecond = 10.0, allocationMbPerSecond = 2.0,
                blockingGcPerSecond = 0.0,
            ),
        )
        assertEquals("Layout / measure", cause)
    }

    @Test
    fun causeDetectorPrioritizesBlockingGcPressure() {
        val cause = detectLikelyBottleneck(
            PerformanceCauseInput(
                refreshRateHz = 120f, fps = 40.0, frameTotalMs = 24.0, frameDurationP95Ms = 30.0, jankPercent = 20.0, gpuMs = 4.0,
                inputMs = 0.2, animationMs = 0.2, layoutMs = 2.0, drawMs = 3.0,
                syncMs = 0.3, commandMs = 1.0, swapMs = 0.5, blurCpuMs = 0.2,
                blurFrames = 20, blurSourceDrawsPerFrame = 1.0, appRecompositionsPerSecond = 10.0,
                chatRecompositionsPerSecond = 10.0, allocationMbPerSecond = 40.0,
                blockingGcPerSecond = 1.0,
            ),
        )
        assertEquals("Allocation / blocking GC pressure", cause)
    }

    @Test
    fun profilerWiringExistsAcrossSettingsRootChatAndBlur() {
        val settings = java.io.File("src/main/java/app/arbor/chat/ui/SettingsScreen.kt").readText()
        val root = java.io.File("src/main/java/app/arbor/chat/ui/ArborApp.kt").readText()
        val chat = java.io.File("src/main/java/app/arbor/chat/ui/ChatScreen.kt").readText()
        val blur = java.io.File("src/main/java/app/arbor/chat/ui/BackdropBlur.kt").readText()
        val overlay = java.io.File("src/main/java/app/arbor/chat/ui/PerformanceOverlay.kt").readText()
        assertTrue(settings.contains("Cause profiler"))
        assertTrue(root.contains("diagnosticProfilerEnabled"))
        assertTrue(chat.contains("recordChatRecomposition"))
        assertTrue(blur.contains("recordBlurFrame"))
        assertTrue(blur.contains("recordBlurEffectBuild"))
        assertTrue(overlay.contains("FrameMetrics.LAYOUT_MEASURE_DURATION"))
        assertTrue(overlay.contains("Likely:"))
    }

    @Test
    fun renderedFrameEstimatorCannotExceedThePhysicalDisplayRate() {
        assertEquals(120.0, boundedRenderedFrameRate(130.0, 120f), 0.0)
        assertEquals(93.5, boundedRenderedFrameRate(93.5, 120f), 0.0)
        assertEquals(0.0, boundedRenderedFrameRate(-4.0, 120f), 0.0)
    }

    @Test
    fun profilerSeparatesDisplayCallbacksRenderedAndPresentedFrames() {
        val source = java.io.File("src/main/java/app/arbor/chat/ui/PerformanceOverlay.kt").readText()
        assertTrue(source.contains("displayRefreshRateHz"))
        assertTrue(source.contains("choreographerCallbackRate"))
        assertTrue(source.contains("appRenderedFrameRate"))
        assertTrue(source.contains("presentedFrameRate"))
        assertTrue(source.contains("Display "))
        assertTrue(source.contains("Callback "))
        assertTrue(source.contains("Present "))
        assertTrue(source.contains("blurDownsampleLevels"))
        assertTrue(source.contains("blurUpsampleLevels"))
    }

    @Test
    fun arborDoesNotForceRefreshRateOrPerformanceClocks() {
        val source = java.io.File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }
        assertTrue(!source.contains("setFrameRate("))
        assertTrue(!source.contains("preferredDisplayModeId"))
        assertTrue(!source.contains("setSustainedPerformanceMode"))
        assertTrue(!source.contains("PerformanceHintManager"))
        assertTrue(!source.contains("FULL_WAKE_LOCK"))
        assertTrue(!source.contains("PARTIAL_WAKE_LOCK"))
        assertTrue(!source.contains("setPowerSaveMode"))
    }

    @Test
    fun overlayIsVisualOnlyAndHasNoPointerConsumer() {
        val source = java.io.File("src/main/java/app/arbor/chat/ui/PerformanceOverlay.kt").readText()
        val function = source.substringAfter("internal fun ArborPerformanceOverlay(").substringBefore("private fun Double.f0")
        assertTrue(function.contains("backgroundOpacity"))
        assertTrue(function.contains("textOpacity"))
        assertTrue(!function.contains(".clickable("))
        assertTrue(!function.contains(".pointerInput("))
    }

}
