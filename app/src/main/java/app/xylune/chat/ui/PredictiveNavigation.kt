package app.xylune.chat.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val NavigationEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private const val CommitFadeStart = 0.62f

internal fun appBackHandlerEnabled(ownerEnabled: Boolean, imeVisible: Boolean): Boolean =
    ownerEnabled && !imeVisible

internal fun predictiveBackCompletionDurationMillis(progress: Float): Int {
    val remaining = 1f - progress.coerceIn(0f, 1f)
    return (80f + 70f * remaining).roundToInt().coerceIn(80, 150)
}

internal fun predictiveBackOutgoingAlpha(progress: Float): Float {
    val fadeProgress = ((progress.coerceIn(0f, 1f) - CommitFadeStart) /
        (1f - CommitFadeStart)).coerceIn(0f, 1f)
    return 1f - NavigationEasing.transform(fadeProgress)
}

internal enum class PredictiveCancellationResolution {
    ROLLBACK,
    FINISH_COMMIT,
}

internal fun predictiveCancellationResolution(commitStarted: Boolean): PredictiveCancellationResolution =
    if (commitStarted) PredictiveCancellationResolution.FINISH_COMMIT
    else PredictiveCancellationResolution.ROLLBACK

private enum class NavigationTransitionMode {
    IDLE,
    ORDINARY,
    PREDICTIVE,
}

private enum class NavigationSlotRole {
    CURRENT,
    SOURCE,
    DESTINATION,
    PARKED,
}

private class NavigationSlot<T : Any>(
    val id: Long,
    val state: T,
    initialRole: NavigationSlotRole,
) {
    var role by mutableStateOf(initialRole)
}

/**
 * Navigation host with stable composition slots. Pages selected by [keepAlive]
 * remain composed but undrawn, so Back can reveal the exact existing Chat tree.
 */
@Composable
internal fun <T : Any> PredictiveNavigationHost(
    targetState: T,
    backTarget: T?,
    onBack: (T) -> Unit,
    depth: (T) -> Int,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = backTarget != null,
    keepAlive: (T) -> Boolean = { false },
    label: String = "PredictiveNavigation",
    content: @Composable (T) -> Unit,
) {
    @Suppress("UNUSED_VARIABLE")
    val inspectorLabel = label
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    val progress = remember { Animatable(0f) }
    val slots = remember {
        mutableStateListOf(
            NavigationSlot(
                id = 0L,
                state = targetState,
                initialRole = NavigationSlotRole.CURRENT,
            ),
        )
    }
    var nextSlotId by remember { mutableLongStateOf(1L) }
    var mode by remember { mutableStateOf(NavigationTransitionMode.IDLE) }
    var transitionForward by remember { mutableStateOf(true) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    val latestTargetState by rememberUpdatedState(targetState)
    val latestBackTarget by rememberUpdatedState(backTarget)
    val latestOnBack by rememberUpdatedState(onBack)
    val latestKeepAlive by rememberUpdatedState(keepAlive)
    val latestContent by rememberUpdatedState(content)
    val stateHolder = rememberSaveableStateHolder()

    fun currentSlot(): NavigationSlot<T> =
        slots.firstOrNull { it.role == NavigationSlotRole.CURRENT } ?: slots.first()

    fun findState(state: T): NavigationSlot<T>? = slots.firstOrNull { it.state == state }

    fun newSlot(state: T, role: NavigationSlotRole): NavigationSlot<T> =
        NavigationSlot(nextSlotId++, state, role)

    fun retire(slot: NavigationSlot<T>) {
        if (latestKeepAlive(slot.state)) slot.role = NavigationSlotRole.PARKED
        else slots.remove(slot)
    }

    fun settleOn(slot: NavigationSlot<T>) {
        Snapshot.withMutableSnapshot {
            slots.toList().filter { it !== slot }.forEach(::retire)
            slot.role = NavigationSlotRole.CURRENT
            mode = NavigationTransitionMode.IDLE
        }
    }

    fun settleImmediatelyOn(state: T) {
        val slot = findState(state) ?: newSlot(state, NavigationSlotRole.CURRENT)
        if (slot !in slots) slots.add(slot)
        settleOn(slot)
    }

    fun removeDestinationAndRestoreSource() {
        Snapshot.withMutableSnapshot {
            slots.toList()
                .filter { it.role == NavigationSlotRole.DESTINATION }
                .forEach(::retire)
            slots.firstOrNull { it.role == NavigationSlotRole.SOURCE }?.role =
                NavigationSlotRole.CURRENT
            mode = NavigationTransitionMode.IDLE
        }
    }

    LaunchedEffect(targetState) {
        if (mode == NavigationTransitionMode.PREDICTIVE) return@LaunchedEffect
        val source = currentSlot()
        if (source.state == targetState) return@LaunchedEffect

        try {
            if (mode == NavigationTransitionMode.ORDINARY) {
                settleImmediatelyOn(latestTargetState)
            }

            val stableSource = currentSlot()
            val destination = findState(targetState)
                ?: newSlot(targetState, NavigationSlotRole.DESTINATION).also(slots::add)

            Snapshot.withMutableSnapshot {
                stableSource.role = NavigationSlotRole.SOURCE
                destination.role = NavigationSlotRole.DESTINATION
                transitionForward = depth(destination.state) >= depth(stableSource.state)
                mode = NavigationTransitionMode.ORDINARY
            }
            progress.snapTo(0f)
            progress.animateTo(1f, tween(120, easing = NavigationEasing))
            settleOn(destination)
            progress.snapTo(0f)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                settleImmediatelyOn(latestTargetState)
                progress.snapTo(0f)
            }
            throw cancelled
        }
    }

    PredictiveBackHandler(
        enabled = appBackHandlerEnabled(
            ownerEnabled = backEnabled && backTarget != null && mode != NavigationTransitionMode.ORDINARY,
            imeVisible = imeVisible,
        ),
    ) { events ->
        val destinationState = latestBackTarget ?: return@PredictiveBackHandler
        val source = currentSlot()
        if (source.state == destinationState) return@PredictiveBackHandler

        val destination = findState(destinationState)
            ?: newSlot(destinationState, NavigationSlotRole.DESTINATION).also(slots::add)

        Snapshot.withMutableSnapshot {
            source.role = NavigationSlotRole.SOURCE
            destination.role = NavigationSlotRole.DESTINATION
            transitionForward = false
            mode = NavigationTransitionMode.PREDICTIVE
        }
        progress.snapTo(0f)

        var commitStarted = false
        var backDispatched = false
        try {
            events.collect { event ->
                swipeEdge = event.swipeEdge
                progress.snapTo(event.progress.coerceIn(0f, 1f))
            }

            commitStarted = true
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = predictiveBackCompletionDurationMillis(progress.value),
                    easing = NavigationEasing,
                ),
            )
            latestOnBack(destinationState)
            backDispatched = true
            settleOn(destination)
            progress.snapTo(0f)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                when (predictiveCancellationResolution(commitStarted)) {
                    PredictiveCancellationResolution.FINISH_COMMIT -> {
                        progress.snapTo(1f)
                        if (!backDispatched) latestOnBack(destinationState)
                        settleOn(destination)
                        progress.snapTo(0f)
                    }
                    PredictiveCancellationResolution.ROLLBACK -> {
                        progress.animateTo(0f, tween(160, easing = NavigationEasing))
                        removeDestinationAndRestoreSource()
                        progress.snapTo(0f)
                    }
                }
            }
            throw cancelled
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val predictiveDirection = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f

        slots.forEach { slot ->
            key(slot.id) {
                val isSource = slot.role == NavigationSlotRole.SOURCE
                val isDestination = slot.role == NavigationSlotRole.DESTINATION
                val isParked = slot.role == NavigationSlotRole.PARKED
                val z = when {
                    isParked -> -1f
                    mode == NavigationTransitionMode.PREDICTIVE && isSource -> 1f
                    mode == NavigationTransitionMode.ORDINARY && isDestination -> 1f
                    else -> 0f
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(z)
                        .graphicsLayer {
                            clip = true
                            if (isParked) {
                                alpha = 0f
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            }
                            val p = progress.value.coerceIn(0f, 1f)
                            when (mode) {
                                NavigationTransitionMode.PREDICTIVE -> when {
                                    isSource -> {
                                        translationX = predictiveDirection * widthPx * 0.26f * p
                                        alpha = predictiveBackOutgoingAlpha(p)
                                        compositingStrategy = CompositingStrategy.ModulateAlpha
                                    }
                                    isDestination -> {
                                        translationX = -predictiveDirection * widthPx * 0.04f * (1f - p)
                                    }
                                }
                                NavigationTransitionMode.ORDINARY -> {
                                    if (transitionForward) {
                                        when {
                                            isSource -> translationX = -widthPx / 18f * p
                                            isDestination -> translationX = widthPx / 8f * (1f - p)
                                        }
                                    } else {
                                        when {
                                            isSource -> translationX = widthPx / 8f * p
                                            isDestination -> translationX = -widthPx / 18f * (1f - p)
                                        }
                                    }
                                }
                                NavigationTransitionMode.IDLE -> Unit
                            }
                        },
                ) {
                    stateHolder.SaveableStateProvider(slot.state) {
                        // Transition progress and mode stay in parent RenderNodes.
                        // Injecting mode through a CompositionLocal invalidated every
                        // kept-alive page at transition start and finish.
                        latestContent(slot.state)
                    }
                }
            }
        }
    }
}
