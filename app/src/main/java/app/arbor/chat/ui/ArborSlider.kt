package app.arbor.chat.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

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
    attractionRadiusFraction: Float = 0.07f,
    pullStrength: Float = 0.96f,
    maximumLivePull: Float = 0.80f,
    snapRange: ClosedFloatingPointRange<Float>? = null,
): MagneticSliderResult {
    val start = valueRange.start
    val end = valueRange.endInclusive
    val span = (end - start).coerceAtLeast(0f)
    val raw = rawValue.coerceIn(start, end)
    if (span <= 0f || anchors.isEmpty()) return MagneticSliderResult(raw, null, false)
    if (snapRange != null && raw !in snapRange) return MagneticSliderResult(raw, null, false)

    val normalizedAnchors = anchors.asSequence()
        .map { it.coerceIn(start, end) }
        .distinct()
        .sorted()
        .toList()
    val anchor = normalizedAnchors.asSequence()
        .minByOrNull { abs(it - raw) }
        ?: return MagneticSliderResult(raw, null, false)
    val distance = abs(anchor - raw)
    val requestedRadius = span * attractionRadiusFraction.coerceIn(0f, 0.25f)
    val minimumSpacing = normalizedAnchors.zipWithNext { a, b -> b - a }.minOrNull()
    // Magnetic wells must never overlap. Overlap makes the nearest-anchor
    // choice switch discontinuously at the midpoint and is perceived as a
    // thumb jump rather than a spring.
    val attractionRadius = if (minimumSpacing != null) {
        min(requestedRadius, minimumSpacing * 0.49f)
    } else {
        requestedRadius
    }
    if (attractionRadius <= 0f || distance >= attractionRadius) {
        return MagneticSliderResult(raw, null, false)
    }

    // A compact Hooke-like well: the pull is strongest close to the anchor,
    // fades smoothly to zero at the capture radius, and remains monotonic.
    // The thumb therefore feels attached to a spring rather than being warped
    // or teleported onto a fixed value.
    val normalizedDistance = (distance / attractionRadius).coerceIn(0f, 1f)
    // A sixth-power well keeps the outer edge smooth but makes the inner half
    // substantially stronger. The thumb still remains monotonic and never
    // teleports onto the anchor while the pointer is down.
    val springInfluence = 1f - normalizedDistance * normalizedDistance *
        normalizedDistance * normalizedDistance * normalizedDistance * normalizedDistance
    // Keep some visible displacement while the pointer is down. This makes
    // the post-release spring travel perceptible instead of appearing instant,
    // while the wider well still feels stronger and harder to pull away from.
    val resistance = min(
        pullStrength.coerceIn(0f, 0.998f) * springInfluence,
        maximumLivePull.coerceIn(0f, 0.92f),
    )
    val attracted = raw + (anchor - raw) * resistance
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
    snapRange: ClosedFloatingPointRange<Float>? = null,
): Float? {
    if (anchors.isEmpty()) return null
    val raw = rawValue.coerceIn(valueRange.start, valueRange.endInclusive)
    if (snapRange != null && raw !in snapRange) return null
    val anchor = anchors.minByOrNull { abs(it - raw) } ?: return null
    if (alwaysNearest || snapRange != null) return anchor
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0f)
    return anchor.takeIf { abs(it - raw) <= span * radiusFraction.coerceIn(0f, 0.25f) }
}

internal fun sliderAnchorFractions(
    valueRange: ClosedFloatingPointRange<Float>,
    anchors: List<Float>,
): List<Float> {
    val span = valueRange.endInclusive - valueRange.start
    if (span <= 0f) return emptyList()
    return anchors.map { ((it.coerceIn(valueRange.start, valueRange.endInclusive) - valueRange.start) / span) }
        .distinct()
        .sorted()
}

/** Material-style ticks exclude the range endpoints, which are already shown by the track caps. */
internal fun sliderInteriorAnchorFractions(
    valueRange: ClosedFloatingPointRange<Float>,
    anchors: List<Float>,
): List<Float> = sliderAnchorFractions(valueRange, anchors)
    .filter { it > 0.0001f && it < 0.9999f }

/** Material slider with spring attraction, spring settling, tactile ticks, and visible anchors. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArborSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    snapPoints: List<Float> = emptyList(),
    attractionRadiusFraction: Float = 0.11f,
    pullStrength: Float = 0.995f,
    maximumLivePull: Float = 0.80f,
    releaseSnapRadiusFraction: Float = 0.065f,
    snapRange: ClosedFloatingPointRange<Float>? = null,
    liveMagnetism: Boolean = snapPoints.isNotEmpty(),
    snapToNearestOnRelease: Boolean = false,
    showSnapPointDots: Boolean = true,
    springDampingRatio: Float = 0.72f,
    springStiffness: Float = 420f,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val haptics = rememberArborHaptics()
    val scope = rememberCoroutineScope()
    val settleAnim = remember { Animatable(value) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var visualValue by remember { mutableStateOf(value.coerceIn(valueRange.start, valueRange.endInclusive)) }
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

    val anchorFractions = remember(valueRange.start, valueRange.endInclusive, anchors) {
        sliderAnchorFractions(valueRange, anchors)
    }
    val visibleValueFraction = remember(visualValue, valueRange.start, valueRange.endInclusive) {
        val span = valueRange.endInclusive - valueRange.start
        if (span <= 0f) 0f else ((visualValue.coerceIn(valueRange.start, valueRange.endInclusive) - valueRange.start) / span)
    }

    SideEffect {
        if (!dragging && settleJob == null) {
            val external = value.coerceIn(valueRange.start, valueRange.endInclusive)
            if (abs(visualValue - external) > 0.00001f) visualValue = external
        }
    }

    Box(modifier = modifier.horizontalGesturePriority(enabled)) {
        Slider(
        value = visualValue.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = { raw ->
            if (!dragging) {
                settleJob?.cancel()
                settleJob = null
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
                    maximumLivePull = maximumLivePull,
                    snapRange = snapRange,
                )
            } else {
                MagneticSliderResult(raw, null, false)
            }

            val proximityAnchor = result.anchor
            if (proximityAnchor != activeAnchor) {
                activeAnchor = proximityAnchor
                if (proximityAnchor != null) haptics.selection()
            }

            if (anchors.size > 1 && (steps > 0 || !liveMagnetism)) {
                val tickIndex = anchors.indices.minByOrNull { index -> abs(anchors[index] - raw) } ?: 0
                if (lastTickIndex != Int.MIN_VALUE && tickIndex != lastTickIndex) haptics.frequentTick()
                lastTickIndex = tickIndex
            }

            lastDeliveredValue = result.value
            visualValue = result.value
            onValueChange(result.value)
        },
        modifier = Modifier.fillMaxWidth(),
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
                        snapRange = snapRange,
                    )
                }
                when {
                    target != null && abs(target - lastDeliveredValue) > 0.00001f -> {
                        val startValue = lastDeliveredValue.coerceIn(valueRange.start, valueRange.endInclusive)
                        val valueSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0f)
                        val initialVelocity = (normalizedVelocityPerSecond * valueSpan * 0.18f)
                            .coerceIn(-valueSpan * 2f, valueSpan * 2f)
                        settleJob?.cancel()
                        settleJob = scope.launch {
                            settleAnim.snapTo(startValue)
                            settleAnim.updateBounds(valueRange.start, valueRange.endInclusive)
                            settleAnim.animateTo(
                                targetValue = target,
                                animationSpec = spring(
                                    dampingRatio = springDampingRatio.coerceIn(0.35f, 1f),
                                    stiffness = springStiffness.coerceAtLeast(1f),
                                ),
                                initialVelocity = initialVelocity,
                            ) {
                                visualValue = value
                            }
                            visualValue = target
                            lastDeliveredValue = target
                            onValueChange(target)
                            haptics.snap()
                            onValueChangeFinished?.invoke()
                            settleJob = null
                        }
                    }
                    target == null && liveMagnetism && steps == 0 &&
                        abs(lastRawValue - lastDeliveredValue) > 0.00001f -> {
                        // Do not persist a magnetically displaced value when the
                        // release occurs outside a snap well.
                        visualValue = lastRawValue
                        lastDeliveredValue = lastRawValue
                        onValueChange(lastRawValue)
                        haptics.gestureEnd()
                        onValueChangeFinished?.invoke()
                    }
                    else -> {
                        haptics.gestureEnd()
                        onValueChangeFinished?.invoke()
                    }
                }
            }
            dragging = false
            activeAnchor = null
            lastTickIndex = Int.MIN_VALUE
            lastSampleAtMs = 0L
            normalizedVelocityPerSecond = 0f
        },
        colors = colors,
        interactionSource = interactionSource,
        track = { sliderState ->
            if (showSnapPointDots && snapPoints.isNotEmpty()) {
                Box(Modifier.fillMaxWidth()) {
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled,
                        colors = colors,
                    )
                    val activeDotColor = if (enabled) colors.activeTickColor else colors.disabledActiveTickColor
                    val inactiveDotColor = if (enabled) colors.inactiveTickColor else colors.disabledInactiveTickColor
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(SliderDefaults.TickSize)
                            .align(Alignment.Center),
                    ) {
                        // Use Material's own tick diameter and keep endpoint
                        // anchors fully inside the rounded track caps. The old
                        // fillMaxSize overlay inherited the whole slider slot
                        // height and could place/scale the dots inconsistently.
                        val radius = SliderDefaults.TickSize.toPx() / 2f
                        anchorFractions.forEach { fraction ->
                            val x = (size.width * fraction).coerceIn(radius, size.width - radius)
                            drawCircle(
                                color = if (fraction <= visibleValueFraction) activeDotColor else inactiveDotColor,
                                radius = radius,
                                center = Offset(x, size.height / 2f),
                            )
                        }
                    }
                }
            } else {
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    colors = colors,
                )
            }
        },
        )
    }
}
