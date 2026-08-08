package app.xylune.chat.ui

import android.app.Activity
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.max

internal data class PerformanceSnapshot(
    /** Legacy compact-overlay alias for appRenderedFrameRate. */
    val fps: Double = 0.0,
    val displayRefreshRateHz: Float = 60f,
    val choreographerCallbackRate: Double = 0.0,
    val appRenderedFrameRate: Double = 0.0,
    val presentedFrameRate: Double? = null,
    val averageFrameMs: Double = 0.0,
    val p95FrameMs: Double = 0.0,
    val p99FrameMs: Double = 0.0,
    val gpuAverageMs: Double? = null,
    val jankPercent: Double = 0.0,
    val missedFramesPerSecond: Double = 0.0,
    val droppedMetricReports: Long = 0,
    val cpuPercent: Double = 0.0,
    val pssMb: Double = 0.0,
    val javaHeapMb: Double = 0.0,
    val refreshRateHz: Float = 60f,
    val totalFrames: Long = 0,
    val diagnosticProfilerActive: Boolean = false,
    val frameMetricsTotalMs: Double = 0.0,
    val inputMs: Double = 0.0,
    val animationMs: Double = 0.0,
    val layoutMeasureMs: Double = 0.0,
    val drawMs: Double = 0.0,
    val syncMs: Double = 0.0,
    val commandIssueMs: Double = 0.0,
    val swapBuffersMs: Double = 0.0,
    val blurCpuMsPerFrame: Double = 0.0,
    val blurFilteredMegapixelsPerSecond: Double = 0.0,
    val blurSourceDrawsPerFrame: Double = 0.0,
    val blurLayerReplaysPerFrame: Double = 0.0,
    val blurCaptureUpdatesPerSecond: Double = 0.0,
    val blurEffectBuildsPerSecond: Double = 0.0,
    val blurDownsampleLevels: Double = 0.0,
    val blurUpsampleLevels: Double = 0.0,
    val appRecompositionsPerSecond: Double = 0.0,
    val chatRecompositionsPerSecond: Double = 0.0,
    val allocationMbPerSecond: Double = 0.0,
    val blockingGcPerSecond: Double = 0.0,
    val screenName: String = "UNKNOWN",
    val causeProfile: PerformanceCauseProfile = PerformanceCauseProfile.disabled(),
)

internal data class RenderProfilerInterval(
    val blurCpuMsPerFrame: Double,
    val blurFilteredMegapixelsPerSecond: Double,
    val blurSourceDrawsPerFrame: Double,
    val blurLayerReplaysPerFrame: Double,
    val blurCaptureUpdatesPerSecond: Double,
    val blurEffectBuildsPerSecond: Double,
    val blurDownsampleLevels: Double,
    val blurUpsampleLevels: Double,
    val appRecompositionsPerSecond: Double,
    val chatRecompositionsPerSecond: Double,
    val blurFrames: Long,
    val screenName: String,
)

/** Xylune-owned counters which Android FrameMetrics cannot attribute by component. */
internal object XyluneRenderProfiler {
    @Volatile private var active = false
    @Volatile private var currentScreen = "UNKNOWN"

    private val blurCpuNanos = AtomicLong()
    private val blurFrames = AtomicLong()
    private val blurFilteredPixels = AtomicLong()
    private val blurSourceDraws = AtomicLong()
    private val blurLayerReplays = AtomicLong()
    private val blurEffectBuilds = AtomicLong()
    private val blurCaptureUpdates = AtomicLong()
    private val blurDownsampleLevels = AtomicLong()
    private val blurUpsampleLevels = AtomicLong()
    private val appRecompositions = AtomicLong()
    private val chatRecompositions = AtomicLong()

    val enabled: Boolean get() = active

    fun setEnabled(value: Boolean) {
        if (active == value) return
        active = value
        reset()
    }

    fun setScreen(name: String) { currentScreen = name }

    fun recordBlurFrame(
        cpuNanos: Long,
        processedPixels: Long,
        sourceTraversals: Int,
        layerReplays: Int,
        downsampleLevels: Int,
        upsampleLevels: Int,
        captureUpdates: Int,
    ) {
        if (!active) return
        blurCpuNanos.addAndGet(cpuNanos.coerceAtLeast(0L))
        blurFilteredPixels.addAndGet(processedPixels.coerceAtLeast(0L))
        blurSourceDraws.addAndGet(sourceTraversals.coerceAtLeast(0).toLong())
        blurLayerReplays.addAndGet(layerReplays.coerceAtLeast(0).toLong())
        blurDownsampleLevels.addAndGet(downsampleLevels.coerceAtLeast(0).toLong())
        blurUpsampleLevels.addAndGet(upsampleLevels.coerceAtLeast(0).toLong())
        blurCaptureUpdates.addAndGet(captureUpdates.coerceAtLeast(0).toLong())
        blurFrames.incrementAndGet()
    }

    fun recordBlurEffectBuild(count: Int = 1) {
        if (active) blurEffectBuilds.addAndGet(count.coerceAtLeast(0).toLong())
    }
    fun recordAppRecomposition() { if (active) appRecompositions.incrementAndGet() }
    fun recordChatRecomposition() { if (active) chatRecompositions.incrementAndGet() }

    fun drain(elapsedMs: Long): RenderProfilerInterval {
        val elapsedSeconds = elapsedMs.coerceAtLeast(1L) / 1_000.0
        val frames = blurFrames.getAndSet(0L)
        val cpuNs = blurCpuNanos.getAndSet(0L)
        val pixels = blurFilteredPixels.getAndSet(0L)
        val sourceDraws = blurSourceDraws.getAndSet(0L)
        val layerReplays = blurLayerReplays.getAndSet(0L)
        val effectBuilds = blurEffectBuilds.getAndSet(0L)
        val captureUpdates = blurCaptureUpdates.getAndSet(0L)
        val downsampleLevels = blurDownsampleLevels.getAndSet(0L)
        val upsampleLevels = blurUpsampleLevels.getAndSet(0L)
        val appRecomposes = appRecompositions.getAndSet(0L)
        val chatRecomposes = chatRecompositions.getAndSet(0L)
        return RenderProfilerInterval(
            blurCpuMsPerFrame = if (frames == 0L) 0.0 else cpuNs / 1_000_000.0 / frames,
            blurFilteredMegapixelsPerSecond = pixels / 1_000_000.0 / elapsedSeconds,
            blurSourceDrawsPerFrame = if (frames == 0L) 0.0 else sourceDraws.toDouble() / frames,
            blurLayerReplaysPerFrame = if (frames == 0L) 0.0 else layerReplays.toDouble() / frames,
            blurCaptureUpdatesPerSecond = captureUpdates / elapsedSeconds,
            blurEffectBuildsPerSecond = effectBuilds / elapsedSeconds,
            blurDownsampleLevels = if (frames == 0L) 0.0 else downsampleLevels.toDouble() / frames,
            blurUpsampleLevels = if (frames == 0L) 0.0 else upsampleLevels.toDouble() / frames,
            appRecompositionsPerSecond = appRecomposes / elapsedSeconds,
            chatRecompositionsPerSecond = chatRecomposes / elapsedSeconds,
            blurFrames = frames,
            screenName = currentScreen,
        )
    }

    private fun reset() {
        blurCpuNanos.set(0L)
        blurFrames.set(0L)
        blurFilteredPixels.set(0L)
        blurSourceDraws.set(0L)
        blurLayerReplays.set(0L)
        blurEffectBuilds.set(0L)
        blurCaptureUpdates.set(0L)
        blurDownsampleLevels.set(0L)
        blurUpsampleLevels.set(0L)
        appRecompositions.set(0L)
        chatRecompositions.set(0L)
    }
}

internal data class PerformanceCauseInput(
    val refreshRateHz: Float,
    val fps: Double,
    val frameTotalMs: Double,
    val frameDurationP95Ms: Double,
    val jankPercent: Double,
    val gpuMs: Double?,
    val inputMs: Double,
    val animationMs: Double,
    val layoutMs: Double,
    val drawMs: Double,
    val syncMs: Double,
    val commandMs: Double,
    val swapMs: Double,
    val blurCpuMs: Double,
    val blurFrames: Long,
    val blurSourceDrawsPerFrame: Double,
    val appRecompositionsPerSecond: Double,
    val chatRecompositionsPerSecond: Double,
    val allocationMbPerSecond: Double,
    val blockingGcPerSecond: Double,
)

internal enum class PerformanceSeverity(val displayName: String) {
    HEALTHY("Healthy"),
    NOTICE("Notice"),
    DEGRADED("Degraded"),
    SEVERE("Severe"),
}

internal data class PerformanceCauseProfile(
    val primaryCause: String,
    val secondaryCause: String? = null,
    val confidencePercent: Int = 0,
    val severity: PerformanceSeverity = PerformanceSeverity.HEALTHY,
    val evidence: String = "",
) {
    companion object {
        fun disabled() = PerformanceCauseProfile(
            primaryCause = "Profiler disabled",
            confidencePercent = 0,
            severity = PerformanceSeverity.HEALTHY,
            evidence = "Enable Cause profiler to collect attribution data",
        )
    }
}

private data class ScoredPerformanceCause(
    val label: String,
    val score: Double,
    val evidence: String,
)

internal fun analyzePerformanceCause(input: PerformanceCauseInput): PerformanceCauseProfile {
    val refresh = input.refreshRateHz.takeIf { it >= 30f } ?: 60f
    val budgetMs = 1_000.0 / refresh
    if (input.fps <= 0.0 && input.frameTotalMs <= 0.0) {
        return PerformanceCauseProfile(
            primaryCause = "Idle / no rendered frames",
            confidencePercent = 100,
            severity = PerformanceSeverity.HEALTHY,
            evidence = "No rendered FrameMetrics samples in this interval",
        )
    }

    val totalRatio = input.frameTotalMs / budgetMs
    val p95Ratio = input.frameDurationP95Ms / budgetMs
    val severity = when {
        p95Ratio >= 3.0 || input.jankPercent >= 30.0 -> PerformanceSeverity.SEVERE
        p95Ratio >= 2.0 || input.jankPercent >= 12.0 -> PerformanceSeverity.DEGRADED
        p95Ratio >= 1.20 || input.jankPercent >= 3.0 -> PerformanceSeverity.NOTICE
        else -> PerformanceSeverity.HEALTHY
    }
    val candidates = mutableListOf<ScoredPerformanceCause>()
    fun add(label: String, score: Double, evidence: String) {
        if (score.isFinite() && score > 0.0) candidates += ScoredPerformanceCause(label, score, evidence)
    }

    val recompositions = input.appRecompositionsPerSecond + input.chatRecompositionsPerSecond
    if (input.blockingGcPerSecond >= 0.25 || input.allocationMbPerSecond >= 96.0) {
        val score = 6.0 +
            max(input.blockingGcPerSecond / 0.5, input.allocationMbPerSecond / 160.0)
        add(
            "Allocation / blocking GC pressure",
            score,
            "bGC ${input.blockingGcPerSecond.f1()}/s, alloc ${input.allocationMbPerSecond.f1()} MB/s",
        )
    }
    if (recompositions > refresh * 1.5 && (totalRatio >= 0.8 || p95Ratio >= 1.2)) {
        add(
            "Compose state churn / recomposition",
            1.8 + recompositions / (refresh * 3.0) + max(totalRatio - 0.8, 0.0),
            "${recompositions.f1()} recompositions/s at ${refresh.toDouble().f0()} Hz",
        )
    }
    if (input.blurSourceDrawsPerFrame > 1.15 && (totalRatio >= 0.8 || p95Ratio >= 1.2)) {
        add(
            "Duplicate content recording for blur",
            2.4 + (input.blurSourceDrawsPerFrame - 1.15) * 1.5 + max(totalRatio - 0.8, 0.0),
            "${input.blurSourceDrawsPerFrame.f1()} blur source traversals/frame",
        )
    }

    val stages = buildList {
        input.gpuMs?.let { add("GPU rendering" to it) }
        add("Layout / measure" to input.layoutMs)
        add("UI draw / recording" to input.drawMs)
        add("Render command issue" to input.commandMs)
        add("Render sync" to input.syncMs)
        add("Buffer swap" to input.swapMs)
        add("Animation" to input.animationMs)
        add("Input handling" to input.inputMs)
        if (input.blurFrames > 0L) add("Blur CPU recording / replay" to input.blurCpuMs)
    }
    val dominant = stages.maxByOrNull { it.second }
    stages.forEach { (rawLabel, durationMs) ->
        val ratio = durationMs / budgetMs
        if (ratio < 0.35) return@forEach
        val label = when {
            rawLabel == "GPU rendering" && input.blurFrames > 0L -> "GPU rendering (blur active)"
            rawLabel == "UI draw / recording" &&
                input.blurFrames > 0L &&
                input.blurCpuMs >= durationMs * 0.35 -> "Blur source recording / extra draws"
            else -> rawLabel
        }
        add(
            label,
            ratio * 1.7,
            "${durationMs.f1()} ms of ${budgetMs.f1()} ms frame budget",
        )
    }

    val intervalSlow = p95Ratio >= 1.45 || input.jankPercent >= 3.0
    val dominantMs = dominant?.second ?: 0.0
    if (intervalSlow && dominantMs < budgetMs * 0.62) {
        add(
            "Frame pacing / scheduling stalls",
            2.0 + max(p95Ratio - 1.0, 0.0) + input.jankPercent / 20.0,
            "p95 ${input.frameDurationP95Ms.f1()} ms, jank ${input.jankPercent.f1()}%, largest measured stage ${dominantMs.f1()} ms",
        )
    }

    if (candidates.isEmpty()) {
        val withinBudget = totalRatio < 1.0 && p95Ratio < 1.2 && input.jankPercent < 3.0
        return PerformanceCauseProfile(
            primaryCause = if (withinBudget) "Within frame budget" else "Mixed / unattributed frame work",
            confidencePercent = if (withinBudget) 96 else 48,
            severity = severity,
            evidence = "total ${input.frameTotalMs.f1()} ms, p95 ${input.frameDurationP95Ms.f1()} ms, budget ${budgetMs.f1()} ms, jank ${input.jankPercent.f1()}%",
        )
    }

    val ranked = candidates.sortedByDescending(ScoredPerformanceCause::score)
    val primary = ranked.first()
    val secondary = ranked.drop(1).firstOrNull { it.label != primary.label }
    val margin = if (secondary == null) {
        1.0
    } else {
        ((primary.score - secondary.score) / primary.score.coerceAtLeast(0.001)).coerceIn(0.0, 1.0)
    }
    val strength = (primary.score / 4.0).coerceIn(0.0, 1.0)
    val dataCoverage = if (input.gpuMs != null) 1.0 else 0.92
    val confidence = ((45.0 + strength * 35.0 + margin * 20.0) * dataCoverage)
        .toInt()
        .coerceIn(40, 99)
    val secondaryLabel = secondary
        ?.takeIf { it.score >= primary.score * 0.45 }
        ?.label
    return PerformanceCauseProfile(
        primaryCause = primary.label,
        secondaryCause = secondaryLabel,
        confidencePercent = confidence,
        severity = severity,
        evidence = primary.evidence,
    )
}

/** Compatibility helper for callers/tests which need only the top-ranked label. */
internal fun detectLikelyBottleneck(input: PerformanceCauseInput): String =
    analyzePerformanceCause(input).primaryCause

internal fun normalizedPerformanceIntervalMs(value: Int): Int = value.coerceIn(250, 2_000)
internal fun normalizedPerformanceOverlayScale(value: Float): Float = value.coerceIn(0.60f, 2.00f)

internal fun performancePercentile(values: List<Double>, percentile: Double): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    val rank = ceil(percentile.coerceIn(0.0, 1.0) * sorted.size).toInt().coerceIn(1, sorted.size)
    return sorted[rank - 1]
}

internal fun boundedRenderedFrameRate(rawRenderedRate: Double, displayRefreshRateHz: Float): Double =
    rawRenderedRate.coerceAtLeast(0.0).coerceAtMost(displayRefreshRateHz.toDouble().coerceAtLeast(1.0))

internal fun estimatedMissedFrames(frameMs: Double, frameBudgetMs: Double): Int {
    if (frameMs <= 0.0 || frameBudgetMs <= 0.0) return 0
    return (ceil(frameMs / frameBudgetMs).toInt() - 1).coerceAtLeast(0)
}

internal class XylunePerformanceMonitor(private val activity: Activity) {
    private val lock = Any()
    private val recentFrameIntervals = ArrayDeque<Double>(MAX_RECENT_FRAMES)
    private val recentFrameDurations = ArrayDeque<Double>(MAX_RECENT_FRAMES)
    private val recentGpuDurations = ArrayDeque<Double>(MAX_RECENT_FRAMES)
    private val _snapshot = kotlinx.coroutines.flow.MutableStateFlow(PerformanceSnapshot())
    val snapshot: kotlinx.coroutines.flow.StateFlow<PerformanceSnapshot> = _snapshot

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var metricsListener: Window.OnFrameMetricsAvailableListener? = null
    private var sampler: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var choreographer: Choreographer? = null
    private var frameCallback: Choreographer.FrameCallback? = null
    private var updateIntervalMs = 500
    private var diagnosticProfilerEnabled = false
    private var intervalCallbacks = 0L
    private var intervalRenderedFrames = 0L
    private var intervalJankFrames = 0L
    private var intervalMissedFrames = 0L
    private var totalFrames = 0L
    private var totalDroppedMetricReports = 0L
    private var latestRefreshRate = 60f
    private var lastFrameTimeNanos = 0L
    private var lastSampleElapsedMs = 0L
    private var lastCpuElapsedMs = 0L
    private var lastMemorySampleElapsedMs = 0L
    private var cachedPssMb = 0.0
    private var cachedHeapMb = 0.0
    private var frameMetricCount = 0L
    private var frameMetricTotalNs = 0L
    private var inputNs = 0L
    private var animationNs = 0L
    private var layoutNs = 0L
    private var drawNs = 0L
    private var syncNs = 0L
    private var commandNs = 0L
    private var swapNs = 0L
    private var lastAllocatedBytes = 0L
    private var lastBlockingGcCount = 0L

    @Synchronized
    fun start(intervalMs: Int, diagnosticsEnabled: Boolean = false) {
        val normalized = normalizedPerformanceIntervalMs(intervalMs)
        if (thread != null && updateIntervalMs == normalized && diagnosticProfilerEnabled == diagnosticsEnabled) return
        stop()
        updateIntervalMs = normalized
        diagnosticProfilerEnabled = diagnosticsEnabled
        XyluneRenderProfiler.setEnabled(diagnosticsEnabled)

        val worker = HandlerThread("XylunePerformanceCounter").also { it.start() }
        val workerHandler = Handler(worker.looper)
        thread = worker
        handler = workerHandler
        lastSampleElapsedMs = SystemClock.elapsedRealtime()
        lastCpuElapsedMs = Process.getElapsedCpuTime()
        lastMemorySampleElapsedMs = 0L
        lastAllocatedBytes = runtimeStatLong(RUNTIME_ALLOCATED_BYTES)
        lastBlockingGcCount = runtimeStatLong(RUNTIME_BLOCKING_GC_COUNT)

        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, droppedReports ->
            val totalDurationNs = metricNanos(metrics, FrameMetrics.TOTAL_DURATION)
            val totalDurationMs = totalDurationNs / 1_000_000.0
            val gpuMs = if (Build.VERSION.SDK_INT >= 31) {
                metrics.getMetric(FrameMetrics.GPU_DURATION).takeIf { it > 0L }?.div(1_000_000.0)
            } else null
            val refreshRate = activity.window.decorView.display?.refreshRate?.takeIf { it >= 30f } ?: latestRefreshRate
            val budgetMs = 1_000.0 / refreshRate.coerceAtLeast(30f)
            synchronized(lock) {
                latestRefreshRate = refreshRate
                intervalRenderedFrames++
                totalFrames++
                if (totalDurationMs.isFinite() && totalDurationMs > 0.0) {
                    recentFrameDurations.addLast(totalDurationMs)
                    while (recentFrameDurations.size > MAX_RECENT_FRAMES) recentFrameDurations.removeFirst()
                    if (totalDurationMs > budgetMs * JANK_MULTIPLIER) intervalJankFrames++
                    intervalMissedFrames += estimatedMissedFrames(totalDurationMs, budgetMs).toLong()
                }
                if (gpuMs != null && gpuMs.isFinite()) {
                    recentGpuDurations.addLast(gpuMs)
                    while (recentGpuDurations.size > MAX_RECENT_FRAMES) recentGpuDurations.removeFirst()
                }
                if (diagnosticProfilerEnabled) {
                    frameMetricCount++
                    frameMetricTotalNs += totalDurationNs
                    inputNs += metricNanos(metrics, FrameMetrics.INPUT_HANDLING_DURATION)
                    animationNs += metricNanos(metrics, FrameMetrics.ANIMATION_DURATION)
                    layoutNs += metricNanos(metrics, FrameMetrics.LAYOUT_MEASURE_DURATION)
                    drawNs += metricNanos(metrics, FrameMetrics.DRAW_DURATION)
                    syncNs += metricNanos(metrics, FrameMetrics.SYNC_DURATION)
                    commandNs += metricNanos(metrics, FrameMetrics.COMMAND_ISSUE_DURATION)
                    swapNs += metricNanos(metrics, FrameMetrics.SWAP_BUFFERS_DURATION)
                }
                if (droppedReports > 0) totalDroppedMetricReports += droppedReports.toLong()
            }
        }
        metricsListener = listener
        activity.window.addOnFrameMetricsAvailableListener(listener, workerHandler)

        mainHandler.post {
            val uiChoreographer = Choreographer.getInstance()
            choreographer = uiChoreographer
            val callback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    recordFrame(frameTimeNanos)
                    if (frameCallback === this) uiChoreographer.postFrameCallback(this)
                }
            }
            frameCallback = callback
            uiChoreographer.postFrameCallback(callback)
        }

        val sampleTask = object : Runnable {
            override fun run() {
                publishSnapshot()
                handler?.postDelayed(this, updateIntervalMs.toLong())
            }
        }
        sampler = sampleTask
        workerHandler.postDelayed(sampleTask, updateIntervalMs.toLong())
    }

    private fun recordFrame(frameTimeNanos: Long) {
        val previous = lastFrameTimeNanos
        lastFrameTimeNanos = frameTimeNanos
        if (previous <= 0L) return
        val intervalMs = (frameTimeNanos - previous) / 1_000_000.0
        if (!intervalMs.isFinite() || intervalMs <= 0.0 || intervalMs > MAX_VALID_FRAME_INTERVAL_MS) return
        val refreshRate = activity.window.decorView.display?.refreshRate?.takeIf { it >= 30f } ?: 60f
        synchronized(lock) {
            latestRefreshRate = refreshRate
            intervalCallbacks++
            recentFrameIntervals.addLast(intervalMs)
            while (recentFrameIntervals.size > MAX_RECENT_FRAMES) recentFrameIntervals.removeFirst()
        }
    }

    @Synchronized
    fun stop() {
        metricsListener?.let { runCatching { activity.window.removeOnFrameMetricsAvailableListener(it) } }
        sampler?.let { handler?.removeCallbacks(it) }
        val callback = frameCallback
        frameCallback = null
        mainHandler.post {
            if (callback != null) choreographer?.removeFrameCallback(callback)
            choreographer = null
        }
        metricsListener = null
        sampler = null
        handler = null
        thread?.quitSafely()
        thread = null
        lastFrameTimeNanos = 0L
        XyluneRenderProfiler.setEnabled(false)
        synchronized(lock) {
            recentFrameIntervals.clear()
            recentFrameDurations.clear()
            recentGpuDurations.clear()
            intervalCallbacks = 0L
            intervalRenderedFrames = 0L
            intervalJankFrames = 0L
            intervalMissedFrames = 0L
            totalFrames = 0L
            totalDroppedMetricReports = 0L
            resetFrameMetricSums()
        }
        _snapshot.value = PerformanceSnapshot()
    }

    private fun publishSnapshot() {
        val now = SystemClock.elapsedRealtime()
        val cpuNow = Process.getElapsedCpuTime()
        val elapsedMs = max(1L, now - lastSampleElapsedMs)
        val cpuElapsedMs = max(0L, cpuNow - lastCpuElapsedMs)
        lastSampleElapsedMs = now
        lastCpuElapsedMs = cpuNow

        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val cpuPercent = (cpuElapsedMs.toDouble() / elapsedMs / cores * 100.0).coerceIn(0.0, 100.0)
        if (now - lastMemorySampleElapsedMs >= MEMORY_SAMPLE_INTERVAL_MS || lastMemorySampleElapsedMs == 0L) {
            val runtime = Runtime.getRuntime()
            cachedHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB
            cachedPssMb = Debug.getPss() / KILOBYTES_PER_MB
            lastMemorySampleElapsedMs = now
        }

        val allocatedNow = runtimeStatLong(RUNTIME_ALLOCATED_BYTES)
        val blockingGcNow = runtimeStatLong(RUNTIME_BLOCKING_GC_COUNT)
        val elapsedSeconds = elapsedMs / 1_000.0
        val allocationMbPerSecond = if (allocatedNow >= lastAllocatedBytes && lastAllocatedBytes > 0L) {
            (allocatedNow - lastAllocatedBytes) / BYTES_PER_MB / elapsedSeconds
        } else 0.0
        val blockingGcPerSecond = if (blockingGcNow >= lastBlockingGcCount && lastBlockingGcCount > 0L) {
            (blockingGcNow - lastBlockingGcCount) / elapsedSeconds
        } else 0.0
        lastAllocatedBytes = allocatedNow
        lastBlockingGcCount = blockingGcNow
        val renderProfiler = if (diagnosticProfilerEnabled) XyluneRenderProfiler.drain(elapsedMs) else EMPTY_RENDER_INTERVAL

        val snapshotData = synchronized(lock) {
            val frameDurations = recentFrameDurations.toList()
            val gpuDurations = recentGpuDurations.toList()
            val callbackCount = intervalCallbacks
            val renderedFrameCount = intervalRenderedFrames
            val jankCount = intervalJankFrames
            val missedCount = intervalMissedFrames
            val p95FrameMs = performancePercentile(frameDurations, 0.95)
            val p99FrameMs = performancePercentile(frameDurations, 0.99)
            val jankPercent = if (renderedFrameCount == 0L) 0.0 else jankCount * 100.0 / renderedFrameCount
            val metricCount = frameMetricCount
            val totalMs = averageMetricMs(frameMetricTotalNs, metricCount)
            val inputAverageMs = averageMetricMs(inputNs, metricCount)
            val animationAverageMs = averageMetricMs(animationNs, metricCount)
            val layoutAverageMs = averageMetricMs(layoutNs, metricCount)
            val drawAverageMs = averageMetricMs(drawNs, metricCount)
            val syncAverageMs = averageMetricMs(syncNs, metricCount)
            val commandAverageMs = averageMetricMs(commandNs, metricCount)
            val swapAverageMs = averageMetricMs(swapNs, metricCount)
            intervalCallbacks = 0L
            intervalRenderedFrames = 0L
            intervalJankFrames = 0L
            intervalMissedFrames = 0L
            resetFrameMetricSums()
            val gpuAverage = gpuDurations.takeIf { it.isNotEmpty() }?.average()
            val callbackRate = callbackCount * 1_000.0 / elapsedMs
            val rawRenderedRate = renderedFrameCount * 1_000.0 / elapsedMs
            // FrameMetrics reports rendered window frames, not guaranteed physical presentation.
            // Clamp the compact FPS value to the active display rate so it cannot imply 130
            // physically displayed frames on a 120 Hz panel.
            val appRenderedRate = boundedRenderedFrameRate(rawRenderedRate, latestRefreshRate)
            val fps = appRenderedRate
            val causeProfile = if (diagnosticProfilerEnabled) analyzePerformanceCause(
                PerformanceCauseInput(
                    refreshRateHz = latestRefreshRate,
                    fps = fps,
                    frameTotalMs = totalMs,
                    frameDurationP95Ms = p95FrameMs,
                    jankPercent = jankPercent,
                    gpuMs = gpuAverage,
                    inputMs = inputAverageMs,
                    animationMs = animationAverageMs,
                    layoutMs = layoutAverageMs,
                    drawMs = drawAverageMs,
                    syncMs = syncAverageMs,
                    commandMs = commandAverageMs,
                    swapMs = swapAverageMs,
                    blurCpuMs = renderProfiler.blurCpuMsPerFrame,
                    blurFrames = renderProfiler.blurFrames,
                    blurSourceDrawsPerFrame = renderProfiler.blurSourceDrawsPerFrame,
                    appRecompositionsPerSecond = renderProfiler.appRecompositionsPerSecond,
                    chatRecompositionsPerSecond = renderProfiler.chatRecompositionsPerSecond,
                    allocationMbPerSecond = allocationMbPerSecond,
                    blockingGcPerSecond = blockingGcPerSecond,
                ),
            ) else PerformanceCauseProfile.disabled()
            PerformanceSnapshot(
                fps = fps,
                displayRefreshRateHz = latestRefreshRate,
                choreographerCallbackRate = callbackRate,
                appRenderedFrameRate = appRenderedRate,
                presentedFrameRate = null,
                averageFrameMs = frameDurations.averageOrZero(),
                p95FrameMs = p95FrameMs,
                p99FrameMs = p99FrameMs,
                gpuAverageMs = gpuAverage,
                jankPercent = jankPercent,
                missedFramesPerSecond = missedCount * 1_000.0 / elapsedMs,
                droppedMetricReports = totalDroppedMetricReports,
                cpuPercent = cpuPercent,
                pssMb = cachedPssMb,
                javaHeapMb = cachedHeapMb,
                refreshRateHz = latestRefreshRate,
                totalFrames = totalFrames,
                diagnosticProfilerActive = diagnosticProfilerEnabled,
                frameMetricsTotalMs = totalMs,
                inputMs = inputAverageMs,
                animationMs = animationAverageMs,
                layoutMeasureMs = layoutAverageMs,
                drawMs = drawAverageMs,
                syncMs = syncAverageMs,
                commandIssueMs = commandAverageMs,
                swapBuffersMs = swapAverageMs,
                blurCpuMsPerFrame = renderProfiler.blurCpuMsPerFrame,
                blurFilteredMegapixelsPerSecond = renderProfiler.blurFilteredMegapixelsPerSecond,
                blurSourceDrawsPerFrame = renderProfiler.blurSourceDrawsPerFrame,
                blurLayerReplaysPerFrame = renderProfiler.blurLayerReplaysPerFrame,
                blurCaptureUpdatesPerSecond = renderProfiler.blurCaptureUpdatesPerSecond,
                blurEffectBuildsPerSecond = renderProfiler.blurEffectBuildsPerSecond,
                blurDownsampleLevels = renderProfiler.blurDownsampleLevels,
                blurUpsampleLevels = renderProfiler.blurUpsampleLevels,
                appRecompositionsPerSecond = renderProfiler.appRecompositionsPerSecond,
                chatRecompositionsPerSecond = renderProfiler.chatRecompositionsPerSecond,
                allocationMbPerSecond = allocationMbPerSecond,
                blockingGcPerSecond = blockingGcPerSecond,
                screenName = renderProfiler.screenName,
                causeProfile = causeProfile,
            )
        }
        _snapshot.value = snapshotData
    }

    private fun resetFrameMetricSums() {
        frameMetricCount = 0L
        frameMetricTotalNs = 0L
        inputNs = 0L
        animationNs = 0L
        layoutNs = 0L
        drawNs = 0L
        syncNs = 0L
        commandNs = 0L
        swapNs = 0L
    }

    private fun runtimeStatLong(key: String): Long =
        runCatching { Debug.getRuntimeStat(key)?.trim()?.toLongOrNull() ?: 0L }.getOrDefault(0L)

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

    private companion object {
        const val MAX_RECENT_FRAMES = 240
        const val JANK_MULTIPLIER = 1.5
        const val MAX_VALID_FRAME_INTERVAL_MS = 250.0
        const val MEMORY_SAMPLE_INTERVAL_MS = 1_000L
        const val BYTES_PER_MB = 1024.0 * 1024.0
        const val KILOBYTES_PER_MB = 1024.0
        const val RUNTIME_ALLOCATED_BYTES = "art.gc.bytes-allocated"
        const val RUNTIME_BLOCKING_GC_COUNT = "art.gc.blocking-gc-count"
        val EMPTY_RENDER_INTERVAL = RenderProfilerInterval(
            blurCpuMsPerFrame = 0.0,
            blurFilteredMegapixelsPerSecond = 0.0,
            blurSourceDrawsPerFrame = 0.0,
            blurLayerReplaysPerFrame = 0.0,
            blurCaptureUpdatesPerSecond = 0.0,
            blurEffectBuildsPerSecond = 0.0,
            blurDownsampleLevels = 0.0,
            blurUpsampleLevels = 0.0,
            appRecompositionsPerSecond = 0.0,
            chatRecompositionsPerSecond = 0.0,
            blurFrames = 0L,
            screenName = "UNKNOWN",
        )
    }
}

private fun metricNanos(metrics: FrameMetrics, id: Int): Long = metrics.getMetric(id).coerceAtLeast(0L)
private fun averageMetricMs(totalNanos: Long, count: Long): Double =
    if (count <= 0L) 0.0 else totalNanos / 1_000_000.0 / count

@Composable
internal fun XylunePerformanceOverlay(
    snapshot: PerformanceSnapshot,
    detailed: Boolean,
    modifier: Modifier = Modifier,
    backgroundOpacity: Float = 0.86f,
    textOpacity: Float = 1f,
    scale: Float = 1f,
) {
    val panelAlpha = backgroundOpacity.coerceIn(0f, 1f)
    val uiScale = normalizedPerformanceOverlayScale(scale)
    val panelShape = RoundedCornerShape((10f * uiScale).dp)
    val secondaryText = Color.White.copy(alpha = 0.86f)
    val diagnosticText = Color.White.copy(alpha = 0.82f)
    Surface(
        modifier = modifier
            .performanceOverlayTouchThrough()
            .border((1f * uiScale).dp, Color.White.copy(alpha = 0.14f * panelAlpha), panelShape),
        color = Color.Black.copy(alpha = panelAlpha),
        contentColor = Color.White,
        shape = panelShape,
        shadowElevation = (6f * uiScale * panelAlpha).dp,
    ) {
        Column(
            Modifier
                .graphicsLayer { alpha = textOpacity.coerceIn(0f, 1f) }
                .padding(horizontal = (9f * uiScale).dp, vertical = (7f * uiScale).dp),
        ) {
            Text(
                uiText("Render ${snapshot.appRenderedFrameRate.f0()} fps  ${snapshot.averageFrameMs.f1()} ms  J ${snapshot.jankPercent.f1()}%"),
                fontFamily = FontFamily.Monospace,
                fontSize = (11f * uiScale).sp,
                maxLines = 1,
            )
            if (detailed) {
                Text(
                    uiText("Display ${snapshot.displayRefreshRateHz.toDouble().f0()} Hz  Callback ${snapshot.choreographerCallbackRate.f0()}/s  Present ${snapshot.presentedFrameRate?.f0() ?: "n/a"}"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = (10f * uiScale).sp,
                    color = secondaryText,
                    maxLines = 1,
                )
                Text(
                    uiText("FM avg ${snapshot.frameMetricsTotalMs.f1()} ms  p95 ${snapshot.p95FrameMs.f1()}  p99 ${snapshot.p99FrameMs.f1()}"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = (10f * uiScale).sp,
                    color = secondaryText,
                    maxLines = 1,
                )
                Text(
                    uiText("CPU ${snapshot.cpuPercent.f1()}%  PSS ${snapshot.pssMb.f0()} MB  Heap ${snapshot.javaHeapMb.f0()} MB"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = (10f * uiScale).sp,
                    color = secondaryText,
                    maxLines = 1,
                )
                Text(
                    uiText("GPU ${snapshot.gpuAverageMs?.f1() ?: "n/a"} ms  Miss/s ${snapshot.missedFramesPerSecond.f1()}  Reports ${snapshot.droppedMetricReports}"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = (10f * uiScale).sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                    maxLines = 1,
                )
                if (snapshot.diagnosticProfilerActive) {
                    val profile = snapshot.causeProfile
                    Text(
                        uiText("Cause ${profile.confidencePercent}% · ${profile.severity.displayName}: ${profile.primaryCause}"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = (10f * uiScale).sp,
                        color = Color(0xFFFFD180),
                        maxLines = 1,
                    )
                    Text(
                        uiText("Evidence: ${profile.evidence}"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = (9f * uiScale).sp,
                        color = Color(0xFFFFE0B2),
                        maxLines = 1,
                    )
                    profile.secondaryCause?.let { secondary ->
                        Text(
                            uiText("Secondary: $secondary"),
                            fontFamily = FontFamily.Monospace,
                            fontSize = (9f * uiScale).sp,
                            color = Color.White.copy(alpha = 0.76f),
                            maxLines = 1,
                        )
                    }
                    Text(
                        uiText("FM ${snapshot.frameMetricsTotalMs.f1()}  L ${snapshot.layoutMeasureMs.f1()}  D ${snapshot.drawMs.f1()}  Cmd ${snapshot.commandIssueMs.f1()}  Sw ${snapshot.swapBuffersMs.f1()}"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = (9f * uiScale).sp,
                        color = diagnosticText,
                        maxLines = 1,
                    )
                    Text(
                        uiText("BlurCPU ${snapshot.blurCpuMsPerFrame.f2()}  ${snapshot.blurFilteredMegapixelsPerSecond.f0()} MP/s  srcTrav×${snapshot.blurSourceDrawsPerFrame.f1()} replay×${snapshot.blurLayerReplaysPerFrame.f1()}"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = (9f * uiScale).sp,
                        color = diagnosticText,
                        maxLines = 1,
                    )
                    Text(
                        uiText("cap/s ${snapshot.blurCaptureUpdatesPerSecond.f1()} fx/s ${snapshot.blurEffectBuildsPerSecond.f1()}  levels D${snapshot.blurDownsampleLevels.f1()}/U${snapshot.blurUpsampleLevels.f1()}"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = (9f * uiScale).sp,
                        color = diagnosticText,
                        maxLines = 1,
                    )
                    Text(
                        uiText("Recomp/s app ${snapshot.appRecompositionsPerSecond.f1()} chat ${snapshot.chatRecompositionsPerSecond.f1()}"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = (9f * uiScale).sp,
                        color = diagnosticText,
                        maxLines = 1,
                    )
                    Text(
                        uiText("Alloc ${snapshot.allocationMbPerSecond.f1()} MB/s  bGC ${snapshot.blockingGcPerSecond.f1()}  Screen ${snapshot.screenName}"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = (9f * uiScale).sp,
                        color = Color.White.copy(alpha = 0.68f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Makes the visual overlay an explicit hit-test participant which shares every event
 * with the sibling underneath and never consumes any change. This is stronger than
 * merely omitting clickable/pointerInput: overlapping sibling hit testing is now
 * guaranteed for scroll, tap, drawer-edge, and stylus input which starts on the panel.
 */
private class PerformanceOverlayTouchThroughNode : Modifier.Node(), PointerInputModifierNode {
    override fun sharePointerInputWithSiblings(): Boolean = true
    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) = Unit
    override fun onCancelPointerInput() = Unit
}

private class PerformanceOverlayTouchThroughElement :
    ModifierNodeElement<PerformanceOverlayTouchThroughNode>() {
    override fun create(): PerformanceOverlayTouchThroughNode = PerformanceOverlayTouchThroughNode()
    override fun update(node: PerformanceOverlayTouchThroughNode) = Unit
    override fun InspectorInfo.inspectableProperties() {
        name = "performanceOverlayTouchThrough"
    }
    override fun equals(other: Any?): Boolean = other is PerformanceOverlayTouchThroughElement
    override fun hashCode(): Int = javaClass.hashCode()
}

private fun Modifier.performanceOverlayTouchThrough(): Modifier =
    this then PerformanceOverlayTouchThroughElement()

private fun Double.f0(): String = String.format(Locale.US, "%.0f", this)
private fun Double.f1(): String = String.format(Locale.US, "%.1f", this)
private fun Double.f2(): String = String.format(Locale.US, "%.2f", this)
