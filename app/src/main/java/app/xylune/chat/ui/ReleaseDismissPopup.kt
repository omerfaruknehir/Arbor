package app.xylune.chat.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@Composable
private fun XylunePopupBackHandler(onDismissRequest: () -> Unit) {
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    BackHandler(enabled = true) {
        if (imeVisible) keyboard?.hide() else onDismissRequest()
    }
}

/**
 * A transparent, full-screen popup placed underneath Xylune popups.
 *
 * Compose reports ordinary outside taps on pointer-down and can cancel the
 * pointer stream when Android takes over an edge Back gesture. Xylune waits for
 * pointer-up and never treats a gesture that began in a system-gesture inset as
 * an outside tap.
 */
@Composable
internal fun ReleaseDismissOutsideLayer(
    visible: Boolean,
    onDismissRequest: () -> Unit,
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
                .pointerInput(onDismissRequest, leftBackEdgePx, rightBackEdgePx) {
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
                        if (!startedInBackEdge && !consumedByChild) onDismissRequest()
                    }
                },
        )
    }
}

/**
 * Xylune's keyboard-safe dialog.
 *
 * Outside pointer-down never destroys an in-progress edit. Back first closes
 * the IME; a subsequent Back dismisses the dialog. Explicit Cancel/Save actions
 * remain the reliable dismissal path.
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
    XylunePopupBackHandler(onDismissRequest)
    MaterialAlertDialog(
        onDismissRequest = {},
        confirmButton = confirmButton,
        modifier = modifier,
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

/** Dropdown menu whose outside dismissal happens on pointer-up, not down. */
@Composable
internal fun XyluneDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnClickOutside: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!dismissOnClickOutside) ReleaseDismissOutsideLayer(expanded, onDismissRequest)
    MaterialDropdownMenu(
        expanded = expanded,
        onDismissRequest = if (dismissOnClickOutside) onDismissRequest else ({}),
        modifier = modifier,
        properties = PopupProperties(
            focusable = dismissOnClickOutside,
            dismissOnBackPress = dismissOnClickOutside,
            dismissOnClickOutside = dismissOnClickOutside,
        ),
        content = content,
    )
}
