package app.arbor.chat.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

private val NavigationEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Navigation host which keeps exactly one destination composed.
 *
 * The previous implementation composed both complete screens during every
 * transition and predictive-back gesture. On chat/settings screens that meant
 * two lazy lists, two blur layers, and two Markdown trees competing for the UI
 * thread. It also disposed and recreated the active screen when the gesture
 * branch changed, which is why scroll and expansion state visibly snapped.
 *
 * This host animates only the active screen's render layer. Destination state is
 * retained by [rememberSaveableStateHolder], while no second screen tree is
 * measured or drawn during the animation.
 */
@Composable
internal fun <T : Any> PredictiveNavigationHost(
    targetState: T,
    backTarget: T?,
    onBack: (T) -> Unit,
    depth: (T) -> Int,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = backTarget != null,
    label: String = "PredictiveNavigation",
    content: @Composable (T) -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val animationLabel = label
    val saveableStateHolder = rememberSaveableStateHolder()
    val predictiveShape = remember { RoundedCornerShape(28.dp) }
    val entryProgress = remember { Animatable(1f) }
    val backProgress = remember { Animatable(0f) }

    var displayedState by remember { mutableStateOf(targetState) }
    var displayedDepth by remember { mutableIntStateOf(depth(targetState)) }
    var entryDirection by remember { mutableFloatStateOf(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var committingPredictiveBack by remember { mutableStateOf(false) }

    val latestBackTarget by rememberUpdatedState(backTarget)
    val latestOnBack by rememberUpdatedState(onBack)

    PredictiveBackHandler(enabled = backEnabled && backTarget != null) { events ->
        val destination = latestBackTarget ?: return@PredictiveBackHandler
        backProgress.snapTo(0f)
        try {
            events.collect { event ->
                swipeEdge = event.swipeEdge
                backProgress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            backProgress.snapTo(1f)
            committingPredictiveBack = true
            latestOnBack(destination)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                backProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 150, easing = NavigationEasing),
                )
            }
            throw cancelled
        }
    }

    LaunchedEffect(targetState) {
        if (displayedState == targetState) return@LaunchedEffect

        val nextDepth = depth(targetState)
        entryDirection = if (nextDepth >= displayedDepth) 1f else -1f
        displayedDepth = nextDepth

        if (committingPredictiveBack) {
            displayedState = targetState
            entryProgress.snapTo(1f)
            backProgress.snapTo(0f)
            committingPredictiveBack = false
        } else {
            // Snap before replacing content so the first frame of the new screen
            // starts at the intended offset instead of flashing at rest.
            entryProgress.snapTo(0f)
            displayedState = targetState
            entryProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 150, easing = NavigationEasing),
            )
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
                    val entry = entryProgress.value.coerceIn(0f, 1f)
                    val predictive = backProgress.value.coerceIn(0f, 1f)
                    val edgeDirection = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f

                    translationX = size.width * (
                        entryDirection * 0.045f * (1f - entry) +
                            edgeDirection * 0.24f * predictive
                        )
                    val scale = (0.985f + 0.015f * entry) * (1f - 0.02f * predictive)
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.92f + 0.08f * entry
                    shadowElevation = 5.dp.toPx() * predictive
                    shape = predictiveShape
                    clip = predictive > 0.001f
                },
        ) {
            saveableStateHolder.SaveableStateProvider(displayedState) {
                content(displayedState)
            }
        }
    }
}
