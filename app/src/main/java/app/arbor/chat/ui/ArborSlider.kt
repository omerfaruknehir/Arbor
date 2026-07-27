package app.arbor.chat.ui

import android.os.SystemClock
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlin.math.abs

internal data class MagneticSliderResult(
    val value: Float,
    val anchor: Float?,
    val captured: Boolean,
)

/**
 * Applies a continuous, monotonic resistance curve around an anchor.
 *
 * There is deliberately no hard settle core while the pointer is moving. The
 * old implementation changed the value discontinuously near every anchor,
 * which made dense and discrete controls—especially Thinking—jump between
 * values. Exact snapping happens only after release.
 */
internal fun applyMagneticSliderForce(
    rawValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    anchors: List<Float>,
    attractionRadiusFraction: Float = 0.03f,
    pullStrength: Float = 0.35f,
): MagneticSliderResult {
    val start = valueRange.start
    val end = valueRange.endInclusive
    val span = (end - start).coerceAtLeast(0f)
    val raw = rawValue.coerceIn(start, end)
    if (span <= 0f || anchors.isEmpty()) return MagneticSliderResult(raw, null, false)

    val anchor = anchors.asSequence()
        .map { it.coerceIn(start, end) }
        .minByOrNull { abs(it - raw) }
        ?: return MagneticSliderResult(raw, null, false)
    val distance = abs(anchor - raw)
    val attractionRadius = span * attractionRadiusFraction.coerceIn(0f, 0.25f)
    if (attractionRadius <= 0f || distance >= attractionRadius) {
        return MagneticSliderResult(raw, null, false)
    }

    // This compression is continuous at both the anchor and radius boundary.
    // It slows the thumb near an anchor without ever teleporting it.
    val normalizedDistance = (distance / attractionRadius).coerceIn(0f, 1f)
    val resistance = pullStrength.coerceIn(0f, 0.75f) *
        (1f - normalizedDistance) * (1f - normalizedDistance)
    val attracted = anchor + (raw - anchor) * (1f - resistance)
    return MagneticSliderResult(attracted.coerceIn(start, end), anchor, true)
}

internal fun sliderStepAnchors(
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
): List<Float> {
    if (steps < 0) return emptyList()
    val divisions = steps + 1
    val span = valueRange.endInclusive - valueRange.start
    return (0..divisions).map { index -> valueRange.start + span * index / divisions }
}

internal fun magneticReleaseRadiusMultiplier(normalizedVelocityPerSecond: Float): Float = when {
    abs(normalizedVelocityPerSecond) >= 2.4f -> 0.40f
    abs(normalizedVelocityPerSecond) >= 1.2f -> 0.70f
    else -> 1f
}

internal fun releaseSnapAnchor(
    rawValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    anchors: List<Float>,
    radiusFraction: Float,
    alwaysNearest: Boolean,
): Float? {
    if (anchors.isEmpty()) return null
    val raw = rawValue.coerceIn(valueRange.start, valueRange.endInclusive)
    val anchor = anchors.minByOrNull { abs(it - raw) } ?: return null
    if (alwaysNearest) return anchor
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0f)
    return anchor.takeIf { abs(it - raw) <= span * radiusFraction.coerceIn(0f, 0.25f) }
}

/** Material slider with subtle magnetic resistance, release-only snapping and tactile ticks. */
@Composable
fun ArborSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    snapPoints: List<Float> = emptyList(),
    attractionRadiusFraction: Float = 0.03f,
    pullStrength: Float = 0.35f,
    releaseSnapRadiusFraction: Float = 0.018f,
    liveMagnetism: Boolean = false,
    snapToNearestOnRelease: Boolean = false,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val haptics = rememberArborHaptics()
    val anchors = remember(valueRange.start, valueRange.endInclusive, steps, snapPoints) {
        (if (steps > 0) sliderStepAnchors(valueRange, steps) else snapPoints)
            .map { it.coerceIn(valueRange.start, valueRange.endInclusive) }
            .distinct()
            .sorted()
    }
    var dragging by remember { mutableStateOf(false) }
    var activeAnchor by remember { mutableStateOf<Float?>(null) }
    var lastTickIndex by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var lastRawValue by remember { mutableStateOf(value) }
    var lastDeliveredValue by remember { mutableStateOf(value) }
    var lastSampleAtMs by remember { mutableStateOf(0L) }
    var normalizedVelocityPerSecond by remember { mutableStateOf(0f) }

    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = { raw ->
            if (!dragging) {
                dragging = true
                haptics.gestureStart()
            }
            val now = SystemClock.uptimeMillis()
            val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
            if (lastSampleAtMs > 0L) {
                val elapsedSeconds = ((now - lastSampleAtMs).coerceAtLeast(1L)) / 1_000f
                val instantaneous = ((raw - lastRawValue) / span) / elapsedSeconds
                normalizedVelocityPerSecond = normalizedVelocityPerSecond * 0.65f + instantaneous * 0.35f
            }
            lastSampleAtMs = now
            lastRawValue = raw

            val result = if (liveMagnetism && steps == 0) {
                applyMagneticSliderForce(
                    rawValue = raw,
                    valueRange = valueRange,
                    anchors = anchors,
                    attractionRadiusFraction = attractionRadiusFraction,
                    pullStrength = pullStrength,
                )
            } else {
                MagneticSliderResult(raw, null, false)
            }

            val proximityAnchor = if (anchors.isNotEmpty()) {
                val nearest = anchors.minByOrNull { abs(it - raw) }
                val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0f)
                nearest?.takeIf {
                    span > 0f && abs(it - raw) < span * attractionRadiusFraction.coerceIn(0f, .25f)
                }
            } else null
            if (proximityAnchor != activeAnchor) {
                activeAnchor = proximityAnchor
                if (proximityAnchor != null) haptics.selection()
            }

            if (anchors.size > 1) {
                val tickIndex = anchors.indices.minByOrNull { index -> abs(anchors[index] - raw) } ?: 0
                if (lastTickIndex != Int.MIN_VALUE && tickIndex != lastTickIndex) haptics.frequentTick()
                lastTickIndex = tickIndex
            }

            lastDeliveredValue = result.value
            onValueChange(result.value)
        },
        modifier = modifier.horizontalGesturePriority(enabled),
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = {
            if (dragging) {
                val releaseMultiplier = magneticReleaseRadiusMultiplier(normalizedVelocityPerSecond)
                val target = if (steps > 0 && !snapToNearestOnRelease) {
                    null // Material already emitted an exact discrete step.
                } else {
                    releaseSnapAnchor(
                        rawValue = lastRawValue,
                        valueRange = valueRange,
                        anchors = anchors,
                        radiusFraction = releaseSnapRadiusFraction * releaseMultiplier,
                        alwaysNearest = snapToNearestOnRelease,
                    )
                }
                when {
                    target != null && abs(target - lastDeliveredValue) > 0.00001f -> {
                        lastDeliveredValue = target
                        onValueChange(target)
                        haptics.snap()
                    }
                    target == null && liveMagnetism && steps == 0 &&
                        abs(lastRawValue - lastDeliveredValue) > 0.00001f -> {
                        // Do not persist a magnetically warped value when the
                        // release was outside the intentionally small snap core.
                        lastDeliveredValue = lastRawValue
                        onValueChange(lastRawValue)
                        haptics.gestureEnd()
                    }
                    else -> haptics.gestureEnd()
                }
            }
            dragging = false
            activeAnchor = null
            lastTickIndex = Int.MIN_VALUE
            lastSampleAtMs = 0L
            normalizedVelocityPerSecond = 0f
            onValueChangeFinished?.invoke()
        },
        colors = colors,
        interactionSource = interactionSource,
    )
}
