package app.arbor.chat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class MagneticSliderResult(
    val value: Float,
    val anchor: Float?,
    val captured: Boolean,
)

internal data class SliderReleaseDecision(
    val target: Float?,
    val shouldRestoreRawValue: Boolean,
)

private const val SliderEpsilon = 0.00001f
private const val MagneticExitRadiusMultiplier = 1.18f
private const val MagneticStiffnessScale = 18f

internal fun normalizedSliderAnchors(
    valueRange: ClosedFloatingPointRange<Float>,
    anchors: List<Float>,
): List<Float> = anchors.asSequence()
    .map { it.coerceIn(valueRange.start, valueRange.endInclusive) }
    .distinct()
    .sorted()
    .toList()

internal fun sliderStepAnchors(
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
): List<Float> {
    if (steps < 0) return emptyList()
    val divisions = steps + 1
    val span = valueRange.endInclusive - valueRange.start
    return (0..divisions).map { index -> valueRange.start + span * index / divisions }
}

internal fun sliderAnchorFractions(
    valueRange: ClosedFloatingPointRange<Float>,
    anchors: List<Float>,
): List<Float> {
    val span = valueRange.endInclusive - valueRange.start
    if (span <= 0f) return emptyList()
    return normalizedSliderAnchors(valueRange, anchors)
        .map { (it - valueRange.start) / span }
}

/** Endpoints are represented by the rounded track caps, exactly like Material sliders. */
internal fun sliderInteriorAnchorFractions(
    valueRange: ClosedFloatingPointRange<Float>,
    anchors: List<Float>,
): List<Float> = sliderAnchorFractions(valueRange, anchors)
    .filter { it > 0.0001f && it < 0.9999f }

internal fun sliderMagneticRadius(
    valueRange: ClosedFloatingPointRange<Float>,
    anchors: List<Float>,
    attractionRadiusFraction: Float,
): Float {
    val normalized = normalizedSliderAnchors(valueRange, anchors)
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0f)
    if (span <= 0f || normalized.isEmpty()) return 0f
    val requested = span * attractionRadiusFraction.coerceIn(0f, 0.35f)
    val smallestSpacing = normalized.zipWithNext { first, second -> second - first }.minOrNull()
    return if (smallestSpacing == null) requested else min(requested, smallestSpacing * 0.5f)
}

/**
 * Chooses one stable magnetic well. A captured well gets a small hysteresis
 * margin, preventing rapid anchor switching when the pointer hovers around a
 * midpoint. Outside [snapRange] there is no magnetic behavior at all.
 */
internal fun selectMagneticSliderAnchor(
    rawValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    anchors: List<Float>,
    attractionRadiusFraction: Float,
    currentAnchor: Float? = null,
    snapRange: ClosedFloatingPointRange<Float>? = null,
): Float? {
    val raw = rawValue.coerceIn(valueRange.start, valueRange.endInclusive)
    if (snapRange != null && raw !in snapRange) return null
    val normalized = normalizedSliderAnchors(valueRange, anchors)
    if (normalized.isEmpty()) return null
    val radius = sliderMagneticRadius(valueRange, normalized, attractionRadiusFraction)
    if (radius <= 0f) return null

    val retained = currentAnchor
        ?.takeIf { anchor -> normalized.any { abs(it - anchor) <= SliderEpsilon } }
        ?.takeIf { anchor -> abs(raw - anchor) <= radius * MagneticExitRadiusMultiplier }
    if (retained != null) return retained

    val nearest = normalized.minByOrNull { abs(it - raw) } ?: return null
    return nearest.takeIf { abs(it - raw) <= radius }
}

/**
 * Maps the pointer through a smooth spring well. The mapping stays continuous
 * and monotonic: the thumb resists leaving an anchor but never teleports while
 * the pointer is down.
 */
internal fun applyMagneticSliderForce(
    rawValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    anchors: List<Float>,
    attractionRadiusFraction: Float = 0.10f,
    pullStrength: Float = 0.92f,
    maximumLivePull: Float = 0.88f,
    snapRange: ClosedFloatingPointRange<Float>? = null,
    currentAnchor: Float? = null,
): MagneticSliderResult {
    val raw = rawValue.coerceIn(valueRange.start, valueRange.endInclusive)
    val anchor = selectMagneticSliderAnchor(
        rawValue = raw,
        valueRange = valueRange,
        anchors = anchors,
        attractionRadiusFraction = attractionRadiusFraction,
        currentAnchor = currentAnchor,
        snapRange = snapRange,
    ) ?: return MagneticSliderResult(raw, null, false)

    val radius = sliderMagneticRadius(valueRange, anchors, attractionRadiusFraction)
    if (radius <= 0f) return MagneticSliderResult(raw, null, false)
    val distance = abs(raw - anchor)
    val normalizedDistance = (distance / radius).coerceIn(0f, 1f)
    val inverseSmoothStep = 1f - normalizedDistance * normalizedDistance * (3f - 2f * normalizedDistance)
    val stiffness = 1f + pullStrength.coerceIn(0f, 1f) * MagneticStiffnessScale * inverseSmoothStep
    val physicalPull = 1f - 1f / stiffness
    val pull = min(physicalPull, maximumLivePull.coerceIn(0f, 0.97f))
    val attracted = raw + (anchor - raw) * pull
    return MagneticSliderResult(
        value = attracted.coerceIn(valueRange.start, valueRange.endInclusive),
        anchor = anchor,
        captured = true,
    )
}

internal fun magneticReleaseRadiusMultiplier(normalizedVelocityPerSecond: Float): Float = when {
    abs(normalizedVelocityPerSecond) >= 2.4f -> 0.55f
    abs(normalizedVelocityPerSecond) >= 1.2f -> 0.78f
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
    val normalized = normalizedSliderAnchors(valueRange, anchors)
    if (normalized.isEmpty()) return null
    val raw = rawValue.coerceIn(valueRange.start, valueRange.endInclusive)
    if (snapRange != null && raw !in snapRange) return null
    val nearest = normalized.minByOrNull { abs(it - raw) } ?: return null
    if (alwaysNearest || snapRange != null) return nearest
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0f)
    return nearest.takeIf { abs(it - raw) <= span * radiusFraction.coerceIn(0f, 0.35f) }
}

internal fun sliderReleaseDecision(
    rawValue: Float,
    displayedValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    anchors: List<Float>,
    radiusFraction: Float,
    alwaysNearest: Boolean,
    snapRange: ClosedFloatingPointRange<Float>?,
    liveMagnetism: Boolean,
): SliderReleaseDecision {
    val target = releaseSnapAnchor(
        rawValue = rawValue,
        valueRange = valueRange,
        anchors = anchors,
        radiusFraction = radiusFraction,
        alwaysNearest = alwaysNearest,
        snapRange = snapRange,
    )
    return SliderReleaseDecision(
        target = target,
        shouldRestoreRawValue = target == null && liveMagnetism && abs(rawValue - displayedValue) > SliderEpsilon,
    )
}

private fun valueToFraction(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
): Float {
    val span = valueRange.endInclusive - valueRange.start
    return if (span <= 0f) 0f else ((value - valueRange.start) / span).coerceIn(0f, 1f)
}

private fun pointerXToValue(
    pointerX: Float,
    widthPx: Float,
    thumbWidthPx: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    layoutDirection: LayoutDirection,
): Float {
    val startPx = thumbWidthPx / 2f
    val endPx = max(startPx, widthPx - thumbWidthPx / 2f)
    val leftToRightFraction = if (endPx <= startPx) 0f else ((pointerX - startPx) / (endPx - startPx)).coerceIn(0f, 1f)
    val fraction = if (layoutDirection == LayoutDirection.Rtl) 1f - leftToRightFraction else leftToRightFraction
    return valueRange.start + (valueRange.endInclusive - valueRange.start) * fraction
}

private fun DrawScope.drawArborSliderTrack(
    fraction: Float,
    tickFractions: List<Float>,
    enabled: Boolean,
    colors: SliderColors,
    trackHeightPx: Float,
    thumbWidthPx: Float,
    thumbHeightPx: Float,
    layoutDirection: LayoutDirection,
    dragging: Boolean,
) {
    val centerY = size.height / 2f
    val trackStart = thumbWidthPx / 2f
    val trackEnd = max(trackStart, size.width - thumbWidthPx / 2f)
    val visualFraction = if (layoutDirection == LayoutDirection.Rtl) 1f - fraction else fraction
    val thumbX = trackStart + (trackEnd - trackStart) * visualFraction
    val trackTop = centerY - trackHeightPx / 2f
    val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
    val radius = trackHeightPx / 2f

    val inactiveTrack = if (enabled) colors.inactiveTrackColor else colors.disabledInactiveTrackColor
    val activeTrack = if (enabled) colors.activeTrackColor else colors.disabledActiveTrackColor
    val inactiveTick = if (enabled) colors.inactiveTickColor else colors.disabledInactiveTickColor
    val activeTick = if (enabled) colors.activeTickColor else colors.disabledActiveTickColor
    val thumbColor = if (enabled) colors.thumbColor else colors.disabledThumbColor

    drawRoundRect(
        color = inactiveTrack,
        topLeft = Offset(trackStart, trackTop),
        size = Size(trackWidth, trackHeightPx),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
    )

    val activeLeft = if (layoutDirection == LayoutDirection.Ltr) trackStart else thumbX
    val activeRight = if (layoutDirection == LayoutDirection.Ltr) thumbX else trackEnd
    if (activeRight > activeLeft) {
        drawRoundRect(
            color = activeTrack,
            topLeft = Offset(activeLeft, trackTop),
            size = Size(activeRight - activeLeft, trackHeightPx),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        )
    }

    val tickRadius = min(trackHeightPx * 0.13f, 1.5.dp.toPx())
    tickFractions.forEach { logicalFraction ->
        val visualTickFraction = if (layoutDirection == LayoutDirection.Rtl) 1f - logicalFraction else logicalFraction
        val x = trackStart + (trackEnd - trackStart) * visualTickFraction
        val isActive = logicalFraction <= fraction
        drawCircle(
            color = if (isActive) activeTick else inactiveTick,
            radius = tickRadius,
            center = Offset(x, centerY),
        )
    }

    val pressedWidth = if (dragging) thumbWidthPx * 0.72f else thumbWidthPx
    drawRoundRect(
        color = thumbColor,
        topLeft = Offset(thumbX - pressedWidth / 2f, centerY - thumbHeightPx / 2f),
        size = Size(pressedWidth, thumbHeightPx),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(pressedWidth / 2f, pressedWidth / 2f),
    )
}

/**
 * Arbor's single slider implementation.
 *
 * It owns pointer tracking, magnetic hysteresis, haptics, spring settling,
 * accessibility, tick rendering, and external-state synchronization. No
 * Material Slider state machine runs underneath it, so release animation and
 * snapping cannot race a second hidden implementation.
 */
@Composable
fun ArborSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    snapPoints: List<Float> = emptyList(),
    attractionRadiusFraction: Float = 0.10f,
    pullStrength: Float = 0.94f,
    maximumLivePull: Float = 0.90f,
    releaseSnapRadiusFraction: Float = 0.07f,
    snapRange: ClosedFloatingPointRange<Float>? = null,
    liveMagnetism: Boolean = snapPoints.isNotEmpty(),
    snapToNearestOnRelease: Boolean = false,
    showSnapPointDots: Boolean = true,
    springDampingRatio: Float = 0.68f,
    springStiffness: Float = 285f,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val density = LocalDensity.current
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val haptics = rememberArborHaptics()
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val settleAnimation = remember { Animatable(value.coerceIn(valueRange.start, valueRange.endInclusive)) }
    var visualValue by remember { mutableFloatStateOf(value.coerceIn(valueRange.start, valueRange.endInclusive)) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    var currentMagneticAnchor by remember { mutableStateOf<Float?>(null) }
    var lastRawValue by remember { mutableFloatStateOf(value.coerceIn(valueRange.start, valueRange.endInclusive)) }
    var lastDisplayedValue by remember { mutableFloatStateOf(lastRawValue) }
    var lastTickIndex by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var activeDragInteraction by remember { mutableStateOf<DragInteraction.Start?>(null) }

    val anchors = remember(valueRange.start, valueRange.endInclusive, steps, snapPoints) {
        normalizedSliderAnchors(
            valueRange = valueRange,
            anchors = if (steps > 0) sliderStepAnchors(valueRange, steps) else snapPoints,
        )
    }
    val tickFractions = remember(valueRange.start, valueRange.endInclusive, anchors, showSnapPointDots) {
        if (showSnapPointDots) sliderInteriorAnchorFractions(valueRange, anchors) else emptyList()
    }
    val thumbWidthPx = with(density) { 4.dp.toPx() }
    val thumbHeightPx = with(density) { 44.dp.toPx() }
    val trackHeightPx = with(density) { 16.dp.toPx() }
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0f)

    LaunchedEffect(valueRange.start, valueRange.endInclusive) {
        settleAnimation.updateBounds(valueRange.start, valueRange.endInclusive)
    }

    LaunchedEffect(value, dragging, settling, valueRange.start, valueRange.endInclusive) {
        if (!dragging && !settling) {
            val external = value.coerceIn(valueRange.start, valueRange.endInclusive)
            if (abs(visualValue - external) > SliderEpsilon) visualValue = external
            lastRawValue = external
            lastDisplayedValue = external
        }
    }

    fun launchSettle(targetValue: Float, initialVelocity: Float, commit: Boolean, snapHaptic: Boolean) {
        val target = targetValue.coerceIn(valueRange.start, valueRange.endInclusive)
        settleJob?.cancel()
        settling = true
        settleJob = scope.launch {
            try {
                val distance = target - visualValue
                val boundedVelocity = if (span <= 0f) 0f else {
                    val rawVelocity = initialVelocity.coerceIn(-span * 3f, span * 3f)
                    if (distance == 0f || rawVelocity == 0f || rawVelocity * distance > 0f) rawVelocity
                    else rawVelocity * 0.18f
                }
                settleAnimation.snapTo(visualValue)
                settleAnimation.animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = springDampingRatio.coerceIn(0.35f, 1f),
                        stiffness = springStiffness.coerceIn(Spring.StiffnessVeryLow, Spring.StiffnessHigh),
                    ),
                    initialVelocity = boundedVelocity,
                ) { visualValue = value }
                visualValue = target
                lastDisplayedValue = target
                lastRawValue = target
                if (commit) currentOnValueChange(target)
                if (snapHaptic) haptics.snap() else haptics.gestureEnd()
                currentOnValueChangeFinished?.invoke()
            } finally {
                settling = false
                settleJob = null
            }
        }
    }

    val semanticModifier = Modifier.semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(
            current = visualValue.coerceIn(valueRange.start, valueRange.endInclusive),
            range = valueRange,
            steps = steps.coerceAtLeast(0),
        )
        if (!enabled) disabled()
        setProgress { requested ->
            if (!enabled) return@setProgress false
            val requestedTarget = requested.coerceIn(valueRange.start, valueRange.endInclusive)
            val target = if (steps > 0 && anchors.isNotEmpty()) {
                anchors.minByOrNull { abs(it - requestedTarget) } ?: requestedTarget
            } else {
                requestedTarget
            }
            settleJob?.cancel()
            launchSettle(target, initialVelocity = 0f, commit = true, snapHaptic = anchors.isNotEmpty())
            true
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .horizontalGesturePriority(enabled)
            .then(semanticModifier)
            .focusable(enabled)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(
                enabled,
                valueRange.start,
                valueRange.endInclusive,
                anchors,
                attractionRadiusFraction,
                pullStrength,
                maximumLivePull,
                releaseSnapRadiusFraction,
                snapRange,
                liveMagnetism,
                snapToNearestOnRelease,
                springDampingRatio,
                springStiffness,
                layoutDirection,
            ) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    settleJob?.cancel()
                    settleJob = null
                    settling = false

                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    dragging = true
                    haptics.gestureStart()
                    val dragInteraction = DragInteraction.Start()
                    activeDragInteraction = dragInteraction
                    interactionSource.tryEmit(dragInteraction)
                    currentMagneticAnchor = null
                    lastTickIndex = Int.MIN_VALUE
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    fun updateFromChange(change: PointerInputChange) {
                        val raw = pointerXToValue(
                            pointerX = change.position.x,
                            widthPx = widthPx.toFloat(),
                            thumbWidthPx = thumbWidthPx,
                            valueRange = valueRange,
                            layoutDirection = layoutDirection,
                        )
                        val result = if (liveMagnetism && anchors.isNotEmpty()) {
                            applyMagneticSliderForce(
                                rawValue = raw,
                                valueRange = valueRange,
                                anchors = anchors,
                                attractionRadiusFraction = attractionRadiusFraction,
                                pullStrength = pullStrength,
                                maximumLivePull = maximumLivePull,
                                snapRange = snapRange,
                                currentAnchor = currentMagneticAnchor,
                            )
                        } else {
                            MagneticSliderResult(raw, null, false)
                        }
                        if (result.anchor != currentMagneticAnchor) {
                            currentMagneticAnchor = result.anchor
                            if (result.anchor != null) haptics.selection()
                        }
                        if (anchors.size > 1) {
                            val tickIndex = anchors.indices.minByOrNull { index -> abs(anchors[index] - raw) } ?: 0
                            if (lastTickIndex != Int.MIN_VALUE && tickIndex != lastTickIndex) haptics.frequentTick()
                            lastTickIndex = tickIndex
                        }
                        lastRawValue = raw
                        lastDisplayedValue = result.value
                        visualValue = result.value
                        currentOnValueChange(result.value)
                    }

                    updateFromChange(down)
                    val pointerId: PointerId = down.id
                    var releasedNormally = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        if (!change.pressed) {
                            releasedNormally = true
                            break
                        }
                        if (change.positionChanged()) {
                            change.consume()
                            updateFromChange(change)
                        }
                    }

                    dragging = false
                    val interaction = activeDragInteraction
                    activeDragInteraction = null
                    if (interaction != null) {
                        interactionSource.tryEmit(
                            if (releasedNormally) DragInteraction.Stop(interaction)
                            else DragInteraction.Cancel(interaction),
                        )
                    }

                    if (!releasedNormally) {
                        haptics.gestureEnd()
                        currentOnValueChangeFinished?.invoke()
                        currentMagneticAnchor = null
                        return@awaitEachGesture
                    }

                    val xVelocity = velocityTracker.calculateVelocity().x
                    val usableTrackWidth = max(1f, widthPx.toFloat() - thumbWidthPx)
                    val direction = if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
                    val valueVelocity = direction * (xVelocity / usableTrackWidth) * span
                    val normalizedVelocity = if (span <= 0f) 0f else valueVelocity / span
                    val releaseMultiplier = magneticReleaseRadiusMultiplier(normalizedVelocity)
                    val decision = sliderReleaseDecision(
                        rawValue = lastRawValue,
                        displayedValue = lastDisplayedValue,
                        valueRange = valueRange,
                        anchors = anchors,
                        radiusFraction = releaseSnapRadiusFraction * releaseMultiplier,
                        alwaysNearest = steps > 0 || snapToNearestOnRelease,
                        snapRange = snapRange,
                        liveMagnetism = liveMagnetism,
                    )
                    currentMagneticAnchor = null
                    lastTickIndex = Int.MIN_VALUE

                    when {
                        decision.target != null -> launchSettle(
                            targetValue = decision.target,
                            initialVelocity = valueVelocity * 0.22f,
                            commit = true,
                            snapHaptic = true,
                        )
                        decision.shouldRestoreRawValue -> launchSettle(
                            targetValue = lastRawValue,
                            initialVelocity = valueVelocity * 0.12f,
                            commit = true,
                            snapHaptic = false,
                        )
                        else -> {
                            haptics.gestureEnd()
                            currentOnValueChangeFinished?.invoke()
                        }
                    }
                }
            },
    ) {
        drawArborSliderTrack(
            fraction = valueToFraction(visualValue, valueRange),
            tickFractions = tickFractions,
            enabled = enabled,
            colors = colors,
            trackHeightPx = trackHeightPx,
            thumbWidthPx = thumbWidthPx,
            thumbHeightPx = thumbHeightPx,
            layoutDirection = layoutDirection,
            dragging = dragging,
        )
    }
}
