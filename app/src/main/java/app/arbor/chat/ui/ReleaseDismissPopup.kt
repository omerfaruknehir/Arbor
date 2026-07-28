package app.arbor.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.DropdownMenu as MaterialDropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * A transparent, full-screen popup placed underneath Arbor popups.
 *
 * Compose's standard focusable popup receives ACTION_OUTSIDE on pointer-down,
 * which dismisses immediately and interrupts predictive/edge Back gestures.
 * This layer keeps the popup alive for the complete gesture and dismisses only
 * after all pointers are released.
 */
@Composable
internal fun ReleaseDismissOutsideLayer(
    visible: Boolean,
    onDismissRequest: () -> Unit,
) {
    if (!visible) return

    BackHandler(enabled = true, onBack = onDismissRequest)
    val configuration = LocalConfiguration.current
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
                .requiredSize(configuration.screenWidthDp.dp, configuration.screenHeightDp.dp)
                .pointerInput(onDismissRequest) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var consumedByChild = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            consumedByChild = consumedByChild || event.changes.any { it.isConsumed }
                            if (event.changes.none { it.pressed }) break
                        }

                        if (!consumedByChild) onDismissRequest()
                    }
                },
        )
    }
}

/** Dropdown menu whose outside dismissal happens on pointer-up, not down. */
@Composable
internal fun ArborDropdownMenu(
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
