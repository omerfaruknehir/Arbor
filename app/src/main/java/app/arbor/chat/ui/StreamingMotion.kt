package app.arbor.chat.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

internal const val StreamingFadeDurationMillis = 180
internal const val StreamingFadeOutDurationMillis = 120
internal const val StreamingFadeStartAlpha = 0.48f
internal const val WorkingCardExpansionDurationMillis = 220

internal fun streamingFadeIn(): EnterTransition = fadeIn(
    initialAlpha = StreamingFadeStartAlpha,
    animationSpec = tween(StreamingFadeDurationMillis, easing = FastOutSlowInEasing),
)

internal fun streamingFadeOut(): ExitTransition = fadeOut(
    animationSpec = tween(StreamingFadeOutDurationMillis, easing = FastOutSlowInEasing),
)

internal fun workingCardExpandIn(): EnterTransition =
    expandVertically(
        expandFrom = Alignment.Top,
        animationSpec = tween(WorkingCardExpansionDurationMillis),
        clip = true,
    ) + fadeIn(
        initialAlpha = 0f,
        animationSpec = tween(WorkingCardExpansionDurationMillis),
    )

internal fun workingCardCollapseOut(): ExitTransition =
    shrinkVertically(
        shrinkTowards = Alignment.Top,
        animationSpec = tween(WorkingCardExpansionDurationMillis),
        clip = true,
    ) + fadeOut(
        animationSpec = tween(WorkingCardExpansionDurationMillis / 2),
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


internal fun nextStreamingTextFrame(rendered: String, target: String): String = when {
    target == rendered -> rendered
    target.startsWith(rendered) -> {
        val backlog = target.length - rendered.length
        val step = when {
            backlog > 768 -> 192
            backlog > 256 -> 96
            backlog > 96 -> 48
            backlog > 32 -> 24
            else -> backlog
        }.coerceAtMost(backlog)
        target.take(rendered.length + step)
    }
    else -> target
}

/**
 * Frame-aligns streaming commits and smooths large provider chunks before
 * expensive Markdown parsing. Thirty visible updates per second are materially
 * smoother than the former 20 Hz timer, while still avoiding a full parse on
 * every display frame. Large chunks are revealed over several commits instead
 * of appearing as one jump.
 */
@Composable
internal fun rememberBatchedStreamingText(
    text: String,
    streaming: Boolean,
    intervalNanos: Long = 33_000_000L,
): String {
    val latestText by rememberUpdatedState(text)
    var renderedText by remember { mutableStateOf(text) }
    LaunchedEffect(streaming, intervalNanos) {
        if (!streaming) {
            renderedText = latestText
            return@LaunchedEffect
        }

        var lastCommitNanos = 0L
        while (isActive) {
            val frameNanos = withFrameNanos { it }
            if (lastCommitNanos != 0L && frameNanos - lastCommitNanos < intervalNanos) continue
            lastCommitNanos = frameNanos

            renderedText = nextStreamingTextFrame(renderedText, latestText)
        }
    }
    return if (streaming) renderedText else text
}
