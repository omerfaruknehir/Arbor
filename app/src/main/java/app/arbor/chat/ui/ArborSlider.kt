package app.arbor.chat.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The one Arbor slider.
 *
 * Gesture arbitration, keyboard/accessibility input, RTL behavior, touch slop,
 * cancellation and release settling belong to Compose's maintained Material
 * slider. Arbor only adds its haptic language and a stable semantic value.
 *
 * Discrete choices do not belong on a slider. Use buttons, chips, or a menu for
 * named options such as thinking effort and panel shape.
 */
@Composable
fun ArborSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val haptics = rememberArborHaptics()
    val dragging by interactionSource.collectIsDraggedAsState()
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    var lastStepIndex by remember(valueRange.start, valueRange.endInclusive, steps) {
        mutableIntStateOf(sliderStepIndex(value, valueRange, steps))
    }

    LaunchedEffect(dragging) {
        if (dragging) {
            lastStepIndex = sliderStepIndex(value, valueRange, steps)
            haptics.gestureStart()
        }
    }

    Slider(
        value = value.coerceIn(valueRange.start, valueRange.endInclusive),
        onValueChange = { requested ->
            val normalized = requested.coerceIn(valueRange.start, valueRange.endInclusive)
            if (steps > 0) {
                val nextIndex = sliderStepIndex(normalized, valueRange, steps)
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
            },
        enabled = enabled,
        valueRange = valueRange,
        steps = steps.coerceAtLeast(0),
        onValueChangeFinished = {
            if (steps > 0) haptics.snap() else haptics.gestureEnd()
            currentOnValueChangeFinished?.invoke()
        },
        colors = colors,
        interactionSource = interactionSource,
    )
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
