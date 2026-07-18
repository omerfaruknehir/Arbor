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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

private val NavigationEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Animated screen host with direct predictive-back progress support.
 *
 * During a system back swipe, the destination is composed below the current page
 * and revealed continuously as the gesture advances. At the activity root no
 * callback is registered, preserving Android/Samsung's system back-to-home preview.
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
    var gestureSource by remember { mutableStateOf<T?>(null) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var suppressNextTransition by remember { mutableStateOf(false) }
    val latestTargetState by rememberUpdatedState(targetState)
    val latestBackTarget by rememberUpdatedState(backTarget)
    val latestOnBack by rememberUpdatedState(onBack)

    PredictiveBackHandler(enabled = backEnabled && backTarget != null) { events ->
        val destination = latestBackTarget ?: return@PredictiveBackHandler
        gestureSource = latestTargetState
        previewTarget = destination
        gestureProgress.snapTo(0f)

        try {
            events.collect { event ->
                swipeEdge = event.swipeEdge
                gestureProgress.snapTo(event.progress.coerceIn(0f, 1f))
            }

            gestureProgress.snapTo(1f)
            suppressNextTransition = true
            latestOnBack(destination)
            // Keep the completed gesture frame on screen until the destination
            // state has reached this composition, avoiding a one-frame flash.
            withFrameNanos { }
            previewTarget = null
            gestureSource = null
            gestureProgress.snapTo(0f)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                gestureProgress.animateTo(0f, tween(durationMillis = 180, easing = NavigationEasing))
                previewTarget = null
                gestureSource = null
            }
            throw cancelled
        }
    }

    LaunchedEffect(targetState) {
        if (suppressNextTransition) {
            withFrameNanos { }
            suppressNextTransition = false
        }
    }

    val destination = previewTarget
    val source = gestureSource
    if (destination != null && source != null) {
        PredictiveBackPreview(
            source = source,
            destination = destination,
            progress = gestureProgress.value,
            swipeEdge = swipeEdge,
            modifier = modifier,
            content = content,
        )
    } else {
        AnimatedContent(
            targetState = targetState,
            modifier = modifier,
            transitionSpec = {
                if (suppressNextTransition) {
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
            Box(Modifier.fillMaxSize().graphicsLayer()) {
                content(state)
            }
        }
    }
}

@Composable
private fun <T : Any> PredictiveBackPreview(
    source: T,
    destination: T,
    progress: Float,
    swipeEdge: Int,
    modifier: Modifier,
    content: @Composable (T) -> Unit,
) {
    val p = progress.coerceIn(0f, 1f)
    val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
    val density = LocalDensity.current
    val maxShadowPx = with(density) { 5.dp.toPx() }
    val corner = 28.dp * p

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = -direction * widthPx * 0.045f * (1f - p)
                    scaleX = 0.98f + 0.02f * p
                    scaleY = 0.98f + 0.02f * p
                },
        ) {
            content(destination)
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = direction * widthPx * 0.30f * p
                    scaleX = 1f - 0.025f * p
                    scaleY = 1f - 0.025f * p
                    shadowElevation = maxShadowPx * p
                    shape = RoundedCornerShape(corner)
                    clip = p > 0.001f
                },
        ) {
            content(source)
        }
    }
}
