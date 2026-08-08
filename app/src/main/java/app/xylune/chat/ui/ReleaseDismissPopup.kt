package app.xylune.chat.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DropdownMenu as MaterialDropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Handles completed Back gestures for popup/dialog windows.
 *
 * If the IME is visible when Back begins, the whole gesture belongs to the IME:
 * Xylune hides the keyboard only after the gesture completes and leaves the
 * surrounding modal surface present. A cancelled gesture changes nothing.
 */
@Composable
internal fun XylunePopupBackHandler(
    onDismissRequest: () -> Unit,
    onProgress: (Float) -> Unit = {},
) {
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val imeInsets = WindowInsets.ime
    PredictiveBackHandler(enabled = true) { events ->
        val imeVisibleAtGestureStart = imeInsets.getBottom(density) > 0
        if (imeVisibleAtGestureStart) {
            try {
                events.collect { }
                keyboard?.hide()
                focusManager.clearFocus(force = true)
                onProgress(0f)
            } catch (cancelled: CancellationException) {
                onProgress(0f)
                throw cancelled
            }
            return@PredictiveBackHandler
        }

        try {
            events.collect { event ->
                onProgress(event.progress.coerceIn(0f, 1f))
            }
            onProgress(1f)
            onDismissRequest()
        } catch (cancelled: CancellationException) {
            onProgress(0f)
            throw cancelled
        }
    }
}

/**
 * Tap-away detector for a full-window modal surface.
 *
 * Dismissal happens only after finger release, only for a tap that started and
 * stayed outside the content, and never for a gesture that began in either
 * Android system Back edge. This prevents the edge-down event of predictive
 * Back from being misclassified as a click outside.
 */
private fun Modifier.dismissOnOutsideRelease(
    contentBounds: IntRect,
    leftBackEdgePx: Int,
    rightBackEdgePx: Int,
    onDismissRequest: () -> Unit,
): Modifier = pointerInput(
    contentBounds,
    leftBackEdgePx,
    rightBackEdgePx,
    onDismissRequest,
) {
    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
        )
        val startedInBackEdge = down.position.x <= leftBackEdgePx ||
            down.position.x >= size.width - rightBackEdgePx
        val boundsReady = contentBounds.width > 0 && contentBounds.height > 0
        val startedInside = boundsReady &&
            down.position.x >= contentBounds.left &&
            down.position.x <= contentBounds.right &&
            down.position.y >= contentBounds.top &&
            down.position.y <= contentBounds.bottom
        val startX = down.position.x
        val startY = down.position.y
        var maxTravelSquared = 0f

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val tracked = event.changes.firstOrNull { it.id == down.id }
            if (tracked != null) {
                val dx = tracked.position.x - startX
                val dy = tracked.position.y - startY
                maxTravelSquared = max(maxTravelSquared, dx * dx + dy * dy)
            }
            if (event.changes.none { it.pressed }) break
        }

        val slop = viewConfiguration.touchSlop
        val wasTap = maxTravelSquared <= slop * slop
        if (boundsReady && !startedInBackEdge && !startedInside && wasTap) {
            onDismissRequest()
        }
    }
}

/**
 * Xylune alert dialog with release-based outside dismissal.
 *
 * BasicAlertDialog is deliberately made full-window so the scrim and the dialog
 * card live in the same dialog window. That gives Xylune the actual pointer-up
 * event instead of relying on Android's native outside-touch callback, which can
 * fire on the first edge contact of a predictive-Back gesture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XyluneAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val minimumBackEdgePx = with(density) { 32.dp.roundToPx() }
    val leftBackEdgePx = max(
        WindowInsets.systemGestures.getLeft(density, layoutDirection),
        minimumBackEdgePx,
    )
    val rightBackEdgePx = max(
        WindowInsets.systemGestures.getRight(density, layoutDirection),
        minimumBackEdgePx,
    )
    var dialogBounds by remember { mutableStateOf(IntRect.Zero) }
    var backProgress by remember { mutableFloatStateOf(0f) }

    BasicAlertDialog(
        onDismissRequest = {},
        modifier = Modifier.fillMaxSize(),
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        XylunePopupBackHandler(
            onDismissRequest = onDismissRequest,
            onProgress = { backProgress = it },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .dismissOnOutsideRelease(
                    contentBounds = dialogBounds,
                    leftBackEdgePx = leftBackEdgePx,
                    rightBackEdgePx = rightBackEdgePx,
                    onDismissRequest = onDismissRequest,
                )
                .padding(horizontal = 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = modifier
                    .widthIn(min = 280.dp, max = 560.dp)
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInRoot()
                        dialogBounds = IntRect(
                            left = bounds.left.roundToInt(),
                            top = bounds.top.roundToInt(),
                            right = bounds.right.roundToInt(),
                            bottom = bounds.bottom.roundToInt(),
                        )
                    }
                    .graphicsLayer {
                        val progress = backProgress.coerceIn(0f, 1f)
                        val scale = 1f - 0.04f * progress
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - 0.14f * progress
                    },
                shape = shape,
                color = containerColor,
                contentColor = textContentColor,
                tonalElevation = tonalElevation,
                shadowElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (icon != null) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                                icon()
                            }
                        }
                    }

                    if (title != null) {
                        CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                            ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                                title()
                            }
                        }
                    }

                    if (text != null) {
                        CompositionLocalProvider(LocalContentColor provides textContentColor) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                text()
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dismissButton != null) {
                            dismissButton()
                            Spacer(Modifier.width(8.dp))
                        }
                        confirmButton()
                    }
                }
            }
        }
    }
}

/**
 * Small anchored menus keep Material's native outside dismissal. Modal dialogs
 * and source previews use the release-based full-window path above; menus remain
 * lightweight and immediately recover the expected tap-away behavior.
 */
@Composable
internal fun XyluneDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnClickOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    MaterialDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = dismissOnClickOutside,
        ),
        content = content,
    )
}
