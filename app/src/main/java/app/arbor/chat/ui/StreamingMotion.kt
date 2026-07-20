package app.arbor.chat.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

internal const val StreamingFadeDurationMillis = 180
internal const val StreamingFadeOutDurationMillis = 120
internal const val StreamingFadeStartAlpha = 0.22f
internal const val WorkingCardExpansionDurationMillis = 220

internal fun streamingFadeIn(): EnterTransition = fadeIn(
    initialAlpha = StreamingFadeStartAlpha,
    animationSpec = tween(StreamingFadeDurationMillis, easing = LinearEasing),
)

internal fun streamingFadeOut(): ExitTransition = fadeOut(
    animationSpec = tween(StreamingFadeOutDurationMillis, easing = LinearEasing),
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
                animationSpec = tween(StreamingFadeDurationMillis, easing = LinearEasing),
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

/**
 * Coalesces fast token/database updates before expensive Markdown parsing.
 * The text itself updates at 20 Hz while scrolling and layer animations remain
 * frame-paced by the renderer. Final content is exposed immediately.
 */
@Composable
internal fun rememberBatchedStreamingText(
    text: String,
    streaming: Boolean,
    intervalMillis: Long = 50L,
): String {
    val latestText by rememberUpdatedState(text)
    var renderedText by remember { mutableStateOf(text) }
    LaunchedEffect(streaming, intervalMillis) {
        if (!streaming) {
            renderedText = latestText
            return@LaunchedEffect
        }
        while (isActive) {
            val next = latestText
            if (next != renderedText) renderedText = next
            delay(intervalMillis)
        }
    }
    return if (streaming) renderedText else text
}
