package app.arbor.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

internal const val ArborStreamFrameMillis = 48L
internal const val ArborFadeInMillis = 150
internal const val ArborFadeOutMillis = 100

internal val ArborFadeIn: EnterTransition
    get() = fadeIn(
        animationSpec = tween(
            durationMillis = ArborFadeInMillis,
            easing = LinearOutSlowInEasing,
        ),
    )

internal val ArborFadeOut: ExitTransition
    get() = fadeOut(
        animationSpec = tween(
            durationMillis = ArborFadeOutMillis,
            easing = FastOutLinearInEasing,
        ),
    )

/** Fade-only visibility avoids relaying out a large chat subtree per frame. */
@Composable
internal fun ArborFadeVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = ArborFadeIn,
        exit = ArborFadeOut,
    ) {
        content()
    }
}

/** Fades a newly appended timeline/tool item once, not whenever it returns on-screen. */
@Composable
internal fun ArborAppearOnce(
    stableKey: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var appeared by rememberSaveable(stableKey) { mutableStateOf(false) }
    val state = remember(stableKey) {
        MutableTransitionState(appeared).apply { targetState = true }
    }
    LaunchedEffect(stableKey) { appeared = true }
    AnimatedVisibility(
        visibleState = state,
        modifier = modifier,
        enter = ArborFadeIn,
        exit = ExitTransition.None,
    ) {
        content()
    }
}
