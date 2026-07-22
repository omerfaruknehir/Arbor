package app.arbor.chat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One physical drawer offset shared by touch, fling, buttons, scrim and Back. */
@Stable
internal class InteractiveDrawerState(private val scope: CoroutineScope) {
    private var widthPx = 1f
    private var animationJob: Job? = null
    private var animationRunning by mutableStateOf(false)

    var offsetPx by mutableFloatStateOf(0f)
        private set
    val fraction: Float get() = DrawerPhysics.fraction(offsetPx, widthPx)
    val isVisible: Boolean get() = offsetPx > 0.5f
    val isClosed: Boolean get() = offsetPx <= 0.5f && !animationRunning

    fun updateWidth(value: Float) {
        val wasOpen = fraction > .99f
        widthPx = value.coerceAtLeast(1f)
        if (wasOpen) offsetPx = widthPx
        else if (offsetPx > widthPx) offsetPx = widthPx
    }

    fun stop() {
        animationJob?.cancel()
        animationJob = null
        animationRunning = false
    }

    fun dragTo(startOffsetPx: Float, accumulatedDragPx: Float) {
        offsetPx = DrawerPhysics.dragOffset(startOffsetPx, accumulatedDragPx, widthPx)
    }

    fun settle(velocityPxPerSecond: Float, velocityThresholdPxPerSecond: Float) {
        val target = DrawerPhysics.settleTarget(
            offsetPx = offsetPx,
            drawerWidthPx = widthPx,
            velocityPxPerSecond = velocityPxPerSecond,
            velocityThresholdPxPerSecond = velocityThresholdPxPerSecond,
        )
        animateTo(target)
    }

    fun open() = animateTo(DrawerAnchor.OPEN)
    fun close() = animateTo(DrawerAnchor.CLOSED)

    private fun animateTo(anchor: DrawerAnchor) {
        stop()
        animationRunning = true
        animationJob = scope.launch {
            try {
                Animatable(offsetPx).animateTo(
                    targetValue = if (anchor == DrawerAnchor.OPEN) widthPx else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) { offsetPx = value }
            } finally {
                animationRunning = false
            }
        }
    }
}

@Composable
internal fun rememberInteractiveDrawerState(): InteractiveDrawerState {
    val scope = rememberCoroutineScope()
    return remember(scope) { InteractiveDrawerState(scope) }
}

@Composable
internal fun InteractiveNavigationDrawer(
    state: InteractiveDrawerState,
    modifier: Modifier = Modifier,
    onGenuinelyOpening: () -> Unit = {},
    drawerContent: @Composable (Modifier) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val activationPx = with(density) { 6.dp.toPx() }
    val velocityThresholdPx = with(density) { 850.dp.toPx() }

    BoxWithConstraints(modifier) {
        val drawerWidth = minOf(310.dp, maxWidth * .90f)
        val drawerWidthPx = with(density) { drawerWidth.toPx() }
        LaunchedEffect(drawerWidthPx) { state.updateWidth(drawerWidthPx) }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(state, drawerWidthPx, activationPx, velocityThresholdPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startOffset = state.offsetPx
                        val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                        var totalX = 0f
                        var totalY = 0f
                        var tracking = false
                        var openingNotified = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val delta = change.position - change.previousPosition
                            totalX += delta.x
                            totalY += delta.y
                            velocity.addPosition(change.uptimeMillis, change.position)

                            if (!tracking) {
                                val intent = if (startOffset <= .5f && totalX <= -activationPx) {
                                    DrawerGestureIntent.REJECTED
                                } else DrawerPhysics.gestureIntent(totalX, totalY, activationPx)
                                when (intent) {
                                    DrawerGestureIntent.TRACK_DRAWER -> {
                                        state.stop()
                                        tracking = true
                                    }
                                    DrawerGestureIntent.PASS_TO_CONTENT, DrawerGestureIntent.REJECTED -> break
                                    DrawerGestureIntent.UNDECIDED -> Unit
                                }
                            }
                            if (tracking) {
                                if (!openingNotified && startOffset <= .5f && totalX > 0f) {
                                    openingNotified = true
                                    focusManager.clearFocus()
                                    onGenuinelyOpening()
                                }
                                change.consume()
                                state.dragTo(startOffset, totalX)
                            }
                            if (!change.pressed) {
                                if (tracking) state.settle(velocity.calculateVelocity().x, velocityThresholdPx)
                                break
                            }
                        }
                    }
                },
        ) {
            // Lightweight layers read the one drag offset. Chat list state is never touched.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = state.offsetPx * .06f },
            ) { content() }

            if (state.isVisible) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = state.fraction * .38f }
                        .background(Color.Black)
                        .clickable(onClick = state::close),
                )
            }

            Box(
                Modifier
                    .width(drawerWidth)
                    .fillMaxSize()
                    .graphicsLayer { translationX = -drawerWidthPx + state.offsetPx },
            ) {
                drawerContent(Modifier.width(drawerWidth).fillMaxSize())
            }
        }
    }
}
