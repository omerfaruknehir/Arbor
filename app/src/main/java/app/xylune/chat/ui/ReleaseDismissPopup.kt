package app.xylune.chat.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.DropdownMenu as MaterialDropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

@Composable
private fun XylunePopupBackHandler(
    onDismissRequest: () -> Unit,
    onProgress: (Float) -> Unit = {},
) {
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val imeInsets = WindowInsets.ime
    PredictiveBackHandler(enabled = true) { events ->
        // Resolve IME visibility when the gesture starts in the popup/dialog
        // window. If the keyboard is visible, this entire Back gesture belongs
        // to the IME: the surrounding surface must remain open and unchanged.
        val imeVisibleAtGestureStart = imeInsets.getBottom(density) > 0
        if (imeVisibleAtGestureStart) {
            events.collect { }
            keyboard?.hide()
            focusManager.clearFocus(force = true)
            onProgress(0f)
            return@PredictiveBackHandler
        }
        try {
            events.collect { event -> onProgress(event.progress.coerceIn(0f, 1f)) }
            onProgress(1f)
            onDismissRequest()
        } catch (cancelled: CancellationException) {
            onProgress(0f)
            throw cancelled
        }
    }
}

/**
 * Release-based outside dismissal for popup windows.
 *
 * Android can report the initial edge contact of a predictive-Back gesture as
 * an outside touch. Native dismissOnClickOutside therefore closes a popup at
 * finger-down, before Back has even progressed. This layer waits for release
 * and explicitly ignores gestures which began in either system Back edge.
 */
@Composable
internal fun ReleaseDismissOutsideLayer(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    dismissOnOutsideRelease: Boolean = true,
) {
    if (!visible) return

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val minimumBackEdgePx = with(density) { 24.dp.roundToPx() }
    val leftBackEdgePx = maxOf(WindowInsets.systemGestures.getLeft(density, layoutDirection), minimumBackEdgePx)
    val rightBackEdgePx = maxOf(WindowInsets.systemGestures.getRight(density, layoutDirection), minimumBackEdgePx)

    XylunePopupBackHandler(onDismissRequest)
    Popup(
        alignment = Alignment.TopStart,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(
                    onDismissRequest,
                    dismissOnOutsideRelease,
                    leftBackEdgePx,
                    rightBackEdgePx,
                ) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val startedInBackEdge = down.position.x <= leftBackEdgePx ||
                            down.position.x >= size.width - rightBackEdgePx
                        var consumedByChild = down.isConsumed
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            consumedByChild = consumedByChild || event.changes.any { it.isConsumed }
                            if (event.changes.none { it.pressed }) break
                        }
                        if (
                            dismissOnOutsideRelease &&
                            !startedInBackEdge &&
                            !consumedByChild
                        ) {
                            onDismissRequest()
                        }
                    }
                },
        )
    }
}

/**
 * Xylune's keyboard-safe modal dialog.
 *
 * Large/modal dialogs intentionally do not use native outside-touch dismissal:
 * an Android predictive-Back gesture begins at the screen edge, which can be
 * mistaken for an outside click before the Back handler has a chance to keep
 * the dialog open for the IME. Explicit dialog actions and Back remain safe.
 */
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
    var backProgress by remember { mutableFloatStateOf(0f) }
    MaterialAlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            // MaterialAlertDialog composes this slot in the dialog's own window.
            // Registering here makes WindowInsets.ime refer to the same window as
            // the focused text field instead of the activity behind the dialog.
            XylunePopupBackHandler(
                onDismissRequest = onDismissRequest,
                onProgress = { backProgress = it },
            )
            confirmButton()
        },
        modifier = modifier.graphicsLayer {
            val progress = backProgress.coerceIn(0f, 1f)
            val scale = 1f - 0.04f * progress
            scaleX = scale
            scaleY = scale
            alpha = 1f - 0.14f * progress
        },
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    )
}

/** Dropdown menu with release-based outside dismissal and predictive Back. */
@Composable
internal fun XyluneDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnClickOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ReleaseDismissOutsideLayer(
        visible = expanded,
        onDismissRequest = onDismissRequest,
        dismissOnOutsideRelease = dismissOnClickOutside,
    )
    MaterialDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        content = content,
    )
}
