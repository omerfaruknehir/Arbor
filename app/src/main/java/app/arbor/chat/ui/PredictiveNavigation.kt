package app.arbor.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException

private val NavigationEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * A navigation host which never composes two destinations at once.
 *
 * AnimatedContent/NavHost-style transitions intentionally keep the outgoing and
 * incoming pages alive together. That is too expensive for Arbor's screens and
 * can expose two full pages when a transition is interrupted. This host fades
 * and shifts the current render layer out, replaces it while fully separated,
 * and then brings the new page in. Saved destination state is retained, but
 * there is exactly one page tree in composition at every frame.
 *
 * Predictive-back preview is deliberately not implemented here. A preview needs
 * either a second live destination or a cached bitmap. The former caused the
 * overlap/jank bug; the latter is not reliable with AndroidView/Markdown. System
 * back therefore uses the same short, single-page transition as toolbar back.
 */
@Composable
internal fun <T : Any> PredictiveNavigationHost(
    targetState: T,
    backTarget: T?,
    onBack: (T) -> Unit,
    depth: (T) -> Int,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = backTarget != null,
    label: String = "SinglePageNavigation",
    content: @Composable (T) -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val animationLabel = label
    val stateHolder = rememberSaveableStateHolder()
    val progress = remember { Animatable(1f) }

    var displayedState by remember { mutableStateOf(targetState) }
    var displayedDepth by remember { mutableIntStateOf(depth(targetState)) }
    var direction by remember { mutableFloatStateOf(0f) }
    var outgoing by remember { mutableStateOf(false) }

    val latestBackTarget by rememberUpdatedState(backTarget)
    val latestOnBack by rememberUpdatedState(onBack)

    BackHandler(enabled = backEnabled && backTarget != null) {
        latestBackTarget?.let(latestOnBack)
    }

    LaunchedEffect(targetState) {
        if (displayedState == targetState) {
            outgoing = false
            progress.snapTo(1f)
            return@LaunchedEffect
        }

        val nextDepth = depth(targetState)
        direction = if (nextDepth >= displayedDepth) 1f else -1f

        try {
            // Make cancellation deterministic: never inherit a half-finished
            // layer transform from a previous rapid navigation request.
            progress.snapTo(1f)
            outgoing = true
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 70, easing = NavigationEasing),
            )

            displayedState = targetState
            displayedDepth = nextDepth
            outgoing = false
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 120, easing = NavigationEasing),
            )
        } catch (cancelled: CancellationException) {
            outgoing = false
            progress.snapTo(1f)
            throw cancelled
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    val travel = size.width * if (outgoing) 0.012f else 0.022f
                    translationX = if (outgoing) {
                        -direction * travel * (1f - p)
                    } else {
                        direction * travel * (1f - p)
                    }
                    alpha = if (outgoing) {
                        0.86f + 0.14f * p
                    } else {
                        0.78f + 0.22f * p
                    }
                },
        ) {
            stateHolder.SaveableStateProvider(displayedState) {
                content(displayedState)
            }
        }
    }
}
