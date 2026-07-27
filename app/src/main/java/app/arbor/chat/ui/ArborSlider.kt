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
import kotlin.math.roundToInt

internal data class MagneticSliderResult(
    val value: Float,
    val anchor: Float?,
    val captured: Boolean,
)

/**
 * Pulls the thumb toward nearby anchors with a smooth force curve.
 * Only the tiny settle core hard-snaps, so leaving an anchor does not jump.
 */
internal fun applyMagneticSliderForce(
    rawValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    anchors: List<Float>,
    attractionRadiusFraction: Float = 0.06f,
    pullStrength: Float = 0.82f,
    settleRadiusFraction: Float = 0.012f,
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
    val attractionRadius = span * attractionRadiusFraction.coerceIn(0f, 0.5f)
    if (attractionRadius <= 0f || distance >= attractionRadius) {
        return MagneticSliderResult(raw, null, false)
    }

    val settleRadius = span * settleRadiusFraction.coerceIn(0f, attractionRadiusFraction)
    if (distance <= settleRadius) return MagneticSliderResult(anchor, anchor, true)

    val proximity = 1f - distance / attractionRadius
    val smoothForce = proximity * proximity * (3f - 2f * proximity)
    val pull = (smoothForce * pullStrength.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    val attracted = raw + (anchor - raw) * pull
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
    abs(normalizedVelocityPerSecond) >= 2.4f -> 0.45f
    abs(normalizedVelocityPerSecond) >= 1.2f -> 0.75f
    else -> 1.35f
}

/** Material slider with magnetic anchors, velocity-aware settling and tactile ticks. */
@Composable
fun ArborSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    snapPoints: List<Float> = emptyList(),
    attractionRadiusFraction: Float = 0.06f,
    pullStrength: Float = 0.82f,
    settleRadiusFraction: Float = 0.012f,
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
    var lastStepIndex by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var lastRawValue by remember { mutableStateOf(value) }
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
                normalizedVelocityPerSecond = normalizedVelocityPerSecond * 0.58f + instantaneous * 0.42f
            }
            lastSampleAtMs = now
            lastRawValue = raw
            val result = applyMagneticSliderForce(
                rawValue = raw,
                valueRange = valueRange,
                anchors = anchors,
                attractionRadiusFraction = attractionRadiusFraction,
                pullStrength = pullStrength,
                settleRadiusFraction = settleRadiusFraction,
            )
            if (result.anchor != activeAnchor) {
                activeAnchor = result.anchor
                if (result.anchor != null) haptics.selection()
            }
            if (steps > 0) {
                val stepSpan = valueRange.endInclusive - valueRange.start
                val index = if (stepSpan <= 0f) 0 else
                    (((result.value - valueRange.start) / stepSpan) * (steps + 1)).roundToInt()
                if (lastStepIndex != Int.MIN_VALUE && index != lastStepIndex) haptics.frequentTick()
                lastStepIndex = index
            }
            onValueChange(result.value)
        },
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = {
            if (dragging) {
                val releaseMultiplier = magneticReleaseRadiusMultiplier(normalizedVelocityPerSecond)
                val settle = applyMagneticSliderForce(
                    rawValue = lastRawValue,
                    valueRange = valueRange,
                    anchors = anchors,
                    attractionRadiusFraction = attractionRadiusFraction * releaseMultiplier,
                    pullStrength = 1f,
                    settleRadiusFraction = attractionRadiusFraction * releaseMultiplier,
                )
                if (settle.anchor != null && settle.value != value) {
                    onValueChange(settle.anchor)
                    haptics.snap()
                } else {
                    haptics.gestureEnd()
                }
            }
            dragging = false
            activeAnchor = null
            lastStepIndex = Int.MIN_VALUE
            lastSampleAtMs = 0L
            normalizedVelocityPerSecond = 0f
            onValueChangeFinished?.invoke()
        },
        colors = colors,
        interactionSource = interactionSource,
    )
}
