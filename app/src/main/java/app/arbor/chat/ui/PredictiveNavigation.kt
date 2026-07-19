package app.arbor.chat.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
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

internal enum class PredictiveCancellationResolution {
    ROLLBACK,
    FINISH_COMMIT,
}

/** A cancellation after the progress flow completed is a committed back, not a rollback. */
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
}

private class NavigationSlot<T : Any>(
    val id: Long,
    val state: T,
    initialRole: NavigationSlotRole,
) {
    var role by mutableStateOf(initialRole)
}

/**
 * Navigation host with one stable composition slot per visible page.
 *
 * The old implementation rendered the predictive destination in one branch and
 * then recreated the same destination inside AnimatedContent on commit. During
 * that hand-off the outgoing layer first lost its transform (visibly centering
 * again), and Compose could briefly register two SaveableStateProvider instances
 * with the same key. A slot is now created once, animated in place, and promoted
 * to CURRENT on commit. No destination key is ever composed twice.
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
    // label remains part of the API and is useful in Layout Inspector traces.
    @Suppress("UNUSED_VARIABLE")
    val inspectorLabel = label

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
    val latestContent by rememberUpdatedState(content)
    val stateHolder = rememberSaveableStateHolder()

    fun currentSlot(): NavigationSlot<T> =
        slots.firstOrNull { it.role == NavigationSlotRole.CURRENT }
            ?: slots.first()

    fun findState(state: T): NavigationSlot<T>? = slots.firstOrNull { it.state == state }

    fun newSlot(state: T, role: NavigationSlotRole): NavigationSlot<T> =
        NavigationSlot(nextSlotId++, state, role)

    fun settleOn(slot: NavigationSlot<T>) {
        Snapshot.withMutableSnapshot {
            slots.toList().filter { it !== slot }.forEach(slots::remove)
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
                .forEach(slots::remove)
            slots.firstOrNull { it.role == NavigationSlotRole.SOURCE }?.role =
                NavigationSlotRole.CURRENT
            mode = NavigationTransitionMode.IDLE
        }
    }

    // Non-predictive navigation uses the same two-slot renderer. If rapid input
    // cancels an animation (especially at 10x duration scale), settle directly on
    // the newest requested state instead of retaining half-finished pages.
    LaunchedEffect(targetState) {
        if (mode == NavigationTransitionMode.PREDICTIVE) return@LaunchedEffect
        val source = currentSlot()
        if (source.state == targetState) return@LaunchedEffect

        try {
            // Clean up any interrupted ordinary transition before starting another.
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
            progress.animateTo(1f, tween(170, easing = NavigationEasing))
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

    // Keep the handler enabled during its own predictive transition. Disabling it
    // when mode becomes PREDICTIVE cancels the callback and was one cause of the
    // apparent rollback-on-commit behavior.
    PredictiveBackHandler(
        enabled = backEnabled && backTarget != null && mode != NavigationTransitionMode.ORDINARY,
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

            // Flow completion means commit. From this point onward, cancellation
            // must finish the back operation rather than animate back to zero.
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
                        // Route changes can dispose/cancel PredictiveBackHandler as
                        // soon as back is committed. Never run the rollback branch.
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
        val predictiveDirection = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f

        slots.forEach { slot ->
            key(slot.id) {
                val isSource = slot.role == NavigationSlotRole.SOURCE
                val isDestination = slot.role == NavigationSlotRole.DESTINATION
                val z = when (mode) {
                    NavigationTransitionMode.PREDICTIVE -> if (isSource) 1f else 0f
                    NavigationTransitionMode.ORDINARY -> if (isDestination) 1f else 0f
                    NavigationTransitionMode.IDLE -> 0f
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(z)
                        .graphicsLayer {
                            val p = progress.value.coerceIn(0f, 1f)
                            when (mode) {
                                NavigationTransitionMode.PREDICTIVE -> when {
                                    isSource -> {
                                        translationX = predictiveDirection * widthPx * 0.30f * p
                                        scaleX = 1f - 0.025f * p
                                        scaleY = 1f - 0.025f * p
                                        alpha = predictiveBackOutgoingAlpha(p)
                                        shadowElevation = maxShadowPx * p
                                        shape = fixedCornerShape
                                        clip = p > 0.001f
                                    }

                                    isDestination -> {
                                        translationX = -predictiveDirection * widthPx * 0.045f * (1f - p)
                                        scaleX = 0.98f + 0.02f * p
                                        scaleY = 0.98f + 0.02f * p
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
                        latestContent(slot.state)
                    }
                }
            }
        }
    }
}
