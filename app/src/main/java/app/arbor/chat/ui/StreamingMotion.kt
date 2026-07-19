package app.arbor.chat.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

internal const val StreamingFadeDurationMillis = 180
internal const val StreamingFadeOutDurationMillis = 120
internal const val StreamingFadeStartAlpha = 0.22f

internal fun streamingFadeIn(): EnterTransition = fadeIn(
    initialAlpha = StreamingFadeStartAlpha,
    animationSpec = tween(StreamingFadeDurationMillis, easing = LinearEasing),
)

internal fun streamingFadeOut(): ExitTransition = fadeOut(
    animationSpec = tween(StreamingFadeOutDurationMillis, easing = LinearEasing),
)

/**
 * Fade-only appearance for newly appended streaming blocks. It deliberately
 * animates only the render-layer alpha, so tool/reasoning insertion never
 * drives a per-frame remeasure of the chat list.
 */
@Composable
internal fun StreamingFade(
    transitionKey: Any?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val alpha = remember(transitionKey) {
        Animatable(if (enabled) StreamingFadeStartAlpha else 1f)
    }
    LaunchedEffect(transitionKey, enabled) {
        if (enabled) {
            alpha.snapTo(StreamingFadeStartAlpha)
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(StreamingFadeDurationMillis, easing = LinearEasing),
            )
        } else {
            alpha.snapTo(1f)
        }
    }
    Box(modifier.graphicsLayer { this.alpha = alpha.value }) {
        content()
    }
}
