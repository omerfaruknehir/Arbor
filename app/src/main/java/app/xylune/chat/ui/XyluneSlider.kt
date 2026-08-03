package app.xylune.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The one Xylune slider.
 *
 * Gesture arbitration, keyboard/accessibility input, RTL behavior, touch slop,
 * cancellation and release settling belong to Compose's maintained Material
 * slider. Xylune only adds its haptic language and a stable semantic value.
 *
 * Continuous settings remain continuous. A bounded named scale may opt into
 * continuous tracking plus visible release snap points with [snapOnRelease].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XyluneSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    snapOnRelease: Boolean = false,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val haptics = rememberXyluneHaptics()
    val dragging by interactionSource.collectIsDraggedAsState()
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    var lastStepIndex by remember(valueRange.start, valueRange.endInclusive, steps, snapOnRelease) {
        mutableIntStateOf(
            if (snapOnRelease) sliderSnapIndex(value, valueRange, steps + 2)
            else sliderStepIndex(value, valueRange, steps),
        )
    }
    val activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = .78f)
    val inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .66f)
    val thumbColor = if (enabled) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)

    LaunchedEffect(dragging) {
        if (dragging) {
            lastStepIndex = if (snapOnRelease) sliderSnapIndex(value, valueRange, steps + 2)
            else sliderStepIndex(value, valueRange, steps)
            haptics.gestureStart()
        }
    }

    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = { requested ->
            val normalized = requested.coerceIn(valueRange.start, valueRange.endInclusive)
            if (steps > 0 || snapOnRelease) {
                val nextIndex = if (snapOnRelease) {
                    sliderSnapIndex(normalized, valueRange, steps + 2)
                } else {
                    sliderStepIndex(normalized, valueRange, steps)
                }
                if (nextIndex != lastStepIndex) {
                    haptics.frequentTick()
                    lastStepIndex = nextIndex
                }
            }
            currentOnValueChange(normalized)
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .horizontalGesturePriority(enabled)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.coerceIn(valueRange.start, valueRange.endInclusive),
                    range = valueRange,
                    steps = steps.coerceAtLeast(0),
                )
            }
            .drawWithContent {
                drawContent()
                if (snapOnRelease && steps > 0) {
                    val intervals = steps + 1
                    val span = valueRange.endInclusive - valueRange.start
                    val progress = if (span <= 0f) 0f else
                        ((value - valueRange.start) / span).coerceIn(0f, 1f)
                    val currentIndex = sliderStepIndex(value, valueRange, steps)
                    val inset = 12.dp.toPx()
                    val usableWidth = (size.width - inset * 2f).coerceAtLeast(0f)
                    for (index in 0..intervals) {
                        if (index == currentIndex) continue
                        val fraction = index.toFloat() / intervals
                        drawCircle(
                            color = if (fraction <= progress) activeTickColor else inactiveTickColor,
                            radius = 1.5.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(
                                x = inset + usableWidth * fraction,
                                y = size.height / 2f,
                            ),
                        )
                    }
                }
            },
        enabled = enabled,
        valueRange = valueRange,
        steps = if (snapOnRelease) 0 else steps.coerceAtLeast(0),
        onValueChangeFinished = {
            if (steps > 0 || snapOnRelease) haptics.snap() else haptics.gestureEnd()
            currentOnValueChangeFinished?.invoke()
        },
        colors = colors,
        interactionSource = interactionSource,
        thumb = {
            Box(
                Modifier
                    .size(22.dp)
                    .background(thumbColor, CircleShape),
            )
        },
    )
}

internal fun sliderSnapIndex(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    anchorCount: Int,
): Int {
    if (anchorCount <= 1) return 0
    val span = valueRange.endInclusive - valueRange.start
    if (span <= 0f) return 0
    val intervals = anchorCount - 1
    return (((value.coerceIn(valueRange.start, valueRange.endInclusive) - valueRange.start) /
        span) * intervals).roundToInt().coerceIn(0, intervals)
}

internal fun sliderStepIndex(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
): Int {
    if (steps <= 0) return -1
    val span = valueRange.endInclusive - valueRange.start
    if (span <= 0f) return 0
    val intervals = steps + 1
    return (((value.coerceIn(valueRange.start, valueRange.endInclusive) - valueRange.start) /
        span) * intervals).roundToInt().coerceIn(0, intervals)
}
