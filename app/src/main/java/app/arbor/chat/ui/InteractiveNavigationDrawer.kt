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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
    private val offsetState = mutableFloatStateOf(0f)
    private var visibleState by mutableStateOf(false)
    private var predictiveBackActive by mutableStateOf(false)
    private var predictiveBackStartOffsetPx = 0f

    /**
     * High-frequency drag position. It is intentionally read only from pointer
     * handlers and graphicsLayer blocks, never from ArborApp composition.
     */
    val offsetPx: Float get() = offsetState.floatValue
    val fraction: Float get() = DrawerPhysics.fraction(offsetPx, widthPx)

    /** Changes only when crossing the fully-closed boundary. */
    val isVisible: Boolean get() = visibleState
    val claimsBack: Boolean get() = visibleState || predictiveBackActive
    val isClosed: Boolean get() = !visibleState && !animationRunning && !predictiveBackActive

    private fun updateOffset(value: Float) {
        val next = value.coerceIn(0f, widthPx)
        if (offsetState.floatValue != next) offsetState.floatValue = next
        val nextVisible = next > 0.01f
        if (visibleState != nextVisible) visibleState = nextVisible
    }

    fun updateWidth(value: Float) {
        val wasOpen = fraction > .99f
        widthPx = value.coerceAtLeast(1f)
        if (wasOpen) updateOffset(widthPx)
        else if (offsetPx > widthPx) updateOffset(widthPx)
    }

    fun stop() {
        animationJob?.cancel()
        animationJob = null
        animationRunning = false
    }

    fun dragTo(startOffsetPx: Float, accumulatedDragPx: Float) {
        updateOffset(DrawerPhysics.dragOffset(startOffsetPx, accumulatedDragPx, widthPx))
    }

    fun settle(velocityPxPerSecond: Float, velocityThresholdPxPerSecond: Float) {
        val target = DrawerPhysics.settleTarget(
            offsetPx = offsetPx,
            drawerWidthPx = widthPx,
            velocityPxPerSecond = velocityPxPerSecond,
            velocityThresholdPxPerSecond = velocityThresholdPxPerSecond,
        )
        animateTo(
            anchor = target,
            initialVelocityPxPerSecond = DrawerPhysics.carriedSettleVelocity(
                velocityPxPerSecond = velocityPxPerSecond,
                drawerWidthPx = widthPx,
            ),
        )
    }

    fun open() = animateTo(DrawerAnchor.OPEN)
    fun close() = animateTo(DrawerAnchor.CLOSED)

    fun beginPredictiveBack() {
        stop()
        predictiveBackStartOffsetPx = offsetPx
        predictiveBackActive = true
    }

    fun updatePredictiveBack(progress: Float) {
        if (!predictiveBackActive) return
        updateOffset(DrawerPhysics.predictiveBackOffset(predictiveBackStartOffsetPx, progress))
    }

    fun commitPredictiveBack() {
        updateOffset(0f)
        predictiveBackActive = false
        predictiveBackStartOffsetPx = 0f
    }

    fun cancelPredictiveBack() {
        val restoreOffset = predictiveBackStartOffsetPx.coerceIn(0f, widthPx)
        predictiveBackActive = false
        predictiveBackStartOffsetPx = 0f
        animateToOffset(restoreOffset)
    }

    private fun animateTo(
        anchor: DrawerAnchor,
        initialVelocityPxPerSecond: Float = 0f,
    ) {
        stop()
        animationRunning = true
        animateToOffset(
            targetOffsetPx = if (anchor == DrawerAnchor.OPEN) widthPx else 0f,
            initialVelocityPxPerSecond = initialVelocityPxPerSecond,
        )
    }

    private fun animateToOffset(
        targetOffsetPx: Float,
        initialVelocityPxPerSecond: Float = 0f,
    ) {
        stop()
        animationRunning = true
        animationJob = scope.launch {
            try {
                Animatable(offsetPx).apply { updateBounds(0f, widthPx) }.animateTo(
                    targetValue = targetOffsetPx.coerceIn(0f, widthPx),
                    animationSpec = spring(
                        dampingRatio = 0.84f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    initialVelocity = initialVelocityPxPerSecond,
                ) { updateOffset(value) }
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
    gesturesEnabled: Boolean = true,
    onGenuinelyOpening: () -> Unit = {},
    drawerContent: @Composable (Modifier) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val haptics = rememberArborHaptics()
    val activationPx = with(density) { 6.dp.toPx() }
    val velocityThresholdPx = with(density) { 850.dp.toPx() }
    val horizontalPriority = remember { HorizontalGesturePriorityRegistry() }
    var drawerOriginInRoot by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier) {
        val drawerWidth = minOf(310.dp, maxWidth * .90f)
        val drawerWidthPx = with(density) { drawerWidth.toPx() }
        LaunchedEffect(drawerWidthPx) { state.updateWidth(drawerWidthPx) }

        val gestureModifier = if (gesturesEnabled) {
            Modifier.pointerInput(
                state,
                drawerWidthPx,
                activationPx,
                velocityThresholdPx,
                drawerOriginInRoot,
            ) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startOffset = state.offsetPx
                    if (
                        startOffset <= .5f &&
                        horizontalPriority.owns(down.position + drawerOriginInRoot)
                    ) {
                        return@awaitEachGesture
                    }
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
                                    haptics.gestureStart()
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
                            if (tracking) {
                                state.settle(velocity.calculateVelocity().x, velocityThresholdPx)
                                haptics.snap()
                            }
                            break
                        }
                    }
                }
            }
        } else Modifier

        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    drawerOriginInRoot = coordinates.boundsInRoot().topLeft
                }
                .then(gestureModifier),
        ) {
            // Lightweight layers read the one drag offset. Chat list state is never touched.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = state.offsetPx * .06f },
            ) {
                CompositionLocalProvider(
                    LocalHorizontalGesturePriorityRegistry provides horizontalPriority,
                ) {
                    content()
                }
            }

            if (state.isVisible) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = state.fraction * .38f }
                        .background(Color.Black)
                        .clickable {
                            haptics.selection()
                            state.close()
                        },
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
