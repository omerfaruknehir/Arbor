package app.xylune.chat.ui

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Central haptic vocabulary for Xylune.
 *
 * View haptics respect Android's system haptic setting and require no vibration
 * permission. Events are rate-limited so rapid sliders and gestures feel
 * textured instead of buzzing continuously.
 */
@Stable
class XyluneHapticController internal constructor(private val view: View) {
    private var lastFeedbackAtMs = 0L
    private var lastConstant = Int.MIN_VALUE

    fun tap() = perform(HapticFeedbackConstants.KEYBOARD_TAP, 28L)

    fun selection() = perform(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK
        else HapticFeedbackConstants.CLOCK_TICK,
        24L,
    )

    fun frequentTick() = perform(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        else HapticFeedbackConstants.CLOCK_TICK,
        34L,
    )

    fun snap() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.CONTEXT_CLICK,
        50L,
    )

    fun streamTick() = perform(
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        else HapticFeedbackConstants.CLOCK_TICK,
        140L,
    )

    fun streamComplete() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.CONTEXT_CLICK,
        180L,
    )

    fun toggle(enabled: Boolean) = perform(
        if (enabled && Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.KEYBOARD_TAP,
        44L,
    )

    fun confirm() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.LONG_PRESS,
        70L,
    )

    fun reject() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS,
        70L,
    )

    fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS, 80L)

    fun gestureStart() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_START
        else HapticFeedbackConstants.CLOCK_TICK,
        60L,
    )

    fun gestureEnd() = perform(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_END
        else HapticFeedbackConstants.CLOCK_TICK,
        60L,
    )

    private fun perform(constant: Int, minimumIntervalMs: Long) {
        if (!view.isHapticFeedbackEnabled || !view.isAttachedToWindow) return
        val now = SystemClock.uptimeMillis()
        if (constant == lastConstant && now - lastFeedbackAtMs < minimumIntervalMs) return
        lastConstant = constant
        lastFeedbackAtMs = now
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberXyluneHaptics(): XyluneHapticController {
    val view = LocalView.current
    return remember(view) { XyluneHapticController(view) }
}
