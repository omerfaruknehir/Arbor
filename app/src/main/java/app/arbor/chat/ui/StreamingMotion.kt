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
            backlog > 1_024 -> 48
            backlog > 512 -> 36
            backlog > 256 -> 28
            backlog > 128 -> 20
            backlog > 64 -> 14
            backlog > 24 -> 10
            else -> minOf(backlog, 6)
        }.coerceAtMost(backlog)
        target.take(rendered.length + step)
    }
    else -> target
}

/**
 * Frame-aligns streaming commits and smooths provider/database bursts before
 * expensive Markdown parsing. The renderer stays at 30 visible updates per
 * second, but reveals word/token-sized micro-batches instead of dumping tens or
 * hundreds of characters at once. A bounded catch-up rate prevents an unusually
 * fast provider from leaving the UI permanently behind.
 *
 * When the worker changes the message from STREAMING to COMPLETE, any remaining
 * backlog is drained with the same cadence. This avoids the former final-frame
 * jump without increasing Markdown parse frequency.
 */
@Composable
internal fun rememberBatchedStreamingText(
    text: String,
    streaming: Boolean,
    intervalNanos: Long = 33_000_000L,
): String {
    val latestText by rememberUpdatedState(text)
    var renderedText by remember { mutableStateOf(text) }
    var wasStreaming by remember { mutableStateOf(streaming) }
    LaunchedEffect(streaming, intervalNanos) {
        var lastCommitNanos = 0L
        if (streaming) wasStreaming = true

        while (isActive && (streaming || (wasStreaming && renderedText != latestText))) {
            val frameNanos = withFrameNanos { it }
            if (lastCommitNanos != 0L && frameNanos - lastCommitNanos < intervalNanos) continue
            lastCommitNanos = frameNanos

            renderedText = nextStreamingTextFrame(renderedText, latestText)
        }

        if (!streaming) {
            renderedText = latestText
            wasStreaming = false
        }
    }
    return if (streaming || wasStreaming) renderedText else text
}
