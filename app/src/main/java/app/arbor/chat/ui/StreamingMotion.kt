package app.arbor.chat.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal const val StreamingFadeDurationMillis = 150
internal const val StreamingFadeOutDurationMillis = 100
internal const val StreamingFadeStartAlpha = 0.52f
private fun streamingRenderIntervalMillis(textLength: Int): Long = when {
    textLength >= 12_000 -> 84L
    textLength >= 6_000 -> 66L
    textLength >= 2_000 -> 50L
    else -> 34L
}

internal fun streamingFadeIn(): EnterTransition = fadeIn(
    initialAlpha = StreamingFadeStartAlpha,
    animationSpec = tween(StreamingFadeDurationMillis, easing = FastOutSlowInEasing),
)

internal fun streamingFadeOut(): ExitTransition = fadeOut(
    animationSpec = tween(StreamingFadeOutDurationMillis, easing = FastOutSlowInEasing),
)

/**
 * Coalesces a high-frequency token stream to roughly one expensive text/Markdown
 * render every two display frames. The latest value is always committed
 * immediately when streaming ends, so no content is lost.
 */
@Composable
internal fun rememberCoalescedStreamingText(text: String, streaming: Boolean): String {
    val latestText by rememberUpdatedState(text)
    var renderedText by remember { mutableStateOf(text) }

    LaunchedEffect(streaming) {
        if (!streaming) {
            renderedText = latestText
            return@LaunchedEffect
        }

        while (isActive) {
            val latest = latestText
            delay(streamingRenderIntervalMillis(latest.length))
            val newest = latestText
            if (renderedText != newest) renderedText = newest
        }
    }

    return renderedText
}

/**
 * Fade-only appearance for newly appended streaming blocks. ModulateAlpha avoids
 * allocating an off-screen texture for every tool/reasoning card, which was the
 * main cause of the single-digit-FPS fade path on large messages.
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
                animationSpec = tween(StreamingFadeDurationMillis, easing = FastOutSlowInEasing),
            )
        } else {
            alpha.snapTo(1f)
        }
    }
    Box(
        modifier.graphicsLayer {
            this.alpha = alpha.value
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
    ) {
        content()
    }
}
