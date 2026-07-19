package app.arbor.chat.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val NavigationEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private const val CommitFadeStart = 0.62f

/**
 * The system may complete a predictive gesture before its last emitted progress
 * reaches 1. Finishing the remaining render-layer distance prevents the source
 * page from disappearing in a single frame.
 */
internal fun predictiveBackCompletionDurationMillis(progress: Float): Int {
    val remaining = 1f - progress.coerceIn(0f, 1f)
    return (80f + 70f * remaining).roundToInt().coerceIn(80, 150)
}

/** Fade only near the committed endpoint, leaving ordinary gesture tracking crisp. */
internal fun predictiveBackOutgoingAlpha(progress: Float): Float {
    val fadeProgress = ((progress.coerceIn(0f, 1f) - CommitFadeStart) /
        (1f - CommitFadeStart)).coerceIn(0f, 1f)
    return 1f - NavigationEasing.transform(fadeProgress)
}

/**
 * Screen host which keeps the active screen in the same composition for the whole
 * predictive-back gesture.
 *
 * Gesture progress is read only by graphics layers, so a back swipe invalidates
 * render properties instead of recomposing the complete source and destination
 * screens on every frame. Each destination also owns a saveable state bucket, so
 * list and scroll positions survive both a cancelled gesture and a committed back.
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
    val gestureProgress = remember { Animatable(0f) }
    var previewTarget by remember { mutableStateOf<T?>(null) }
    var displayedState by remember { mutableStateOf(targetState) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var suppressTransitionFor by remember { mutableStateOf<T?>(null) }

    val latestTargetState by rememberUpdatedState(targetState)
    val latestBackTarget by rememberUpdatedState(backTarget)
    val latestOnBack by rememberUpdatedState(onBack)
    val latestContent = rememberUpdatedState(content)
    val stateHolder = rememberSaveableStateHolder()

    val renderState: @Composable (T) -> Unit = { state ->
        stateHolder.SaveableStateProvider(state) {
            latestContent.value(state)
        }
    }

    // Ordinary navigation still animates. During predictive back, the visible
    // source remains frozen at displayedState until the gesture either cancels or
    // commits, so external state changes cannot replace it halfway through a swipe.
    LaunchedEffect(targetState, previewTarget) {
        if (previewTarget == null && displayedState != targetState) {
            displayedState = targetState
        }
    }

    PredictiveBackHandler(enabled = backEnabled && backTarget != null) { events ->
        val destination = latestBackTarget ?: return@PredictiveBackHandler
        previewTarget = destination
        gestureProgress.snapTo(0f)

        try {
            events.collect { event ->
                swipeEdge = event.swipeEdge
                gestureProgress.snapTo(event.progress.coerceIn(0f, 1f))
            }

            // A committed Flow commonly ends before the last emitted value is
            // exactly 1f. Complete only the cheap layer animation first; the
            // outgoing page fades fully before the state swap, so there is no
            // visible hard cut even with Animator duration scale set to 5x/10x.
            val completionDuration = predictiveBackCompletionDurationMillis(gestureProgress.value)
            gestureProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = completionDuration,
                    easing = NavigationEasing,
                ),
            )
            suppressTransitionFor = destination
            latestOnBack(destination)

            // Wait until the application state has actually accepted the back
            // destination, then replace preview + source in one snapshot. This
            // avoids the slow post-gesture AnimatedContent pass and any overlap.
            snapshotFlow { latestTargetState }.first { it == destination }
            withFrameNanos { }
            Snapshot.withMutableSnapshot {
                displayedState = destination
                previewTarget = null
            }
            gestureProgress.snapTo(0f)

            // Keep suppression alive for the first destination composition. The
            // transition spec has already been selected by the time this clears.
            withFrameNanos { }
            suppressTransitionFor = null
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                gestureProgress.animateTo(0f, tween(durationMillis = 160, easing = NavigationEasing))
                previewTarget = null
            }
            throw cancelled
        }
    }

    val fixedCornerShape = remember { RoundedCornerShape(28.dp) }
    val density = LocalDensity.current
    val maxShadowPx = with(density) { 5.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val destination = previewTarget
        val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f

        if (destination != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = gestureProgress.value.coerceIn(0f, 1f)
                        translationX = -direction * widthPx * 0.045f * (1f - p)
                        scaleX = 0.98f + 0.02f * p
                        scaleY = 0.98f + 0.02f * p
                    },
            ) {
                renderState(destination)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (previewTarget != null) {
                        val p = gestureProgress.value.coerceIn(0f, 1f)
                        translationX = direction * widthPx * 0.30f * p
                        scaleX = 1f - 0.025f * p
                        scaleY = 1f - 0.025f * p
                        alpha = predictiveBackOutgoingAlpha(p)
                        shadowElevation = maxShadowPx * p
                        shape = fixedCornerShape
                        clip = p > 0.001f
                    } else {
                        translationX = 0f
                        scaleX = 1f
                        scaleY = 1f
                        alpha = 1f
                        shadowElevation = 0f
                        clip = false
                    }
                },
        ) {
            AnimatedContent(
                targetState = displayedState,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (suppressTransitionFor == targetState) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        val forward = depth(targetState) >= depth(initialState)
                        if (forward) {
                            slideInHorizontally(
                                animationSpec = tween(170, easing = NavigationEasing),
                                initialOffsetX = { it / 8 },
                            ) togetherWith slideOutHorizontally(
                                animationSpec = tween(150, easing = NavigationEasing),
                                targetOffsetX = { -it / 18 },
                            )
                        } else {
                            slideInHorizontally(
                                animationSpec = tween(170, easing = NavigationEasing),
                                initialOffsetX = { -it / 18 },
                            ) togetherWith slideOutHorizontally(
                                animationSpec = tween(150, easing = NavigationEasing),
                                targetOffsetX = { it / 8 },
                            )
                        }
                    }
                },
                contentKey = { it },
                label = label,
            ) { state ->
                Box(Modifier.fillMaxSize()) {
                    renderState(state)
                }
            }
        }
    }
}
