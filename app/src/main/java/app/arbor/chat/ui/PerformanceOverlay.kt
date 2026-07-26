package app.arbor.chat.ui

import android.app.Activity
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

internal data class PerformanceSnapshot(
    val fps: Double = 0.0,
    val averageFrameMs: Double = 0.0,
    val p95FrameMs: Double = 0.0,
    val p99FrameMs: Double = 0.0,
    val gpuAverageMs: Double? = null,
    val jankPercent: Double = 0.0,
    val missedFrames: Long = 0,
    val droppedMetricReports: Long = 0,
    val cpuPercent: Double = 0.0,
    val pssMb: Double = 0.0,
    val javaHeapMb: Double = 0.0,
    val refreshRateHz: Float = 60f,
    val totalFrames: Long = 0,
)

internal fun normalizedPerformanceIntervalMs(value: Int): Int = value.coerceIn(250, 2_000)

internal fun performancePercentile(values: List<Double>, percentile: Double): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    val rank = ceil(percentile.coerceIn(0.0, 1.0) * sorted.size).toInt().coerceIn(1, sorted.size)
    return sorted[rank - 1]
}

internal fun estimatedMissedFrames(frameMs: Double, frameBudgetMs: Double): Int {
    if (frameMs <= 0.0 || frameBudgetMs <= 0.0) return 0
    return (ceil(frameMs / frameBudgetMs).toInt() - 1).coerceAtLeast(0)
}

internal class ArborPerformanceMonitor(private val activity: Activity) {
    private data class FrameSample(val totalMs: Double, val gpuMs: Double?)

    private val lock = Any()
    private val recentFrames = ArrayDeque<FrameSample>(MAX_RECENT_FRAMES)
    private val _snapshot = kotlinx.coroutines.flow.MutableStateFlow(PerformanceSnapshot())
    val snapshot: kotlinx.coroutines.flow.StateFlow<PerformanceSnapshot> = _snapshot

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var listener: Window.OnFrameMetricsAvailableListener? = null
    private var sampler: Runnable? = null
    private var updateIntervalMs = 500
    private var intervalFrames = 0L
    private var intervalJankFrames = 0L
    private var totalFrames = 0L
    private var totalMissedFrames = 0L
    private var totalDroppedMetricReports = 0L
    private var latestRefreshRate = 60f
    private var lastSampleElapsedMs = 0L
    private var lastCpuElapsedMs = 0L
    private var lastMemorySampleElapsedMs = 0L
    private var cachedPssMb = 0.0
    private var cachedHeapMb = 0.0

    @Synchronized
    fun start(intervalMs: Int) {
        val normalized = normalizedPerformanceIntervalMs(intervalMs)
        if (thread != null && updateIntervalMs == normalized) return
        stop()
        updateIntervalMs = normalized

        val worker = HandlerThread("ArborPerformanceCounter").also { it.start() }
        val workerHandler = Handler(worker.looper)
        thread = worker
        handler = workerHandler
        lastSampleElapsedMs = SystemClock.elapsedRealtime()
        lastCpuElapsedMs = Process.getElapsedCpuTime()
        lastMemorySampleElapsedMs = 0L

        val metricsListener = Window.OnFrameMetricsAvailableListener { _, metrics, droppedReports ->
            val totalNs = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            if (totalNs <= 0L) return@OnFrameMetricsAvailableListener
            val totalMs = totalNs / 1_000_000.0
            val gpuMs = if (Build.VERSION.SDK_INT >= 31) {
                metrics.getMetric(FrameMetrics.GPU_DURATION).takeIf { it > 0L }?.div(1_000_000.0)
            } else null
            val refreshRate = activity.window.decorView.display?.refreshRate?.takeIf { it >= 30f } ?: 60f
            val budgetMs = 1_000.0 / refreshRate
            synchronized(lock) {
                latestRefreshRate = refreshRate
                intervalFrames++
                totalFrames++
                if (totalMs > budgetMs * JANK_MULTIPLIER) intervalJankFrames++
                totalMissedFrames += estimatedMissedFrames(totalMs, budgetMs).toLong()
                if (droppedReports > 0) totalDroppedMetricReports += droppedReports.toLong()
                recentFrames.addLast(FrameSample(totalMs, gpuMs))
                while (recentFrames.size > MAX_RECENT_FRAMES) recentFrames.removeFirst()
            }
        }
        listener = metricsListener
        activity.window.addOnFrameMetricsAvailableListener(metricsListener, workerHandler)

        val sampleTask = object : Runnable {
            override fun run() {
                publishSnapshot()
                handler?.postDelayed(this, updateIntervalMs.toLong())
            }
        }
        sampler = sampleTask
        workerHandler.postDelayed(sampleTask, updateIntervalMs.toLong())
    }

    @Synchronized
    fun stop() {
        listener?.let { runCatching { activity.window.removeOnFrameMetricsAvailableListener(it) } }
        sampler?.let { handler?.removeCallbacks(it) }
        listener = null
        sampler = null
        handler = null
        thread?.quitSafely()
        thread = null
        synchronized(lock) {
            recentFrames.clear()
            intervalFrames = 0L
            intervalJankFrames = 0L
            totalFrames = 0L
            totalMissedFrames = 0L
            totalDroppedMetricReports = 0L
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
        val cpuPercent = (cpuElapsedMs.toDouble() / elapsedMs.toDouble() / cores.toDouble() * 100.0).coerceIn(0.0, 100.0)
        if (now - lastMemorySampleElapsedMs >= MEMORY_SAMPLE_INTERVAL_MS || lastMemorySampleElapsedMs == 0L) {
            val runtime = Runtime.getRuntime()
            cachedHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB
            cachedPssMb = Debug.getPss() / KILOBYTES_PER_MB
            lastMemorySampleElapsedMs = now
        }

        val snapshotData = synchronized(lock) {
            val samples = recentFrames.toList()
            val frameCount = intervalFrames
            val jankCount = intervalJankFrames
            intervalFrames = 0L
            intervalJankFrames = 0L
            val durations = samples.map { it.totalMs }
            val gpuDurations = samples.mapNotNull { it.gpuMs }
            PerformanceSnapshot(
                fps = frameCount * 1_000.0 / elapsedMs,
                averageFrameMs = durations.averageOrZero(),
                p95FrameMs = performancePercentile(durations, 0.95),
                p99FrameMs = performancePercentile(durations, 0.99),
                gpuAverageMs = gpuDurations.takeIf { it.isNotEmpty() }?.average(),
                jankPercent = if (frameCount == 0L) 0.0 else jankCount * 100.0 / frameCount,
                missedFrames = totalMissedFrames,
                droppedMetricReports = totalDroppedMetricReports,
                cpuPercent = cpuPercent,
                pssMb = cachedPssMb,
                javaHeapMb = cachedHeapMb,
                refreshRateHz = latestRefreshRate,
                totalFrames = totalFrames,
            )
        }
        _snapshot.value = snapshotData
    }

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

    private companion object {
        const val MAX_RECENT_FRAMES = 240
        const val JANK_MULTIPLIER = 1.5
        const val MEMORY_SAMPLE_INTERVAL_MS = 1_000L
        const val BYTES_PER_MB = 1024.0 * 1024.0
        const val KILOBYTES_PER_MB = 1024.0
    }
}

@Composable
internal fun ArborPerformanceOverlay(
    snapshot: PerformanceSnapshot,
    detailed: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
        color = Color.Black.copy(alpha = 0.82f),
        contentColor = Color.White,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
            Text(
                "${snapshot.fps.f0()} FPS  ${snapshot.averageFrameMs.f1()} ms  J ${snapshot.jankPercent.f1()}%",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 1,
            )
            if (detailed) {
                Text(
                    "p95 ${snapshot.p95FrameMs.f1()}  p99 ${snapshot.p99FrameMs.f1()}  ${snapshot.refreshRateHz.toDouble().f0()} Hz",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 1,
                )
                Text(
                    "CPU ${snapshot.cpuPercent.f1()}%  PSS ${snapshot.pssMb.f0()} MB  Heap ${snapshot.javaHeapMb.f0()} MB",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 1,
                )
                Text(
                    "GPU ${snapshot.gpuAverageMs?.f1() ?: "n/a"} ms  Miss ${snapshot.missedFrames}  Reports ${snapshot.droppedMetricReports}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                    maxLines = 1,
                )
            }
        }
    }
}

private fun Double.f0(): String = String.format(Locale.US, "%.0f", this)
private fun Double.f1(): String = String.format(Locale.US, "%.1f", this)
